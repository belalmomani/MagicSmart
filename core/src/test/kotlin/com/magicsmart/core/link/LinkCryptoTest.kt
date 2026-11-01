package com.magicsmart.core.link

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class LinkCryptoTest {
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    @Test fun hkdf_matches_rfc5869_case3() {
        val okm = LinkCrypto.hkdf(ByteArray(22) { 0x0b }, ByteArray(0), "", 42)
        assertEquals("8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8", hex(okm))
    }

    @Test fun ecdh_is_symmetric() {
        val a = LinkCrypto.newEcKeyPair()
        val b = LinkCrypto.newEcKeyPair()
        assertArrayEquals(
            LinkCrypto.ecdh(a.private, b.public.encoded),
            LinkCrypto.ecdh(b.private, a.public.encoded)
        )
    }

    @Test fun sas_is_six_digits_deterministic_and_input_sensitive() {
        val x = ByteArray(16) { 1 }
        val y = ByteArray(16) { 2 }
        val s1 = LinkCrypto.sas(x, y)
        assertEquals(6, s1.length)
        assertTrue(s1.all { it.isDigit() })
        assertEquals(s1, LinkCrypto.sas(x, y))
        assertNotEquals(s1, LinkCrypto.sas(y, x))
    }

    @Test fun session_keys_are_distinct_per_direction() {
        val (c2s, s2c) = LinkCrypto.sessionKeys(ByteArray(32) { 3 }, ByteArray(32) { 4 }, ByteArray(32) { 5 })
        assertEquals(32, c2s.size)
        assertEquals(32, s2c.size)
        assertFalse(c2s.contentEquals(s2c))
    }

    private fun channels(): Triple<SecureChannel, ByteArrayOutputStream, (ByteArray) -> SecureChannel> {
        val k1 = ByteArray(32) { 1 }
        val k2 = ByteArray(32) { 2 }
        val wire = ByteArrayOutputStream()
        val sender = SecureChannel(DataInputStream(ByteArrayInputStream(ByteArray(0))), DataOutputStream(wire), k1, k2)
        val receiverFor = { bytes: ByteArray ->
            SecureChannel(DataInputStream(ByteArrayInputStream(bytes)), DataOutputStream(ByteArrayOutputStream()), k2, k1)
        }
        return Triple(sender, wire, receiverFor)
    }

    @Test fun secure_channel_roundtrip() {
        val (sender, wire, receiverFor) = channels()
        sender.send(JSONObject().put("type", "ping").put("n", 7))
        val got = receiverFor(wire.toByteArray()).recv()
        assertEquals("ping", got.getString("type"))
        assertEquals(7, got.getInt("n"))
    }

    @Test fun secure_channel_detects_tampering() {
        val (sender, wire, receiverFor) = channels()
        sender.send(JSONObject().put("type", "ping"))
        val bytes = wire.toByteArray()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x01).toByte()
        try { receiverFor(bytes).recv(); fail("كان يجب رفض الإطار المعدَّل") } catch (e: java.security.GeneralSecurityException) { }
    }

    @Test fun secure_channel_rejects_replayed_or_reordered_frame() {
        val (sender, wire, receiverFor) = channels()
        sender.send(JSONObject().put("type", "first"))
        val n1 = wire.size()
        sender.send(JSONObject().put("type", "second"))
        val second = wire.toByteArray().copyOfRange(n1, wire.size())
        try { receiverFor(second).recv(); fail("كان يجب رفض إطار خارج الترتيب") } catch (e: java.security.GeneralSecurityException) { }
    }
}
