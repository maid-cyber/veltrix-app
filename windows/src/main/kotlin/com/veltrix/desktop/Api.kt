package com.veltrix.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URL
import java.net.URLDecoder

object Api {
    /** Iz linka, linka sa /app ili veltrix://import?url=… napravi pretplatni link. */
    fun normalize(input: String): String? {
        var t = input.trim()
        if (t.startsWith("veltrix://")) {
            val q = runCatching { URI(t).rawQuery }.getOrNull() ?: return null
            t = q.split("&").firstOrNull { it.startsWith("url=") }?.removePrefix("url=")?.let { URLDecoder.decode(it, "UTF-8") } ?: return null
        }
        val m = Regex("(https?://[^\\s]+?/s/[A-Za-z0-9_-]{8,40})").find(t) ?: return null
        return m.groupValues[1]
    }

    suspend fun fetch(link: String): String = get("$link/app")

    suspend fun get(url: String, accept: String? = null): String = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "VeltrixVPN/Windows")
        if (accept != null) c.setRequestProperty("Accept", accept)
        try {
            val code = c.responseCode
            if (code == 404) throw IllegalStateException("not_found")
            if (code != 200) throw IllegalStateException("HTTP $code")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private suspend fun postJson(url: String, body: String): Pair<Int, org.json.JSONObject> = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 20000; c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("User-Agent", "VeltrixVPN/Windows")
        try {
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: "{}"
            code to runCatching { org.json.JSONObject(text) }.getOrDefault(org.json.JSONObject())
        } finally { c.disconnect() }
    }

    suspend fun trial(ref: String): String {
        val (code, j) = postJson("${BuildInfo.PANEL_URL}/trial", org.json.JSONObject().put("device", Store.deviceId).put("ref", ref).put("app", "windows").toString())
        if (code != 200) throw IllegalStateException(j.optString("error").ifBlank { "HTTP $code" })
        return j.getString("link")
    }

    suspend fun renew(link: String, planId: Int): String {
        val (code, j) = postJson("$link/renew", org.json.JSONObject().put("plan_id", planId).toString())
        if (code != 200) throw IllegalStateException(j.optString("error").ifBlank { "HTTP $code" })
        return j.getString("url")
    }

    private val vpnProxy get() = java.net.Proxy(java.net.Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", ConfigBuilder.DIAG_PORT))

    /** Prolazi li promet kroz VPN (preko lokalnog proxyja motora). */
    suspend fun vpnWorks(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("http://cp.cloudflare.com/generate_204").openConnection(vpnProxy) as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000
            try { c.responseCode in 200..299 } finally { c.disconnect() }
        }.getOrDefault(false)
    }

    /** Test brzine kroz VPN: (preuzimanje, slanje) u Mbps, -1 = greška. */
    suspend fun speed(onPhase: (String) -> Unit): Pair<Double, Double> = withContext(Dispatchers.IO) {
        fun mbps(bytes: Long, ns: Long) = if (ns <= 0) -1.0 else bytes * 8.0 / (ns / 1e9) / 1e6
        onPhase("down")
        val down = runCatching {
            val c = URL("https://speed.cloudflare.com/__down?bytes=25000000").openConnection(vpnProxy) as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 20000
            val t = System.nanoTime(); var n = 0L
            c.inputStream.use { val b = ByteArray(64 * 1024); while (true) { val r = it.read(b); if (r < 0) break; n += r } }
            c.disconnect(); mbps(n, System.nanoTime() - t)
        }.getOrDefault(-1.0)
        onPhase("up")
        val up = runCatching {
            val data = ByteArray(5_000_000)
            val c = URL("https://speed.cloudflare.com/__up").openConnection(vpnProxy) as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 20000; c.requestMethod = "POST"; c.doOutput = true; c.setFixedLengthStreamingMode(data.size)
            val t = System.nanoTime(); c.outputStream.use { it.write(data) }; c.responseCode
            val ns = System.nanoTime() - t; c.disconnect(); mbps(data.size.toLong(), ns)
        }.getOrDefault(-1.0)
        down to up
    }

    /** TCP vrijeme spajanja (ms), -1 ako ne odgovara ni iz 3 pokušaja. */
    suspend fun ping(host: String, port: Int): Int = withContext(Dispatchers.IO) {
        repeat(3) { n ->
            val ms = runCatching {
                Socket().use { s ->
                    val t = System.nanoTime()
                    s.connect(InetSocketAddress(host, port), if (n == 0) 3000 else 5000)
                    ((System.nanoTime() - t) / 1_000_000).toInt().coerceAtLeast(1)
                }
            }.getOrNull()
            if (ms != null) return@withContext ms
        }
        -1
    }
}
