package com.magicsmart.core.engine

import android.content.ComponentName
import android.content.Context
import com.magicsmart.core.DeviceSnapshot
import com.magicsmart.core.ResultStatus
import com.magicsmart.core.oem.OemProfiles

/** اختبارات محلل الشركات: عامة + مُولَّدة من ملفات assets/oem لكل ملف مطابق للجهاز. / OEM analyzer tests: generic + generated from assets/oem for each matching profile. */
object OemTests {
    fun build(ctx: Context): List<TestDefinition> {
        val load = OemProfiles.get(ctx)
        val list = mutableListOf<TestDefinition>()

        list += TestDefinition(
            id = "OEM-000", name = "تحميل ملفات تعريف الشركات / Load OEM profile files",
            description = "قراءة ملفات assets/oem والتحقق من سلامتها / Read the assets/oem files and verify they're well-formed",
            engine = Engine.OEM, method = "قراءة JSON / Reading JSON", expected = "تحميل كل الملفات بلا أخطاء / All files load without errors"
        ) { _ ->
            if (load.errors.isEmpty()) TestOutcome(ResultStatus.SUCCESS, "حُمّل ${load.profiles.size} ملف تعريف / Loaded ${load.profiles.size} profile file(s)")
            else TestOutcome(ResultStatus.FAILED, "حُمّل ${load.profiles.size} ملفاً مع أخطاء / Loaded ${load.profiles.size} file(s) with errors", load.errors.joinToString(" | "), "صحّح ملفات JSON المذكورة / Fix the JSON files named above")
        }
        list += TestDefinition(
            id = "OEM-001", name = "اكتشاف الشركة وملف التعريف / Manufacturer & profile detection",
            description = "مطابقة الشركة والعلامة والموديل والعتاد مع ملفات التعريف قبل اختيار الاختبارات / " +
                "Match manufacturer, brand, model, and hardware against profile files before choosing tests",
            engine = Engine.OEM, method = "مقارنة Build.* مع ملفات oem / Comparing Build.* against oem files", expected = "تحديد ملف مطابق أو الملف العام / A matching profile, or the generic one, is identified"
        ) { tc ->
            val sel = OemProfiles.select(load, tc.snapshot)
            if (sel.isEmpty()) TestOutcome(ResultStatus.UNKNOWN, "لا ملفات تعريف محمّلة / No profile files loaded")
            else TestOutcome(ResultStatus.SUCCESS, sel.joinToString("، ") { "${it.first.name} (${it.second})" })
        }
        list += TestDefinition(
            id = "OEM-002", name = "بصمة بناء النظام / System build fingerprint",
            description = "الشركة والعلامة والموديل والمنتج والعتاد واللوحة ورقم البناء (معلومات جهاز لا شخصية) / " +
                "Manufacturer, brand, model, product, hardware, board, and build number (device info, not personal data)",
            engine = Engine.OEM, method = "android.os.Build", expected = "قراءة المعلومات / Reading the information"
        ) { tc ->
            val s = tc.snapshot
            TestOutcome(
                ResultStatus.SUCCESS,
                "الشركة/Manufacturer=${s.manufacturer} | العلامة/Brand=${s.brand} | الموديل/Model=${s.model} | المنتج/Product=${s.product} | " +
                    "العتاد/Hardware=${s.hardware} | اللوحة/Board=${s.board} | البناء/Build=${s.buildDisplay}"
            )
        }
        list += TestDefinition(
            id = "OEM-003", name = "حزم تحدد نوع واجهة النظام / Packages that fingerprint the system UI",
            description = "رصد حزم معروفة (Launcher وGoogle TV وإعدادات التلفاز وغيرها) لتمييز نسخة النظام / " +
                "Detect known packages (Launcher, Google TV, TV settings, etc.) to identify the system flavor",
            engine = Engine.OEM, method = "PackageManager.getPackageInfo", expected = "قائمة بالحزم الموجودة / A list of the packages present"
        ) { tc ->
            val found = load.fingerprintPackages.filter { Probes.hasPackage(tc.context, it) }
            TestOutcome(
                ResultStatus.SUCCESS,
                "موجودة / Present (${found.size}/${load.fingerprintPackages.size}): " + found.joinToString("، ").ifEmpty { "لا شيء / none" }
            )
        }

        for (p in load.profiles) {
            val cond = { s: DeviceSnapshot -> OemProfiles.select(load, s).any { it.first.id == p.id } }
            if (p.packages.isNotEmpty()) list += TestDefinition(
                id = "OEM-${p.id.uppercase()}-PKG", name = "حزم الملف / Profile packages: ${p.name}",
                description = "رصد حزم النظام المعرّفة في ملف ${p.name}. ${p.notes} / Detect the system packages defined in the ${p.name} profile. ${p.notes}",
                engine = Engine.OEM, method = "PackageManager.getPackageInfo", expected = "رصد الحزم المعروفة / Detecting the known packages",
                condition = cond
            ) { tc ->
                val found = p.packages.filter { Probes.hasPackage(tc.context, it) }
                val missing = p.packages - found.toSet()
                TestOutcome(
                    ResultStatus.SUCCESS,
                    "موجودة / Present: ${found.joinToString("، ").ifEmpty { "لا شيء / none" }} — " +
                        "غير موجودة / Absent: ${missing.joinToString("، ").ifEmpty { "لا شيء / none" }}"
                )
            }
            for (c in p.components) list += TestDefinition(
                id = c.id, name = c.title, description = "مكوّن من ملف ${p.name}: ${c.pkg}/${c.cls} / A component from the ${p.name} profile: ${c.pkg}/${c.cls}",
                engine = Engine.OEM, method = "PackageManager.getActivityInfo", expected = "مكوّن موجود ومصدَّر / Component exists and is exported",
                target = c.target, launch = LaunchSpec(component = ComponentName(c.pkg, c.cls)), condition = cond
            ) { tc -> Probes.probeComponent(tc, ComponentName(c.pkg, c.cls)) }
            for (a in p.actions) list += TestDefinition(
                id = a.id, name = a.title, description = "مسار من ملف ${p.name}: ${a.action} / A path from the ${p.name} profile: ${a.action}",
                engine = Engine.OEM, method = "PackageManager.resolveActivity", expected = "وجود نشاط يعالج المسار / An activity handling the path exists",
                launch = LaunchSpec(action = a.action), condition = cond
            ) { tc -> Probes.probeAction(tc, a.action) }
        }
        return list
    }
}
