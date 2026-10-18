package com.veltrix.vpn.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltrix.vpn.bg.Status
import com.veltrix.vpn.bg.VpnState
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(vm: MainViewModel, onToggle: () -> Unit, onLocations: () -> Unit, onSettings: () -> Unit, onSpeed: () -> Unit = {}) {
    val acc by vm.account.collectAsState()
    val status by vm.status.collectAsState()
    val pings by vm.pings.collectAsState()
    val favs by vm.favorites.collectAsState()
    val speed by vm.speed.collectAsState()
    vm.selected.collectAsState().value
    val server = vm.currentServer()
    val on = status == Status.Started
    val busy = status == Status.Starting || status == Status.Stopping

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar(acc?.brand ?: "Veltrix VPN", Icons.Outlined.Settings, onSettings)

        // stanje zaštite
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (on) Icons.Outlined.VerifiedUser else Icons.Outlined.GppBad, null, tint = if (on) C.green else C.red, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    when (status) {
                        Status.Started -> t("PROTECTED", "ZAŠTIĆEN")
                        Status.Starting -> t("CONNECTING…", "SPAJANJE…")
                        Status.Stopping -> t("DISCONNECTING…", "PREKIDANJE…")
                        else -> t("NOT PROTECTED", "NISI ZAŠTIĆEN")
                    },
                    color = if (on) C.green else if (busy) C.amber else C.redHi, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                )
            }
            Text(
                if (on) t("Your connection is secure", "Tvoja veza je sigurna") else t("Your connection is not secure", "Tvoja veza nije zaštićena"),
                color = C.soft, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp),
            )
        }

        // veliki štit sa prstenovima
        PowerRing(on = on, busy = busy, onClick = onToggle)

        // izabrani server
        Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), glow = on, onClick = onLocations) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Flag(server?.country ?: "", 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (vm.selected.value == -1) t("Fastest server", "Najbrži server") else if (server?.isChain == true) server.name else server?.countryName ?: "—", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Outlined.ChevronRight, null, tint = C.red, modifier = Modifier.size(20.dp))
                    }
                    Text(server?.let { if (it.isChain) it.route else "${it.countryName} · ${it.city}" } ?: t("No server", "Nema servera"), color = C.muted, fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        PingDot(server?.let { pings[it.id] })
                        val ms = server?.let { pings[it.id] }
                        if (ms != null && ms in 0..120) {
                            Text("  •  " + t("Optimal", "Optimalno"), color = C.green, fontSize = 13.sp)
                        }
                    }
                }
                if (server != null) {
                    Icon(
                        if (server.id in favs) Icons.Filled.Star else Icons.Outlined.StarBorder, null,
                        tint = if (server.id in favs) C.red else C.muted,
                        modifier = Modifier.size(28.dp).clip(CircleShape).clickable { vm.toggleFavorite(server.id) },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // vrijeme, preuzimanje, slanje
        Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth()) {
            Row(Modifier.padding(vertical = 14.dp)) {
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(on) { while (on) { now = System.currentTimeMillis(); delay(1000) } }
                val secs = if (on && VpnState.connectedAt > 0) (now - VpnState.connectedAt) / 1000 else 0
                Stat(Icons.Outlined.Schedule, t("Connection Time", "Vrijeme veze"), String.format("%02d:%02d:%02d", secs / 3600, secs % 3600 / 60, secs % 60), "", Modifier.weight(1f))
                VLine()
                val d = fmtSpeed(speed.down)
                Stat(Icons.Outlined.ArrowDownward, t("Download", "Preuzimanje"), d.first, d.second, Modifier.weight(1f))
                VLine()
                val u = fmtSpeed(speed.up)
                Stat(Icons.Outlined.ArrowUpward, t("Upload", "Slanje"), u.first, u.second, Modifier.weight(1f))
            }
        }

        if (on && acc?.features?.speedtest != false) {
            Text(
                t("Test speed →", "Izmjeri brzinu →"), color = C.redHi, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onSpeed).padding(8.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.padding(horizontal = 18.dp)) {
            BigButton(
                when {
                    on -> t("Disconnect", "Prekini vezu")
                    busy -> t("Please wait…", "Sačekaj…")
                    else -> t("Connect", "Spoji se")
                },
                Icons.Outlined.PowerSettingsNew, filled = true, onClick = onToggle,
            )
        }
        acc?.announce?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(12.dp))
            Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth()) {
                Text(it, color = C.amber, fontSize = 14.sp, modifier = Modifier.padding(14.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun VLine() = Box(Modifier.width(1.dp).height(56.dp).background(C.line))

@Composable
private fun Stat(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, unit: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = C.soft, modifier = Modifier.size(22.dp))
        Text(label, color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = C.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            if (unit.isNotEmpty()) Text(" $unit", color = C.soft, fontSize = 13.sp, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

@Composable
private fun PowerRing(on: Boolean, busy: Boolean, onClick: () -> Unit) {
    val tr = rememberInfiniteTransition(label = "ring")
    val pulse by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "pulse")
    val spin by tr.animateFloat(0f, 360f, infiniteRepeatable(tween(if (busy) 1200 else 9000, easing = LinearEasing)), label = "spin")
    val main = if (on) C.red else if (busy) C.amber else Color(0xFF5A1C24)

    Box(
        Modifier.fillMaxWidth().height(300.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(290.dp)) {
            val c = center
            val r = size.minDimension / 2
            // sjaj
            drawCircle(Brush.radialGradient(listOf(main.copy(alpha = if (on) .45f else .18f), Color.Transparent), c, r), r, c)
            // pulsirajući talas
            if (on || busy) drawCircle(main.copy(alpha = (1f - pulse) * .5f), r * (.62f + .36f * pulse), c, style = Stroke(3.dp.toPx()))
            // prstenovi
            drawCircle(main.copy(alpha = .35f), r * .86f, c, style = Stroke(1.5.dp.toPx()))
            drawCircle(main, r * .72f, c, style = Stroke(5.dp.toPx()))
            drawCircle(main.copy(alpha = .5f), r * .64f, c, style = Stroke(1.dp.toPx()))
            // rotirajući luk
            drawArc(
                Brush.sweepGradient(listOf(Color.Transparent, main, Color.Transparent), c), spin, 120f, false,
                topLeft = Offset(c.x - r * .79f, c.y - r * .79f), size = androidx.compose.ui.geometry.Size(r * 1.58f, r * 1.58f),
                style = Stroke(3.dp.toPx()),
            )
            drawCircle(Color(0xFF0E0E13), r * .62f, c)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Logo(78.dp)
            Spacer(Modifier.height(12.dp))
            Icon(Icons.Outlined.PowerSettingsNew, null, tint = Color.White, modifier = Modifier.size(34.dp))
            Text(
                if (on) t("Tap to Disconnect", "Dodirni za prekid") else t("Tap to Connect", "Dodirni za spajanje"),
                color = C.soft, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
