package com.magicsmart.core.engine

import android.os.Build
import com.magicsmart.core.ResultStatus

object SettingsScannerTests {
    private val visibility = TestDefinition(
        id = "SET-000", name = "رؤية حزمة الإعدادات / Settings package visibility",
        description = "هل يستطيع التطبيق رؤية حزمة إعدادات النظام؟ / Can the app see the system's settings package?",
        engine = Engine.SETTINGS_SCANNER, method = "PackageManager.getPackageInfo",
        expected = "رؤية com.android.settings أو com.android.tv.settings / Seeing com.android.settings or com.android.tv.settings"
    ) { tc ->
        val seen = listOf("com.android.settings", "com.android.tv.settings").filter { Probes.hasPackage(tc.context, it) }
        if (seen.isNotEmpty()) TestOutcome(ResultStatus.SUCCESS, "مرئي / Visible: ${seen.joinToString()} (API ${Build.VERSION.SDK_INT})")
        else TestOutcome(
            ResultStatus.BLOCKED, "لا تظهر أي حزمة إعدادات معروفة / No known settings package is visible",
            "قيود Package Visibility أو أن المصنّع يستخدم حزمة بديلة / Package Visibility restrictions, or the vendor uses a different package",
            "ستُضاف أسماء الحزم البديلة في ملفات OEM (المرحلة 4) / Alternate package names will be added via OEM files (phase 4)"
        )
    }

    private val actionTests = SETTINGS_ACTIONS.map { a ->
        TestDefinition(
            id = a.id, name = "صفحة ${a.title}",
            description = "هل يوجد نشاط يعالج ${a.action}؟ / Is there an activity that handles ${a.action}?",
            engine = Engine.SETTINGS_SCANNER, method = "PackageManager.resolveActivity",
            expected = "وجود نشاط يعالج المسار / An activity handling the path exists",
            launch = LaunchSpec(action = a.action)
        ) { tc -> Probes.probeAction(tc, a.action) }
    }

    val all: List<TestDefinition> = listOf(visibility) + actionTests
}
