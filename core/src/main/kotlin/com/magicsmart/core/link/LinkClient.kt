package com.magicsmart.core.link

import android.content.Context
import com.magicsmart.core.report.ResultStore
import com.magicsmart.core.report.StoredRun
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

sealed class PairResult {
    class Paired(val peer: PairedPeer) : PairResult()
    class Rejected(val reason: String) : PairResult()
    class Failed(val reason: String) : PairResult()
}

/** عميل الهاتف: اقتران بتأكيد رمز مرئي، ثم جلسات مصادَقة ومشفّرة لجلب التقارير. */
internal object LinkClient {

    suspend fun pair(
        ctx: Context, host: String, port: Int,
        onSas: suspend (peerName: String, sas: String) -> Boolean
    ): PairResult = withContext(Dispatchers.IO) {
        try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(InetAddress.getByName(host), port), 5000)
                sock.soTimeout = 15_000
                val inp = DataInputStream(BufferedInputStream(sock.getInputStream()))
                val out = DataOutputStream(BufferedOutputStream(sock.getOutputStream()))

                val kp = LinkCrypto.newEcKeyPair()
                val nonceA = LinkCrypto.randomBytes(16)
                val pkA = kp.public.encoded
                Wire.writeJson(
                    out, JSONObject().put("type", "pair").put("v", 1).put("name", LinkStore.deviceName(ctx))
                        .put("deviceId", LinkStore.deviceId(ctx)).put("commit", Wire.b64(LinkCrypto.sha256(pkA, nonceA)))
                )
                val offer = Wire.readJson(inp)
                if (offer.optString("type") == "error") return@withContext PairResult.Failed(offer.optString("message", "رفض التلفاز الطلب"))
                if (offer.optString("type") != "offer") return@withContext PairResult.Failed("رد غير متوقع")
                val pkB = Wire.unb64(offer.getString("pk"))
                val nonceB = Wire.unb64(offer.getString("nonce"))
                val peerName = offer.optString("name", "تلفاز").take(40)
                val peerId = offer.getString("deviceId")

                Wire.writeJson(out, JSONObject().put("type", "reveal").put("pk", Wire.b64(pkA)).put("nonce", Wire.b64(nonceA)))
                val secret = LinkCrypto.ecdh(kp.private, pkB)
                val transcript = LinkCrypto.sha256(pkA, nonceA, pkB, nonceB)
                val sas = LinkCrypto.sas(pkA, nonceA, pkB, nonceB)

                if (!onSas(peerName, sas)) {
                    try { Wire.writeJson(out, JSONObject().put("type", "reject")) } catch (e: Exception) { }
                    return@withContext PairResult.Rejected("أُلغي الاقتران من الهاتف")
                }
                val macKey = LinkCrypto.hkdf(secret, transcript, "MS-CONFIRM", 32)
                Wire.writeJson(out, JSONObject().put("type", "confirm").put("mac", Wire.b64(LinkCrypto.hmac(macKey, "client".toByteArray(), transcript))))
                sock.soTimeout = 130_000
                val resp = Wire.readJson(inp)
                when (resp.optString("type")) {
                    "reject" -> return@withContext PairResult.Rejected("رفض التلفاز طلب الربط")
                    "confirm" -> {
                        val ok = LinkCrypto.eq(Wire.unb64(resp.getString("mac")), LinkCrypto.hmac(macKey, "server".toByteArray(), transcript))
                        if (!ok) return@withContext PairResult.Failed("فشل التحقق من تأكيد التلفاز")
                    }
                    else -> return@withContext PairResult.Failed(resp.optString("message", "رد غير متوقع"))
                }
                val psk = LinkCrypto.hkdf(secret, transcript, "MS-PSK", 32)
                val peer = PairedPeer(peerId, peerName, psk, System.currentTimeMillis())
                LinkStore.add(ctx, peer)
                PairResult.Paired(peer)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            PairResult.Failed("${e.javaClass.simpleName}: ${e.message ?: "تعذّر الاتصال"}")
        }
    }

    private fun openSession(ctx: Context, sock: Socket, host: String, port: Int, peer: PairedPeer): SecureChannel {
        sock.connect(InetSocketAddress(InetAddress.getByName(host), port), 5000)
        sock.soTimeout = 15_000
        val inp = DataInputStream(BufferedInputStream(sock.getInputStream()))
        val out = DataOutputStream(BufferedOutputStream(sock.getOutputStream()))
        val kp = LinkCrypto.newEcKeyPair()
        val nonceC = LinkCrypto.randomBytes(16)
        val pkC = kp.public.encoded
        Wire.writeJson(
            out, JSONObject().put("type", "session").put("deviceId", LinkStore.deviceId(ctx))
                .put("pk", Wire.b64(pkC)).put("nonce", Wire.b64(nonceC))
        )
        val resp = Wire.readJson(inp)
        if (resp.optString("type") == "error") {
            throw IllegalStateException(
                if (resp.optString("code") == "unpaired") "التلفاز لا يعرف هذا الهاتف (ألغي الربط من جهته). أعد الاقتران."
                else resp.optString("message", "رفض التلفاز")
            )
        }
        val pkS = Wire.unb64(resp.getString("pk"))
        val nonceS = Wire.unb64(resp.getString("nonce"))
        val transcript = LinkCrypto.sha256(pkC, nonceC, pkS, nonceS)
        val authKey = LinkCrypto.authKey(peer.psk, transcript)
        // التحقق من هوية التلفاز: لا يستطيع إنتاج هذا الرمز إلا من يملك مفتاح الاقتران
        if (!LinkCrypto.eq(Wire.unb64(resp.getString("mac")), LinkCrypto.hmac(authKey, "server".toByteArray(), transcript)))
            throw IllegalStateException("فشل التحقق من هوية التلفاز؛ ربما ليس الجهاز المقترن نفسه")
        Wire.writeJson(out, JSONObject().put("type", "auth").put("mac", Wire.b64(LinkCrypto.hmac(authKey, "client".toByteArray(), transcript))))
        val secret = LinkCrypto.ecdh(kp.private, pkS)
        val (c2s, s2c) = LinkCrypto.sessionKeys(secret, peer.psk, transcript)
        return SecureChannel(inp, out, sendKey = c2s, recvKey = s2c)
    }

    suspend fun fetchReport(ctx: Context, host: String, port: Int, peer: PairedPeer): StoredRun =
        withContext(Dispatchers.IO) {
            Socket().use { sock ->
                val ch = openSession(ctx, sock, host, port, peer)
                ch.send(JSONObject().put("type", "get_report"))
                val r = ch.recv()
                if (r.optString("type") != "report") throw IllegalStateException(r.optString("message", "رد غير متوقع"))
                ResultStore.fromJson(r.getJSONObject("run"))
            }
        }

    suspend fun unpair(ctx: Context, host: String, port: Int, peer: PairedPeer) =
        withContext(Dispatchers.IO) {
            Socket().use { sock ->
                val ch = openSession(ctx, sock, host, port, peer)
                ch.send(JSONObject().put("type", "unpair"))
                ch.recv()
                Unit
            }
        }
}
