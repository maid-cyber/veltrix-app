package com.veltrix.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/** veltrix://import?url=… (iz e-maila ili sa stranice kupca), i iz drugog pokretanja programa. */
val incoming = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
private const val IPC_PORT = 47913

fun main(args: Array<String>) {
    val link = args.firstOrNull { it.startsWith("veltrix://", ignoreCase = true) }
    // samo jedan primjerak programa: ako već radi, pošalji mu link i izađi
    val lo = java.net.InetAddress.getLoopbackAddress()
    val server = runCatching { java.net.ServerSocket(IPC_PORT, 5, lo) }.getOrNull()
    if (server == null) {
        runCatching { java.net.Socket(lo, IPC_PORT).use { it.getOutputStream().write(((link ?: "show") + "\n").toByteArray()) } }
        kotlin.system.exitProcess(0)
    }
    Thread {
        while (true) {
            val c = runCatching { server.accept() }.getOrNull() ?: break
            runCatching { c.use { incoming.value = it.getInputStream().bufferedReader().readLine()?.trim() } }
        }
    }.apply { isDaemon = true }.start()
    if (link != null) incoming.value = link
    // VPN motor se gasi i kad se aplikacija zatvori na bilo koji način
    Runtime.getRuntime().addShutdownHook(Thread { Core.stopBlocking() })
    application {
        val state = remember { AppState() }
        val icon = remember { runCatching { BitmapPainter(useResource("veltrix.png", ::loadImageBitmap)) }.getOrNull() }
        Window(
            onCloseRequest = { Core.stopBlocking(); exitApplication() },
            title = "AdiLink VPN",
            icon = icon,
            state = rememberWindowState(width = 1120.dp, height = 780.dp),
        ) {
            window.minimumSize = java.awt.Dimension(900, 640)
            LaunchedEffect(Unit) {
                incoming.collect { msg ->
                    if (msg == null) return@collect
                    incoming.value = null
                    window.toFront(); window.requestFocus()
                    if (msg.startsWith("veltrix://", ignoreCase = true)) state.login(msg, autoConnect = true)
                }
            }
            Root(state)
        }
    }
}
