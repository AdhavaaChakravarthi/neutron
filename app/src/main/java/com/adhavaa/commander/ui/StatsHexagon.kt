package com.adhavaa.commander.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adhavaa.commander.model.Hexagon
import com.adhavaa.commander.model.Stat
import com.adhavaa.commander.model.StatsConstants
import com.adhavaa.commander.stats.StatsPicture
import com.adhavaa.commander.stats.deltaText
import com.adhavaa.commander.stats.scoreText
import com.adhavaa.commander.stats.statMeaning
import com.adhavaa.commander.stats.weekAgoComparable
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val RANGE_FMT = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

private fun countedText(s: Stat, h: Hexagon): String {
    val n = h.counted.getValue(s)
    return when {
        s == Stat.RHYTHM && h.scores[s] == null -> "needs ${StatsConstants.MIN_DAYS} nights in a row"
        h.scores[s] == null -> "needs ${StatsConstants.MIN_DAYS} days · $n so far"
        s == Stat.SLEEP || s == Stat.RHYTHM -> "$n/${StatsConstants.WINDOW} nights"
        else -> "$n/${StatsConstants.WINDOW} days"
    }
}

/** The last 21 days as a hexagon, last week's shape as an outline, and a row per corner. */
@Composable
fun HexagonPanel(pic: StatsPicture) {
    var how by rememberSaveable { mutableStateOf(false) }
    val now = pic.now
    HudPanel(
        modifier = Modifier.animateContentSize(),
        title = "Last 21 days",
        subtitle = "Orders followed · ${pic.start.format(RANGE_FMT)} – ${pic.end.format(RANGE_FMT)} · updated daily",
        trailing = { HudChip("Overall ${scoreText(now.overall)}", if (now.overall != null) Tone.GREEN else Tone.MUTED) },
    ) {
        val ghost = weekAgoComparable(now, pic.weekAgo)
        HexagonChart(now, pic.weekAgo.takeIf { ghost }, Modifier.padding(horizontal = 2.dp))
        if (now.scores.values.any { it != null }) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HudChip("■ Last 21 days", Tone.GREEN)
                if (ghost) HudChip("┄ A week ago")
            }
        }
        if (now.scores.values.all { it == null }) {
            Text(
                "Each corner needs ${StatsConstants.MIN_DAYS} days of data, so the shape fills in over your first week.",
                style = HudType.small, color = Hud.muted,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (s in Stat.entries) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.label.uppercase(), style = HudType.label, color = Hud.muted, modifier = Modifier.width(84.dp))
                    Text(scoreText(now.scores[s]), style = HudType.value, color = if (now.scores[s] != null) Hud.ink else Hud.dim, modifier = Modifier.width(34.dp))
                    val delta = deltaText(now.scores[s], pic.weekAgo.scores[s])
                    Text(delta, style = HudType.label, color = if (delta.startsWith("▲")) Hud.green else Hud.muted, modifier = Modifier.width(40.dp))
                    Text(countedText(s, now), style = HudType.label, color = Hud.dim, modifier = Modifier.weight(1f), maxLines = 1)
                }
                if (how) Text(statMeaning(s, pic.needH), style = HudType.small, color = Hud.muted, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        if (how) {
            Text(
                "Covers the 21 days to yesterday and the nights after them, recalculated every day. Every day counts the same, " +
                    "and a missed one drops out after 21 days. Stand-down days don't count against Train. The outline is the same " +
                    "score a week ago. This scores orders followed, not fitness or health.",
                style = HudType.small, color = Hud.muted,
            )
        }
        Text(
            if (how) "HIDE HOW IT'S SCORED" else "HOW IT'S SCORED",
            style = HudType.label,
            color = Hud.green,
            modifier = Modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                .clickable(role = Role.Button) { how = !how }
                .padding(vertical = 6.dp, horizontal = 2.dp),
        )
    }
}

/** Six corners clockwise from the top, rings at 25/50/75/100; a corner with no number sits at the centre. */
@Composable
fun HexagonChart(now: Hexagon, weekAgo: Hexagon?, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val nameStyle = HudType.label.copy(color = Hud.muted)
    val valueStyle = HudType.bigValue.copy(fontSize = 17.sp)
    val description = "Stats hexagon, last 21 days. " +
        Stat.entries.joinToString(". ") { "${it.label} ${scoreText(now.scores[it])}" } + ". Overall ${scoreText(now.overall)}."
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.15f)
            .semantics { contentDescription = description },
    ) {
        val c = center
        val r = min((size.width / 2f - 78.dp.toPx()) / 0.866f, size.height / 2f - 36.dp.toPx())
        fun angle(i: Int): Float = (-90f + 60f * i) * PI.toFloat() / 180f
        fun point(i: Int, f: Float): Offset = Offset(c.x + r * f * cos(angle(i)), c.y + r * f * sin(angle(i)))
        fun shape(values: List<Float>): Path = Path().apply {
            values.forEachIndexed { i, v -> point(i, v).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
            close()
        }
        val n = Stat.entries.size

        for (f in listOf(0.25f, 0.5f, 0.75f, 1f)) {
            drawPath(shape(List(n) { f }), if (f == 1f) Hud.muted.copy(alpha = 0.6f) else Hud.line, style = Stroke(1.dp.toPx()))
        }
        for (i in 0 until n) drawLine(Hud.grid, c, point(i, 1f), 1.dp.toPx())

        fun fractions(h: Hexagon): List<Float> = Stat.entries.map { ((h.scores[it] ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) }

        if (weekAgo != null && weekAgo.scores.values.any { it != null }) {
            drawPath(
                shape(fractions(weekAgo)), Hud.muted,
                style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
            )
        }
        if (now.scores.values.any { it != null }) {
            val p = shape(fractions(now))
            drawPath(p, Hud.green.copy(alpha = 0.26f))
            drawPath(p, Hud.green, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
            fractions(now).forEachIndexed { i, v -> if (now.scores[Stat.entries[i]] != null) drawCircle(Hud.green, 3.5.dp.toPx(), point(i, v)) }
        }

        // Corner labels: name over value, outside each vertex.
        Stat.entries.forEachIndexed { i, s ->
            val name = measurer.measure(AnnotatedString(s.label.uppercase()), style = nameStyle)
            val value = measurer.measure(
                AnnotatedString(scoreText(now.scores[s])),
                style = valueStyle.copy(color = if (now.scores[s] != null) Hud.ink else Hud.dim),
            )
            val w = maxOf(name.size.width, value.size.width).toFloat()
            val h = (name.size.height + value.size.height).toFloat()
            val v = point(i, 1f)
            val gap = 10.dp.toPx()
            val cx = cos(angle(i))
            val topLeft = when {
                cx > 0.1f -> Offset(v.x + gap, v.y - h / 2f)
                cx < -0.1f -> Offset(v.x - gap - w, v.y - h / 2f)
                sin(angle(i)) < 0f -> Offset(v.x - w / 2f, v.y - gap / 2f - h)
                else -> Offset(v.x - w / 2f, v.y + gap / 2f)
            }
            val alignRight = cx < -0.1f
            fun x(layoutW: Int): Float = when {
                alignRight -> topLeft.x + w - layoutW
                cx > 0.1f -> topLeft.x
                else -> topLeft.x + (w - layoutW) / 2f
            }
            drawText(name, topLeft = Offset(x(name.size.width), topLeft.y))
            drawText(value, topLeft = Offset(x(value.size.width), topLeft.y + name.size.height))
        }
    }
}
