package com.magicsmart.core.engine

import android.content.Context
import com.magicsmart.core.DeviceInspector
import com.magicsmart.core.DeviceSnapshot
import com.magicsmart.core.ResultStatus
import com.magicsmart.core.oem.OemProfiles
import com.magicsmart.core.report.ResultStore
import com.magicsmart.core.report.StoredRun
import com.magicsmart.core.report.toStored
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** نقطة التحكم المشتركة بين تطبيقي الهاتف والتلفاز. كل دورة تُحفظ محلياً في سجل JSON. */
class ScanController private constructor(private val appContext: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val oem = OemProfiles.get(appContext)
    private val engine = TestEngine(TestRegistry.all(appContext))
    private var snapshot: DeviceSnapshot = DeviceInspector.inspect(appContext)
    private var job: Job? = null
    @Volatile private var pendingVerification = false
    @Volatile private var sessionId = 0L

    private val _results = MutableStateFlow<List<TestResult>>(emptyList())
    val results: StateFlow<List<TestResult>> = _results.asStateFlow()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    private val _historyVersion = MutableStateFlow(0)
    val historyVersion: StateFlow<Int> = _historyVersion.asStateFlow()

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
                persist()
            }
        }
    }

    fun stop() { job?.cancel() }

    fun resetAndStart() {
        if (_running.value) return
        engine.reset(); _results.value = emptyList(); sessionId = 0L; start()
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
            persist()
        }
    }

    /** يفتح صفحة بمبادرة المستخدم، ويُعلّم أن التحقق من النتيجة مطلوب عند العودة للتطبيق. */
    fun launch(context: Context, spec: LaunchSpec): TestOutcome {
        val o = Launcher.launch(context, spec)
        if (o.status == ResultStatus.USER_ACTION_REQUIRED) pendingVerification = true
        return o
    }

    /** عند العودة من صفحة إعدادات: أعد فحص المحركات المرتبطة للتحقق مما تغيّر فعلاً. */
    fun onAppResumed() {
        if (!pendingVerification || _running.value) return
        pendingVerification = false
        engine.forgetEngines(
            setOf(Engine.DEVELOPER_OPTIONS, Engine.USB_DEBUGGING, Engine.WIRELESS_DEBUGGING, Engine.ADB)
        )
        _results.value = engine.results()
        start()
    }

    // ---- السجل والتقارير ----
    fun currentRun(): StoredRun {
        val rs = _results.value
        if (sessionId == 0L) sessionId = System.currentTimeMillis()
        return StoredRun(
            id = sessionId,
            device = "${snapshot.manufacturer} ${snapshot.model}",
            androidInfo = "${snapshot.androidRelease} (API ${snapshot.sdkInt})",
            isTv = snapshot.isTv,
            oem = OemProfiles.select(oem, snapshot).joinToString("، ") { it.first.name },
            complete = rs.count { !it.definition.requiresConsent } >= planned(),
            results = rs.map { it.toStored() }
        )
    }

    fun history(): List<StoredRun> = ResultStore.list(appContext)
    fun clearHistory() { ResultStore.clear(appContext); _historyVersion.value++ }

    private fun persist() {
        if (_results.value.isEmpty()) return
        try {
            ResultStore.save(appContext, currentRun())
            _historyVersion.value++
        } catch (e: Exception) { /* فشل الحفظ لا يوقف الفحص */ }
    }

    companion object {
        @Volatile private var instance: ScanController? = null
        fun get(context: Context): ScanController = instance ?: synchronized(this) {
            instance ?: ScanController(context.applicationContext).also { instance = it }
        }
    }
}
