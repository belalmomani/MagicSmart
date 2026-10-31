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
        appendLine("تقرير MAGIC SMART — Developer Unlock Engine")
        appendLine("التاريخ: ${date(run.id)}")
        appendLine("الجهاز: ${run.device} (${if (run.isTv) "تلفاز" else "هاتف"}) — Android ${run.androidInfo}")
        appendLine("ملف الشركة: ${run.oem}")
        appendLine("الاختبارات المنفذة: ${run.results.size} (${if (run.complete) "دورة مكتملة" else "دورة غير مكتملة"})")
        appendLine("الملخص: ${counts(run)}")
        val findings = ErrorAnalyzer.analyze(run.results)
        if (findings.isNotEmpty()) {
            appendLine().appendLine("== تحليل الأخطاء ==")
            findings.forEach {
                appendLine("- ${it.title} (${it.ids.size}): ${it.explanation} المقترح: ${it.suggestion}")
                appendLine("  الاختبارات: ${it.ids.joinToString(", ")}")
            }
        }
        run.results.groupBy { it.engine }.forEach { (engine, list) ->
            appendLine().appendLine("== ${engineLabel(engine)} ==")
            list.forEach { r ->
                appendLine("[${label(r.status)}] ${r.id} ${r.name}")
                appendLine("  المتوقع: ${r.expected}")
                appendLine("  الفعلي: ${r.actual}")
                r.failureReason?.let { appendLine("  السبب: $it") }
                r.nextAction?.let { appendLine("  الإجراء التالي: $it") }
            }
        }
    }
}
