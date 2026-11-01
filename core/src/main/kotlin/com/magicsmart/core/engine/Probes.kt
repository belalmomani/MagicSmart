package com.magicsmart.core.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.magicsmart.core.ResultStatus

/** فحوص قراءة فقط: لا تفتح أي صفحة ولا تغيّر أي إعداد. */
internal object Probes {

    @Suppress("DEPRECATION")
    fun probeAction(tc: TestContext, action: String): TestOutcome {
        val ai = tc.context.packageManager
            .resolveActivity(Intent(action), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        return if (ai != null) {
            TestOutcome(
                ResultStatus.SUCCESS,
                "يوجد نشاط يعالج المسار: ${ai.packageName}/${ai.name}. " +
                    "وجود المسار لا يعني تفعيل أي إعداد."
            )
        } else {
            TestOutcome(
                ResultStatus.UNSUPPORTED,
                "لا يوجد نشاط يعالج هذا المسار",
                "سيُنتج الفتح ActivityNotFoundException",
                "جرّب مساراً بديلاً أو ملف تعريف الشركة المصنّعة (لاحقاً)"
            )
        }
    }

    @Suppress("DEPRECATION")
    fun probeComponent(tc: TestContext, cn: ComponentName): TestOutcome = try {
        val pm = tc.context.packageManager
        // MATCH_DISABLED_COMPONENTS: الأنشطة المعطّلة حالياً تُعتبر "موجودة لكن معطّلة" لا "غير موجودة"
        val ai = pm.getActivityInfo(cn, PackageManager.MATCH_DISABLED_COMPONENTS)
        val enabled = when (pm.getComponentEnabledSetting(cn)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> ai.enabled
            else -> false
        }
        val perm: String? = ai.permission
        when {
            !enabled -> TestOutcome(
                ResultStatus.BLOCKED, "المكوّن موجود لكنه معطّل حالياً",
                "معطّل في النظام (يُفعَّل عادةً عند تفعيل خيارات المطوّر)", "فعّل خيارات المطوّر ثم أعد الفحص"
            )
            !ai.exported -> TestOutcome(
                ResultStatus.BLOCKED, "المكوّن موجود لكنه غير مصدَّر",
                "لا يمكن لتطبيق خارجي تشغيله دون صلاحيات نظام"
            )
            perm != null && tc.context.checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED ->
                TestOutcome(ResultStatus.PERMISSION_REQUIRED, "المكوّن يتطلب الصلاحية $perm", perm)
            else -> TestOutcome(
                ResultStatus.SUCCESS,
                "المكوّن موجود ومصدَّر. وجوده لا يعني تفعيل أي إعداد."
            )
        }
    } catch (e: PackageManager.NameNotFoundException) {
        TestOutcome(
            ResultStatus.UNSUPPORTED, "المكوّن غير موجود على هذا الجهاز",
            "NameNotFoundException: ${cn.flattenToShortString()}"
        )
    } catch (e: SecurityException) {
        TestOutcome(ResultStatus.BLOCKED, "النظام منع الاستعلام عن المكوّن", "SecurityException")
    }

    @Suppress("DEPRECATION")
    fun hasPackage(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0); true
    } catch (e: PackageManager.NameNotFoundException) { false }
}
