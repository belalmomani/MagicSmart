package com.magicsmart.core.engine

import android.content.Context
import com.magicsmart.core.DeviceInspector
import com.magicsmart.core.DeviceSnapshot
import com.magicsmart.core.ResultStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** نقطة التحكم المشتركة بين تطبيقي الهاتف والتلفاز. النتائج في الذاكرة فقط (المرحلة 4 تضيف السجل). */
class ScanController private constructor(private val appContext: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = TestEngine(TestRegistry.all())
    private var snapshot: DeviceSnapshot = DeviceInspector.inspect(appContext)
    private var job: Job? = null
    @Volatile private var pendingVerification = false

    private val _results = MutableStateFlow<List<TestResult>>(emptyList())
    val results: StateFlow<List<TestResult>> = _results.asStateFlow()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** عدد الاختبارات التلقائية المناسبة لهذا الجهاز (العدد الحقيقي). */
    fun planned(): Int = engine.automatic(snapshot).size
    fun plannedEngines(): List<Engine> = engine.automatic(snapshot).map { it.engine }.distinct()
    fun consentTests(): List<TestDefinition> = engine.consent(snapshot)

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

    /** تنفيذ اختبار يتطلب موافقة؛ تستدعيه الواجهة فقط بعد تأكيد المستخدم. */
    fun runConsented(id: String) {
        scope.launch {
            snapshot = DeviceInspector.inspect(appContext)
            engine.runOne(TestContext(appContext, snapshot), id) { _results.value = it }
        }
    }

    /** يفتح صفحة بمبادرة المستخدم، ويُعلّم أن التحقق من النتيجة مطلوب عند العودة للتطبيق. */
    fun launch(context: Context, spec: LaunchSpec): TestOutcome {
        val o = Launcher.launch(context, spec)
        if (o.status == ResultStatus.USER_ACTION_REQUIRED) pendingVerification = true
        return o
    }

    /** عند العودة من صفحة الإعدادات: أعد فحص المحركات المرتبطة للتحقق مما تغيّر فعلاً. */
    fun onAppResumed() {
        if (!pendingVerification || _running.value) return
        pendingVerification = false
        engine.forgetEngines(
            setOf(Engine.DEVELOPER_OPTIONS, Engine.USB_DEBUGGING, Engine.WIRELESS_DEBUGGING, Engine.ADB)
        )
        _results.value = engine.results()
        start()
    }

    companion object {
        @Volatile private var instance: ScanController? = null
        fun get(context: Context): ScanController = instance ?: synchronized(this) {
            instance ?: ScanController(context.applicationContext).also { instance = it }
        }
    }
}
