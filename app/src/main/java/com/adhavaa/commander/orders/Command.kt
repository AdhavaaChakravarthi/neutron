package com.adhavaa.commander.orders

import com.adhavaa.commander.data.Settings
import com.adhavaa.commander.model.SleepPicture
import com.adhavaa.commander.model.SleepRecord
import com.adhavaa.commander.model.UsualSchedule
import com.adhavaa.commander.model.detectRestPeriod
import com.adhavaa.commander.model.habitualSchedule
import com.adhavaa.commander.model.planDateFor
import com.adhavaa.commander.model.sleepPicture
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Everything the orders are computed from. Pure, so the scheduler, the receiver and the UI agree. */
data class CommandData(val settings: Settings, val sleep: List<SleepRecord>, val skipped: Set<LocalDate>) {
    val usual: UsualSchedule get() = UsualSchedule(settings.bedtime, settings.wake)

    fun habitual(now: LocalDateTime): UsualSchedule = habitualSchedule(sleep, usual, now)

    fun planDate(now: LocalDateTime): LocalDate = planDateFor(now, habitual(now))

    fun picture(date: LocalDate): SleepPicture = sleepPicture(
        date, sleep, skipped, usual,
        needOverrideH = if (settings.needAuto) null else settings.needMinutes / 60.0,
        targetWake = settings.targetWake,
        freeDays = settings.freeDays,
    )

    fun nightRecorded(wakeDate: LocalDate): Boolean =
        wakeDate in skipped || sleep.any { it.kind == com.adhavaa.commander.model.SleepKind.NIGHT && it.end.toLocalDate() == wakeDate }
}

/** The morning report comes an hour after your usual wake time. ASSUMPTION (D-45). */
const val CHECK_IN_AFTER_WAKE_MIN = 60L

fun checkInTime(data: CommandData, wakeDate: LocalDate, now: LocalDateTime): LocalDateTime =
    wakeDate.atTime(data.habitual(now).wake).plusMinutes(CHECK_IN_AFTER_WAKE_MIN)

/** Show the morning report on Today when last night is missing and you've plausibly been up a while. */
fun needsMorningReport(data: CommandData, now: LocalDateTime): Boolean {
    val date = data.planDate(now)
    if (data.nightRecorded(date)) return false
    return !now.isBefore(date.atTime(data.habitual(now).wake).minusHours(1))
}

// --- Phone-only detection (MODEL.md section 6) --------------------------------------------------

data class DetectionWindow(val start: LocalDateTime, val end: LocalDateTime)

data class DetectedNight(val start: LocalDateTime, val end: LocalDateTime)

const val BIN_MIN = 5L

/**
 * 18:00 the evening before to 15:00 on the wake day, moved later for late sleepers by however far habitual
 * mid-sleep is after 03:00 (up to 6 h). D-14.
 */
fun detectionWindow(wakeDate: LocalDate, habitual: UsualSchedule): DetectionWindow {
    val bed = habitual.bedtime.toSecondOfDay() / 3600.0
    val dur = habitual.durationH
    val midAfterNoon = ((bed + dur / 2.0) - 12.0).mod(24.0)
    val shiftMin = ((midAfterNoon - 15.0).coerceIn(0.0, 6.0) * 60).toLong()
    return DetectionWindow(
        wakeDate.minusDays(1).atTime(18, 0).plusMinutes(shiftMin),
        wakeDate.atTime(15, 0).plusMinutes(shiftMin),
    )
}

fun binCounts(window: DetectionWindow, end: LocalDateTime, screenOn: List<LocalDateTime>): IntArray {
    val bins = (ChronoUnit.MINUTES.between(window.start, end) / BIN_MIN).toInt().coerceAtLeast(0)
    val counts = IntArray(bins)
    for (t in screenOn) {
        if (t.isBefore(window.start) || !t.isBefore(end)) continue
        val i = (ChronoUnit.MINUTES.between(window.start, t) / BIN_MIN).toInt()
        if (i in counts.indices) counts[i]++
    }
    return counts
}

/** The night's rest period from screen-on times, or null if there isn't a clear one or it hasn't ended yet. */
fun detectNight(window: DetectionWindow, screenOn: List<LocalDateTime>, now: LocalDateTime): DetectedNight? {
    val end = if (now.isBefore(window.end)) now else window.end
    val counts = binCounts(window, end, screenOn)
    if (counts.size < 48) return null
    val range = detectRestPeriod(counts, BIN_MIN / 60.0, 3.0) ?: return null
    val start = window.start.plusMinutes(range.first * BIN_MIN)
    val stop = window.start.plusMinutes((range.last + 1) * BIN_MIN)
    if (!stop.isBefore(end.minusMinutes(15))) return null
    return DetectedNight(start, stop)
}

/** A night from a wake date and two clock times; a bedtime from noon on belongs to the evening before. */
fun nightFrom(wakeDate: LocalDate, bed: LocalTime, wake: LocalTime): Pair<LocalDateTime, LocalDateTime> {
    val start = if (bed.hour >= 12) wakeDate.minusDays(1).atTime(bed) else wakeDate.atTime(bed)
    val end = wakeDate.atTime(wake)
    return start to if (end.isAfter(start)) end else end.plusDays(1)
}

fun hoursBetween(a: LocalDateTime, b: LocalDateTime): Double = Duration.between(a, b).seconds / 3600.0

/** Orders ticked off today out of those that can be ticked. Informational only: no streaks (rule 7). */
fun doneCount(list: List<Order>, done: Set<OrderKind>): Pair<Int, Int> {
    val tickable = list.filter { it.kind in TICKABLE }
    return tickable.count { it.kind in done } to tickable.size
}

val TICKABLE: Set<OrderKind> = setOf(
    OrderKind.WAKE, OrderKind.CAFFEINE, OrderKind.WIND_DOWN, OrderKind.LIGHTS_OUT, OrderKind.NAP,
)
