package com.magicsmart.core.report

import com.magicsmart.core.ResultStatus
import com.magicsmart.core.engine.Engine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportBuilder {
    fun date(ms: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ms))

    fun label(status: String): String = try { ResultStatus.valueOf(status).arabic } catch (e: Exception) { status }
    fun argb(status: String): Long = try { ResultStatus.valueOf(status).argb } catch (e: Exception) { 0xFFB0BEC5 }
    fun engineLabel(engine: String): String = try { Engine.valueOf(engine).arabic } catch (e: Exception) { engine }

    fun counts(run: StoredRun): String =
        run.results.groupingBy { it.status }.eachCount().entries.joinToString("، ") { "${label(it.key)}: ${it.value}" }

    fun build(run: StoredRun): String = buildString {
        appendLine("تقرير MAGIC SMART — Developer Unlock Engine / MAGIC SMART Report")
        appendLine("التاريخ / Date: ${date(run.id)}")
        appendLine("الجهاز / Device: ${run.device} (${if (run.isTv) "تلفاز / TV" else "هاتف / Phone"}) — Android ${run.androidInfo}")
        appendLine("ملف الشركة / OEM profile: ${run.oem}")
        appendLine("الاختبارات المنفذة / Tests run: ${run.results.size} (${if (run.complete) "دورة مكتملة / complete" else "دورة غير مكتملة / incomplete"})")
        appendLine("الملخص / Summary: ${counts(run)}")
        val findings = ErrorAnalyzer.analyze(run.results)
        if (findings.isNotEmpty()) {
            appendLine().appendLine("== تحليل الأخطاء / Error analysis ==")
            findings.forEach {
                appendLine("- ${it.title} (${it.ids.size}): ${it.explanation} المقترح/Suggestion: ${it.suggestion}")
                appendLine("  الاختبارات / Tests: ${it.ids.joinToString(", ")}")
            }
        }
        run.results.groupBy { it.engine }.forEach { (engine, list) ->
            appendLine().appendLine("== ${engineLabel(engine)} ==")
            list.forEach { r ->
                appendLine("[${label(r.status)}] ${r.id} ${r.name}")
                appendLine("  المتوقع / Expected: ${r.expected}")
                appendLine("  الفعلي / Actual: ${r.actual}")
                r.failureReason?.let { appendLine("  السبب / Reason: $it") }
                r.nextAction?.let { appendLine("  الإجراء التالي / Next step: $it") }
            }
        }
    }
}
