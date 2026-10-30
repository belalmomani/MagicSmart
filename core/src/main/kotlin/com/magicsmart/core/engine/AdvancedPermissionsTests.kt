package com.magicsmart.core.engine

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import com.magicsmart.core.ResultStatus
import java.io.File

object AdvancedPermissionsTests {
    private val suPaths = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
        "/system/sbin/su", "/vendor/bin/su", "/data/local/bin/su", "/data/local/xbin/su"
    )

    val all: List<TestDefinition> = listOf(
        TestDefinition(
            id = "PERM-001", name = "رصد ملف su",
            description = "البحث عن ملف su في المسارات المعروفة. وجود الملف مؤشر فقط وليس دليل Root فعّال.",
            engine = Engine.ADVANCED_PERMISSIONS, method = "File.exists",
            expected = "معرفة ما إذا كان هناك مؤشر Root"
        ) { _ ->
            val found = suPaths.filter { File(it).exists() }
            if (found.isEmpty()) TestOutcome(
                ResultStatus.UNSUPPORTED, "لم يُرصد su في المسارات المعروفة",
                "لا يعني ذلك استحالة وجود Root بطرق أخرى"
            ) else TestOutcome(
                ResultStatus.USER_ACTION_REQUIRED, "رُصد: ${found.joinToString()}", null,
                "للتحقق الفعلي شغّل PERM-002 بموافقتك"
            )
        },
        TestDefinition(
            id = "PERM-002", name = "تحقق فعلي من Root (بموافقتك)",
            description = "ينفّذ الأمر id فقط عبر su ليرى هل يُمنح uid=0. لا يُغيّر أي شيء. قد يظهر طلب من مدير Root.",
            engine = Engine.ADVANCED_PERMISSIONS, method = "su -c id (مهلة 15ث)",
            expected = "uid=0",
            requiresConsent = true
        ) { _ ->
            try {
                val p = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
                val deadline = System.currentTimeMillis() + 15_000
                var exit: Int? = null
                while (exit == null && System.currentTimeMillis() < deadline) {
                    try { exit = p.exitValue() } catch (e: IllegalThreadStateException) { Thread.sleep(100) }
                }
                if (exit == null) {
                    p.destroy()
                    TestOutcome(ResultStatus.BLOCKED, "انتهت المهلة دون رد", "لم تتم الموافقة أو تعطّل su")
                } else {
                    val out = p.inputStream.bufferedReader().use { it.readText().trim() }
                    if (exit == 0 && out.contains("uid=0")) TestOutcome(ResultStatus.SUCCESS, "Root فعّال: $out")
                    else TestOutcome(ResultStatus.FAILED, "لم يُمنح Root (exit=$exit)", out.take(120))
                }
            } catch (e: java.io.IOException) {
                TestOutcome(ResultStatus.UNSUPPORTED, "الأمر su غير موجود", e.message)
            }
        },
        TestDefinition(
            id = "PERM-003", name = "تطبيق Shizuku",
            description = "رصد تثبيت Shizuku. التثبيت وحده لا يعني أن الخدمة تعمل أو أن هذا التطبيق مُنح الإذن.",
            engine = Engine.ADVANCED_PERMISSIONS, method = "PackageManager.getPackageInfo",
            expected = "معرفة حالة Shizuku"
        ) { tc ->
            if (Probes.hasPackage(tc.context, "moe.shizuku.privileged.api")) TestOutcome(
                ResultStatus.UNKNOWN, "Shizuku مثبّت؛ لم يُتحقق من تشغيل الخدمة ولا من منح الإذن",
                "التحقق يتطلب Shizuku API", "سيُضاف التحقق بموافقتك لاحقاً"
            ) else TestOutcome(ResultStatus.UNSUPPORTED, "Shizuku غير مثبّت")
        },
        TestDefinition(
            id = "PERM-004", name = "Device Owner",
            description = "هل هذا التطبيق هو Device Owner؟",
            engine = Engine.ADVANCED_PERMISSIONS, method = "DevicePolicyManager.isDeviceOwnerApp",
            expected = "تحديد حالة Device Owner"
        ) { tc ->
            val dpm = tc.context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isDeviceOwnerApp(tc.context.packageName)) TestOutcome(ResultStatus.SUCCESS, "التطبيق هو Device Owner")
            else TestOutcome(
                ResultStatus.UNSUPPORTED, "التطبيق ليس Device Owner",
                "لا يُعيَّن من داخل التطبيق", "يتطلب مكوّن إدارة وإعداداً عبر ADB (غير مُنفَّذ بعد)"
            )
        },
        TestDefinition(
            id = "PERM-005", name = "صلاحيات التطبيق المطلوبة والممنوحة",
            description = "قائمة الصلاحيات المعلنة في manifest وحالة منحها فعلياً",
            engine = Engine.ADVANCED_PERMISSIONS, method = "PackageManager.getPackageInfo(GET_PERMISSIONS)",
            expected = "قائمة موثقة"
        ) { tc ->
            @Suppress("DEPRECATION")
            val info = tc.context.packageManager.getPackageInfo(tc.context.packageName, PackageManager.GET_PERMISSIONS)
            val names = info.requestedPermissions ?: emptyArray()
            val flags = info.requestedPermissionsFlags ?: IntArray(0)
            val granted = names.indices.filter {
                it < flags.size && (flags[it] and PackageInfo_GRANTED) != 0
            }
            TestOutcome(
                ResultStatus.SUCCESS,
                "المعلنة: ${names.size}، الممنوحة: ${granted.size} — " +
                    names.joinToString("، ") { it.substringAfterLast('.') },
                null,
                "صلاحيات النظام (مثل WRITE_SECURE_SETTINGS) غير معلنة عمداً ولن تُمنح"
            )
        },
        TestDefinition(
            id = "PERM-006", name = "هوية تنفيذ التطبيق (uid)",
            description = "تحديد المستوى الذي تعمل به العملية: تطبيق عادي أم shell أم root",
            engine = Engine.ADVANCED_PERMISSIONS, method = "Process.myUid",
            expected = "معرفة الهوية"
        ) { _ ->
            val uid = Process.myUid()
            val label = when (uid) {
                0 -> "root"
                2000 -> "shell (صلاحيات ADB)"
                else -> "تطبيق عادي"
            }
            TestOutcome(ResultStatus.SUCCESS, "uid=$uid: $label")
        }
    )

    private const val PackageInfo_GRANTED = android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED
}
