package com.magicsmart.core.engine

import com.magicsmart.core.ResultStatus
import com.magicsmart.core.SettingState

object UsbDebuggingTests {
    private const val DEV_ACTION = "android.settings.APPLICATION_DEVELOPMENT_SETTINGS"

    val all: List<TestDefinition> = listOf(
        TestDefinition(
            id = "USB-001", name = "قراءة حالة تصحيح USB / Read USB debugging state",
            description = "قراءة adb_enabled من Settings.Global (وتحديد ما إذا كان الإعداد موجوداً) / " +
                "Read adb_enabled from Settings.Global (and whether the setting exists at all)",
            engine = Engine.USB_DEBUGGING, method = "Settings.Global.getInt",
            expected = "قراءة القيمة دون صلاحيات / Reading the value needs no permission"
        ) { tc ->
            val r = tc.snapshot.usbDebugging
            when (r.state) {
                SettingState.ENABLED, SettingState.DISABLED -> TestOutcome(ResultStatus.SUCCESS, "الحالة / State: ${r.state.arabic}")
                SettingState.UNSUPPORTED -> TestOutcome(ResultStatus.UNSUPPORTED, r.state.arabic, r.note)
                SettingState.UNREADABLE -> TestOutcome(ResultStatus.UNKNOWN, r.state.arabic, r.note)
            }
        },
        TestDefinition(
            id = "USB-002", name = "شرط تفعيل خيارات المطوّر / Developer-options prerequisite",
            description = "تصحيح USB يظهر داخل صفحة المطوّر؛ هل الشرط متحقق؟ / USB debugging lives inside the developer page; is that prerequisite met?",
            engine = Engine.USB_DEBUGGING, method = "قراءة development_settings_enabled / Read development_settings_enabled",
            expected = "خيارات المطوّر مفعّلة / Developer options are enabled",
            launch = LaunchSpec(action = "android.settings.DEVICE_INFO_SETTINGS")
        ) { tc ->
            when (tc.snapshot.developerOptions.state) {
                SettingState.ENABLED -> TestOutcome(ResultStatus.SUCCESS, "خيارات المطوّر مفعّلة / Developer options are enabled")
                SettingState.DISABLED -> TestOutcome(
                    ResultStatus.USER_ACTION_REQUIRED, "خيارات المطوّر غير مفعّلة / Developer options are off", null,
                    "فعّلها بالضغط 7 مرات على رقم البنية في معلومات الجهاز / Enable it by tapping Build number 7 times in About device"
                )
                else -> TestOutcome(ResultStatus.UNKNOWN, "تعذّر قراءة حالة خيارات المطوّر / Could not read developer-options state", tc.snapshot.developerOptions.note)
            }
        },
        TestDefinition(
            id = "USB-003", name = "قيود تمنع تصحيح USB / Restrictions blocking USB debugging",
            description = "فحص قيد سياسة no_debugging_features / Check the no_debugging_features policy restriction",
            engine = Engine.USB_DEBUGGING, method = "UserManager.hasUserRestriction",
            expected = "عدم وجود قيد / No restriction present"
        ) { tc ->
            if (Env.debugRestricted(tc.context)) TestOutcome(
                ResultStatus.BLOCKED, "القيد مفعّل / Restriction is active",
                "سياسة الجهاز تمنع ميزات التصحيح / Device policy blocks debugging features",
                "يزيله مسؤول الجهاز فقط / Only the device admin can remove it"
            ) else TestOutcome(ResultStatus.SUCCESS, "لا يوجد قيد / No restriction")
        },
        TestDefinition(
            id = "USB-004", name = "مسار تفعيل تصحيح USB والتحقق منه / USB-debugging activation path & verification",
            description = "لا يوجد مسار مباشر لتبديل الخيار؛ المسار هو صفحة المطوّر. النجاح لا يُعلن إلا إذا قرأنا الإعداد مفعّلاً. / " +
                "There's no direct toggle path; the path is the developer page. Success is only declared once the setting reads as enabled.",
            engine = Engine.USB_DEBUGGING, method = "resolveActivity + قراءة adb_enabled / resolveActivity + reading adb_enabled",
            expected = "الإعداد مفعّل بعد تدخل المستخدم / The setting is enabled after the user's action",
            launch = LaunchSpec(action = DEV_ACTION)
        ) { tc ->
            when (tc.snapshot.usbDebugging.state) {
                SettingState.ENABLED -> TestOutcome(ResultStatus.SUCCESS, "تصحيح USB مفعّل (تم التحقق بالقراءة) / USB debugging is on (verified by reading it)")
                SettingState.DISABLED -> {
                    val p = Probes.probeAction(tc, DEV_ACTION)
                    if (p.status == ResultStatus.SUCCESS) TestOutcome(
                        ResultStatus.USER_ACTION_REQUIRED, "الإعداد غير مفعّل وصفحة المطوّر متاحة / Setting is off and the developer page is available", null,
                        "افتح صفحة المطوّر وفعّل «تصحيح USB» بنفسك، ثم ارجع للتطبيق ليُعاد التحقق تلقائياً / " +
                            "Open the developer page and enable \"USB debugging\" yourself, then return to the app for automatic re-verification"
                    ) else TestOutcome(ResultStatus.UNSUPPORTED, "الإعداد غير مفعّل وصفحة المطوّر غير متاحة / Setting is off and the developer page is unavailable", p.failureReason, p.nextAction)
                }
                else -> TestOutcome(ResultStatus.UNKNOWN, "تعذّر قراءة الإعداد / Could not read the setting", tc.snapshot.usbDebugging.note)
            }
        }
    )
}
