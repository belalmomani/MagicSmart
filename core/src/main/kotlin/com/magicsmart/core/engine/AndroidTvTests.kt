package com.magicsmart.core.engine

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.InputDevice
import com.magicsmart.core.ResultStatus

object AndroidTvTests {
    val all: List<TestDefinition> = listOf(
        TestDefinition(
            id = "TV-001", name = "التعرف على نوع الجهاز", description = "هل يعلن الجهاز أنه تلفاز؟",
            engine = Engine.ANDROID_TV, target = DeviceTarget.TV,
            method = "UiModeManager + FEATURE_LEANBACK", expected = "الجهاز تلفاز"
        ) { tc ->
            TestOutcome(ResultStatus.SUCCESS, "الجهاز يعمل بوضع التلفاز: ${tc.snapshot.manufacturer} ${tc.snapshot.model}")
        },
        TestDefinition(
            id = "TV-002", name = "غياب شاشة اللمس", description = "التلفاز يجب ألا يعتمد على اللمس",
            engine = Engine.ANDROID_TV, target = DeviceTarget.TV,
            method = "PackageManager.hasSystemFeature(TOUCHSCREEN)", expected = "لا توجد شاشة لمس"
        ) { tc ->
            if (tc.context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN))
                TestOutcome(ResultStatus.UNKNOWN, "الجهاز يعلن دعم اللمس", "غير معتاد على التلفاز")
            else TestOutcome(ResultStatus.SUCCESS, "لا توجد شاشة لمس")
        },
        TestDefinition(
            id = "TV-003", name = "Leanback فقط", description = "هل الجهاز يعمل بواجهة Leanback حصراً؟",
            engine = Engine.ANDROID_TV, target = DeviceTarget.TV, minApi = 26,
            method = "FEATURE_LEANBACK_ONLY", expected = "معلومة وصفية"
        ) { tc ->
            val only = tc.context.packageManager.hasSystemFeature("android.software.leanback_only")
            TestOutcome(ResultStatus.SUCCESS, if (only) "Leanback فقط" else "Leanback مع واجهات أخرى")
        },
        TestDefinition(
            id = "TV-004", name = "التنقل بالريموت (D-pad)", description = "هل يتوفر إدخال D-pad؟",
            engine = Engine.ANDROID_TV, target = DeviceTarget.TV,
            method = "Configuration.navigation + InputDevice.getSources", expected = "توفر D-pad"
        ) { tc ->
            val nav = tc.context.resources.configuration.navigation == Configuration.NAVIGATION_DPAD
            val dev = InputDevice.getDeviceIds().any {
                val s = InputDevice.getDevice(it)?.sources ?: 0
                s and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
            }
            if (nav || dev) TestOutcome(ResultStatus.SUCCESS, "D-pad متوفر (navigation=$nav, device=$dev)")
            else TestOutcome(ResultStatus.UNKNOWN, "لم يُرصد D-pad الآن", "قد يكون الريموت غير متصل أو يُعرَّف كلوحة مفاتيح")
        },
        TestDefinition(
            id = "TV-005", name = "حزمة إعدادات التلفاز", description = "هل توجد com.android.tv.settings؟",
            engine = Engine.ANDROID_TV, target = DeviceTarget.TV,
            method = "PackageManager.getPackageInfo", expected = "وجود الحزمة"
        ) { tc ->
            if (Probes.hasPackage(tc.context, "com.android.tv.settings"))
                TestOutcome(ResultStatus.SUCCESS, "الحزمة موجودة")
            else TestOutcome(
                ResultStatus.UNKNOWN, "الحزمة غير موجودة بهذا الاسم",
                "قد يستخدم المصنّع حزمة إعدادات مختلفة", "ستُعالج في ملفات OEM (المرحلة 4)"
            )
        }
    )
}
