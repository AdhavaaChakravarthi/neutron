package com.adhavaa.commander.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adhavaa.commander.Rest
import com.adhavaa.commander.TrainUi
import com.adhavaa.commander.model.Equipment
import com.adhavaa.commander.model.LiftSet
import com.adhavaa.commander.train.LiftDay
import com.adhavaa.commander.train.LiftTarget
import com.adhavaa.commander.train.SetRecord
import com.adhavaa.commander.train.kg
import com.adhavaa.commander.train.mmss
import com.adhavaa.commander.train.suggestedWeight
import com.adhavaa.commander.train.targetText
import kotlinx.coroutines.delay

/** What the lift logger can do. */
data class LiftActions(
    val onBack: () -> Unit = {},
    val onLogSet: (exercise: String, weight: Double, reps: Int, rir: Int?, restSeconds: Int, next: String) -> Unit = { _, _, _, _, _, _ -> },
    val onEditSet: (SetRecord) -> Unit = {},
    val onFinish: () -> Unit = {},
    val onExercise: (LiftTarget) -> Unit = {},
    val onWarmUp: () -> Unit = {},
    val onAddRest: () -> Unit = {},
    val onSkipRest: () -> Unit = {},
)

/**
 * Today's lift, set by set: the target from double progression, the sets logged so far, and a row for the next
 * set. Logging a set starts the rest timer (ACSM 2009 rest periods).
 */
@Composable
fun LiftScreen(
    ui: TrainUi,
    day: LiftDay,
    workoutId: Long,
    startMs: Long,
    rest: Rest?,
    actions: LiftActions,
    now: () -> Long = System::currentTimeMillis,
    ticking: Boolean = true,
) {
    BackHandler(onBack = actions.onBack)
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val targets = ui.targets[day.key].orEmpty()
    val logged = ui.sets.filter { it.workoutId == workoutId }
    var clock by remember { mutableLongStateOf(now()) }
    if (ticking) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                clock = now()
            }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Hud.bg).imePadding(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = insets.calculateTopPadding() + 8.dp, bottom = insets.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SquareButton("Back", actions.onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = Hud.muted, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(day.label.uppercase(), style = HudType.title, color = Hud.ink)
                    Text("${mmss((clock - startMs) / 1000)} · ${logged.size} sets logged".uppercase(), style = HudType.label, color = Hud.green)
                }
                Text(
                    "FINISH",
                    style = HudType.label,
                    color = Hud.onMint,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Hud.green)
                        .clickable(onClickLabel = "Finish workout", role = Role.Button, onClick = actions.onFinish)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                )
            }
        }
        rest?.let { r ->
            item {
                val left = (r.endAtMillis - clock) / 1000
                HudPanel(
                    background = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(if (left > 0) Hud.greenDeep else Hud.amberDeep, Hud.panel)),
                    border = if (left > 0) Hud.green.copy(alpha = 0.5f) else Hud.amber,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (left > 0) "REST ${mmss(left)}" else "REST OVER", style = HudType.headline, color = if (left > 0) Hud.green else Hud.amber)
                            Text("Next: ${r.next}", style = HudType.small, color = Hud.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(onClick = actions.onAddRest) { Text("+30 S") }
                        TextButton(onClick = actions.onSkipRest) { Text(if (left > 0) "SKIP" else "CLEAR") }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Warm up first: 5 min of dynamic moves.", style = HudType.small, color = Hud.muted, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = actions.onWarmUp) { Text("WARM-UP") }
            }
        }
        if (ui.picture.day.eased) {
            item { Text("Sleep debt is high: one set fewer per exercise, keep 3 or more reps in reserve.", style = HudType.small, color = Hud.amber) }
        }
        items(targets, key = { it.item.slot }) { t ->
            val mine = logged.filter { it.exercise == t.exerciseId }.sortedBy { it.setIndex }
            val sets = ui.picture.day.setsFor(t.item)
            val idx = targets.indexOf(t)
            val next = when {
                mine.size + 1 < sets -> "${nameOf(ui, t)} set ${mine.size + 2}"
                idx + 1 < targets.size -> nameOf(ui, targets[idx + 1])
                else -> "Finish and rate the session"
            }
            ExerciseCard(ui, t, sets, mine, next, actions)
        }
    }
}

private fun nameOf(ui: TrainUi, t: LiftTarget): String = ui.exercises[t.exerciseId]?.name ?: t.exerciseId.replace('_', ' ')

@Composable
private fun ExerciseCard(ui: TrainUi, t: LiftTarget, sets: Int, mine: List<SetRecord>, next: String, actions: LiftActions) {
    val body = t.equipment == Equipment.BODY
    val i = t.item
    HudPanel(
        title = nameOf(ui, t),
        subtitle = "${sets} × ${i.lo}–${i.hi}${if (i.timed) " s" else ""} · rest ${mmss(i.restSeconds.toLong())} · 1–3 in reserve",
        trailing = {
            Text(
                "HOW",
                style = HudType.label,
                color = Hud.green,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(role = Role.Button) { actions.onExercise(t) }.padding(6.dp),
            )
        },
    ) {
        Text(targetText(t), style = HudType.small, color = Hud.green)
        for (s in mine) {
            Row(
                Modifier.fillMaxWidth().clickableRow { actions.onEditSet(s) },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${s.setIndex + 1}", style = HudType.value, color = Hud.green)
                Text(
                    (if (body && s.weight == 0.0) "BW" else if (body) "BW+${kg(s.weight)} kg" else "${kg(s.weight)} kg") +
                        " × ${s.reps}${if (i.timed) " s" else ""}",
                    style = HudType.value,
                    color = Hud.ink,
                    modifier = Modifier.weight(1f),
                )
                s.rir?.let { Text("RIR $it", style = HudType.label, color = Hud.muted) }
            }
        }
        if (mine.size < sets) {
            SetEntry(t, mine.map { LiftSet(it.weight, it.reps) }, body) { w, r, rir -> actions.onLogSet(t.exerciseId, w, r, rir, i.restSeconds, next) }
        } else {
            var extra by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✓ ${mine.size} sets done", style = HudType.small, color = Hud.green, modifier = Modifier.weight(1f))
                if (!extra) TextButton(onClick = { extra = true }) { Text("+ SET") }
            }
            if (extra) {
                SetEntry(t, mine.map { LiftSet(it.weight, it.reps) }, body) { w, r, rir ->
                    extra = false
                    actions.onLogSet(t.exerciseId, w, r, rir, i.restSeconds, next)
                }
            }
        }
    }
}

@Composable
private fun SetEntry(t: LiftTarget, today: List<LiftSet>, body: Boolean, onLog: (Double, Int, Int?) -> Unit) {
    val i = t.item
    var weight by remember(today.size) { mutableStateOf(numText(suggestedWeight(t, today))) }
    var reps by remember(today.size) { mutableStateOf("") }
    var rir by remember(today.size) { mutableStateOf<Int?>(null) }
    val w = parseNumber(weight) ?: if (body) 0.0 else null
    val r = reps.toIntOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberField(if (body) "Added kg" else "Weight", weight, { weight = it }, Modifier.weight(1f), "kg")
            NumberField(if (i.timed) "Seconds" else "Reps", reps, { reps = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), if (i.timed) "s" else null)
            Text(
                "LOG",
                style = HudType.label,
                color = if (w != null && r != null && r > 0) Hud.onMint else Hud.dim,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (w != null && r != null && r > 0) Hud.green else Hud.panelHi)
                    .clickable(enabled = w != null && r != null && r > 0, onClickLabel = "Log set", role = Role.Button) { onLog(w!!, r!!, rir) }
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("IN RESERVE", style = HudType.label, color = Hud.muted)
            Choices((0..5).toList(), { it == rir }, { if (it == 5) "5+" else it.toString() }, { rir = if (rir == it) null else it })
        }
    }
}
