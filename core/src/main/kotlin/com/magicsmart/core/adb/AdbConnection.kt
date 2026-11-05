package com.magicsmart.core.adb

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyPair
import kotlin.random.Random

sealed class AdbConnectResult {
    object Connected : AdbConnectResult()
    /** النظام ينتظر منك قبول «السماح بتصحيح USB؟» على شاشة الجهاز المستهدف. */
    object AwaitingDeviceApproval : AdbConnectResult()
    class Failed(val reason: String) : AdbConnectResult()
}

class AdbExecResult(val output: String, val closedCleanly: Boolean)

/**
 * عميل ADB أدنى حد: مصادقة RSA قياسية ثم تنفيذ أمر shell واحد لكل اتصال.
 * يعيد تنفيذ بروتوكول adb الموثَّق (CNXN/AUTH/OPEN/WRTE/OKAY/CLSE) محلياً داخل التطبيق،
 * بدلاً من استدعاء أداة adb خارجية. هذا هو نفس مسار «السماح بتصحيح USB؟» المعتاد، بلا أي تجاوز لحماية النظام.
 */
object AdbConnection {

    suspend fun connectAndExec(
        ctx: Context, host: String, port: Int, command: String, timeoutMs: Long = 20_000
    ): Pair<AdbConnectResult, AdbExecResult?> = withContext(Dispatchers.IO) {
        withTimeout(timeoutMs) {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(host, port), 5000)
                sock.soTimeout = (timeoutMs).toInt()
                val inp = DataInputStream(BufferedInputStream(sock.getInputStream()))
                val out = DataOutputStream(BufferedOutputStream(sock.getOutputStream()))
                val kp = AdbKey.keyPair(ctx)

                val connectResult = handshake(ctx, inp, out, kp)
                if (connectResult != AdbConnectResult.Connected) return@withTimeout connectResult to null
                val execResult = runShell(inp, out, command)
                connectResult to execResult
            }
        }
    }

    private fun sendCnxn(out: DataOutputStream) {
        AdbMessage(AdbMessage.A_CNXN, AdbMessage.A_VERSION, AdbMessage.MAX_PAYLOAD, cstr("host::")).write(out)
    }

    private fun handshake(ctx: Context, inp: DataInputStream, out: DataOutputStream, kp: KeyPair): AdbConnectResult {
        sendCnxn(out)
        var triedSignature = false
        var triedPublicKey = false
        repeat(6) {
            val msg = AdbMessage.read(inp)
            when (msg.command) {
                AdbMessage.A_CNXN -> return AdbConnectResult.Connected
                AdbMessage.A_AUTH -> {
                    if (msg.arg0 != AdbMessage.AUTH_TOKEN) return AdbConnectResult.Failed("رسالة AUTH غير متوقعة")
                    if (!triedSignature) {
                        triedSignature = true
                        val sig = AdbKey.sign(kp.private, msg.data)
                        AdbMessage(AdbMessage.A_AUTH, AdbMessage.AUTH_SIGNATURE, 0, sig).write(out)
                    } else if (!triedPublicKey) {
                        triedPublicKey = true
                        val pub = cstr(AdbKey.wirePublicKey(ctx, kp.public))
                        AdbMessage(AdbMessage.A_AUTH, AdbMessage.AUTH_RSAPUBLICKEY, 0, pub).write(out)
                        return AdbConnectResult.AwaitingDeviceApproval
                    } else {
                        return AdbConnectResult.Failed("رفض الجهاز المفتاح")
                    }
                }
                else -> return AdbConnectResult.Failed("رسالة غير متوقعة أثناء المصادقة")
            }
        }
        return AdbConnectResult.Failed("انتهت محاولات المصادقة")
    }

    private fun runShell(inp: DataInputStream, out: DataOutputStream, command: String): AdbExecResult {
        val localId = Random.nextInt(1, Int.MAX_VALUE)
        AdbMessage(AdbMessage.A_OPEN, localId, 0, cstr("shell:$command")).write(out)
        var remoteId = 0
        val sb = StringBuilder()
        var opened = false
        while (true) {
            val msg = AdbMessage.read(inp)
            when (msg.command) {
                AdbMessage.A_OKAY -> { remoteId = msg.arg0; opened = true }
                AdbMessage.A_WRTE -> {
                    if (msg.arg1 != localId) continue
                    sb.append(String(msg.data, Charsets.UTF_8))
                    if (remoteId == 0) remoteId = msg.arg0
                    AdbMessage(AdbMessage.A_OKAY, localId, remoteId, ByteArray(0)).write(out)
                }
                AdbMessage.A_CLSE -> {
                    if (opened) AdbMessage(AdbMessage.A_CLSE, localId, msg.arg0, ByteArray(0)).write(out)
                    return AdbExecResult(sb.toString(), true)
                }
                else -> { /* تجاهل */ }
            }
        }
    }
}
