package com.adhavaa.commander.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

enum class SleepKind { NIGHT, NAP }

/** A recorded sleep, in wall-clock time. [wokeNaturally] is the morning answer, null if not given. */
data class SleepRecord(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val kind: SleepKind,
    val wokeNaturally: Boolean? = null,
)

/** What the recorded nights say about you. Null fields need more data. */
data class SleepStats(
    val debtH: Double?,
    val needH: Double,
    val needLearned: Boolean,
    val qualifyingNights: Int,
    val daily: List<Pair<LocalDate, Double>>,
    val sri: Double?,
    val socialJetlagH: Double?,
    val chronotype: LocalTime?,
    val habitualBed: LocalTime,
    val habitualWake: LocalTime,
    val recordedNights: Int,
)

data class SleepPicture(val orders: TodayOrders, val stats: SleepStats)

/** How far back recorded nights are read. Older nights stop counting toward need. ASSUMPTION. */
const val LOOKBACK_DAYS = 60

fun clockOf(h: Double): LocalTime = LocalTime.ofSecondOfDay((h * 3600.0).roundToLong().mod(86_400L))

private class Frame(val date: LocalDate) {
    val origin: LocalDateTime = date.minusDays(LOOKBACK_DAYS.toLong()).atStartOfDay()
    val today = LOOKBACK_DAYS
    fun h(t: LocalDateTime): Double = Duration.between(origin, t).seconds / 3600.0
    fun day(d: LocalDate): Int = ChronoUnit.DAYS.between(origin.toLocalDate(), d).toInt()
}

/** Habitual sleep and wake times from recorded nights (falls back to [usual]); used to pick the plan day. */
fun habitualSchedule(records: List<SleepRecord>, usual: UsualSchedule, now: LocalDateTime): UsualSchedule {
    val f = Frame(now.toLocalDate())
    val nights = records.filter { it.kind == SleepKind.NIGHT && it.end.isAfter(f.origin) && !it.end.isAfter(now) }
        .map { Episode(f.h(it.start), f.h(it.end)) }
    val (bed, wake) = habitualTimes(nights, clockH(usual.bedtime), clockH(usual.wake))
    return UsualSchedule(clockOf(bed), clockOf(wake))
}

/** Today's orders and sleep stats from recorded nights; with none, identical to [ordersFor]. */
fun sleepPicture(
    date: LocalDate,
    records: List<SleepRecord>,
    skipped: Set<LocalDate>,
    usual: UsualSchedule,
    needOverrideH: Double?,
    targetWake: LocalTime?,
    freeDays: Set<DayOfWeek>,
    settings: PlanSettings = PlanSettings(),
): SleepPicture {
    val f = Frame(date)
    val inRange = records.filter { it.end.isAfter(f.origin) && it.end.isAfter(it.start) }.sortedBy { it.end }
    val nightRecs = inRange.filter { it.kind == SleepKind.NIGHT }
    val nights = nightRecs.map { Episode(f.h(it.start), f.h(it.end)) }
    val natural = nightRecs.map { it.wokeNaturally == true }
    val naps = inRange.filter { it.kind == SleepKind.NAP }.map { Episode(f.h(it.start), f.h(it.end)) }
    val skippedDays = skipped.map { f.day(it) }.filter { it in 0..f.today }
    val freeWake = (0..f.today + 1).filter { f.origin.toLocalDate().plusDays(it.toLong()).dayOfWeek in freeDays }

    val prior = SleepConstants.DEFAULT_NEED_H
    val qualifying = qualifyingNights(nights, natural, naps, skippedDays, prior)
    val need = needOverrideH ?: estimateNeed(qualifying, prior)
    val tst = dailySleep(nights, naps, skippedDays, f.today)
    val debt = if (tst.isEmpty()) null else sleepDebt(tst, need)

    val plan = planFromHistory(
        nights, naps, f.today, clockH(usual.bedtime), clockH(usual.wake), need, debt ?: 0.0,
        targetWake?.let(::clockH), st = settings,
    )
    val orders = toOrders(plan, f.origin, date, need, debt ?: 0.0)

    val daily = ArrayList<Pair<LocalDate, Double>>()
    for (d in f.today downTo f.today - 29) {
        if (nights.none { wakeDay(it) == d } && d !in skippedDays) continue
        val total = nights.filter { wakeDay(it) == d }.sumOf { it.end - it.start } +
            naps.filter { wakeDay(it) == d }.sumOf { it.end - it.start }
        daily += f.origin.toLocalDate().plusDays(d.toLong()) to total
        if (daily.size == SleepConstants.DEBT_WINDOW) break
    }
    val (hb, hw) = habitualTimes(nights, clockH(usual.bedtime), clockH(usual.wake))
    val stats = SleepStats(
        debtH = debt,
        needH = need,
        needLearned = needOverrideH == null,
        qualifyingNights = qualifying.size,
        daily = daily,
        sri = sleepRegularityIndex(nights, naps, f.today),
        socialJetlagH = socialJetlag(nights, freeWake),
        chronotype = chronotypeMsfsc(nights, natural, freeWake, freeDays.size)?.let(::clockOf),
        habitualBed = clockOf(hb),
        habitualWake = clockOf(hw),
        recordedNights = nights.count { wakeDay(it) > f.today - 14 },
    )
    return SleepPicture(orders, stats)
}
