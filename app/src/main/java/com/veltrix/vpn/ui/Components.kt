package com.veltrix.vpn.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltrix.vpn.data.Store

// ---------------------------------------------------------------- boje
object C {
    val bg = Color(0xFF0B0B0F)
    val card = Color(0xFF15151B)
    val card2 = Color(0xFF1B1B22)
    val line = Color(0xFF2A1A1E)
    val line2 = Color(0xFF34343F)
    val red = Color(0xFFE3172B)
    val redDark = Color(0xFF9E0C1A)
    val redHi = Color(0xFFFF4A5A)
    val text = Color(0xFFF4F4F6)
    val soft = Color(0xFFC7C7D1)
    val muted = Color(0xFF9A9AA8)
    val green = Color(0xFF3DDC84)
    val amber = Color(0xFFF5A524)
}

/** Tekst na bosanskom ili engleskom, prema jeziku telefona. */
fun t(en: String, bs: String) = if (Store.bosnian()) bs else en

// ---------------------------------------------------------------- logo (štit sa V)
private fun p(vararg pts: Float, close: Boolean = true) = Path().apply {
    moveTo(pts[0], pts[1])
    var i = 2
    while (i < pts.size) { lineTo(pts[i], pts[i + 1]); i += 2 }
    if (close) close()
}

private val shieldOuter = Path().apply {
    moveTo(100f, 4f); lineTo(190f, 38f); lineTo(190f, 102f)
    cubicTo(190f, 158f, 154f, 200f, 100f, 220f); cubicTo(46f, 200f, 10f, 158f, 10f, 102f)
    lineTo(10f, 38f); close()
}
private val shieldInner = Path().apply {
    moveTo(100f, 22f); lineTo(174f, 50f); lineTo(174f, 102f)
    cubicTo(174f, 148f, 145f, 182f, 100f, 199f); cubicTo(55f, 182f, 26f, 148f, 26f, 102f)
    lineTo(26f, 50f); close()
}
private val vLeft = p(48f, 70f, 80f, 70f, 100f, 138f, 100f, 184f)
private val vRight = p(152f, 70f, 120f, 70f, 100f, 138f, 100f, 184f)
private val vTop = p(86f, 58f, 114f, 58f, 100f, 96f)

@Composable
fun Logo(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size, size * 1.12f)) {
        val s = this.size.width / 200f
        scale(s, s, pivot = Offset.Zero) {
            drawPath(shieldOuter, C.red)
            drawPath(shieldInner, Color(0xFF121216))
            drawPath(vLeft, C.red)
            drawPath(vRight, C.redDark)
            drawPath(vTop, C.redHi)
        }
    }
}

@Composable
fun TopBar(brand: String, icon: ImageVector? = null, onIcon: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Logo(30.dp)
        Spacer(Modifier.width(12.dp))
        Text(brand, color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (icon != null) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onIcon), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = C.soft, modifier = Modifier.size(24.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- zastava u krugu
@Composable
fun Flag(emoji: String, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(C.card2).border(1.dp, C.line2, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size.value * 0.58f).sp)
    }
}

// ---------------------------------------------------------------- kartica
@Composable
fun Card(modifier: Modifier = Modifier, glow: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    var m = modifier.clip(shape)
        .background(if (glow) Brush.linearGradient(listOf(Color(0xFF2A0E14), C.card)) else Brush.linearGradient(listOf(C.card, C.card)))
        .border(1.dp, if (glow) C.red.copy(alpha = .55f) else C.line, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Box(m) { content() }
}

@Composable
fun PingDot(ms: Int?) {
    val color = when {
        ms == null -> C.muted
        ms < 0 -> C.red
        ms < 120 -> C.green
        ms < 250 -> C.amber
        else -> C.red
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            when {
                ms == null -> "…"
                ms < 0 -> t("offline", "ne odgovara")
                else -> "$ms ms"
            },
            color = C.soft, fontSize = 13.sp,
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = C.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
}

@Composable
fun BigButton(text: String, icon: ImageVector?, filled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(shape)
            .background(if (filled) Brush.verticalGradient(listOf(Color(0xFFE3172B), Color(0xFFB0101F))) else Brush.verticalGradient(listOf(C.card, C.card)))
            .border(1.dp, if (filled) C.redHi.copy(alpha = .6f) else C.line2, shape)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

val Small = TextStyle(color = C.muted, fontSize = 13.sp)

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

@Composable
fun ColumnGap(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))

@Composable
fun Glow(modifier: Modifier, color: Color, radiusFraction: Float = .5f) {
    Canvas(modifier) {
        drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = size.minDimension * radiusFraction))
    }
}

