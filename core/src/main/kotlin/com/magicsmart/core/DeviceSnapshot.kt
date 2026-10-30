package com.magicsmart.core

/** حالة إعداد نظام كما قُرئت فعلياً من الجهاز. */
enum class SettingState(val arabic: String) {
    ENABLED("مفعّل"),
    DISABLED("غير مفعّل"),
    UNSUPPORTED("غير مدعوم في هذا الإصدار"),
    UNREADABLE("لا يمكن قراءته")
}

data class SettingReading(val state: SettingState, val note: String? = null)

data class DeviceSnapshot(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
    val isTv: Boolean,
    val developerOptions: SettingReading,
    val usbDebugging: SettingReading,
    val wirelessDebugging: SettingReading
)
