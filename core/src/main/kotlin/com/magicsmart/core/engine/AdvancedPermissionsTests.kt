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
            id = "PERM-001", name = "رصد ملف su / Detect su binary",
            description = "البحث عن ملف su في المسارات المعروفة. وجود الملف مؤشر فقط وليس دليل Root فعّال. / " +
                "Look for an su binary in known paths. Its presence is only an indicator, not proof of active Root.",
            engine = Engine.ADVANCED_PERMISSIONS, method = "File.exists",
            expected = "معرفة ما إذا كان هناك مؤشر Root / Knowing whether there's a Root indicator"
        ) { _ ->
            val found = suPaths.filter { File(it).exists() }
            if (found.isEmpty()) TestOutcome(
                ResultStatus.UNSUPPORTED, "لم يُرصد su في المسارات المعروفة / No su found in the known paths",
                "لا يعني ذلك استحالة وجود Root بطرق أخرى / This doesn't rule out Root via other means"
            ) else TestOutcome(
                ResultStatus.USER_ACTION_REQUIRED, "رُصد / Detected: ${found.joinToString()}", null,
                "للتحقق الفعلي شغّل PERM-002 بموافقتك / For an actual check, run PERM-002 with your consent"
            )
        },
        TestDefinition(
            id = "PERM-002", name = "تحقق فعلي من Root (بموافقتك) / Actual Root check (with your consent)",
            description = "ينفّذ الأمر id فقط عبر su ليرى هل يُمنح uid=0. لا يُغيّر أي شيء. قد يظهر طلب من مدير Root. / " +
                "Runs only the id command via su to see whether uid=0 is granted. Changes nothing. A Root manager prompt may appear.",
            engine = Engine.ADVANCED_PERMISSIONS, method = "su -c id (مهلة 15ث / 15s timeout)",
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
                    TestOutcome(ResultStatus.BLOCKED, "انتهت المهلة دون رد / Timed out with no reply", "لم تتم الموافقة أو تعطّل su / Not approved, or su malfunctioned")
                } else {
                    val out = p.inputStream.bufferedReader().use { it.readText().trim() }
                    if (exit == 0 && out.contains("uid=0")) TestOutcome(ResultStatus.SUCCESS, "Root فعّال / Root is active: $out")
                    else TestOutcome(ResultStatus.FAILED, "لم يُمنح Root (exit=$exit) / Root not granted (exit=$exit)", out.take(120))
                }
            } catch (e: java.io.IOException) {
                TestOutcome(ResultStatus.UNSUPPORTED, "الأمر su غير موجود / The su command doesn't exist", e.message)
            }
        },
        TestDefinition(
            id = "PERM-003", name = "تطبيق Shizuku / Shizuku app",
            description = "رصد تثبيت Shizuku. التثبيت وحده لا يعني أن الخدمة تعمل أو أن هذا التطبيق مُنح الإذن. / " +
                "Detect whether Shizuku is installed. Installation alone doesn't mean its service is running or that this app was granted access.",
            engine = Engine.ADVANCED_PERMISSIONS, method = "PackageManager.getPackageInfo",
            expected = "معرفة حالة Shizuku / Knowing Shizuku's state"
        ) { tc ->
            if (Probes.hasPackage(tc.context, "moe.shizuku.privileged.api")) TestOutcome(
                ResultStatus.UNKNOWN, "Shizuku مثبّت؛ لم يُتحقق من تشغيل الخدمة ولا من منح الإذن / Shizuku is installed; neither the running service nor granted access was verified",
                "التحقق يتطلب Shizuku API / Verifying requires the Shizuku API", "سيُضاف التحقق بموافقتك لاحقاً / A real check will be added later, with your consent"
            ) else TestOutcome(ResultStatus.UNSUPPORTED, "Shizuku غير مثبّت / Shizuku is not installed")
        },
        TestDefinition(
            id = "PERM-004", name = "Device Owner",
            description = "هل هذا التطبيق هو Device Owner؟ / Is this app the Device Owner?",
            engine = Engine.ADVANCED_PERMISSIONS, method = "DevicePolicyManager.isDeviceOwnerApp",
            expected = "تحديد حالة Device Owner / Determining Device Owner status"
        ) { tc ->
            val dpm = tc.context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isDeviceOwnerApp(tc.context.packageName)) TestOutcome(ResultStatus.SUCCESS, "التطبيق هو Device Owner / This app is the Device Owner")
            else TestOutcome(
                ResultStatus.UNSUPPORTED, "التطبيق ليس Device Owner / This app is not the Device Owner",
                "لا يُعيَّن من داخل التطبيق / Can't be set from inside the app", "يتطلب مكوّن إدارة وإعداداً عبر ADB (غير مُنفَّذ بعد) / Requires an admin component and ADB provisioning (not implemented yet)"
            )
        },
        TestDefinition(
            id = "PERM-005", name = "صلاحيات التطبيق المطلوبة والممنوحة / App's requested & granted permissions",
            description = "قائمة الصلاحيات المعلنة في manifest وحالة منحها فعلياً / List of permissions declared in the manifest and whether each was actually granted",
            engine = Engine.ADVANCED_PERMISSIONS, method = "PackageManager.getPackageInfo(GET_PERMISSIONS)",
            expected = "قائمة موثقة / A documented list"
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
                "المعلنة / Declared: ${names.size}، الممنوحة / Granted: ${granted.size} — " +
                    names.joinToString("، ") { it.substringAfterLast('.') },
                null,
                "صلاحيات النظام (مثل WRITE_SECURE_SETTINGS) غير معلنة عمداً ولن تُمنح / " +
                    "System permissions (like WRITE_SECURE_SETTINGS) are deliberately not declared and won't be granted"
            )
        },
        TestDefinition(
            id = "PERM-006", name = "هوية تنفيذ التطبيق (uid) / App execution identity (uid)",
            description = "تحديد المستوى الذي تعمل به العملية: تطبيق عادي أم shell أم root / Determine the level the process runs at: regular app, shell, or root",
            engine = Engine.ADVANCED_PERMISSIONS, method = "Process.myUid",
            expected = "معرفة الهوية / Knowing the identity"
        ) { _ ->
            val uid = Process.myUid()
            val label = when (uid) {
                0 -> "root"
                2000 -> "shell (صلاحيات ADB / ADB privileges)"
                else -> "تطبيق عادي / regular app"
            }
            TestOutcome(ResultStatus.SUCCESS, "uid=$uid: $label")
        }
    )

    private const val PackageInfo_GRANTED = android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED
}
