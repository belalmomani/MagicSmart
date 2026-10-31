package com.magicsmart.core.report

import android.content.Context
import com.magicsmart.core.engine.TestResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class StoredResult(
    val id: String, val name: String, val engine: String, val status: String,
    val expected: String, val actual: String, val failureReason: String?, val nextAction: String?,
    val durationMs: Long, val finishedAt: Long
)

class StoredRun(
    val id: Long, val device: String, val androidInfo: String, val isTv: Boolean,
    val oem: String, val complete: Boolean, val results: List<StoredResult>
)

fun TestResult.toStored() = StoredResult(
    definition.id, definition.name, definition.engine.name, status.name, definition.expected,
    actual, failureReason, nextAction, durationMs, finishedAtMillis
)

/** تخزين محلي بملفات JSON داخل مساحة التطبيق الخاصة. لا شبكة ولا رفع. */
object ResultStore {
    private const val MAX_RUNS = 20

    private fun dir(ctx: Context) = File(ctx.filesDir, "runs").apply { mkdirs() }

    fun save(ctx: Context, run: StoredRun) {
        File(dir(ctx), "${run.id}.json").writeText(toJson(run).toString(), Charsets.UTF_8)
        list(ctx).drop(MAX_RUNS).forEach { delete(ctx, it.id) }
    }

    fun list(ctx: Context): List<StoredRun> =
        (dir(ctx).listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { f -> try { fromJson(JSONObject(f.readText(Charsets.UTF_8))) } catch (e: Exception) { null } }
            .sortedByDescending { it.id }

    fun delete(ctx: Context, id: Long) { File(dir(ctx), "$id.json").delete() }
    fun clear(ctx: Context) { dir(ctx).listFiles()?.forEach { it.delete() } }

    fun toJson(r: StoredRun) = JSONObject().apply {
        put("id", r.id); put("device", r.device); put("androidInfo", r.androidInfo)
        put("isTv", r.isTv); put("oem", r.oem); put("complete", r.complete)
        put("results", JSONArray().apply {
            r.results.forEach { x ->
                put(JSONObject().apply {
                    put("id", x.id); put("name", x.name); put("engine", x.engine); put("status", x.status)
                    put("expected", x.expected); put("actual", x.actual)
                    put("failureReason", x.failureReason ?: ""); put("nextAction", x.nextAction ?: "")
                    put("durationMs", x.durationMs); put("finishedAt", x.finishedAt)
                })
            }
        })
    }

    fun fromJson(o: JSONObject): StoredRun {
        val arr = o.getJSONArray("results")
        val rs = (0 until arr.length()).map {
            val x = arr.getJSONObject(it)
            StoredResult(
                x.getString("id"), x.getString("name"), x.getString("engine"), x.getString("status"),
                x.getString("expected"), x.getString("actual"),
                x.optString("failureReason", "").ifEmpty { null }, x.optString("nextAction", "").ifEmpty { null },
                x.optLong("durationMs"), x.optLong("finishedAt")
            )
        }
        return StoredRun(
            o.getLong("id"), o.getString("device"), o.getString("androidInfo"), o.optBoolean("isTv"),
            o.optString("oem"), o.optBoolean("complete"), rs
        )
    }
}
