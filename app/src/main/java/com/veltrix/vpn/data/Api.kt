package com.veltrix.vpn.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import org.json.JSONObject

object Api {
    /** Iz bilo čega što kupac zalijepi (link, link sa /app, veltrix://import?url=…) napravi pretplatni link. */
    fun normalize(input: String): String? {
        var t = input.trim()
        if (t.startsWith("veltrix://")) t = Uri.parse(t).getQueryParameter("url") ?: return null
        val m = Regex("(https?://[^\\s]+?/s/[A-Za-z0-9_-]{8,40})").find(t) ?: return null
        return m.groupValues[1]
    }

    suspend fun fetch(link: String): String = withContext(Dispatchers.IO) {
        val c = URL("$link/app").openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "VeltrixVPN/Android")
        try {
            val code = c.responseCode
            if (code == 404) throw IllegalStateException("not_found")
            if (code != 200) throw IllegalStateException("HTTP $code")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private suspend fun postJson(url: String, body: String): Pair<Int, JSONObject> = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 20000
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("User-Agent", "VeltrixVPN/Android")
        try {
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: "{}"
            code to runCatching { JSONObject(text) }.getOrDefault(JSONObject())
        } finally { c.disconnect() }
    }

    /** Probni nalog: vraća pretplatni link ili baca grešku sa porukom panela. */
    suspend fun trial(device: String, ref: String): String {
        val (code, j) = postJson("${com.veltrix.vpn.BuildConfig.PANEL_URL}/trial", JSONObject().put("device", device).put("ref", ref).put("app", "android").toString())
        if (code != 200) throw IllegalStateException(j.optString("error").ifBlank { "HTTP $code" })
        return j.getString("link")
    }

    /** Stripe plaćanje za produženje: vraća adresu stranice za plaćanje. */
    suspend fun renew(link: String, planId: Int): String {
        val (code, j) = postJson("$link/renew", JSONObject().put("plan_id", planId).toString())
        if (code != 200) throw IllegalStateException(j.optString("error").ifBlank { "HTTP $code" })
        return j.getString("url")
    }

    /** Ping: vrijeme TCP spajanja na server (ms), -1 ako ne odgovara ni iz 3 pokušaja. */
    suspend fun ping(host: String, port: Int): Int = withContext(Dispatchers.IO) {
        repeat(3) { n ->
            val ms = runCatching {
                Socket().use { s ->
                    val t = System.nanoTime()
                    s.connect(InetSocketAddress(host, port), if (n == 0) 3000 else 5000)
                    ((System.nanoTime() - t) / 1_000_000).toInt().coerceAtLeast(1)
                }
            }.onFailure { android.util.Log.w("Veltrix", "ping $host:$port", it) }.getOrNull()
            if (ms != null) return@withContext ms
        }
        -1
    }
}
