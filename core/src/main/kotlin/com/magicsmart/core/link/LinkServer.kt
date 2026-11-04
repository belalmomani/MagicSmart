package com.magicsmart.core.link

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.magicsmart.core.engine.Env
import com.magicsmart.core.engine.ScanController
import com.magicsmart.core.report.ResultStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

class ServerState(val listening: Boolean, val address: String?, val port: Int, val serviceName: String?)
class ServerPrompt(val peerName: String, val sas: String)

/**
 * خادم الربط على التلفاز. يعمل فقط عندما يشغّله المستخدم صراحةً وتبقى شاشة الربط مفتوحة.
 * يدعم أمرين فقط بعد الاقتران: قراءة التقرير والإلغاء. لا يوجد أي أمر يغيّر إعدادات النظام.
 */
class LinkServer private constructor(private val ctx: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ServerState(false, null, 0, null))
    val state: StateFlow<ServerState> = _state.asStateFlow()
    private val _prompt = MutableStateFlow<ServerPrompt?>(null)
    val prompt: StateFlow<ServerPrompt?> = _prompt.asStateFlow()
    private val _event = MutableStateFlow<String?>(null)
    val event: StateFlow<String?> = _event.asStateFlow()
    private val _peers = MutableStateFlow(LinkStore.peers(ctx))
    val peers: StateFlow<List<PairedPeer>> = _peers.asStateFlow()

    private var server: ServerSocket? = null
    private var acceptJob: Job? = null
    private var regListener: NsdManager.RegistrationListener? = null
    private val busy = AtomicBoolean(false)
    private val open = Collections.synchronizedSet(HashSet<Socket>())
    @Volatile private var decision: CompletableDeferred<Boolean>? = null

    @Synchronized fun start() {
        if (server != null) return
        try {
            val ss = ServerSocket(0)
            server = ss
            val id = LinkStore.deviceId(ctx)
            val svc = "MAGICSMART-" + id.take(6)
            registerNsd(svc, ss.localPort, id)
            _state.value = ServerState(true, Env.localIpv4(), ss.localPort, svc)
            _event.value = "وضع الربط يعمل / Pairing mode is on"
            acceptJob = scope.launch {
                while (isActive) {
                    val s = try { ss.accept() } catch (e: IOException) { break }
                    if (open.size >= 8) { try { s.close() } catch (e: Exception) { }; continue }
                    launch { handle(s) }
                }
            }
        } catch (e: Exception) {
            _event.value = "تعذّر بدء الاستماع / Could not start listening: ${e.message}"
            stop()
        }
    }

    @Synchronized fun stop() {
        acceptJob?.cancel(); acceptJob = null
        try { server?.close() } catch (e: Exception) { }
        server = null
        open.toList().forEach { try { it.close() } catch (e: Exception) { } }
        unregisterNsd()
        decision?.complete(false)
        _prompt.value = null
        _state.value = ServerState(false, null, 0, null)
    }

    fun answerPrompt(accept: Boolean) { decision?.complete(accept) }

    fun removePeer(id: String) {
        LinkStore.remove(ctx, id)
        _peers.value = LinkStore.peers(ctx)
        _event.value = "أُلغي ربط الجهاز / Device unpaired"
    }

    private fun registerNsd(svc: String, port: Int, id: String) {
        val nsd = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
        val info = NsdServiceInfo().apply {
            serviceName = svc; serviceType = Wire.SERVICE_TYPE; setPort(port)
            setAttribute("id", id); setAttribute("name", LinkStore.deviceName(ctx))
            Env.localIpv4()?.let { setAttribute("ip", it) }
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) {}
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, code: Int) {}
            override fun onRegistrationFailed(i: NsdServiceInfo, code: Int) {
                _event.value = "تعذّر نشر الخدمة (رمز $code). استخدم العنوان اليدوي من الهاتف. / Could not advertise the service (code $code). Use the manual address on the phone."
            }
        }
        regListener = l
        try { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) } catch (e: Exception) { _event.value = "تعذّر نشر الخدمة / Could not advertise service: ${e.message}" }
    }

    private fun unregisterNsd() {
        val l = regListener ?: return
        regListener = null
        try { (ctx.getSystemService(Context.NSD_SERVICE) as NsdManager).unregisterService(l) } catch (e: Exception) { }
    }

    private suspend fun handle(sock: Socket) {
        open.add(sock)
        try {
            sock.soTimeout = 15_000
            val inp = DataInputStream(BufferedInputStream(sock.getInputStream()))
            val out = DataOutputStream(BufferedOutputStream(sock.getOutputStream()))
            val hello = Wire.readJson(inp)
            when (hello.optString("type")) {
                "pair" -> doPair(sock, inp, out, hello)
                "session" -> doSession(sock, inp, out, hello)
                else -> Wire.writeJson(out, Wire.error("بروتوكول غير معروف"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // اتصال مقطوع أو غير صالح: يُتجاهل
        } finally {
            open.remove(sock)
            try { sock.close() } catch (e: Exception) { }
        }
    }

    private suspend fun doPair(sock: Socket, inp: DataInputStream, out: DataOutputStream, hello: JSONObject) {
        if (!busy.compareAndSet(false, true)) {
            Wire.writeJson(out, Wire.error("التلفاز مشغول بطلب ربط آخر / TV is busy with another pairing request", "busy")); return
        }
        try {
            val peerName = hello.optString("name", "جهاز").take(40)
            val peerId = hello.getString("deviceId")
            val commit = Wire.unb64(hello.getString("commit"))

            val kp = LinkCrypto.newEcKeyPair()
            val nonceB = LinkCrypto.randomBytes(16)
            val pkB = kp.public.encoded
            Wire.writeJson(
                out, JSONObject().put("type", "offer").put("name", LinkStore.deviceName(ctx))
                    .put("deviceId", LinkStore.deviceId(ctx)).put("pk", Wire.b64(pkB)).put("nonce", Wire.b64(nonceB))
            )

            val reveal = Wire.readJson(inp)
            val pkA = Wire.unb64(reveal.getString("pk"))
            val nonceA = Wire.unb64(reveal.getString("nonce"))
            if (!LinkCrypto.eq(LinkCrypto.sha256(pkA, nonceA), commit)) {
                Wire.writeJson(out, Wire.error("فشل التحقق من الالتزام / Commitment verification failed")); _event.value = "طلب ربط مرفوض: التزام غير صحيح / Pairing request rejected: bad commitment"; return
            }
            val secret = LinkCrypto.ecdh(kp.private, pkA)
            val transcript = LinkCrypto.sha256(pkA, nonceA, pkB, nonceB)
            val sas = LinkCrypto.sas(pkA, nonceA, pkB, nonceB)

            // موافقة المستخدم على التلفاز نفسه
            val d = CompletableDeferred<Boolean>()
            decision = d
            _prompt.value = ServerPrompt(peerName, sas)
            val accepted = withTimeoutOrNull(120_000) { d.await() } ?: false
            _prompt.value = null
            decision = null
            if (!accepted) {
                Wire.writeJson(out, JSONObject().put("type", "reject")); _event.value = "رُفض طلب الربط من $peerName / Pairing request from $peerName rejected"; return
            }

            val macKey = LinkCrypto.hkdf(secret, transcript, "MS-CONFIRM", 32)
            Wire.writeJson(out, JSONObject().put("type", "confirm").put("mac", Wire.b64(LinkCrypto.hmac(macKey, "server".toByteArray(), transcript))))
            sock.soTimeout = 130_000
            val peerConf = Wire.readJson(inp)
            val ok = peerConf.optString("type") == "confirm" &&
                LinkCrypto.eq(Wire.unb64(peerConf.getString("mac")), LinkCrypto.hmac(macKey, "client".toByteArray(), transcript))
            if (!ok) { _event.value = "فشل تأكيد الاقتران من $peerName / Pairing confirmation from $peerName failed"; return }

            val psk = LinkCrypto.hkdf(secret, transcript, "MS-PSK", 32)
            LinkStore.add(ctx, PairedPeer(peerId, peerName, psk, System.currentTimeMillis()))
            _peers.value = LinkStore.peers(ctx)
            _event.value = "تم ربط $peerName / Paired with $peerName"
        } finally {
            _prompt.value = null
            decision = null
            busy.set(false)
        }
    }

    private fun doSession(sock: Socket, inp: DataInputStream, out: DataOutputStream, hello: JSONObject) {
        val peer = LinkStore.find(ctx, hello.getString("deviceId"))
        if (peer == null) {
            Wire.writeJson(out, Wire.error("هذا الجهاز غير مقترن / This device isn\u2019t paired", "unpaired")); return
        }
        val pkC = Wire.unb64(hello.getString("pk"))
        val nonceC = Wire.unb64(hello.getString("nonce"))
        val kp = LinkCrypto.newEcKeyPair()
        val nonceS = LinkCrypto.randomBytes(16)
        val pkS = kp.public.encoded
        val transcript = LinkCrypto.sha256(pkC, nonceC, pkS, nonceS)
        val authKey = LinkCrypto.authKey(peer.psk, transcript)
        val secret = LinkCrypto.ecdh(kp.private, pkC)

        Wire.writeJson(
            out, JSONObject().put("type", "accept").put("pk", Wire.b64(pkS)).put("nonce", Wire.b64(nonceS))
                .put("mac", Wire.b64(LinkCrypto.hmac(authKey, "server".toByteArray(), transcript)))
        )
        val auth = Wire.readJson(inp)
        if (!LinkCrypto.eq(Wire.unb64(auth.getString("mac")), LinkCrypto.hmac(authKey, "client".toByteArray(), transcript))) {
            _event.value = "فشلت مصادقة جهاز مقترن / Authentication failed for a paired device (${peer.name})"; return
        }
        val (c2s, s2c) = LinkCrypto.sessionKeys(secret, peer.psk, transcript)
        val ch = SecureChannel(inp, out, sendKey = s2c, recvKey = c2s)
        sock.soTimeout = 60_000
        while (true) {
            val msg = ch.recv()
            when (msg.optString("type")) {
                "ping" -> ch.send(JSONObject().put("type", "pong"))
                "get_report" -> {
                    val run = ScanController.get(ctx).currentRun()
                    ch.send(JSONObject().put("type", "report").put("run", ResultStore.toJson(run)))
                    _event.value = "أُرسل التقرير إلى ${peer.name} / Report sent to ${peer.name}"
                }
                "unpair" -> {
                    LinkStore.remove(ctx, peer.id); _peers.value = LinkStore.peers(ctx)
                    ch.send(JSONObject().put("type", "ok")); _event.value = "ألغى ${peer.name} الربط / ${peer.name} unpaired"; return
                }
                else -> ch.send(JSONObject().put("type", "error").put("message", "أمر غير مدعوم"))
            }
        }
    }

    companion object {
        @Volatile private var instance: LinkServer? = null
        fun get(context: Context): LinkServer = instance ?: synchronized(this) {
            instance ?: LinkServer(context.applicationContext).also { instance = it }
        }
    }
}
