package com.veltrix.vpn.ui

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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LocationsScreen(vm: MainViewModel, onPicked: () -> Unit) {
    val acc by vm.account.collectAsState()
    val pings by vm.pings.collectAsState()
    val favs by vm.favorites.collectAsState()
    val selected by vm.selected.collectAsState()
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }   // 0 svi, 1 omiljeni

    val all = acc?.servers.orEmpty()
    val list = all
        .filter { tab == 0 || it.id in favs }
        .filter { query.isBlank() || listOf(it.name, it.city, it.countryName, it.country).any { f -> f.contains(query, true) } }
        .sortedBy { pings[it.id]?.takeIf { p -> p >= 0 } ?: 99999 }

    Column(Modifier.fillMaxSize()) {
        TopBar(acc?.brand ?: "Veltrix VPN", Icons.Outlined.Refresh) { vm.refresh() }
        SectionTitle(t("Locations", "Lokacije"))
        Spacer(Modifier.height(12.dp))

        // pretraga
        Row(
            Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                .background(C.card).border(1.dp, C.line2, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, tint = C.muted)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(t("Search countries or cities", "Traži državu ili grad"), color = C.muted, fontSize = 16.sp)
                BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = C.text, fontSize = 16.sp), cursorBrush = SolidColor(C.red), modifier = Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(14.dp))

        // filteri
        Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip(t("All", "Svi"), tab == 0) { tab = 0 }
            Chip(t("Favorites", "Omiljeni"), tab == 1, Icons.Outlined.StarBorder) { tab = 1 }
        }
        Spacer(Modifier.height(10.dp))

        LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (tab == 0 && query.isBlank()) {
                item {
                    Card(Modifier.fillMaxWidth(), glow = selected == -1, onClick = { vm.select(-1); onPicked() }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(C.red.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Bolt, null, tint = C.red, modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t("Fastest server", "Najbrži server"), color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                Text(t("Chosen automatically", "Bira se automatski"), color = C.muted, fontSize = 14.sp)
                            }
                            if (selected == -1) Icon(Icons.Outlined.CheckCircle, null, tint = C.red)
                        }
                    }
                }
            }
            items(list, key = { it.id }) { s ->
                Card(Modifier.fillMaxWidth(), glow = selected == s.id, onClick = { vm.select(s.id); onPicked() }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Flag(s.flag, 48.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.countryName, color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text(s.city, color = C.muted, fontSize = 14.sp)
                            Box(Modifier.padding(top = 4.dp)) { PingDot(pings[s.id]) }
                        }
                        Icon(
                            if (s.id in favs) Icons.Filled.Star else Icons.Outlined.StarBorder, null,
                            tint = if (s.id in favs) C.red else C.muted,
                            modifier = Modifier.size(30.dp).clip(CircleShape).clickable { vm.toggleFavorite(s.id) }.padding(2.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Outlined.ChevronRight, null, tint = C.muted)
                    }
                }
            }
            if (list.isEmpty()) {
                item {
                    Text(
                        if (tab == 1) t("No favorites yet. Tap the star next to a server.", "Još nema omiljenih. Dodirni zvjezdicu kod servera.")
                        else t("No servers found.", "Nema servera."),
                        color = C.muted, fontSize = 15.sp, modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.height(42.dp).clip(shape)
            .background(if (on) C.red else Color.Transparent)
            .border(1.dp, if (on) C.red else C.line2, shape)
            .clickable(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (on) Color.White else C.red, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = if (on) Color.White else C.soft, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
