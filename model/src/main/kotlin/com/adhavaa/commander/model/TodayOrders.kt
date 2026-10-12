package com.adhavaa.commander.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.roundToLong

/** The user's stated usual night, used until real nights are recorded. */
data class UsualSchedule(val bedtime: LocalTime, val wake: LocalTime) {
    /** Hours asleep on a usual night. */
    val durationH: Double get() = (clockH(wake) - clockH(bedtime)).mod(24.0)
}

/** A point on today's energy curve. */
data class EnergyPoint(val at: LocalDateTime, val alertness: Double)

/** Today's orders as wall-clock times. Built from [DayPlan]. */
data class TodayOrders(
    val date: LocalDate,
    val wake: LocalDateTime,
    val lightUntil: LocalDateTime,
    val groggyUntil: LocalDateTime,
    val peakStart: LocalDateTime,
    val peakAt: LocalDateTime,
    val peakEnd: LocalDateTime,
    val dipAt: LocalDateTime?,
    val sleepyFrom: LocalDateTime?,
    val caffeineCutoff: LocalDateTime,
    val melatoninRise: LocalDateTime,
    val windDown: LocalDateTime,
    val idealLightsOut: LocalDateTime,
    val lightsOut: LocalDateTime,
    val bedStepLimited: Boolean,
    val targetWake: LocalDateTime,
    val nextWake: LocalDateTime,
    val wakeStepLimited: Boolean,
    val needH: Double,
    val debtH: Double,
    val curve: List<EnergyPoint>,
) {
    /** Alertness at [at], interpolated; null outside the waking window. */
    fun alertnessAt(at: LocalDateTime): Double? {
        if (curve.isEmpty() || at.isBefore(curve.first().at) || at.isAfter(curve.last().at)) return null
        val i = curve.indexOfFirst { !it.at.isBefore(at) }
        if (i <= 0) return curve[0].alertness
        val a = curve[i - 1]
        val b = curve[i]
        val span = java.time.Duration.between(a.at, b.at).seconds.toDouble()
        if (span <= 0.0) return b.alertness
        val frac = java.time.Duration.between(a.at, at).seconds / span
        return a.alertness + (b.alertness - a.alertness) * frac
    }
}

fun clockH(t: LocalTime): Double = t.hour + t.minute / 60.0 + t.second / 3600.0

/**
 * Orders for [date], the day that starts with this morning's wake. Wall-clock arithmetic only:
 * a DST change is treated as a normal 24 h day (DECISIONS.md, handled properly in Phase 6).
 */
fun ordersFor(
    date: LocalDate,
    usual: UsualSchedule,
    needH: Double,
    debtH: Double = 0.0,
    targetWake: LocalTime? = null,
    nights: Int = 20,
    settings: PlanSettings = PlanSettings(),
): TodayOrders {
    val plan = planForSchedule(
        bedClock = clockH(usual.bedtime),
        wakeClock = clockH(usual.wake),
        needH = needH,
        debtH = debtH,
        targetWakeClock = targetWake?.let(::clockH),
        nights = nights,
        st = settings,
    )
    val origin = date.minusDays(nights.toLong()).atStartOfDay()
    fun at(h: Double): LocalDateTime = origin.plusSeconds((h * 3600.0).roundToLong())
    return TodayOrders(
        date = date,
        wake = at(plan.wake),
        lightUntil = at(plan.lightUntil),
        groggyUntil = at(plan.groggyUntil),
        peakStart = at(plan.peakStart),
        peakAt = at(plan.peakAt),
        peakEnd = at(plan.peakEnd),
        dipAt = plan.dipAt?.let(::at),
        sleepyFrom = plan.sleepyFrom?.let(::at),
        caffeineCutoff = at(plan.caffeineCutoff),
        melatoninRise = at(plan.melatoninRise),
        windDown = at(plan.windDown),
        idealLightsOut = at(plan.idealLightsOut),
        lightsOut = at(plan.lightsOut),
        bedStepLimited = plan.bedStepLimited,
        targetWake = at(plan.targetWake),
        nextWake = at(plan.nextWake),
        wakeStepLimited = plan.wakeStepLimited,
        needH = needH,
        debtH = debtH,
        curve = plan.curve.map { EnergyPoint(at(it.t), it.alertness!!) },
    )
}

/**
 * Which day's orders apply at [now]: switch to today's orders [switchBeforeWakeH] hours before the
 * usual wake time, so at 01:00 you still see last night's "lights out". ASSUMPTION (DECISIONS.md).
 */
fun planDateFor(now: LocalDateTime, usual: UsualSchedule, switchBeforeWakeH: Double = 3.0): LocalDate {
    val switchAt = now.toLocalDate().atStartOfDay()
        .plusSeconds(((clockH(usual.wake) - switchBeforeWakeH) * 3600.0).roundToLong())
    return if (now.isBefore(switchAt)) now.toLocalDate().minusDays(1) else now.toLocalDate()
}
