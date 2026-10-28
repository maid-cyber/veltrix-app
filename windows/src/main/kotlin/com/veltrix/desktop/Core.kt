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

/** Pokreće i gasi sing-box (VPN motor). Windows: traži administratorska prava (Wintun).
 *  Linux: TUN traži root, pa se motor pokreće preko pkexec (grafička potvrda lozinkom). */
object Core {
    private val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    private val binName = if (isWindows) "sing-box.exe" else "sing-box"
    // Compose appResources poddirektorij: Windows "windows", Linux x64 "linux-x64"
    private val resSub = if (isWindows) "windows" else "linux-x64"
    private val isRoot = !isWindows && runCatching {
        ProcessBuilder("id", "-u").redirectErrorStream(true).start().inputStream.bufferedReader().readText().trim() == "0"
    }.getOrDefault(false)
    private val pkexec: String? by lazy {
        if (isWindows || isRoot) null
        else listOf("/usr/bin/pkexec", "/bin/pkexec").firstOrNull { File(it).canExecute() }
    }
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
            return listOfNotNull(dir?.let { File(it, binName) }, File("resources/$resSub/$binName"), File("resources/linux/$binName")).firstOrNull { it.exists() } ?: File(binName)
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
                if (!bin.exists()) throw IllegalStateException(if (Store.bosnian()) "Nedostaje $binName. Ponovo instaliraj aplikaciju." else "$binName is missing. Reinstall the app.")
                if (!isWindows) bin.setExecutable(true)
                val cfg = File(Store.dataDir, "config.json").apply { writeText(lastConfig) }
                synchronized(logLines) { logLines.clear() }
                // Linux: TUN traži root → preko pkexec (grafička potvrda). Windows i root: direktno.
                val coreCmd = listOf(bin.absolutePath, "run", "-c", cfg.absolutePath, "-D", Store.dataDir.absolutePath, "--disable-color")
                val cmd = pkexec?.let { listOf(it) + coreCmd } ?: coreCmd
                val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
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
        val l = log(); val low = l.lowercase()
        return when {
            isWindows && ("Access is denied" in l || "access denied" in low || "elevat" in low) ->
                if (Store.bosnian()) "Pokreni AdiLink VPN kao administrator." else "Run AdiLink VPN as administrator."
            !isWindows && ("not authorized" in low || "dismissed" in low || "authentication failed" in low) ->
                if (Store.bosnian()) "Potvrda je odbijena. Dozvoli pristup (lozinkom) da bi se VPN pokrenuo." else "Authorization was dismissed. Allow access to start the VPN."
            !isWindows && ("operation not permitted" in low || "permission denied" in low || "must be run as root" in low || pkexec == null && !isRoot) ->
                if (Store.bosnian()) "VPN traži administratorska prava. Instaliraj 'pkexec' (polkit) ili pokreni kao root." else "The VPN needs admin rights. Install 'pkexec' (polkit) or run as root."
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
