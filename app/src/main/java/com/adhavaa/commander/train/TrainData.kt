package com.adhavaa.commander.train

import com.adhavaa.commander.data.TrainSettings
import com.adhavaa.commander.fuel.FuelData
import com.adhavaa.commander.model.Equipment
import com.adhavaa.commander.model.Intensity
import com.adhavaa.commander.model.LiftSet
import com.adhavaa.commander.model.NextLoad
import com.adhavaa.commander.model.PlanWeek
import com.adhavaa.commander.model.PlannedSession
import com.adhavaa.commander.model.Sport
import com.adhavaa.commander.model.e1rm
import com.adhavaa.commander.model.easyShare
import com.adhavaa.commander.model.nextLoad
import com.adhavaa.commander.model.sessionLoad
import com.adhavaa.commander.model.swimBackDay
import com.adhavaa.commander.model.weekPlan
import com.adhavaa.commander.model.weekSessions
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class WorkoutKind(val label: String) {
    LIFT("Lift"), SWIM("Swim"), BIKE("Bike"), RUN("Run"), MOBILITY("Mobility"), OTHER("Other");

    val sport: Sport? get() = when (this) {
        SWIM -> Sport.SWIM
        BIKE -> Sport.BIKE
        RUN -> Sport.RUN
        else -> null
    }

    companion object {
        fun of(s: Sport): WorkoutKind = when (s) {
            Sport.SWIM -> SWIM
            Sport.BIKE -> BIKE
            Sport.RUN -> RUN
        }
    }
}

/** A logged session. A lift in progress has [ended] false. [rpe] is Foster's session RPE, 0-10. */
data class Workout(
    val id: Long,
    val date: LocalDate,
    val kind: WorkoutKind,
    val label: String,
    val ended: Boolean,
    val minutes: Double,
    val distanceKm: Double?,
    val rpe: Double?,
    val notes: String,
    val brick: Boolean,
    val startMs: Long = 0,
)

/** One logged set. For a timed exercise [reps] holds seconds. */
data class SetRecord(
    val id: Long,
    val workoutId: Long,
    val date: LocalDate,
    val exercise: String,
    val setIndex: Int,
    val weight: Double,
    val reps: Int,
    val rir: Int?,
)

/** Why you're standing down today (rule of command 6). */
enum class StandDown(val label: String) { PAIN("Pain"), ILLNESS("Illness"), EXHAUSTED("Exhausted") }

/** Sleep debt at which training eases off (the High band, MODEL.md 11.3). */
const val HIGH_DEBT_H = 5.0

/**
 * Where the swimming pause stands (D-77): the day the trend first reached the mark, the Monday swimming returns, and
 * kg still to go on the latest trend. [tracked] is false without a pause or without Fuel's weigh-ins.
 */
data class SwimStatus(
    val pauseKg: Double? = null,
    val markDay: LocalDate? = null,
    val from: LocalDate? = null,
    val kgToGo: Double? = null,
    val tracked: Boolean = false,
)

/** The swimming pause from Fuel's trend and setup weight (MODEL.md 11.3). */
fun swimStatus(fuel: FuelData?, pauseKg: Double?, today: LocalDate): SwimStatus {
    if (pauseKg == null) return SwimStatus()
    if (fuel == null || !fuel.settings.setUp) return SwimStatus(pauseKg)
    val start = fuel.settings.startWeightKg!!
    val mark = start - pauseKg
    val trend = fuel.trend()
    val from = swimBackDay(trend, start, pauseKg)
    val hit = trend.entries.firstOrNull { it.value <= mark + 1e-9 }?.key
    val latest = trend.floorEntry(today)?.value
    return SwimStatus(pauseKg, hit, from, latest?.let { maxOf(0.0, it - mark) }, tracked = true)
}

data class TrainData(
    val settings: TrainSettings,
    val workouts: List<Workout>,
    val sets: List<SetRecord>,
    val standDowns: Map<LocalDate, StandDown>,
    val swim: SwimStatus = SwimStatus(),
) {
    /** Swimming is in the plan for the week starting [monday] (D-77): no pause, or the pause has ended. */
    fun swimOn(monday: LocalDate): Boolean = settings.swimPauseKg == null || (swim.from != null && !monday.isBefore(swim.from))
}

fun mondayOf(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Plan week index of [date]: 0 is the week containing the setup day. */
fun weekIndex(start: LocalDate, date: LocalDate): Int =
    Math.floorDiv(ChronoUnit.DAYS.between(mondayOf(start), mondayOf(date)), 7L).toInt()

/** Swim + bike + run minutes per completed week, by plan index (weeks before setup included). */
fun actualMinutes(data: TrainData, start: LocalDate, today: LocalDate): Map<Int, Double> {
    val current = weekIndex(start, today)
    return data.workouts.filter { it.kind.sport != null && it.ended }
        .groupBy { weekIndex(start, it.date) }
        .filterKeys { it < current }
        .mapValues { (_, l) -> l.sumOf { it.minutes } }
}

/** What a day asks for. */
data class DayPlan(
    val date: LocalDate,
    val lift: LiftDay?,
    val sessions: List<PlannedSession>,
    val standDown: StandDown?,
    /** High sleep debt: quality becomes easy and lifts drop a set (MODEL.md 11.3). */
    val eased: Boolean,
) {
    val rest: Boolean get() = lift == null && sessions.isEmpty()

    /** Lifting sets for an item today, after easing. */
    fun setsFor(item: ProgramItem): Int = if (eased) maxOf(1, item.sets - 1) else item.sets
}

/** Where a program exercise stands: the exercise in use (after any swap), what happened last and what's next. */
data class LiftTarget(
    val item: ProgramItem,
    val exerciseId: String,
    val equipment: Equipment,
    val next: NextLoad,
    val last: List<LiftSet>?,
    val bestE1rm: Double?,
)

data class TrainPicture(
    val date: LocalDate,
    val today: LocalDate,
    val setUp: Boolean,
    val week: PlanWeek?,
    val weekStart: LocalDate,
    val raceWeek: Int?,
    val day: DayPlan,
    val days: List<DayPlan>,
    val doneOn: Map<LocalDate, List<Workout>>,
    val inProgress: Workout?,
    val weekPlanned: Map<Sport, Double>,
    val weekDone: Map<Sport, Double>,
    val weekLoad: Double,
    val easyShare4w: Double?,
    val setsPerMuscle: Map<String, Double>,
    val plan: List<PlanWeek>,
    val actual: Map<Int, Double>,
    /** The swimming pause (D-77) and whether this week's plan has swims. */
    val swim: SwimStatus = SwimStatus(),
    val swimThisWeek: Boolean = true,
) {
    /** The trend reached the swim mark today: swimming is back from Monday. */
    val swimBackNews: Boolean get() = swim.markDay == today && swim.from != null
    val weeksToRace: Int? get() = raceWeek?.let { r -> week?.let { r - it.index } }

    fun done(date: LocalDate): List<Workout> = doneOn[date].orEmpty()

    /** Everything planned for the day is logged (a lift that's finished, a session per planned sport). */
    fun dayComplete(d: DayPlan): Boolean {
        if (d.standDown != null || d.rest) return d.standDown != null
        val done = done(d.date)
        val liftDone = d.lift == null || done.any { it.kind == WorkoutKind.LIFT && it.ended }
        val need = d.sessions.groupingBy { it.sport }.eachCount()
        val have = done.filter { it.kind.sport != null }.groupingBy { it.kind.sport!! }.eachCount()
        return liftDone && need.all { (s, n) -> (have[s] ?: 0) >= n }
    }

    fun mobilityDone(date: LocalDate): Boolean = done(date).any { it.kind == WorkoutKind.MOBILITY }
}

private fun dayPlan(date: LocalDate, week: PlanWeek?, standDown: StandDown?, eased: Boolean, swim: Boolean): DayPlan {
    val iso = date.dayOfWeek.value
    if (standDown != null) return DayPlan(date, null, emptyList(), standDown, eased)
    val sessions = week?.let { w -> weekSessions(w, swim).filter { it.day == iso } }.orEmpty()
        .map { if (eased) it.copy(intensity = Intensity.EASY) else it }
    return DayPlan(date, liftDayFor(iso), sessions, null, eased)
}

/**
 * What each day from [from] to [to] asked for, as the plan stood (no easing). Days before setup are left out
 * (MODEL.md section 12).
 */
fun dayPlans(data: TrainData, from: LocalDate, to: LocalDate, today: LocalDate): List<DayPlan> {
    val s = data.settings
    val start = s.start ?: return emptyList()
    val actual = actualMinutes(data, start, today)
    val raceWeek = s.raceDate?.let { weekIndex(start, it) }
    val weeks = HashMap<Int, PlanWeek>()
    val out = ArrayList<DayPlan>()
    var d = if (from.isBefore(start)) start else from
    while (!d.isAfter(to)) {
        val i = weekIndex(start, d)
        val week = weeks.getOrPut(i) { weekPlan(i, s.currentMinutes, s.peakMinutes, raceWeek, actual) }
        out += dayPlan(d, week, data.standDowns[d], eased = false, swim = data.swimOn(mondayOf(d)))
        d = d.plusDays(1)
    }
    return out
}

/** Sessions of [exerciseId] as lists of sets, oldest first, from finished workouts before [before]. */
fun liftHistory(data: TrainData, exerciseId: String, before: LocalDate? = null): List<List<LiftSet>> {
    val ended = data.workouts.filter { it.kind == WorkoutKind.LIFT && it.ended }.associateBy { it.id }
    return data.sets.filter { it.exercise == exerciseId && it.workoutId in ended && (before == null || it.date.isBefore(before)) }
        .groupBy { it.workoutId }
        .entries.sortedWith(compareBy({ ended.getValue(it.key).date }, { ended.getValue(it.key).startMs }, { it.key }))
        .map { (_, l) -> l.sortedBy { it.setIndex }.map { LiftSet(it.weight, it.reps) } }
}

fun liftTargets(data: TrainData, day: LiftDay, today: LocalDate, equipment: (String) -> Equipment): List<LiftTarget> =
    day.items.map { item ->
        val id = data.settings.swaps[item.slot] ?: item.exerciseId
        val eq = equipment(id)
        val history = liftHistory(data, id, before = today)
        LiftTarget(
            item = item,
            exerciseId = id,
            equipment = eq,
            next = nextLoad(item.prescription(eq), history),
            last = history.lastOrNull(),
            bestE1rm = if (item.timed) null else history.flatten().mapNotNull { e1rm(it.weight, it.reps) }.maxOrNull(),
        )
    }

/**
 * Weekly sets per muscle in the week containing [date]: primary muscle 1 per set, each secondary 0.5
 * (MODEL.md 11.1).
 */
fun setsPerMuscle(data: TrainData, date: LocalDate, exercises: (String) -> Exercise?): Map<String, Double> {
    val start = mondayOf(date)
    val end = start.plusDays(6)
    val out = HashMap<String, Double>()
    for (s in data.sets) {
        if (s.date.isBefore(start) || s.date.isAfter(end)) continue
        val e = exercises(s.exercise) ?: continue
        e.primary.forEach { out[it] = (out[it] ?: 0.0) + 1.0 }
        e.secondary.forEach { out[it] = (out[it] ?: 0.0) + 0.5 }
    }
    return out
}

fun trainPicture(
    data: TrainData,
    date: LocalDate,
    today: LocalDate,
    debtH: Double?,
    exercises: (String) -> Exercise? = { null },
): TrainPicture {
    val s = data.settings
    val start = s.start
    val eased = (debtH ?: 0.0) > HIGH_DEBT_H
    val weekStart = mondayOf(date)
    val actual = if (start != null) actualMinutes(data, start, today) else emptyMap()
    val raceWeek = if (start != null) s.raceDate?.let { weekIndex(start, it) } else null
    val idx = start?.let { weekIndex(it, date) }
    val week = idx?.let { weekPlan(it, s.currentMinutes, s.peakMinutes, raceWeek, actual) }
    val swimWeek = data.swimOn(weekStart)
    val days = (0L..6L).map { k ->
        val d = weekStart.plusDays(k)
        dayPlan(d, week.takeIf { start != null && !d.isBefore(start) }, data.standDowns[d], eased && d == today, swimWeek)
    }
    val doneOn = data.workouts.groupBy { it.date }
    val inWeek = data.workouts.filter { !it.date.isBefore(weekStart) && !it.date.isAfter(weekStart.plusDays(6)) && it.ended }
    val weekDone = inWeek.filter { it.kind.sport != null }.groupBy { it.kind.sport!! }.mapValues { (_, l) -> l.sumOf { it.minutes } }
    val weekPlanned = week?.let { w -> weekSessions(w, swimWeek).groupBy { it.sport }.mapValues { (_, l) -> l.sumOf { it.minutes } } }.orEmpty()
    val fourWeeks = data.workouts.filter {
        it.kind.sport != null && it.ended && it.rpe != null && !it.date.isBefore(today.minusDays(27)) && !it.date.isAfter(today)
    }
    val planWeeks = if (start != null) {
        val last = maxOf((raceWeek ?: 0) + 1, (idx ?: 0) + 8)
        (0..last).map { weekPlan(it, s.currentMinutes, s.peakMinutes, raceWeek, actual) }
    } else {
        emptyList()
    }
    return TrainPicture(
        date = date,
        today = today,
        setUp = s.setUp,
        week = week,
        weekStart = weekStart,
        raceWeek = raceWeek,
        day = days.first { it.date == date },
        days = days,
        doneOn = doneOn,
        inProgress = data.workouts.lastOrNull { it.kind == WorkoutKind.LIFT && !it.ended && it.date == today },
        weekPlanned = weekPlanned,
        weekDone = weekDone,
        weekLoad = inWeek.sumOf { w -> w.rpe?.let { sessionLoad(w.minutes, it) } ?: 0.0 },
        easyShare4w = easyShare(fourWeeks.map { it.rpe!! }),
        setsPerMuscle = setsPerMuscle(data, date, exercises),
        plan = planWeeks,
        actual = actual,
        swim = data.swim,
        swimThisWeek = swimWeek,
    )
}

/** "Lower A · Swim 35 min easy · Bike 45 min quality". */
fun dayLine(d: DayPlan): String {
    if (d.standDown != null) return "Stand down: ${d.standDown.label.lowercase()}"
    if (d.rest) return "Rest day"
    val parts = mutableListOf<String>()
    d.lift?.let { parts += it.label }
    d.sessions.forEach { parts += "${if (it.brick) "Brick run" else it.sport.label} ${it.minutes.toInt()} min ${it.intensity.name.lowercase()}" }
    return parts.joinToString(" · ")
}

fun phaseLabel(w: PlanWeek?): String = when {
    w == null -> "Not set up"
    w.recovery -> "${w.phase.name.lowercase().replaceFirstChar { it.uppercase() }} · recovery week"
    else -> w.phase.name.lowercase().replaceFirstChar { it.uppercase() }
}
