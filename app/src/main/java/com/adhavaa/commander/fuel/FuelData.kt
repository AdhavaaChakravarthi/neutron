package com.adhavaa.commander.fuel

import com.adhavaa.commander.data.FuelSettings
import com.adhavaa.commander.model.CheckIn
import com.adhavaa.commander.model.WeighIn
import com.adhavaa.commander.model.fuelCheckIns
import com.adhavaa.commander.model.losingTooFast
import com.adhavaa.commander.model.targetsOn
import com.adhavaa.commander.model.trendSeries
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.TreeMap

enum class Meal(val label: String) { BREAKFAST("Breakfast"), LUNCH("Lunch"), DINNER("Dinner"), SNACKS("Snacks") }

/** One logged food. Nutrients are stored with the entry, so editing a food later doesn't rewrite the past. */
data class LogEntry(
    val id: Long,
    val date: LocalDate,
    val meal: Meal,
    val ref: String,
    val name: String,
    /** Null for a quick add. */
    val grams: Double?,
    val amount: String,
    val n: Nutrients,
)

/** Your weekly practices (D-58). */
enum class DayType(val label: String) { NORMAL("Normal day"), FRUIT("Fruit day"), VEGAN("Vegan day") }

fun dayType(s: FuelSettings, date: LocalDate): DayType = when (date.dayOfWeek) {
    in s.fruitDays -> DayType.FRUIT
    in s.veganDays -> DayType.VEGAN
    else -> DayType.NORMAL
}

/** The search filter a day starts with. You can change it on the search screen. */
fun defaultFilter(s: FuelSettings, date: LocalDate): SearchFilter = when (dayType(s, date)) {
    DayType.FRUIT -> SearchFilter(fishOnly = s.fishOnly, fruitOnly = true)
    DayType.VEGAN -> SearchFilter(fishOnly = s.fishOnly, vegan = true)
    DayType.NORMAL -> SearchFilter(fishOnly = s.fishOnly)
}

/** Everything the fuel numbers come from. Pure, so Today, Fuel and the tests agree. */
data class FuelData(
    val settings: FuelSettings,
    val weighIns: List<WeighIn>,
    /** kcal per day that has at least one entry. */
    val dailyKcal: Map<LocalDate, Double>,
    /** Days you marked "not fully logged". */
    val incomplete: Set<LocalDate>,
) {
    /** Fully logged days only (D-60): days with entries that you didn't mark incomplete. */
    fun intake(): Map<LocalDate, Double> = dailyKcal.filterKeys { it !in incomplete }

    fun trend(): TreeMap<LocalDate, Double> = trendSeries(weighIns)

    fun checkIns(through: LocalDate): List<CheckIn> =
        settings.profile(through)?.let { fuelCheckIns(it, weighIns, intake(), through) } ?: emptyList()
}

/** Calorie and macro targets for one day; none on a fruit day (D-58). */
data class DayTargets(val kcal: Double, val proteinG: Double, val fatG: Double, val carbG: Double)

/** What the Fuel tab and Today show for one day. */
data class FuelPicture(
    val date: LocalDate,
    val today: LocalDate,
    val setUp: Boolean,
    val dayType: DayType,
    val checkIns: List<CheckIn>,
    /** The check-in in force on [date] (for a future date, the latest so far). */
    val current: CheckIn?,
    val targets: DayTargets?,
    val trendKg: Double?,
    val weightOnDate: Double?,
    val losingTooFast: Boolean,
    val nextCheckIn: LocalDate?,
    val incomplete: Boolean,
) {
    /** A check-in happened today (not counting the setup day). */
    val checkInToday: Boolean get() = current?.date == today && checkIns.size > 1
}

fun fuelPicture(data: FuelData, date: LocalDate, today: LocalDate): FuelPicture {
    val s = data.settings
    val through = if (date.isAfter(today)) today else date
    val cis = data.checkIns(through)
    val current = targetsOn(cis, through)
    val type = dayType(s, date)
    val trend = data.trend()
    val targets = current?.takeIf { type != DayType.FRUIT }?.let { DayTargets(it.kcal, it.proteinG, it.fatG, it.carbG) }
    val next = if (s.setUp) today.with(TemporalAdjusters.next(s.checkInDay)) else null
    return FuelPicture(
        date = date,
        today = today,
        setUp = s.setUp,
        dayType = type,
        checkIns = cis,
        current = current,
        targets = targets,
        trendKg = trend.floorEntry(through)?.value,
        weightOnDate = data.weighIns.filter { it.date == date }.takeIf { it.isNotEmpty() }?.let { l -> l.sumOf { it.kg } / l.size },
        losingTooFast = s.setUp && s.rate < 0 && losingTooFast(current),
        nextCheckIn = next,
        incomplete = date in data.incomplete,
    )
}

fun totals(entries: List<LogEntry>): Nutrients = entries.fold(Nutrients.ZERO) { acc, e -> acc + e.n }

/** Trend change over the last [days] days, kg (negative = down), or null without enough trend. */
fun trendChange(trend: TreeMap<LocalDate, Double>, today: LocalDate, days: Long = 7): Double? {
    val now = trend.floorEntry(today) ?: return null
    val then = trend.floorEntry(today.minusDays(days)) ?: return null
    if (ChronoUnit.DAYS.between(then.key, now.key) < days / 2) return null
    return now.value - then.value
}

/** Weekly goal as words: "Lose 0.5% a week". */
fun goalLabel(rate: Double): String = when {
    rate < 0 -> "Lose ${pct(-rate)} a week"
    rate > 0 -> "Gain ${pct(rate)} a week"
    else -> "Maintain"
}

fun pct(fraction: Double): String {
    val p = fraction * 100
    return (if (p == Math.round(p).toDouble()) Math.round(p).toString() else String.format(java.util.Locale.US, "%.2f", p).trimEnd('0').trimEnd('.')) + "%"
}
