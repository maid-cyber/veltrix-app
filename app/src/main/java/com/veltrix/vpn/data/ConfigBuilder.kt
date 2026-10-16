package com.veltrix.vpn.data

import org.json.JSONArray
import org.json.JSONObject

/** Sigurnosne opcije koje važe za ovu vezu (admin ih dozvoli u panelu, kupac uključi u aplikaciji). */
data class SecurityOptions(
    /** DNS ide na server (tamo rade blokade reklama i opasnih stranica); inače šifrovani 1.1.1.1 bez filtera */
    val dnsProtect: Boolean = true,
    /** IPv6 i promet mimo tunela se blokira */
    val ipLeak: Boolean = true,
    /** STUN (WebRTC) upiti se blokiraju */
    val webrtc: Boolean = true,
    /** aplikacije koje idu mimo VPN-a */
    val exclude: Set<String> = emptySet(),
) {
    companion object {
        fun from(acc: Account?): SecurityOptions {
            val f = acc?.features ?: Features()
            return SecurityOptions(
                dnsProtect = if (f.dns) Store.dnsProtect ?: f.dnsDefault else true,
                ipLeak = if (f.ipleak) Store.ipLeak ?: f.ipleakDefault else false,
                webrtc = if (f.webrtc) Store.webrtc ?: f.webrtcDefault else false,
                exclude = if (f.split && Store.splitOn) Store.splitApps else emptySet(),
            )
        }
    }
}

/** sing-box konfiguracija za izabrani server. */
object ConfigBuilder {
    fun outboundFor(server: Server, protocol: String): JSONObject {
        val list = (0 until server.outbounds.length()).map { server.outbounds.getJSONObject(it) }
        val pick = when (protocol) {
            "shadowsocks" -> list.firstOrNull { it.optString("type") == "shadowsocks" }
            "vless" -> list.firstOrNull { it.optString("type") == "vless" }
            else -> null
        } ?: list.firstOrNull { it.optString("type") == "vless" } ?: list.first()
        return JSONObject(pick.toString()).put("tag", "proxy")
    }

    fun build(server: Server, protocol: String, sec: SecurityOptions = SecurityOptions()): String {
        // DNS: običan UDP na port 53 server presretne i filtrira; HTTPS (443) prolazi bez filtera
        val remoteDns = if (sec.dnsProtect) {
            JSONObject().put("type", "udp").put("tag", "remote").put("server", "1.1.1.1").put("detour", "proxy")
        } else {
            JSONObject().put("type", "https").put("tag", "remote").put("server", "1.1.1.1").put("detour", "proxy")
        }
        val tun = JSONObject()
            .put("type", "tun").put("tag", "tun-in")
            .put("address", JSONArray().put("172.19.0.1/30").apply { if (sec.ipLeak) put("fdfe:dcba:9876::1/126") })
            .put("mtu", 9000)
            .put("auto_route", true)
            .put("strict_route", true)
            .put("stack", "mixed")
        if (sec.exclude.isNotEmpty()) tun.put("exclude_package", JSONArray(sec.exclude.sorted()))

        val rules = JSONArray()
            .put(JSONObject().put("action", "sniff"))
            .put(JSONObject().put("protocol", "dns").put("action", "hijack-dns"))
        // IP leak: IPv6 uhvaćen u tunel i odbijen (server radi samo IPv4), da ne ide mimo VPN-a
        if (sec.ipLeak) rules.put(JSONObject().put("ip_version", 6).put("action", "reject"))
        // WebRTC: STUN upiti preko kojih stranice traže IP adrese
        if (sec.webrtc) {
            rules.put(
                JSONObject().put("network", "udp")
                    .put("port", JSONArray().put(3478).put(3479).put(5349))
                    .put("port_range", JSONArray().put("19302:19309"))
                    .put("action", "reject"),
            )
            rules.put(JSONObject().put("protocol", "stun").put("action", "reject"))
        }
        rules.put(JSONObject().put("ip_is_private", true).put("outbound", "direct"))

        val config = JSONObject()
            .put("log", JSONObject().put("level", "warn"))
            .put(
                "dns",
                JSONObject()
                    .put("servers", JSONArray().put(remoteDns).put(JSONObject().put("type", "local").put("tag", "local")))
                    .put("final", "remote")
                    .put("strategy", "ipv4_only"),
            )
            .put("inbounds", JSONArray().put(tun))
            .put(
                "outbounds",
                JSONArray()
                    .put(outboundFor(server, protocol))
                    .put(JSONObject().put("type", "direct").put("tag", "direct")),
            )
            .put(
                "route",
                JSONObject()
                    .put("rules", rules)
                    .put("final", "proxy")
                    .put("auto_detect_interface", true)
                    .put("default_domain_resolver", "local"),
            )
        return config.toString(2)
    }
}
