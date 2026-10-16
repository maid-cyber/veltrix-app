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
) {
    val countryName: String
        get() = if (country.length == 2) Locale("", country).getDisplayCountry(if (Store.bosnian()) Locale("bs") else Locale.ENGLISH).ifBlank { name } else name

    val flag: String
        get() = if (country.length == 2 && country.all { it in 'A'..'Z' }) {
            String(Character.toChars(0x1F1E6 + (country[0] - 'A'))) + String(Character.toChars(0x1F1E6 + (country[1] - 'A')))
        } else "🌐"

    fun has(type: String) = (0 until outbounds.length()).any { outbounds.getJSONObject(it).optString("type") == type }
}

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
                )
            }
            return Account(
                brand = o.optString("brand", "Veltrix VPN"), support = o.optString("support"), announce = o.optString("announce"),
                username = o.optString("username"), plan = o.optString("plan"), status = o.optString("status"),
                canConnect = o.optBoolean("can_connect"), expiresAt = o.optLong("expires_at"), createdAt = o.optLong("created_at"),
                trafficUsed = o.optLong("traffic_used"), trafficLimit = o.optLong("traffic_limit"), devices = o.optInt("devices", 1),
                servers = servers,
            )
        }
    }
}
