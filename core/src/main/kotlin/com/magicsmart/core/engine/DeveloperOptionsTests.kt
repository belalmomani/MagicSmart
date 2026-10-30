package com.magicsmart.core.engine

import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import android.provider.Settings
import com.magicsmart.core.ResultStatus
import com.magicsmart.core.SettingState

object DeveloperOptionsTests {
    private const val DEV_ACTION = "android.settings.APPLICATION_DEVELOPMENT_SETTINGS"
    private const val DEVICE_INFO = "android.settings.DEVICE_INFO_SETTINGS"

    private class Candidate(val id: String, val pkg: String, val cls: String, val target: DeviceTarget)

    private val candidates = listOf(
        Candidate("DEV-C01", "com.android.settings", "com.android.settings.DevelopmentSettings", DeviceTarget.ANY),
        Candidate("DEV-C02", "com.android.settings", "com.android.settings.development.DevelopmentSettingsActivity", DeviceTarget.ANY),
        Candidate("DEV-C03", "com.android.settings", "com.android.settings.Settings\$DevelopmentSettingsDashboardActivity", DeviceTarget.ANY),
        Candidate("DEV-C04", "com.android.tv.settings", "com.android.tv.settings.system.development.DevelopmentActivity", DeviceTarget.TV)
    )

    private val base = listOf(
        TestDefinition(
            id = "DEV-001", name = "قراءة حالة Developer Options",
            description = "قراءة development_settings_enabled من Settings.Global",
            engine = Engine.DEVELOPER_OPTIONS, method = "Settings.Global.getInt",
            expected = "قراءة القيمة دون صلاحيات"
        ) { tc ->
            val r = tc.snapshot.developerOptions
            when (r.state) {
                SettingState.ENABLED, SettingState.DISABLED ->
                    TestOutcome(ResultStatus.SUCCESS, "الحالة: ${r.state.arabic}")
                else -> TestOutcome(ResultStatus.UNKNOWN, r.state.arabic, r.note)
            }
        },
        TestDefinition(
            id = "DEV-002", name = "مسار APPLICATION_DEVELOPMENT_SETTINGS",
            description = "هل يوجد نشاط يعالج المسار الرسمي لصفحة المطوّر؟",
            engine = Engine.DEVELOPER_OPTIONS, method = "PackageManager.resolveActivity",
            expected = "وجود نشاط يعالج المسار",
            launch = LaunchSpec(action = DEV_ACTION)
        ) { tc -> Probes.probeAction(tc, DEV_ACTION) },
        TestDefinition(
            id = "DEV-010", name = "مسار تفعيل Developer Options عبر رقم البنية",
            description = "التفعيل الرسمي: الضغط 7 مرات على رقم البنية في معلومات الجهاز",
            engine = Engine.DEVELOPER_OPTIONS, method = "قراءة الحالة + resolveActivity لصفحة معلومات الجهاز",
            expected = "الخيارات مفعّلة، أو صفحة معلومات الجهاز متاحة لتفعيلها يدوياً",
            launch = LaunchSpec(action = DEVICE_INFO)
        ) { tc ->
            if (tc.snapshot.developerOptions.state == SettingState.ENABLED) {
                TestOutcome(ResultStatus.SUCCESS, "Developer Options مفعّلة مسبقاً")
            } else {
                val p = Probes.probeAction(tc, DEVICE_INFO)
                if (p.status == ResultStatus.SUCCESS) TestOutcome(
                    ResultStatus.USER_ACTION_REQUIRED, "الخيارات غير مفعّلة وصفحة معلومات الجهاز متاحة",
                    null, "افتح معلومات الجهاز واضغط على رقم البنية (Build number) 7 مرات"
                ) else TestOutcome(ResultStatus.UNSUPPORTED, "صفحة معلومات الجهاز غير متاحة", p.failureReason, p.nextAction)
            }
        },
        TestDefinition(
            id = "DEV-012", name = "قيود سياسة تعطيل ميزات التصحيح",
            description = "هل يمنع النظام أو سياسة الجهاز ميزات التصحيح (no_debugging_features)؟",
            engine = Engine.DEVELOPER_OPTIONS, method = "UserManager.hasUserRestriction",
            expected = "عدم وجود قيد"
        ) { tc ->
            val um = tc.context.getSystemService(Context.USER_SERVICE) as UserManager
            if (um.hasUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES)) TestOutcome(
                ResultStatus.BLOCKED, "قيد DISALLOW_DEBUGGING_FEATURES مفعّل",
                "سياسة الجهاز تمنع ميزات التصحيح", "يزيل القيد مسؤول الجهاز فقط"
            ) else TestOutcome(ResultStatus.SUCCESS, "لا يوجد قيد على ميزات التصحيح")
        }
    )

    private val componentTests = candidates.map { c ->
        val cn = ComponentName(c.pkg, c.cls)
        TestDefinition(
            id = c.id, name = "مكوّن ${c.cls.substringAfterLast('.')}",
            description = "فحص وجود المكوّن ${c.cls} وإمكانية تشغيله",
            engine = Engine.DEVELOPER_OPTIONS, method = "PackageManager.getActivityInfo",
            expected = "مكوّن موجود ومصدَّر", target = c.target,
            launch = LaunchSpec(component = cn)
        ) { tc -> Probes.probeComponent(tc, cn) }
    }

    val all: List<TestDefinition> = base + componentTests
}
