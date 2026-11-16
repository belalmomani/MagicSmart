package com.magicsmart.core.adb

import android.content.Context
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyPair
import kotlin.random.Random

/**
 * منطق المصافحة وتنفيذ shell المشترك بين AdbConnection وAdbInstall وAdbRemoteSession.
 * كان هذا الكود مكرَّراً ثلاث مرات؛ وُحِّد هنا دون أي تغيير في السلوك (تأكَّد بمقارنة الصيغ الثلاث
 * قبل التوحيد) حتى لا يحتاج أي إصلاح مستقبلي للبروتوكول تطبيقه في أكثر من مكان.
 */
internal object AdbCore {

    fun handshake(ctx: Context, inp: DataInputStream, out: DataOutputStream, kp: KeyPair): AdbConnectResult {
        AdbMessage(AdbMessage.A_CNXN, AdbMessage.A_VERSION, AdbMessage.MAX_PAYLOAD, cstr("host::")).write(out)
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

    /** يفتح قناة shell:<command> وينفّذها على اتصال مُصادَق بالفعل، ويُعيد مخرجاتها نصاً. */
    fun execShell(inp: DataInputStream, out: DataOutputStream, command: String): String {
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
                else -> { /* تجاهل */ }
            }
        }
    }
}
