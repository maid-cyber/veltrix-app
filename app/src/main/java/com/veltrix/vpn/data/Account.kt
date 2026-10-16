package com.veltrix.vpn.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class Server(
    val id: Int,
    val name: String,
    val city: String,
    val country: String,
    val host: String,
    val port: Int,
    val outbounds: JSONArray,
    /** server | double | multihop */
    val kind: String = "server",
    val tags: List<String> = emptyList(),
    /** za lance: države/gradovi redom (ulaz → izlaz) */
    val hops: List<Pair<String, String>> = emptyList(),
) {
    val isChain get() = kind != "server"
    val route: String get() = hops.joinToString("  →  ") { flagOf(it.first) + " " + it.second }

    val countryName: String
        get() = if (country.length == 2) Locale("", country).getDisplayCountry(if (Store.bosnian()) Locale("bs") else Locale.ENGLISH).ifBlank { name } else name

    val flag: String get() = flagOf(country)

    fun has(type: String) = (0 until outbounds.length()).any { outbounds.getJSONObject(it).optString("type") == type }
}

fun flagOf(country: String): String = if (country.length == 2 && country.all { it in 'A'..'Z' }) {
    String(Character.toChars(0x1F1E6 + (country[0] - 'A'))) + String(Character.toChars(0x1F1E6 + (country[1] - 'A')))
} else "🌐"

/** Funkcije koje je admin uključio u panelu (Postavke → Veltrix aplikacija). */
data class Features(
    val killswitch: Boolean = true, val dns: Boolean = true, val dnsDefault: Boolean = true, val split: Boolean = true,
    val ipleak: Boolean = true, val ipleakDefault: Boolean = true, val webrtc: Boolean = true, val webrtcDefault: Boolean = true,
    val boot: Boolean = true, val history: Boolean = true, val dnsFilters: List<String> = emptyList(),
)

data class HistoryItem(val ts: Long, val text: String)

data class Account(
    val brand: String,
    val support: String,
    val announce: String,
    val username: String,
    val plan: String,
    val status: String,
    val canConnect: Boolean,
    val expiresAt: Long,
    val createdAt: Long,
    val trafficUsed: Long,
    val trafficLimit: Long,
    val devices: Int,
    val servers: List<Server>,
    val devicesActive: Int = 0,
    val features: Features = Features(),
    val history: List<HistoryItem> = emptyList(),
) {
    companion object {
        fun parse(json: String): Account {
            val o = JSONObject(json)
            val arr = o.optJSONArray("servers") ?: JSONArray()
            val servers = (0 until arr.length()).map { i ->
                val s = arr.getJSONObject(i)
                Server(
                    id = s.getInt("id"), name = s.optString("name"), city = s.optString("city"), country = s.optString("country"),
                    host = s.optString("host"), port = s.optInt("port", 443), outbounds = s.optJSONArray("outbounds") ?: JSONArray(),
                    kind = s.optString("kind", "server").ifBlank { "server" },
                    tags = s.optJSONArray("tags").strings(),
                    hops = s.optJSONArray("hops")?.let { h -> (0 until h.length()).map { h.getJSONObject(it).let { x -> x.optString("country") to x.optString("city") } } } ?: emptyList(),
                )
            }
            return Account(
                brand = o.optString("brand", "Veltrix VPN"), support = o.optString("support"), announce = o.optString("announce"),
                username = o.optString("username"), plan = o.optString("plan"), status = o.optString("status"),
                canConnect = o.optBoolean("can_connect"), expiresAt = o.optLong("expires_at"), createdAt = o.optLong("created_at"),
                trafficUsed = o.optLong("traffic_used"), trafficLimit = o.optLong("traffic_limit"), devices = o.optInt("devices", 1),
                servers = servers,
                devicesActive = o.optInt("devices_active"),
                features = o.optJSONObject("features")?.let { f ->
                    Features(
                        killswitch = f.optBoolean("killswitch", true), dns = f.optBoolean("dns", true), dnsDefault = f.optBoolean("dns_default", true),
                        split = f.optBoolean("split", true), ipleak = f.optBoolean("ipleak", true), ipleakDefault = f.optBoolean("ipleak_default", true),
                        webrtc = f.optBoolean("webrtc", true), webrtcDefault = f.optBoolean("webrtc_default", true), boot = f.optBoolean("boot", true),
                        history = f.optBoolean("history", true), dnsFilters = f.optJSONArray("dns_filters").strings(),
                    )
                } ?: Features(),
                history = o.optJSONArray("history")?.let { h -> (0 until h.length()).map { h.getJSONObject(it).let { x -> HistoryItem(x.optLong("ts"), x.optString("text")) } } } ?: emptyList(),
            )
        }
    }
}

private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
