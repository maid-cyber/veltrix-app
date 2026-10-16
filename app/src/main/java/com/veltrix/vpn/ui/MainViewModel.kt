package com.veltrix.vpn.ui

import android.net.TrafficStats
import android.os.Process
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.veltrix.vpn.App
import com.veltrix.vpn.bg.BoxService
import com.veltrix.vpn.bg.Status
import com.veltrix.vpn.bg.VpnState
import com.veltrix.vpn.data.Account
import com.veltrix.vpn.data.Api
import com.veltrix.vpn.data.ConfigBuilder
import com.veltrix.vpn.data.Notifier
import com.veltrix.vpn.data.SecurityOptions
import com.veltrix.vpn.data.Server
import com.veltrix.vpn.data.Store
import com.veltrix.vpn.data.UpdateInfo
import com.veltrix.vpn.data.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Speed(val down: Long = 0, val up: Long = 0)

class MainViewModel : ViewModel() {
    val account = MutableStateFlow(Store.accountJson.takeIf { it.isNotBlank() }?.let { runCatching { Account.parse(it) }.getOrNull() })
    val loggedIn = MutableStateFlow(Store.link.isNotBlank())
    val loading = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val pings = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val selected = MutableStateFlow(Store.selectedServer)
    val favorites = MutableStateFlow(Store.favorites)
    val protocol = MutableStateFlow(Store.protocol)
    val autoConnect = MutableStateFlow(Store.autoConnect)
    val speed = MutableStateFlow(Speed())
    val status: StateFlow<Status> = VpnState.status
    val error: StateFlow<String?> = VpnState.error
    val sec = MutableStateFlow(SecurityOptions.from(account.value))
    val splitOn = MutableStateFlow(Store.splitOn)
    val bootConnect = MutableStateFlow(Store.bootConnect)
    val notifyDrop = MutableStateFlow(Store.notifyDrop)
    val notifyExpiry = MutableStateFlow(Store.notifyExpiry)
    val alwaysOn: StateFlow<Boolean> = VpnState.alwaysOn
    val lockdown: StateFlow<Boolean> = VpnState.lockdown
    val update = MutableStateFlow<UpdateInfo?>(null)
    val updateDismissed = MutableStateFlow(false)
    /** null = ne skida se; 0..1 = napredak skidanja */
    val updateProgress = MutableStateFlow<Float?>(null)

    init {
        checkUpdate()
        if (loggedIn.value) refresh(silent = true)
        // brzina: bajtovi koje je aplikacija (VPN motor) prenijela u zadnjoj sekundi
        viewModelScope.launch {
            val uid = Process.myUid()
            var rx = TrafficStats.getUidRxBytes(uid)
            var tx = TrafficStats.getUidTxBytes(uid)
            while (true) {
                delay(1000)
                val nrx = TrafficStats.getUidRxBytes(uid)
                val ntx = TrafficStats.getUidTxBytes(uid)
                speed.value = if (status.value == Status.Started) Speed((nrx - rx).coerceAtLeast(0), (ntx - tx).coerceAtLeast(0)) else Speed()
                rx = nrx
                tx = ntx
            }
        }
        // nalog se osvježava svakih 10 minuta
        viewModelScope.launch {
            while (true) {
                delay(10 * 60 * 1000L)
                if (loggedIn.value) refresh(silent = true)
            }
        }
    }

    fun login(input: String, onDone: () -> Unit = {}) {
        val link = Api.normalize(input)
        if (link == null) {
            message.value = t("That doesn't look like your access link. Copy it again or scan the QR code.", "To nije ispravan pristupni link. Kopiraj ga ponovo ili skeniraj QR kod.")
            return
        }
        viewModelScope.launch {
            loading.value = true
            try {
                val json = Api.fetch(link)
                val acc = Account.parse(json)
                Store.link = link
                Store.accountJson = json
                account.value = acc
                sec.value = SecurityOptions.from(acc)
                loggedIn.value = true
                message.value = null
                pingAll()
                onDone()
            } catch (e: Exception) {
                message.value = if (e.message == "not_found") {
                    t("Account not found. Ask support for a new link.", "Nalog nije pronađen. Traži novi link od podrške.")
                } else {
                    t("Can't reach the server. Check your internet.", "Ne mogu dohvatiti nalog. Provjeri internet.") + " (${e.message})"
                }
            } finally {
                loading.value = false
            }
        }
    }

    // ------------------------------------------------------------ probni nalog i produženje
    fun trial(ref: String) {
        viewModelScope.launch {
            loading.value = true
            try {
                val id = android.provider.Settings.Secure.getString(App.application.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: java.util.UUID.randomUUID().toString()
                val device = "a-" + java.security.MessageDigest.getInstance("SHA-256").digest(("veltrix:" + id).toByteArray()).joinToString("") { "%02x".format(it) }.take(40)
                val link = Api.trial(device, ref.trim())
                Store.refCodeInput = ref.trim()
                loading.value = false
                login(link)
            } catch (e: Exception) {
                message.value = e.message ?: t("Trial is not available.", "Probni nalog trenutno nije dostupan.")
            } finally {
                loading.value = false
            }
        }
    }

    /** Otvori Stripe plaćanje za plan; onUrl dobija adresu za browser. */
    fun renew(planId: Int, onUrl: (String) -> Unit) {
        val link = Store.link.ifBlank { return }
        viewModelScope.launch {
            loading.value = true
            try { onUrl(Api.renew(link, planId)) } catch (e: Exception) { message.value = e.message } finally { loading.value = false }
        }
    }

    // ------------------------------------------------------------ automatski protokol: ako VLESS ne prolazi, probaj Shadowsocks
    private var fallbackSS = false
    private fun effectiveProtocol() = if (fallbackSS && protocol.value == "auto") "shadowsocks" else protocol.value
    private fun watchConnection() {
        viewModelScope.launch {
            var n = 0
            while (status.value != Status.Started && n++ < 100) delay(100)
            if (status.value != Status.Started || protocol.value != "auto" || fallbackSS) return@launch
            delay(2500)
            if (status.value != Status.Started) return@launch
            val ok = com.veltrix.vpn.data.Diagnostics.vpnWorks() || run { delay(3000); com.veltrix.vpn.data.Diagnostics.vpnWorks() }
            val server = currentServer()
            if (!ok && server != null && server.has("shadowsocks")) {
                fallbackSS = true
                message.value = t("VLESS is blocked on this network. Switching to Shadowsocks…", "VLESS ne prolazi na ovoj mreži. Prebacujem na Shadowsocks…")
                reconnect()
            } else if (!ok) {
                message.value = t("Connected, but traffic does not pass. Open Account → Connection test.", "Spojeno, ali promet ne prolazi. Otvori Nalog → Dijagnostika veze.")
            }
        }
    }

    fun refresh(silent: Boolean = false) {
        val link = Store.link
        if (link.isBlank()) return
        viewModelScope.launch {
            if (!silent) loading.value = true
            try {
                val json = Api.fetch(link)
                Store.accountJson = json
                val acc = Account.parse(json)
                account.value = acc
                sec.value = SecurityOptions.from(acc)
                Notifier.expiry(App.application, acc)
                pingAll()
            } catch (e: Exception) {
                if (e.message == "not_found") {
                    message.value = t("Your access link is no longer valid.", "Tvoj pristupni link više ne važi.")
                } else if (!silent) {
                    message.value = t("Can't refresh account.", "Ne mogu osvježiti nalog.")
                }
            } finally {
                loading.value = false
            }
        }
    }

    fun pingAll() {
        val servers = account.value?.servers ?: return
        viewModelScope.launch {
            for (s in servers) {
                launch {
                    val ms = Api.ping(s.host, s.port)
                    pings.value = pings.value + (s.id to ms)
                }
            }
        }
    }

    /** Server koji se koristi: izabrani, ili najbrži ako je izabrano "Automatski". */
    fun currentServer(): Server? {
        val list = account.value?.servers ?: return null
        if (list.isEmpty()) return null
        list.firstOrNull { it.id == selected.value }?.let { return it }
        val p = pings.value
        val plain = list.filter { !it.isChain }.ifEmpty { list }
        return plain.filter { (p[it.id] ?: 9999) >= 0 }.minByOrNull { p[it.id] ?: 9999 } ?: plain.first()
    }

    fun select(id: Int) {
        fallbackSS = false
        selected.value = id
        Store.selectedServer = id
        if (status.value == Status.Started) reconnect()
    }

    fun toggleFavorite(id: Int) {
        val f = favorites.value.toMutableSet()
        if (!f.add(id)) f.remove(id)
        favorites.value = f
        Store.favorites = f
    }

    fun setProtocol(p: String) {
        fallbackSS = false
        protocol.value = p
        Store.protocol = p
        if (status.value == Status.Started) reconnect()
    }

    // ------------------------------------------------------------ sigurnost (promjena odmah ponovo spoji)
    private fun secChanged() {
        sec.value = SecurityOptions.from(account.value)
        if (status.value == Status.Started) reconnect()
    }
    fun setDns(v: Boolean) { Store.dnsProtect = v; secChanged() }
    fun setIpLeak(v: Boolean) { Store.ipLeak = v; secChanged() }
    fun setWebrtc(v: Boolean) { Store.webrtc = v; secChanged() }
    fun setSplitOn(v: Boolean) { Store.splitOn = v; splitOn.value = v; secChanged() }
    fun setSplitApps(apps: Set<String>) { Store.splitApps = apps; secChanged() }
    fun setBoot(v: Boolean) { Store.bootConnect = v; bootConnect.value = v }
    fun setNotifyDrop(v: Boolean) { Store.notifyDrop = v; notifyDrop.value = v }
    fun setNotifyExpiry(v: Boolean) { Store.notifyExpiry = v; notifyExpiry.value = v }

    fun setAutoConnect(v: Boolean) {
        autoConnect.value = v
        Store.autoConnect = v
    }

    /** Priprema konfiguraciju; vraća false ako se ne može spojiti (poruka u message). */
    fun prepare(): Boolean {
        val acc = account.value
        if (acc == null) { message.value = t("Log in first.", "Prvo se prijavi."); return false }
        if (!acc.canConnect) {
            message.value = when (acc.status) {
                "expired", "grace" -> t("Your subscription has expired. Renew it to connect.", "Pretplata je istekla. Produži je da se možeš spojiti.")
                "limited" -> t("Your data limit is used up.", "Potrošio si sav promet.")
                "devices" -> t("Too many devices. Try again in 15 minutes.", "Previše uređaja. Pokušaj ponovo za 15 minuta.")
                else -> t("Your account is disabled. Contact support.", "Nalog je ugašen. Javi se podršci.")
            }
            return false
        }
        val server = currentServer()
        if (server == null) { message.value = t("No servers available right now.", "Trenutno nema dostupnih servera."); return false }
        BoxService.configFile(App.application).writeText(ConfigBuilder.build(server, effectiveProtocol(), SecurityOptions.from(acc)))
        Store.selectedNameCache = if (server.isChain) server.name else "${server.flag} ${server.city}"
        return true
    }

    fun start() {
        if (prepare()) { BoxService.start(App.application); watchConnection() }
    }

    fun stop() { fallbackSS = false; BoxService.stop(App.application) }

    private fun reconnect() {
        viewModelScope.launch {
            BoxService.stop(App.application)
            withContext(Dispatchers.Default) {
                var n = 0
                while (status.value != Status.Stopped && n++ < 50) delay(100)
            }
            start()
        }
    }

    // ------------------------------------------------------------ ažuriranje aplikacije
    fun checkUpdate(manual: Boolean = false) {
        viewModelScope.launch {
            val u = runCatching { Updater.check() }.getOrNull()
            update.value = u
            if (u != null) updateDismissed.value = false
            if (manual && u == null) message.value = t("You have the latest version.", "Imaš najnoviju verziju.")
        }
    }

    fun installUpdate() {
        val u = update.value ?: return
        val ctx = App.application
        if (!Updater.canInstall(ctx)) {
            message.value = t("Allow installing updates for Veltrix VPN, then tap Update again.", "Dozvoli instaliranje za Veltrix VPN, pa ponovo klikni Ažuriraj.")
            Updater.openInstallPermission(ctx)
            return
        }
        if (updateProgress.value != null) return
        viewModelScope.launch {
            updateProgress.value = 0f
            try {
                val f = Updater.download(ctx, u) { p -> updateProgress.value = p }
                Updater.install(ctx, f)
            } catch (e: Exception) {
                message.value = t("Update download failed: ", "Skidanje nove verzije nije uspjelo: ") + e.message
            } finally {
                updateProgress.value = null
            }
        }
    }

    fun logout() {
        stop()
        Store.logout()
        account.value = null
        loggedIn.value = false
        selected.value = -1
    }
}
