package com.magicsmart.core.adb

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.random.Random

/** أكواد أزرار الريموت القياسية في Android (android.view.KeyEvent)، كافية لتصفّح واجهة تلفاز. */
object TvKey {
    const val UP = 19
    const val DOWN = 20
    const val LEFT = 21
    const val RIGHT = 22
    const val SELECT = 23 // DPAD_CENTER / OK
    const val BACK = 4
    const val HOME = 3
    const val MENU = 82
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val MUTE = 164
    const val POWER = 26
    const val PLAY_PAUSE = 85
    const val STOP = 86
    const val NEXT = 87
    const val PREVIOUS = 88
    const val REWIND = 89
    const val FAST_FORWARD = 90
    const val RECORD = 130
    const val CHANNEL_UP = 166
    const val CHANNEL_DOWN = 167
    const val SETTINGS = 176
    const val TV_INPUT = 178 // زر SOURCE في أجهزة Android TV القياسية
    const val PROG_RED = 183
    const val PROG_GREEN = 184
    const val PROG_YELLOW = 185
    const val PROG_BLUE = 186
    const val NUM_0 = 7 // الأرقام 0-9 متتالية: 7..16
}

/**
 * جلسة ريموت كنترول: اتصال واحد مُصادَق يبقى مفتوحاً طوال استخدام الشاشة، لإرسال ضغطات متتالية
 * بسرعة دون إعادة المصافحة الكاملة لكل زر. يُفتح فقط بعد موافقة صريحة واحدة عند بدء الجلسة
 * (لا نافذة لكل ضغطة، لأن كل ما تُرسله هذه الجلسة هو input keyevent فقط — مطابق لضغط الريموت
 * الفعلي، لا شيء يغيّر إعدادات النظام أو يحذف أو يثبّت أي شيء).
 */
class AdbRemoteSession private constructor(
    private val socket: Socket, private val inp: DataInputStream, private val out: DataOutputStream
) {
    private val lock = Mutex()

    suspend fun sendKeyEvent(code: Int): Result<Unit> = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                withTimeout(8_000) { execShellIgnoringOutput("input keyevent $code") }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun execShellIgnoringOutput(command: String) {
        val localId = Random.nextInt(1, Int.MAX_VALUE)
        AdbMessage(AdbMessage.A_OPEN, localId, 0, cstr("shell:$command")).write(out)
        var remoteId = 0
        while (true) {
            val msg = AdbMessage.read(inp)
            when (msg.command) {
                AdbMessage.A_OKAY -> remoteId = msg.arg0
                AdbMessage.A_WRTE -> {
                    if (remoteId == 0) remoteId = msg.arg0
                    AdbMessage(AdbMessage.A_OKAY, localId, remoteId, ByteArray(0)).write(out)
                }
                AdbMessage.A_CLSE -> { AdbMessage(AdbMessage.A_CLSE, localId, msg.arg0, ByteArray(0)).write(out); return }
            }
        }
    }

    fun close() { try { socket.close() } catch (e: Exception) { } }

    companion object {
        suspend fun open(ctx: Context, host: String, port: Int): Pair<AdbConnectResult, AdbRemoteSession?> =
            withContext(Dispatchers.IO) {
                withTimeout(20_000) {
                    val sock = Socket()
                    try {
                        sock.connect(InetSocketAddress(host, port), 5000)
                        sock.soTimeout = 15_000
                        val inp = DataInputStream(BufferedInputStream(sock.getInputStream()))
                        val out = DataOutputStream(BufferedOutputStream(sock.getOutputStream()))
                        val kp = AdbKey.keyPair(ctx)
                        val result = handshake(ctx, inp, out, kp)
                        if (result == AdbConnectResult.Connected) result to AdbRemoteSession(sock, inp, out)
                        else { sock.close(); result to null }
                    } catch (e: Exception) {
                        sock.close()
                        AdbConnectResult.Failed("${e.javaClass.simpleName}: ${e.message}") to null
                    }
                }
            }

        private fun handshake(ctx: Context, inp: DataInputStream, out: DataOutputStream, kp: java.security.KeyPair): AdbConnectResult {
            AdbMessage(AdbMessage.A_CNXN, AdbMessage.A_VERSION, AdbMessage.MAX_PAYLOAD, cstr("host::")).write(out)
            var triedSig = false; var triedPub = false
            repeat(6) {
                val msg = AdbMessage.read(inp)
                when (msg.command) {
                    AdbMessage.A_CNXN -> return AdbConnectResult.Connected
                    AdbMessage.A_AUTH -> {
                        if (msg.arg0 != AdbMessage.AUTH_TOKEN) return AdbConnectResult.Failed("رسالة AUTH غير متوقعة")
                        if (!triedSig) { triedSig = true; AdbMessage(AdbMessage.A_AUTH, AdbMessage.AUTH_SIGNATURE, 0, AdbKey.sign(kp.private, msg.data)).write(out) }
                        else if (!triedPub) { triedPub = true; AdbMessage(AdbMessage.A_AUTH, AdbMessage.AUTH_RSAPUBLICKEY, 0, cstr(AdbKey.wirePublicKey(ctx, kp.public))).write(out); return AdbConnectResult.AwaitingDeviceApproval }
                        else return AdbConnectResult.Failed("رفض الجهاز المفتاح")
                    }
                    else -> return AdbConnectResult.Failed("رسالة غير متوقعة أثناء المصادقة")
                }
            }
            return AdbConnectResult.Failed("انتهت محاولات المصادقة")
        }
    }
}
