package com.magicsmart.core

/** حالة إعداد نظام كما قُرئت فعلياً من الجهاز. */
enum class SettingState(val arabic: String) {
    ENABLED("مفعّل / Enabled"),
    DISABLED("غير مفعّل / Disabled"),
    UNSUPPORTED("غير مدعوم في هذا الإصدار / Unsupported on this version"),
    UNREADABLE("لا يمكن قراءته / Unreadable")
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
    val wirelessDebugging: SettingReading,
    val hardware: String = "",
    val board: String = "",
    val product: String = "",
    val buildDisplay: String = "",
    val buildFingerprint: String = ""
)
