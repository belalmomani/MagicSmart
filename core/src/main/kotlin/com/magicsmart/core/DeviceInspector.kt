package com.magicsmart.core

import android.app.UiModeManager
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings

/** يقرأ معلومات الجهاز وحالة الإعدادات القابلة للقراءة دون أي صلاحية خاصة. */
object DeviceInspector {

    fun inspect(context: Context): DeviceSnapshot {
        val pm = context.packageManager
        val ui = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        val isTv = ui.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val resolver = context.contentResolver

        val wireless = if (Build.VERSION.SDK_INT >= 30) {
            readGlobal(resolver, "adb_wifi_enabled")
        } else {
            SettingReading(SettingState.UNSUPPORTED, "يتطلب Android 11 أو أحدث")
        }

        return DeviceSnapshot(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            model = Build.MODEL.orEmpty(),
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            isTv = isTv,
            developerOptions = readGlobal(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
            usbDebugging = readGlobal(resolver, Settings.Global.ADB_ENABLED),
            wirelessDebugging = wireless
        )
    }

    private fun readGlobal(resolver: ContentResolver, key: String): SettingReading =
        try {
            val v = Settings.Global.getInt(resolver, key)
            SettingReading(if (v == 1) SettingState.ENABLED else SettingState.DISABLED)
        } catch (e: Settings.SettingNotFoundException) {
            // نفس منطق تطبيق الإعدادات: الغياب يُقرأ بقيمة افتراضية 0 (معطّل) في إصدارات user
            SettingReading(SettingState.DISABLED, "القيمة غير مخزَّنة؛ النظام يعاملها كمعطّلة (الافتراضي 0)")
        } catch (e: SecurityException) {
            SettingReading(SettingState.UNREADABLE, "النظام منع القراءة")
        }
}
