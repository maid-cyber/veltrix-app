package com.veltrix.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray

data class UpdateInfo(val build: Int, val version: String, val url: String)

/** Stanje aplikacije (isto kao MainViewModel na Androidu). */
class AppState(private val demo: Account? = null) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val account = MutableStateFlow(demo ?: Store.accountJson.takeIf { it.isNotBlank() }?.let { runCatching { Account.parse(it) }.getOrNull() })
    val loggedIn = MutableStateFlow(demo != null || Store.link.isNotBlank())
    val loading = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val pings = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val selected = MutableStateFlow(Store.selectedServer)
    val favorites = MutableStateFlow(Store.favorites)
    val protocol = MutableStateFlow(Store.protocol)
    val autoConnect = MutableStateFlow(Store.autoConnect)
    val reconnectOn = MutableStateFlow(Store.reconnect)
    val sec = MutableStateFlow(SecurityOptions.from(account.value))
    val splitOn = MutableStateFlow(Store.splitOn)
    val splitApps = MutableStateFlow(Store.splitApps)
    val status: StateFlow<Status> = Core.status
    val error: StateFlow<String?> = Core.error
    val update = MutableStateFlow<UpdateInfo?>(null)
    /** Ekran prijave: šta je admin uključio (probni nalog, kod prijatelja). */
    val loginCfg = MutableStateFlow(LoginConfig())
    /** Panel traži noviju verziju aplikacije (poruka), null = ova verzija radi. */
    val mustUpdate = MutableStateFlow<String?>(null)

    /** Prekidači iz panela: jezik i obavezno ažuriranje. */
    private fun applyFeatures(f: Features) {
        Store.adminLang = f.langDefault; Store.langLocked = !f.language
        mustUpdate.value = if (f.minWindows > BuildInfo.BUILD) f.updateMsg.ifBlank { t("This version no longer works. Please update.", "Ova verzija aplikacije više ne radi. Ažuriraj na novu verziju.") } else null
        if (mustUpdate.value != null && update.value == null) checkUpdate()
    }

    init {
        if (demo == null) {
            account.value?.let { applyFeatures(it.features) }
            if (loggedIn.value) refresh(silent = true)
            if (account.value?.features?.updates != false) checkUpdate()
            scope.launch {
                runCatching { LoginConfig.parse(Api.config()) }.getOrNull()?.let { c ->
                    loginCfg.value = c
                    if (!loggedIn.value) applyFeatures(c.features)
                }
            }
            scope.launch { while (true) { delay(10 * 60 * 1000L); if (loggedIn.value) refresh(silent = true) } }
            if (Store.autoConnect && loggedIn.value) scope.launch { delay(1500); connect() }
        }
    }

    fun login(input: String) {
        val link = Api.normalize(input) ?: run {
            message.value = t("That doesn't look like your access link.", "To nije ispravan pristupni link. Kopiraj ga ponovo.")
            return
        }
        scope.launch {
            loading.value = true
            try {
                val json = Api.fetch(link)
                val acc = Account.parse(json)
                Store.link = link; Store.accountJson = json
                account.value = acc; sec.value = SecurityOptions.from(acc)
                applyFeatures(acc.features)
                loggedIn.value = true
                pingAll()
            } catch (e: Exception) {
                message.value = if (e.message == "not_found") t("Account not found. Ask support for a new link.", "Nalog nije pronađen. Traži novi link od podrške.")
                else t("Can't reach the server: ", "Ne mogu dohvatiti nalog: ") + e.message
            } finally {
                loading.value = false
            }
        }
    }

    fun trial(ref: String) {
        scope.launch {
            loading.value = true
            try {
                val link = Api.trial(ref.trim())
                loading.value = false
                login(link)
            } catch (e: Exception) {
                message.value = e.message ?: t("Trial is not available.", "Probni nalog trenutno nije dostupan.")
            } finally { loading.value = false }
        }
    }

    fun renew(planId: Int) {
        val link = Store.link.ifBlank { return }
        scope.launch {
            loading.value = true
            try {
                openUrl(Api.renew(link, planId))
                message.value = t("Finish the payment in your browser, then come back.", "Završi plaćanje u browseru i vrati se ovdje.")
                // poslije plaćanja: osvježi nalog par puta
                repeat(6) { delay(20_000); refresh(silent = true) }
            } catch (e: Exception) { message.value = e.message } finally { loading.value = false }
        }
    }

    // automatski protokol: ako VLESS ne prolazi, probaj Shadowsocks
    private var fallbackSS = false
    /** Kad admin isključi izbor protokola, uvijek Automatski. */
    private fun chosenProtocol() = if (account.value?.features?.protocol == false) "auto" else protocol.value
    private fun effectiveProtocol() = if (fallbackSS && chosenProtocol() == "auto") "shadowsocks" else chosenProtocol()
    private fun watchConnection() {
        scope.launch {
            var n = 0
            while (status.value != Status.Started && n++ < 150) delay(100)
            if (status.value != Status.Started || chosenProtocol() != "auto" || fallbackSS) return@launch
            delay(2000)
            val ok = Api.vpnWorks() || run { delay(3000); Api.vpnWorks() }
            val server = currentServer()
            val hasSS = server != null && (0 until server.outbounds.length()).any { server.outbounds.getJSONObject(it).optString("type") == "shadowsocks" }
            if (!ok && hasSS && account.value?.features?.ssFallback != false) {
                fallbackSS = true
                message.value = t("VLESS is blocked on this network. Switching to Shadowsocks…", "VLESS ne prolazi na ovoj mreži. Prebacujem na Shadowsocks…")
                reconnectIfOn()
            } else if (!ok) {
                message.value = t("Connected, but traffic does not pass. See Diagnostics.", "Spojeno, ali promet ne prolazi. Pogledaj Dijagnostiku.")
            }
        }
    }

    fun refresh(silent: Boolean = false) {
        val link = Store.link.ifBlank { return }
        scope.launch {
            if (!silent) loading.value = true
            try {
                val json = Api.fetch(link)
                Store.accountJson = json
                val acc = Account.parse(json)
                account.value = acc; sec.value = SecurityOptions.from(acc)
                applyFeatures(acc.features)
                pingAll()
            } catch (e: Exception) {
                if (e.message == "not_found") message.value = t("Your access link is no longer valid.", "Tvoj pristupni link više ne važi.")
                else if (!silent) message.value = t("Can't refresh account.", "Ne mogu osvježiti nalog.")
            } finally {
                loading.value = false
            }
        }
    }

    fun pingAll() {
        val servers = account.value?.servers ?: return
        for (s in servers) scope.launch { val ms = Api.ping(s.host, s.port); pings.value = pings.value + (s.id to ms) }
    }

    fun currentServer(): Server? {
        val list = account.value?.servers ?: return null
        if (list.isEmpty()) return null
        list.firstOrNull { it.id == selected.value }?.let { return it }
        val p = pings.value
        val plain = list.filter { !it.isChain }.ifEmpty { list }
        return plain.filter { (p[it.id] ?: 9999) >= 0 }.minByOrNull { p[it.id] ?: 9999 } ?: plain.first()
    }

    fun connect() {
        val acc = account.value ?: return run { message.value = t("Log in first.", "Prvo se prijavi.") }
        mustUpdate.value?.let { message.value = it; return }
        if (!acc.canConnect) {
            message.value = when (acc.status) {
                "expired", "grace" -> t("Your subscription has expired.", "Pretplata je istekla. Produži je da se možeš spojiti.")
                "limited" -> t("Your data limit is used up.", "Potrošio si sav promet.")
                "devices" -> t("Too many devices. Try again in 15 minutes.", "Previše uređaja. Pokušaj ponovo za 15 minuta.")
                else -> t("Your account is disabled.", "Nalog je ugašen. Javi se podršci.")
            }
            return
        }
        val server = currentServer() ?: return run { message.value = t("No servers available.", "Trenutno nema dostupnih servera.") }
        val s = SecurityOptions.from(acc)
        val proto = effectiveProtocol()
        Core.start { secret -> ConfigBuilder.build(server, proto, s, secret) }
        watchConnection()
    }

    fun disconnect() { fallbackSS = false; Core.stop() }
    fun toggle() = when (status.value) { Status.Started -> disconnect(); Status.Stopped -> connect(); else -> {} }

    private fun reconnectIfOn() {
        if (status.value != Status.Started) return
        Core.stop()
        scope.launch {
            var n = 0
            while (status.value != Status.Stopped && n++ < 50) delay(100)
            connect()
        }
    }

    fun select(id: Int) { fallbackSS = false; selected.value = id; Store.selectedServer = id; reconnectIfOn() }
    fun toggleFavorite(id: Int) { val f = favorites.value.toMutableSet(); if (!f.add(id)) f.remove(id); favorites.value = f; Store.favorites = f }
    fun setProtocol(p: String) { fallbackSS = false; protocol.value = p; Store.protocol = p; reconnectIfOn() }
    fun setAutoConnect(v: Boolean) { autoConnect.value = v; Store.autoConnect = v }
    fun setReconnect(v: Boolean) { reconnectOn.value = v; Store.reconnect = v }
    private fun secChanged() { sec.value = SecurityOptions.from(account.value); reconnectIfOn() }
    fun setDns(v: Boolean) { Store.dnsProtect = v; secChanged() }
    fun setIpLeak(v: Boolean) { Store.ipLeak = v; secChanged() }
    fun setWebrtc(v: Boolean) { Store.webrtc = v; secChanged() }
    fun setSplitOn(v: Boolean) { Store.splitOn = v; splitOn.value = v; secChanged() }
    fun setSplitApps(v: Set<String>) { Store.splitApps = v; splitApps.value = v; secChanged() }

    fun logout() {
        Core.stop()
        Store.logout()
        account.value = null; loggedIn.value = false; selected.value = -1
    }

    // ------------------------------------------------------------ nova verzija (GitHub Releases, tag win-N)
    fun checkUpdate(manual: Boolean = false) {
        scope.launch {
            val u = runCatching {
                val arr = JSONArray(Api.get("https://api.github.com/repos/${BuildInfo.UPDATE_REPO}/releases?per_page=20", "application/vnd.github+json"))
                (0 until arr.length()).map { arr.getJSONObject(it) }
                    .filter { !it.optBoolean("draft") && it.optString("tag_name").startsWith("win-") }
                    .mapNotNull { r ->
                        val b = r.optString("tag_name").removePrefix("win-").toIntOrNull() ?: return@mapNotNull null
                        val a = r.optJSONArray("assets") ?: return@mapNotNull null
                        val exe = (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".exe") } ?: return@mapNotNull null
                        UpdateInfo(b, "1.0.$b", exe.optString("browser_download_url"))
                    }.maxByOrNull { it.build }?.takeIf { it.build > BuildInfo.BUILD }
            }.getOrNull()
            update.value = u
            if (manual && u == null) message.value = t("You have the latest version.", "Imaš najnoviju verziju.")
        }
    }
}
