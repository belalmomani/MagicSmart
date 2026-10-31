package com.magicsmart.core.oem

import android.content.Context
import com.magicsmart.core.DeviceSnapshot
import com.magicsmart.core.engine.DeviceTarget
import org.json.JSONArray
import org.json.JSONObject

class OemComponent(val id: String, val pkg: String, val cls: String, val target: DeviceTarget, val title: String)
class OemAction(val id: String, val action: String, val title: String)

class OemProfile(
    val id: String,
    val name: String,
    val notes: String,
    private val manufacturers: List<String>,
    private val brands: List<String>,
    private val modelRegex: Regex?,
    private val hardwareRegex: Regex?,
    private val tvOnly: Boolean,
    private val minApi: Int,
    private val maxApi: Int,
    val packages: List<String>,
    val components: List<OemComponent>,
    val actions: List<OemAction>
) {
    /** سبب المطابقة أو null. الملف العام لا يطابق أبداً؛ يُختار احتياطياً. */
    fun matchReason(s: DeviceSnapshot): String? {
        if (tvOnly && !s.isTv) return null
        if (s.sdkInt !in minApi..maxApi) return null
        manufacturers.firstOrNull { it.equals(s.manufacturer, true) }?.let { return "الشركة المصنّعة = $it" }
        brands.firstOrNull { it.equals(s.brand, true) }?.let { return "العلامة = $it" }
        modelRegex?.takeIf { it.containsMatchIn(s.model) }?.let { return "الموديل يطابق ${it.pattern}" }
        hardwareRegex?.takeIf { it.containsMatchIn(s.hardware + " " + s.board) }?.let { return "العتاد يطابق ${it.pattern}" }
        return null
    }
}

class OemLoad(val profiles: List<OemProfile>, val fingerprintPackages: List<String>, val errors: List<String>)

object OemProfiles {
    @Volatile private var cached: OemLoad? = null

    fun get(ctx: Context): OemLoad = cached ?: synchronized(this) {
        cached ?: load(ctx).also { cached = it }
    }

    /** الملفات المطابقة مع السبب، وإلا الملف العام. */
    fun select(load: OemLoad, s: DeviceSnapshot): List<Pair<OemProfile, String>> {
        val matched = load.profiles.filter { it.id != "generic" }.mapNotNull { p -> p.matchReason(s)?.let { p to it } }
        if (matched.isNotEmpty()) return matched
        val g = load.profiles.firstOrNull { it.id == "generic" } ?: return emptyList()
        return listOf(g to "لا ملف مطابق؛ استُخدم الملف العام")
    }

    private fun load(ctx: Context): OemLoad {
        val errors = mutableListOf<String>()
        val profiles = mutableListOf<OemProfile>()
        var fp: List<String> = emptyList()
        val names = try {
            ctx.assets.list("oem")?.sorted() ?: emptyList()
        } catch (e: Exception) {
            errors += "تعذّر سرد مجلد oem: ${e.message}"; emptyList()
        }
        for (n in names) {
            if (!n.endsWith(".json")) continue
            try {
                val text = ctx.assets.open("oem/$n").bufferedReader(Charsets.UTF_8).use { it.readText() }
                val o = JSONObject(text)
                if (n.startsWith("_")) {
                    if (n == "_fingerprint.json") fp = o.optJSONArray("packages").strings()
                } else profiles += parse(o)
            } catch (e: Exception) {
                errors += "$n: ${e.javaClass.simpleName}: ${e.message}"
            }
        }
        return OemLoad(profiles, fp, errors)
    }

    private fun parse(o: JSONObject): OemProfile {
        fun rx(k: String) = o.optString(k, "").takeIf { it.isNotEmpty() }?.let { Regex(it) }
        val comps = o.optJSONArray("components").objects().map {
            OemComponent(
                it.getString("id"), it.getString("package"), it.getString("class"),
                try { DeviceTarget.valueOf(it.optString("target", "ANY").uppercase()) } catch (e: Exception) { DeviceTarget.ANY },
                it.optString("title", it.getString("class").substringAfterLast('.'))
            )
        }
        val acts = o.optJSONArray("actions").objects().map {
            OemAction(it.getString("id"), it.getString("action"), it.optString("title", it.getString("action")))
        }
        return OemProfile(
            id = o.getString("id"), name = o.getString("name"), notes = o.optString("notes", ""),
            manufacturers = o.optJSONArray("manufacturers").strings(), brands = o.optJSONArray("brands").strings(),
            modelRegex = rx("model_regex"), hardwareRegex = rx("hardware_regex"),
            tvOnly = o.optBoolean("tv_only", false), minApi = o.optInt("min_api", 1), maxApi = o.optInt("max_api", 999),
            packages = o.optJSONArray("packages").strings(), components = comps, actions = acts
        )
    }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }
    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
}
