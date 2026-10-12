package com.adhavaa.commander.orders

import com.adhavaa.commander.model.TodayOrders
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** A notification Commander sends at [at]. Pure Kotlin; the Android side only delivers it. */
data class Reminder(val kind: OrderKind, val at: LocalDateTime, val title: String, val text: String)

/** Orders that can send a reminder, in the order they happen in a day. */
val REMINDABLE: List<OrderKind> = listOf(
    OrderKind.WAKE, OrderKind.CHECK_IN, OrderKind.PEAK, OrderKind.CAFFEINE, OrderKind.MELATONIN, OrderKind.WIND_DOWN,
    OrderKind.LIGHTS_OUT,
)

/** On by default. Melatonin is an estimate with no action of its own, so it starts off. DECISIONS.md D-38. */
val DEFAULT_REMINDERS: Set<OrderKind> = setOf(
    OrderKind.WAKE, OrderKind.CHECK_IN, OrderKind.PEAK, OrderKind.CAFFEINE, OrderKind.WIND_DOWN, OrderKind.LIGHTS_OUT,
)

/** The caffeine reminder comes early so there is still time for a last cup. ASSUMPTION, DECISIONS.md D-38. */
const val CAFFEINE_LEAD_MIN = 30L

fun reminderLabel(kind: OrderKind): String = when (kind) {
    OrderKind.WAKE -> "Up and outside light"
    OrderKind.PEAK -> "Sharpest window opens"
    OrderKind.CAFFEINE -> "Last caffeine, 30 min warning"
    OrderKind.MELATONIN -> "Melatonin rising"
    OrderKind.WIND_DOWN -> "Wind down"
    OrderKind.LIGHTS_OUT -> "Lights out"
    OrderKind.CHECK_IN -> "Morning report (confirm last night)"
    else -> kind.name.lowercase()
}

fun reminders(o: TodayOrders, enabled: Set<OrderKind>, fmt: (LocalDateTime) -> String): List<Reminder> {
    val out = mutableListOf<Reminder>()
    if (OrderKind.WAKE in enabled) {
        out += Reminder(
            OrderKind.WAKE, o.wake, "Up",
            "Outside light before ${fmt(o.lightUntil)}. Groggy until ${fmt(o.groggyUntil)}, so nothing important yet.",
        )
    }
    if (OrderKind.PEAK in enabled) {
        out += Reminder(
            OrderKind.PEAK, o.peakStart, "Sharpest window open",
            "Hardest work now, until ${fmt(o.peakEnd)}. This window is an estimate.",
        )
    }
    if (OrderKind.CAFFEINE in enabled) {
        out += Reminder(
            OrderKind.CAFFEINE, o.caffeineCutoff.minusMinutes(CAFFEINE_LEAD_MIN), "Last caffeine by ${fmt(o.caffeineCutoff)}",
            "$CAFFEINE_LEAD_MIN minutes left. Nothing caffeinated after that.",
        )
    }
    if (OrderKind.MELATONIN in enabled) {
        out += Reminder(
            OrderKind.MELATONIN, o.melatoninRise, "Melatonin rising",
            "Keep lights low from now. This time is an estimate.",
        )
    }
    if (OrderKind.WIND_DOWN in enabled) {
        out += Reminder(
            OrderKind.WIND_DOWN, o.windDown, "Wind down",
            "Lights dim, screens away. Lights out at ${fmt(o.lightsOut)}.",
        )
    }
    if (OrderKind.LIGHTS_OUT in enabled) {
        out += Reminder(
            OrderKind.LIGHTS_OUT, o.lightsOut, "Lights out",
            "Now. Up at ${fmt(o.nextWake)}." + if (o.bedStepLimited) " Moving 30 min earlier each night." else "",
        )
    }
    return out.sortedBy { it.at }
}

/**
 * Reminders after [now] for the current plan day and the next, soonest first, including the morning report.
 * The receiver re-runs this every time one fires, so the window keeps rolling forward.
 */
fun upcomingReminders(
    data: CommandData,
    now: LocalDateTime,
    fmt: (LocalDateTime) -> String,
    days: Int = 2,
    max: Int = MAX_SCHEDULED,
): List<Reminder> {
    val settings = data.settings
    if (!settings.onboarded || settings.reminders.isEmpty()) return emptyList()
    val first = data.planDate(now)
    return (0 until days)
        .flatMap { offset ->
            val date = first.plusDays(offset.toLong())
            val list = reminders(data.picture(date).orders, settings.reminders, fmt).toMutableList()
            if (OrderKind.CHECK_IN in settings.reminders && !data.nightRecorded(date)) {
                list += Reminder(
                    OrderKind.CHECK_IN, checkInTime(data, date, now), "Morning report",
                    "Confirm last night's sleep. It takes ten seconds.",
                )
            }
            list
        }
        .filter { it.at.isAfter(now) }
        .sortedBy { it.at }
        .take(max)
}

const val MAX_SCHEDULED = 16

/** The next order after [now], or null if the day is done. */
fun nextOrder(orders: List<Order>, now: LocalDateTime): Order? = orders.firstOrNull { it.at.isAfter(now) }

/** "T-2:05": hours and minutes until [at], rounded up to the minute. */
fun countdown(now: LocalDateTime, at: LocalDateTime): String {
    val minutes = ((ChronoUnit.SECONDS.between(now, at) + 59) / 60).coerceAtLeast(0)
    return "T-${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
}

/** Short name of where the day is, for the HUD. */
fun phase(o: TodayOrders, now: LocalDateTime): String = when {
    now.isBefore(o.wake) -> "Asleep"
    now.isBefore(o.groggyUntil) -> "Groggy"
    now.isBefore(o.peakStart) -> "Warming up"
    !now.isAfter(o.peakEnd) -> "Sharpest"
    now.isBefore(o.windDown) -> "Steady"
    now.isBefore(o.lightsOut) -> "Wind down"
    else -> "Lights out"
}

/** The 7-month campaign the user signed up for (D-01). */
data class Campaign(val day: Int, val totalDays: Int, val start: LocalDate, val end: LocalDate) {
    val complete: Boolean get() = day > totalDays
}

const val CAMPAIGN_MONTHS = 7L

fun campaign(start: LocalDate, today: LocalDate): Campaign {
    val end = start.plusMonths(CAMPAIGN_MONTHS)
    val total = ChronoUnit.DAYS.between(start, end).toInt()
    val day = (ChronoUnit.DAYS.between(start, today) + 1).toInt().coerceAtLeast(1)
    return Campaign(day, total, start, end)
}
