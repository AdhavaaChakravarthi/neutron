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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adhavaa.commander.TrainUi
import com.adhavaa.commander.model.Intensity
import com.adhavaa.commander.model.PlannedSession
import com.adhavaa.commander.model.Sport
import com.adhavaa.commander.train.DayPlan
import com.adhavaa.commander.train.swimLine
import com.adhavaa.commander.train.LiftTarget
import com.adhavaa.commander.train.RECOVERY_STRETCH
import com.adhavaa.commander.train.Routine
import com.adhavaa.commander.train.WARM_UP
import com.adhavaa.commander.train.Workout
import com.adhavaa.commander.train.WorkoutKind
import com.adhavaa.commander.train.dayLine
import com.adhavaa.commander.train.phaseLabel
import com.adhavaa.commander.train.setsText
import com.adhavaa.commander.train.targetText
import com.adhavaa.commander.model.Equipment
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Immutable
data class TrainActions(
    val onOpenSettings: () -> Unit = {},
    val onSetup: () -> Unit = {},
    val onStartLift: () -> Unit = {},
    val onLogSession: (PlannedSession?) -> Unit = {},
    val onEditWorkout: (Workout) -> Unit = {},
    val onStartRoutine: (Routine) -> Unit = {},
    val onStandDown: () -> Unit = {},
    val onUndoStandDown: () -> Unit = {},
    val onExercise: (LiftTarget) -> Unit = {},
)

private val DAY_FMT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

@Composable
fun TrainScreen(ui: TrainUi, actions: TrainActions) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val p = ui.picture
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Hud.bg),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = insets.calculateTopPadding() + 8.dp, bottom = bottomPadding(insets)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TrainTopBar(ui, actions.onOpenSettings) }
        if (!p.setUp) item { TrainSetupPrompt(actions.onSetup) }
        item { TodayPanel(ui, actions) }
        if (p.setUp) item { WeekPanel(ui) }
        item { LoadPanel(ui) }
        if (p.setUp) item { PlanPanel(ui, actions.onSetup) }
        item { LiftsPanel(ui, actions.onExercise) }
        item { RecentPanel(ui, actions.onEditWorkout) }
        item {
            Text(
                "Program and plan follow ACSM 2009 and 2026, Foster 2001, Seiler 2010 and Bosquet 2007 (sources in Settings). " +
                    "Exercises from free-exercise-db. Wellness tool, not coaching or medical advice.",
                style = HudType.small,
                color = Hud.dim,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}

@Composable
private fun TrainTopBar(ui: TrainUi, onOpenSettings: () -> Unit) {
    val p = ui.picture
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Mark()
        Column(Modifier.weight(1f)) {
            Text("TRAIN", style = HudType.title.copy(fontSize = 14.sp), color = Hud.ink)
            Text(
                (phaseLabel(p.week) + (p.week?.let { " · week ${it.index + 1}" } ?: "")).uppercase(),
                style = HudType.label,
                color = Hud.green,
            )
        }
        val w = p.weeksToRace
        HudChip(
            when {
                !p.setUp -> "Not set up"
                w == null -> "No race set"
                w < 0 -> "Race done"
                w == 0 -> "Race week"
                else -> "70.3 in $w wk"
            },
            if (w != null && w in 0..2) Tone.AMBER else Tone.MUTED,
        )
        SquareButton("Open settings", onOpenSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = null, tint = Hud.muted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TrainSetupPrompt(onSetup: () -> Unit) {
    HudPanel(
        title = "Set up Train",
        subtitle = "Lifting 4 days, swim/bike/run plan, mobility",
        decoration = Modifier.contours(seed = 4f),
        background = Brush.verticalGradient(listOf(Hud.panel, Hud.bg)),
    ) {
        Text(
            "Tell Commander how much you swim, bike and run now, how much time you have, and your 70.3 date if you " +
                "have one. It plans each week, builds gradually, and tapers before the race.",
            style = HudType.body,
            color = Hud.ink,
        )
        HudButton("Set up Train", onSetup)
    }
}

@Composable
private fun TodayPanel(ui: TrainUi, actions: TrainActions) {
    val p = ui.picture
    val d = p.day
    val done = p.done(d.date)
    HudPanel(background = Brush.verticalGradient(listOf(Hud.mint, Hud.mintDeep)), border = Hud.mint) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("◆ TODAY · ${d.date.format(DAY_FMT).uppercase()}", style = HudType.title, color = Hud.onMint, modifier = Modifier.weight(1f))
            if (d.eased) Text("EASED: SLEEP DEBT", style = HudType.label, color = Hud.onMint.copy(alpha = 0.75f))
        }
        Text(dayLine(d), style = HudType.headline.copy(fontSize = 18.sp, lineHeight = 24.sp), color = Hud.onMint)
        Column(
            Modifier.clip(RoundedCornerShape(6.dp)).background(Hud.panel).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                d.standDown != null -> {
                    Text("Standing down: ${d.standDown.label.lowercase()}. Rest is the order.", style = HudType.body, color = Hud.ink)
                    TextButton(onClick = actions.onUndoStandDown) { Text("I'M OK TO TRAIN") }
                }
                !p.setUp && d.lift == null -> Text("Set up Train to get today's plan.", style = HudType.body, color = Hud.muted)
                else -> {
                    d.lift?.let { lift ->
                        val liftDone = done.any { it.kind == WorkoutKind.LIFT && it.ended }
                        TodayRow(
                            lift.label.uppercase(),
                            "${lift.items.size} exercises" + if (d.eased) " · one set fewer each" else "",
                            when {
                                liftDone -> "✓ DONE"
                                p.inProgress != null -> "CONTINUE"
                                else -> "START"
                            },
                            liftDone,
                        ) { if (!liftDone) actions.onStartLift() }
                    }
                    for (s in d.sessions) {
                        val logged = done.firstOrNull { it.kind.sport == s.sport && it.ended }
                        TodayRow(
                            (if (s.brick) "BRICK RUN" else s.sport.label.uppercase()),
                            "${s.minutes.roundToInt()} min · ${s.intensity.name.lowercase()}" +
                                if (s.intensity == Intensity.QUALITY) " (≤ a fifth hard)" else " (can talk in sentences)",
                            if (logged != null) "✓ ${logged.minutes.roundToInt()} MIN" else "LOG",
                            logged != null,
                        ) { if (logged != null) actions.onEditWorkout(logged) else actions.onLogSession(s) }
                    }
                    if (d.rest) Text("Rest day. Recovery is part of the plan.", style = HudType.body, color = Hud.ink)
                }
            }
            val mob = done.firstOrNull { it.kind == WorkoutKind.MOBILITY }
            TodayRow(
                "MOBILITY",
                "${RECOVERY_STRETCH.label} · ${RECOVERY_STRETCH.totalSeconds / 60} min",
                if (mob != null) "✓ DONE" else "START",
                mob != null,
            ) { actions.onStartRoutine(RECOVERY_STRETCH) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { actions.onStartRoutine(WARM_UP) }) { Text("WARM-UP 5 MIN", color = Hud.onMint) }
            OutlinedButton(onClick = { actions.onLogSession(null) }) { Text("LOG OTHER", color = Hud.onMint) }
        }
        if (d.standDown == null && p.setUp) {
            Text(
                "Pain, illness or exhaustion? Stand down",
                style = HudType.small,
                color = Hud.onMint,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClickLabel = "Stand down today", role = Role.Button, onClick = actions.onStandDown)
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun TodayRow(title: String, detail: String, action: String, done: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = HudType.value, color = if (done) Hud.green else Hud.ink)
            Text(detail, style = HudType.small, color = Hud.muted)
        }
        Text(
            action,
            style = HudType.label,
            color = if (done) Hud.green else Hud.onMint,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (done) Hud.greenDeep else Hud.green)
                .clickable(onClickLabel = action.lowercase(), role = Role.Button, onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun WeekPanel(ui: TrainUi) {
    val p = ui.picture
    HudPanel(title = "This week", subtitle = p.week?.let { "${it.minutes.roundToInt()} min of swim, bike and run planned" }) {
        for (d in p.days) {
            val today = d.date == p.today
            val complete = p.dayComplete(d)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (today) Hud.panelHi else Hud.panel)
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
                    style = HudType.label,
                    color = if (today) Hud.green else Hud.muted,
                    modifier = Modifier.width(34.dp),
                )
                Text(dayLine(d), style = HudType.small, color = Hud.ink, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (complete) Text("✓", style = HudType.value, color = Hud.green)
            }
        }
    }
}

@Composable
private fun LoadPanel(ui: TrainUi) {
    val p = ui.picture
    HudPanel(title = "Load", subtitle = "Effort × minutes (Foster) · easy = rated 4 or less") {
        for (s in Sport.entries) {
            TargetBar(s.label, p.weekDone[s] ?: 0.0, p.weekPlanned[s]?.takeIf { it > 0 }, "min")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell("Week load", p.weekLoad.roundToInt().toString())
            StatCell("Easy, 4 wk", p.easyShare4w?.let { "${(it * 100).roundToInt()}% / 80%" } ?: "—", accent = if ((p.easyShare4w ?: 1.0) < 0.7) Hud.amber else Hud.ink)
        }
        val muscles = p.setsPerMuscle.entries.sortedByDescending { it.value }.take(10)
        if (muscles.isNotEmpty()) {
            Text("SETS PER MUSCLE THIS WEEK · ACSM ~10 FOR GROWTH", style = HudType.label, color = Hud.muted)
            for ((m, v) in muscles) TargetBar(m, v, 10.0, "sets")
        }
    }
}

@Composable
private fun PlanPanel(ui: TrainUi, onEdit: () -> Unit) {
    val p = ui.picture
    val s = ui.settings
    HudPanel(title = "Plan", subtitle = "Weekly swim + bike + run minutes · bars planned, dots done") {
        KeyValue("Race", s.raceDate?.format(DAY_FMT) ?: "Not set", if (s.raceDate != null) Hud.green else Hud.muted)
        KeyValue("Now / most per week", "${(s.currentMinutes / 60).fmt1()} h / ${(s.peakMinutes / 60).fmt1()} h")
        KeyValue("This week", p.week?.let { "${it.minutes.roundToInt()} min" + (if (it.capped) " (held back after a light week)" else "") } ?: "—")
        KeyValue("Swimming", swimLine(p.swim, s.swimPauseKg, p.swimThisWeek), if (p.swimThisWeek) Hud.green else Hud.amber)
        PlanChart(ui, Modifier.fillMaxWidth().height(110.dp))
        Hint("Builds 8% a week, every fourth week lighter, a two-week taper before the race, and never more than 10% above your biggest recent week.")
        OutlinedButton(onClick = onEdit) { Text("EDIT PLAN") }
    }
}

private fun Double.fmt1(): String = String.format(Locale.US, "%.1f", this).removeSuffix(".0")

@Composable
private fun PlanChart(ui: TrainUi, modifier: Modifier) {
    val p = ui.picture
    val weeks = p.plan
    if (weeks.isEmpty()) return
    val hi = (weeks.maxOf { it.minutes }.coerceAtLeast(1.0) * 1.1).toFloat()
    val current = p.week?.index ?: 0
    Canvas(modifier.semantics { contentDescription = "Plan: ${weeks.size} weeks, up to ${weeks.maxOf { it.minutes }.roundToInt()} minutes a week" }) {
        val bw = size.width / weeks.size
        weeks.forEachIndexed { i, w ->
            val h = (w.minutes.toFloat() / hi) * size.height
            val color = when {
                w.phase.name == "TAPER" -> Hud.amber
                w.index == current -> Hud.green
                w.recovery -> Hud.dim
                else -> Hud.greenDeep
            }
            drawRect(color, Offset(i * bw + bw * 0.15f, size.height - h), Size(bw * 0.7f, h))
            p.actual[w.index]?.let { a ->
                drawCircle(Hud.ink, 2.5.dp.toPx(), Offset(i * bw + bw / 2, size.height - (a.toFloat() / hi) * size.height))
            }
        }
    }
}

@Composable
private fun LiftsPanel(ui: TrainUi, onExercise: (LiftTarget) -> Unit) {
    HudPanel(title = "Lifts", subtitle = "Last session · best estimated 1RM · next") {
        for (day in com.adhavaa.commander.train.PROGRAM) {
            Text(day.label.uppercase(), style = HudType.label, color = Hud.green)
            for (t in ui.targets[day.key].orEmpty()) {
                val name = ui.exercises[t.exerciseId]?.name ?: t.exerciseId.replace('_', ' ')
                Column(Modifier.fillMaxWidth().clickableRow { onExercise(t) }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row {
                        Text(name, style = HudType.small, color = Hud.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.item.rangeLabel(), style = HudType.label, color = Hud.muted)
                    }
                    Text(
                        "Last: ${t.last?.let { setsText(it, t.item.timed, t.equipment == Equipment.BODY) } ?: "—"}" +
                            (t.bestE1rm?.let { " · e1RM ${it.roundToInt()} kg" } ?: ""),
                        style = HudType.label,
                        color = Hud.muted,
                    )
                    Text(targetText(t), style = HudType.small, color = Hud.green)
                }
            }
        }
    }
}

@Composable
private fun RecentPanel(ui: TrainUi, onEdit: (Workout) -> Unit) {
    val recent = ui.workouts.filter { it.ended }.sortedWith(compareByDescending<Workout> { it.date }.thenByDescending { it.startMs }).take(10)
    HudPanel(title = "Recent", subtitle = if (recent.isEmpty()) "Nothing logged yet" else "Tap a swim, bike or run to fix it") {
        for (w in recent) {
            Row(
                Modifier.fillMaxWidth().clickableRow { if (w.kind != WorkoutKind.LIFT) onEdit(w) },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(w.date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())).uppercase(), style = HudType.label, color = Hud.muted, modifier = Modifier.width(48.dp))
                Text(
                    (if (w.brick) "Brick run" else w.label.ifBlank { w.kind.label }) + w.distanceKm?.let { " · ${it.fmt1()} km" }.orEmpty(),
                    style = HudType.small,
                    color = Hud.ink,
                    modifier = Modifier.weight(1f),
                )
                Text("${w.minutes.roundToInt()} min" + (w.rpe?.let { " · RPE ${it.roundToInt()}" } ?: ""), style = HudType.label, color = Hud.muted)
            }
        }
    }
}
