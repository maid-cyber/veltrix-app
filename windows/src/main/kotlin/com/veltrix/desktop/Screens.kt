package com.veltrix.desktop

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CardMembership
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VideoCall
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.text.DateFormat
import java.util.Date

fun openUrl(url: String) = runCatching { Desktop.getDesktop().browse(URI(url)) }
fun clipboardText(): String = runCatching { Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as String }.getOrDefault("")
fun copyText(s: String) = runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(s), null) }

// ================================================================== okvir
@Composable
fun Root(state: AppState, startTab: Int = 0) {
    val loggedIn by state.loggedIn.collectAsState()
    Box(Modifier.fillMaxSize().background(C.bg).background(Brush.radialGradient(listOf(C.red.copy(alpha = .14f), Color.Transparent), center = Offset(1200f, 0f), radius = 1100f))) {
        if (!loggedIn) LoginScreen(state) else Shell(state, startTab)
        Toasts(state)
    }
}

@Composable
private fun Toasts(state: AppState) {
    val msg by state.message.collectAsState()
    val err by state.error.collectAsState()
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(msg) { msg?.let { shown = it; delay(4000); state.message.value = null; shown = null } }
    LaunchedEffect(err) { err?.let { shown = t("Connection failed: ", "Spajanje nije uspjelo: ") + it; delay(6000); shown = null } }
    shown?.let {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomCenter) {
            Text(it, color = C.text, fontSize = 14.sp, modifier = Modifier.widthIn(max = 560.dp).clip(RoundedCornerShape(12.dp)).background(C.card2).border(1.dp, C.line2, RoundedCornerShape(12.dp)).padding(horizontal = 18.dp, vertical = 12.dp))
        }
    }
}

@Composable
private fun Shell(state: AppState, startTab: Int) {
    var tab by remember { mutableStateOf(startTab) }
    val acc by state.account.collectAsState()
    val status by state.status.collectAsState()
    val upd by state.update.collectAsState()
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(230.dp).fillMaxHeight().background(C.side.copy(alpha = .92f)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, bottom = 26.dp, top = 6.dp)) {
                Logo(34.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text((acc?.brand ?: "Veltrix VPN").substringBefore(" VPN").uppercase(), color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Text("VPN · Windows", color = C.muted, fontSize = 12.sp)
                }
            }
            NavItem(Icons.Outlined.Home, t("Home", "Početna"), tab == 0) { tab = 0 }
            NavItem(Icons.Outlined.LocationOn, t("Locations", "Lokacije"), tab == 1) { tab = 1 }
            NavItem(Icons.Outlined.Shield, t("Security", "Sigurnost"), tab == 2) { tab = 2 }
            NavItem(Icons.Outlined.Person, t("Account", "Nalog"), tab == 3) { tab = 3 }
            NavItem(Icons.Outlined.Terminal, t("Diagnostics", "Dijagnostika"), tab == 4) { tab = 4 }
            Spacer(Modifier.weight(1f))
            upd?.let {
                Card(Modifier.fillMaxWidth().padding(bottom = 10.dp), glow = true, onClick = { openUrl(it.url) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(t("New version ", "Nova verzija ") + it.version, color = C.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(t("Click to download", "Klikni za preuzimanje"), color = C.redHi, fontSize = 12.sp)
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (status == Status.Started) C.green else C.muted))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(acc?.username ?: "—", color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (status == Status.Started) t("Protected", "Zaštićen") else t("Not protected", "Nije zaštićen"), color = if (status == Status.Started) C.green else C.muted, fontSize = 12.sp)
                    }
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(C.line))
        Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 34.dp, vertical = 26.dp)) {
            when (tab) {
                0 -> HomeScreen(state, onLocations = { tab = 1 }, onSpeed = { tab = 4 })
                1 -> LocationsScreen(state) { tab = 0 }
                2 -> SecurityScreen(state)
                3 -> AccountScreen(state)
                else -> DiagnosticsScreen(state)
            }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(11.dp))
            .background(if (on) Brush.horizontalGradient(listOf(C.red, C.redDark)) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (on) Color.White else C.soft, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = if (on) Color.White else C.soft, fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ================================================================== prijava
@Composable
private fun LoginScreen(state: AppState) {
    val loading by state.loading.collectAsState()
    var input by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(Modifier.width(440.dp)) {
            Column(Modifier.padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Logo(90.dp)
                Spacer(Modifier.height(16.dp))
                Text("VELTRIX", color = C.text, fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                Text("V P N", color = C.redHi, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp)
                Spacer(Modifier.height(22.dp))
                Text(t("Paste the access link you received after purchase.", "Zalijepi pristupni link koji si dobio nakon kupovine."), color = C.soft, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(12.dp)).background(C.bg).border(1.dp, C.line2, RoundedCornerShape(12.dp)).padding(start = 14.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (input.isEmpty()) Text(t("Access link (http…/s/…)", "Pristupni link (http…/s/…)"), color = C.muted, fontSize = 14.sp)
                        BasicTextField(input, { input = it }, singleLine = true, textStyle = TextStyle(color = C.text, fontSize = 14.sp), cursorBrush = SolidColor(C.red), modifier = Modifier.fillMaxWidth())
                    }
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)).clickable { input = clipboardText().trim() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.ContentPaste, t("Paste", "Zalijepi"), tint = C.soft)
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (loading) CircularProgressIndicator(color = C.red) else BigButton(t("Log in", "Prijavi se"), null) { state.login(input) }
                Spacer(Modifier.height(18.dp))
                var ref by remember { mutableStateOf("") }
                var showRef by remember { mutableStateOf(false) }
                if (!loading) {
                    BigButton(t("Try free", "Probaj besplatno"), null, filled = false) { state.trial(ref) }
                    Spacer(Modifier.height(8.dp))
                    if (!showRef) Text(t("Have a friend's code?", "Imaš kod prijatelja?"), color = C.redHi, fontSize = 13.sp, modifier = Modifier.clickable { showRef = true }.padding(6.dp))
                    else Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(C.bg).border(1.dp, C.line2, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                        if (ref.isEmpty()) Text(t("Friend's code (optional)", "Kod prijatelja (nije obavezno)"), color = C.muted, fontSize = 14.sp)
                        BasicTextField(ref, { ref = it.uppercase().take(12) }, singleLine = true, textStyle = TextStyle(color = C.text, fontSize = 14.sp, letterSpacing = 2.sp), cursorBrush = SolidColor(C.red), modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(t("Don't have an account? Contact the seller you bought from.", "Nemaš nalog? Javi se prodavcu od kojeg kupuješ."), color = C.muted, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

// ================================================================== početna
@Composable
private fun HomeScreen(state: AppState, onLocations: () -> Unit, onSpeed: () -> Unit = {}) {
    val acc by state.account.collectAsState()
    val status by state.status.collectAsState()
    val pings by state.pings.collectAsState()
    val favs by state.favorites.collectAsState()
    val selected by state.selected.collectAsState()
    val down by Core.down.collectAsState()
    val up by Core.up.collectAsState()
    val server = state.currentServer()
    val on = status == Status.Started
    val busy = status == Status.Starting || status == Status.Stopping
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (on) Icons.Outlined.VerifiedUser else Icons.Outlined.GppBad, null, tint = if (on) C.green else C.red, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                when (status) { Status.Started -> t("PROTECTED", "ZAŠTIĆEN"); Status.Starting -> t("CONNECTING…", "SPAJANJE…"); Status.Stopping -> t("DISCONNECTING…", "PREKIDANJE…"); else -> t("NOT PROTECTED", "NISI ZAŠTIĆEN") },
                color = if (on) C.green else if (busy) C.amber else C.redHi, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
            )
        }
        Text(if (on) t("Your connection is secure", "Tvoja veza je sigurna") else t("Your connection is not secure", "Tvoja veza nije zaštićena"), color = C.soft, fontSize = 15.sp)
        PowerRing(on, busy) { state.toggle() }
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            Card(Modifier.fillMaxWidth(), glow = on, onClick = onLocations) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CountryBadge(server?.country ?: "", 46.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (selected == -1) t("Fastest server", "Najbrži server") else if (server?.isChain == true) server.name else server?.countryName ?: "—", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Icon(Icons.Outlined.ChevronRight, null, tint = C.red, modifier = Modifier.size(20.dp))
                        }
                        Text(server?.let { if (it.isChain) it.route else "${it.countryName} · ${it.city}" } ?: t("No server", "Nema servera"), color = C.muted, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            PingDot(server?.let { pings[it.id] })
                            val ms = server?.let { pings[it.id] }
                            if (ms != null && ms in 0..120) Text("  •  " + t("Optimal", "Optimalno"), color = C.green, fontSize = 13.sp)
                        }
                    }
                    if (server != null) {
                        Icon(if (server.id in favs) Icons.Filled.Star else Icons.Outlined.StarBorder, null, tint = if (server.id in favs) C.red else C.muted, modifier = Modifier.size(28.dp).clip(CircleShape).clickable { state.toggleFavorite(server.id) })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(vertical = 14.dp)) {
                    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(on) { while (on) { now = System.currentTimeMillis(); delay(1000) } }
                    val secs = if (on && Core.connectedAt > 0) (now - Core.connectedAt) / 1000 else 0
                    Stat(Icons.Outlined.Schedule, t("Connection Time", "Vrijeme veze"), String.format("%02d:%02d:%02d", secs / 3600, secs % 3600 / 60, secs % 60), "", Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(56.dp).background(C.line))
                    val d = fmtSpeed(down)
                    Stat(Icons.Outlined.ArrowDownward, t("Download", "Preuzimanje"), d.first, d.second, Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(56.dp).background(C.line))
                    val u = fmtSpeed(up)
                    Stat(Icons.Outlined.ArrowUpward, t("Upload", "Slanje"), u.first, u.second, Modifier.weight(1f))
                }
            }
            if (on) Text(t("Test speed →", "Izmjeri brzinu →"), color = C.redHi, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onSpeed).padding(8.dp))
            Spacer(Modifier.height(10.dp))
            BigButton(when { on -> t("Disconnect", "Prekini vezu"); busy -> t("Please wait…", "Sačekaj…"); else -> t("Connect", "Spoji se") }, Icons.Outlined.PowerSettingsNew) { state.toggle() }
            acc?.announce?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth()) { Text(it, color = C.amber, fontSize = 14.sp, modifier = Modifier.padding(14.dp)) }
            }
        }
    }
}

@Composable
private fun Stat(icon: ImageVector, label: String, value: String, unit: String, modifier: Modifier) {
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
    val main = if (on) C.red else if (busy) C.amber else Color(0xFF3A2440)
    Box(
        Modifier.fillMaxWidth().height(300.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(290.dp)) {
            val c = center
            val r = size.minDimension / 2
            drawCircle(Brush.radialGradient(listOf(main.copy(alpha = if (on) .45f else .2f), Color.Transparent), c, r), r, c)
            if (on || busy) drawCircle(main.copy(alpha = (1f - pulse) * .5f), r * (.62f + .36f * pulse), c, style = Stroke(3.dp.toPx()))
            drawCircle(main.copy(alpha = .35f), r * .86f, c, style = Stroke(1.5.dp.toPx()))
            drawCircle(main, r * .72f, c, style = Stroke(5.dp.toPx()))
            drawCircle(main.copy(alpha = .5f), r * .64f, c, style = Stroke(1.dp.toPx()))
            drawArc(
                Brush.sweepGradient(listOf(Color.Transparent, main, Color.Transparent), c), spin, 120f, false,
                topLeft = Offset(c.x - r * .79f, c.y - r * .79f), size = androidx.compose.ui.geometry.Size(r * 1.58f, r * 1.58f), style = Stroke(3.dp.toPx()),
            )
            drawCircle(Color(0xFF0B0F24), r * .62f, c)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Logo(76.dp)
            Spacer(Modifier.height(10.dp))
            Icon(Icons.Outlined.PowerSettingsNew, null, tint = Color.White, modifier = Modifier.size(32.dp))
            Text(if (on) t("Click to Disconnect", "Klikni za prekid") else t("Click to Connect", "Klikni za spajanje"), color = C.soft, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ================================================================== lokacije
@Composable
private fun LocationsScreen(state: AppState, onPicked: () -> Unit) {
    val acc by state.account.collectAsState()
    val pings by state.pings.collectAsState()
    val favs by state.favorites.collectAsState()
    val selected by state.selected.collectAsState()
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("all") }
    val all = acc?.servers.orEmpty()
    val cats = listOf(
        "all" to t("All", "Svi"), "fav" to t("Favorites", "Omiljeni"), "p2p" to "P2P / Torrent", "streaming" to "Streaming",
        "gaming" to "Gaming", "double" to "Double VPN", "multihop" to "Multihop", "special" to t("Special", "Specijalni"),
    ).filter { (k, _) -> k == "all" || k == "fav" || all.any { k in it.tags || it.kind == k } }
    val list = all.filter {
        when (cat) { "all" -> !it.isChain; "fav" -> it.id in favs; "double", "multihop" -> it.kind == cat; else -> cat in it.tags }
    }.filter { query.isBlank() || listOf(it.name, it.city, it.countryName, it.country).any { f -> f.contains(query, true) } }
        .sortedBy { pings[it.id]?.takeIf { p -> p >= 0 } ?: 99999 }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Title(t("Locations", "Lokacije"), t("${all.count { !it.isChain }} servers", "Servera: ${all.count { !it.isChain }}")) }
            Box(Modifier.size(42.dp).clip(CircleShape).clickable { state.pingAll() }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Refresh, null, tint = C.soft) }
        }
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(C.card).border(1.dp, C.line2, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, tint = C.muted)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(t("Search countries or cities", "Traži državu ili grad"), color = C.muted, fontSize = 14.sp)
                BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = C.text, fontSize = 14.sp), cursorBrush = SolidColor(C.red), modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cats.forEach { (k, l) ->
                val shape = RoundedCornerShape(12.dp)
                Text(
                    l, color = if (cat == k) Color.White else C.soft, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(shape).background(if (cat == k) C.red else Color.Transparent).border(1.dp, if (cat == k) C.red else C.line2, shape).clickable { cat = k }.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (cat == "all" && query.isBlank()) {
                Card(Modifier.fillMaxWidth(), glow = selected == -1, onClick = { state.select(-1); onPicked() }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(CircleShape).background(C.red.copy(alpha = .15f)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Bolt, null, tint = C.red, modifier = Modifier.size(26.dp)) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t("Fastest server", "Najbrži server"), color = C.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(t("Chosen automatically", "Bira se automatski"), color = C.muted, fontSize = 13.sp)
                        }
                        if (selected == -1) Icon(Icons.Outlined.CheckCircle, null, tint = C.red)
                    }
                }
            }
            list.forEach { s ->
                Card(Modifier.fillMaxWidth(), glow = selected == s.id, onClick = { state.select(s.id); onPicked() }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CountryBadge(s.country, 46.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (s.isChain) s.name else s.countryName, color = C.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (s.isChain) s.route else s.city, color = C.muted, fontSize = 13.sp)
                            if (s.tags.isNotEmpty() && !s.isChain) Text(s.tags.joinToString(" · ") { tagName(it) }, color = C.redHi, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        PingDot(pings[s.id])
                        Spacer(Modifier.width(16.dp))
                        Icon(if (s.id in favs) Icons.Filled.Star else Icons.Outlined.StarBorder, null, tint = if (s.id in favs) C.red else C.muted, modifier = Modifier.size(28.dp).clip(CircleShape).clickable { state.toggleFavorite(s.id) })
                    }
                }
            }
            if (list.isEmpty()) Text(if (cat == "fav") t("No favorites yet. Click the star next to a server.", "Još nema omiljenih. Klikni zvjezdicu kod servera.") else t("No servers found.", "Nema servera."), color = C.muted, modifier = Modifier.padding(20.dp))
        }
    }
}

// ================================================================== sigurnost
@Composable
private fun SecurityScreen(state: AppState) {
    val acc by state.account.collectAsState()
    val sec by state.sec.collectAsState()
    val splitOn by state.splitOn.collectAsState()
    val apps by state.splitApps.collectAsState()
    val rec by state.reconnectOn.collectAsState()
    val f = acc?.features ?: Features()
    var newApp by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Title(t("Security", "Sigurnost"), t("Changes apply right away. If connected, the VPN reconnects in a second.", "Promjene važe odmah. Ako si spojen, VPN se ponovo spoji za sekundu."))
        Card(Modifier.fillMaxWidth()) {
            Column {
                if (f.killswitch) SetSwitch(Icons.Outlined.Autorenew, t("Auto reconnect", "Automatsko ponovno spajanje"), t("If the VPN drops, it reconnects by itself", "Ako VPN pukne, sam se ponovo spoji"), rec) { state.setReconnect(it) }
                if (f.dns) SetSwitch(
                    Icons.Outlined.Dns, t("DNS protection", "DNS zaštita"),
                    if (sec.dnsProtect) {
                        val n = f.dnsFilters.map { when (it) { "ads" -> t("ads", "reklame"); "adult" -> t("adult sites", "sadržaj za odrasle"); else -> t("malware", "malver") } }
                        if (n.isEmpty()) t("DNS through the VPN server", "DNS ide preko VPN servera") else t("Blocks: ", "Blokira: ") + n.joinToString(", ")
                    } else t("Encrypted 1.1.1.1, no filtering", "Šifrovani 1.1.1.1, bez filtriranja"),
                    sec.dnsProtect,
                ) { state.setDns(it) }
                if (f.ipleak) SetSwitch(Icons.Outlined.Shield, t("IP leak protection", "IP leak zaštita"), t("Blocks IPv6 and traffic outside the VPN", "Blokira IPv6 i promet mimo VPN-a"), sec.ipLeak) { state.setIpLeak(it) }
                if (f.webrtc) SetSwitch(Icons.Outlined.VideoCall, t("WebRTC protection", "WebRTC zaštita"), t("Blocks STUN requests that reveal IP addresses", "Blokira STUN upite koji otkrivaju IP adresu"), sec.webrtc, last = !f.split) { state.setWebrtc(it) }
                if (f.split) SetSwitch(Icons.Outlined.Apps, "Split tunneling", t("Chosen programs bypass the VPN", "Izabrani programi idu mimo VPN-a"), splitOn, last = true) { state.setSplitOn(it) }
            }
        }
        if (f.split && splitOn) {
            Spacer(Modifier.height(16.dp))
            Text(t("Programs that bypass the VPN", "Programi koji idu mimo VPN-a"), color = C.soft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    apps.sorted().forEach { a ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(a, color = C.text, fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                            Box(Modifier.size(32.dp).clip(CircleShape).clickable { state.setSplitApps(apps - a) }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Delete, null, tint = C.muted, modifier = Modifier.size(18.dp)) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(10.dp)).background(C.bg).border(1.dp, C.line2, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                            if (newApp.isEmpty()) Text(t("e.g. chrome.exe", "npr. chrome.exe"), color = C.muted, fontSize = 14.sp)
                            BasicTextField(newApp, { newApp = it }, singleLine = true, textStyle = TextStyle(color = C.text, fontSize = 14.sp), cursorBrush = SolidColor(C.red), modifier = Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.width(10.dp))
                        BigButton(t("Add", "Dodaj"), null, modifier = Modifier.width(110.dp)) {
                            val n = newApp.trim().substringAfterLast('\\').let { if (it.endsWith(".exe", true) || it.isBlank()) it else "$it.exe" }
                            if (n.isNotBlank()) state.setSplitApps(apps + n)
                            newApp = ""
                        }
                    }
                    Text(t("Type the program file name as shown in Task Manager → Details.", "Upiši ime programa kako piše u Task Manager → Detalji."), color = C.muted, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(t("All traffic is encrypted (VLESS Reality). Websites only see the server's IP address.", "Sav promet je šifrovan (VLESS Reality). Sajtovi vide samo IP adresu servera."), color = C.muted, fontSize = 13.sp)
    }
}

// ================================================================== nalog
@Composable
private fun AccountScreen(state: AppState) {
    val acc by state.account.collectAsState()
    val protocol by state.protocol.collectAsState()
    val auto by state.autoConnect.collectAsState()
    val upd by state.update.collectAsState()
    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    var showHistory by remember { mutableStateOf(false) }
    var showRenew by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 900.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Title(t("My Account", "Moj nalog"), acc?.username)
            Card(Modifier.fillMaxWidth(), glow = true) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Logo(56.dp)
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text((acc?.plan?.ifBlank { null } ?: "Premium").uppercase(), color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(statusText(acc?.status), color = if (acc?.canConnect == true) C.green else C.redHi, fontSize = 15.sp)
                        acc?.expiresAt?.takeIf { it > 0 }?.let { Text(t("Valid until ", "Vrijedi do ") + df.format(Date(it)), color = C.soft, fontSize = 13.sp) }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        val used = acc?.trafficUsed ?: 0; val limit = acc?.trafficLimit ?: 0
                        Text(if (limit > 0) "${fmtBytes(used)} / ${fmtBytes(limit)}" else t("Unlimited", "Neograničeno"), color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(t("${acc?.devicesActive ?: 0} of ${acc?.devices ?: 1} devices", "${acc?.devicesActive ?: 0} od ${acc?.devices ?: 1} uređaja"), color = C.muted, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            val settings: @Composable () -> Unit = {
                Column {
                    Text(t("Settings", "Podešavanja"), color = C.soft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                    Card(Modifier.fillMaxWidth()) {
                        Column {
                            SetRow(Icons.Outlined.VpnKey, t("VPN protocol", "VPN protokol"), when (protocol) { "vless" -> "VLESS Reality"; "shadowsocks" -> "Shadowsocks 2022"; else -> t("Automatic (recommended)", "Automatski (preporučeno)") }, onClick = {
                                state.setProtocol(when (protocol) { "auto" -> "vless"; "vless" -> "shadowsocks"; else -> "auto" })
                            }) { Text(t("change", "promijeni"), color = C.redHi, fontSize = 13.sp) }
                            SetSwitch(Icons.Outlined.Autorenew, t("Connect on start", "Spoji pri pokretanju"), t("Connects when the app opens", "Spoji se čim otvoriš aplikaciju"), auto) { state.setAutoConnect(it) }
                            SetRow(Icons.Outlined.Language, t("Language", "Jezik"), t("Restart the app to apply", "Važi poslije ponovnog pokretanja")) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("auto" to "Auto", "bs" to "BS", "en" to "EN").forEach { (k, l) ->
                                        Text(l, color = if (Store.lang == k) Color.White else C.soft, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (Store.lang == k) C.red else C.card2).clickable { Store.lang = k; state.message.value = t("Restart the app to change the language.", "Ponovo pokreni aplikaciju da se promijeni jezik.") }.padding(horizontal = 10.dp, vertical = 5.dp))
                                    }
                                }
                            }
                            SetRow(Icons.Outlined.Palette, t("Accent color", "Boja akcenta"), t("Restart the app to apply", "Važi poslije ponovnog pokretanja"), last = true) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ACCENTS.forEach { (k, c) ->
                                        Box(Modifier.size(26.dp).clip(CircleShape).background(c.first).border(2.dp, if (Store.accent == k) Color.White else Color.Transparent, CircleShape).clickable { Store.accent = k })
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val account: @Composable () -> Unit = {
                Column {
                    Text(t("Account", "Račun"), color = C.soft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                    Card(Modifier.fillMaxWidth()) {
                        Column {
                            SetRow(Icons.Outlined.CardMembership, t("Subscription", "Pretplata"), (acc?.plan?.ifBlank { null } ?: "—") + " · " + statusText(acc?.status))
                            if (acc?.renew?.isNotEmpty() == true) SetRow(Icons.Outlined.CreditCard, t("Renew subscription", "Produži pretplatu"), t("Pay by card, days are added right away", "Plati karticom, dani se dodaju odmah"), onClick = { showRenew = !showRenew }) { Icon(Icons.Outlined.ChevronRight, null, tint = C.muted) }
                            acc?.ref?.let { r -> SetRow(Icons.Outlined.CardGiftcard, t("Invite a friend", "Pozovi prijatelja"), t("Your code ${r.code} · both get ${r.days} free days", "Tvoj kod ${r.code} · obojica ${r.days} dana gratis"), onClick = { copyText(r.code); state.message.value = t("Code copied", "Kod kopiran") }) { Text(t("copy", "kopiraj"), color = C.redHi, fontSize = 13.sp) } }
                            SetRow(Icons.Outlined.Devices, t("Devices", "Uređaji"), t("${acc?.devicesActive ?: 0} of ${acc?.devices ?: 1} connected now", "${acc?.devicesActive ?: 0} od ${acc?.devices ?: 1} spojeno sada"))
                            if (acc?.features?.history != false) SetRow(Icons.Outlined.History, t("Activity history", "Povijest aktivnosti"), t("${acc?.history?.size ?: 0} entries", "Zapisa: ${acc?.history?.size ?: 0}"), onClick = { showHistory = !showHistory }) { Icon(Icons.Outlined.ChevronRight, null, tint = C.muted) }
                            val support = acc?.support.orEmpty()
                            SetRow(Icons.Outlined.SupportAgent, t("Help & Support", "Podrška"), support.ifBlank { t("Contact your seller", "Javi se prodavcu") }, onClick = {
                                val m = Regex("@([A-Za-z0-9_]{4,})").find(support)
                                when { support.startsWith("http") -> openUrl(support); m != null -> openUrl("https://t.me/" + m.groupValues[1]) }
                            })
                            SetRow(Icons.Outlined.Info, t("Version ", "Verzija ") + BuildInfo.VERSION, upd?.let { t("Update available: ", "Dostupna nova: ") + it.version } ?: t("Check for updates", "Provjeri ima li nova verzija"), onClick = { upd?.let { openUrl(it.url) } ?: state.checkUpdate(true) })
                            SetRow(Icons.AutoMirrored.Outlined.Logout, t("Log out", "Odjava"), t("You will need your access link again", "Za ponovnu prijavu treba pristupni link"), last = true, onClick = { state.logout() })
                        }
                    }
                }
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Box(Modifier.weight(1f)) { settings() }
                    Box(Modifier.weight(1f)) { account() }
                }
            } else {
                settings(); Spacer(Modifier.height(18.dp)); account()
            }
            if (showRenew) {
                Spacer(Modifier.height(18.dp))
                Text(t("Choose a plan", "Izaberi plan"), color = C.soft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    acc?.renew.orEmpty().forEach { p ->
                        Card(Modifier.weight(1f), onClick = { state.renew(p.id) }) {
                            Column(Modifier.padding(16.dp)) {
                                Text(p.name, color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(t("${p.days} days", "${p.days} dana"), color = C.muted, fontSize = 13.sp)
                                Text(acc!!.currencySign + (if (p.price % 1.0 == 0.0) p.price.toInt().toString() else String.format("%.2f", p.price)), color = C.redHi, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }
            if (showHistory) {
                Spacer(Modifier.height(18.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val dtf = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        acc?.history.orEmpty().forEach { h ->
                            Row { Text(dtf.format(Date(h.ts)), color = C.muted, fontSize = 13.sp, modifier = Modifier.width(170.dp)); Text(h.text, color = C.text, fontSize = 13.sp) }
                        }
                        if (acc?.history.isNullOrEmpty()) Text(t("No activity yet.", "Još nema aktivnosti."), color = C.muted)
                    }
                }
            }
        }
    }
}

// ================================================================== dijagnostika
@Composable
private fun DiagnosticsScreen(state: AppState) {
    val status by state.status.collectAsState()
    var log by remember { mutableStateOf(Core.log()) }
    var test by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { while (true) { log = Core.log(); delay(1500) } }
    LaunchedEffect(Unit) {
        val s = state.currentServer()
        val net = Api.ping("1.1.1.1", 443)
        val srv = s?.let { Api.ping(it.host, it.port) }
        test = buildString {
            append(if (net >= 0) "✓ " else "✗ "); append(t("Internet: ", "Internet: ")); append(if (net >= 0) "$net ms" else t("no connection", "nema veze")); append("\n")
            if (s != null) { append(if ((srv ?: -1) >= 0) "✓ " else "✗ "); append("VPN server ${s.host}:${s.port}: "); append(if ((srv ?: -1) >= 0) "$srv ms" else t("not reachable", "ne odgovara")); append("\n") }
            append(t("Engine: ", "Motor: ")); append(if (Core.binary.exists()) Core.binary.path else t("sing-box.exe missing!", "nema sing-box.exe!"))
        }
    }
    Column(Modifier.fillMaxSize()) {
        Title(t("Diagnostics", "Dijagnostika"), t("Status: ", "Stanje: ") + status.name)
        Card(Modifier.fillMaxWidth()) { Text(test ?: t("Testing…", "Testiram…"), color = C.text, fontSize = 14.sp, modifier = Modifier.padding(16.dp)) }
        Spacer(Modifier.height(12.dp))
        var speed by remember { mutableStateOf<String?>(null) }
        var speedRun by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            BigButton(t("Copy report", "Kopiraj izvještaj"), null, filled = false, modifier = Modifier.width(200.dp)) { copyText((test ?: "") + "\n\n" + log) }
            if (status == Status.Started) {
                BigButton(if (speedRun) t("Testing…", "Mjerim…") else t("Speed test", "Test brzine"), null, filled = !speedRun, modifier = Modifier.width(200.dp)) {
                    if (speedRun) return@BigButton
                    speedRun = true
                    scope.launch {
                        val (d, u) = Api.speed { ph -> speed = if (ph == "down") t("Measuring download…", "Mjerim preuzimanje…") else t("Measuring upload…", "Mjerim slanje…") }
                        fun f(x: Double) = if (x < 0) t("error", "greška") else String.format("%.1f Mbps", x)
                        speed = "↓ ${f(d)}    ↑ ${f(u)}"
                        speedRun = false
                    }
                }
                speed?.let { Text(it, color = C.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth().weight(1f)) {
            SelectionContainer {
                Text(log.ifBlank { t("Empty. Connect the VPN to see the engine log.", "Prazno. Spoji VPN da vidiš log motora.") }, color = C.soft, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp))
            }
        }
    }
}
