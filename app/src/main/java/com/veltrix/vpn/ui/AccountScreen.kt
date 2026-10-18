package com.veltrix.vpn.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CardMembership
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.VpnKey
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltrix.vpn.BuildConfig
import com.veltrix.vpn.data.Store
import java.text.DateFormat
import java.util.Date

@Composable
fun AccountScreen(vm: MainViewModel, onSub: (String) -> Unit) {
    val acc by vm.account.collectAsState()
    val protocol by vm.protocol.collectAsState()
    val auto by vm.autoConnect.collectAsState()
    val boot by vm.bootConnect.collectAsState()
    val nDrop by vm.notifyDrop.collectAsState()
    val nExp by vm.notifyExpiry.collectAsState()
    val upd by vm.update.collectAsState()
    val ctx = LocalContext.current
    var dialog by remember { mutableStateOf("") }
    val df = DateFormat.getDateInstance(DateFormat.MEDIUM)
    val f = acc?.features ?: com.veltrix.vpn.data.Features()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar(acc?.brand ?: "AdiLink VPN", Icons.Outlined.Refresh) { vm.refresh() }
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
                    Text(statusText(acc?.status), color = if (acc?.canConnect == true) C.green else C.redHi, fontSize = 15.sp)
                    acc?.expiresAt?.takeIf { it > 0 }?.let {
                        Text(t("Valid until ", "Vrijedi do ") + df.format(Date(it)), color = C.soft, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        SecGroup {
            val used = acc?.trafficUsed ?: 0
            val limit = acc?.trafficLimit ?: 0
            SecRow(Icons.Outlined.AllInclusive, if (limit > 0) t("Data", "Promet") else t("Unlimited bandwidth", "Neograničen promet"), if (limit > 0) "${fmtBytes(used)} / ${fmtBytes(limit)}" else fmtBytes(used) + t(" used", " potrošeno"))
            SecRow(Icons.Outlined.Public, t("${acc?.servers?.count { !it.isChain } ?: 0} locations", "${acc?.servers?.count { !it.isChain } ?: 0} lokacija"), t("Switch anytime in Locations", "Mijenjaš kad hoćeš u Lokacijama"), last = true)
        }

        // ---------------- podešavanja
        GroupTitle(t("Settings", "Podešavanja"))
        SecGroup {
            if (f.protocol) SecRow(
                Icons.Outlined.VpnKey, t("VPN protocol", "VPN protokol"),
                when (protocol) { "vless" -> "VLESS Reality"; "shadowsocks" -> "Shadowsocks 2022"; else -> t("Automatic (recommended)", "Automatski (preporučeno)") },
                trailing = { Chev() }, onClick = { dialog = "proto" },
            )
            SecRow(
                Icons.Outlined.Autorenew, t("Auto-connect", "Automatsko povezivanje"),
                listOfNotNull(if (auto) t("on app start", "pri otvaranju") else null, if (boot && f.boot) t("on phone start", "pri paljenju telefona") else null).joinToString(", ").ifBlank { t("Off", "Isključeno") },
                trailing = { Chev() }, onClick = { dialog = "auto" },
            )
            if (f.split) SecRow(Icons.Outlined.Apps, t("App management", "Upravljanje aplikacijama"), t("Choose apps that bypass the VPN", "Izaberi aplikacije koje idu mimo VPN-a"), trailing = { Chev() }, onClick = { onSub("apps") })
            if (f.look || f.language) SecRow(
                Icons.Outlined.Palette, t("Appearance", "Izgled aplikacije"),
                listOfNotNull(if (f.look) accentName(Store.accent) + (if (Store.amoled) " · AMOLED" else "") else null, if (f.language) langName(Store.lang) else null).joinToString(" · "),
                trailing = { Chev() }, onClick = { dialog = "look" }, last = !f.notify,
            )
            if (f.notify) SecRow(
                Icons.Outlined.Notifications, t("Notifications", "Obavijesti"),
                listOfNotNull(if (nDrop) t("disconnects", "prekid veze") else null, if (nExp) t("expiry", "istek pretplate") else null).joinToString(", ").ifBlank { t("Off", "Isključene") },
                trailing = { Chev() }, onClick = { dialog = "notif" }, last = true,
            )
        }

        // ---------------- račun
        GroupTitle(t("Account", "Račun"))
        SecGroup {
            if (acc?.renew?.isNotEmpty() == true) {
                SecRow(Icons.Outlined.CreditCard, t("Renew subscription", "Produži pretplatu"), t("PayPal or card, days are added right away", "PayPal ili kartica, dani se dodaju odmah"), trailing = { Chev() }, onClick = { dialog = "renew" })
            }
            acc?.ref?.let { r ->
                SecRow(Icons.Outlined.CardGiftcard, t("Invite a friend", "Pozovi prijatelja"), t("You both get ${r.days} free days", "Obojica dobijete ${r.days} dana gratis"), trailing = { Chev() }, onClick = { dialog = "ref" })
            }
            SecRow(Icons.Outlined.CardMembership, t("Subscription", "Pretplata"), (acc?.plan?.ifBlank { null } ?: "—") + " · " + statusText(acc?.status), trailing = { Chev() }, onClick = { dialog = "sub" })
            if (f.devices) SecRow(Icons.Outlined.Devices, t("Devices", "Uređaji"), t("${acc?.devicesActive ?: 0} of ${acc?.devices ?: 1} connected", "${acc?.devicesActive ?: 0} od ${acc?.devices ?: 1} spojeno"), trailing = { Chev() }, onClick = { onSub("devices") })
            if (f.history) SecRow(Icons.Outlined.History, t("Activity history", "Povijest aktivnosti"), t("${acc?.history?.size ?: 0} entries", "Zapisa: ${acc?.history?.size ?: 0}"), trailing = { Chev() }, onClick = { onSub("history") }, last = !f.support)
            val support = acc?.support.orEmpty()
            if (f.support) SecRow(
                Icons.Outlined.SupportAgent, t("Help & Support", "Podrška"), support.ifBlank { t("Contact your seller", "Javi se prodavcu") },
                trailing = { Chev() }, last = true,
                onClick = {
                    val m = Regex("@([A-Za-z0-9_]{4,})").find(support)
                    val url = when {
                        support.startsWith("http") -> support
                        m != null -> "https://t.me/" + m.groupValues[1]
                        else -> null
                    }
                    if (url != null) runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                },
            )
        }
        Spacer(Modifier.height(14.dp))
        SecGroup {
            if (f.diag) SecRow(Icons.Outlined.NetworkCheck, t("Connection test", "Dijagnostika veze"), t("Find out why the internet does not work", "Provjeri zašto internet ne radi"), trailing = { Chev() }, onClick = { onSub("diag") })
            SecRow(
                Icons.Outlined.Info, t("Version ", "Verzija ") + BuildConfig.VERSION_NAME,
                upd?.let { t("Update available: ", "Dostupna nova: ") + it.version } ?: "sing-box · GPLv3",
                trailing = { Chev() },
                onClick = { if (upd != null) vm.updateDismissed.value = false else if (f.updates) vm.checkUpdate(manual = true) },
            )
            SecRow(Icons.AutoMirrored.Outlined.Logout, t("Log out", "Odjava"), t("You will need your link or QR code again", "Za ponovnu prijavu treba link ili QR kod"), last = true, onClick = { dialog = "logout" })
        }
        Spacer(Modifier.height(24.dp))
    }

    when (dialog) {
        "proto" -> Dlg(t("VPN protocol", "VPN protokol"), { dialog = "" }) {
            listOf("auto" to t("Automatic (recommended)", "Automatski (preporučeno)"), "vless" to "VLESS Reality", "shadowsocks" to "Shadowsocks 2022").forEach { (k, l) ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { vm.setProtocol(k); dialog = "" }.padding(12.dp)) {
                    Text(l, color = if (protocol == k) C.red else C.text, fontSize = 16.sp, fontWeight = if (protocol == k) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        "auto" -> Dlg(t("Auto-connect", "Automatsko povezivanje"), { dialog = "" }) {
            ToggleLine(t("When the app opens", "Kad otvorim aplikaciju"), auto) { vm.setAutoConnect(it) }
            if (f.boot) ToggleLine(t("When the phone starts", "Kad se telefon upali"), boot) { vm.setBoot(it) }
            Text(t("For always-on protection use Kill Switch in Security.", "Za stalnu zaštitu koristi Kill Switch u Sigurnosti."), color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }
        "notif" -> Dlg(t("Notifications", "Obavijesti"), { dialog = "" }) {
            ToggleLine(t("When the VPN disconnects", "Kad se VPN prekine"), nDrop) { vm.setNotifyDrop(it) }
            ToggleLine(t("3 days before expiry", "3 dana prije isteka pretplate"), nExp) { vm.setNotifyExpiry(it) }
        }
        "look" -> Dlg(t("Appearance", "Izgled aplikacije"), { dialog = "" }) {
          if (f.look) {
            Text(t("Accent color", "Boja akcenta"), color = C.soft, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ACCENTS.forEach { (k, c) ->
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(c.first)
                            .border(3.dp, if (Store.accent == k) Color.White else Color.Transparent, CircleShape)
                            .clickable { Store.accent = k; (ctx as? Activity)?.recreate() },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            ToggleLine(t("AMOLED black background", "AMOLED crna pozadina"), Store.amoled) { Store.amoled = it; (ctx as? Activity)?.recreate() }
            Spacer(Modifier.height(10.dp))
          }
          if (f.language) {
            Text(t("Language", "Jezik"), color = C.soft, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("auto" to t("Phone", "Kao telefon"), "bs" to "Bosanski", "en" to "English").forEach { (k, l) ->
                    val on = Store.lang == k
                    Text(
                        l, color = if (on) Color.White else C.soft, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) C.red else C.card2).clickable { Store.lang = k; (ctx as? Activity)?.recreate() }.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
          }
        }
        "renew" -> Dlg(t("Renew subscription", "Produži pretplatu"), { dialog = "" }) {
            val a = acc
            Text(t("Days are added to your current expiry date.", "Dani se dodaju na trenutni datum isteka."), color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
            a?.renew.orEmpty().forEach { p ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(C.card2).border(1.dp, C.line2, RoundedCornerShape(12.dp))
                        .clickable { vm.renew(p.id) { url -> dialog = ""; runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } } }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(t("${p.days} days", "${p.days} dana"), color = C.muted, fontSize = 13.sp)
                    }
                    Text(a!!.currencySign + (if (p.price % 1.0 == 0.0) p.price.toInt().toString() else String.format("%.2f", p.price)), color = C.redHi, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(t("Secure payment. We never see your card details.", "Sigurno plaćanje. Podatke kartice ne vidimo."), color = C.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        "ref" -> Dlg(t("Invite a friend", "Pozovi prijatelja"), { dialog = "" }) {
            val r = acc?.ref
            if (r != null) {
                Text(
                    t("Your friend installs the app, taps \"Try free\" and enters your code. When they pay for the first time, you both get ${r.days} free days.",
                        "Prijatelj instalira aplikaciju, klikne \"Probaj besplatno\" i upiše tvoj kod. Kad prvi put plati, obojica dobijete ${r.days} dana gratis."),
                    color = C.soft, fontSize = 14.sp,
                )
                Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                    Text(r.code, color = C.text, fontSize = 30.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(t("Invited: ${r.invited} · rewarded: ${r.rewarded}", "Pozvano: ${r.invited} · nagrađeno: ${r.rewarded}"), color = C.muted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(14.dp))
                BigButton(t("Share", "Podijeli"), null) {
                    val text = t("Try ${acc?.brand ?: "AdiLink VPN"} free. Enter my code ${r.code} in the app: ", "Probaj ${acc?.brand ?: "AdiLink VPN"} besplatno. U aplikaciji upiši moj kod ${r.code}: ") +
                        "${BuildConfig.PANEL_URL}/download/android"
                    runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null)) }
                }
            }
        }
        "sub" -> Dlg(t("Subscription", "Pretplata"), { dialog = "" }) {
            val a = acc
            KV(t("Plan", "Plan"), a?.plan?.ifBlank { null } ?: "—")
            KV(t("Status", "Status"), statusText(a?.status))
            KV(t("Valid until", "Vrijedi do"), a?.expiresAt?.takeIf { it > 0 }?.let { df.format(Date(it)) } ?: "—")
            KV(t("Data", "Promet"), if ((a?.trafficLimit ?: 0) > 0) "${fmtBytes(a!!.trafficUsed)} / ${fmtBytes(a.trafficLimit)}" else fmtBytes(a?.trafficUsed ?: 0) + " / ∞")
            KV(t("Devices", "Uređaji"), "${a?.devices ?: 1}")
            Text(t("To renew, contact your seller.", "Za produženje se javi prodavcu."), color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        }
        "logout" -> AlertDialog(
            onDismissRequest = { dialog = "" },
            containerColor = C.card,
            title = { Text(t("Log out?", "Odjaviti se?"), color = C.text) },
            text = { Text(t("You will need your access link or QR code to log in again.", "Za ponovnu prijavu trebat će ti pristupni link ili QR kod."), color = C.soft) },
            confirmButton = { TextButton({ dialog = ""; vm.logout() }) { Text(t("Log out", "Odjava"), color = C.red) } },
            dismissButton = { TextButton({ dialog = "" }) { Text(t("Cancel", "Odustani"), color = C.soft) } },
        )
    }
}

fun statusText(st: String?) = when (st) {
    "active", "expiring" -> t("Active VPN access", "VPN pristup aktivan")
    "grace" -> t("Expired, grace period", "Istekao, grace period")
    "expired" -> t("Expired", "Istekao")
    "limited" -> t("Data limit used up", "Potrošen promet")
    "devices" -> t("Too many devices", "Previše uređaja")
    null -> "—"
    else -> t("Disabled", "Ugašen")
}

private fun langName(k: String) = when (k) { "bs" -> "Bosanski"; "en" -> "English"; else -> t("Phone language", "Jezik telefona") }

private fun accentName(k: String) = when (k) {
    "plava" -> t("Blue", "Plava"); "zelena" -> t("Green", "Zelena"); "ljubicasta" -> t("Purple", "Ljubičasta"); else -> t("Red", "Crvena")
}

@Composable
private fun Chev() = Icon(Icons.Outlined.ChevronRight, null, tint = C.muted)

@Composable
private fun GroupTitle(text: String) {
    Text(text, color = C.soft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 22.dp, top = 22.dp, bottom = 8.dp))
}

@Composable
private fun Dlg(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = C.card,
        title = { Text(title, color = C.text) },
        text = { Column { content() } },
        confirmButton = { TextButton(onClose) { Text(t("Close", "Zatvori"), color = C.soft) } },
    )
}

@Composable
private fun ToggleLine(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    var v by remember(on) { mutableStateOf(on) }
    Row(Modifier.fillMaxWidth().clickable { v = !v; onChange(v) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = C.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(v, { v = it; onChange(it) }, colors = SwitchDefaults.colors(checkedTrackColor = C.red, checkedThumbColor = Color.White))
    }
}

@Composable
private fun KV(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(k, color = C.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(v, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
