package com.veltrix.desktop

import org.json.JSONArray
import org.json.JSONObject

data class SecurityOptions(
    val dnsProtect: Boolean = true,
    val ipLeak: Boolean = true,
    val webrtc: Boolean = true,
    /** programi (chrome.exe…) koji idu mimo VPN-a */
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

/** sing-box konfiguracija za Windows (isti princip kao Android aplikacija). */
object ConfigBuilder {
    const val API_PORT = 9097
    const val DIAG_PORT = 10809

    fun outboundFor(server: Server, protocol: String): JSONObject {
        val list = (0 until server.outbounds.length()).map { server.outbounds.getJSONObject(it) }
        val pick = when (protocol) {
            "shadowsocks" -> list.firstOrNull { it.optString("type") == "shadowsocks" }
            "vless" -> list.firstOrNull { it.optString("type") == "vless" }
            else -> null
        } ?: list.firstOrNull { it.optString("type") == "vless" } ?: list.first()
        return JSONObject(pick.toString()).put("tag", "proxy")
    }

    fun build(server: Server, protocol: String, sec: SecurityOptions, apiSecret: String): String {
        val remoteDns = JSONObject().put("type", if (sec.dnsProtect) "udp" else "https").put("tag", "remote").put("server", "1.1.1.1").put("detour", "proxy")
        val tun = JSONObject()
            .put("type", "tun").put("tag", "tun-in").put("interface_name", "AdiLink VPN")
            .put("address", JSONArray().put("172.19.0.1/30").apply { if (sec.ipLeak) put("fdfe:dcba:9876::1/126") })
            .put("mtu", 9000).put("auto_route", true).put("strict_route", true).put("stack", "mixed")
        // Windows: adresa VPN servera ide mimo tunela (inače se veza do servera vrti u krug kroz sam tunel → i/o timeout)
        val host = server.host.trim()
        if (Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host)) tun.put("route_exclude_address", JSONArray().put("$host/32"))
        val rules = JSONArray()
            .put(JSONObject().put("action", "sniff"))
            .put(JSONObject().put("protocol", "dns").put("action", "hijack-dns"))
        if (sec.exclude.isNotEmpty()) rules.put(JSONObject().put("process_name", JSONArray(sec.exclude.sorted())).put("outbound", "direct"))
        if (sec.ipLeak) rules.put(JSONObject().put("ip_version", 6).put("action", "reject"))
        if (sec.webrtc) {
            rules.put(JSONObject().put("network", "udp").put("port", JSONArray().put(3478).put(3479).put(5349)).put("port_range", JSONArray().put("19302:19309")).put("action", "reject"))
            rules.put(JSONObject().put("protocol", "stun").put("action", "reject"))
        }
        rules.put(JSONObject().put("ip_is_private", true).put("outbound", "direct"))
        return JSONObject()
            .put("log", JSONObject().put("level", "info").put("timestamp", true))
            .put("dns", JSONObject().put("servers", JSONArray().put(remoteDns).put(JSONObject().put("type", "local").put("tag", "local"))).put("final", "remote").put("strategy", "ipv4_only"))
            // lokalni proxy samo za test brzine i provjeru veze (sluša samo na 127.0.0.1)
            .put("inbounds", JSONArray().put(tun).put(JSONObject().put("type", "mixed").put("tag", "diag-in").put("listen", "127.0.0.1").put("listen_port", DIAG_PORT)))
            .put("outbounds", JSONArray().put(outboundFor(server, protocol)).put(JSONObject().put("type", "direct").put("tag", "direct")))
            .put("route", JSONObject().put("rules", rules).put("final", "proxy").put("auto_detect_interface", true).put("default_domain_resolver", "local"))
            // samo za brzinu i provjeru da motor radi (sluša samo na 127.0.0.1, sa tajnom)
            .put("experimental", JSONObject().put("clash_api", JSONObject().put("external_controller", "127.0.0.1:$API_PORT").put("secret", apiSecret)))
            .toString(2)
    }
}
