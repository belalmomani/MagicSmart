package com.magicsmart.core.adb

/** كشف الكلمات المفتاحية لأوامر قد تضر بالنظام، لإظهار تحذير إضافي قبل تنفيذها. ليس حجباً، فقط تنبيهاً أوضح. */
object DangerWords {
    private val patterns = listOf(
        "rm\\s+-rf", "mkfs", "\\bdd\\s+if=", "\\bformat\\b", "factory\\s*reset", "wipe", "\\breboot\\b",
        "recovery", "bootloader", "flash", "settings\\s+put", "pm\\s+uninstall", "pm\\s+clear",
        "am\\s+force-stop", "\\bkill\\b", "\\bsvc\\b", "setenforce", "chmod\\s+777", "format-all"
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    /** نص التحذير إن طابق الأمر كلمة خطرة، وإلا null. */
    fun warn(command: String): String? {
        val hit = patterns.any { it.containsMatchIn(command) }
        return if (hit) {
            "هذا الأمر يبدو قادراً على حذف بيانات أو تغيير النظام أو إعادة تشغيله. تأكد أنك تقصده بالضبط قبل الموافقة.\n" +
                "This command looks able to delete data, change the system, or restart it. Make sure you really intend it before agreeing."
        } else null
    }
}
