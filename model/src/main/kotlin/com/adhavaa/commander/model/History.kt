package com.adhavaa.commander.model

import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Real nights (MODEL.md section 9). Line-for-line port of the "Real nights" section of
 * tools/model-reference/commander_model.py; golden tests hold them together. Absolute hours as elsewhere.
 */
object HistoryConstants {
    /** Habitual times come from the last 7 recorded nights. ASSUMPTION. */
    const val HABIT_WINDOW = 7

    /** Fewer recorded nights than this and the stated usual schedule is used. ASSUMPTION. */
    const val HABIT_MIN_NIGHTS = 3
}

private fun median(xs: List<Double>): Double {
    val s = xs.sorted()
    val n = s.size
    return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2.0
}

/** Clock hour as hours after noon, so 23:00 and 01:00 sit next to each other. */
private fun afterNoon(clockH: Double): Double = (clockH - 12.0).mod(24.0)

fun wakeDay(e: Episode): Int = floor(e.end / 24.0).toInt()

fun habitualTimes(nights: List<Episode>, fallbackBed: Double, fallbackWake: Double): Pair<Double, Double> {
    val recent = nights.sortedBy { it.end }.takeLast(HistoryConstants.HABIT_WINDOW)
    if (recent.size < HistoryConstants.HABIT_MIN_NIGHTS) return fallbackBed to fallbackWake
    val onset = (median(recent.map { afterNoon(it.start.mod(24.0)) }) + 12.0).mod(24.0)
    val wake = median(recent.map { it.end.mod(24.0) })
    return onset to wake
}

fun nightsWithFill(nights: List<Episode>, today: Int, bedClock: Double, wakeClock: Double, days: Int = 20): List<Episode> {
    val byDay = LinkedHashMap<Int, Episode>()
    for (e in nights) {
        val d = wakeDay(e)
        val cur = byDay[d]
        if (cur == null || (e.end - e.start) > (cur.end - cur.start)) byDay[d] = e
    }
    val dur = (wakeClock - bedClock).mod(24.0)
    return (today - days + 1..today).map { d ->
        byDay[d] ?: run {
            val end = 24.0 * d + wakeClock
            Episode(end - dur, end)
        }
    }
}

/** Where the 30-minute steps start: the earlier of the habitual time and last night's. ASSUMPTION (D-44). */
fun stepAnchors(nights: List<Episode>, today: Int, bedC: Double, wakeC: Double): Pair<Double, Double> {
    val last = nights.filter { wakeDay(it) == today }
    if (last.isEmpty()) return bedC to wakeC
    val e = last.maxBy { it.end - it.start }
    val bed = (min(afterNoon(bedC), afterNoon(e.start.mod(24.0))) + 12.0).mod(24.0)
    val wake = min(wakeC, e.end.mod(24.0))
    return bed to wake
}

fun planFromHistory(
    nights: List<Episode>,
    naps: List<Episode>,
    today: Int,
    usualBed: Double,
    usualWake: Double,
    needH: Double,
    debtH: Double = 0.0,
    targetWakeClock: Double? = null,
    days: Int = 20,
    prm: TpmParams = TpmParams(),
    st: PlanSettings = PlanSettings(),
): DayPlan {
    val past = nights.filter { wakeDay(it) <= today }
    val (bedC, wakeC) = habitualTimes(past, usualBed, usualWake)
    val filled = nightsWithFill(past, today, bedC, wakeC, days)
    val keepNaps = naps.filter { n ->
        filled[0].start <= n.start && filled.none { f -> n.start < f.end && f.start < n.end }
    }
    val eps = (filled + keepNaps).sortedBy { it.start }
    val wake = filled.last().end
    val dur = (wakeC - bedC).mod(24.0)
    val mid = (bedC + dur / 2.0).mod(24.0)
    val personal = prm.copy(p = personalAcrophase(mid, prm))
    val (bedA, wakeA) = stepAnchors(past, today, bedC, wakeC)
    val habitualOnset = if (bedA >= 12.0) 24.0 * today + bedA else 24.0 * (today + 1) + bedA
    val usualNextWake = 24.0 * (today + 1) + wakeA
    val tw = targetWakeClock ?: wakeA
    val targetNextWake = usualNextWake + wrap12(tw - wakeA)
    return planDay(eps, wake, habitualOnset, usualNextWake, targetNextWake, needH, debtH, personal, st)
}

/** Total sleep per wake day, newest first, for days with a recorded night or a "didn't sleep" mark. */
fun dailySleep(nights: List<Episode>, naps: List<Episode>, skippedDays: Collection<Int>, today: Int, lookback: Int = 30): List<Double> {
    val out = ArrayList<Double>()
    for (d in today downTo today - lookback + 1) {
        val hasNight = nights.any { wakeDay(it) == d }
        if (!hasNight && d !in skippedDays) continue
        var total = 0.0
        for (e in nights) if (wakeDay(e) == d) total += e.end - e.start
        for (n in naps) if (wakeDay(n) == d) total += n.end - n.start
        out += total
    }
    return out
}

/** Durations of nights that count toward sleep need (MODEL.md section 3). */
fun qualifyingNights(
    nights: List<Episode>,
    natural: List<Boolean>,
    naps: List<Episode>,
    skippedDays: Collection<Int>,
    priorH: Double = SleepConstants.DEFAULT_NEED_H,
): List<Double> {
    val out = ArrayList<Double>()
    for ((e, nat) in nights.zip(natural)) {
        if (!nat) continue
        val before = dailySleep(nights, naps, skippedDays, wakeDay(e) - 1)
        if (sleepDebt(before, priorH) < SleepConstants.DEBT_LOW_H) out += e.end - e.start
    }
    return out
}

fun needFromHistory(
    nights: List<Episode>,
    natural: List<Boolean>,
    naps: List<Episode>,
    skippedDays: Collection<Int>,
    priorH: Double = SleepConstants.DEFAULT_NEED_H,
): Double = estimateNeed(qualifyingNights(nights, natural, naps, skippedDays, priorH), priorH)

/** Phillips et al. 2017: SRI = 200 * P(same state 24 h apart) - 100, over consecutive recorded wake days. */
fun sleepRegularityIndex(
    nights: List<Episode>,
    naps: List<Episode>,
    today: Int,
    days: Int = 7,
    stepH: Double = 5.0 / 60.0,
    minPairs: Int = 3,
): Double? {
    val recorded = nights.map { wakeDay(it) }.toSet()
    val eps = nights + naps
    fun asleep(t: Double): Boolean = eps.any { it.start <= t && t < it.end }
    var same = 0
    var total = 0
    var pairs = 0
    for (d in today - days + 1..today) {
        if (d !in recorded || (d - 1) !in recorded) continue
        pairs++
        val t = 24.0 * (d - 2) + 12.0
        val n = (24.0 / stepH).roundToInt()
        for (i in 0 until n) {
            val a = asleep(t + i * stepH)
            val b = asleep(t + i * stepH + 24.0)
            if (a == b) same++
            total++
        }
    }
    if (pairs < minPairs) return null
    return 200.0 * same / total - 100.0
}

/** Mid-sleep as hours after noon. */
fun midSleep(e: Episode): Double = afterNoon(((e.start + e.end) / 2.0).mod(24.0))

/** Wittmann et al. 2006: |mid-sleep on free days - mid-sleep on workdays|, hours. */
fun socialJetlag(nights: List<Episode>, freeWakeDays: Collection<Int>): Double? {
    val free = nights.filter { wakeDay(it) in freeWakeDays }.map { midSleep(it) }
    val work = nights.filter { wakeDay(it) !in freeWakeDays }.map { midSleep(it) }
    if (free.size < 2 || work.size < 3) return null
    return kotlin.math.abs(median(free) - median(work))
}

/** MCTQ chronotype MSFsc as a clock hour (Roenneberg et al. 2003/2012). */
fun chronotypeMsfsc(nights: List<Episode>, natural: List<Boolean>, freeWakeDays: Collection<Int>, freeDaysPerWeek: Int): Double? {
    val free = nights.zip(natural).filter { wakeDay(it.first) in freeWakeDays }
    val work = nights.filter { wakeDay(it) !in freeWakeDays }
    if (free.size < 2 || work.size < 3) return null
    if (free.count { !it.second } * 2 > free.size) return null
    val msf = median(free.map { midSleep(it.first) })
    val sdf = free.sumOf { it.first.end - it.first.start } / free.size
    val sdw = work.sumOf { it.end - it.start } / work.size
    val fd = freeDaysPerWeek
    val sdweek = (sdw * (7 - fd) + sdf * fd) / 7.0
    val msfsc = if (sdf <= sdw) msf else msf - (sdf - sdweek) / 2.0
    return (msfsc + 12.0).mod(24.0)
}
