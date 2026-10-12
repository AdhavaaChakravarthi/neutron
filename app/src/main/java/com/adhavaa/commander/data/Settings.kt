package com.adhavaa.commander.data

import com.adhavaa.commander.model.Activity
import com.adhavaa.commander.model.FuelProfile
import com.adhavaa.commander.orders.DEFAULT_REMINDERS
import com.adhavaa.commander.orders.OrderKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Everything the user has told Commander. Pure Kotlin so reminder logic can be tested on the JVM. */
data class Settings(
    val onboarded: Boolean,
    val bedtime: LocalTime,
    val wake: LocalTime,
    val targetWake: LocalTime?,
    val needMinutes: Int,
    val reminders: Set<OrderKind> = DEFAULT_REMINDERS,
    /** First day of the 7-month campaign; null until set on onboarding or first run. */
    val campaignStart: LocalDate? = null,
    /** Learn sleep need from natural-wake nights; when false, [needMinutes] is used as set. */
    val needAuto: Boolean = true,
    /** Days with no fixed wake time; nights before them are "free" nights for chronotype and social jetlag. */
    val freeDays: Set<DayOfWeek> = DEFAULT_FREE_DAYS,
    val fuel: FuelSettings = FuelSettings(),
) {
    companion object {
        val DEFAULT_BEDTIME: LocalTime = LocalTime.of(23, 30)
        val DEFAULT_WAKE: LocalTime = LocalTime.of(7, 30)
        const val DEFAULT_NEED_MIN = 480
        const val MIN_NEED_MIN = 360
        const val MAX_NEED_MIN = 600
        val DEFAULT_FREE_DAYS: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}

/** Usual nights must be 3-14 h so the model has something sensible to work with. */
fun isReasonableNight(bed: LocalTime, wake: LocalTime): Boolean {
    val minutes = Math.floorMod(minutesOf(wake) - minutesOf(bed), 24 * 60)
    return minutes in 180..840
}

internal fun minutesOf(t: LocalTime): Int = t.hour * 60 + t.minute

/**
 * Fuel setup (MODEL.md section 10). Profile fields stay null until setup is done. The weekly practices default to
 * yours: fruit on Tuesday, vegan on Saturday, fish but no meat (D-58).
 */
data class FuelSettings(
    val male: Boolean? = null,
    val birthYear: Int? = null,
    val heightCm: Double? = null,
    val startWeightKg: Double? = null,
    val start: LocalDate? = null,
    val activity: Activity = Activity.ACTIVE,
    /** Goal, fraction of body weight per week; negative = lose. */
    val rate: Double = DEFAULT_RATE,
    val checkInDay: DayOfWeek = DayOfWeek.MONDAY,
    val fruitDays: Set<DayOfWeek> = setOf(DayOfWeek.TUESDAY),
    val veganDays: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY),
    val fishOnly: Boolean = true,
) {
    val setUp: Boolean get() = male != null && birthYear != null && heightCm != null && startWeightKg != null && start != null

    /** Age is whole years from the birth year (MODEL.md section 10). */
    fun profile(today: LocalDate): FuelProfile? = if (!setUp) null else FuelProfile(
        male = male!!,
        ageY = (today.year - birthYear!!).toDouble(),
        heightCm = heightCm!!,
        startWeightKg = startWeightKg!!,
        activity = activity,
        rate = rate,
        start = start!!,
        checkInDay = checkInDay,
    )

    companion object {
        const val DEFAULT_RATE = -0.005
        /** Goal choices (D-61), fraction of body weight per week. */
        val RATES = listOf(-0.01, -0.005, -0.0025, 0.0, 0.0025, 0.005)
    }
}

