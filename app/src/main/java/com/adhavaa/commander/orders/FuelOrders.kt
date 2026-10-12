package com.adhavaa.commander.orders

import com.adhavaa.commander.fuel.DayType
import com.adhavaa.commander.fuel.FuelPicture
import com.adhavaa.commander.fuel.Nutrients
import com.adhavaa.commander.fuel.pct
import com.adhavaa.commander.model.TodayOrders
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** The weigh-in comes 15 minutes after waking, once you're up. ASSUMPTION (D-62). */
const val WEIGH_IN_AFTER_WAKE_MIN = 15L

fun weighInTime(o: TodayOrders): LocalDateTime = o.wake.plusMinutes(WEIGH_IN_AFTER_WAKE_MIN)

fun kcalText(v: Double): String = String.format(Locale.US, "%,d", v.roundToInt())

fun kgText(v: Double): String = String.format(Locale.US, "%.1f", v)

/** Fuel's orders for Today (MODEL.md section 10), or nothing until Fuel is set up. */
fun fuelOrders(f: FuelPicture?, o: TodayOrders, fmt: (LocalDateTime) -> String): List<Order> {
    if (f == null || !f.setUp) return emptyList()
    val list = mutableListOf<Order>()
    val weighed = f.weightOnDate != null
    list += Order(
        OrderKind.WEIGH_IN, weighInTime(o), null, "Weigh in.",
        when {
            weighed -> "Done: ${kgText(f.weightOnDate!!)} kg." + (f.trendKg?.let { " Trend ${kgText(it)} kg." } ?: "")
            f.checkInToday -> "After the toilet, before food or drink. Check-in day: new targets on Fuel."
            else -> "After the toilet, before food or drink. Same scale every morning."
        },
        "Daily weight swings with water and food, so Commander follows a smoothed trend (The Hacker's Diet) and " +
            "fits a line through 3 weeks of weigh-ins to estimate what you burn. Weighing under the same " +
            "conditions each morning keeps the noise down (an assumption).",
    )
    if (f.losingTooFast) {
        val rate = f.current?.weeklyRate ?: 0.0
        list += Order(
            OrderKind.EAT_TO_TARGET, o.groggyUntil, null, "Eat to target.",
            "You're losing about ${pct(abs(rate))} of body weight a week. Eat your full " +
                "${kcalText(f.targets?.kcal ?: f.current?.kcal ?: 0.0)} kcal today.",
            "Commander keeps loss at or under 1% of body weight a week, the top of the range Helms et al. 2014 " +
                "recommend. Faster isn't better. If you're eating to target and still losing this fast, talk to a doctor.",
        )
    }
    return list
}

/** One line for the mission brief. */
fun fuelBrief(f: FuelPicture?, eaten: Nutrients): String? {
    if (f == null || !f.setUp) return null
    if (f.dayType == DayType.FRUIT) return "Fruit day: log what you eat. No targets today."
    val t = f.targets ?: return null
    val parts = "Fuel: ${kcalText(eaten.kcal)} of ${kcalText(t.kcal)} kcal, protein ${eaten.protein.roundToInt()} of ${t.proteinG.roundToInt()} g."
    return if (f.weightOnDate == null) "$parts Weigh in." else parts
}
