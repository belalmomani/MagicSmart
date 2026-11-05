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
import kotlin.random.Random

sealed class InstallResult {
    object Success : InstallResult()
    class PmError(val output: String) : InstallResult()
    class Failed(val reason: String) : InstallResult()
}

/**
 * رفع ملف APK إلى /data/local/tmp عبر بروتوكول sync الخاص بـADB، ثم تثبيته بـ pm install.
 * هذا الجزء هو الأكثر تعقيداً في التطبيق ولم يُختبر مقابل جهاز حقيقي. أسوأ احتمال عند خطأ في
 * الترميز هو فشل الرفع أو فشل pm install برسالة واضحة؛ لا يوجد خطر على أجزاء النظام الأخرى.
 */
object AdbInstall {
    private const val CHUNK = 64 * 1024
    private const val MODE_0644 = 33188 // S_IFREG | 0644 بالعشري

    suspend fun pushAndInstall(
        ctx: Context, host: String, port: Int, apk: ByteArray, remoteName: String,
        onProgress: (String) -> Unit, timeoutMs: Long = 120_000
    ): Pair<AdbConnectResult, InstallResult?> = withContext(Dispatchers.IO) {
        withTimeout(timeoutMs) {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(host, port), 5000)
                sock.soTimeout = timeoutMs.toInt()
                val inp = DataInputStream(BufferedInputStream(sock.getInputStream()))
                val out = DataOutputStream(BufferedOutputStream(sock.getOutputStream()))
                val kp = AdbKey.keyPair(ctx)

                val conn = handshakeShared(ctx, inp, out, kp)
                if (conn != AdbConnectResult.Connected) return@withTimeout conn to null

                onProgress("جارٍ الرفع… / Uploading…")
                val remotePath = "/data/local/tmp/$remoteName"
                val pushed = syncPush(inp, out, apk, remotePath)
                if (!pushed.first) return@withTimeout AdbConnectResult.Connected to InstallResult.Failed(pushed.second)

                onProgress("جارٍ التثبيت… / Installing…")
                val result = execOnOpenSocket(inp, out, "pm install -r -d \"$remotePath\"")
                val ok = result.contains("Success", ignoreCase = true)
                AdbConnectResult.Connected to (if (ok) InstallResult.Success else InstallResult.PmError(result.trim().ifEmpty { "لا مخرجات / no output" }))
            }
        }
    }

    // مطابق لـ AdbConnection.handshake لكن متاح هنا أيضاً لأن المصافحة تُعاد قبل الرفع والتثبيت في نفس الاتصال
    private fun handshakeShared(ctx: Context, inp: DataInputStream, out: DataOutputStream, kp: java.security.KeyPair): AdbConnectResult {
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

    /** ينفّذ أمر shell على اتصال مُصادَق بالفعل (دون إعادة المصافحة). */
    private fun execOnOpenSocket(inp: DataInputStream, out: DataOutputStream, command: String): String {
        val localId = Random.nextInt(1, Int.MAX_VALUE)
        AdbMessage(AdbMessage.A_OPEN, localId, 0, cstr("shell:$command")).write(out)
        var remoteId = 0
        val sb = StringBuilder()
        while (true) {
            val msg = AdbMessage.read(inp)
            when (msg.command) {
                AdbMessage.A_OKAY -> remoteId = msg.arg0
                AdbMessage.A_WRTE -> {
                    sb.append(String(msg.data, Charsets.UTF_8))
                    if (remoteId == 0) remoteId = msg.arg0
                    AdbMessage(AdbMessage.A_OKAY, localId, remoteId, ByteArray(0)).write(out)
                }
                AdbMessage.A_CLSE -> { AdbMessage(AdbMessage.A_CLSE, localId, msg.arg0, ByteArray(0)).write(out); return sb.toString() }
            }
        }
    }

    /** رفع بايتات عبر خدمة sync: (SEND/DATA/DONE)، وفق بروتوكول adb sync الموثَّق. */
    private fun syncPush(inp: DataInputStream, out: DataOutputStream, data: ByteArray, remotePath: String): Pair<Boolean, String> {
        val localId = Random.nextInt(1, Int.MAX_VALUE)
        AdbMessage(AdbMessage.A_OPEN, localId, 0, cstr("sync:")).write(out)
        var remoteId = 0
        // ننتظر فتح القناة (OKAY على مستوى ADB) قبل أي إرسال
        while (remoteId == 0) {
            val msg = AdbMessage.read(inp)
            when (msg.command) {
                AdbMessage.A_OKAY -> remoteId = msg.arg0
                AdbMessage.A_CLSE -> return false to "الجهاز لا يدعم خدمة sync / device doesn't support sync service"
            }
        }

        fun sendFrame(payload: ByteArray) {
            AdbMessage(AdbMessage.A_WRTE, localId, remoteId, payload).write(out)
            // ننتظر تأكيد تدفق البيانات (OKAY) قبل أي إطار تالٍ
            while (true) {
                val msg = AdbMessage.read(inp)
                when (msg.command) {
                    AdbMessage.A_OKAY -> return
                    AdbMessage.A_CLSE -> throw IllegalStateException("أُغلقت القناة أثناء الرفع / channel closed during upload")
                    AdbMessage.A_WRTE -> { AdbMessage(AdbMessage.A_OKAY, localId, remoteId, ByteArray(0)).write(out) } // رسالة من الجهاز قبل أوانها؛ نُقرّها ونواصل الانتظار
                }
            }
        }

        fun le32(v: Int): ByteArray {
            val b = java.io.ByteArrayOutputStream(4)
            b.write(v and 0xFF); b.write((v ushr 8) and 0xFF); b.write((v ushr 16) and 0xFF); b.write((v ushr 24) and 0xFF)
            return b.toByteArray()
        }

        try {
            val pathSpec = "$remotePath,$MODE_0644".toByteArray(Charsets.UTF_8)
            sendFrame("SEND".toByteArray() + le32(pathSpec.size) + pathSpec)

            var offset = 0
            while (offset < data.size) {
                val len = minOf(CHUNK, data.size - offset)
                val chunk = data.copyOfRange(offset, offset + len)
                sendFrame("DATA".toByteArray() + le32(len) + chunk)
                offset += len
            }
            sendFrame("DONE".toByteArray() + le32((System.currentTimeMillis() / 1000).toInt()))

            // قراءة حالة الرفع (OKAY أو FAIL) التي يرسلها الجهاز كـ WRTE
            var status: String? = null
            while (status == null) {
                val msg = AdbMessage.read(inp)
                when (msg.command) {
                    AdbMessage.A_WRTE -> {
                        val id = String(msg.data.copyOfRange(0, minOf(4, msg.data.size)), Charsets.US_ASCII)
                        status = if (id == "FAIL") {
                            val mlen = if (msg.data.size >= 8) {
                                (msg.data[4].toInt() and 0xFF) or ((msg.data[5].toInt() and 0xFF) shl 8) or
                                    ((msg.data[6].toInt() and 0xFF) shl 16) or ((msg.data[7].toInt() and 0xFF) shl 24)
                            } else 0
                            val m = if (msg.data.size >= 8 + mlen) String(msg.data, 8, mlen, Charsets.UTF_8) else "فشل غير مُفسَّر / unparsed failure"
                            "FAIL:$m"
                        } else "OKAY"
                        AdbMessage(AdbMessage.A_OKAY, localId, remoteId, ByteArray(0)).write(out)
                    }
                    AdbMessage.A_CLSE -> { status = "FAIL:أُغلقت القناة دون رد / channel closed with no reply" }
                }
            }
            AdbMessage(AdbMessage.A_WRTE, localId, remoteId, "QUIT".toByteArray() + le32(0)).write(out)
            AdbMessage(AdbMessage.A_CLSE, localId, remoteId, ByteArray(0)).write(out)
            return if (status == "OKAY") true to "" else false to status!!.removePrefix("FAIL:")
        } catch (e: Exception) {
            return false to "${e.javaClass.simpleName}: ${e.message}"
        }
    }
}
