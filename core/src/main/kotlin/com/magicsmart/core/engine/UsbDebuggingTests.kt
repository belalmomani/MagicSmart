package com.magicsmart.core.engine

import com.magicsmart.core.ResultStatus
import com.magicsmart.core.SettingState

object UsbDebuggingTests {
    private const val DEV_ACTION = "android.settings.APPLICATION_DEVELOPMENT_SETTINGS"

    val all: List<TestDefinition> = listOf(
        TestDefinition(
            id = "USB-001", name = "قراءة حالة تصحيح USB",
            description = "قراءة adb_enabled من Settings.Global (وتحديد ما إذا كان الإعداد موجوداً)",
            engine = Engine.USB_DEBUGGING, method = "Settings.Global.getInt",
            expected = "قراءة القيمة دون صلاحيات"
        ) { tc ->
            val r = tc.snapshot.usbDebugging
            when (r.state) {
                SettingState.ENABLED, SettingState.DISABLED -> TestOutcome(ResultStatus.SUCCESS, "الحالة: ${r.state.arabic}")
                SettingState.UNSUPPORTED -> TestOutcome(ResultStatus.UNSUPPORTED, r.state.arabic, r.note)
                SettingState.UNREADABLE -> TestOutcome(ResultStatus.UNKNOWN, r.state.arabic, r.note)
            }
        },
        TestDefinition(
            id = "USB-002", name = "شرط تفعيل خيارات المطوّر",
            description = "تصحيح USB يظهر داخل صفحة المطوّر؛ هل الشرط متحقق؟",
            engine = Engine.USB_DEBUGGING, method = "قراءة development_settings_enabled",
            expected = "خيارات المطوّر مفعّلة",
            launch = LaunchSpec(action = "android.settings.DEVICE_INFO_SETTINGS")
        ) { tc ->
            when (tc.snapshot.developerOptions.state) {
                SettingState.ENABLED -> TestOutcome(ResultStatus.SUCCESS, "خيارات المطوّر مفعّلة")
                SettingState.DISABLED -> TestOutcome(
                    ResultStatus.USER_ACTION_REQUIRED, "خيارات المطوّر غير مفعّلة", null,
                    "فعّلها بالضغط 7 مرات على رقم البنية في معلومات الجهاز"
                )
                else -> TestOutcome(ResultStatus.UNKNOWN, "تعذّر قراءة حالة خيارات المطوّر", tc.snapshot.developerOptions.note)
            }
        },
        TestDefinition(
            id = "USB-003", name = "قيود تمنع تصحيح USB",
            description = "فحص قيد سياسة no_debugging_features",
            engine = Engine.USB_DEBUGGING, method = "UserManager.hasUserRestriction",
            expected = "عدم وجود قيد"
        ) { tc ->
            if (Env.debugRestricted(tc.context)) TestOutcome(
                ResultStatus.BLOCKED, "القيد مفعّل", "سياسة الجهاز تمنع ميزات التصحيح", "يزيله مسؤول الجهاز فقط"
            ) else TestOutcome(ResultStatus.SUCCESS, "لا يوجد قيد")
        },
        TestDefinition(
            id = "USB-004", name = "مسار تفعيل تصحيح USB والتحقق منه",
            description = "لا يوجد مسار مباشر لتبديل الخيار؛ المسار هو صفحة المطوّر. النجاح لا يُعلن إلا إذا قرأنا الإعداد مفعّلاً.",
            engine = Engine.USB_DEBUGGING, method = "resolveActivity + قراءة adb_enabled",
            expected = "الإعداد مفعّل بعد تدخل المستخدم",
            launch = LaunchSpec(action = DEV_ACTION)
        ) { tc ->
            when (tc.snapshot.usbDebugging.state) {
                SettingState.ENABLED -> TestOutcome(ResultStatus.SUCCESS, "تصحيح USB مفعّل (تم التحقق بالقراءة)")
                SettingState.DISABLED -> {
                    val p = Probes.probeAction(tc, DEV_ACTION)
                    if (p.status == ResultStatus.SUCCESS) TestOutcome(
                        ResultStatus.USER_ACTION_REQUIRED, "الإعداد غير مفعّل وصفحة المطوّر متاحة", null,
                        "افتح صفحة المطوّر وفعّل «تصحيح USB» بنفسك، ثم ارجع للتطبيق ليُعاد التحقق تلقائياً"
                    ) else TestOutcome(ResultStatus.UNSUPPORTED, "الإعداد غير مفعّل وصفحة المطوّر غير متاحة", p.failureReason, p.nextAction)
                }
                else -> TestOutcome(ResultStatus.UNKNOWN, "تعذّر قراءة الإعداد", tc.snapshot.usbDebugging.note)
            }
        }
    )
}
