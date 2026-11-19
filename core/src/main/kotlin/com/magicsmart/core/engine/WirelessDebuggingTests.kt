package com.magicsmart.core.engine

import android.Manifest
import android.content.pm.PackageManager
import com.magicsmart.core.ResultStatus
import com.magicsmart.core.SettingState

object WirelessDebuggingTests {
    private const val DEV_ACTION = "android.settings.APPLICATION_DEVELOPMENT_SETTINGS"
    private const val PAIRING_HELP =
        "افتح «التصحيح اللاسلكي» ← «اقتران جهاز برمز الاقتران»، وأدخل الرمز في الحاسوب ثم وافق على بصمة المفتاح عند ظهور الطلب / " +
            "Open \"Wireless debugging\" \u2192 \"Pair device with pairing code\", enter the code on the computer, then approve the key fingerprint prompt"

    val all: List<TestDefinition> = listOf(
        TestDefinition(
            id = "WIFI-001", name = "دعم التصحيح اللاسلكي / Wireless-debugging support",
            description = "الميزة تتطلب Android 11+ وعتاد Wi-Fi / The feature requires Android 11+ and Wi-Fi hardware",
            engine = Engine.WIRELESS_DEBUGGING, method = "SDK_INT + FEATURE_WIFI",
            expected = "النظام يدعم الميزة / The system supports the feature"
        ) { tc ->
            when {
                tc.snapshot.sdkInt < 30 -> TestOutcome(
                    ResultStatus.UNSUPPORTED, "يتطلب Android 11 (API 30) أو أحدث؛ الجهاز API ${tc.snapshot.sdkInt} / Requires Android 11 (API 30)+; device is API ${tc.snapshot.sdkInt}",
                    "إصدار النظام أقدم من المطلوب / System version is older than required"
                )
                !tc.context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI) -> TestOutcome(
                    ResultStatus.UNSUPPORTED, "لا يوجد عتاد Wi-Fi / No Wi-Fi hardware", "الميزة تعتمد على Wi-Fi / The feature relies on Wi-Fi"
                )
                else -> TestOutcome(
                    ResultStatus.SUCCESS,
                    "الإصدار وعتاد Wi-Fi مناسبان. ملاحظة: قد يخفي المصنّع الخيار رغم ذلك. / " +
                        "Version and Wi-Fi hardware are suitable. Note: the vendor may still hide the option."
                )
            }
        },
        TestDefinition(
            id = "WIFI-002", name = "قراءة حالة التصحيح اللاسلكي / Read wireless-debugging state",
            description = "قراءة adb_wifi_enabled عندما تكون قابلة للقراءة / Read adb_wifi_enabled when it's readable",
            engine = Engine.WIRELESS_DEBUGGING, method = "Settings.Global.getInt",
            expected = "قراءة القيمة / Reading the value",
            launch = LaunchSpec(action = DEV_ACTION)
        ) { tc ->
            val r = tc.snapshot.wirelessDebugging
            when (r.state) {
                SettingState.ENABLED -> TestOutcome(ResultStatus.SUCCESS, "الحالة: مفعّل (تم التحقق بالقراءة) / State: enabled (verified by reading it)")
                SettingState.DISABLED -> TestOutcome(
                    ResultStatus.SUCCESS, "الحالة: غير مفعّل / State: disabled", null,
                    "فعّله بنفسك من صفحة المطوّر (يتطلب موافقتك على الشبكة) / Enable it yourself from the developer page (requires your network consent)"
                )
                SettingState.UNSUPPORTED -> TestOutcome(ResultStatus.UNSUPPORTED, r.state.arabic, r.note)
                SettingState.UNREADABLE -> TestOutcome(ResultStatus.UNKNOWN, r.state.arabic, r.note)
            }
        },
        TestDefinition(
            id = "WIFI-003", name = "الاتصال بشبكة Wi-Fi / Wi-Fi connectivity",
            description = "الميزة تتطلب اتصالاً بشبكة Wi-Fi / The feature requires an active Wi-Fi connection",
            engine = Engine.WIRELESS_DEBUGGING, method = "ConnectivityManager.getNetworkCapabilities",
            expected = "الجهاز متصل بشبكة Wi-Fi / Device is connected to Wi-Fi",
            requiredPermissions = listOf(Manifest.permission.ACCESS_NETWORK_STATE)
        ) { tc ->
            if (Env.wifiIpv4(tc.context).first) TestOutcome(ResultStatus.SUCCESS, "متصل بشبكة Wi-Fi / Connected to Wi-Fi")
            else TestOutcome(
                ResultStatus.USER_ACTION_REQUIRED, "غير متصل بشبكة Wi-Fi / Not connected to Wi-Fi", null,
                "اتصل بشبكة Wi-Fi (والتلفاز على Ethernet قد لا يوفر الخيار) / Connect to Wi-Fi (a TV on Ethernet may not offer this option)"
            )
        },
        TestDefinition(
            id = "WIFI-004", name = "عنوان الجهاز على الشبكة المحلية / Local network address",
            description = "عنوان IPv4 المحلي لشبكة Wi-Fi (يُعرض محلياً فقط ولا يُرسل) / The local IPv4 address on Wi-Fi (shown locally only, never sent anywhere)",
            engine = Engine.WIRELESS_DEBUGGING, method = "LinkProperties.linkAddresses",
            expected = "عنوان IPv4 متاح / An IPv4 address is available",
            requiredPermissions = listOf(Manifest.permission.ACCESS_NETWORK_STATE)
        ) { tc ->
            val (connected, ip) = Env.wifiIpv4(tc.context)
            when {
                !connected -> TestOutcome(ResultStatus.USER_ACTION_REQUIRED, "لا توجد شبكة Wi-Fi / No Wi-Fi network", null, "اتصل بشبكة Wi-Fi أولاً / Connect to Wi-Fi first")
                ip == null -> TestOutcome(ResultStatus.UNKNOWN, "الشبكة متصلة لكن لا يوجد IPv4 / Connected but no IPv4", "قد تكون الشبكة IPv6 فقط / The network may be IPv6-only")
                else -> TestOutcome(ResultStatus.SUCCESS, "IPv4: $ip. معرفة العنوان وحدها ليست اتصال ADB. / Knowing the address alone is not an ADB connection.")
            }
        },
        TestDefinition(
            id = "WIFI-005", name = "اكتشاف خدمة الاتصال اللاسلكي (mDNS) / Wireless-connect service discovery (mDNS)",
            description = "البحث عن الخدمة _adb-tls-connect._tcp ومنفذها / Look for the _adb-tls-connect._tcp service and its port",
            engine = Engine.WIRELESS_DEBUGGING, method = "NsdManager.discoverServices",
            expected = "رصد الخدمة عند تفعيل التصحيح اللاسلكي / Service detected once wireless debugging is on"
        ) { tc ->
            if (tc.snapshot.wirelessDebugging.state != SettingState.ENABLED) TestOutcome(
                ResultStatus.USER_ACTION_REQUIRED, "التصحيح اللاسلكي غير مفعّل أو غير مقروء؛ لا خدمة للاكتشاف / Wireless debugging is off or unreadable; nothing to discover", null,
                "فعّل التصحيح اللاسلكي ثم أعد الفحص / Enable wireless debugging, then re-scan"
            ) else {
                val r = NsdProbe.connect(tc)
                val f = r.found.firstOrNull()
                when {
                    f != null -> TestOutcome(
                        ResultStatus.SUCCESS,
                        "رُصدت الخدمة / Service detected: ${f.name}" + (if (f.host != null) " على / at ${f.host}:${f.port}" else " (تعذّر تحديد المنفذ / port could not be determined)")
                    )
                    r.error != null -> TestOutcome(ResultStatus.FAILED, "تعذّر الاكتشاف / Discovery failed", r.error)
                    else -> TestOutcome(
                        ResultStatus.UNKNOWN, "لم تُرصد الخدمة خلال المهلة / Service not detected within the timeout",
                        "قد يمنع النظام أو الشبكة mDNS، والفحص لحظي / The system or network may block mDNS, and the check is a point-in-time snapshot",
                        "أعد الفحص بعد التأكد من اتصال Wi-Fi / Re-scan after confirming Wi-Fi connectivity"
                    )
                }
            }
        },
        TestDefinition(
            id = "WIFI-006", name = "رصد نافذة الاقتران برمز / Pairing-code window detection",
            description = "البحث عن الخدمة _adb-tls-pairing._tcp (تظهر فقط أثناء فتح نافذة الاقتران) / " +
                "Look for the _adb-tls-pairing._tcp service (only appears while the pairing window is open)",
            engine = Engine.WIRELESS_DEBUGGING, method = "NsdManager.discoverServices",
            expected = "رصد خدمة الاقتران إن كانت النافذة مفتوحة / Pairing service detected if the window is open"
        ) { tc ->
            if (tc.snapshot.wirelessDebugging.state != SettingState.ENABLED) TestOutcome(
                ResultStatus.USER_ACTION_REQUIRED, "التصحيح اللاسلكي غير مفعّل أو غير مقروء / Wireless debugging is off or unreadable", null, PAIRING_HELP
            ) else {
                val r = NsdProbe.pairing(tc)
                val f = r.found.firstOrNull()
                when {
                    f != null -> TestOutcome(ResultStatus.SUCCESS, "رُصدت خدمة اقتران مفتوحة / Open pairing service detected: ${f.name}")
                    r.error != null -> TestOutcome(ResultStatus.FAILED, "تعذّر الاكتشاف / Discovery failed", r.error)
                    else -> TestOutcome(
                        ResultStatus.USER_ACTION_REQUIRED, "لا توجد نافذة اقتران مفتوحة الآن / No pairing window is open right now", null, PAIRING_HELP
                    )
                }
            }
        }
    )
}
