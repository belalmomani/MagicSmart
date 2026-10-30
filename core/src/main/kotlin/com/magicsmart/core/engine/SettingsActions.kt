package com.magicsmart.core.engine

// ملف مُولَّد من قائمة المسارات نفسها المصرَّح بها في <queries> داخل AndroidManifest.
internal class SettingsAction(val id: String, val action: String, val title: String)

internal val SETTINGS_ACTIONS = listOf(
    SettingsAction("SET-001", "android.settings.SETTINGS", "الإعدادات العامة"),
    SettingsAction("SET-002", "android.settings.DEVICE_INFO_SETTINGS", "معلومات الجهاز"),
    SettingsAction("SET-003", "android.settings.WIFI_SETTINGS", "الواي فاي"),
    SettingsAction("SET-004", "android.settings.WIRELESS_SETTINGS", "الشبكات اللاسلكية"),
    SettingsAction("SET-005", "android.settings.BLUETOOTH_SETTINGS", "البلوتوث"),
    SettingsAction("SET-006", "android.settings.APPLICATION_SETTINGS", "التطبيقات"),
    SettingsAction("SET-007", "android.settings.MANAGE_ALL_APPLICATIONS_SETTINGS", "كل التطبيقات"),
    SettingsAction("SET-008", "android.settings.SECURITY_SETTINGS", "الأمان"),
    SettingsAction("SET-009", "android.settings.PRIVACY_SETTINGS", "الخصوصية"),
    SettingsAction("SET-010", "android.settings.ACCESSIBILITY_SETTINGS", "سهولة الاستخدام"),
    SettingsAction("SET-011", "android.settings.DISPLAY_SETTINGS", "العرض"),
    SettingsAction("SET-012", "android.settings.SOUND_SETTINGS", "الصوت"),
    SettingsAction("SET-013", "android.settings.DATE_SETTINGS", "التاريخ والوقت"),
    SettingsAction("SET-014", "android.settings.LOCALE_SETTINGS", "اللغة"),
    SettingsAction("SET-015", "android.settings.INPUT_METHOD_SETTINGS", "لوحات الإدخال"),
    SettingsAction("SET-016", "android.settings.INTERNAL_STORAGE_SETTINGS", "التخزين"),
    SettingsAction("SET-017", "android.settings.CAST_SETTINGS", "البث"),
    SettingsAction("SET-018", "android.settings.BATTERY_SAVER_SETTINGS", "توفير الطاقة"),
)
