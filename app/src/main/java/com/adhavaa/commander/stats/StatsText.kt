package com.adhavaa.commander.stats

import com.adhavaa.commander.model.Stat
import com.adhavaa.commander.orders.formatHours
import kotlin.math.roundToInt

fun scoreText(v: Double?): String = v?.roundToInt()?.toString() ?: "—"

/** "▲3", "▼2", "±0", or "" when either week has no number. Never red: a dip isn't a failure (rule of command 7). */
fun deltaText(now: Double?, before: Double?): String {
    if (now == null || before == null) return ""
    val d = now.roundToInt() - before.roundToInt()
    return when {
        d > 0 -> "▲$d"
        d < 0 -> "▼${-d}"
        else -> "±0"
    }
}

/** What each corner counts, in a line (MODEL.md section 12). */
fun statMeaning(s: Stat, needH: Double): String = when (s) {
    Stat.SLEEP -> "Each night against your ${formatHours(needH)} sleep need. Sleeping past your need counts as 100%, not more."
    Stat.RHYTHM -> "Sleep Regularity Index: how alike your sleep is from one day to the next, 24 h apart (Phillips et al. 2017). 100 means identical days."
    Stat.FUEL -> "A fully logged day within 10% of your calorie target scores 1, a fully logged day outside it 0.5, an unlogged day 0. Fruit days don't count."
    Stat.STRENGTH -> "Lifts done out of lifts planned. A lift moved to another day still counts."
    Stat.ENDURANCE -> "Swim, bike and run minutes done out of minutes planned. Going over earns nothing extra."
    Stat.MOBILITY -> "Days with a mobility session."
}

/**
 * Last week's outline is drawn only when every corner that has a number now also had one a week ago; otherwise a
 * missing corner would sit at the centre and look like a drop.
 */
fun weekAgoComparable(now: com.adhavaa.commander.model.Hexagon, before: com.adhavaa.commander.model.Hexagon): Boolean =
    now.scores.values.any { it != null } && Stat.entries.all { now.scores[it] == null || before.scores[it] != null }
