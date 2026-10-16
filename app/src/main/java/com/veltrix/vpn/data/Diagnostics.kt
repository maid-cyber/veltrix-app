package com.veltrix.vpn.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL

/** Provjere za ekran Dijagnostika: gdje tačno zapinje veza. */
object Diagnostics {
    data class Result(val name: String, val ok: Boolean, val detail: String)

    private suspend fun tcp(host: String, port: Int): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { s ->
                val t = System.nanoTime()
                s.connect(InetSocketAddress(host, port), 5000)
                true to "${(System.nanoTime() - t) / 1_000_000} ms"
            }
        }.getOrElse { false to (it.javaClass.simpleName + ": " + (it.message ?: "")) }
    }

    private suspend fun viaVpn(url: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL(url).openConnection(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", ConfigBuilder.DIAG_PORT))) as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 10000
            try {
                val code = c.responseCode
                val body = runCatching { c.inputStream.bufferedReader().use { it.readText().trim().take(60) } }.getOrDefault("")
                (code in 200..299) to "HTTP $code ${body}".trim()
            } finally { c.disconnect() }
        }.getOrElse { false to (it.javaClass.simpleName + ": " + (it.message ?: "")) }
    }

    suspend fun run(server: Server?, connected: Boolean): List<Result> {
        val out = mutableListOf<Result>()
        val bs = Store.bosnian()
        tcp("1.1.1.1", 443).let { out += Result(if (bs) "Internet na telefonu" else "Phone internet", it.first, it.second) }
        if (server != null) {
            tcp(server.host, server.port).let {
                out += Result((if (bs) "VPN server " else "VPN server ") + "${server.host}:${server.port}", it.first, it.second)
            }
        }
        if (connected) {
            viaVpn("http://cp.cloudflare.com/generate_204").let { out += Result(if (bs) "Promet kroz VPN" else "Traffic through VPN", it.first, it.second) }
            viaVpn("https://api.ipify.org").let { out += Result(if (bs) "Tvoja IP preko VPN-a" else "Your IP via VPN", it.first, it.second) }
        }
        return out
    }

    fun logTail(lines: Int = 80): String = runCatching {
        ConfigBuilder.logFile().readLines().takeLast(lines).joinToString("\n")
    }.getOrDefault("")
}
