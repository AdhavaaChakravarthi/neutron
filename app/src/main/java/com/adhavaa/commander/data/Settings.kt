package com.adhavaa.commander.data

import com.adhavaa.commander.orders.DEFAULT_REMINDERS
import com.adhavaa.commander.orders.OrderKind
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
) {
    companion object {
        val DEFAULT_BEDTIME: LocalTime = LocalTime.of(23, 30)
        val DEFAULT_WAKE: LocalTime = LocalTime.of(7, 30)
        const val DEFAULT_NEED_MIN = 480
        const val MIN_NEED_MIN = 360
        const val MAX_NEED_MIN = 600
    }
}

/** Usual nights must be 3-14 h so the model has something sensible to work with. */
fun isReasonableNight(bed: LocalTime, wake: LocalTime): Boolean {
    val minutes = Math.floorMod(minutesOf(wake) - minutesOf(bed), 24 * 60)
    return minutes in 180..840
}

internal fun minutesOf(t: LocalTime): Int = t.hour * 60 + t.minute
