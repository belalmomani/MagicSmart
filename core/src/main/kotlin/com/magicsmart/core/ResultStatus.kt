package com.magicsmart.core

/** تصنيفات نتائج الاختبارات المعتمدة في المشروع. */
enum class ResultStatus {
    SUCCESS, FAILED, UNSUPPORTED, BLOCKED,
    PERMISSION_REQUIRED, USER_ACTION_REQUIRED, UNKNOWN
}
