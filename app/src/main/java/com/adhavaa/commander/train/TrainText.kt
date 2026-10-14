package com.adhavaa.commander.train

import com.adhavaa.commander.model.Equipment
import com.adhavaa.commander.model.LiftSet
import com.adhavaa.commander.model.LoadAction
import java.util.Locale

/** "100", "102.5", "22". */
fun kg(w: Double): String =
    if (w == Math.rint(w)) w.toLong().toString() else String.format(Locale.US, "%.1f", w).trimEnd('0').trimEnd('.')

/** "100 × 9, 9, 8" or "BW × 9, 8, 8" or "45, 40 s". */
fun setsText(sets: List<LiftSet>, timed: Boolean, body: Boolean): String {
    if (sets.isEmpty()) return "—"
    val reps = sets.joinToString(", ") { it.reps.toString() }
    if (timed) return "$reps s"
    val weights = sets.map { it.weight }.distinct()
    val w = if (weights.size == 1) {
        if (body && weights[0] == 0.0) "BW" else if (body) "BW+${kg(weights[0])}" else kg(weights[0])
    } else {
        weights.joinToString("/") { kg(it) }
    }
    return "$w × $reps"
}

/** What to aim for on an exercise today (MODEL.md 11.1). */
fun targetText(t: LiftTarget): String {
    val i = t.item
    val unit = if (i.timed) " s" else ""
    val body = t.equipment == Equipment.BODY
    val last = t.last
    return when (t.next.action) {
        LoadAction.START ->
            if (body) "First session: as many clean ${if (i.timed) "seconds" else "reps"} as you can, stopping 1–3 short of failure."
            else "First session: pick a weight you could lift ${i.hi + 2}–${i.hi + 3} times, then do ${i.lo}–${i.hi}."
        LoadAction.UP -> "Up to ${kg(t.next.weight!!)} kg: you beat ${i.hi} on every set twice."
        LoadAction.DOWN -> "Down to ${kg(t.next.weight!!)} kg after two sessions below ${i.lo}. Build back up."
        LoadAction.HOLD -> {
            val beat = last?.let { s -> s.joinToString(", ") { it.reps.toString() } } ?: ""
            when {
                body && i.timed -> "Beat last time: $beat$unit."
                body -> "${if ((t.next.weight ?: 0.0) > 0.0) "BW+${kg(t.next.weight!!)}" else "Bodyweight"} · beat $beat."
                else -> "${kg(t.next.weight!!)} kg · beat $beat. Two sessions at ${i.hi + 1}+ on every set adds weight."
            }
        }
    }
}

/** The weight to pre-fill for the next set. */
fun suggestedWeight(t: LiftTarget, loggedToday: List<LiftSet>): Double? =
    loggedToday.lastOrNull()?.weight ?: t.next.weight ?: if (t.equipment == Equipment.BODY) 0.0 else null

fun mmss(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/** Where the swimming pause stands, for the Plan panel (D-77). */
fun swimLine(s: SwimStatus, pauseKg: Double?, swimThisWeek: Boolean): String {
    val day = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.getDefault())
    return when {
        pauseKg == null -> "On"
        s.from != null -> (if (swimThisWeek) "Back since " else "Back from ") + s.from.format(day)
        !s.tracked -> "Paused until ${kg(pauseKg)} kg down · set up Fuel to track it"
        s.kgToGo != null -> "Paused · ${String.format(java.util.Locale.US, "%.1f", s.kgToGo)} kg to go"
        else -> "Paused until ${kg(pauseKg)} kg down · weigh in to track it"
    }
}
