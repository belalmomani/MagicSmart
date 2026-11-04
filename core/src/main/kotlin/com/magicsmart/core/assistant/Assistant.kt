package com.magicsmart.core.assistant

import android.content.Context
import android.os.Build
import android.os.UserManager
import com.magicsmart.core.DeviceInspector
import com.magicsmart.core.ResultStatus
import com.magicsmart.core.SettingState
import com.magicsmart.core.engine.Env
import com.magicsmart.core.engine.LaunchSpec
import com.magicsmart.core.engine.Probes
import com.magicsmart.core.engine.TestContext
import kotlinx.coroutines.flow.MutableStateFlow

/** ما أبلغ به المستخدم بعد محاولته الضغط على رقم البنية. */
enum class Feedback { NONE, MESSAGE_SHOWN, NO_BUILD_NUMBER, NOTHING_HAPPENED, SEARCHED_EVERYWHERE }

enum class StepState(val mark: String, val argb: Long) {
    DONE("✔", 0xFF4CAF50), CURRENT("◐", 0xFF42A5F5), PENDING("○", 0xFF9E9E9E), BLOCKED("✖", 0xFFEF5350)
}

class AssistantStep(
    val title: String, val detail: String, val state: StepState,
    val launch: LaunchSpec? = null, val launchLabel: String? = null
)

class Diagnosis(
    val headline: String, val tone: ResultStatus,
    val facts: List<String>, val steps: List<AssistantStep>, val showFeedback: Boolean
)

object AssistantState {
    val feedback = MutableStateFlow(Feedback.NONE)
}

/**
 * مساعد تفعيل خيارات المطوّر: يشخّص حالة الجهاز فعلياً ويقترح خطوات يدوية رسمية ويتحقق من النتيجة.
 * لا يغيّر أي إعداد بنفسه؛ التفعيل يتم من المستخدم داخل إعدادات النظام.
 */
object Assistant {
    private const val DEVICE_INFO = "android.settings.DEVICE_INFO_SETTINGS"
    private const val DEV_ACTION = "android.settings.APPLICATION_DEVELOPMENT_SETTINGS"

    fun diagnose(ctx: Context, fb: Feedback): Diagnosis {
        val snap = DeviceInspector.inspect(ctx)
        val tc = TestContext(ctx, snap)
        val um = ctx.getSystemService(Context.USER_SERVICE) as UserManager
        val restricted = Env.debugRestricted(ctx)
        val primary = um.isSystemUser
        val devOn = snap.developerOptions.state == SettingState.ENABLED
        val usbOn = snap.usbDebugging.state == SettingState.ENABLED
        val infoOk = Probes.probeAction(tc, DEVICE_INFO).status == ResultStatus.SUCCESS
        val devProbe = Probes.probeAction(tc, DEV_ACTION)
        val devPageReal = devProbe.status == ResultStatus.SUCCESS && !devProbe.actual.contains("Disabled")
        val adbd = Env.prop("init.svc.adbd")
        val port = listOf("service.adb.tcp.port", "persist.adb.tcp.port")
            .firstNotNullOfOrNull { k -> Env.prop(k)?.toIntOrNull()?.takeIf { it in 1..65535 } }

        val facts = listOf(
            "الجهاز: ${snap.manufacturer} ${snap.model} — Android ${snap.androidRelease} (API ${snap.sdkInt})",
            "نوع البناء: ${Build.TYPE}",
            "المستخدم الرئيسي: ${if (primary) "نعم" else "لا"}",
            "خيارات المطوّر: ${snap.developerOptions.state.arabic}",
            "تصحيح USB: ${snap.usbDebugging.state.arabic}",
            "خدمة adbd: ${adbd ?: "غير معروفة"}",
            "اللوحة/العتاد (للبحث عن فرم وير بديل): ${snap.board.ifEmpty { snap.hardware }} / ${snap.hardware}"
        )

        if (restricted) {
            return Diagnosis(
                "سياسة الجهاز تمنع ميزات التصحيح / Device policy blocks debugging", ResultStatus.BLOCKED, facts,
                listOf(
                    AssistantStep(
                        "قيد من مسؤول الجهاز / Admin restriction",
                        "قيد DISALLOW_DEBUGGING_FEATURES مفعّل. لا يستطيع أي تطبيق إزالته؛ يزيله مسؤول الجهاز أو المؤسسة فقط.\n" +
                            "The DISALLOW_DEBUGGING_FEATURES restriction is active. No app can remove it; only the device admin/organization can.",
                        StepState.BLOCKED
                    )
                ),
                false
            )
        }

        val steps = mutableListOf<AssistantStep>()
        if (!primary) steps += AssistantStep(
            "بدّل إلى المستخدم الرئيسي / Switch to the primary user",
            "أنت تعمل بمستخدم ثانوي أو مقيّد، وخيارات المطوّر تُفعَّل عادةً من المستخدم الرئيسي فقط.\n" +
                "You're on a secondary/restricted user profile. Developer options are usually enabled only from the primary (owner) user.",
            StepState.BLOCKED
        )

        val s1 = when { devOn -> StepState.DONE; infoOk -> StepState.CURRENT; else -> StepState.BLOCKED }
        steps += AssistantStep(
            "افتح «معلومات الجهاز» / Open \"About device\"",
            if (infoOk) "يفتح التطبيق صفحة معلومات الجهاز الأصلية من النظام. / Opens the system's native About page."
            else "الصفحة غير متاحة لهذا التطبيق على جهازك؛ افتح الإعدادات يدوياً وابحث عن «حول الجهاز / About».\n" +
                "Not reachable from this app on your device; open Settings manually and look for \"About / About device\".",
            s1,
            if (infoOk && !devOn) LaunchSpec(action = DEVICE_INFO) else null, "فتح معلومات الجهاز / Open"
        )

        val (s2, d2) = when {
            devOn -> StepState.DONE to "خيارات المطوّر مفعّلة."
            fb == Feedback.NO_BUILD_NUMBER -> StepState.BLOCKED to
                "لم تجد العنصر: ابحث عن «حول الجهاز / About» أو «معلومات الإصدار / Version» داخل كل صفحات الإعدادات التي تظهر عندك، فبعض الواجهات تضعه خلف صفحة فرعية مثل «معلومات البرنامج» أو «حالة الجهاز». إن بحثت في كل مكان ولم تجده، اختر الزر التالي."
            fb == Feedback.SEARCHED_EVERYWHERE -> StepState.BLOCKED to
                "هذا تعطيل متعمَّد من الشركة المصنّعة لهذا البناء، وليس خللاً عابراً ولا نقصاً في المحاولة. لا يوجد طريق رسمي من داخل أي تطبيق لتجاوزه. " +
                "الخيارات الواقعية المتبقية: (1) فرم وير بديل للوحة ذاتها إن وُجد في مجتمعات الصناديق الصينية — ابحث باسم اللوحة لا باسم العلامة التجارية (راجع بصمة النظام أدناه)، وهذا يحمل خطر تعطيل الجهاز إن لم يطابق تماماً. " +
                "(2) مراسلة المتجر أو الموزّع وطلب نسخة تدعم وضع المطوّر. (3) تقبّل أن هذا الجهاز بالذات قد يبقى مغلقاً؛ هذا احتمال حقيقي مع هذه الفئة من الأجهزة."
            fb == Feedback.NOTHING_HAPPENED -> StepState.CURRENT to
                "اضغط 7 مرات متتالية بسرعة دون توقف (تظهر رسالة عدّاد في آخر ضغطات). إن طلب النظام رمز القفل فأدخله. " +
                "إن لم يتغير شيء فقد يكون المصنّع عطّل الميزة في هذا البناء."
            fb == Feedback.MESSAGE_SHOWN -> StepState.CURRENT to
                "ظهرت الرسالة لكن النظام ما زال يقرأ الخيارات مغلقة. اضغط «تحقّق الآن»؛ وإن بقيت مغلقة فربما تحجب الواجهة التغيير."
            s1 == StepState.CURRENT -> StepState.CURRENT to
                "في صفحة معلومات الجهاز اضغط على «رقم البنية / Build number» سبع مرات متتالية بسرعة (زر OK على التلفاز). قد يطلب رمز القفل."
            else -> StepState.PENDING to "بعد فتح معلومات الجهاز."
        }
        steps += AssistantStep("اضغط «Build number» سبع مرات / Tap \"Build number\" 7 times", d2, s2)

        val s3 = when { usbOn -> StepState.DONE; devOn -> StepState.CURRENT; else -> StepState.PENDING }
        val d3 = when {
            usbOn -> "تصحيح USB مفعّل."
            devOn && devPageReal -> "افتح خيارات المطوّر وفعّل «تصحيح USB» بنفسك."
            devOn -> "الخيارات مفعّلة لكن المسار ما زال يقود لصفحة «غير مفعّلة»؛ اضغط «تحقّق الآن» أو أعد تشغيل التطبيق."
            else -> "بعد تفعيل خيارات المطوّر."
        }
        steps += AssistantStep(
            "فعّل «تصحيح USB» / Enable \"USB debugging\"", d3, s3,
            if (devOn && !usbOn) LaunchSpec(action = DEV_ACTION) else null, "فتح خيارات المطوّر / Open"
        )

        if (usbOn && port != null) {
            val ip = Env.localIpv4() ?: "عنوان-الجهاز"
            steps += AssistantStep(
                "اختياري: الاتصال عبر ADB من حاسوب / Optional: connect via ADB from a computer",
                "على حاسوب في الشبكة نفسها جرّب: adb connect $ip:$port — المنفذ مُعلن ولم يُتحقق من فتحه فعلياً.\n" +
                    "On a computer on the same network try: adb connect $ip:$port — the port is advertised but opening it is unverified. You'll see an RSA key-confirmation prompt on the TV.",
                StepState.CURRENT
            )
        }

        val (headline, tone) = when {
            usbOn -> "جاهز: تصحيح USB مفعّل / Ready: USB debugging is on" to ResultStatus.SUCCESS
            devOn -> "خيارات المطوّر مفعّلة — بقي تصحيح USB / Developer options on — USB debugging left" to ResultStatus.USER_ACTION_REQUIRED
            fb == Feedback.NO_BUILD_NUMBER -> "العنصر غير ظاهر: قد يكون مخفياً من المصنّع / Item not found: may be hidden by the manufacturer" to ResultStatus.BLOCKED
            fb == Feedback.SEARCHED_EVERYWHERE -> "تعطيل متعمَّد من المصنّع / Deliberately disabled by the manufacturer" to ResultStatus.BLOCKED
            else -> "خيارات المطوّر غير مفعّلة — اتبع الخطوات / Developer options are off — follow the steps" to ResultStatus.USER_ACTION_REQUIRED
        }
        return Diagnosis(headline, tone, facts, steps, showFeedback = !devOn)
    }
}
