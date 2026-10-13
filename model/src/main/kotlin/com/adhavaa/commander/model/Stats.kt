package com.adhavaa.commander.model

/**
 * The stats hexagon (MODEL.md section 12): six corners scoring how fully orders were followed over the last 21 days.
 * Line-for-line port of section 9 of tools/model-reference/commander_model.py; golden tests hold them together.
 */
object StatsConstants {
    /** The same 21 days as the Fuel window. ASSUMPTION. */
    const val WINDOW = 21

    /** Counted days (night pairs for rhythm) before a corner shows a number. ASSUMPTION. */
    const val MIN_DAYS = 7

    /** A fully logged day within 10% of the calorie target scores 1. ASSUMPTION. */
    const val KCAL_BAND = 0.10

    /** A fully logged day outside the band scores 0.5. ASSUMPTION. */
    const val LOGGED_CREDIT = 0.5
}

enum class Stat(val label: String) {
    SLEEP("Sleep"), RHYTHM("Rhythm"), FUEL("Fuel"), STRENGTH("Strength"), ENDURANCE("Endurance"), MOBILITY("Mobility")
}

/**
 * One day of the window with the night after it. [sleepH]: sleep that night (null = no data). [kcalTarget]: the day's
 * calorie target (null = none). [kcal]: eaten if fully logged (null = not fully logged). [train]: Train set up and
 * not a stand-down day.
 */
data class StatDay(
    val sleepH: Double? = null,
    val kcalTarget: Double? = null,
    val kcal: Double? = null,
    val train: Boolean = false,
    val liftPlanned: Boolean = false,
    val lifts: Int = 0,
    val endurancePlanned: Double = 0.0,
    val enduranceDone: Double = 0.0,
    val mobility: Boolean = false,
)

/** Scores 0-100 (null until there's enough data), counted days per corner (rhythm: nights with data), and overall. */
data class Hexagon(val scores: Map<Stat, Double?>, val counted: Map<Stat, Int>, val overall: Double?)

fun fuelDayScore(target: Double, kcal: Double?): Double = when {
    kcal == null -> 0.0
    kotlin.math.abs(kcal - target) <= StatsConstants.KCAL_BAND * target -> 1.0
    else -> StatsConstants.LOGGED_CREDIT
}

fun hexagon(days: List<StatDay>, needH: Double, sri: Double?): Hexagon {
    val slept = days.mapNotNull { it.sleepH }
    val fuel = days.filter { it.kcalTarget != null }
    val train = days.filter { it.train }
    val liftPlan = train.count { it.liftPlanned }
    val endPlan = train.sumOf { it.endurancePlanned }

    fun enough(n: Int, value: Double?): Double? = if (n < StatsConstants.MIN_DAYS || value == null) null else 100.0 * value

    val scores = linkedMapOf(
        Stat.SLEEP to enough(slept.size, if (slept.isEmpty()) null else slept.sumOf { minOf(1.0, it / needH) } / slept.size),
        Stat.RHYTHM to sri?.coerceIn(0.0, 100.0),
        Stat.FUEL to enough(fuel.size, if (fuel.isEmpty()) null else fuel.sumOf { fuelDayScore(it.kcalTarget!!, it.kcal) } / fuel.size),
        Stat.STRENGTH to enough(train.size, if (liftPlan == 0) null else minOf(1.0, train.sumOf { it.lifts }.toDouble() / liftPlan)),
        Stat.ENDURANCE to enough(train.size, if (endPlan <= 0.0) null else minOf(1.0, train.sumOf { it.enduranceDone } / endPlan)),
        Stat.MOBILITY to enough(train.size, if (train.isEmpty()) null else train.count { it.mobility }.toDouble() / train.size),
    )
    val counted = mapOf(
        Stat.SLEEP to slept.size, Stat.RHYTHM to slept.size, Stat.FUEL to fuel.size,
        Stat.STRENGTH to train.size, Stat.ENDURANCE to train.size, Stat.MOBILITY to train.size,
    )
    val shown = scores.values.filterNotNull()
    return Hexagon(scores, counted, if (shown.isEmpty()) null else shown.sum() / shown.size)
}
