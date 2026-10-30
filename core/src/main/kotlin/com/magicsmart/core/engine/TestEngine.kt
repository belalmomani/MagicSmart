package com.magicsmart.core.engine

import android.content.pm.PackageManager
import android.os.SystemClock
import com.magicsmart.core.DeviceSnapshot
import com.magicsmart.core.ResultStatus
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

object TestRegistry {
    fun all(): List<TestDefinition> =
        (DeveloperOptionsTests.all + SettingsScannerTests.all + AndroidTvTests.all).distinctBy { it.id }
}

/** يشغّل الاختبارات المناسبة للجهاز مرة واحدة لكل معرّف، مع دعم الإيقاف والاستكمال وإعادة الفاشل. */
class TestEngine(tests: List<TestDefinition>) {
    private val registry = tests.distinctBy { it.id }
    private val done = LinkedHashMap<String, TestResult>()

    fun applicable(s: DeviceSnapshot): List<TestDefinition> = registry.filter { it.appliesTo(s) }
    fun results(): List<TestResult> = synchronized(done) { done.values.toList() }
    fun forgetFailed() = synchronized(done) {
        done.values.removeAll { it.status == ResultStatus.FAILED || it.status == ResultStatus.UNKNOWN }
    }
    fun reset() = synchronized(done) { done.clear() }

    suspend fun run(tc: TestContext, onProgress: (List<TestResult>) -> Unit) {
        for (t in applicable(tc.snapshot)) {
            currentCoroutineContext().ensureActive()
            if (synchronized(done) { done.containsKey(t.id) }) continue
            val r = execute(t, tc)
            synchronized(done) { done[t.id] = r }
            onProgress(results())
        }
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
