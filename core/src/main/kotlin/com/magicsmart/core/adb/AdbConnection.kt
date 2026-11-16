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

    private fun handshake(ctx: Context, inp: DataInputStream, out: DataOutputStream, kp: KeyPair): AdbConnectResult =
        AdbCore.handshake(ctx, inp, out, kp)

    private fun runShell(inp: DataInputStream, out: DataOutputStream, command: String): AdbExecResult =
        AdbExecResult(AdbCore.execShell(inp, out, command), true)
}
