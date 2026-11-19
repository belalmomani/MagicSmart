package com.magicsmart.core.engine

// ملف مُولَّد بواسطة tools/sync_queries.py — لا تعدّله يدوياً.
internal class SettingsAction(val id: String, val action: String, val title: String)

internal val SETTINGS_ACTIONS = listOf(
    SettingsAction("SET-001", "android.settings.SETTINGS", "الإعدادات العامة / General settings"),
    SettingsAction("SET-002", "android.settings.DEVICE_INFO_SETTINGS", "معلومات الجهاز / Device info"),
    SettingsAction("SET-003", "android.settings.WIFI_SETTINGS", "الواي فاي / Wi-Fi"),
    SettingsAction("SET-004", "android.settings.WIRELESS_SETTINGS", "الشبكات اللاسلكية / Wireless networks"),
    SettingsAction("SET-005", "android.settings.BLUETOOTH_SETTINGS", "البلوتوث / Bluetooth"),
    SettingsAction("SET-006", "android.settings.APPLICATION_SETTINGS", "التطبيقات / Apps"),
    SettingsAction("SET-007", "android.settings.MANAGE_ALL_APPLICATIONS_SETTINGS", "كل التطبيقات / All apps"),
    SettingsAction("SET-008", "android.settings.SECURITY_SETTINGS", "الأمان / Security"),
    SettingsAction("SET-009", "android.settings.PRIVACY_SETTINGS", "الخصوصية / Privacy"),
    SettingsAction("SET-010", "android.settings.ACCESSIBILITY_SETTINGS", "سهولة الاستخدام / Accessibility"),
    SettingsAction("SET-011", "android.settings.DISPLAY_SETTINGS", "العرض / Display"),
    SettingsAction("SET-012", "android.settings.SOUND_SETTINGS", "الصوت / Sound"),
    SettingsAction("SET-013", "android.settings.DATE_SETTINGS", "التاريخ والوقت / Date & time"),
    SettingsAction("SET-014", "android.settings.LOCALE_SETTINGS", "اللغة / Language"),
    SettingsAction("SET-015", "android.settings.INPUT_METHOD_SETTINGS", "لوحات الإدخال / Keyboards"),
    SettingsAction("SET-016", "android.settings.INTERNAL_STORAGE_SETTINGS", "التخزين / Storage"),
    SettingsAction("SET-017", "android.settings.CAST_SETTINGS", "البث / Cast"),
    SettingsAction("SET-018", "android.settings.BATTERY_SAVER_SETTINGS", "توفير الطاقة / Battery saver"),
    SettingsAction("SET-019", "android.settings.HOME_SETTINGS", "التطبيق الرئيسي الافتراضي / Default home app"),
    SettingsAction("SET-020", "android.settings.NOTIFICATION_LISTENER_SETTINGS", "مستمعو الإشعارات / Notification listeners"),
    SettingsAction("SET-021", "android.settings.VOICE_INPUT_SETTINGS", "الإدخال الصوتي / Voice input"),
    SettingsAction("SET-022", "android.settings.CAPTIONING_SETTINGS", "الترجمة النصية / Captions"),
    SettingsAction("SET-023", "android.settings.NFC_SETTINGS", "NFC"),
    SettingsAction("SET-024", "android.settings.DATA_ROAMING_SETTINGS", "التجوال / Roaming"),
    SettingsAction("SET-025", "android.settings.MEMORY_CARD_SETTINGS", "بطاقة الذاكرة / Memory card"),
    SettingsAction("SET-026", "android.settings.SYNC_SETTINGS", "المزامنة / Sync"),
    SettingsAction("SET-027", "android.settings.ADD_ACCOUNT_SETTINGS", "إضافة حساب / Add account"),
    SettingsAction("SET-028", "android.settings.NETWORK_OPERATOR_SETTINGS", "مشغّل الشبكة / Network operator"),
)
