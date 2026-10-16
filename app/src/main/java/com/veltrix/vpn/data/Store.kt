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

    fun brand(context: Context? = null): String =
        runCatching { JSONObject(accountJson).optString("brand") }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Veltrix VPN"

    fun selectedName(context: Context? = null): String = selectedNameCache

    fun bosnian(): Boolean = Locale.getDefault().language in setOf("bs", "hr", "sr", "sh", "me")

    fun logout() {
        prefs.edit().remove("link").remove("account").remove("server").remove("server_name").apply()
    }
}
