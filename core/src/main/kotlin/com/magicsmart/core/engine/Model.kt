package com.magicsmart.core.engine

import android.content.ComponentName
import android.content.Context
import com.magicsmart.core.DeviceSnapshot
import com.magicsmart.core.ResultStatus
import java.util.concurrent.ConcurrentHashMap

enum class Engine(val arabic: String) {
    DEVELOPER_OPTIONS("خيارات المطوّر / Developer options"),
    SETTINGS_SCANNER("ماسح الإعدادات / Settings scanner"),
    USB_DEBUGGING("تصحيح USB / USB debugging"),
    WIRELESS_DEBUGGING("التصحيح اللاسلكي / Wireless debugging"),
    ADB("محلل ADB / ADB analyzer"),
    ADVANCED_PERMISSIONS("الصلاحيات المتقدمة / Advanced permissions"),
    OEM("محلل الشركات / OEM analyzer"),
    ANDROID_TV("محلل Android TV / Android TV analyzer")
}

enum class DeviceTarget { ANY, PHONE, TV }

/** وصف مسار قابل للفتح بمبادرة المستخدم. */
class LaunchSpec(val action: String? = null, val component: ComponentName? = null)

class TestContext(val context: Context, val snapshot: DeviceSnapshot) {
    private val cache = ConcurrentHashMap<String, Any>()

    /** يتيح مشاركة نتيجة فحص مكلف (مثل اكتشاف mDNS) بين عدة اختبارات في الدورة نفسها. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> cached(key: String, compute: () -> T): T = cache.getOrPut(key, compute) as T
}

class TestOutcome(
    val status: ResultStatus,
    val actual: String,
    val failureReason: String? = null,
    val nextAction: String? = null
)

/** تعريف اختبار مستقل؛ إضافة اختبار جديد = إضافة كائن جديد إلى قائمة محرك ما. */
class TestDefinition(
    val id: String,
    val name: String,
    val description: String,
    val engine: Engine,
    val method: String,
    val expected: String,
    val minApi: Int = 1,
    val maxApi: Int = Int.MAX_VALUE,
    val target: DeviceTarget = DeviceTarget.ANY,
    val requiredPermissions: List<String> = emptyList(),
    val launch: LaunchSpec? = null,
    /** اختبار لا يُنفَّذ تلقائياً؛ يُنفَّذ فقط بعد موافقة صريحة من المستخدم. */
    /** شرط إضافي لانطباق الاختبار (مثل مطابقة ملف OEM). */
    val condition: (DeviceSnapshot) -> Boolean = { true },
    val requiresConsent: Boolean = false,
    val run: (TestContext) -> TestOutcome
) {
    fun appliesTo(s: DeviceSnapshot): Boolean =
        condition(s) && s.sdkInt in minApi..maxApi && when (target) {
            DeviceTarget.ANY -> true
            DeviceTarget.TV -> s.isTv
            DeviceTarget.PHONE -> !s.isTv
        }
}

class TestResult(
    val definition: TestDefinition,
    val status: ResultStatus,
    val actual: String,
    val failureReason: String?,
    val nextAction: String?,
    val durationMs: Long,
    val finishedAtMillis: Long
)
