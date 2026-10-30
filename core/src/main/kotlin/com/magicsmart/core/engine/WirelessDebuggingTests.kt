package com.magicsmart.core.engine

import android.Manifest
import android.content.pm.PackageManager
import com.magicsmart.core.ResultStatus
import com.magicsmart.core.SettingState

object WirelessDebuggingTests {
    private const val DEV_ACTION = "android.settings.APPLICATION_DEVELOPMENT_SETTINGS"
    private const val PAIRING_HELP =
        "افتح «التصحيح اللاسلكي» ← «اقتران جهاز برمز الاقتران»، وأدخل الرمز في الحاسوب ثم وافق على بصمة المفتاح عند ظهور الطلب"

    val all: List<TestDefinition> = listOf(
        TestDefinition(
            id = "WIFI-001", name = "دعم التصحيح اللاسلكي",
            description = "الميزة تتطلب Android 11+ وعتاد Wi-Fi",
            engine = Engine.WIRELESS_DEBUGGING, method = "SDK_INT + FEATURE_WIFI",
            expected = "النظام يدعم الميزة"
        ) { tc ->
            when {
                tc.snapshot.sdkInt < 30 -> TestOutcome(
                    ResultStatus.UNSUPPORTED, "يتطلب Android 11 (API 30) أو أحدث؛ الجهاز API ${tc.snapshot.sdkInt}",
                    "إصدار النظام أقدم من المطلوب"
                )
                !tc.context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI) -> TestOutcome(
                    ResultStatus.UNSUPPORTED, "لا يوجد عتاد Wi-Fi", "الميزة تعتمد على Wi-Fi"
                )
                else -> TestOutcome(
                    ResultStatus.SUCCESS,
                    "الإصدار وعتاد Wi-Fi مناسبان. ملاحظة: قد يخفي المصنّع الخيار رغم ذلك."
                )
            }
        },
        TestDefinition(
            id = "WIFI-002", name = "قراءة حالة التصحيح اللاسلكي",
            description = "قراءة adb_wifi_enabled عندما تكون قابلة للقراءة",
            engine = Engine.WIRELESS_DEBUGGING, method = "Settings.Global.getInt",
            expected = "قراءة القيمة",
            launch = LaunchSpec(action = DEV_ACTION)
        ) { tc ->
            val r = tc.snapshot.wirelessDebugging
            when (r.state) {
                SettingState.ENABLED -> TestOutcome(ResultStatus.SUCCESS, "الحالة: مفعّل (تم التحقق بالقراءة)")
                SettingState.DISABLED -> TestOutcome(
                    ResultStatus.SUCCESS, "الحالة: غير مفعّل", null,
                    "فعّله بنفسك من صفحة المطوّر (يتطلب موافقتك على الشبكة)"
                )
                SettingState.UNSUPPORTED -> TestOutcome(ResultStatus.UNSUPPORTED, r.state.arabic, r.note)
                SettingState.UNREADABLE -> TestOutcome(ResultStatus.UNKNOWN, r.state.arabic, r.note)
            }
        },
        TestDefinition(
            id = "WIFI-003", name = "الاتصال بشبكة Wi-Fi",
            description = "الميزة تتطلب اتصالاً بشبكة Wi-Fi",
            engine = Engine.WIRELESS_DEBUGGING, method = "ConnectivityManager.getNetworkCapabilities",
            expected = "الجهاز متصل بشبكة Wi-Fi",
            requiredPermissions = listOf(Manifest.permission.ACCESS_NETWORK_STATE)
        ) { tc ->
            if (Env.wifiIpv4(tc.context).first) TestOutcome(ResultStatus.SUCCESS, "متصل بشبكة Wi-Fi")
            else TestOutcome(
                ResultStatus.USER_ACTION_REQUIRED, "غير متصل بشبكة Wi-Fi", null,
                "اتصل بشبكة Wi-Fi (والتلفاز على Ethernet قد لا يوفر الخيار)"
            )
        },
        TestDefinition(
            id = "WIFI-004", name = "عنوان الجهاز على الشبكة المحلية",
            description = "عنوان IPv4 المحلي لشبكة Wi-Fi (يُعرض محلياً فقط ولا يُرسل)",
            engine = Engine.WIRELESS_DEBUGGING, method = "LinkProperties.linkAddresses",
            expected = "عنوان IPv4 متاح",
            requiredPermissions = listOf(Manifest.permission.ACCESS_NETWORK_STATE)
        ) { tc ->
            val (connected, ip) = Env.wifiIpv4(tc.context)
            when {
                !connected -> TestOutcome(ResultStatus.USER_ACTION_REQUIRED, "لا توجد شبكة Wi-Fi", null, "اتصل بشبكة Wi-Fi أولاً")
                ip == null -> TestOutcome(ResultStatus.UNKNOWN, "الشبكة متصلة لكن لا يوجد IPv4", "قد تكون الشبكة IPv6 فقط")
                else -> TestOutcome(ResultStatus.SUCCESS, "IPv4: $ip. معرفة العنوان وحدها ليست اتصال ADB.")
            }
        },
        TestDefinition(
            id = "WIFI-005", name = "اكتشاف خدمة الاتصال اللاسلكي (mDNS)",
            description = "البحث عن الخدمة _adb-tls-connect._tcp ومنفذها",
            engine = Engine.WIRELESS_DEBUGGING, method = "NsdManager.discoverServices",
            expected = "رصد الخدمة عند تفعيل التصحيح اللاسلكي"
        ) { tc ->
            if (tc.snapshot.wirelessDebugging.state != SettingState.ENABLED) TestOutcome(
                ResultStatus.USER_ACTION_REQUIRED, "التصحيح اللاسلكي غير مفعّل أو غير مقروء؛ لا خدمة للاكتشاف", null,
                "فعّل التصحيح اللاسلكي ثم أعد الفحص"
            ) else {
                val r = NsdProbe.connect(tc)
                val f = r.found.firstOrNull()
                when {
                    f != null -> TestOutcome(
                        ResultStatus.SUCCESS,
                        "رُصدت الخدمة ${f.name}" + (if (f.host != null) " على ${f.host}:${f.port}" else " (تعذّر تحديد المنفذ)")
                    )
                    r.error != null -> TestOutcome(ResultStatus.FAILED, "تعذّر الاكتشاف", r.error)
                    else -> TestOutcome(
                        ResultStatus.UNKNOWN, "لم تُرصد الخدمة خلال المهلة",
                        "قد يمنع النظام أو الشبكة mDNS، والفحص لحظي", "أعد الفحص بعد التأكد من اتصال Wi-Fi"
                    )
                }
            }
        },
        TestDefinition(
            id = "WIFI-006", name = "رصد نافذة الاقتران برمز",
            description = "البحث عن الخدمة _adb-tls-pairing._tcp (تظهر فقط أثناء فتح نافذة الاقتران)",
            engine = Engine.WIRELESS_DEBUGGING, method = "NsdManager.discoverServices",
            expected = "رصد خدمة الاقتران إن كانت النافذة مفتوحة"
        ) { tc ->
            if (tc.snapshot.wirelessDebugging.state != SettingState.ENABLED) TestOutcome(
                ResultStatus.USER_ACTION_REQUIRED, "التصحيح اللاسلكي غير مفعّل أو غير مقروء", null, PAIRING_HELP
            ) else {
                val r = NsdProbe.pairing(tc)
                val f = r.found.firstOrNull()
                when {
                    f != null -> TestOutcome(ResultStatus.SUCCESS, "رُصدت خدمة اقتران مفتوحة: ${f.name}")
                    r.error != null -> TestOutcome(ResultStatus.FAILED, "تعذّر الاكتشاف", r.error)
                    else -> TestOutcome(
                        ResultStatus.USER_ACTION_REQUIRED, "لا توجد نافذة اقتران مفتوحة الآن", null, PAIRING_HELP
                    )
                }
            }
        }
    )
}
