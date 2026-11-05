package com.magicsmart.core.adb

import android.content.Context
import android.util.Base64
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPrivateCrtKeySpec
import java.security.spec.RSAPublicKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * مفتاح RSA خاص بهذا التطبيق لمصادقة ADB (مثل adbkey/adbkey.pub في أدوات adb الرسمية).
 * يُنشأ مرة واحدة ويُخزَّن في مساحة التطبيق الخاصة. لا يُستخدم مفتاح أي أداة أخرى ولا يُشارَك.
 */
object AdbKey {
    private const val BITS = 2048
    private const val WORDS = BITS / 32 // 64

    private fun dir(ctx: Context) = File(ctx.filesDir, "adbkey").apply { mkdirs() }

    fun keyPair(ctx: Context): KeyPair {
        val priv = File(dir(ctx), "priv")
        val pub = File(dir(ctx), "pub")
        if (priv.exists() && pub.exists()) {
            val kf = KeyFactory.getInstance("RSA")
            val privateKey = kf.generatePrivate(PKCS8EncodedKeySpec(priv.readBytes()))
            val publicKey = kf.generatePublic(X509EncodedKeySpec(pub.readBytes()))
            return KeyPair(publicKey, privateKey)
        }
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(BITS) }.generateKeyPair()
        priv.writeBytes(kp.private.encoded)
        pub.writeBytes(kp.public.encoded)
        return kp
    }

    /**
     * يبني نص المفتاح العام بصيغة adb السلكية (بنية mincrypt الثنائية مُرمَّزة base64 + تعليق).
     * هذا تنسيق ثابت موثَّق منذ سنوات في أدوات adb؛ لم يُختبر هنا مقابل جهاز حقيقي.
     */
    fun wirePublicKey(ctx: Context, public: PublicKey): String {
        val kf = KeyFactory.getInstance("RSA")
        val spec = kf.getKeySpec(public, RSAPublicKeySpec::class.java)
        val n = spec.modulus
        val e = spec.publicExponent
        val r = BigInteger.ONE.shiftLeft(WORDS * 32) // R = 2^(numWords*32)
        val n0 = n.mod(BigInteger.ONE.shiftLeft(32))
        val n0inv = (BigInteger.ONE.shiftLeft(32) - n0.modInverse(BigInteger.ONE.shiftLeft(32))).mod(BigInteger.ONE.shiftLeft(32))
        val rr = r.multiply(r).mod(n)

        val buf = java.io.ByteArrayOutputStream()
        fun u32(v: Long) { for (i in 0 until 4) buf.write(((v shr (8 * i)) and 0xFF).toInt()) }
        fun words(v: BigInteger) {
            var x = v
            val mask = BigInteger.ONE.shiftLeft(32) - BigInteger.ONE
            for (i in 0 until WORDS) {
                val w = x.and(mask).toLong()
                u32(w)
                x = x.shiftRight(32)
            }
        }
        u32(WORDS.toLong())
        u32(n0inv.toLong())
        words(n)
        words(rr)
        u32(e.toLong())

        val b64 = Base64.encodeToString(buf.toByteArray(), Base64.NO_WRAP)
        return "$b64 magicsmart@device"
    }

    /** يوقّع رمز التحقق (20 بايت) بصيغة PKCS#1 v1.5 لخوارزمية SHA1، كما يتوقع adbd. */
    fun sign(private: PrivateKey, token: ByteArray): ByteArray {
        // بادئة ASN.1 الثابتة لـ DigestInfo الخاصة بـ SHA1 (RFC 3447)
        val sha1Prefix = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b.toByte(), 0x0e, 0x03, 0x02,
            0x1a, 0x05, 0x00, 0x04, 0x14
        )
        val digestInfo = sha1Prefix + token
        val sig = Signature.getInstance("NONEwithRSA")
        sig.initSign(private)
        sig.update(digestInfo)
        return sig.sign()
    }
}
