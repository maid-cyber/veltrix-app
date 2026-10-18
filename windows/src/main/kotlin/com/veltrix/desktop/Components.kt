package com.veltrix.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ACCENTS = linkedMapOf(
    "crvena" to Triple(Color(0xFFE3172B), Color(0xFF9E0C1A), Color(0xFFFF4A5A)),
    "plava" to Triple(Color(0xFF2F6BFF), Color(0xFF1A3FA8), Color(0xFF6B9BFF)),
    "zelena" to Triple(Color(0xFF1FB36B), Color(0xFF0D6B3F), Color(0xFF5FE0A0)),
    "ljubicasta" to Triple(Color(0xFF8A4DFF), Color(0xFF4C22A3), Color(0xFFB68CFF)),
)

object C {
    private val acc get() = ACCENTS[Store.accent] ?: ACCENTS.getValue("crvena")
    val bg = Color(0xFF07091A)
    val side = Color(0xFF0A0E22)
    val card = Color(0xFF10152B)
    val card2 = Color(0xFF151B35)
    val line = Color(0xFF1C2444)
    val line2 = Color(0xFF2A3560)
    val red get() = acc.first
    val redDark get() = acc.second
    val redHi get() = acc.third
    val text = Color(0xFFF2F4FB)
    val soft = Color(0xFFC5CDE0)
    val muted = Color(0xFF8E9AB8)
    val green = Color(0xFF3DDC84)
    val amber = Color(0xFFF5A524)
}

fun t(en: String, bs: String) = if (Store.bosnian()) bs else en

private fun p(vararg pts: Float) = Path().apply {
    moveTo(pts[0], pts[1]); var i = 2
    while (i < pts.size) { lineTo(pts[i], pts[i + 1]); i += 2 }
    close()
}
private val shieldOuter = Path().apply {
    moveTo(100f, 4f); lineTo(190f, 38f); lineTo(190f, 102f)
    cubicTo(190f, 158f, 154f, 200f, 100f, 220f); cubicTo(46f, 200f, 10f, 158f, 10f, 102f); lineTo(10f, 38f); close()
}
private val shieldInner = Path().apply {
    moveTo(100f, 22f); lineTo(174f, 50f); lineTo(174f, 102f)
    cubicTo(174f, 148f, 145f, 182f, 100f, 199f); cubicTo(55f, 182f, 26f, 148f, 26f, 102f); lineTo(26f, 50f); close()
}
private val vLeft = p(48f, 70f, 80f, 70f, 100f, 138f, 100f, 184f)
private val vRight = p(152f, 70f, 120f, 70f, 100f, 138f, 100f, 184f)
private val vTop = p(86f, 58f, 114f, 58f, 100f, 96f)

@Composable
fun Logo(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size, size * 1.12f)) {
        val s = this.size.width / 200f
        scale(s, s, pivot = Offset.Zero) {
            drawPath(shieldOuter, C.red); drawPath(shieldInner, Color(0xFF0E1124))
            drawPath(vLeft, C.red); drawPath(vRight, C.redDark); drawPath(vTop, C.redHi)
        }
    }
}

// zastave su PNG u resources/flags (Windows ne prikazuje emoji zastave); učitaju se jednom
private val flagCache = HashMap<String, androidx.compose.ui.graphics.ImageBitmap?>()
private fun flagBitmap(cc: String): androidx.compose.ui.graphics.ImageBitmap? {
    val k = cc.lowercase()
    if (k.length != 2 || !k.all { it in 'a'..'z' }) return null
    return flagCache.getOrPut(k) {
        runCatching {
            Thread.currentThread().contextClassLoader.getResourceAsStream("flags/$k.png")
                ?.use { androidx.compose.ui.res.loadImageBitmap(it) }
        }.getOrNull()
    }
}

/** Zastava države u krugu; bez zastave ostaje oznaka sa slovima. */
@Composable
fun CountryBadge(cc: String, size: Dp = 42.dp) {
    val flag = remember(cc) { flagBitmap(cc) }
    if (flag != null) {
        androidx.compose.foundation.Image(
            flag, cc,
            Modifier.size(size).clip(CircleShape).border(1.dp, Color.White.copy(alpha = .14f), CircleShape),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        return
    }
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(C.red.copy(alpha = .9f), Color(0xFF6B3FE0)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(cc.ifBlank { "VPN" }.take(3).uppercase(), color = Color.White, fontSize = (size.value * 0.32f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, glow: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val src = remember { MutableInteractionSource() }
    val hover by src.collectIsHoveredAsState()
    var m = modifier.clip(shape)
        .background(if (glow) Brush.linearGradient(listOf(C.red.copy(alpha = .16f), C.card)) else Brush.linearGradient(listOf(C.card2.copy(alpha = .7f), C.card)))
        .border(1.dp, if (glow) C.red.copy(alpha = .55f) else if (hover && onClick != null) C.line2 else C.line, shape)
        .hoverable(src)
    if (onClick != null) m = m.clickable(interactionSource = src, indication = null, onClick = onClick)
    Box(m) { content() }
}

@Composable
fun PingDot(ms: Int?) {
    val color = when { ms == null -> C.muted; ms < 0 -> C.red; ms < 120 -> C.green; ms < 250 -> C.amber; else -> C.red }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(when { ms == null -> "…"; ms < 0 -> t("offline", "ne odgovara"); else -> "$ms ms" }, color = C.soft, fontSize = 13.sp)
    }
}

@Composable
fun Title(text: String, sub: String? = null) {
    Column(Modifier.padding(bottom = 18.dp)) {
        Text(text, color = C.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        if (sub != null) Text(sub, color = C.muted, fontSize = 14.sp)
    }
}

@Composable
fun BigButton(text: String, icon: ImageVector?, filled: Boolean = true, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.height(50.dp).clip(shape)
            .background(if (filled) Brush.verticalGradient(listOf(C.redHi, C.red)) else Brush.verticalGradient(listOf(C.card2, C.card)))
            .border(1.dp, if (filled) C.redHi.copy(alpha = .6f) else C.line2, shape)
            .clickable(onClick = onClick).padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))

@Composable
fun SetRow(icon: ImageVector, title: String, sub: String, last: Boolean = false, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    var m = Modifier.fillMaxWidth()
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(m.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(C.red.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = C.redHi, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = C.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = C.muted, fontSize = 13.sp)
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
    if (!last) Box(Modifier.padding(start = 70.dp)) { Divider() }
}

@Composable
fun SetSwitch(icon: ImageVector, title: String, sub: String, on: Boolean, last: Boolean = false, onChange: (Boolean) -> Unit) {
    SetRow(icon, title, sub, last, onClick = { onChange(!on) }) {
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = C.red, checkedThumbColor = Color.White, uncheckedTrackColor = C.card2))
    }
}

@Composable
fun Pill(text: String, ok: Boolean) {
    Text(
        text, color = if (ok) C.green else C.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background((if (ok) C.green else C.muted).copy(alpha = .14f)).padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

fun fmtBytes(n: Long): String = when {
    n >= 1L shl 40 -> String.format("%.2f TB", n / (1L shl 40).toDouble())
    n >= 1L shl 30 -> String.format("%.1f GB", n / (1L shl 30).toDouble())
    n >= 1L shl 20 -> String.format("%.0f MB", n / (1L shl 20).toDouble())
    else -> String.format("%.0f KB", n / 1024.0)
}

fun fmtSpeed(bps: Long): Pair<String, String> {
    val mbit = bps * 8 / 1_000_000.0
    return if (mbit >= 1) String.format("%.1f", mbit) to "Mbps" else String.format("%.0f", bps * 8 / 1000.0) to "Kbps"
}

fun tagName(k: String) = when (k) {
    "p2p" -> "P2P"; "streaming" -> "Streaming"; "gaming" -> "Gaming"; "special" -> t("Special", "Specijalni")
    "static" -> t("Static IP", "Statička IP"); "dedicated" -> t("Your dedicated IP", "Tvoja IP"); "obfuscated" -> t("Obfuscated", "Zaštita od blokada")
    "double" -> "Double VPN"; "multihop" -> "Multihop"; else -> k
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
