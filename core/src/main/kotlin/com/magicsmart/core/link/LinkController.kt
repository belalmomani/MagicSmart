package com.magicsmart.core.link

import android.content.Context
import com.magicsmart.core.report.StoredRun
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SasPrompt(val peerName: String, val code: String)
class RemoteReport(val peerName: String, val run: StoredRun)

/** تحكم الهاتف بالربط: اكتشاف، اقتران، جلب تقرير التلفاز، إلغاء الربط. */
class LinkController private constructor(private val ctx: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _tvs = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    val tvs: StateFlow<List<DiscoveredTv>> = _tvs.asStateFlow()
    private val _paired = MutableStateFlow(LinkStore.peers(ctx))
    val paired: StateFlow<List<PairedPeer>> = _paired.asStateFlow()
    private val _status = MutableStateFlow("غير متصل / Not connected")
    val status: StateFlow<String> = _status.asStateFlow()
    private val _sas = MutableStateFlow<SasPrompt?>(null)
    val sas: StateFlow<SasPrompt?> = _sas.asStateFlow()
    private val _remote = MutableStateFlow<RemoteReport?>(null)
    val remote: StateFlow<RemoteReport?> = _remote.asStateFlow()

    private val discovery = LinkDiscovery(ctx) { _tvs.value = it }
    @Volatile private var sasAnswer: CompletableDeferred<Boolean>? = null
    @Volatile private var busy = false

    fun startDiscovery() { discovery.start(); _status.value = "جارٍ البحث عن تلفازات في الشبكة… / Searching for TVs on the network…" }
    fun stopDiscovery() = discovery.stop()

    /** "192.168.1.5:43210" ← (المضيف، المنفذ) أو null. */
    fun parseManual(text: String): Pair<String, Int>? {
        val t = text.trim()
        val i = t.lastIndexOf(':')
        if (i <= 0) return null
        val port = t.substring(i + 1).toIntOrNull() ?: return null
        return if (port in 1..65535) t.substring(0, i) to port else null
    }

    fun answerSas(ok: Boolean) { sasAnswer?.complete(ok) }

    fun pair(host: String, port: Int) {
        if (busy) return
        busy = true
        scope.launch {
            _status.value = "جارٍ الاقتران… قارن الرمز مع الظاهر على التلفاز / Pairing… compare the code with the TV\u2019s screen"
            val r = LinkClient.pair(ctx, host, port) { peerName, code ->
                val d = CompletableDeferred<Boolean>()
                sasAnswer = d
                _sas.value = SasPrompt(peerName, code)
                val ok = withTimeoutOrNull(120_000) { d.await() } ?: false
                _sas.value = null
                sasAnswer = null
                ok
            }
            _status.value = when (r) {
                is PairResult.Paired -> "تم الاقتران مع ${r.peer.name} / Paired with ${r.peer.name}"
                is PairResult.Rejected -> r.reason
                is PairResult.Failed -> "فشل الاقتران / Pairing failed: ${r.reason}"
            }
            _paired.value = LinkStore.peers(ctx)
            busy = false
        }
    }

    fun fetch(peer: PairedPeer, host: String, port: Int) {
        if (busy) return
        busy = true
        scope.launch {
            _status.value = "جارٍ الاتصال بـ ${peer.name}… / Connecting to ${peer.name}…"
            try {
                val run = LinkClient.fetchReport(ctx, host, port, peer)
                _remote.value = RemoteReport(peer.name, run)
                _status.value = "متصل ومصادَق — استُلم تقرير ${peer.name} (${run.results.size} اختباراً) / Connected & authenticated — received ${peer.name}\u2019s report (${run.results.size} tests)"
            } catch (e: Exception) {
                _status.value = "تعذّر جلب التقرير / Could not fetch report: ${LinkClient.explain(e)}"
            }
            busy = false
        }
    }

    /** يحاول إبلاغ التلفاز بالإلغاء إن كان متاحاً، ثم يحذف الربط محلياً في كل الأحوال. */
    fun unpair(peer: PairedPeer, host: String?, port: Int) {
        scope.launch {
            if (host != null) try { LinkClient.unpair(ctx, host, port, peer) } catch (e: Exception) { }
            LinkStore.remove(ctx, peer.id)
            _paired.value = LinkStore.peers(ctx)
            if (_remote.value?.peerName == peer.name) _remote.value = null
            _status.value = "أُلغي الربط مع ${peer.name} / Unpaired ${peer.name}" + if (host == null) " (محلياً فقط؛ التلفاز لم يُبلَّغ / local only, TV not notified)" else ""
        }
    }

    companion object {
        @Volatile private var instance: LinkController? = null
        fun get(context: Context): LinkController = instance ?: synchronized(this) {
            instance ?: LinkController(context.applicationContext).also { instance = it }
        }
    }
}
