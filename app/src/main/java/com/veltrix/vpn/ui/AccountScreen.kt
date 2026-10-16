package com.veltrix.vpn.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
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
import com.veltrix.vpn.BuildConfig
import java.text.DateFormat
import java.util.Date

@Composable
fun AccountScreen(vm: MainViewModel) {
    val acc by vm.account.collectAsState()
    val protocol by vm.protocol.collectAsState()
    val auto by vm.autoConnect.collectAsState()
    val ctx = LocalContext.current
    var protoDialog by remember { mutableStateOf(false) }
    var logoutDialog by remember { mutableStateOf(false) }
    val df = DateFormat.getDateInstance(DateFormat.MEDIUM)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar(acc?.brand ?: "Veltrix VPN", Icons.Outlined.Refresh) { vm.refresh() }
        SectionTitle(t("My Account", "Moj nalog"))
        Spacer(Modifier.height(12.dp))

        // profil
        Row(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(C.red.copy(alpha = .15f)).border(2.dp, C.red, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Person, null, tint = C.red, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(acc?.username ?: "—", color = C.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(t("Member since ", "Član od ") + (acc?.createdAt?.takeIf { it > 0 }?.let { df.format(Date(it)) } ?: "—"), color = C.muted, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(16.dp))

        // pretplata
        Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), glow = true) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Logo(56.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text((acc?.plan?.ifBlank { null } ?: "Premium").uppercase(), color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    val st = acc?.status
                    Text(
                        when (st) {
                            "active", "expiring" -> t("Active VPN access", "VPN pristup aktivan")
                            "grace" -> t("Expired, grace period", "Istekao, grace period")
                            "expired" -> t("Expired", "Istekao")
                            "limited" -> t("Data limit used up", "Potrošen promet")
                            "devices" -> t("Too many devices", "Previše uređaja")
                            else -> t("Disabled", "Ugašen")
                        },
                        color = if (acc?.canConnect == true) C.green else C.redHi, fontSize = 15.sp,
                    )
                    acc?.expiresAt?.let {
                        Text(t("Valid until ", "Vrijedi do ") + df.format(Date(it)), color = C.soft, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // detalji
        Group {
            val used = acc?.trafficUsed ?: 0
            val limit = acc?.trafficLimit ?: 0
            Item(Icons.Outlined.AllInclusive, if (limit > 0) t("Data: ", "Promet: ") + "${fmtBytes(used)} / ${fmtBytes(limit)}" else t("Unlimited bandwidth", "Neograničen promet"))
            Item(Icons.Outlined.Devices, t("${acc?.devices ?: 1} devices", "${acc?.devices ?: 1} uređaja"))
            Item(Icons.Outlined.Public, t("${acc?.servers?.size ?: 0} locations", "${acc?.servers?.size ?: 0} lokacija"), last = true)
        }
        Spacer(Modifier.height(14.dp))

        // postavke
        Group {
            Item(
                Icons.Outlined.Shield, t("Protocol: ", "Protokol: ") + when (protocol) { "vless" -> "VLESS Reality"; "shadowsocks" -> "Shadowsocks"; else -> t("Automatic", "Automatski") },
                onClick = { protoDialog = true },
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Bolt, null, tint = C.soft, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(t("Auto-connect on app start", "Automatski spoji pri otvaranju"), color = C.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Switch(auto, { vm.setAutoConnect(it) }, colors = SwitchDefaults.colors(checkedTrackColor = C.red, checkedThumbColor = Color.White))
            }
            Divider()
            val support = acc?.support.orEmpty()
            Item(
                Icons.AutoMirrored.Outlined.HelpOutline, t("Help & Support", "Pomoć i podrška") + if (support.isNotBlank()) " · $support" else "",
                onClick = {
                    val url = when {
                        support.startsWith("http") -> support
                        Regex("@([A-Za-z0-9_]{4,})").find(support) != null -> "https://t.me/" + Regex("@([A-Za-z0-9_]{4,})").find(support)!!.groupValues[1]
                        else -> null
                    }
                    if (url != null) runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                },
            )
            val upd by vm.update.collectAsState()
            Item(
                Icons.Outlined.Info,
                t("Version ", "Verzija ") + BuildConfig.VERSION_NAME + (upd?.let { t(" · update ", " · nova ") + it.version } ?: ""),
                onClick = { if (upd != null) vm.updateDismissed.value = false else vm.checkUpdate(manual = true) },
            )
            Item(Icons.AutoMirrored.Outlined.Logout, t("Log out", "Odjava"), danger = true, last = true, onClick = { logoutDialog = true })
        }
        Spacer(Modifier.height(24.dp))
    }

    if (protoDialog) {
        AlertDialog(
            onDismissRequest = { protoDialog = false },
            containerColor = C.card,
            title = { Text(t("Protocol", "Protokol"), color = C.text) },
            text = {
                Column {
                    listOf("auto" to t("Automatic (recommended)", "Automatski (preporučeno)"), "vless" to "VLESS Reality", "shadowsocks" to "Shadowsocks 2022").forEach { (k, l) ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { vm.setProtocol(k); protoDialog = false }.padding(12.dp)) {
                            Text(l, color = if (protocol == k) C.red else C.text, fontSize = 16.sp, fontWeight = if (protocol == k) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ protoDialog = false }) { Text(t("Close", "Zatvori"), color = C.soft) } },
        )
    }
    if (logoutDialog) {
        AlertDialog(
            onDismissRequest = { logoutDialog = false },
            containerColor = C.card,
            title = { Text(t("Log out?", "Odjaviti se?"), color = C.text) },
            text = { Text(t("You will need your access link or QR code to log in again.", "Za ponovnu prijavu trebat će ti pristupni link ili QR kod."), color = C.soft) },
            confirmButton = { TextButton({ logoutDialog = false; vm.logout() }) { Text(t("Log out", "Odjava"), color = C.red) } },
            dismissButton = { TextButton({ logoutDialog = false }) { Text(t("Cancel", "Odustani"), color = C.soft) } },
        )
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth()) { Column { content() } }
}

@Composable
private fun Item(icon: ImageVector, text: String, danger: Boolean = false, last: Boolean = false, onClick: (() -> Unit)? = null) {
    var m = Modifier.fillMaxWidth()
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(m.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (danger) C.red else C.red.copy(alpha = .9f), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, color = if (danger) C.redHi else C.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (onClick != null) Icon(Icons.Outlined.ChevronRight, null, tint = C.muted)
    }
    if (!last) Box(Modifier.padding(start = 56.dp)) { Divider() }
}
