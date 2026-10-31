package com.magicsmart.core.link

import android.util.Base64
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** إطارات بطول مسبق: 4 بايت للطول ثم البيانات. بروتوكول تبادل النتائج منفصل تماماً عن ADB. */
internal object Wire {
    const val SERVICE_TYPE = "_magicsmart._tcp."
    private const val MAX_PLAIN = 64 * 1024
    const val MAX_SECURE = 2 * 1024 * 1024

    fun write(out: DataOutputStream, data: ByteArray) { out.writeInt(data.size); out.write(data); out.flush() }
    fun read(inp: DataInputStream, max: Int): ByteArray {
        val n = inp.readInt()
        require(n in 0..max) { "حجم إطار غير مقبول" }
        return ByteArray(n).also { inp.readFully(it) }
    }
    fun writeJson(out: DataOutputStream, o: JSONObject) = write(out, o.toString().toByteArray(Charsets.UTF_8))
    fun readJson(inp: DataInputStream): JSONObject = JSONObject(String(read(inp, MAX_PLAIN), Charsets.UTF_8))
    fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
    fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
    fun error(msg: String, code: String = "error") = JSONObject().put("type", "error").put("code", code).put("message", msg)
}

internal object LinkCrypto {
    private val rnd = SecureRandom()

    fun randomBytes(n: Int) = ByteArray(n).also { rnd.nextBytes(it) }

    fun sha256(vararg parts: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        parts.forEach { md.update(it) }
        return md.digest()
    }

    fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(if (key.isEmpty()) ByteArray(32) else key, "HmacSHA256"))
        parts.forEach { mac.update(it) }
        return mac.doFinal()
    }

    /** HKDF-SHA256 (RFC 5869). */
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: String, len: Int): ByteArray {
        val prk = hmac(salt, ikm)
        val infoB = info.toByteArray(Charsets.UTF_8)
        val out = java.io.ByteArrayOutputStream()
        var t = ByteArray(0)
        var i = 1
        while (out.size() < len) {
            t = hmac(prk, t, infoB, byteArrayOf(i.toByte()))
            out.write(t); i++
        }
        return out.toByteArray().copyOf(len)
    }

    fun newEcKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    fun ecdh(priv: PrivateKey, peerPubEncoded: ByteArray): ByteArray {
        val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(peerPubEncoded))
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(priv); ka.doPhase(pub, true)
        return ka.generateSecret()
    }

    fun eq(a: ByteArray, b: ByteArray) = MessageDigest.isEqual(a, b)

    /** رمز تحقق من 6 أرقام يُشتق من مفاتيح الطرفين؛ يقارنه المستخدم بصرياً على الجهازين. */
    fun sas(vararg parts: ByteArray): String {
        val h = sha256("MS-SAS".toByteArray(), *parts)
        val n = ((h[0].toInt() and 0xFF) shl 24) or ((h[1].toInt() and 0xFF) shl 16) or
            ((h[2].toInt() and 0xFF) shl 8) or (h[3].toInt() and 0xFF)
        return "%06d".format((n.toLong() and 0xFFFFFFFFL) % 1_000_000L)
    }

    fun authKey(psk: ByteArray, transcript: ByteArray) = hkdf(psk, transcript, "MS-AUTH", 32)

    /** يرجع (مفتاح عميل→خادم، مفتاح خادم→عميل). */
    fun sessionKeys(secret: ByteArray, psk: ByteArray, transcript: ByteArray): Pair<ByteArray, ByteArray> {
        val k = hkdf(secret + psk, transcript, "MS-SESSION", 64)
        return k.copyOfRange(0, 32) to k.copyOfRange(32, 64)
    }
}

/** قناة مشفّرة AES-256-GCM بمفتاح لكل اتجاه وعدّاد يمنع إعادة الإرسال. */
internal class SecureChannel(
    private val inp: DataInputStream, private val out: DataOutputStream,
    private val sendKey: ByteArray, private val recvKey: ByteArray
) {
    private var sendCtr = 0L
    private var recvCtr = 0L
    private fun iv(c: Long) = ByteBuffer.allocate(12).putInt(0).putLong(c).array()

    fun send(o: JSONObject) {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sendKey, "AES"), GCMParameterSpec(128, iv(sendCtr)))
        val ct = c.doFinal(o.toString().toByteArray(Charsets.UTF_8))
        sendCtr++
        Wire.write(out, ct)
    }

    fun recv(): JSONObject {
        val ct = Wire.read(inp, Wire.MAX_SECURE)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(recvKey, "AES"), GCMParameterSpec(128, iv(recvCtr)))
        val pt = c.doFinal(ct)
        recvCtr++
        return JSONObject(String(pt, Charsets.UTF_8))
    }
}
