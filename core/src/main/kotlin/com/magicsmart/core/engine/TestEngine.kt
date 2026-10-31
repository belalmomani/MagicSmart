package com.magicsmart.core.engine

import android.content.pm.PackageManager
import android.os.SystemClock
import com.magicsmart.core.DeviceSnapshot
import com.magicsmart.core.ResultStatus
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

object TestRegistry {
    fun all(context: android.content.Context): List<TestDefinition> = (
        DeveloperOptionsTests.all + SettingsScannerTests.all + AndroidTvTests.all +
            UsbDebuggingTests.all + WirelessDebuggingTests.all + AdbAnalyzerTests.all +
            AdvancedPermissionsTests.all + OemTests.build(context)
        ).distinctBy { it.id }
}

/**
 * يشغّل الاختبارات المناسبة للجهاز مرة واحدة لكل معرّف، مع دعم الإيقاف والاستكمال وإعادة الفاشل.
 * اختبارات requiresConsent لا تعمل تلقائياً أبداً؛ تُنفَّذ فقط عبر runOne بعد موافقة المستخدم.
 */
class TestEngine(tests: List<TestDefinition>) {
    private val registry = tests.distinctBy { it.id }
    private val done = LinkedHashMap<String, TestResult>()

    fun automatic(s: DeviceSnapshot): List<TestDefinition> = registry.filter { it.appliesTo(s) && !it.requiresConsent }
    fun consent(s: DeviceSnapshot): List<TestDefinition> = registry.filter { it.appliesTo(s) && it.requiresConsent }
    fun results(): List<TestResult> = synchronized(done) { done.values.toList() }

    fun forgetFailed() = synchronized(done) {
        done.values.removeAll { it.status == ResultStatus.FAILED || it.status == ResultStatus.UNKNOWN }
    }
    fun forgetEngines(engines: Set<Engine>) = synchronized(done) {
        done.values.removeAll { it.definition.engine in engines && !it.definition.requiresConsent }
    }
    fun reset() = synchronized(done) { done.clear() }

    suspend fun run(tc: TestContext, onProgress: (List<TestResult>) -> Unit) {
        for (t in automatic(tc.snapshot)) {
            currentCoroutineContext().ensureActive()
            if (synchronized(done) { done.containsKey(t.id) }) continue
            store(execute(t, tc), onProgress)
        }
    }

    /** تنفيذ اختبار واحد بعد موافقة صريحة (يُستدعى من الواجهة فقط). */
    fun runOne(tc: TestContext, id: String, onProgress: (List<TestResult>) -> Unit) {
        val t = registry.firstOrNull { it.id == id && it.appliesTo(tc.snapshot) } ?: return
        store(execute(t, tc), onProgress)
    }

    private fun store(r: TestResult, onProgress: (List<TestResult>) -> Unit) {
        synchronized(done) { done[r.definition.id] = r }
        onProgress(results())
    }

    private fun execute(t: TestDefinition, tc: TestContext): TestResult {
        val start = SystemClock.elapsedRealtime()
        val missing = t.requiredPermissions.filter {
            tc.context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        val o = if (missing.isNotEmpty()) {
            TestOutcome(ResultStatus.PERMISSION_REQUIRED, "صلاحيات غير ممنوحة", missing.joinToString(), "امنح الصلاحية المذكورة")
        } else try {
            t.run(tc)
        } catch (e: Throwable) {
            TestOutcome(ResultStatus.FAILED, "استثناء أثناء التنفيذ", "${e.javaClass.simpleName}: ${e.message}")
        }
        return TestResult(
            t, o.status, o.actual, o.failureReason, o.nextAction,
            SystemClock.elapsedRealtime() - start, System.currentTimeMillis()
        )
    }
}
