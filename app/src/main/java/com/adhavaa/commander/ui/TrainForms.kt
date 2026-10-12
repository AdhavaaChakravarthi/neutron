package com.adhavaa.commander.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adhavaa.commander.train.Exercise
import com.adhavaa.commander.train.SetRecord
import com.adhavaa.commander.train.StandDown
import com.adhavaa.commander.train.Workout
import com.adhavaa.commander.train.WorkoutKind

/** A tappable list row on the HUD's raised surface. */
fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    clip(RoundedCornerShape(6.dp)).background(Hud.panelHi).clickable(role = Role.Button, onClick = onClick).padding(10.dp)

/** Borg CR-10 anchors as used for session RPE (Foster et al. 2001; MODEL.md 11.2). */
fun rpeLabel(v: Int): String = when (v) {
    0 -> "Rest"
    1 -> "Very, very easy"
    2 -> "Easy"
    3 -> "Moderate"
    4 -> "Somewhat hard"
    5 -> "Hard"
    7 -> "Very hard"
    9 -> "Nearly maximal"
    10 -> "Maximal"
    else -> ""
}

/** "How was your workout?" 0-10. */
@Composable
fun RpePicker(value: Int?, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("HOW WAS YOUR WORKOUT? 0–10", style = HudType.label, color = Hud.muted)
        Choices((0..10).toList(), { it == value }, { it.toString() }, onPick)
        Text(
            value?.let { v -> "$v · ${rpeLabel(v).ifBlank { "between anchors" }}" + if (v <= 4) " · counts as easy" else "" } ?: "Rate the whole session.",
            style = HudType.small,
            color = if (value != null) Hud.green else Hud.muted,
        )
    }
}

/** Log or fix a swim, bike, run or other session. */
@Composable
fun SessionDialog(
    initialKind: WorkoutKind,
    initialMinutes: Double?,
    existing: Workout?,
    onSave: (kind: WorkoutKind, minutes: Double, km: Double?, rpe: Double?, brick: Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    initialBrick: Boolean = false,
) {
    var kind by remember { mutableStateOf(existing?.kind ?: initialKind) }
    var minutes by remember { mutableStateOf(numText(existing?.minutes ?: initialMinutes)) }
    var km by remember { mutableStateOf(numText(existing?.distanceKm)) }
    var rpe by remember { mutableStateOf(existing?.rpe?.toInt()) }
    var brick by remember { mutableStateOf(existing?.brick ?: initialBrick) }
    val m = parseNumber(minutes)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Hud.panel,
        title = { Text(if (existing == null) "LOG SESSION" else "FIX SESSION", style = HudType.title, color = Hud.ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Choices(listOf(WorkoutKind.SWIM, WorkoutKind.BIKE, WorkoutKind.RUN, WorkoutKind.OTHER), { it == kind }, { it.label }, { kind = it })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberField("Minutes", minutes, { minutes = it }, Modifier.weight(1f), "min")
                    NumberField("Distance", km, { km = it }, Modifier.weight(1f), "km")
                }
                if (kind == WorkoutKind.RUN) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Brick (straight off the bike)", style = HudType.body, color = Hud.ink, modifier = Modifier.weight(1f))
                        Switch(brick, { brick = it }, colors = SwitchDefaults.colors(checkedThumbColor = Hud.onMint, checkedTrackColor = Hud.green))
                    }
                }
                RpePicker(rpe) { rpe = it }
                Hint("Best rated about 30 minutes after you finish (Foster et al. 2001).")
            }
        },
        confirmButton = {
            TextButton(enabled = m != null && m in 1.0..900.0, onClick = {
                onSave(kind, m!!, parseNumber(km), rpe?.toDouble(), brick && kind == WorkoutKind.RUN)
                onDismiss()
            }) { Text("SAVE") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { onDelete(); onDismiss() }) { Text("DELETE", color = Hud.amber) }
                TextButton(onClick = onDismiss) { Text("CANCEL") }
            }
        },
    )
}

/** Session RPE when a lift ends. */
@Composable
fun FinishLiftDialog(onFinish: (Double?) -> Unit, onDismiss: () -> Unit) {
    var rpe by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Hud.panel,
        title = { Text("FINISH WORKOUT", style = HudType.title, color = Hud.ink) },
        text = { RpePicker(rpe) { rpe = it } },
        confirmButton = { TextButton(onClick = { onFinish(rpe?.toDouble()); onDismiss() }) { Text(if (rpe == null) "FINISH WITHOUT RATING" else "FINISH") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("KEEP GOING") } },
    )
}

/** Stand down for the day: pain, illness or exhaustion (rule of command 6). */
@Composable
fun StandDownDialog(onPick: (StandDown) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Hud.panel,
        title = { Text("STAND DOWN TODAY?", style = HudType.title, color = Hud.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Today's training becomes rest. Nothing is counted against you.", style = HudType.body, color = Hud.ink)
                Choices(StandDown.entries, { false }, { it.label }, { onPick(it); onDismiss() })
                Hint("Chest pain, faintness or unusual breathlessness during exercise: stop and get medical help.")
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}

/** Fix or delete a logged set. */
@Composable
fun SetDialog(set: SetRecord, timed: Boolean, onSave: (Double, Int, Int?) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var weight by remember { mutableStateOf(numText(set.weight)) }
    var reps by remember { mutableStateOf(set.reps.toString()) }
    var rir by remember { mutableStateOf(set.rir) }
    val w = parseNumber(weight)
    val r = reps.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Hud.panel,
        title = { Text("SET ${set.setIndex + 1}", style = HudType.title, color = Hud.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberField("Weight", weight, { weight = it }, Modifier.weight(1f), "kg")
                    NumberField(if (timed) "Seconds" else "Reps", reps, { reps = it.filter(Char::isDigit) }, Modifier.weight(1f))
                }
                Text("REPS IN RESERVE", style = HudType.label, color = Hud.muted)
                Choices((0..5).toList(), { it == rir }, { if (it == 5) "5+" else it.toString() }, { rir = if (rir == it) null else it })
            }
        },
        confirmButton = {
            TextButton(enabled = w != null && w in 0.0..500.0 && r != null && r in 1..600, onClick = { onSave(w!!, r!!, rir); onDismiss() }) { Text("SAVE") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onDelete(); onDismiss() }) { Text("DELETE", color = Hud.amber) }
                TextButton(onClick = onDismiss) { Text("CANCEL") }
            }
        },
    )
}

/** How to do an exercise, and what to swap it for. */
@Composable
fun ExerciseDialog(
    exercise: Exercise?,
    fallbackName: String,
    alternatives: List<Exercise>,
    swapped: Boolean,
    onSwap: ((String?) -> Unit)?,
    onDismiss: () -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Hud.panel,
        title = { Text(exercise?.name ?: fallbackName, style = HudType.value, color = Hud.ink) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!choosing) {
                    exercise?.let { e ->
                        Text(
                            ("Works: " + (e.primary + e.secondary.map { "$it (some)" }).joinToString(", ")).uppercase(),
                            style = HudType.label,
                            color = Hud.green,
                        )
                        e.instructions.forEachIndexed { i, step -> Text("${i + 1}. $step", style = HudType.small, color = Hud.ink) }
                    } ?: Text("Details unavailable.", style = HudType.small, color = Hud.muted)
                    Hint("From free-exercise-db. Stop if anything hurts.")
                } else {
                    Text("SAME MAIN MUSCLE", style = HudType.label, color = Hud.muted)
                    alternatives.take(30).forEach { a ->
                        Text(
                            "${a.name} · ${a.equipment.ifBlank { "body only" }}",
                            style = HudType.small,
                            color = Hud.ink,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickableRow { onSwap?.invoke(a.id); onDismiss() },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (onSwap != null && swapped && !choosing) TextButton(onClick = { onSwap(null); onDismiss() }) { Text("USE ORIGINAL") }
                if (onSwap != null && !choosing && alternatives.isNotEmpty()) TextButton(onClick = { choosing = true }) { Text("SWAP") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } },
    )
}
