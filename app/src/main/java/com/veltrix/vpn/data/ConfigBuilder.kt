package com.veltrix.vpn.data

import org.json.JSONArray
import org.json.JSONObject

/** sing-box konfiguracija za izabrani server. DNS ide kroz VPN na server (tamo radi blokada reklama). */
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

    fun build(server: Server, protocol: String): String {
        val config = JSONObject()
            .put("log", JSONObject().put("level", "warn"))
            .put(
                "dns",
                JSONObject()
                    .put(
                        "servers",
                        JSONArray()
                            .put(JSONObject().put("type", "udp").put("tag", "remote").put("server", "1.1.1.1").put("detour", "proxy"))
                            .put(JSONObject().put("type", "local").put("tag", "local")),
                    )
                    .put("final", "remote")
                    .put("strategy", "ipv4_only"),
            )
            .put(
                "inbounds",
                JSONArray().put(
                    JSONObject()
                        .put("type", "tun").put("tag", "tun-in")
                        .put("address", JSONArray().put("172.19.0.1/30"))
                        .put("mtu", 9000)
                        .put("auto_route", true)
                        .put("strict_route", true)
                        .put("stack", "mixed"),
                ),
            )
            .put(
                "outbounds",
                JSONArray()
                    .put(outboundFor(server, protocol))
                    .put(JSONObject().put("type", "direct").put("tag", "direct")),
            )
            .put(
                "route",
                JSONObject()
                    .put(
                        "rules",
                        JSONArray()
                            .put(JSONObject().put("action", "sniff"))
                            .put(JSONObject().put("protocol", "dns").put("action", "hijack-dns"))
                            .put(JSONObject().put("ip_is_private", true).put("outbound", "direct")),
                    )
                    .put("final", "proxy")
                    .put("auto_detect_interface", true)
                    .put("default_domain_resolver", "local"),
            )
        return config.toString(2)
    }
}
