package com.magicsmart.core

/** تصنيفات نتائج الاختبارات المعتمدة في المشروع. */
enum class ResultStatus(val arabic: String, val argb: Long) {
    SUCCESS("نجاح / Success", 0xFF4CAF50),
    FAILED("فشل / Failed", 0xFFEF5350),
    UNSUPPORTED("غير مدعوم / Unsupported", 0xFF9E9E9E),
    BLOCKED("محجوب / Blocked", 0xFFFF7043),
    PERMISSION_REQUIRED("يحتاج صلاحية / Permission needed", 0xFFFFCA28),
    USER_ACTION_REQUIRED("يحتاج تدخلك / Needs your action", 0xFF42A5F5),
    UNKNOWN("غير معروف / Unknown", 0xFFB0BEC5)
}
