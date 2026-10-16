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
