package com.magicsmart.core.engine

// ملف مُولَّد بواسطة tools/sync_queries.py — لا تعدّله يدوياً.
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
    SettingsAction("SET-019", "android.settings.HOME_SETTINGS", "التطبيق الرئيسي الافتراضي"),
    SettingsAction("SET-020", "android.settings.NOTIFICATION_LISTENER_SETTINGS", "مستمعو الإشعارات"),
    SettingsAction("SET-021", "android.settings.VOICE_INPUT_SETTINGS", "الإدخال الصوتي"),
    SettingsAction("SET-022", "android.settings.CAPTIONING_SETTINGS", "الترجمة النصية"),
    SettingsAction("SET-023", "android.settings.NFC_SETTINGS", "NFC"),
    SettingsAction("SET-024", "android.settings.DATA_ROAMING_SETTINGS", "التجوال"),
    SettingsAction("SET-025", "android.settings.MEMORY_CARD_SETTINGS", "بطاقة الذاكرة"),
    SettingsAction("SET-026", "android.settings.SYNC_SETTINGS", "المزامنة"),
    SettingsAction("SET-027", "android.settings.ADD_ACCOUNT_SETTINGS", "إضافة حساب"),
    SettingsAction("SET-028", "android.settings.NETWORK_OPERATOR_SETTINGS", "مشغّل الشبكة"),
)
