package com.veltrix.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

enum class Status { Stopped, Starting, Started, Stopping }

/** Pokreće i gasi sing-box.exe (VPN motor). Traži administratorska prava (Wintun adapter). */
object Core {
    val status = MutableStateFlow(Status.Stopped)
    val error = MutableStateFlow<String?>(null)
    val down = MutableStateFlow(0L)
    val up = MutableStateFlow(0L)
    @Volatile var connectedAt = 0L
    private val logLines = ArrayDeque<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var proc: Process? = null
    private var watch: Job? = null
    private var secret = ""
    @Volatile private var userStop = false
    private var lastConfig = ""

    fun log(): String = synchronized(logLines) { logLines.joinToString("\n") }
    private fun addLog(s: String) = synchronized(logLines) { logLines.addLast(s); while (logLines.size > 300) logLines.removeFirst() }

    val binary: File
        get() {
            val dir = System.getProperty("compose.application.resources.dir")?.let { File(it) }
            return listOfNotNull(dir?.let { File(it, "sing-box.exe") }, File("resources/windows/sing-box.exe")).firstOrNull { it.exists() } ?: File("sing-box.exe")
        }

    fun start(config: (String) -> String) {
        if (status.value == Status.Starting || status.value == Status.Started) return
        userStop = false
        error.value = null
        secret = UUID.randomUUID().toString()
        lastConfig = config(secret)
        launchCore(0)
    }

    private fun launchCore(attempt: Int) {
        status.value = Status.Starting
        watch?.cancel()
        watch = scope.launch {
            try {
                val bin = binary
                if (!bin.exists()) throw IllegalStateException(if (Store.bosnian()) "Nedostaje sing-box.exe. Ponovo instaliraj aplikaciju." else "sing-box.exe is missing. Reinstall the app.")
                val cfg = File(Store.dataDir, "config.json").apply { writeText(lastConfig) }
                synchronized(logLines) { logLines.clear() }
                val p = ProcessBuilder(bin.path, "run", "-c", cfg.path, "-D", Store.dataDir.path, "--disable-color")
                    .redirectErrorStream(true).start()
                proc = p
                launch { p.inputStream.bufferedReader().forEachLine { addLog(it) } }
                // motor radi kad odgovara njegov lokalni API
                var ok = false
                var i = 0
                while (i < 40 && p.isAlive) {
                    if (api("/version") != null) { ok = true; break }
                    delay(250)
                    i++
                }
                if (!ok) {
                    p.destroy()
                    throw IllegalStateException(reason())
                }
                connectedAt = System.currentTimeMillis()
                status.value = Status.Started
                var lastDown = -1L; var lastUp = -1L
                while (p.isAlive) {
                    api("/connections")?.let { j ->
                        val d = j.optLong("downloadTotal"); val u = j.optLong("uploadTotal")
                        if (lastDown >= 0) { down.value = (d - lastDown).coerceAtLeast(0); up.value = (u - lastUp).coerceAtLeast(0) }
                        lastDown = d; lastUp = u
                    }
                    delay(1000)
                }
                // motor se ugasio sam
                down.value = 0; up.value = 0
                if (!userStop) {
                    error.value = reason()
                    if (Store.reconnect && attempt < 5) {
                        addLog("--- ponovno spajanje (${attempt + 1}) ---")
                        delay(3000)
                        if (!userStop) { launchCore(attempt + 1); return@launch }
                    }
                }
                status.value = Status.Stopped
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error.value = e.message ?: e.toString()
                status.value = Status.Stopped
            }
        }
    }

    /** Zadnja greška iz loga, u razumljivom obliku. */
    private fun reason(): String {
        val l = log()
        return when {
            "Access is denied" in l || "access denied" in l.lowercase() || "elevat" in l.lowercase() ->
                if (Store.bosnian()) "Pokreni Veltrix VPN kao administrator." else "Run Veltrix VPN as administrator."
            else -> l.lines().lastOrNull { "FATAL" in it || "ERROR" in it }?.substringAfter("] ")?.take(200)
                ?: (if (Store.bosnian()) "VPN motor se ugasio." else "VPN engine stopped.")
        }
    }

    fun stop() {
        userStop = true
        val p = proc ?: run { status.value = Status.Stopped; return }
        status.value = Status.Stopping
        scope.launch {
            p.destroy()
            if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly()
            watch?.cancel()
            down.value = 0; up.value = 0
            connectedAt = 0
            status.value = Status.Stopped
        }
    }

    fun stopBlocking() {
        userStop = true
        proc?.let { p -> p.destroy(); runCatching { p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS) }; if (p.isAlive) p.destroyForcibly() }
    }

    private fun api(path: String): JSONObject? = runCatching {
        val c = URL("http://127.0.0.1:${ConfigBuilder.API_PORT}$path").openConnection(java.net.Proxy.NO_PROXY) as HttpURLConnection
        c.connectTimeout = 800; c.readTimeout = 1500
        c.setRequestProperty("Authorization", "Bearer $secret")
        try { if (c.responseCode == 200) JSONObject(c.inputStream.bufferedReader().readText()) else null } finally { c.disconnect() }
    }.getOrNull()
}
