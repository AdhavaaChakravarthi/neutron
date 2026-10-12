package com.adhavaa.commander

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adhavaa.commander.data.TrainSettings
import com.adhavaa.commander.notify.RestTimer
import com.adhavaa.commander.orders.CommandData
import com.adhavaa.commander.model.Equipment
import com.adhavaa.commander.train.Exercise
import com.adhavaa.commander.train.LiftTarget
import com.adhavaa.commander.train.PROGRAM
import com.adhavaa.commander.train.equipmentOf
import com.adhavaa.commander.train.liftTargets
import com.adhavaa.commander.train.Routine
import com.adhavaa.commander.train.SetRecord
import com.adhavaa.commander.train.StandDown
import com.adhavaa.commander.train.TrainData
import com.adhavaa.commander.train.TrainPicture
import com.adhavaa.commander.train.Workout
import com.adhavaa.commander.train.WorkoutKind
import com.adhavaa.commander.train.trainPicture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

/** Everything the Train tab draws. */
data class TrainUi(
    val settings: TrainSettings,
    val picture: TrainPicture,
    val workouts: List<Workout>,
    val sets: List<SetRecord>,
    val exercises: Map<String, Exercise>,
    val debtH: Double?,
    /** Each program day's exercises with their next loads, by day key. */
    val targets: Map<String, List<LiftTarget>>,
)

/** A running rest timer: ends at [endAtMillis], [next] is what comes after it. */
data class Rest(val endAtMillis: Long, val totalSeconds: Int, val next: String)

class TrainViewModel(private val app: Application) : AndroidViewModel(app) {
    private val c = app.container()

    private val today: Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000)
        }
    }.distinctUntilChanged()

    private val data: Flow<TrainData> = combine(
        c.settings.settings.map { it.train }, c.train.observeWorkouts(), c.train.observeSets(), c.train.observeStandDowns(),
    ) { s, w, sets, sd -> TrainData(s, w, sets, sd) }.distinctUntilChanged()

    /** Sleep debt from the sleep picture, for easing training on high-debt days (MODEL.md 11.3). */
    private val debt: Flow<Double?> = combine(c.settings.settings, c.sleep.observeSleep(), c.sleep.observeSkipped()) { s, sleep, skipped ->
        CommandData(s, sleep.map { it.record }, skipped)
    }.distinctUntilChanged().map { d -> d.picture(d.planDate(LocalDateTime.now())).stats.debtH }.distinctUntilChanged()

    private val exercises: Flow<Map<String, Exercise>> = flow { emit(c.exercises.all()) }

    val state: StateFlow<TrainUi?> = combine(today, data, debt, exercises) { t, d, debtH, ex ->
        val eq: (String) -> Equipment = { id -> ex[id]?.let { equipmentOf(it.equipment) } ?: Equipment.BODY }
        TrainUi(
            d.settings, trainPicture(d, t, t, debtH) { ex[it] }, d.workouts, d.sets, ex, debtH,
            PROGRAM.associate { day -> day.key to liftTargets(d, day, t, eq) },
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val restState = MutableStateFlow<Rest?>(null)
    val rest: StateFlow<Rest?> = restState.asStateFlow()

    fun catalogError(): String? = c.exercises.error

    fun setup(t: TrainSettings) = io { c.settings.setTrain(t, LocalDate.now()) }

    fun swap(slot: String, exerciseId: String?) = io {
        c.settings.setSwap(slot, exerciseId, c.settings.settings.first().train.swaps)
    }

    suspend fun startLift(label: String): Long = withContext(Dispatchers.IO) { c.train.startLift(LocalDate.now(), label) }

    fun logSet(workoutId: Long, exercise: String, weight: Double, reps: Int, rir: Int?) =
        io { c.train.logSet(workoutId, LocalDate.now(), exercise, weight, reps, rir) }

    fun updateSet(s: SetRecord, weight: Double, reps: Int, rir: Int?) = io { c.train.updateSet(s, weight, reps, rir) }

    fun deleteSet(id: Long) = io { c.train.deleteSet(id) }

    fun finishLift(workoutId: Long, rpe: Double?) {
        stopRest()
        io { c.train.finishLift(workoutId, rpe) }
    }

    fun logSession(date: LocalDate, kind: WorkoutKind, label: String, minutes: Double, km: Double?, rpe: Double?, brick: Boolean) =
        io { c.train.logSession(date, kind, label, minutes, km, rpe, brick) }

    fun updateSession(w: Workout, minutes: Double, km: Double?, rpe: Double?, kind: WorkoutKind, brick: Boolean) =
        io { c.train.updateSession(w, minutes, km, rpe, kind, brick) }

    fun deleteWorkout(id: Long) = io { c.train.deleteWorkout(id) }

    fun standDown(date: LocalDate, reason: StandDown?) = io { c.train.setStandDown(date, reason) }

    fun logRoutine(r: Routine, seconds: Int) =
        io { c.train.logSession(LocalDate.now(), WorkoutKind.MOBILITY, r.label, maxOf(1.0, seconds / 60.0), null, null, false) }

    fun startRest(seconds: Int, next: String) {
        val end = System.currentTimeMillis() + seconds * 1000L
        restState.value = Rest(end, seconds, next)
        RestTimer.start(app, end, next)
    }

    fun addRest(seconds: Int) {
        val r = restState.value ?: return
        startRest(((r.endAtMillis - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0) + seconds, r.next)
    }

    fun stopRest() {
        restState.value = null
        RestTimer.cancel(app)
    }

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { block() }
    }
}
