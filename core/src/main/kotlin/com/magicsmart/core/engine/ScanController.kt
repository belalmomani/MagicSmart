package com.magicsmart.core.engine

import android.content.Context
import com.magicsmart.core.DeviceInspector
import com.magicsmart.core.DeviceSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** نقطة التحكم المشتركة بين تطبيقي الهاتف والتلفاز. تخزين النتائج في الذاكرة فقط (المرحلة 4 تضيف السجل). */
class ScanController private constructor(private val appContext: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = TestEngine(TestRegistry.all())
    private var snapshot: DeviceSnapshot = DeviceInspector.inspect(appContext)
    private var job: Job? = null

    private val _results = MutableStateFlow<List<TestResult>>(emptyList())
    val results: StateFlow<List<TestResult>> = _results.asStateFlow()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** عدد الاختبارات المناسبة لهذا الجهاز (العدد الحقيقي). */
    fun planned(): Int = engine.applicable(snapshot).size
    fun plannedEngines(): List<Engine> = engine.applicable(snapshot).map { it.engine }.distinct()

    fun start() {
        if (_running.value) return
        _running.value = true
        snapshot = DeviceInspector.inspect(appContext)
        job = scope.launch {
            try {
                engine.run(TestContext(appContext, snapshot)) { _results.value = it }
            } finally {
                _running.value = false
            }
        }
    }

    fun stop() { job?.cancel() }

    fun resetAndStart() {
        if (_running.value) return
        engine.reset(); _results.value = emptyList(); start()
    }

    fun rerunFailed() {
        if (_running.value) return
        engine.forgetFailed(); _results.value = engine.results(); start()
    }

    companion object {
        @Volatile private var instance: ScanController? = null
        fun get(context: Context): ScanController = instance ?: synchronized(this) {
            instance ?: ScanController(context.applicationContext).also { instance = it }
        }
    }
}
