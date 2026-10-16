package com.veltrix.vpn.data

import android.content.Context
import com.veltrix.vpn.App
import org.json.JSONObject
import java.util.Locale

/** Lokalno spremljeni podaci: link naloga, izabrani server, omiljeni, postavke. */
object Store {
    private val prefs by lazy { App.application.getSharedPreferences("veltrix", Context.MODE_PRIVATE) }

    var link: String
        get() = prefs.getString("link", "") ?: ""
        set(v) = prefs.edit().putString("link", v).apply()

    var accountJson: String
        get() = prefs.getString("account", "") ?: ""
        set(v) = prefs.edit().putString("account", v).apply()

    /** -1 = automatski (najbrži) */
    var selectedServer: Int
        get() = prefs.getInt("server", -1)
        set(v) = prefs.edit().putInt("server", v).apply()

    var selectedNameCache: String
        get() = prefs.getString("server_name", "") ?: ""
        set(v) = prefs.edit().putString("server_name", v).apply()

    /** auto | vless | shadowsocks */
    var protocol: String
        get() = prefs.getString("protocol", "auto") ?: "auto"
        set(v) = prefs.edit().putString("protocol", v).apply()

    var autoConnect: Boolean
        get() = prefs.getBoolean("auto_connect", false)
        set(v) = prefs.edit().putBoolean("auto_connect", v).apply()

    var favorites: Set<Int>
        get() = (prefs.getString("favorites", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }.toSet()
        set(v) = prefs.edit().putString("favorites", v.joinToString(",")).apply()

    // ---- sigurnost (null = kako je admin podesio za nove)
    private fun optBool(key: String): Boolean? = if (prefs.contains(key)) prefs.getBoolean(key, false) else null
    private fun setBool(key: String, v: Boolean?) = prefs.edit().apply { if (v == null) remove(key) else putBoolean(key, v) }.apply()
    var dnsProtect: Boolean?
        get() = optBool("dns_protect")
        set(v) = setBool("dns_protect", v)
    var ipLeak: Boolean?
        get() = optBool("ip_leak")
        set(v) = setBool("ip_leak", v)
    var webrtc: Boolean?
        get() = optBool("webrtc")
        set(v) = setBool("webrtc", v)

    /** Aplikacije koje idu mimo VPN-a (split tunneling). */
    var splitApps: Set<String>
        get() = prefs.getStringSet("split_apps", emptySet())?.toSet() ?: emptySet()
        set(v) = prefs.edit().putStringSet("split_apps", v).apply()
    var splitOn: Boolean
        get() = prefs.getBoolean("split_on", false)
        set(v) = prefs.edit().putBoolean("split_on", v).apply()

    var bootConnect: Boolean
        get() = prefs.getBoolean("boot_connect", false)
        set(v) = prefs.edit().putBoolean("boot_connect", v).apply()
    var notifyDrop: Boolean
        get() = prefs.getBoolean("notify_drop", true)
        set(v) = prefs.edit().putBoolean("notify_drop", v).apply()
    var notifyExpiry: Boolean
        get() = prefs.getBoolean("notify_expiry", true)
        set(v) = prefs.edit().putBoolean("notify_expiry", v).apply()
    var expiryNotifiedDay: Long
        get() = prefs.getLong("expiry_day", 0)
        set(v) = prefs.edit().putLong("expiry_day", v).apply()

    // ---- izgled
    /** crvena | plava | zelena | ljubicasta */
    var accent: String
        get() = prefs.getString("accent", "crvena") ?: "crvena"
        set(v) = prefs.edit().putString("accent", v).apply()
    var amoled: Boolean
        get() = prefs.getBoolean("amoled", false)
        set(v) = prefs.edit().putBoolean("amoled", v).apply()

    fun brand(context: Context? = null): String =
        runCatching { JSONObject(accountJson).optString("brand") }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Veltrix VPN"

    fun selectedName(context: Context? = null): String = selectedNameCache

    fun bosnian(): Boolean = Locale.getDefault().language in setOf("bs", "hr", "sr", "sh", "me")

    fun logout() {
        prefs.edit().remove("link").remove("account").remove("server").remove("server_name").apply()
    }
}
