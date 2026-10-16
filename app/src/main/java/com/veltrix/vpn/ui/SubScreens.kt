package com.veltrix.vpn.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltrix.vpn.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun SubHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, t("Back", "Nazad"), tint = C.text)
        }
        Spacer(Modifier.width(6.dp))
        Text(title, color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------- split tunneling: izbor aplikacija
@Suppress("DEPRECATION")
private fun legacyQuery(pm: PackageManager, intent: Intent) = pm.queryIntentActivities(intent, 0)

private data class AppInfo(val pkg: String, val label: String, val icon: ImageBitmap?)

@Composable
fun AppsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val splitOn by vm.splitOn.collectAsState()
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var chosen by remember { mutableStateOf(Store.splitApps) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = if (Build.VERSION.SDK_INT >= 33) pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L)) else legacyQuery(pm, intent)
            list.map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }.filter { it.packageName != ctx.packageName }.map { ai ->
                val icon = runCatching {
                    val d = pm.getApplicationIcon(ai)
                    val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                    d.setBounds(0, 0, 96, 96); d.draw(Canvas(b)); b.asImageBitmap()
                }.getOrNull()
                AppInfo(ai.packageName, pm.getApplicationLabel(ai).toString(), icon)
            }.sortedBy { it.label.lowercase() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SubHeader("Split tunneling", onBack)
        Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("Let chosen apps bypass the VPN", "Izabrane aplikacije idu mimo VPN-a"), color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(t("Useful for banking apps or local devices", "Korisno za bankarske aplikacije ili uređaje u kući"), color = C.muted, fontSize = 13.sp)
                }
                Switch(splitOn, { vm.setSplitOn(it) }, colors = SwitchDefaults.colors(checkedTrackColor = C.red, checkedThumbColor = Color.White))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp))
                .background(C.card).border(1.dp, C.line2, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, tint = C.muted)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(t("Search apps", "Traži aplikaciju"), color = C.muted, fontSize = 15.sp)
                BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = C.text, fontSize = 15.sp), cursorBrush = SolidColor(C.red), modifier = Modifier.fillMaxWidth())
            }
        }
        Text(
            t("${chosen.size} selected", "Izabrano: ${chosen.size}"),
            color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 4.dp),
        )
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.red) }
        } else {
            val shown = list.filter { query.isBlank() || it.label.contains(query, true) }.sortedByDescending { it.pkg in chosen }
            LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(shown, key = { it.pkg }) { a ->
                    val on = a.pkg in chosen
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) C.red.copy(alpha = .10f) else C.card)
                            .clickable {
                                chosen = if (on) chosen - a.pkg else chosen + a.pkg
                                vm.setSplitApps(chosen)
                            }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (a.icon != null) Image(a.icon, null, Modifier.size(36.dp)) else Box(Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.label, color = C.text, fontSize = 15.sp)
                            Text(a.pkg, color = C.muted, fontSize = 11.sp, maxLines = 1)
                        }
                        Checkbox(on, null, colors = CheckboxDefaults.colors(checkedColor = C.red))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- uređaji
@Composable
fun DevicesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val acc by vm.account.collectAsState()
    val limit = acc?.devices ?: 1
    val active = acc?.devicesActive ?: 0
    Column(Modifier.fillMaxSize()) {
        SubHeader(t("Devices", "Uređaji"), onBack)
        Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), glow = true) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Devices, null, tint = C.redHi, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(t("Connected now", "Spojeno sada"), color = C.soft, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text("$active / $limit", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(C.line2)) {
                    Box(Modifier.fillMaxWidth((active.toFloat() / limit).coerceIn(0f, 1f)).height(8.dp).background(if (active > limit) C.amber else C.red))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    t(
                        "Your plan allows $limit devices at the same time. The server counts different IP addresses in the last 3 minutes.",
                        "Tvoj plan dozvoljava $limit uređaja u isto vrijeme. Server broji različite IP adrese u zadnje 3 minute.",
                    ),
                    color = C.muted, fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PhoneAndroid, null, tint = C.green, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}", color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("Android ${Build.VERSION.RELEASE} · " + t("this device", "ovaj uređaj"), color = C.muted, fontSize = 13.sp)
                }
                Pill(t("This one", "Ovaj"), true)
            }
        }
        Text(
            t(
                "If you go over the limit, the account is paused for 15 minutes. Disconnect the VPN on a device you no longer use.",
                "Ako pređeš limit, nalog se pauzira na 15 minuta. Ugasi VPN na uređaju koji ne koristiš.",
            ),
            color = C.muted, fontSize = 13.sp, modifier = Modifier.padding(20.dp),
        )
    }
}

// ---------------------------------------------------------------- povijest aktivnosti
@Composable
fun HistoryScreen(vm: MainViewModel, onBack: () -> Unit) {
    val acc by vm.account.collectAsState()
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val items = acc?.history.orEmpty()
    Column(Modifier.fillMaxSize()) {
        SubHeader(t("Activity history", "Povijest aktivnosti"), onBack)
        if (items.isEmpty()) {
            Text(t("No activity yet.", "Još nema aktivnosti."), color = C.muted, modifier = Modifier.padding(20.dp))
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { h ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(C.red.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.History, null, tint = C.redHi, modifier = Modifier.size(19.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.text, color = C.text, fontSize = 14.sp)
                            Text(df.format(Date(h.ts)), color = C.muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
