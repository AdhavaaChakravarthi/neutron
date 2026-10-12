package com.adhavaa.commander.model

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/*
 * Train: estimated 1RM, double progression, the endurance plan and session load (MODEL.md section 11).
 * Mirrors section 8 of tools/model-reference/commander_model.py; golden vectors in golden/train_*.csv.
 */

object TrainParams {
    /** ASSUMPTION: no 1RM estimate above 12 reps. */
    const val E1RM_MAX_REPS = 12
    /** ASSUMPTIONS inside ACSM 2009's 2-10% load increase. */
    const val UP_SMALL = 0.025
    const val UP_LARGE = 0.05
    const val DOWN = 0.10
    /** ASSUMPTIONS: weekly growth, every 4th week at 70%, the cap on jumps and the restart floor. */
    const val PLAN_GROWTH = 1.08
    const val RECOVERY_FACTOR = 0.7
    const val CAP_FACTOR = 1.1
    const val RESTART_FLOOR = 0.5
    /** Week before the race, race week. Bosquet 2007: cut volume 41-60% over a two-week taper. */
    const val TAPER_1 = 0.55
    const val TAPER_0 = 0.45
    /** ASSUMPTION. */
    const val MIN_SESSION = 15.0
    /** ASSUMPTION: a session rated 4 or below (CR-10) counts as easy. */
    const val EASY_RPE = 4
    /** ACSM 2009 rest: at least 2-3 min for core lifts, 1-2 min for assistance (midpoints, ASSUMPTION). */
    const val REST_MAIN_S = 150
    const val REST_ACCESSORY_S = 90
}

/** Epley: w (1 + r/30) for r >= 2; a single is its own 1RM. Null above 12 reps or for no reps. */
fun e1rm(weight: Double, reps: Int): Double? = when {
    reps <= 0 || reps > TrainParams.E1RM_MAX_REPS -> null
    reps == 1 -> weight
    else -> weight * (1.0 + reps / 30.0)
}

/** Smallest load step per equipment type, kg. ASSUMPTION. Bodyweight progresses by reps. */
enum class Equipment(val step: Double) { BARBELL(2.5), DUMBBELL(2.0), CABLE(2.5), MACHINE(2.5), BODY(0.0) }

data class Prescription(val sets: Int, val lo: Int, val hi: Int, val large: Boolean, val equipment: Equipment)

/** One working set. */
data class LiftSet(val weight: Double, val reps: Int)

enum class LoadAction { START, UP, DOWN, HOLD }

data class NextLoad(val action: LoadAction, val weight: Double?)

private fun sameWeight(session: List<LiftSet>): Double? =
    if (session.isNotEmpty() && session.all { it.weight == session[0].weight }) session[0].weight else null

/**
 * Double progression (ACSM 2009). [history] is the working sets of each past session of this exercise, oldest
 * first. Up after two sessions at the same weight with every set at target+1 or more; down 10% after two sessions
 * with most sets below the range; otherwise hold.
 */
fun nextLoad(p: Prescription, history: List<List<LiftSet>>): NextLoad {
    if (history.isEmpty() || history.last().isEmpty()) return NextLoad(LoadAction.START, null)
    val w = history.last()[0].weight
    val step = p.equipment.step
    if (history.size >= 2) {
        val a = history[history.size - 2]
        val b = history.last()
        val wa = sameWeight(a)
        val wb = sameWeight(b)
        if (wa != null && wa == wb && a.size >= p.sets && b.size >= p.sets) {
            if (a.all { it.reps >= p.hi + 1 } && b.all { it.reps >= p.hi + 1 }) {
                if (step == 0.0) return NextLoad(LoadAction.HOLD, w)
                val target = w * (1.0 + if (p.large) TrainParams.UP_LARGE else TrainParams.UP_SMALL)
                val up = ceil(target / step - 1e-9) * step
                return NextLoad(LoadAction.UP, max(up, w + step))
            }
            if (listOf(a, b).all { s -> s.count { it.reps < p.lo } * 2 > s.size }) {
                if (step == 0.0) return NextLoad(LoadAction.HOLD, w)
                val down = floor(w * (1.0 - TrainParams.DOWN) / step + 1e-9) * step
                return NextLoad(LoadAction.DOWN, max(down, 0.0))
            }
        }
    }
    return NextLoad(LoadAction.HOLD, w)
}

enum class Phase { BASE, BUILD, TAPER, AFTER }

data class PlanWeek(val index: Int, val minutes: Double, val phase: Phase, val recovery: Boolean, val capped: Boolean)

/** Ramp value for week [i]: grows on non-recovery weeks only, up to [vp]. */
fun planBase(i: Int, v0: Double, vp: Double): Double {
    val n = i - Math.floorDiv(i + 1, 4)
    return min(vp, v0 * TrainParams.PLAN_GROWTH.pow(max(n, 0)))
}

/**
 * Planned swim + bike + run minutes for week [i] (0 = the setup week). [raceWeek] is the race week's index or null.
 * [actual] holds the minutes done in completed weeks, by index (may include weeks before setup).
 */
fun weekPlan(i: Int, v0: Double, vp: Double, raceWeek: Int?, actual: Map<Int, Double>): PlanWeek {
    var recovery = false
    var m: Double
    val phase: Phase
    if (raceWeek != null && i > raceWeek) {
        m = min(v0, 0.5 * planBase(max(raceWeek - 2, 0), v0, vp))
        phase = Phase.AFTER
    } else if (raceWeek != null && i >= raceWeek - 1) {
        val pre = planBase(max(raceWeek - 2, 0), v0, vp)
        m = pre * if (i == raceWeek - 1) TrainParams.TAPER_1 else TrainParams.TAPER_0
        phase = Phase.TAPER
    } else {
        recovery = Math.floorMod(i, 4) == 3
        m = planBase(i, v0, vp) * if (recovery) TrainParams.RECOVERY_FACTOR else 1.0
        phase = if (raceWeek == null || raceWeek - i >= 13) Phase.BASE else Phase.BUILD
    }
    val prior = listOf(i - 1, i - 2, i - 3).mapNotNull { actual[it] }
    var capped = false
    if (prior.isNotEmpty()) {
        val cap = max(TrainParams.CAP_FACTOR * prior.max(), TrainParams.RESTART_FLOOR * v0)
        if (m > cap) {
            m = cap
            capped = true
        }
    }
    return PlanWeek(i, m, phase, recovery, capped)
}

enum class Sport(val label: String) { SWIM("Swim"), BIKE("Bike"), RUN("Run") }

enum class Intensity { EASY, QUALITY }

/** A slot in the week (MODEL.md 11.3). [day] is ISO (Monday = 1). */
data class Slot(val day: Int, val sport: Sport, val weight: Double, val base: Intensity, val build: Intensity, val buildOnly: Boolean = false)

val WEEK_SLOTS: List<Slot> = listOf(
    Slot(1, Sport.SWIM, 1.0, Intensity.EASY, Intensity.EASY),
    Slot(2, Sport.RUN, 0.8, Intensity.EASY, Intensity.EASY),
    Slot(4, Sport.BIKE, 1.2, Intensity.QUALITY, Intensity.QUALITY),
    Slot(5, Sport.SWIM, 1.0, Intensity.EASY, Intensity.QUALITY),
    Slot(6, Sport.BIKE, 2.6, Intensity.EASY, Intensity.EASY),
    Slot(6, Sport.RUN, 0.4, Intensity.EASY, Intensity.EASY, buildOnly = true),
    Slot(7, Sport.RUN, 1.8, Intensity.EASY, Intensity.EASY),
)

data class PlannedSession(val day: Int, val sport: Sport, val minutes: Double, val intensity: Intensity, val brick: Boolean = false)

fun round5(x: Double): Double = floor(x / 5.0 + 0.5) * 5.0

/** The week's planned sessions. */
fun weekSessions(week: PlanWeek): List<PlannedSession> {
    if (week.minutes <= 0) return emptyList()
    val build = week.phase == Phase.BUILD || week.phase == Phase.TAPER
    val slots = WEEK_SLOTS.filter { build || !it.buildOnly }
    val total = slots.sumOf { it.weight }
    return slots.map { s ->
        val intensity = when {
            week.recovery || week.phase == Phase.AFTER -> Intensity.EASY
            build -> s.build
            else -> s.base
        }
        PlannedSession(s.day, s.sport, max(TrainParams.MIN_SESSION, round5(week.minutes * s.weight / total)), intensity, s.buildOnly)
    }
}

/** Foster 2001: session RPE (0-10) times minutes. */
fun sessionLoad(minutes: Double, rpe: Double): Double = minutes * rpe

/** Share of sessions rated easy, or null with none. */
fun easyShare(rpes: List<Double>): Double? =
    if (rpes.isEmpty()) null else rpes.count { it <= TrainParams.EASY_RPE }.toDouble() / rpes.size
