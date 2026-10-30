package com.magicsmart.core

/** تصنيفات نتائج الاختبارات المعتمدة في المشروع. */
enum class ResultStatus(val arabic: String, val argb: Long) {
    SUCCESS("نجاح", 0xFF4CAF50),
    FAILED("فشل", 0xFFEF5350),
    UNSUPPORTED("غير مدعوم", 0xFF9E9E9E),
    BLOCKED("محجوب", 0xFFFF7043),
    PERMISSION_REQUIRED("يحتاج صلاحية", 0xFFFFCA28),
    USER_ACTION_REQUIRED("يحتاج تدخلك", 0xFF42A5F5),
    UNKNOWN("غير معروف", 0xFFB0BEC5)
}
