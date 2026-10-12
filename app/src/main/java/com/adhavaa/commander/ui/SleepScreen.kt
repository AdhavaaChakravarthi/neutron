package com.adhavaa.commander.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.adhavaa.commander.MorningReport
import com.adhavaa.commander.UiState
import com.adhavaa.commander.data.StoredSleep
import com.adhavaa.commander.model.SleepKind
import com.adhavaa.commander.model.SleepRecord
import com.adhavaa.commander.orders.debtBand
import com.adhavaa.commander.orders.formatHours
import com.adhavaa.commander.orders.hoursBetween
import com.adhavaa.commander.orders.nightFrom
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Immutable
data class SleepActions(
    val onOpenSettings: () -> Unit = {},
    val onSave: suspend (SleepRecord, Long?) -> Boolean = { _, _ -> true },
    val onDelete: (Long) -> Unit = {},
    val onSleepless: (LocalDate) -> Unit = {},
    val onClearSleepless: (LocalDate) -> Unit = {},
    val onEnableDetection: () -> Unit = {},
)

private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

@Composable
fun SleepScreen(state: UiState.Ready, actions: SleepActions, detectionOn: Boolean) {
    val fmt = rememberTimeFormatter()
    val stats = state.stats
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    var editing by remember { mutableStateOf<EditTarget?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Hud.bg),
        contentPadding = PaddingValues(
            start = 14.dp, end = 14.dp,
            top = insets.calculateTopPadding() + 8.dp,
            bottom = bottomPadding(insets),
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Mark()
                Column(Modifier.weight(1f)) {
                    Text("SLEEP", style = HudType.title, color = Hud.ink)
                    Text("${stats.recordedNights} nights recorded · last 14 days".uppercase(), style = HudType.label, color = Hud.green)
                }
                SquareButton("Open settings", actions.onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = null, tint = Hud.muted, modifier = Modifier.size(18.dp))
                }
            }
        }
        item {
            HudPanel(
                title = "Sleep debt",
                subtitle = "Weighted over your last 14 recorded nights",
                trailing = { stats.debtH?.let { HudChip(debtBand(it), if (it > 5.0) Tone.AMBER else if (it >= 2.0) Tone.AMBER else Tone.GREEN) } },
            ) {
                Text(stats.debtH?.let { formatHours(it) } ?: "—", style = HudType.headline, color = if ((stats.debtH ?: 0.0) >= 2.0) Hud.amber else Hud.green)
                if (stats.daily.isEmpty()) {
                    Hint("No nights recorded yet. Confirm tomorrow's morning report, or log a night below.")
                } else {
                    DebtBars(stats.daily, stats.needH, Modifier.fillMaxWidth().height(140.dp))
                }
                KeyValue("Sleep need", formatHours(stats.needH), Hud.green)
                Hint(
                    if (stats.needLearned) {
                        "Learned from ${stats.qualifyingNights} night(s) you woke without an alarm while lightly in debt, starting from 8 h."
                    } else {
                        "Set by you in Settings."
                    },
                )
            }
        }
        item {
            HudPanel(title = "Rhythm", subtitle = "How steady your sleep timing is") {
                KeyValue("Usual sleep", "${fmtT(fmt, stats.habitualBed)} → ${fmtT(fmt, stats.habitualWake)}")
                KeyValue("Regularity", stats.sri?.let { "${it.toInt()} / 100" } ?: "Needs 4 nights in a row", if (stats.sri != null) Hud.green else Hud.dim)
                KeyValue("Social jetlag", stats.socialJetlagH?.let { formatHours(it) } ?: "Needs 2 free + 3 work nights", if (stats.socialJetlagH != null) Hud.ink else Hud.dim)
                KeyValue("Chronotype", stats.chronotype?.let { "Mid-sleep ${fmtT(fmt, it)}" } ?: "Needs alarm-free free days", if (stats.chronotype != null) Hud.ink else Hud.dim)
                Hint(
                    "Regularity: chance you're in the same state (asleep or awake) 24 h apart; 100 = same times every day " +
                        "(Phillips et al. 2017). Social jetlag: how far your free-day sleep shifts from workdays " +
                        "(Wittmann et al. 2006). Chronotype: mid-sleep on free days, corrected for catch-up sleep (MCTQ).",
                )
            }
        }
        item {
            HudPanel(title = "Automatic detection", border = if (detectionOn) Hud.line else Hud.amber.copy(alpha = 0.6f)) {
                if (detectionOn) {
                    Text("On. Commander reads only when your screen turned on, never which apps you used.", style = HudType.body, color = Hud.ink)
                } else {
                    Text("Off. Allow Usage access and Commander finds your night from when the phone went down and came back up.", style = HudType.body, color = Hud.amber)
                    HudButton("Allow usage access", actions.onEnableDetection)
                }
            }
        }
        item {
            HudPanel(title = "Log") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editing = EditTarget(SleepKind.NIGHT, null, state.now.toLocalDate()) }, modifier = Modifier.weight(1f)) { Text("LOG NIGHT") }
                    OutlinedButton(onClick = { editing = EditTarget(SleepKind.NAP, null, state.now.toLocalDate()) }, modifier = Modifier.weight(1f)) { Text("LOG NAP") }
                }
            }
        }
        item {
            HudPanel(title = "History", subtitle = "Tap an entry to fix it") {
                val rows = historyRows(state.sleep, state.skipped, state.now.toLocalDate())
                if (rows.isEmpty()) Hint("Nothing yet.")
                for (row in rows) HistoryRow(row, fmt, onEdit = { s -> editing = EditTarget(s.record.kind, s, s.record.end.toLocalDate()) }, onClearSleepless = actions.onClearSleepless)
            }
        }
    }

    editing?.let { target ->
        SleepDialog(
            target = target,
            onSave = actions.onSave,
            onDelete = target.existing?.let { s -> { actions.onDelete(s.id) } },
            onDismiss = { editing = null },
        )
    }
}

private fun fmtT(fmt: (LocalDateTime) -> String, t: LocalTime) = fmt(LocalDate.of(2026, 1, 1).atTime(t))

data class EditTarget(val kind: SleepKind, val existing: StoredSleep?, val date: LocalDate)

private sealed interface HistoryItem {
    val date: LocalDate
}
private data class SleepItem(val s: StoredSleep) : HistoryItem {
    override val date: LocalDate get() = s.record.end.toLocalDate()
}
private data class SleeplessItem(override val date: LocalDate) : HistoryItem

private fun historyRows(sleep: List<StoredSleep>, skipped: Set<LocalDate>, today: LocalDate): List<HistoryItem> {
    val from = today.minusDays(14)
    val items = sleep.filter { !it.record.end.toLocalDate().isBefore(from) }.map { SleepItem(it) } +
        skipped.filter { !it.isBefore(from) }.map { SleeplessItem(it) }
    return items.sortedWith(compareByDescending<HistoryItem> { it.date }.thenByDescending { (it as? SleepItem)?.s?.record?.end })
}

@Composable
private fun HistoryRow(item: HistoryItem, fmt: (LocalDateTime) -> String, onEdit: (StoredSleep) -> Unit, onClearSleepless: (LocalDate) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Hud.panelHi)
            .clickable(role = Role.Button) {
                when (item) {
                    is SleepItem -> onEdit(item.s)
                    is SleeplessItem -> onClearSleepless(item.date)
                }
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.date.format(dayFmt).uppercase(), style = HudType.label, color = Hud.muted)
            when (item) {
                is SleepItem -> {
                    val r = item.s.record
                    Text("${fmt(r.start)} → ${fmt(r.end)} · ${formatHours(hoursBetween(r.start, r.end))}", style = HudType.value, color = Hud.ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HudChip(if (r.kind == SleepKind.NIGHT) "Night" else "Nap", if (r.kind == SleepKind.NIGHT) Tone.GREEN else Tone.MUTED)
                        HudChip(if (item.s.source == "PHONE") "Phone" else "Manual")
                        if (!item.s.confirmed) HudChip("Unconfirmed", Tone.AMBER)
                        if (r.wokeNaturally == true) HudChip("No alarm")
                    }
                }
                is SleeplessItem -> {
                    Text("Didn't sleep", style = HudType.value, color = Hud.amber)
                    Text("Tap to undo", style = HudType.small, color = Hud.muted)
                }
            }
        }
    }
}

/** Last 14 recorded days, oldest left: total sleep against need (dashed). */
@Composable
fun DebtBars(daily: List<Pair<LocalDate, Double>>, needH: Double, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val labelStyle = HudType.label.copy(color = Hud.muted)
    val needStyle = HudType.label.copy(color = Hud.green)
    val days = daily.reversed()
    val description = "Sleep per day: " + days.joinToString { "${it.first.dayOfMonth}: ${formatHours(it.second)}" } + ". Need ${formatHours(needH)}."
    Canvas(modifier.semantics { contentDescription = description }) {
        val label = measurer.measure(AnnotatedString("30"), style = labelStyle)
        val plotH = size.height - label.size.height - 6.dp.toPx()
        val maxH = maxOf(10.0, needH + 2.0, days.maxOf { it.second } + 0.5)
        fun y(h: Double) = plotH - (h / maxH).toFloat() * plotH
        val n = 14
        val slot = size.width / n
        val barW = slot * 0.62f
        days.forEachIndexed { i, (date, h) ->
            val x = (n - days.size + i) * slot + (slot - barW) / 2f
            val short = h < needH - 0.25
            drawRect(
                if (short) Hud.amber.copy(alpha = 0.85f) else Hud.green.copy(alpha = 0.85f),
                Offset(x, y(h)), Size(barW, (plotH - y(h)).coerceAtLeast(1.dp.toPx())),
            )
            val l = measurer.measure(AnnotatedString(date.dayOfMonth.toString()), style = labelStyle)
            drawText(l, topLeft = Offset(x + barW / 2f - l.size.width / 2f, plotH + 4.dp.toPx()))
        }
        drawLine(
            Hud.green, Offset(0f, y(needH)), Offset(size.width, y(needH)), 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        )
        val nl = measurer.measure(AnnotatedString("NEED"), style = needStyle)
        drawText(nl, topLeft = Offset(2.dp.toPx(), y(needH) - nl.size.height - 2.dp.toPx()))
        drawLine(Hud.line, Offset(0f, plotH), Offset(size.width, plotH), 1.dp.toPx())
    }
}

/** Add, fix or delete a night or nap. Nights are entered by wake date; a bedtime from noon on is the evening before. */
@Composable
fun SleepDialog(
    target: EditTarget,
    onSave: suspend (SleepRecord, Long?) -> Boolean,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing = target.existing?.record
    val night = target.kind == SleepKind.NIGHT
    var date by remember { mutableStateOf(target.date) }
    var start by remember { mutableStateOf(existing?.start?.toLocalTime() ?: if (night) LocalTime.of(23, 30) else LocalTime.of(14, 0)) }
    var end by remember { mutableStateOf(existing?.end?.toLocalTime() ?: if (night) LocalTime.of(7, 30) else LocalTime.of(14, 20)) }
    var natural by remember { mutableStateOf(existing?.wokeNaturally == true) }
    var error by remember { mutableStateOf<String?>(null) }
    val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Hud.panel,
        title = { Text((if (existing == null) "LOG " else "FIX ") + if (night) "NIGHT" else "NAP", style = HudType.title, color = Hud.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Hud.panelHi)
                        .clickable(role = Role.Button) { showDatePicker(context, date) { date = it } }
                        .padding(12.dp),
                ) {
                    Text(if (night) "Woke up on" else "Date", style = HudType.body, color = Hud.ink, modifier = Modifier.weight(1f))
                    Text(date.format(dateFmt), style = HudType.value, color = Hud.green)
                }
                TimeRow(if (night) "Fell asleep" else "Nap started", start) { start = it }
                TimeRow(if (night) "Woke up" else "Nap ended", end) { end = it }
                if (night) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Woke without an alarm", style = HudType.body, color = Hud.ink, modifier = Modifier.weight(1f))
                        Switch(
                            checked = natural, onCheckedChange = { natural = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = Hud.green, checkedThumbColor = Hud.onMint),
                        )
                    }
                }
                error?.let { Text(it, style = HudType.small, color = Hud.amber) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val (s, e) = if (night) nightFrom(date, start, end) else date.atTime(start) to date.atTime(end)
                when {
                    !e.isAfter(s) -> error = "The end has to be after the start."
                    hoursBetween(s, e) > 16.0 -> error = "That's longer than 16 hours. Check the times."
                    else -> scope.launch {
                        val ok = onSave(SleepRecord(s, e, target.kind, if (night) natural else null), target.existing?.id)
                        if (ok) onDismiss() else error = "That overlaps another entry."
                    }
                }
            }) { Text("SAVE") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = { onDelete(); onDismiss() }) { Text("DELETE", color = Hud.amber) }
                }
                TextButton(onClick = onDismiss) { Text("CANCEL") }
            }
        },
    )
}

/** Shown on Today until last night is confirmed. Ten seconds: check the times, say whether an alarm woke you. */
@Composable
fun MorningReportCard(
    report: MorningReport,
    habitualBed: LocalTime,
    habitualWake: LocalTime,
    onDetect: (LocalDate) -> Unit,
    onConfirm: (LocalDateTime, LocalDateTime, Boolean) -> Unit,
    onSleepless: () -> Unit,
    onEnableDetection: () -> Unit,
) {
    val fmt = rememberTimeFormatter()
    LaunchedEffect(report.date, report.usageAccess) {
        if (report.pending == null && report.detected == null && report.usageAccess) onDetect(report.date)
    }
    val seen = report.pending?.record?.let { it.start to it.end } ?: report.detected?.let { it.start to it.end }
    val fallback = nightFrom(report.date, habitualBed, habitualWake)
    var times by remember(seen) { mutableStateOf(seen ?: fallback) }
    var natural by remember { mutableStateOf(false) }
    val context = LocalContext.current

    HudPanel(
        title = "Morning report",
        subtitle = when {
            seen != null -> "From your phone · confirm or fix"
            report.usageAccess -> "No clear night found · enter it"
            else -> "Enter last night"
        },
        trailing = { HudChip(report.date.format(dayFmt), Tone.GREEN) },
        border = Hud.green.copy(alpha = 0.6f),
    ) {
        Text(
            "${fmt(times.first)} → ${fmt(times.second)} · ${formatHours(hoursBetween(times.first, times.second))}",
            style = HudType.headline,
            color = Hud.ink,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                showTimePicker(context, times.first.toLocalTime()) { t -> times = nightFrom(report.date, t, times.second.toLocalTime()) }
            }, modifier = Modifier.weight(1f)) { Text("ASLEEP") }
            OutlinedButton(onClick = {
                showTimePicker(context, times.second.toLocalTime()) { t -> times = nightFrom(report.date, times.first.toLocalTime(), t) }
            }, modifier = Modifier.weight(1f)) { Text("UP") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Woke without an alarm", style = HudType.body, color = Hud.ink, modifier = Modifier.weight(1f))
            Switch(
                checked = natural, onCheckedChange = { natural = it },
                colors = SwitchDefaults.colors(checkedTrackColor = Hud.green, checkedThumbColor = Hud.onMint),
            )
        }
        HudButton("Confirm", { onConfirm(times.first, times.second, natural) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSleepless) { Text("I DIDN'T SLEEP") }
            if (!report.usageAccess) TextButton(onClick = onEnableDetection) { Text("DETECT AUTOMATICALLY") }
        }
        Hint("Phone down to phone up is close to, but not the same as, sleep. Adjust if you lay awake.")
    }
}
