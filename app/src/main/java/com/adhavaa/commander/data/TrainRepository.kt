package com.adhavaa.commander.data

import android.content.Context
import androidx.room.withTransaction
import com.adhavaa.commander.data.db.CommanderDb
import com.adhavaa.commander.data.db.SetEntity
import com.adhavaa.commander.data.db.TrainDayEntity
import com.adhavaa.commander.data.db.WorkoutEntity
import com.adhavaa.commander.train.Exercise
import com.adhavaa.commander.train.SetRecord
import com.adhavaa.commander.train.StandDown
import com.adhavaa.commander.train.Workout
import com.adhavaa.commander.train.WorkoutKind
import com.adhavaa.commander.train.readExercises
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

/** Workouts, lifted sets and stand-down days. */
class TrainRepository(private val db: CommanderDb) {
    private val dao = db.dao()

    private fun toWorkout(e: WorkoutEntity) = Workout(
        id = e.id,
        date = LocalDate.ofEpochDay(e.epochDay),
        kind = WorkoutKind.entries.firstOrNull { it.name == e.kind } ?: WorkoutKind.OTHER,
        label = e.label,
        ended = e.endMs != null,
        minutes = e.minutes,
        distanceKm = e.distanceKm,
        rpe = e.rpe,
        notes = e.notes,
        brick = e.brick,
        startMs = e.startMs,
    )

    private fun toSet(e: SetEntity) = SetRecord(e.id, e.workoutId, LocalDate.ofEpochDay(e.epochDay), e.exercise, e.setIndex, e.weightKg, e.reps, e.rir)

    fun observeWorkouts(): Flow<List<Workout>> = dao.observeWorkouts().map { l -> l.map(::toWorkout) }

    fun observeSets(): Flow<List<SetRecord>> = dao.observeSets().map { l -> l.map(::toSet) }

    fun observeStandDowns(): Flow<Map<LocalDate, StandDown>> = dao.observeTrainDays().map { l ->
        l.mapNotNull { e -> StandDown.entries.firstOrNull { it.name == e.standDown }?.let { LocalDate.ofEpochDay(e.epochDay) to it } }.toMap()
    }

    /** Starts a lift, or returns the one already in progress. */
    suspend fun startLift(date: LocalDate, label: String): Long = db.withTransaction {
        // A lift left open on an earlier day is closed at its last logged set, so it counts for progression.
        for (stale in dao.openLifts(WorkoutKind.LIFT.name).filter { it.epochDay != date.toEpochDay() }) {
            val last = dao.setsOf(stale.id).maxOfOrNull { it.atMs } ?: stale.startMs
            dao.updateWorkout(stale.copy(endMs = last, minutes = ((last - stale.startMs) / 60_000.0).coerceIn(1.0, 360.0)))
        }
        val open = dao.workoutsOn(date.toEpochDay()).firstOrNull { it.kind == WorkoutKind.LIFT.name && it.endMs == null }
        open?.id ?: dao.insertWorkout(
            WorkoutEntity(
                epochDay = date.toEpochDay(), kind = WorkoutKind.LIFT.name, label = label, startMs = System.currentTimeMillis(),
                endMs = null, minutes = 0.0, distanceKm = null, rpe = null, notes = "", brick = false,
            ),
        )
    }

    suspend fun logSet(workoutId: Long, date: LocalDate, exercise: String, weight: Double, reps: Int, rir: Int?): Long {
        val index = dao.setsOf(workoutId).count { it.exercise == exercise }
        return dao.insertSet(SetEntity(0, workoutId, date.toEpochDay(), exercise, index, weight, reps, rir, System.currentTimeMillis()))
    }

    /** Fixes a set's numbers; it keeps its place (time logged). */
    suspend fun updateSet(s: SetRecord, weight: Double, reps: Int, rir: Int?) {
        val e = dao.setsOf(s.workoutId).firstOrNull { it.id == s.id } ?: return
        dao.updateSet(e.copy(weightKg = weight, reps = reps, rir = rir))
    }

    suspend fun deleteSet(id: Long) = dao.deleteSet(id)

    /** Ends a lift with its session RPE; minutes run from the start to now (at least 1). */
    suspend fun finishLift(workoutId: Long, rpe: Double?) {
        val w = dao.workout(workoutId) ?: return
        val now = System.currentTimeMillis()
        val minutes = ((now - w.startMs) / 60_000.0).coerceIn(1.0, 360.0)
        dao.updateWorkout(w.copy(endMs = now, minutes = minutes, rpe = rpe))
    }

    /** A finished session logged in one go: swim, bike, run, mobility or other. */
    suspend fun logSession(
        date: LocalDate, kind: WorkoutKind, label: String, minutes: Double, distanceKm: Double?, rpe: Double?, brick: Boolean, notes: String = "",
    ): Long {
        val now = System.currentTimeMillis()
        return dao.insertWorkout(
            WorkoutEntity(
                epochDay = date.toEpochDay(), kind = kind.name, label = label, startMs = now, endMs = now,
                minutes = minutes, distanceKm = distanceKm, rpe = rpe, notes = notes, brick = brick,
            ),
        )
    }

    suspend fun updateSession(w: Workout, minutes: Double, distanceKm: Double?, rpe: Double?, kind: WorkoutKind, brick: Boolean) {
        val e = dao.workout(w.id) ?: return
        dao.updateWorkout(e.copy(minutes = minutes, distanceKm = distanceKm, rpe = rpe, kind = kind.name, brick = brick))
    }

    suspend fun deleteWorkout(id: Long) = db.withTransaction {
        dao.deleteSetsOf(id)
        dao.deleteWorkout(id)
    }

    suspend fun setStandDown(date: LocalDate, reason: StandDown?) {
        if (reason == null) dao.deleteTrainDay(date.toEpochDay()) else dao.upsertTrainDay(TrainDayEntity(date.toEpochDay(), reason.name))
    }

    suspend fun mobilityDoneOn(date: LocalDate): Boolean = dao.workoutsOn(date.toEpochDay()).any { it.kind == WorkoutKind.MOBILITY.name }
}

/** The exercise list bundled from free-exercise-db (876 entries), loaded once. A failed load never crashes. */
class ExerciseCatalog(private val load: () -> List<Exercise>) {
    private val lock = Mutex()
    @Volatile private var cache: Map<String, Exercise>? = null

    @Volatile
    var error: String? = null
        private set

    /** Already loaded, or null. */
    fun loaded(): Map<String, Exercise>? = cache

    suspend fun all(): Map<String, Exercise> = cache ?: lock.withLock {
        cache ?: try {
            withContext(Dispatchers.IO) { load().associateBy { it.id } }.also {
                cache = it
                error = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "The exercise list couldn't load (${e.javaClass.simpleName})."
            emptyMap()
        }
    }

    companion object {
        fun fromAssets(context: Context): ExerciseCatalog = ExerciseCatalog {
            context.assets.open("exercises.tsv").use { readExercises(it) }
        }
    }
}
