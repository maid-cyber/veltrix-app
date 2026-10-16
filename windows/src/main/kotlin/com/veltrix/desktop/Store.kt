package com.veltrix.desktop

import java.io.File
import java.util.Locale
import java.util.prefs.Preferences

/** Postavke aplikacije (Windows registar, HKCU\Software\JavaSoft\Prefs\com\veltrix). */
object Store {
    private val p: Preferences = Preferences.userRoot().node("com/veltrix/vpn")

    private fun str(k: String, d: String = "") = p.get(k, d)
    private fun setStr(k: String, v: String) { p.put(k, v); p.flush() }
    private fun bool(k: String, d: Boolean) = p.getBoolean(k, d)
    private fun setBool(k: String, v: Boolean) { p.putBoolean(k, v); p.flush() }
    private fun optBool(k: String): Boolean? = p.get(k, null)?.toBooleanStrictOrNull()
    private fun setOpt(k: String, v: Boolean?) { if (v == null) p.remove(k) else p.put(k, v.toString()); p.flush() }

    var link: String get() = str("link"); set(v) = setStr("link", v)
    var accountJson: String get() = str("account"); set(v) = setStr("account", v)
    var selectedServer: Int get() = p.getInt("server", -1); set(v) { p.putInt("server", v); p.flush() }
    /** auto | vless | shadowsocks */
    var protocol: String get() = str("protocol", "auto"); set(v) = setStr("protocol", v)
    var autoConnect: Boolean get() = bool("auto_connect", false); set(v) = setBool("auto_connect", v)
    var reconnect: Boolean get() = bool("reconnect", true); set(v) = setBool("reconnect", v)
    var favorites: Set<Int>
        get() = str("favorites").split(",").mapNotNull { it.toIntOrNull() }.toSet()
        set(v) = setStr("favorites", v.joinToString(","))
    var dnsProtect: Boolean? get() = optBool("dns_protect"); set(v) = setOpt("dns_protect", v)
    var ipLeak: Boolean? get() = optBool("ip_leak"); set(v) = setOpt("ip_leak", v)
    var webrtc: Boolean? get() = optBool("webrtc"); set(v) = setOpt("webrtc", v)
    /** programi (npr. chrome.exe) koji idu mimo VPN-a */
    var splitApps: Set<String>
        get() = str("split_apps").split("|").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        set(v) = setStr("split_apps", v.joinToString("|"))
    var splitOn: Boolean get() = bool("split_on", false); set(v) = setBool("split_on", v)
    var accent: String get() = str("accent", "crvena"); set(v) = setStr("accent", v)

    fun bosnian(): Boolean = Locale.getDefault().language in setOf("bs", "hr", "sr", "sh", "me")

    fun logout() {
        listOf("link", "account", "server").forEach { p.remove(it) }
        p.flush()
    }

    /** %APPDATA%\VeltrixVPN (konfiguracija i log motora) */
    val dataDir: File by lazy {
        val base = System.getenv("APPDATA")?.let { File(it) } ?: File(System.getProperty("user.home"), ".config")
        File(base, "VeltrixVPN").apply { mkdirs() }
    }
}
