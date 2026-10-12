package com.adhavaa.commander.orders

import com.adhavaa.commander.data.TrainSettings
import com.adhavaa.commander.model.TodayOrders
import com.adhavaa.commander.train.RECOVERY_STRETCH
import com.adhavaa.commander.train.StandDown
import com.adhavaa.commander.train.TrainPicture
import com.adhavaa.commander.train.dayLine
import com.adhavaa.commander.train.liftDayFor
import java.time.LocalDate
import java.time.LocalDateTime

/** Mobility comes 45 minutes before wind-down. ASSUMPTION (MODEL.md 11.4). */
const val MOBILITY_BEFORE_WIND_DOWN_MIN = 45L

fun mobilityTime(o: TodayOrders): LocalDateTime = o.windDown.minusMinutes(MOBILITY_BEFORE_WIND_DOWN_MIN)

/** Train's orders for Today (MODEL.md section 11), or nothing until Train is set up. */
fun trainOrders(t: TrainPicture?, o: TodayOrders, fmt: (LocalDateTime) -> String): List<Order> {
    if (t == null || !t.setUp) return emptyList()
    val d = t.day
    val list = mutableListOf<Order>()
    val (title, detail, why) = when {
        d.standDown == StandDown.PAIN -> Triple(
            "Stand down.", "Rest today. Don't train through pain.",
            "Pain means stand down (rule of command 6). If it lasts more than a few days or gets worse, see a doctor or physiotherapist.",
        )
        d.standDown != null -> Triple(
            "Stand down.", "Rest today (${d.standDown.label.lowercase()}). Mobility only if it feels good.",
            "Illness or exhaustion means stand down (rule of command 6). If you're exhausted for weeks while following orders, see a doctor.",
        )
        d.rest -> Triple(
            "Rest day.", "No lifting or endurance today. Mobility at ${fmt(mobilityTime(o))}.",
            "One full rest day a week keeps 48 hours between the lower-body days and the long ride (an assumption, D-66).",
        )
        else -> Triple(
            "Train.", dayLine(d) + if (d.eased) ". Sleep debt is high: easy only, one set fewer per lift." else ".",
            "Today's slot in your 4-day upper/lower program and the week's swim, bike and run plan. About 80% of endurance " +
                "sessions stay easy (Seiler 2010); weights rise only after two good sessions (ACSM 2009). " +
                "Finish hard sessions at least an hour before lights out (Stutz et al. 2019).",
        )
    }
    list += Order(OrderKind.TRAIN, o.groggyUntil, null, title, detail, why)
    if (o.windDown.minusMinutes(MOBILITY_BEFORE_WIND_DOWN_MIN).isAfter(o.groggyUntil)) {
        list += Order(
            OrderKind.MOBILITY, mobilityTime(o), null, "Mobility.",
            "${RECOVERY_STRETCH.label}: ${RECOVERY_STRETCH.steps().size} holds of 30 s, about ${RECOVERY_STRETCH.totalSeconds / 60} min. " +
                "Stretch to mild tightness, never pain.",
            "ACSM recommends flexibility work at least 2–3 days a week, daily being most effective, holding each stretch " +
                "10–30 seconds to the point of tightness or slight discomfort.",
        )
    }
    return list
}

/** Reminder text that only needs settings: the program and week are fixed (D-66). */
fun trainReminderText(s: TrainSettings, date: LocalDate): String {
    val lift = liftDayFor(date.dayOfWeek.value)
    return when {
        date.dayOfWeek.value == 3 -> "Rest day. Mobility tonight."
        lift != null -> "${lift.label} plus today's swim, bike or run. Details on the Train tab."
        else -> "Endurance day. Details on the Train tab."
    }
}
