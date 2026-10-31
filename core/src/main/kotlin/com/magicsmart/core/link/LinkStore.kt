package com.magicsmart.core.link

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class PairedPeer(val id: String, val name: String, val psk: ByteArray, val pairedAt: Long)

/** هوية الجهاز والأجهزة المقترنة، في مساحة التطبيق الخاصة فقط. لا تُرسل لأي جهة خارجية. */
object LinkStore {
    private fun file(ctx: Context) = File(File(ctx.filesDir, "link").apply { mkdirs() }, "store.json")

    @Synchronized private fun read(ctx: Context): JSONObject =
        try { JSONObject(file(ctx).readText(Charsets.UTF_8)) } catch (e: Exception) { JSONObject() }

    @Synchronized private fun write(ctx: Context, o: JSONObject) { file(ctx).writeText(o.toString(), Charsets.UTF_8) }

    @Synchronized fun deviceId(ctx: Context): String {
        val o = read(ctx)
        o.optString("deviceId").takeIf { it.isNotEmpty() }?.let { return it }
        val id = LinkCrypto.randomBytes(8).joinToString("") { "%02x".format(it) }
        o.put("deviceId", id); write(ctx, o)
        return id
    }

    fun deviceName(@Suppress("UNUSED_PARAMETER") ctx: Context): String =
        "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(40)

    @Synchronized fun peers(ctx: Context): List<PairedPeer> {
        val arr = read(ctx).optJSONArray("peers") ?: return emptyList()
        return (0 until arr.length()).mapNotNull {
            try {
                val x = arr.getJSONObject(it)
                PairedPeer(x.getString("id"), x.getString("name"), Wire.unb64(x.getString("psk")), x.optLong("pairedAt"))
            } catch (e: Exception) { null }
        }
    }

    fun find(ctx: Context, id: String): PairedPeer? = peers(ctx).firstOrNull { it.id == id }

    @Synchronized fun add(ctx: Context, p: PairedPeer) {
        val all = peers(ctx).filter { it.id != p.id } + p
        save(ctx, all)
    }

    @Synchronized fun remove(ctx: Context, id: String) { save(ctx, peers(ctx).filter { it.id != id }) }

    private fun save(ctx: Context, list: List<PairedPeer>) {
        val o = read(ctx)
        o.put("peers", JSONArray().apply {
            list.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("psk", Wire.b64(it.psk)).put("pairedAt", it.pairedAt)) }
        })
        write(ctx, o)
    }
}
