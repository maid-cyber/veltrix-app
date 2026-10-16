package com.veltrix.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() {
    // VPN motor se gasi i kad se aplikacija zatvori na bilo koji način
    Runtime.getRuntime().addShutdownHook(Thread { Core.stopBlocking() })
    application {
        val state = remember { AppState() }
        val icon = remember { runCatching { BitmapPainter(useResource("veltrix.png", ::loadImageBitmap)) }.getOrNull() }
        Window(
            onCloseRequest = { Core.stopBlocking(); exitApplication() },
            title = "Veltrix VPN",
            icon = icon,
            state = rememberWindowState(width = 1120.dp, height = 780.dp),
        ) {
            window.minimumSize = java.awt.Dimension(900, 640)
            Root(state)
        }
    }
}
