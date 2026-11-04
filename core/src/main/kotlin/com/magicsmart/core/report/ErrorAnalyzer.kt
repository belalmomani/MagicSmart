package com.magicsmart.core.report

class Finding(val title: String, val ids: List<String>, val explanation: String, val suggestion: String)

/** تحليل قائم على قواعد لنتائج الدورة الفعلية: يجمع الإخفاقات في أسباب مفهومة. أول قاعدة مطابقة تُحتسب فقط. */
object ErrorAnalyzer {
    private class Rule(val title: String, val explanation: String, val suggestion: String, val test: (StoredResult) -> Boolean)

    private fun text(r: StoredResult) = r.actual + " " + (r.failureReason ?: "")

    private val rules = listOf(
        Rule("سياسة الجهاز تمنع ميزات التصحيح / Device policy blocks debugging", "قيد DISALLOW_DEBUGGING_FEATURES مفعّل من مسؤول الجهاز. / The DISALLOW_DEBUGGING_FEATURES restriction is set by the device admin.",
            "لا يمكن تجاوزه من التطبيق؛ يزيله مسؤول الجهاز. / Cannot be bypassed from the app; only the admin can remove it.") { text(it).contains("DISALLOW_DEBUGGING_FEATURES") },
        Rule("استثناءات داخلية أثناء التنفيذ / Internal exceptions", "اختبارات رمت استثناءً غير متوقع، وقد يكون خطأً في الاختبار نفسه. / Some tests threw an unexpected exception, possibly a bug in the test itself.",
            "أرسل التقرير للمطوّر لإصلاحها. / Send the report to the developer to fix it.") { it.status == "FAILED" && text(it).contains("استثناء") },
        Rule("النظام يمنع التطبيق (SecurityException/قيود) / Blocked by the system", "النظام أو الشركة المصنّعة حجب الوصول. / The system or manufacturer blocked access.",
            "هذا حد حماية في Android ولا يُتجاوز؛ جرّب المسار اليدوي. / This is an Android protection boundary; try the manual path instead.") { it.status == "BLOCKED" },
        Rule("صلاحيات ناقصة / Missing permission", "اختبارات تحتاج صلاحية غير ممنوحة. / Tests need a permission that was not granted.", "امنح الصلاحية المذكورة في سبب الفشل. / Grant the permission named in the failure reason.") { it.status == "PERMISSION_REQUIRED" },
        Rule("خصائص النظام محجوبة عن التطبيق / System properties hidden", "القراءة عبر getprop ترجع فراغاً بسبب قيود SELinux. / Reading via getprop returns empty due to SELinux restrictions.",
            "نتيجة طبيعية للتطبيقات العادية؛ التحقق الكامل يحتاج ADB. / Normal for regular apps; full verification needs ADB.") { text(it).contains("SELinux") || text(it).contains("لم يعرض النظام") },
        Rule("اكتشاف mDNS لم يُرصد / mDNS not detected", "لم تظهر خدمة ADB اللاسلكي خلال المهلة. / The wireless ADB service did not appear within the timeout.",
            "تأكد من اتصال Wi-Fi وتفعيل التصحيح اللاسلكي ثم أعد الفحص. / Confirm Wi-Fi and wireless debugging are on, then re-scan.") {
            text(it).contains("mDNS") || text(it).contains("لم تُرصد") || text(it).contains("لم يُكتشف")
        },
        Rule("مسارات أو مكوّنات غير موجودة على الجهاز / Paths/components not found", "المسار غير معرَّف في هذا النظام (ActivityNotFound/NameNotFound). / The path isn\u2019t defined on this system.",
            "طبيعي؛ استعمل مساراً بديلاً أو أضف مسار الشركة إلى ملف OEM. / Normal; use an alternate path or add the vendor\u2019s path to its OEM file.") {
            it.status == "UNSUPPORTED" && (text(it).contains("ActivityNotFound") || text(it).contains("NameNotFound") ||
                text(it).contains("غير موجود") || text(it).contains("لا يوجد نشاط"))
        },
        Rule("ميزات غير مدعومة في هذا الإصدار أو العتاد / Unsupported on this version/hardware", "الميزة غير متاحة على هذا الجهاز. / The feature isn\u2019t available on this device.", "لا إجراء مطلوب. / No action needed.") { it.status == "UNSUPPORTED" },
        Rule("خطوات تحتاج تدخلك / Steps need your action", "اختبارات لا يكتمل التحقق منها إلا بعد إجراء منك. / These tests can\u2019t be verified until you act.",
            "نفّذ الإجراء المذكور ثم عد للتطبيق ليُعاد التحقق تلقائياً. / Do the action, then return to the app for automatic re-verification.") { it.status == "USER_ACTION_REQUIRED" },
        Rule("حالات غير معروفة / Unknown cases", "لا توجد معلومات كافية للحكم. / Not enough information to judge.", "أعد الفحص أو راجع تفاصيل كل حالة. / Re-scan or review each case\u2019s details.") { it.status == "UNKNOWN" },
        Rule("إخفاقات أخرى / Other failures", "اختبارات فشلت لأسباب غير مصنفة. / Tests failed for unclassified reasons.", "راجع تفاصيلها. / Review their details.") { it.status == "FAILED" }
    )

    fun analyze(results: List<StoredResult>): List<Finding> {
        val buckets = LinkedHashMap<Int, MutableList<String>>()
        for (r in results) {
            if (r.status == "SUCCESS") continue
            val i = rules.indexOfFirst { it.test(r) }
            if (i >= 0) buckets.getOrPut(i) { mutableListOf() }.add(r.id)
        }
        return buckets.entries.sortedByDescending { it.value.size }
            .map { (i, ids) -> rules[i].let { Finding(it.title, ids, it.explanation, it.suggestion) } }
    }
}
