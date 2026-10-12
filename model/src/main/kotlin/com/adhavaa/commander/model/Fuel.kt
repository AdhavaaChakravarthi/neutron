package com.adhavaa.commander.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.TreeMap

/*
 * Fuel: resting energy, trend weight, adaptive expenditure and daily targets (MODEL.md section 10).
 * Mirrors section 7 of tools/model-reference/commander_model.py; golden vectors in golden/fuel_*.csv.
 */

object FuelParams {
    /** ~3500 kcal per lb = 32.2 MJ/kg, the conventional rule examined by Hall 2008. */
    const val KCAL_PER_KG = 7700.0
    /** Hacker's Diet: exponentially smoothed moving average with 10% smoothing. */
    const val TREND_ALPHA = 0.1
    /** ASSUMPTION: expenditure is never estimated below resting energy. */
    const val E_MIN_FACTOR = 1.0
    /** ASSUMPTION: FAO/WHO/UNU 2001 say PAL above 2.40 is difficult to maintain; widened for error. */
    const val E_MAX_FACTOR = 2.5
    /** ASSUMPTIONS: the expenditure window and how much data it needs. */
    const val WINDOW_DAYS = 21
    const val MIN_LOGGED_DAYS = 14
    const val MIN_WEIGHED_DAYS = 8
    const val MIN_WEIGH_SPAN = 14
    /** ASSUMPTION: each check-in moves the estimate halfway to what the data imply. */
    const val BLEND = 0.5
    /** ASSUMPTION: the kcal target falls at most this much per check-in (rule of command 3). */
    const val MAX_STEP_KCAL = 200.0
    /** Morton et al. 2018: no further gain in fat-free mass beyond 1.62 g/kg/day. */
    const val PROTEIN_G_PER_KG = 1.6
    /** ASSUMPTION inside the IOM 2005 acceptable range of 20-35% of energy from fat. */
    const val FAT_SHARE = 0.25
    /** Helms et al. 2014: lose 0.5-1% of body weight per week. */
    const val MAX_LOSS_RATE = 0.01
    /** ASSUMPTION. */
    const val MAX_GAIN_RATE = 0.005
}

/** Physical activity level, as the midpoint of an FAO/WHO/UNU 2001 band (ASSUMPTION: midpoints). */
enum class Activity(val pal: Double, val label: String, val band: String) {
    LIGHT(1.55, "Sedentary or light", "1.40–1.69"),
    ACTIVE(1.85, "Active or moderately active", "1.70–1.99"),
    VIGOROUS(2.20, "Vigorous", "2.00–2.40"),
}

data class FuelProfile(
    val male: Boolean,
    val ageY: Double,
    val heightCm: Double,
    val startWeightKg: Double,
    val activity: Activity,
    /** Goal as a fraction of body weight per week; negative = lose. */
    val rate: Double,
    val start: LocalDate,
    val checkInDay: DayOfWeek = DayOfWeek.MONDAY,
)

data class WeighIn(val date: LocalDate, val kg: Double)

data class CheckIn(
    val date: LocalDate,
    val trendKg: Double,
    val restKcal: Double,
    val expenditure: Double,
    /** Expenditure implied by the last WINDOW_DAYS, when there was enough data. */
    val observed: Double?,
    /** Observed weight change, as a fraction of body weight per week. */
    val weeklyRate: Double?,
    val kcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbG: Double,
    val floorApplied: Boolean,
    val stepLimited: Boolean,
)

/** Mifflin-St Jeor 1990: 10 W + 6.25 H − 5 A + 5 (men) or − 161 (women), kcal/day. */
fun restingEnergy(weightKg: Double, heightCm: Double, ageY: Double, male: Boolean): Double =
    10.0 * weightKg + 6.25 * heightCm - 5.0 * ageY + if (male) 5.0 else -161.0

/** Mean weigh-in per day. */
fun dailyWeights(weighIns: List<WeighIn>): TreeMap<LocalDate, Double> {
    val acc = TreeMap<LocalDate, MutableList<Double>>()
    for (w in weighIns) acc.getOrPut(w.date) { ArrayList() } += w.kg
    return acc.mapValuesTo(TreeMap()) { (_, v) -> v.sum() / v.size }
}

/**
 * Trend weight for every day from the first weigh-in to the last. Gaps are straight lines between weigh-ins
 * (ASSUMPTION), then T(d) = T(d−1) + 0.1·(w(d) − T(d−1)), starting at the first weigh-in.
 */
fun trendSeries(weighIns: List<WeighIn>): TreeMap<LocalDate, Double> {
    val w = dailyWeights(weighIns)
    val out = TreeMap<LocalDate, Double>()
    if (w.isEmpty()) return out
    val days = w.keys.toList()
    val filled = HashMap<LocalDate, Double>()
    filled[days.last()] = w.getValue(days.last())
    for (i in 0 until days.size - 1) {
        val a = days[i]
        val b = days[i + 1]
        val span = (b.toEpochDay() - a.toEpochDay()).toDouble()
        var d = a
        while (d < b) {
            filled[d] = w.getValue(a) + (w.getValue(b) - w.getValue(a)) * (d.toEpochDay() - a.toEpochDay()) / span
            d = d.plusDays(1)
        }
    }
    var t: Double? = null
    var d = days.first()
    while (d <= days.last()) {
        val x = filled.getValue(d)
        t = if (t == null) x else t + FuelParams.TREND_ALPHA * (x - t)
        out[d] = t
        d = d.plusDays(1)
    }
    return out
}

/** Trend on the latest day at or before [date]; it holds flat after the last weigh-in. */
fun trendAt(trend: TreeMap<LocalDate, Double>, date: LocalDate): Double? = trend.floorEntry(date)?.value

/**
 * Least-squares slope (kg/day) of the daily weigh-ins from [first] to [last] inclusive, or null when there are
 * fewer than MIN_WEIGHED_DAYS of them or they span less than MIN_WEIGH_SPAN days.
 */
fun weightSlope(weights: TreeMap<LocalDate, Double>, first: LocalDate, last: LocalDate): Double? {
    val pts = weights.subMap(first, true, last, true).entries.map { it.key.toEpochDay().toDouble() to it.value }
    if (pts.size < FuelParams.MIN_WEIGHED_DAYS || pts.last().first - pts.first().first < FuelParams.MIN_WEIGH_SPAN) return null
    val mx = pts.sumOf { it.first } / pts.size
    val my = pts.sumOf { it.second } / pts.size
    val sxx = pts.sumOf { (it.first - mx) * (it.first - mx) }
    return pts.sumOf { (it.first - mx) * (it.second - my) } / sxx
}

fun clampRate(rate: Double): Double = rate.coerceIn(-FuelParams.MAX_LOSS_RATE, FuelParams.MAX_GAIN_RATE)

/** Protein, fat and carbohydrate grams for a kcal target. */
fun macros(kcal: Double, weightKg: Double): Triple<Double, Double, Double> {
    val protein = FuelParams.PROTEIN_G_PER_KG * weightKg
    val fat = FuelParams.FAT_SHARE * kcal / 9.0
    val carb = maxOf(0.0, kcal - 4.0 * protein - 9.0 * fat) / 4.0
    return Triple(protein, fat, carb)
}

/**
 * Targets at the start day and at every check-in weekday after it, up to [through].
 * [intake] holds the kcal eaten on each fully logged day; other days are absent.
 */
fun fuelCheckIns(profile: FuelProfile, weighIns: List<WeighIn>, intake: Map<LocalDate, Double>, through: LocalDate): List<CheckIn> {
    val weights = dailyWeights(weighIns)
    val trend = trendSeries(weighIns)
    val rate = clampRate(profile.rate)
    val out = ArrayList<CheckIn>()
    var c = profile.start
    while (c <= through) {
        if (c == profile.start || c.dayOfWeek == profile.checkInDay) {
            val w = trendAt(trend, c) ?: profile.startWeightKg
            val ree = restingEnergy(w, profile.heightCm, profile.ageY, profile.male)
            val first = c.minusDays(FuelParams.WINDOW_DAYS.toLong())
            val last = c.minusDays(1)
            val slope = weightSlope(weights, first, last)
            val weekly = slope?.let { it * 7.0 / w }
            var observed: Double? = null
            var e: Double
            if (out.isEmpty()) {
                e = ree * profile.activity.pal
            } else {
                e = out.last().expenditure
                val logged = ArrayList<Double>()
                var d = first
                while (d <= last) {
                    intake[d]?.let { logged += it }
                    d = d.plusDays(1)
                }
                if (slope != null && logged.size >= FuelParams.MIN_LOGGED_DAYS) {
                    observed = logged.sum() / logged.size - FuelParams.KCAL_PER_KG * slope
                    e += FuelParams.BLEND * (observed - e)
                }
            }
            e = e.coerceIn(FuelParams.E_MIN_FACTOR * ree, FuelParams.E_MAX_FACTOR * ree)
            var kcal = e + rate * w * FuelParams.KCAL_PER_KG / 7.0
            var limited = false
            if (out.isNotEmpty() && kcal < out.last().kcal - FuelParams.MAX_STEP_KCAL) {
                kcal = out.last().kcal - FuelParams.MAX_STEP_KCAL
                limited = true
            }
            val floor = kcal < ree
            kcal = maxOf(kcal, ree)
            val (p, f, cb) = macros(kcal, w)
            out += CheckIn(c, w, ree, e, observed, weekly, kcal, p, f, cb, floor, limited)
        }
        c = c.plusDays(1)
    }
    return out
}

/** The check-in in force on [date]: the latest one on or before it. */
fun targetsOn(checkIns: List<CheckIn>, date: LocalDate): CheckIn? = checkIns.lastOrNull { it.date <= date }

/** Helms et al. 2014 upper bound: losing faster than 1% of body weight a week triggers an "eat to target" order. */
fun losingTooFast(ci: CheckIn?): Boolean = ci?.weeklyRate?.let { it < -FuelParams.MAX_LOSS_RATE } ?: false
