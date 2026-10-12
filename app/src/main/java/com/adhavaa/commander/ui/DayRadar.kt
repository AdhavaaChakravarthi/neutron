package com.adhavaa.commander.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adhavaa.commander.model.TodayOrders
import com.adhavaa.commander.orders.Order
import com.adhavaa.commander.orders.OrderKind
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The day as a 24-hour sonar dial: 00:00 at the top, clockwise. Outer band shows sleep, grogginess, the sharpest
 * window and wind-down; blips are today's orders; the sweep marks now.
 */
@Composable
fun DayRadar(
    orders: TodayOrders,
    list: List<Order>,
    next: Order?,
    now: LocalDateTime,
    fmt: (LocalDateTime) -> String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val hourStyle = HudType.label.copy(color = Hud.muted)
    val nowStyle = HudType.bigValue.copy(color = Hud.ink, fontSize = 20.sp)
    val nowLabel = HudType.label.copy(color = Hud.green)
    val description = "24-hour radar. Sleep from ${fmt(orders.lightsOut)} to ${fmt(orders.nextWake)}. " +
        "Sharpest ${fmt(orders.peakStart)} to ${fmt(orders.peakEnd)}. Now ${fmt(now)}."

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics { contentDescription = description },
    ) {
        val c = center
        val r = min(size.width, size.height) / 2f - 22.dp.toPx()
        fun deg(t: LocalDateTime): Float = (t.hour * 60 + t.minute) / 1440f * 360f - 90f
        fun polar(d: Float, rad: Float): Offset {
            val a = d * PI.toFloat() / 180f
            return Offset(c.x + rad * cos(a), c.y + rad * sin(a))
        }

        // Range rings and crosshair.
        for (f in listOf(0.25f, 0.5f, 0.75f, 1f)) {
            drawCircle(Hud.line, r * f, c, style = Stroke(1.dp.toPx()))
        }
        drawLine(Hud.grid, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
        drawLine(Hud.grid, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx())

        // Hour ticks and labels.
        for (h in 0 until 24) {
            val d = h * 15f - 90f
            val inner = if (h % 6 == 0) r * 0.9f else r * 0.95f
            drawLine(if (h % 6 == 0) Hud.muted else Hud.dim, polar(d, inner), polar(d, r), 1.dp.toPx())
            if (h % 6 == 0) {
                val layout = measurer.measure(AnnotatedString(h.toString().padStart(2, '0')), style = hourStyle)
                val p = polar(d, r + 12.dp.toPx())
                drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
            }
        }

        // Day bands.
        val band = r * 0.82f
        val bandW = 12.dp.toPx()
        fun arc(from: LocalDateTime, to: LocalDateTime, color: Color) {
            val start = deg(from)
            var sweep = deg(to) - start
            if (sweep < 0f) sweep += 360f
            drawArc(
                color, start, sweep, useCenter = false,
                topLeft = Offset(c.x - band, c.y - band), size = Size(band * 2, band * 2),
                style = Stroke(bandW, cap = StrokeCap.Butt),
            )
        }
        arc(orders.lightsOut, orders.nextWake, Hud.dim.copy(alpha = 0.55f))
        arc(orders.wake, orders.groggyUntil, Hud.muted.copy(alpha = 0.45f))
        arc(orders.peakStart, orders.peakEnd, Hud.green.copy(alpha = 0.85f))
        arc(orders.windDown, orders.lightsOut, Hud.amber.copy(alpha = 0.85f))

        // Sweep: a fading wedge trailing the "now" line.
        val nowDeg = deg(now)
        rotate(nowDeg, pivot = c) {
            drawArc(
                brush = Brush.sweepGradient(
                    0f to Hud.green.copy(alpha = 0.32f),
                    0.88f to Color.Transparent,
                    1f to Hud.green.copy(alpha = 0.32f),
                    center = c,
                ),
                startAngle = -42f, sweepAngle = 42f, useCenter = true,
                topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
            )
        }
        drawLine(Hud.green, c, polar(nowDeg, r), 1.5.dp.toPx())

        // Order blips.
        for (o in list) {
            if (o.kind == OrderKind.GROGGY || o.kind == OrderKind.TOMORROW) continue
            val warn = o.kind == OrderKind.CAFFEINE || o.kind == OrderKind.LIGHTS_OUT
            val color = when {
                o.at.isBefore(now) -> Hud.dim
                warn -> Hud.amber
                else -> Hud.green
            }
            val p = polar(deg(o.at), r * 0.6f)
            if (o == next) drawCircle(color.copy(alpha = 0.35f), 9.dp.toPx(), p, style = Stroke(1.dp.toPx()))
            drawCircle(color, 3.5.dp.toPx(), p)
        }

        // Centre readout.
        drawCircle(Hud.bg, r * 0.24f, c)
        drawCircle(Hud.line, r * 0.24f, c, style = Stroke(1.dp.toPx()))
        centreText(measurer.measure(AnnotatedString(fmt(now)), style = nowStyle), c, -6.dp.toPx())
        centreText(measurer.measure(AnnotatedString("NOW"), style = nowLabel), c, 14.dp.toPx())
    }
}

private fun DrawScope.centreText(layout: androidx.compose.ui.text.TextLayoutResult, c: Offset, dy: Float) {
    drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y + dy - layout.size.height / 2f))
}
