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
            id = "ADB-001", name = "تصنيف حالة ADB / ADB state classification",
            description = "التمييز بين: غير متاح، غير مفعّل، مفعّل. الاتصال بمضيف نشط لا يمكن الحكم عليه من داخل التطبيق. / " +
                "Distinguishing: unavailable, disabled, enabled. Whether an active host is actually connected can't be judged from inside the app.",
            engine = Engine.ADB, method = "قراءة Settings.Global + قيود المستخدم / Reading Settings.Global + user restrictions",
            expected = "تصنيف واضح للحالة / A clear state classification",
            launch = LaunchSpec(action = DEV_ACTION)
        ) { tc ->
            val s = tc.snapshot
            when {
                Env.debugRestricted(tc.context) -> TestOutcome(
                    ResultStatus.BLOCKED, "ADB غير متاح: سياسة الجهاز تمنع ميزات التصحيح / ADB unavailable: device policy blocks debugging features",
                    "DISALLOW_DEBUGGING_FEATURES", "يزيل القيد مسؤول الجهاز فقط / Only the device admin can remove it"
                )
                s.developerOptions.state == SettingState.DISABLED -> TestOutcome(
                    ResultStatus.USER_ACTION_REQUIRED, "ADB غير مفعّل: خيارات المطوّر مغلقة / ADB disabled: developer options are off", null,
                    "فعّل خيارات المطوّر أولاً (DEV-010) / Enable developer options first (DEV-010)"
                )
                s.usbDebugging.state == SettingState.ENABLED || s.wirelessDebugging.state == SettingState.ENABLED ->
                    TestOutcome(
                        ResultStatus.SUCCESS,
                        "ADB مفعّل (USB: ${s.usbDebugging.state.arabic}، لاسلكي: ${s.wirelessDebugging.state.arabic}). " +
                            "حالة الاتصال بمضيف نشط غير معروفة. / ADB is enabled (USB: ${s.usbDebugging.state.arabic}, wireless: ${s.wirelessDebugging.state.arabic}). " +
                            "Whether an active host is connected is unknown."
                    )
                s.usbDebugging.state == SettingState.DISABLED -> TestOutcome(
                    ResultStatus.USER_ACTION_REQUIRED, "ADB غير مفعّل (القناتان مغلقتان) / ADB disabled (both channels off)", null,
                    "فعّل تصحيح USB أو اللاسلكي من صفحة المطوّر / Enable USB or wireless debugging from the developer page"
                )
                else -> TestOutcome(ResultStatus.UNKNOWN, "تعذّر قراءة حالة ADB / Could not read ADB state", s.usbDebugging.note)
            }
        },
        TestDefinition(
            id = "ADB-002", name = "حالة خدمة adbd / adbd service state",
            description = "قراءة init.svc.adbd عبر getprop (قد يحجبها النظام عن التطبيقات) / Read init.svc.adbd via getprop (the system may hide it from apps)",
            engine = Engine.ADB, method = "getprop init.svc.adbd",
            expected = "قيمة running أو stopped / A value of running or stopped"
        ) { tc ->
            when (val v = Env.prop("init.svc.adbd")) {
                "running" -> TestOutcome(ResultStatus.SUCCESS, "خدمة adbd تعمل / adbd service is running")
                "stopped" -> TestOutcome(ResultStatus.SUCCESS, "خدمة adbd متوقفة / adbd service is stopped", null, "تعمل عند تفعيل التصحيح / Starts once debugging is enabled")
                null -> TestOutcome(
                    ResultStatus.UNKNOWN, "لم يعرض النظام الخاصية للتطبيق / The system didn't expose the property to the app",
                    "قيود SELinux على قراءة الخصائص / SELinux restrictions on reading properties"
                )
                else -> TestOutcome(ResultStatus.UNKNOWN, "قيمة غير متوقعة / Unexpected value: $v")
            }
        },
        TestDefinition(
            id = "ADB-003", name = "منافذ ADB المُعلنة في الخصائص / ADB ports advertised in properties",
            description = "قراءة service.adb.tcp.port و service.adb.tls.port و persist.adb.tcp.port / Read service.adb.tcp.port, service.adb.tls.port, and persist.adb.tcp.port",
            engine = Engine.ADB, method = "getprop",
            expected = "منفذ واحد على الأقل إن كان ADB الشبكي مفعّلاً / At least one port if network ADB is enabled"
        ) { tc ->
            val keys = listOf("service.adb.tcp.port", "service.adb.tls.port", "persist.adb.tcp.port")
            val vals = keys.mapNotNull { k -> Env.prop(k)?.let { "$k=$it" } }
            if (vals.isNotEmpty()) TestOutcome(ResultStatus.SUCCESS, vals.joinToString("، "))
            else TestOutcome(
                ResultStatus.UNKNOWN, "لا منافذ معروضة / No ports exposed",
                "إما أن ADB الشبكي مغلق أو أن النظام يحجب الخصائص عن التطبيقات / Either network ADB is off, or the system hides properties from apps"
            )
        },
        TestDefinition(
            id = "ADB-004", name = "هل يستجيب منفذ ADB اللاسلكي؟ / Does the wireless ADB port respond?",
            description = "محاولة اتصال TCP بالمنفذ المكتشف عبر mDNS. استجابة المنفذ ليست اتصال ADB مصرَّحاً. / " +
                "Attempt a TCP connection to the port found via mDNS. A responding port is not the same as an authorized ADB connection.",
            engine = Engine.ADB, method = "mDNS + Socket.connect (مهلة 1.5ث / 1.5s timeout)",
            expected = "المنفذ يقبل اتصال TCP / The port accepts a TCP connection"
        ) { tc ->
            if (tc.snapshot.wirelessDebugging.state != SettingState.ENABLED) TestOutcome(
                ResultStatus.USER_ACTION_REQUIRED, "التصحيح اللاسلكي غير مفعّل أو غير مقروء / Wireless debugging is off or unreadable", null,
                "فعّله ثم أعد الفحص / Enable it, then re-scan"
            ) else {
                val f = NsdProbe.connect(tc).found.firstOrNull { it.host != null && it.port > 0 }
                if (f == null) TestOutcome(ResultStatus.UNKNOWN, "لم يُكتشف منفذ لاختباره / No port was discovered to test", "راجع WIFI-005 / See WIFI-005")
                else try {
                    Socket().use { it.connect(InetSocketAddress(f.host, f.port), 1500) }
                    TestOutcome(
                        ResultStatus.SUCCESS,
                        "المنفذ ${f.port} يقبل اتصال TCP. الاتصال الفعلي يتطلب اقتراناً ومصادقة TLS وموافقتك. / " +
                            "Port ${f.port} accepts a TCP connection. An actual connection still needs pairing, TLS auth, and your approval."
                    )
                } catch (e: Exception) {
                    TestOutcome(ResultStatus.FAILED, "لم يستجب المنفذ ${f.port} / Port ${f.port} did not respond", "${e.javaClass.simpleName}: ${e.message}")
                }
            }
        },
        TestDefinition(
            id = "ADB-005", name = "منفذ ADB الشبكي الكلاسيكي (adb tcpip) / Classic network ADB port (adb tcpip)",
            description = "إن أعلنت الخصائص منفذاً (مثل 5555) فاختبر استجابته على الحلقة المحلية 127.0.0.1؛ لا يخرج الاتصال من الجهاز. / " +
                "If a property advertises a port (e.g. 5555), test it on the local loopback 127.0.0.1; the connection never leaves the device.",
            engine = Engine.ADB, method = "getprop + Socket.connect(127.0.0.1)",
            expected = "المنفذ يستجيب عند تفعيل التصحيح / The port responds once debugging is enabled"
        ) { tc ->
            val port = listOf("service.adb.tcp.port", "persist.adb.tcp.port")
                .firstNotNullOfOrNull { k -> Env.prop(k)?.toIntOrNull()?.takeIf { it in 1..65535 } }
            if (port == null) TestOutcome(ResultStatus.UNKNOWN, "لا منفذ شبكي معلن في الخصائص / No network port advertised in properties")
            else try {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 1500) }
                TestOutcome(ResultStatus.SUCCESS, "المنفذ $port يستجيب محلياً (adbd يستمع). هذا لا يعني أن الاتصال مصرَّح. / Port $port responds locally (adbd is listening). This doesn't mean a connection is authorized.")
            } catch (e: Exception) {
                if (tc.snapshot.usbDebugging.state != SettingState.ENABLED) TestOutcome(
                    ResultStatus.USER_ACTION_REQUIRED, "المنفذ $port معلن لكنه مغلق الآن (تصحيح USB غير مفعّل) / Port $port is advertised but closed right now (USB debugging is off)",
                    e.javaClass.simpleName,
                    "فعّل خيارات المطوّر ثم تصحيح USB وأعد الفحص؛ ربما يُفتح المنفذ بعدها (لم يُتحقق) / Enable developer options then USB debugging and re-scan; the port may open afterward (unverified)"
                ) else TestOutcome(
                    ResultStatus.FAILED, "المنفذ $port لا يستجيب رغم تفعيل التصحيح / Port $port doesn't respond despite debugging being enabled", "${e.javaClass.simpleName}: ${e.message}"
                )
            }
        },
        TestDefinition(
            id = "ADB-006", name = "مستوى تصحيح البناء / Build debug level",
            description = "نوع البناء (user/userdebug/eng) وخصائص الأمان المعلنة. معلومة وصفية. / Build type (user/userdebug/eng) and declared security properties. Descriptive info.",
            engine = Engine.ADB, method = "Build.TYPE + getprop", expected = "قراءة المعلومات / Reading the information"
        ) { _ ->
            val props = listOf("ro.debuggable", "ro.secure", "ro.adb.secure").mapNotNull { k -> Env.prop(k)?.let { "$k=$it" } }
            TestOutcome(
                ResultStatus.SUCCESS,
                "Build.TYPE=${Build.TYPE}، Build.TAGS=${Build.TAGS}" + if (props.isNotEmpty()) "، " + props.joinToString("، ") else "",
                null,
                if (Build.TYPE != "user")
                    "بناء غير تجاري (${Build.TYPE}): قد يكون التصحيح أقل تقييداً من الأجهزة التجارية؛ تأكد بتفعيله فعلياً. / " +
                        "Non-retail build (${Build.TYPE}): debugging may be less restricted than on retail devices; confirm by actually enabling it."
                else null
            )
        }
    )
}
