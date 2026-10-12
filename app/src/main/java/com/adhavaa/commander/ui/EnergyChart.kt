package com.adhavaa.commander.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
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

/** Predicted alertness from waking to lights out, drawn as instrument telemetry. */
@Composable
fun EnergyChart(orders: TodayOrders, now: LocalDateTime, fmt: (LocalDateTime) -> String, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val labelStyle = HudType.label.copy(color = Hud.muted)
    val warnStyle = HudType.label.copy(color = Hud.amber)
    val description = daySummary(orders, fmt)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(200.dp)
            .semantics { contentDescription = description },
    ) {
        val start = orders.wake
        val end = orders.lightsOut
        val totalMin = Duration.between(start, end).toMinutes().toFloat().coerceAtLeast(1f)
        val w = size.width
        val sample = measurer.measure(AnnotatedString(fmt(start.withHour(12).withMinute(0))), style = labelStyle)
        val labelBand = sample.size.height + 8.dp.toPx()
        val plotH = size.height - labelBand
        val pxPerHour = w / (totalMin / 60f)
        val stepH = listOf(3, 4, 6, 8, 12).firstOrNull { it * pxPerHour >= sample.size.width + 12.dp.toPx() } ?: 12

        fun x(t: LocalDateTime): Float = (Duration.between(start, t).toMinutes() / totalMin).coerceIn(0f, 1f) * w
        fun y(a: Double): Float = plotH - ((a.toFloat() - Y_MIN) / (Y_MAX - Y_MIN)).coerceIn(0f, 1f) * plotH

        // Grid.
        for (level in listOf(6.0, 8.0, 10.0, 12.0, 14.0)) {
            drawLine(Hud.grid, Offset(0f, y(level)), Offset(w, y(level)), 1.dp.toPx())
        }

        // Zones: groggy hatched, sharpest green, wind-down amber.
        val gx0 = x(orders.wake); val gx1 = x(orders.groggyUntil)
        clipRect(gx0, 0f, gx1, plotH) {
            val step = 6.dp.toPx()
            var k = -plotH
            while (k < gx1 - gx0) {
                drawLine(Hud.dim.copy(alpha = 0.6f), Offset(gx0 + k, plotH), Offset(gx0 + k + plotH, 0f), 1.dp.toPx())
                k += step
            }
        }
        drawRect(Hud.green.copy(alpha = 0.09f), Offset(x(orders.peakStart), 0f), Size(x(orders.peakEnd) - x(orders.peakStart), plotH))
        drawLine(Hud.green.copy(alpha = 0.6f), Offset(x(orders.peakStart), 0f), Offset(x(orders.peakEnd), 0f), 2.dp.toPx())
        drawRect(Hud.amber.copy(alpha = 0.10f), Offset(x(orders.windDown), 0f), Size(x(orders.lightsOut) - x(orders.windDown), plotH))

        // Sleepiness threshold.
        drawLine(
            Hud.amber.copy(alpha = 0.7f), Offset(0f, y(SLEEPY)), Offset(w, y(SLEEPY)), 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        )
        val sleepyLabel = measurer.measure(AnnotatedString("SLEEPY 7"), style = warnStyle)
        drawText(sleepyLabel, topLeft = Offset(w - sleepyLabel.size.width - 2.dp.toPx(), y(SLEEPY) + 3.dp.toPx()))

        // Curve: filled area, glow, line.
        val line = Path()
        orders.curve.forEachIndexed { i, p ->
            if (i == 0) line.moveTo(x(p.at), y(p.alertness)) else line.lineTo(x(p.at), y(p.alertness))
        }
        val area = Path().apply {
            addPath(line)
            lineTo(x(orders.curve.last().at), plotH)
            lineTo(x(orders.curve.first().at), plotH)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(Hud.green.copy(alpha = 0.22f), Hud.green.copy(alpha = 0f)), 0f, plotH))
        drawPath(line, Hud.green.copy(alpha = 0.18f), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(line, Hud.green, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Now.
        if (!now.isBefore(start) && !now.isAfter(end)) {
            val nx = x(now)
            drawLine(Hud.ink.copy(alpha = 0.8f), Offset(nx, 0f), Offset(nx, plotH), 1.dp.toPx())
            orders.alertnessAt(now)?.let { a ->
                drawCircle(Hud.bg, 6.dp.toPx(), Offset(nx, y(a)))
                drawCircle(Hud.ink, 4.dp.toPx(), Offset(nx, y(a)))
            }
        }

        // Axis.
        drawLine(Hud.line, Offset(0f, plotH), Offset(w, plotH), 1.dp.toPx())
        var tick = start.truncatedTo(ChronoUnit.HOURS).plusHours(1)
        while (!tick.isAfter(end)) {
            if (tick.hour % stepH == 0) {
                val tx = x(tick)
                drawLine(Hud.dim, Offset(tx, plotH), Offset(tx, plotH + 3.dp.toPx()), 1.dp.toPx())
                val layout = measurer.measure(AnnotatedString(fmt(tick)), style = labelStyle)
                val lx = (tx - layout.size.width / 2f).coerceIn(0f, (w - layout.size.width).coerceAtLeast(0f))
                drawText(layout, topLeft = Offset(lx, plotH + 5.dp.toPx()))
            }
            tick = tick.plusHours(1)
        }
    }
}
