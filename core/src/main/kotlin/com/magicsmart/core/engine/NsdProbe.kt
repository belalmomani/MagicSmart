package com.magicsmart.core.engine

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class NsdFound(val name: String, val host: String?, val port: Int)
internal class NsdResult(val found: List<NsdFound>, val error: String?)

/** اكتشاف خدمات mDNS الخاصة بـ ADB اللاسلكي. نتيجة لحظية: عدم الرصد لا يعني عدم الدعم. */
internal object NsdProbe {
    fun discover(ctx: Context, type: String, timeoutMs: Long): NsdResult {
        val nsd = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
        val found = CopyOnWriteArrayList<NsdFound>()
        val error = AtomicReference<String?>(null)
        val latch = CountDownLatch(1)
        val resolving = AtomicBoolean(false)

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                error.set("فشل بدء الاكتشاف (رمز $errorCode)")
                latch.countDown()
            }
            @Suppress("DEPRECATION")
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!resolving.compareAndSet(false, true)) return
                nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        found.add(NsdFound(info.serviceName, null, 0)); latch.countDown()
                    }
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        found.add(NsdFound(info.serviceName, info.host?.hostAddress, info.port)); latch.countDown()
                    }
                })
            }
        }
        nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
        try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } finally {
            try { nsd.stopServiceDiscovery(listener) } catch (e: Exception) { }
        }
        return NsdResult(found.toList(), error.get())
    }

    const val CONNECT = "_adb-tls-connect._tcp."
    const val PAIRING = "_adb-tls-pairing._tcp."

    fun connect(tc: TestContext): NsdResult = tc.cached("nsd:connect") { discover(tc.context, CONNECT, 4000) }
    fun pairing(tc: TestContext): NsdResult = tc.cached("nsd:pairing") { discover(tc.context, PAIRING, 3000) }
}
