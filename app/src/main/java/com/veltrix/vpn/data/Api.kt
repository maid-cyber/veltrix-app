package com.veltrix.vpn.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

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

    /** Ping: vrijeme TCP spajanja na server (ms), -1 ako ne odgovara. */
    suspend fun ping(host: String, port: Int): Int = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { s ->
                val t = System.nanoTime()
                s.connect(InetSocketAddress(host, port), 2500)
                ((System.nanoTime() - t) / 1_000_000).toInt().coerceAtLeast(1)
            }
        }.getOrDefault(-1)
    }
}
