package com.magicsmart.core.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.UserManager
import java.net.Inet4Address
import java.net.NetworkInterface

internal object Env {
    fun debugRestricted(ctx: Context): Boolean {
        val um = ctx.getSystemService(Context.USER_SERVICE) as UserManager
        return um.hasUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES)
    }

    /** قراءة خاصية نظام عبر getprop (قراءة فقط). null إن لم يعرضها النظام للتطبيق. */
    fun prop(key: String): String? = try {
        val p = ProcessBuilder("getprop", key).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().use { it.readText().trim() }
        p.destroy()
        out.ifEmpty { null }
    } catch (e: Exception) { null }

    @Suppress("DEPRECATION")
    fun wifiIpv4(ctx: Context): Pair<Boolean, String?> {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.allNetworks.firstOrNull {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: return false to null
        val ip = cm.getLinkProperties(n)?.linkAddresses?.map { it.address }
            ?.firstOrNull { it is Inet4Address }?.hostAddress
        return true to ip
    }

    /** أول عنوان IPv4 محلي (Wi-Fi أو Ethernet) لعرضه على الشاشة؛ لا يُرسل لأي جهة. */
    fun localIpv4(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
    } catch (e: Exception) { null }
}
