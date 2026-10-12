package com.adhavaa.commander.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.adhavaa.commander.model.TodayOrders
import com.adhavaa.commander.orders.daySummary
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

private const val Y_MIN = 4f
private const val Y_MAX = 14f
private const val SLEEPY = 7.0

/** Today's predicted alertness from waking to lights out, with the order zones shaded. */
@Composable
fun EnergyChart(orders: TodayOrders, now: LocalDateTime, fmt: (LocalDateTime) -> String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val groggyColor = cs.outline.copy(alpha = 0.22f)
    val peakColor = cs.secondary.copy(alpha = 0.22f)
    val windColor = cs.tertiary.copy(alpha = 0.22f)
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = cs.onSurfaceVariant)
    val description = daySummary(orders, fmt)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .semantics { contentDescription = description },
        ) {
            val start = orders.wake
            val end = orders.lightsOut
            val totalMin = Duration.between(start, end).toMinutes().toFloat().coerceAtLeast(1f)
            val w = size.width
            // Size the label band and the tick spacing from a real label, so large text never overlaps.
            val sample = measurer.measure(AnnotatedString(fmt(start.withHour(12).withMinute(0))), style = labelStyle)
            val labelBand = sample.size.height + 8.dp.toPx()
            val plotH = size.height - labelBand
            val pxPerHour = w / (totalMin / 60f)
            val stepH = listOf(3, 4, 6, 8, 12).firstOrNull { it * pxPerHour >= sample.size.width + 12.dp.toPx() } ?: 12

            fun x(t: LocalDateTime): Float =
                (Duration.between(start, t).toMinutes() / totalMin).coerceIn(0f, 1f) * w

            fun y(a: Double): Float = plotH - ((a.toFloat() - Y_MIN) / (Y_MAX - Y_MIN)).coerceIn(0f, 1f) * plotH

            fun zone(a: LocalDateTime, b: LocalDateTime, color: Color) {
                val left = x(a)
                drawRect(color, topLeft = Offset(left, 0f), size = Size((x(b) - left).coerceAtLeast(0f), plotH))
            }

            zone(orders.wake, orders.groggyUntil, groggyColor)
            zone(orders.peakStart, orders.peakEnd, peakColor)
            zone(orders.windDown, orders.lightsOut, windColor)

            drawLine(
                color = cs.outline,
                start = Offset(0f, y(SLEEPY)),
                end = Offset(w, y(SLEEPY)),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
            drawLine(cs.outline, Offset(0f, plotH), Offset(w, plotH), strokeWidth = 1.dp.toPx())

            val path = Path()
            orders.curve.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(x(p.at), y(p.alertness)) else path.lineTo(x(p.at), y(p.alertness))
            }
            drawPath(
                path,
                color = cs.primary,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            if (!now.isBefore(start) && !now.isAfter(end)) {
                val nx = x(now)
                drawLine(cs.onSurface, Offset(nx, 0f), Offset(nx, plotH), strokeWidth = 1.5.dp.toPx())
                orders.alertnessAt(now)?.let { a ->
                    drawCircle(cs.background, radius = 6.dp.toPx(), center = Offset(nx, y(a)))
                    drawCircle(cs.onSurface, radius = 4.dp.toPx(), center = Offset(nx, y(a)))
                }
            }

            var tick = start.truncatedTo(ChronoUnit.HOURS).plusHours(1)
            while (!tick.isAfter(end)) {
                if (tick.hour % stepH == 0) {
                    val layout = measurer.measure(AnnotatedString(fmt(tick)), style = labelStyle)
                    val lx = (x(tick) - layout.size.width / 2f).coerceIn(0f, (w - layout.size.width).coerceAtLeast(0f))
                    drawLine(cs.outline, Offset(x(tick), plotH), Offset(x(tick), plotH + 3.dp.toPx()), 1.dp.toPx())
                    drawText(layout, topLeft = Offset(lx, plotH + 5.dp.toPx()))
                }
                tick = tick.plusHours(1)
            }
        }
        // Two rows so the legend fits a narrow phone at large font sizes.
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(groggyColor, "Groggy")
            Legend(peakColor, "Sharpest")
            Legend(windColor, "Wind down")
        }
        Legend(cs.outline, "Below the dashed line: sleepy", dashed = true)
    }
}

@Composable
private fun Legend(color: Color, label: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (dashed) {
            Box(Modifier.size(width = 12.dp, height = 2.dp).background(color))
        } else {
            Box(Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
