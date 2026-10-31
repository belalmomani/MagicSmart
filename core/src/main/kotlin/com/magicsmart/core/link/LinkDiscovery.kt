package com.magicsmart.core.link

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

class DiscoveredTv(val serviceName: String, val name: String, val id: String?, val host: String, val port: Int)

/** اكتشاف تلفازات MAGIC SMART على الشبكة المحلية (mDNS)، مع حل الخدمات واحدةً تلو الأخرى. */
internal class LinkDiscovery(ctx: Context, private val onChange: (List<DiscoveredTv>) -> Unit) {
    private val nsd = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val lock = Any()
    private val found = LinkedHashMap<String, DiscoveredTv>()
    private val queue = java.util.ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var listener: NsdManager.DiscoveryListener? = null

    fun start() {
        synchronized(lock) {
            if (listener != null) return
            val l = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { synchronized(lock) { listener = null } }
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    synchronized(lock) { queue.add(serviceInfo) }
                    resolveNext()
                }
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    synchronized(lock) { found.remove(serviceInfo.serviceName) }
                    publish()
                }
            }
            listener = l
            try { nsd.discoverServices(Wire.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) } catch (e: Exception) { listener = null }
        }
    }

    fun stop() {
        synchronized(lock) {
            listener?.let { try { nsd.stopServiceDiscovery(it) } catch (e: Exception) { } }
            listener = null; queue.clear(); found.clear(); resolving = false
        }
        publish()
    }

    private fun publish() = onChange(synchronized(lock) { found.values.toList() })

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        val info = synchronized(lock) {
            if (resolving || queue.isEmpty()) return
            resolving = true
            queue.removeFirst()
        }
        try {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    synchronized(lock) { resolving = false }
                    resolveNext()
                }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val attrs = serviceInfo.attributes
                    val id = attrs["id"]?.toString(Charsets.UTF_8)
                    val name = attrs["name"]?.toString(Charsets.UTF_8) ?: serviceInfo.serviceName
                    val host = serviceInfo.host?.hostAddress
                    synchronized(lock) {
                        if (host != null) found[serviceInfo.serviceName] = DiscoveredTv(serviceInfo.serviceName, name, id, host, serviceInfo.port)
                        resolving = false
                    }
                    publish()
                    resolveNext()
                }
            })
        } catch (e: Exception) {
            synchronized(lock) { resolving = false }
        }
    }
}
