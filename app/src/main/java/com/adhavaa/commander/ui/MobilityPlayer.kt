package com.adhavaa.commander.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adhavaa.commander.train.Exercise
import com.adhavaa.commander.train.Routine
import com.adhavaa.commander.train.mmss
import kotlinx.coroutines.delay

/**
 * Plays a mobility routine hold by hold: name, side, a countdown and how to do it. The screen stays on while it
 * runs. Finishing (or stopping after at least one hold) logs the minutes done.
 */
@Composable
fun MobilityPlayer(
    routine: Routine,
    exercises: Map<String, Exercise>,
    onDone: (secondsDone: Int) -> Unit,
    onBack: () -> Unit,
    autoStart: Boolean = true,
) {
    val steps = routine.steps()
    var index by rememberSaveable { mutableIntStateOf(0) }
    var left by rememberSaveable { mutableIntStateOf(steps.first().first.seconds) }
    var running by rememberSaveable { mutableStateOf(autoStart) }
    var doneSeconds by rememberSaveable { mutableIntStateOf(0) }
    var logged by rememberSaveable { mutableStateOf(false) }
    val finished = index >= steps.size
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(running, index) {
        while (running && index < steps.size) {
            delay(1000)
            left -= 1
            doneSeconds += 1
            if (left <= 0) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                index += 1
                if (index < steps.size) left = steps[index].first.seconds
            }
        }
    }
    LaunchedEffect(finished) {
        if (finished && !logged) {
            logged = true
            onDone(doneSeconds)
        }
    }
    val leave = {
        if (!logged && doneSeconds >= 30) {
            logged = true
            onDone(doneSeconds)
        }
        onBack()
    }
    BackHandler(onBack = leave)
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Hud.bg),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = insets.calculateTopPadding() + 8.dp, bottom = insets.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SquareButton("Back", leave) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = Hud.muted, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(routine.label.uppercase(), style = HudType.title, color = Hud.ink)
                    Text(
                        "${minOf(index + 1, steps.size)} of ${steps.size} · ${mmss((routine.totalSeconds - doneSeconds).toLong())} left".uppercase(),
                        style = HudType.label,
                        color = Hud.green,
                    )
                }
            }
        }
        if (finished) {
            item {
                HudPanel(background = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Hud.mint, Hud.mintDeep)), border = Hud.mint) {
                    Text("◆ DONE", style = HudType.title, color = Hud.onMint)
                    Text("${routine.label} complete. Logged.", style = HudType.body, color = Hud.onMint)
                }
            }
            item { HudButton("Close", onBack) }
        } else {
            val (move, side) = steps[index]
            val e = exercises[move.exerciseId]
            item {
                HudPanel(
                    background = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Hud.panel, Hud.bg)),
                    decoration = Modifier.contours(seed = 6f),
                ) {
                    Text((e?.name ?: move.exerciseId.replace('_', ' ')).uppercase(), style = HudType.title, color = Hud.ink)
                    side?.let { HudChip("$it side", Tone.AMBER) }
                    Text(
                        mmss(left.toLong()),
                        style = HudType.headline.copy(fontSize = 64.sp, lineHeight = 70.sp),
                        color = if (running) Hud.green else Hud.muted,
                    )
                    Text("Ease into mild tightness. Never pain. Breathe.", style = HudType.small, color = Hud.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { running = !running }) { Text(if (running) "PAUSE" else "GO") }
                        OutlinedButton(onClick = {
                            index += 1
                            if (index < steps.size) left = steps[index].first.seconds
                        }) { Text("SKIP") }
                    }
                }
            }
            e?.let { ex ->
                item {
                    HudPanel(title = "How", subtitle = "Hold for the timer (${move.seconds} s), even if the steps give another time") {
                        ex.instructions.forEachIndexed { i, s -> Text("${i + 1}. $s", style = HudType.small, color = Hud.ink) }
                    }
                }
            }
            item {
                HudPanel(title = "Up next") {
                    steps.drop(index + 1).take(4).forEach { (m, sd) ->
                        Text(
                            (exercises[m.exerciseId]?.name ?: m.exerciseId.replace('_', ' ')) + (sd?.let { " · $it" } ?: "") + " · ${m.seconds} s",
                            style = HudType.small,
                            color = Hud.muted,
                        )
                    }
                    if (index + 1 >= steps.size) Text("Last one.", style = HudType.small, color = Hud.muted)
                }
            }
        }
        item { androidx.compose.foundation.layout.Spacer(Modifier.fillMaxWidth().height(8.dp)) }
    }
}
