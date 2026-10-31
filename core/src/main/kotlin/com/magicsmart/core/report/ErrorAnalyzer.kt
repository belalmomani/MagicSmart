package com.magicsmart.core.report

class Finding(val title: String, val ids: List<String>, val explanation: String, val suggestion: String)

/** تحليل قائم على قواعد لنتائج الدورة الفعلية: يجمع الإخفاقات في أسباب مفهومة. أول قاعدة مطابقة تُحتسب فقط. */
object ErrorAnalyzer {
    private class Rule(val title: String, val explanation: String, val suggestion: String, val test: (StoredResult) -> Boolean)

    private fun text(r: StoredResult) = r.actual + " " + (r.failureReason ?: "")

    private val rules = listOf(
        Rule("سياسة الجهاز تمنع ميزات التصحيح", "قيد DISALLOW_DEBUGGING_FEATURES مفعّل من مسؤول الجهاز.",
            "لا يمكن تجاوزه من التطبيق؛ يزيله مسؤول الجهاز.") { text(it).contains("DISALLOW_DEBUGGING_FEATURES") },
        Rule("استثناءات داخلية أثناء التنفيذ", "اختبارات رمت استثناءً غير متوقع، وقد يكون خطأً في الاختبار نفسه.",
            "أرسل التقرير للمطوّر لإصلاحها.") { it.status == "FAILED" && text(it).contains("استثناء") },
        Rule("النظام يمنع التطبيق (SecurityException/قيود)", "النظام أو الشركة المصنّعة حجب الوصول.",
            "هذا حد حماية في Android ولا يُتجاوز؛ جرّب المسار اليدوي.") { it.status == "BLOCKED" },
        Rule("صلاحيات ناقصة", "اختبارات تحتاج صلاحية غير ممنوحة.", "امنح الصلاحية المذكورة في سبب الفشل.") { it.status == "PERMISSION_REQUIRED" },
        Rule("خصائص النظام محجوبة عن التطبيق", "القراءة عبر getprop ترجع فراغاً بسبب قيود SELinux.",
            "نتيجة طبيعية للتطبيقات العادية؛ التحقق الكامل يحتاج ADB.") { text(it).contains("SELinux") || text(it).contains("لم يعرض النظام") },
        Rule("اكتشاف mDNS لم يُرصد", "لم تظهر خدمة ADB اللاسلكي خلال المهلة.",
            "تأكد من اتصال Wi-Fi وتفعيل التصحيح اللاسلكي ثم أعد الفحص.") {
            text(it).contains("mDNS") || text(it).contains("لم تُرصد") || text(it).contains("لم يُكتشف")
        },
        Rule("مسارات أو مكوّنات غير موجودة على الجهاز", "المسار غير معرَّف في هذا النظام (ActivityNotFound/NameNotFound).",
            "طبيعي؛ استعمل مساراً بديلاً أو أضف مسار الشركة إلى ملف OEM.") {
            it.status == "UNSUPPORTED" && (text(it).contains("ActivityNotFound") || text(it).contains("NameNotFound") ||
                text(it).contains("غير موجود") || text(it).contains("لا يوجد نشاط"))
        },
        Rule("ميزات غير مدعومة في هذا الإصدار أو العتاد", "الميزة غير متاحة على هذا الجهاز.", "لا إجراء مطلوب.") { it.status == "UNSUPPORTED" },
        Rule("خطوات تحتاج تدخلك", "اختبارات لا يكتمل التحقق منها إلا بعد إجراء منك.",
            "نفّذ الإجراء المذكور ثم عد للتطبيق ليُعاد التحقق تلقائياً.") { it.status == "USER_ACTION_REQUIRED" },
        Rule("حالات غير معروفة", "لا توجد معلومات كافية للحكم.", "أعد الفحص أو راجع تفاصيل كل حالة.") { it.status == "UNKNOWN" },
        Rule("إخفاقات أخرى", "اختبارات فشلت لأسباب غير مصنفة.", "راجع تفاصيلها.") { it.status == "FAILED" }
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
