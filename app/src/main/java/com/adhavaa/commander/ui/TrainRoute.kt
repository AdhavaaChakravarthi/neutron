package com.adhavaa.commander.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adhavaa.commander.TrainViewModel
import com.adhavaa.commander.model.PlannedSession
import com.adhavaa.commander.train.LiftTarget
import com.adhavaa.commander.train.Routine
import com.adhavaa.commander.train.SetRecord
import com.adhavaa.commander.train.WARM_UP
import com.adhavaa.commander.train.Workout
import com.adhavaa.commander.train.WorkoutKind
import com.adhavaa.commander.train.alternatives
import kotlinx.coroutines.launch

private sealed interface TrainPage {
    data object Setup : TrainPage
    data object Lift : TrainPage
    data class Play(val routine: Routine, val back: TrainPage?) : TrainPage
}

/** The Train tab with its state, pages and dialogs. */
@Composable
fun TrainRoute(onOpenSettings: () -> Unit, onFullScreen: (Boolean) -> Unit, vm: TrainViewModel = viewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val rest by vm.rest.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf<TrainPage?>(null) }
    var logSession by remember { mutableStateOf<Pair<PlannedSession?, Workout?>?>(null) }
    var standDown by remember { mutableStateOf(false) }
    var exercise by remember { mutableStateOf<LiftTarget?>(null) }
    var editSet by remember { mutableStateOf<SetRecord?>(null) }
    var finishing by remember { mutableStateOf(false) }
    var liftId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(page) { onFullScreen(page != null) }

    val u = ui ?: run {
        Column(Modifier.fillMaxSize().background(Hud.bg)) {}
        return
    }
    val today = u.picture.today
    val liftDay = u.picture.day.lift
    val open = u.picture.inProgress

    when (val p = page) {
        TrainPage.Setup -> TrainSetupScreen(u.settings, today, vm::setup) { page = null }
        TrainPage.Lift -> {
            // Right after starting, the new workout may not have reached the state yet: wait rather than bounce back.
            val w = u.workouts.firstOrNull { it.id == liftId && !it.ended } ?: open
            val day = com.adhavaa.commander.train.PROGRAM.firstOrNull { it.label == w?.label } ?: liftDay
            if (w == null || day == null) {
                Column(Modifier.fillMaxSize().background(Hud.bg)) {}
            } else {
                LiftScreen(
                    ui = u,
                    day = day,
                    workoutId = w.id,
                    startMs = w.startMs,
                    rest = rest,
                    actions = LiftActions(
                        onBack = { page = null },
                        onLogSet = { ex, weight, reps, rir, restS, next ->
                            vm.logSet(w.id, ex, weight, reps, rir)
                            vm.startRest(restS, next)
                        },
                        onEditSet = { editSet = it },
                        onFinish = { finishing = true },
                        onExercise = { exercise = it },
                        onWarmUp = { page = TrainPage.Play(WARM_UP, TrainPage.Lift) },
                        onAddRest = { vm.addRest(30) },
                        onSkipRest = vm::stopRest,
                    ),
                )
            }
        }
        is TrainPage.Play -> MobilityPlayer(p.routine, u.exercises, onDone = { vm.logRoutine(p.routine, it) }, onBack = { page = p.back })
        null -> TrainScreen(
            u,
            TrainActions(
                onOpenSettings = onOpenSettings,
                onSetup = { page = TrainPage.Setup },
                onStartLift = {
                    liftDay?.let { d ->
                        scope.launch {
                            liftId = vm.startLift(d.label)
                            page = TrainPage.Lift
                        }
                    }
                },
                onLogSession = { logSession = it to null },
                onEditWorkout = { logSession = null to it },
                onStartRoutine = { page = TrainPage.Play(it, null) },
                onStandDown = { standDown = true },
                onUndoStandDown = { vm.standDown(today, null) },
                onExercise = { exercise = it },
            ),
        )
    }

    logSession?.let { (planned, existing) ->
        SessionDialog(
            initialKind = planned?.let { WorkoutKind.of(it.sport) } ?: WorkoutKind.RUN,
            initialMinutes = planned?.minutes,
            existing = existing,
            initialBrick = planned?.brick == true,
            onSave = { kind, minutes, km, rpe, brick ->
                if (existing == null) {
                    vm.logSession(today, kind, if (brick) "Brick run" else kind.label, minutes, km, rpe, brick)
                } else {
                    vm.updateSession(existing, minutes, km, rpe, kind, brick)
                }
            },
            onDelete = existing?.let { w -> { vm.deleteWorkout(w.id) } },
            onDismiss = { logSession = null },
        )
    }
    if (standDown) StandDownDialog(onPick = { vm.standDown(today, it) }, onDismiss = { standDown = false })
    exercise?.let { t ->
        val e = u.exercises[t.exerciseId]
        val original = t.exerciseId == t.item.exerciseId
        ExerciseDialog(
            exercise = e,
            fallbackName = t.exerciseId.replace('_', ' '),
            alternatives = e?.let { alternatives(it, u.exercises.values.toList()) }.orEmpty(),
            swapped = !original,
            onSwap = { id -> vm.swap(t.item.slot, id) },
            onDismiss = { exercise = null },
        )
    }
    editSet?.let { s ->
        val timed = u.targets.values.flatten().firstOrNull { it.exerciseId == s.exercise }?.item?.timed == true
        SetDialog(s, timed, onSave = { w, r, rir -> vm.updateSet(s, w, r, rir) }, onDelete = { vm.deleteSet(s.id) }, onDismiss = { editSet = null })
    }
    if (finishing) {
        FinishLiftDialog(
            onFinish = { rpe ->
                (liftId ?: open?.id)?.let { vm.finishLift(it, rpe) }
                liftId = null
                page = null
            },
            onDismiss = { finishing = false },
        )
    }
}
