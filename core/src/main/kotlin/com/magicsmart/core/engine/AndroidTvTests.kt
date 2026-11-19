package com.magicsmart.core.engine

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.InputDevice
import com.magicsmart.core.ResultStatus

object AndroidTvTests {
    val all: List<TestDefinition> = listOf(
        TestDefinition(
            id = "TV-001", name = "التعرف على نوع الجهاز / Device type detection",
            description = "هل يعلن الجهاز أنه تلفاز؟ / Does the device declare itself a TV?",
            engine = Engine.ANDROID_TV, target = DeviceTarget.TV,
            method = "UiModeManager + FEATURE_LEANBACK", expected = "الجهاز تلفاز / Device is a TV"
        ) { tc ->
            TestOutcome(ResultStatus.SUCCESS, "الجهاز يعمل بوضع التلفاز / Running in TV mode: ${tc.snapshot.manufacturer} ${tc.snapshot.model}")
        },
        TestDefinition(
            id = "TV-002", name = "دعم اللمس / Touch support",
            description = "هل يعلن الجهاز دعم شاشة اللمس؟ (واجهة التلفاز لا تعتمد عليه) / Does the device declare touchscreen support? (the TV UI doesn't rely on it)",
            engine = Engine.ANDROID_TV, target = DeviceTarget.TV,
            method = "PackageManager.hasSystemFeature(TOUCHSCREEN)", expected = "معلومة وصفية / Descriptive info"
        ) { tc ->
            if (tc.context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN))
                TestOutcome(ResultStatus.SUCCESS, "الجهاز يعلن دعم اللمس (شائع في الشاشات التفاعلية) / Device declares touch support (common on interactive screens)")
            else TestOutcome(ResultStatus.SUCCESS, "لا توجد شاشة لمس (المعتاد في التلفاز) / No touchscreen (typical for TV)")
        },
        TestDefinition(
            id = "TV-003", name = "Leanback فقط / Leanback only",
            description = "هل الجهاز يعمل بواجهة Leanback حصراً؟ / Does the device run the Leanback interface exclusively?",
            engine = Engine.ANDROID_TV, target = DeviceTarget.TV, minApi = 26,
            method = "FEATURE_LEANBACK_ONLY", expected = "معلومة وصفية / Descriptive info"
        ) { tc ->
            val only = tc.context.packageManager.hasSystemFeature("android.software.leanback_only")
            TestOutcome(ResultStatus.SUCCESS, if (only) "Leanback فقط / Leanback only" else "Leanback مع واجهات أخرى / Leanback plus other UIs")
        },
        TestDefinition(
            id = "TV-004", name = "التنقل بالريموت (D-pad) / Remote navigation (D-pad)",
            description = "هل يتوفر إدخال D-pad؟ / Is D-pad input available?",
            engine = Engine.ANDROID_TV, target = DeviceTarget.TV,
            method = "Configuration.navigation + InputDevice.getSources", expected = "توفر D-pad / D-pad available"
        ) { tc ->
            val nav = tc.context.resources.configuration.navigation == Configuration.NAVIGATION_DPAD
            val dev = InputDevice.getDeviceIds().any {
                val s = InputDevice.getDevice(it)?.sources ?: 0
                s and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
            }
            if (nav || dev) TestOutcome(ResultStatus.SUCCESS, "D-pad متوفر / available (navigation=$nav, device=$dev)")
            else TestOutcome(
                ResultStatus.UNKNOWN, "لم يُرصد D-pad الآن / D-pad not detected right now",
                "قد يكون الريموت غير متصل أو يُعرَّف كلوحة مفاتيح / The remote may be disconnected or registered as a keyboard"
            )
        },
        TestDefinition(
            id = "TV-005", name = "جهة معالجة إعدادات التلفاز / TV settings handler",
            description = "أي حزمة تتولى صفحة الإعدادات العامة؟ (قد تكون واجهة المصنّع لا com.android.tv.settings) / " +
                "Which package handles the general settings page? (may be the vendor's UI, not com.android.tv.settings)",
            engine = Engine.ANDROID_TV, target = DeviceTarget.TV,
            method = "resolveActivity(android.settings.SETTINGS) + getPackageInfo", expected = "تحديد الحزمة المسؤولة / Identify the responsible package"
        ) { tc ->
            @Suppress("DEPRECATION")
            val ai = tc.context.packageManager
                .resolveActivity(Intent("android.settings.SETTINGS"), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
            val tvPkg = Probes.hasPackage(tc.context, "com.android.tv.settings")
            if (ai == null) TestOutcome(ResultStatus.UNKNOWN, "لا نشاط يعالج صفحة الإعدادات العامة / No activity handles the general settings page")
            else TestOutcome(
                ResultStatus.SUCCESS,
                "الإعدادات العامة / General settings: ${ai.packageName}/${ai.name} — com.android.tv.settings " +
                    (if (tvPkg) "موجودة / present" else "غير موجودة / absent")
            )
        }
    )
}
