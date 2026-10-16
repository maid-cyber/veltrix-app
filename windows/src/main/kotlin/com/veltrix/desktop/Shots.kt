package com.veltrix.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import org.json.JSONArray
import java.io.File

/** Pravi screenshotove svih ekrana sa primjer podacima (za provjeru izgleda na GitHubu). */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "shots").apply { mkdirs() }
    val vless = JSONArray().put(org.json.JSONObject().put("type", "vless"))
    fun srv(id: Int, name: String, city: String, cc: String, tags: List<String> = emptyList(), kind: String = "server", hops: List<Pair<String, String>> = emptyList()) =
        Server(id, name, city, cc, "10.0.0.$id", 443, vless, kind, tags, hops)
    val demo = Account(
        brand = "Veltrix VPN", support = "@veltrixvpn", announce = "", username = "maid", plan = "Premium 30 dana", status = "active", canConnect = true,
        expiresAt = System.currentTimeMillis() + 24L * 86_400_000, createdAt = System.currentTimeMillis() - 40L * 86_400_000,
        trafficUsed = 12L shl 30, trafficLimit = 0, devices = 3,
        servers = listOf(
            srv(1, "DE Falkenstein", "Falkenstein", "DE", listOf("p2p")), srv(2, "NL Amsterdam", "Amsterdam", "NL", listOf("streaming")),
            srv(3, "CH Zurich", "Zurich", "CH", listOf("special")), srv(4, "US New York", "New York", "US", listOf("gaming", "streaming")),
            srv(100001, "Njemačka → Švicarska", "Zurich", "CH", kind = "double", hops = listOf("DE" to "Falkenstein", "CH" to "Zurich")),
        ),
        devicesActive = 1, history = listOf(HistoryItem(System.currentTimeMillis() - 86_400_000, "Produžen · Premium 30 dana")),
    )
    val st = AppState(demo)
    st.pings.value = mapOf(1 to 28, 2 to 41, 3 to 55, 4 to 112, 100001 to 63)
    for ((i, name) in listOf("home", "locations", "security", "account", "diagnostics").withIndex()) {
        if (i == 0) Core.status.value = Status.Started.also { Core.connectedAt = System.currentTimeMillis() - 3_725_000; Core.down.value = 3_400_000; Core.up.value = 420_000 }
        else Core.status.value = Status.Stopped
        val scene = ImageComposeScene(1180, 800, Density(1f)) { Root(st, i) }
        scene.render(0)
        val img = scene.render(1_200_000_000L)
        File(out, "$name.png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
        println("ok $name")
    }
    // prijava
    val login = AppState(null)
    login.loggedIn.value = false
    val scene = ImageComposeScene(1180, 800, Density(1f)) { Root(login) }
    scene.render(0)
    File(out, "login.png").writeBytes(scene.render(500_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes)
    scene.close()
    println("ok login")
    kotlin.system.exitProcess(0)
}
