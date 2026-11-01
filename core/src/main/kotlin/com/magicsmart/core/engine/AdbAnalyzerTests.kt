package com.magicsmart.core.engine

import android.os.Build
import com.magicsmart.core.ResultStatus
import com.magicsmart.core.SettingState
import java.net.InetSocketAddress
import java.net.Socket

object AdbAnalyzerTests {
    private const val DEV_ACTION = "android.settings.APPLICATION_DEVELOPMENT_SETTINGS"

    val all: List<TestDefinition> = listOf(
        TestDefinition(
            id = "ADB-001", name = "تصنيف حالة ADB",
            description = "التمييز بين: غير متاح، غير مفعّل، مفعّل. الاتصال بمضيف نشط لا يمكن الحكم عليه من داخل التطبيق.",
            engine = Engine.ADB, method = "قراءة Settings.Global + قيود المستخدم",
            expected = "تصنيف واضح للحالة",
            launch = LaunchSpec(action = DEV_ACTION)
        ) { tc ->
            val s = tc.snapshot
            when {
                Env.debugRestricted(tc.context) -> TestOutcome(
                    ResultStatus.BLOCKED, "ADB غير متاح: سياسة الجهاز تمنع ميزات التصحيح",
                    "DISALLOW_DEBUGGING_FEATURES", "يزيل القيد مسؤول الجهاز فقط"
                )
                s.developerOptions.state == SettingState.DISABLED -> TestOutcome(
                    ResultStatus.USER_ACTION_REQUIRED, "ADB غير مفعّل: خيارات المطوّر مغلقة", null,
                    "فعّل خيارات المطوّر أولاً (DEV-010)"
                )
                s.usbDebugging.state == SettingState.ENABLED || s.wirelessDebugging.state == SettingState.ENABLED ->
                    TestOutcome(
                        ResultStatus.SUCCESS,
                        "ADB مفعّل (USB: ${s.usbDebugging.state.arabic}، لاسلكي: ${s.wirelessDebugging.state.arabic}). " +
                            "حالة الاتصال بمضيف نشط غير معروفة."
                    )
                s.usbDebugging.state == SettingState.DISABLED -> TestOutcome(
                    ResultStatus.USER_ACTION_REQUIRED, "ADB غير مفعّل (القناتان مغلقتان)", null,
                    "فعّل تصحيح USB أو اللاسلكي من صفحة المطوّر"
                )
                else -> TestOutcome(ResultStatus.UNKNOWN, "تعذّر قراءة حالة ADB", s.usbDebugging.note)
            }
        },
        TestDefinition(
            id = "ADB-002", name = "حالة خدمة adbd",
            description = "قراءة init.svc.adbd عبر getprop (قد يحجبها النظام عن التطبيقات)",
            engine = Engine.ADB, method = "getprop init.svc.adbd",
            expected = "قيمة running أو stopped"
        ) { tc ->
            when (val v = Env.prop("init.svc.adbd")) {
                "running" -> TestOutcome(ResultStatus.SUCCESS, "خدمة adbd تعمل")
                "stopped" -> TestOutcome(ResultStatus.SUCCESS, "خدمة adbd متوقفة", null, "تعمل عند تفعيل التصحيح")
                null -> TestOutcome(
                    ResultStatus.UNKNOWN, "لم يعرض النظام الخاصية للتطبيق",
                    "قيود SELinux على قراءة الخصائص"
                )
                else -> TestOutcome(ResultStatus.UNKNOWN, "قيمة غير متوقعة: $v")
            }
        },
        TestDefinition(
            id = "ADB-003", name = "منافذ ADB المُعلنة في الخصائص",
            description = "قراءة service.adb.tcp.port و service.adb.tls.port و persist.adb.tcp.port",
            engine = Engine.ADB, method = "getprop",
            expected = "منفذ واحد على الأقل إن كان ADB الشبكي مفعّلاً"
        ) { tc ->
            val keys = listOf("service.adb.tcp.port", "service.adb.tls.port", "persist.adb.tcp.port")
            val vals = keys.mapNotNull { k -> Env.prop(k)?.let { "$k=$it" } }
            if (vals.isNotEmpty()) TestOutcome(ResultStatus.SUCCESS, vals.joinToString("، "))
            else TestOutcome(
                ResultStatus.UNKNOWN, "لا منافذ معروضة",
                "إما أن ADB الشبكي مغلق أو أن النظام يحجب الخصائص عن التطبيقات"
            )
        },
        TestDefinition(
            id = "ADB-004", name = "هل يستجيب منفذ ADB اللاسلكي؟",
            description = "محاولة اتصال TCP بالمنفذ المكتشف عبر mDNS. استجابة المنفذ ليست اتصال ADB مصرَّحاً.",
            engine = Engine.ADB, method = "mDNS + Socket.connect (مهلة 1.5ث)",
            expected = "المنفذ يقبل اتصال TCP"
        ) { tc ->
            if (tc.snapshot.wirelessDebugging.state != SettingState.ENABLED) TestOutcome(
                ResultStatus.USER_ACTION_REQUIRED, "التصحيح اللاسلكي غير مفعّل أو غير مقروء", null, "فعّله ثم أعد الفحص"
            ) else {
                val f = NsdProbe.connect(tc).found.firstOrNull { it.host != null && it.port > 0 }
                if (f == null) TestOutcome(ResultStatus.UNKNOWN, "لم يُكتشف منفذ لاختباره", "راجع WIFI-005")
                else try {
                    Socket().use { it.connect(InetSocketAddress(f.host, f.port), 1500) }
                    TestOutcome(
                        ResultStatus.SUCCESS,
                        "المنفذ ${f.port} يقبل اتصال TCP. الاتصال الفعلي يتطلب اقتراناً ومصادقة TLS وموافقتك."
                    )
                } catch (e: Exception) {
                    TestOutcome(ResultStatus.FAILED, "لم يستجب المنفذ ${f.port}", "${e.javaClass.simpleName}: ${e.message}")
                }
            }
        },
        TestDefinition(
            id = "ADB-005", name = "منفذ ADB الشبكي الكلاسيكي (adb tcpip)",
            description = "إن أعلنت الخصائص منفذاً (مثل 5555) فاختبر استجابته على الحلقة المحلية 127.0.0.1؛ لا يخرج الاتصال من الجهاز.",
            engine = Engine.ADB, method = "getprop + Socket.connect(127.0.0.1)",
            expected = "المنفذ يستجيب عند تفعيل التصحيح"
        ) { tc ->
            val port = listOf("service.adb.tcp.port", "persist.adb.tcp.port")
                .firstNotNullOfOrNull { k -> Env.prop(k)?.toIntOrNull()?.takeIf { it in 1..65535 } }
            if (port == null) TestOutcome(ResultStatus.UNKNOWN, "لا منفذ شبكي معلن في الخصائص")
            else try {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 1500) }
                TestOutcome(ResultStatus.SUCCESS, "المنفذ $port يستجيب محلياً (adbd يستمع). هذا لا يعني أن الاتصال مصرَّح.")
            } catch (e: Exception) {
                if (tc.snapshot.usbDebugging.state != SettingState.ENABLED) TestOutcome(
                    ResultStatus.USER_ACTION_REQUIRED, "المنفذ $port معلن لكنه مغلق الآن (تصحيح USB غير مفعّل)",
                    e.javaClass.simpleName, "فعّل خيارات المطوّر ثم تصحيح USB وأعد الفحص؛ ربما يُفتح المنفذ بعدها (لم يُتحقق)"
                ) else TestOutcome(
                    ResultStatus.FAILED, "المنفذ $port لا يستجيب رغم تفعيل التصحيح", "${e.javaClass.simpleName}: ${e.message}"
                )
            }
        },
        TestDefinition(
            id = "ADB-006", name = "مستوى تصحيح البناء",
            description = "نوع البناء (user/userdebug/eng) وخصائص الأمان المعلنة. معلومة وصفية.",
            engine = Engine.ADB, method = "Build.TYPE + getprop", expected = "قراءة المعلومات"
        ) { _ ->
            val props = listOf("ro.debuggable", "ro.secure", "ro.adb.secure").mapNotNull { k -> Env.prop(k)?.let { "$k=$it" } }
            TestOutcome(
                ResultStatus.SUCCESS,
                "Build.TYPE=${Build.TYPE}، Build.TAGS=${Build.TAGS}" + if (props.isNotEmpty()) "، " + props.joinToString("، ") else "",
                null,
                if (Build.TYPE != "user") "بناء غير تجاري (${Build.TYPE}): قد يكون التصحيح أقل تقييداً من الأجهزة التجارية؛ تأكد بتفعيله فعلياً." else null
            )
        }
    )
}
