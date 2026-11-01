package com.magicsmart.core.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorAnalyzerTest {
    private fun r(id: String, status: String, actual: String = "", reason: String? = null) =
        StoredResult(id, "اختبار $id", "ADB", status, "متوقع", actual, reason, null, 1, 1)

    @Test fun success_results_are_ignored() {
        assertTrue(ErrorAnalyzer.analyze(listOf(r("A", "SUCCESS"))).isEmpty())
    }

    @Test fun policy_rule_wins_over_generic_blocked() {
        val f = ErrorAnalyzer.analyze(listOf(r("A", "BLOCKED", "x", "DISALLOW_DEBUGGING_FEATURES")))
        assertEquals(1, f.size)
        assertTrue(f[0].title.contains("سياسة"))
    }

    @Test fun missing_paths_are_grouped_and_sorted_by_count() {
        val list = listOf(
            r("N1", "UNSUPPORTED", "لا يوجد نشاط يعالج هذا المسار"),
            r("N2", "UNSUPPORTED", "المكوّن غير موجود على هذا الجهاز"),
            r("U1", "USER_ACTION_REQUIRED", "يحتاج تدخلاً")
        )
        val f = ErrorAnalyzer.analyze(list)
        assertEquals(2, f.size)
        assertEquals(listOf("N1", "N2"), f[0].ids)
        assertEquals(listOf("U1"), f[1].ids)
    }

    @Test fun each_result_counts_once() {
        val f = ErrorAnalyzer.analyze(listOf(r("X", "FAILED", "استثناء أثناء التنفيذ", "IOException")))
        assertEquals(1, f.sumOf { it.ids.size })
    }
}
