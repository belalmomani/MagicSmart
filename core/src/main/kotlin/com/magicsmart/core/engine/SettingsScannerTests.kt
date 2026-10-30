package com.magicsmart.core.engine

import android.os.Build
import com.magicsmart.core.ResultStatus

object SettingsScannerTests {
    private val visibility = TestDefinition(
        id = "SET-000", name = "رؤية حزمة الإعدادات (Package Visibility)",
        description = "هل يستطيع التطبيق رؤية حزمة إعدادات النظام؟",
        engine = Engine.SETTINGS_SCANNER, method = "PackageManager.getPackageInfo",
        expected = "رؤية com.android.settings أو com.android.tv.settings"
    ) { tc ->
        val seen = listOf("com.android.settings", "com.android.tv.settings").filter { Probes.hasPackage(tc.context, it) }
        if (seen.isNotEmpty()) TestOutcome(ResultStatus.SUCCESS, "مرئي: ${seen.joinToString()} (API ${Build.VERSION.SDK_INT})")
        else TestOutcome(
            ResultStatus.BLOCKED, "لا تظهر أي حزمة إعدادات معروفة",
            "قيود Package Visibility أو أن المصنّع يستخدم حزمة بديلة",
            "ستُضاف أسماء الحزم البديلة في ملفات OEM (المرحلة 4)"
        )
    }

    private val actionTests = SETTINGS_ACTIONS.map { a ->
        TestDefinition(
            id = a.id, name = "صفحة ${a.title}",
            description = "هل يوجد نشاط يعالج ${a.action}؟",
            engine = Engine.SETTINGS_SCANNER, method = "PackageManager.resolveActivity",
            expected = "وجود نشاط يعالج المسار",
            launch = LaunchSpec(action = a.action)
        ) { tc -> Probes.probeAction(tc, a.action) }
    }

    val all: List<TestDefinition> = listOf(visibility) + actionTests
}
