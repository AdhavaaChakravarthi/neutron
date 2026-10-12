package com.adhavaa.commander.orders

import com.adhavaa.commander.model.TodayOrders
import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * Turns [TodayOrders] into the words Commander says. Pure Kotlin (no Android) so it is unit-tested on the JVM.
 * Copy rules: imperative and short; every order carries its reason; estimates say so; wellness only.
 */
enum class OrderKind {
    WAKE, GROGGY, PEAK, DIP, CAFFEINE, NAP, MELATONIN, WIND_DOWN, SLEEPY, LIGHTS_OUT, TOMORROW, CHECK_IN,
    WEIGH_IN, EAT_TO_TARGET, TRAIN, MOBILITY,
}

data class Order(
    val kind: OrderKind,
    val at: LocalDateTime,
    val until: LocalDateTime?,
    val title: String,
    val detail: String,
    val why: String,
    val estimate: Boolean = false,
)

fun formatHours(h: Double): String {
    val m = (h * 60).roundToInt()
    return if (m % 60 == 0) "${m / 60} h" else "${m / 60} h ${m % 60} min"
}

fun buildOrders(o: TodayOrders, fmt: (LocalDateTime) -> String): List<Order> {
    val need = formatHours(o.needH)
    val list = mutableListOf(
        Order(
            OrderKind.WAKE, o.wake, null, "Up.",
            "Get outside light before ${fmt(o.lightUntil)}.",
            "Bright light soon after waking moves your body clock earlier, so sleep comes on time tonight.",
        ),
        Order(
            OrderKind.GROGGY, o.wake, o.groggyUntil, "Groggy. Nothing important.",
            "It takes about an hour to fully wake up. Ease in.",
            "Sleep inertia: the brain is slow for a while after waking (three-process model, Ingre et al. 2014).",
        ),
        Order(
            OrderKind.PEAK, o.peakStart, o.peakEnd, "Sharpest. Hardest work here.",
            "Strongest around ${fmt(o.peakAt)}.",
            "Your predicted alertness is within 0.5 points of its daily high. For most people the model's " +
                "curve is fairly flat across the afternoon, so the exact edges matter less than the middle.",
            estimate = true,
        ),
        Order(
            OrderKind.CAFFEINE, o.caffeineCutoff, null, "Last caffeine.",
            "Nothing caffeinated after this.",
            "In a study, caffeine taken 6 h before bed still cost people over an hour of sleep (Drake et al. " +
                "2013). Commander allows 8 h.",
        ),
        Order(
            OrderKind.MELATONIN, o.melatoninRise, null, "Melatonin rising.",
            "Your body is starting to prepare for sleep. Keep lights low from here.",
            "Sleep usually starts about 2 h after melatonin begins to rise, but this varies a lot between " +
                "people (Sletten et al. 2010). Estimated from your usual bedtime.",
            estimate = true,
        ),
        Order(
            OrderKind.WIND_DOWN, o.windDown, o.lightsOut, "Wind down.",
            "Dim the lights. Screens away. Nothing stressful. No hard training from now.",
            "Bright light in the evening pushes your body clock later. An hour of low light helps sleep come on time. " +
                "Evening exercise is fine for sleep in general, but vigorous exercise ending within an hour of bedtime " +
                "can delay sleep (Stutz et al. 2019).",
        ),
        Order(
            OrderKind.LIGHTS_OUT, o.lightsOut, null, "Lights out.",
            if (o.bedStepLimited) {
                "Moving 30 min earlier each night toward ${fmt(o.idealLightsOut)}."
            } else {
                "That's your $need before ${fmt(o.nextWake)}."
            },
            "Your sleep need ($need) plus 15 min to fall asleep, counted back from your wake time" +
                (if (o.debtH >= 2.0) ", plus extra for sleep debt" else "") + ". " +
                "Bedtime never moves more than 30 min a day, because sleep is hard to start in the 2-3 h " +
                "before your usual bedtime (Monterastelli et al. 2024).",
        ),
        Order(
            OrderKind.TOMORROW, o.nextWake, null, "Up (tomorrow).",
            when {
                o.wakeStepLimited -> "Moving 30 min earlier each day toward ${fmt(o.targetWake)}."
                o.nextWake.toLocalTime() == o.wake.toLocalTime() -> "Same time as today."
                else -> "Your target wake time."
            },
            "Your wake time also moves at most 30 min a day, so changes stay gradual. The 30 min is a cautious " +
                "choice, not a measured limit.",
        ),
    )
    if (o.debtH >= NAP_DEBT_H && o.caffeineCutoff.isAfter(o.groggyUntil)) {
        list += Order(
            OrderKind.NAP, o.groggyUntil, o.caffeineCutoff, "Nap allowed.",
            "Up to 20 minutes, before ${fmt(o.caffeineCutoff)}. Set an alarm.",
            "You're carrying sleep debt. Short naps restore alertness: in one study a 10-minute nap worked best and " +
                "naps up to 30 minutes still helped (Brooks & Lack 2006). Ending by the caffeine cutoff keeps it from " +
                "eating into tonight's sleep (an assumption).",
        )
    }
    o.dipAt?.let {
        list += Order(
            OrderKind.DIP, it, null, "Dip.", "Easy tasks or a short walk.",
            "Your predicted alertness has a local low here.", estimate = true,
        )
    }
    o.sleepyFrom?.takeIf { it.isBefore(o.windDown) }?.let {
        list += Order(
            OrderKind.SLEEPY, it, null, "Sleepiness sets in.",
            "Expect to feel it. Avoid driving or anything risky.",
            "Predicted alertness drops below the sleepiness threshold (Åkerstedt, Folkard & Portin 2004).",
            estimate = true,
        )
    }
    return list.sortedWith(compareBy<Order> { it.at }.thenBy { it.kind.ordinal })
}

/** One line for "right now". */
fun statusLine(o: TodayOrders, now: LocalDateTime, fmt: (LocalDateTime) -> String): String = when {
    now.isBefore(o.wake) -> "Asleep. Up at ${fmt(o.wake)}."
    now.isBefore(o.groggyUntil) -> "Groggy until ${fmt(o.groggyUntil)}. Ease in."
    now.isBefore(o.peakStart) -> "Warming up. Sharpest from ${fmt(o.peakStart)}."
    !now.isAfter(o.peakEnd) -> "Sharpest until ${fmt(o.peakEnd)}. Do the hard thing."
    now.isBefore(o.windDown) ->
        if (now.isBefore(o.caffeineCutoff)) "Steady. Last caffeine at ${fmt(o.caffeineCutoff)}."
        else "Steady. Wind down at ${fmt(o.windDown)}."
    now.isBefore(o.lightsOut) -> "Wind down. Lights out at ${fmt(o.lightsOut)}."
    else -> "Lights out. Up at ${fmt(o.nextWake)}."
}

/** Debt at which Commander allows a nap (the "moderate" band, MODEL.md section 4). */
const val NAP_DEBT_H = 2.0

fun debtBand(h: Double): String = when {
    h < 2.0 -> "Low"
    h <= 5.0 -> "Moderate"
    else -> "High"
}

/** Karolinska Sleepiness Scale anchors (Åkerstedt & Gillberg 1990); even steps are unlabelled. */
fun kssLabel(v: Int): String = when (v) {
    1 -> "Extremely alert"
    3 -> "Alert"
    5 -> "Neither alert nor sleepy"
    7 -> "Sleepy, but no difficulty staying awake"
    9 -> "Extremely sleepy, fighting sleep"
    else -> ""
}

/** A short name for HUD readouts, where the full order sentence doesn't fit. */
fun shortName(kind: OrderKind): String = when (kind) {
    OrderKind.WAKE -> "Up"
    OrderKind.GROGGY -> "Groggy"
    OrderKind.PEAK -> "Sharpest window"
    OrderKind.DIP -> "Dip"
    OrderKind.CAFFEINE -> "Last caffeine"
    OrderKind.NAP -> "Nap window"
    OrderKind.MELATONIN -> "Melatonin rising"
    OrderKind.WIND_DOWN -> "Wind down"
    OrderKind.SLEEPY -> "Sleepiness"
    OrderKind.LIGHTS_OUT -> "Lights out"
    OrderKind.TOMORROW -> "Up"
    OrderKind.CHECK_IN -> "Morning report"
    OrderKind.WEIGH_IN -> "Weigh in"
    OrderKind.EAT_TO_TARGET -> "Eat to target"
    OrderKind.TRAIN -> "Train"
    OrderKind.MOBILITY -> "Mobility"
}

/** The mission brief: what's left of today in two or three plain sentences. */
fun brief(o: TodayOrders, now: LocalDateTime, fmt: (LocalDateTime) -> String): String {
    if (now.isBefore(o.wake)) return "Sleep. You're up at ${fmt(o.wake)}."
    if (!now.isBefore(o.lightsOut)) return "Lights out now. You're up at ${fmt(o.nextWake)}."
    val parts = mutableListOf<String>()
    if (now.isBefore(o.groggyUntil)) parts += "Ease in until ${fmt(o.groggyUntil)} and get outside light before ${fmt(o.lightUntil)}."
    when {
        now.isBefore(o.peakStart) -> parts += "Save your hardest work for ${fmt(o.peakStart)}–${fmt(o.peakEnd)}."
        !now.isAfter(o.peakEnd) -> parts += "Hardest work now, until ${fmt(o.peakEnd)}."
    }
    parts += if (now.isBefore(o.caffeineCutoff)) "Last caffeine by ${fmt(o.caffeineCutoff)}." else "No more caffeine today."
    parts += if (now.isBefore(o.windDown)) {
        "Wind down at ${fmt(o.windDown)}, lights out at ${fmt(o.lightsOut)}."
    } else {
        "Lights dim, screens away. Lights out at ${fmt(o.lightsOut)}."
    }
    return parts.joinToString(" ")
}

/** Plain-language summary of the day for TalkBack. */
fun daySummary(o: TodayOrders, fmt: (LocalDateTime) -> String): String =
    "Energy curve for today. Groggy until ${fmt(o.groggyUntil)}. " +
        "Sharpest from ${fmt(o.peakStart)} to ${fmt(o.peakEnd)}. " +
        "Wind down at ${fmt(o.windDown)}. Lights out at ${fmt(o.lightsOut)}."
