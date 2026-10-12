package com.adhavaa.commander.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

val PanelShape = RoundedCornerShape(10.dp)
private val ChipShape = RoundedCornerShape(4.dp)

/** A hairline-bordered panel with an optional title row, like the instrument panels in the reference. */
@Composable
fun HudPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    background: Brush = Brush.verticalGradient(listOf(Hud.panel, Hud.panel)),
    border: Color = Hud.line,
    padding: PaddingValues = PaddingValues(14.dp),
    /** Drawn over the background and under the content, e.g. [contours]. */
    decoration: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(background)
            .then(decoration)
            .border(BorderStroke(1.dp, border), PanelShape)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null || trailing != null) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (title != null) Text(title.uppercase(), style = HudType.title, color = Hud.ink)
                    if (subtitle != null) Text(subtitle, style = HudType.label, color = Hud.muted)
                }
                trailing?.invoke()
            }
        }
        content()
    }
}

enum class Tone { GREEN, AMBER, MUTED, SOLID }

@Composable
fun HudChip(text: String, tone: Tone = Tone.MUTED, modifier: Modifier = Modifier) {
    val (bg, fg) = when (tone) {
        Tone.GREEN -> Hud.greenDeep to Hud.green
        Tone.AMBER -> Hud.amberDeep to Hud.amber
        Tone.MUTED -> Hud.panelHi to Hud.muted
        Tone.SOLID -> Hud.green to Hud.onMint
    }
    Text(
        text.uppercase(),
        style = HudType.label,
        color = fg,
        maxLines = 1,
        modifier = modifier
            .clip(ChipShape)
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

/** A small square button with a hairline border, used for panel actions. */
@Composable
fun SquareButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    size: Dp = 34.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) Hud.greenDeep else Hud.panelHi)
            .border(1.dp, if (active) Hud.green.copy(alpha = 0.5f) else Hud.line, RoundedCornerShape(6.dp))
            .clickable(onClickLabel = contentDescription, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** One cell of the telemetry strip: tiny label over a mono value. */
@Composable
fun StatCell(label: String, value: String, modifier: Modifier = Modifier, accent: Color = Hud.ink) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Hud.panel)
            .border(1.dp, Hud.line, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label.uppercase(), style = HudType.label, color = Hud.muted, maxLines = 1)
        Text(value, style = HudType.value, color = accent, maxLines = 1)
    }
}

@Composable
fun KeyValue(key: String, value: String, valueColor: Color = Hud.ink) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(key.uppercase(), style = HudType.label, color = Hud.muted, modifier = Modifier.weight(1f))
        Text(value, style = HudType.value, color = valueColor, textAlign = TextAlign.End)
    }
}

/** The app mark: three chevrons on a signal-green tile. */
@Composable
fun Mark(size: Dp = 30.dp) {
    Canvas(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(7.dp))
            .background(Hud.green),
    ) {
        val s = this.size.width / 24f
        for (k in 0..2) {
            val y = (5f + k * 5.5f) * s
            val p = Path().apply {
                moveTo(6f * s, y + 4.5f * s); lineTo(12f * s, y); lineTo(18f * s, y + 4.5f * s)
                lineTo(18f * s, y + 6.8f * s); lineTo(12f * s, y + 2.3f * s); lineTo(6f * s, y + 6.8f * s); close()
            }
            drawPath(p, Hud.onMint)
        }
    }
}

private fun field(x: Float, y: Float): Float =
    sin(x * 1.1f + 0.3f) * cos(y * 0.9f - 0.5f) +
        0.55f * sin(x * 0.45f - y * 1.3f + 1.7f) +
        0.35f * cos(x * 1.9f + y * 0.7f - 0.9f)

/**
 * Topographic contour lines (marching squares over a smooth field), drawn behind content. Computed once per size.
 */
fun Modifier.contours(color: Color = Hud.contour, seed: Float = 0f, levels: Int = 9): Modifier = drawWithCache {
    val cell = 12.dp.toPx()
    val scale = 1f / 95.dp.toPx()
    val cols = (size.width / cell).toInt() + 2
    val rows = (size.height / cell).toInt() + 2
    val v = Array(rows) { j -> FloatArray(cols) { i -> field(i * cell * scale + seed, j * cell * scale - seed * 0.6f) } }
    val path = Path()
    fun seg(a: Offset, b: Offset) {
        path.moveTo(a.x, a.y); path.lineTo(b.x, b.y)
    }
    for (lv in 0 until levels) {
        val iso = -1.5f + lv * (3f / (levels - 1))
        for (j in 0 until rows - 1) for (i in 0 until cols - 1) {
            val a = v[j][i]; val b = v[j][i + 1]; val c = v[j + 1][i + 1]; val d = v[j + 1][i]
            var idx = 0
            if (a > iso) idx = idx or 8
            if (b > iso) idx = idx or 4
            if (c > iso) idx = idx or 2
            if (d > iso) idx = idx or 1
            if (idx == 0 || idx == 15) continue
            fun t(p: Float, q: Float) = ((iso - p) / (q - p)).coerceIn(0f, 1f)
            val x0 = i * cell; val y0 = j * cell
            val top = Offset(x0 + t(a, b) * cell, y0)
            val right = Offset(x0 + cell, y0 + t(b, c) * cell)
            val bottom = Offset(x0 + t(d, c) * cell, y0 + cell)
            val left = Offset(x0, y0 + t(a, d) * cell)
            when (idx) {
                1, 14 -> seg(left, bottom)
                2, 13 -> seg(bottom, right)
                3, 12 -> seg(left, right)
                4, 11 -> seg(top, right)
                5 -> { seg(left, top); seg(bottom, right) }
                6, 9 -> seg(top, bottom)
                7, 8 -> seg(left, top)
                10 -> { seg(left, bottom); seg(top, right) }
            }
        }
    }
    val stroke = Stroke(width = 1.dp.toPx())
    onDrawBehind { drawPath(path, color, style = stroke) }
}
