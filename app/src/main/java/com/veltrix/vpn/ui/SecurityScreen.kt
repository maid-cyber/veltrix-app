package com.veltrix.vpn.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VideoCall
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltrix.vpn.bg.Status
import com.veltrix.vpn.data.Store

@Composable
fun SecurityScreen(vm: MainViewModel, onApps: () -> Unit) {
    val acc by vm.account.collectAsState()
    val sec by vm.sec.collectAsState()
    val splitOn by vm.splitOn.collectAsState()
    val status by vm.status.collectAsState()
    val lockdown by vm.lockdown.collectAsState()
    val alwaysOn by vm.alwaysOn.collectAsState()
    val f = acc?.features ?: com.veltrix.vpn.data.Features()
    val ctx = LocalContext.current
    var ksDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar(acc?.brand ?: "Veltrix VPN", Icons.Outlined.Refresh) { vm.refresh() }
        SectionTitle(t("Security", "Sigurnost"))
        Text(
            t("Changes apply right away. If you are connected, the VPN reconnects in a second.", "Promjene važe odmah. Ako si spojen, VPN se ponovo spoji za sekundu."),
            color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(14.dp))

        SecGroup {
            if (f.killswitch) {
                val ks = lockdown
                SecRow(
                    Icons.Outlined.Lock, "Kill Switch",
                    when {
                        ks -> t("On: no internet without VPN", "Uključen: bez VPN-a nema interneta")
                        alwaysOn -> t("Always-on is on, blocking is off", "Uvijek uključen je upaljen, blokada nije")
                        status == Status.Started -> t("Off. Tap to turn on in Android settings", "Isključen. Dodirni da ga uključiš u postavkama")
                        else -> t("Set up in Android VPN settings", "Uključuje se u Android postavkama VPN-a")
                    },
                    trailing = { Pill(if (ks) t("On", "Uključen") else t("Off", "Isključen"), ks) },
                    onClick = { ksDialog = true },
                )
            }
            if (f.dns) {
                SecSwitch(
                    Icons.Outlined.Dns, t("DNS protection", "DNS zaštita"),
                    if (sec.dnsProtect) {
                        val names = f.dnsFilters.map { when (it) { "ads" -> t("ads", "reklame"); "adult" -> t("adult sites", "sadržaj za odrasle"); else -> t("malware", "malver") } }
                        if (names.isEmpty()) t("DNS through the VPN server", "DNS ide preko VPN servera") else t("Blocks: ", "Blokira: ") + names.joinToString(", ")
                    } else {
                        t("Encrypted 1.1.1.1, no filtering", "Šifrovani 1.1.1.1, bez filtriranja")
                    },
                    sec.dnsProtect,
                ) { vm.setDns(it) }
            }
            if (f.split) {
                SecRow(
                    Icons.Outlined.Apps, "Split tunneling",
                    if (splitOn && Store.splitApps.isNotEmpty()) t("${Store.splitApps.size} apps bypass the VPN", "${Store.splitApps.size} aplikacija ide mimo VPN-a")
                    else t("All apps use the VPN", "Sve aplikacije idu kroz VPN"),
                    trailing = { Icon(Icons.Outlined.ChevronRight, null, tint = C.muted) },
                    onClick = onApps,
                )
            }
            if (f.ipleak) {
                SecSwitch(
                    Icons.Outlined.Shield, t("IP leak protection", "IP leak zaštita"),
                    t("Blocks IPv6 and traffic outside the VPN", "Blokira IPv6 i promet mimo VPN-a"), sec.ipLeak,
                ) { vm.setIpLeak(it) }
            }
            if (f.webrtc) {
                SecSwitch(
                    Icons.Outlined.VideoCall, t("WebRTC protection", "WebRTC zaštita"),
                    t("Blocks STUN requests that reveal IP addresses", "Blokira STUN upite koji otkrivaju IP adresu"), sec.webrtc, last = true,
                ) { vm.setWebrtc(it) }
            }
        }
        if (!f.killswitch && !f.dns && !f.split && !f.ipleak && !f.webrtc) {
            Text(t("Your provider has not enabled extra security options.", "Prodavac nije uključio dodatne sigurnosne opcije."), color = C.muted, modifier = Modifier.padding(20.dp))
        }
        Spacer(Modifier.height(14.dp))
        Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Language, null, tint = C.redHi, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    t("All your traffic is encrypted (VLESS Reality). Websites only see the server's IP address.", "Sav promet je šifrovan (VLESS Reality). Sajtovi vide samo IP adresu servera."),
                    color = C.soft, fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (ksDialog) {
        AlertDialog(
            onDismissRequest = { ksDialog = false },
            containerColor = C.card,
            title = { Text("Kill Switch", color = C.text) },
            text = {
                Text(
                    t(
                        "Android has a built-in kill switch. In the next screen tap the gear next to Veltrix VPN, then turn on:\n\n1. Always-on VPN\n2. Block connections without VPN\n\nAfter that, apps have no internet unless the VPN is connected.",
                        "Android ima ugrađen Kill Switch. Na sljedećem ekranu dodirni zupčanik pored Veltrix VPN, pa uključi:\n\n1. Uvijek uključen VPN\n2. Blokiraj veze bez VPN-a\n\nPoslije toga aplikacije nemaju internet ako VPN nije spojen.",
                    ),
                    color = C.soft,
                )
            },
            confirmButton = {
                TextButton({
                    ksDialog = false
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text(t("Open settings", "Otvori postavke"), color = C.red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton({ ksDialog = false }) { Text(t("Close", "Zatvori"), color = C.soft) } },
        )
    }
}

@Composable
fun SecGroup(content: @Composable () -> Unit) {
    Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth()) { Column { content() } }
}

@Composable
fun SecRow(icon: ImageVector, title: String, sub: String, trailing: @Composable () -> Unit = {}, last: Boolean = false, onClick: (() -> Unit)? = null) {
    var m = Modifier.fillMaxWidth()
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(m.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(C.red.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = C.redHi, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = C.muted, fontSize = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
    if (!last) Box(Modifier.padding(start = 68.dp)) { Divider() }
}

@Composable
fun SecSwitch(icon: ImageVector, title: String, sub: String, on: Boolean, last: Boolean = false, onChange: (Boolean) -> Unit) {
    SecRow(icon, title, sub, trailing = {
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = C.red, checkedThumbColor = Color.White))
    }, last = last, onClick = { onChange(!on) })
}

@Composable
fun Pill(text: String, ok: Boolean) {
    Text(
        text, color = if (ok) C.green else C.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background((if (ok) C.green else C.muted).copy(alpha = .14f)).padding(horizontal = 9.dp, vertical = 3.dp),
    )
}
