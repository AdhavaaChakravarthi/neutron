package com.adhavaa.commander.stats

import com.adhavaa.commander.fuel.DayType
import com.adhavaa.commander.fuel.FuelData
import com.adhavaa.commander.fuel.dayType
import com.adhavaa.commander.model.Hexagon
import com.adhavaa.commander.model.SleepKind
import com.adhavaa.commander.model.SleepRecord
import com.adhavaa.commander.model.StatDay
import com.adhavaa.commander.model.StatsConstants
import com.adhavaa.commander.model.hexagon
import com.adhavaa.commander.model.regularityOver
import com.adhavaa.commander.model.targetsOn
import com.adhavaa.commander.train.TrainData
import com.adhavaa.commander.train.WorkoutKind
import com.adhavaa.commander.train.dayPlans
import java.time.Duration
import java.time.LocalDate

/** The hexagon for the window ending [end] and for the window a week earlier (MODEL.md section 12). */
data class StatsPicture(val end: LocalDate, val now: Hexagon, val weekAgo: Hexagon, val needH: Double) {
    val start: LocalDate get() = end.minusDays(StatsConstants.WINDOW - 1L)
}

/**
 * Total sleep per wake day: nights plus naps that end that day. A "didn't sleep" mark counts as 0; days with neither a
 * night nor a mark are left out (MODEL.md section 9).
 */
fun sleepByWakeDay(records: List<SleepRecord>, skipped: Set<LocalDate>): Map<LocalDate, Double> {
    val days = records.filter { it.kind == SleepKind.NIGHT }.map { it.end.toLocalDate() }.toSet() + skipped
    val out = HashMap<LocalDate, Double>()
    for (d in days) out[d] = 0.0
    for (r in records) {
        val d = r.end.toLocalDate()
        if (d in out && r.end.isAfter(r.start)) out[d] = out.getValue(d) + Duration.between(r.start, r.end).seconds / 3600.0
    }
    return out
}

/** The 21 days ending [end], oldest first, each with the night after it. [today] is the plan day. */
fun statDays(
    end: LocalDate,
    today: LocalDate,
    sleep: Map<LocalDate, Double>,
    fuel: FuelData?,
    train: TrainData?,
): List<StatDay> {
    val start = end.minusDays(StatsConstants.WINDOW - 1L)
    val checkIns = fuel?.checkIns(end).orEmpty()
    val intake = fuel?.intake().orEmpty()
    val plans = train?.let { t -> dayPlans(t, start, end, today).associateBy { it.date } }.orEmpty()
    val setsOf = train?.sets.orEmpty().groupBy { it.workoutId }
    val workouts = train?.workouts.orEmpty().groupBy { it.date }
    return (0 until StatsConstants.WINDOW).map { k ->
        val d = start.plusDays(k.toLong())
        val target = if (fuel == null || dayType(fuel.settings, d) == DayType.FRUIT) null else targetsOn(checkIns, d)?.kcal
        val plan = plans[d]
        val on = plan != null && plan.standDown == null
        val done = workouts[d].orEmpty()
        StatDay(
            sleepH = sleep[d.plusDays(1)],
            kcalTarget = target,
            kcal = if (target == null) null else intake[d],
            train = on,
            liftPlanned = on && plan?.lift != null,
            lifts = if (on) done.count { it.kind == WorkoutKind.LIFT && (it.ended || setsOf[it.id].orEmpty().isNotEmpty()) } else 0,
            endurancePlanned = if (on) plan!!.sessions.sumOf { it.minutes } else 0.0,
            enduranceDone = if (on) done.filter { it.kind.sport != null }.sumOf { it.minutes } else 0.0,
            mobility = on && done.any { it.kind == WorkoutKind.MOBILITY },
        )
    }
}

/** Today's hexagon: the 21 days to yesterday, and the same a week earlier, for the plan day [today]. */
fun statsPicture(
    today: LocalDate,
    records: List<SleepRecord>,
    skipped: Set<LocalDate>,
    needH: Double,
    fuel: FuelData?,
    train: TrainData?,
): StatsPicture {
    val sleep = sleepByWakeDay(records, skipped)
    fun at(end: LocalDate): Hexagon = hexagon(
        statDays(end, today, sleep, fuel, train),
        needH,
        regularityOver(records, end.plusDays(1), StatsConstants.WINDOW, StatsConstants.MIN_DAYS),
    )
    val end = today.minusDays(1)
    return StatsPicture(end, at(end), at(end.minusDays(7)), needH)
}
