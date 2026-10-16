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

    /** Test brzine kroz VPN (Cloudflare): preuzimanje 10 MB i slanje 3 MB. Vraća Mbps (-1 = greška). */
    suspend fun speed(onPhase: (String) -> Unit): Pair<Double, Double> = withContext(Dispatchers.IO) {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", ConfigBuilder.DIAG_PORT))
        fun mbps(bytes: Long, ns: Long) = if (ns <= 0) -1.0 else bytes * 8.0 / (ns / 1e9) / 1e6
        onPhase("down")
        val down = runCatching {
            val c = URL("https://speed.cloudflare.com/__down?bytes=10000000").openConnection(proxy) as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 20000
            val t = System.nanoTime(); var n = 0L
            c.inputStream.use { val b = ByteArray(64 * 1024); while (true) { val r = it.read(b); if (r < 0) break; n += r } }
            c.disconnect(); mbps(n, System.nanoTime() - t)
        }.getOrDefault(-1.0)
        onPhase("up")
        val up = runCatching {
            val data = ByteArray(3_000_000)
            val c = URL("https://speed.cloudflare.com/__up").openConnection(proxy) as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 20000; c.requestMethod = "POST"; c.doOutput = true; c.setFixedLengthStreamingMode(data.size)
            val t = System.nanoTime()
            c.outputStream.use { it.write(data) }
            c.responseCode; val ns = System.nanoTime() - t
            c.disconnect(); mbps(data.size.toLong(), ns)
        }.getOrDefault(-1.0)
        down to up
    }

    /** Radi li promet kroz VPN (za automatski izbor protokola). */
    suspend fun vpnWorks(): Boolean = viaVpn("http://cp.cloudflare.com/generate_204").first

    fun logTail(lines: Int = 80): String = runCatching {
        ConfigBuilder.logFile().readLines().takeLast(lines).joinToString("\n")
    }.getOrDefault("")
}
