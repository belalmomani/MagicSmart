package com.magicsmart.core.engine

import android.content.ComponentName
import android.content.Context
import com.magicsmart.core.DeviceSnapshot
import com.magicsmart.core.ResultStatus
import com.magicsmart.core.oem.OemProfiles

/** اختبارات محلل الشركات: عامة + مُولَّدة من ملفات assets/oem لكل ملف مطابق للجهاز. */
object OemTests {
    fun build(ctx: Context): List<TestDefinition> {
        val load = OemProfiles.get(ctx)
        val list = mutableListOf<TestDefinition>()

        list += TestDefinition(
            id = "OEM-000", name = "تحميل ملفات تعريف الشركات",
            description = "قراءة ملفات assets/oem والتحقق من سلامتها",
            engine = Engine.OEM, method = "قراءة JSON", expected = "تحميل كل الملفات بلا أخطاء"
        ) { _ ->
            if (load.errors.isEmpty()) TestOutcome(ResultStatus.SUCCESS, "حُمّل ${load.profiles.size} ملف تعريف")
            else TestOutcome(ResultStatus.FAILED, "حُمّل ${load.profiles.size} ملفاً مع أخطاء", load.errors.joinToString(" | "), "صحّح ملفات JSON المذكورة")
        }
        list += TestDefinition(
            id = "OEM-001", name = "اكتشاف الشركة وملف التعريف",
            description = "مطابقة الشركة والعلامة والموديل والعتاد مع ملفات التعريف قبل اختيار الاختبارات",
            engine = Engine.OEM, method = "مقارنة Build.* مع ملفات oem", expected = "تحديد ملف مطابق أو الملف العام"
        ) { tc ->
            val sel = OemProfiles.select(load, tc.snapshot)
            if (sel.isEmpty()) TestOutcome(ResultStatus.UNKNOWN, "لا ملفات تعريف محمّلة")
            else TestOutcome(ResultStatus.SUCCESS, sel.joinToString("، ") { "${it.first.name} (${it.second})" })
        }
        list += TestDefinition(
            id = "OEM-002", name = "بصمة بناء النظام",
            description = "الشركة والعلامة والموديل والمنتج والعتاد واللوحة ورقم البناء (معلومات جهاز لا شخصية)",
            engine = Engine.OEM, method = "android.os.Build", expected = "قراءة المعلومات"
        ) { tc ->
            val s = tc.snapshot
            TestOutcome(
                ResultStatus.SUCCESS,
                "الشركة=${s.manufacturer} | العلامة=${s.brand} | الموديل=${s.model} | المنتج=${s.product} | " +
                    "العتاد=${s.hardware} | اللوحة=${s.board} | البناء=${s.buildDisplay}"
            )
        }
        list += TestDefinition(
            id = "OEM-003", name = "حزم تحدد نوع واجهة النظام",
            description = "رصد حزم معروفة (Launcher وGoogle TV وإعدادات التلفاز وغيرها) لتمييز نسخة النظام",
            engine = Engine.OEM, method = "PackageManager.getPackageInfo", expected = "قائمة بالحزم الموجودة"
        ) { tc ->
            val found = load.fingerprintPackages.filter { Probes.hasPackage(tc.context, it) }
            TestOutcome(
                ResultStatus.SUCCESS,
                "موجودة (${found.size}/${load.fingerprintPackages.size}): " + found.joinToString("، ").ifEmpty { "لا شيء" }
            )
        }

        for (p in load.profiles) {
            val cond = { s: DeviceSnapshot -> OemProfiles.select(load, s).any { it.first.id == p.id } }
            if (p.packages.isNotEmpty()) list += TestDefinition(
                id = "OEM-${p.id.uppercase()}-PKG", name = "حزم الملف: ${p.name}",
                description = "رصد حزم النظام المعرّفة في ملف ${p.name}. ${p.notes}",
                engine = Engine.OEM, method = "PackageManager.getPackageInfo", expected = "رصد الحزم المعروفة",
                condition = cond
            ) { tc ->
                val found = p.packages.filter { Probes.hasPackage(tc.context, it) }
                val missing = p.packages - found.toSet()
                TestOutcome(
                    ResultStatus.SUCCESS,
                    "موجودة: ${found.joinToString("، ").ifEmpty { "لا شيء" }} — غير موجودة: ${missing.joinToString("، ").ifEmpty { "لا شيء" }}"
                )
            }
            for (c in p.components) list += TestDefinition(
                id = c.id, name = c.title, description = "مكوّن من ملف ${p.name}: ${c.pkg}/${c.cls}",
                engine = Engine.OEM, method = "PackageManager.getActivityInfo", expected = "مكوّن موجود ومصدَّر",
                target = c.target, launch = LaunchSpec(component = ComponentName(c.pkg, c.cls)), condition = cond
            ) { tc -> Probes.probeComponent(tc, ComponentName(c.pkg, c.cls)) }
            for (a in p.actions) list += TestDefinition(
                id = a.id, name = a.title, description = "مسار من ملف ${p.name}: ${a.action}",
                engine = Engine.OEM, method = "PackageManager.resolveActivity", expected = "وجود نشاط يعالج المسار",
                launch = LaunchSpec(action = a.action), condition = cond
            ) { tc -> Probes.probeAction(tc, a.action) }
        }
        return list
    }
}
