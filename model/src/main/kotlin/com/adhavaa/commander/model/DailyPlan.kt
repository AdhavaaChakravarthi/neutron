package com.adhavaa.commander.model

import kotlin.math.ln
import kotlin.math.max

/** Offsets behind the daily orders (MODEL.md section 7). All but two are ASSUMPTIONS, adjustable later. */
data class PlanSettings(
    val latencyH: Double = 0.25,
    /** Drake et al. 2013: caffeine 6 h before bed still cut sleep by over an hour, so the cutoff is earlier. */
    val caffeineH: Double = 8.0,
    val windDownH: Double = 1.0,
    val lightWindowH: Double = 1.0,
    val peakBand: Double = 0.5,
    val groggyW: Double = -1.0,
    /** Rule of command: bedtime and wake move at most this much per day. */
    val maxShiftH: Double = 0.5,
    /** Sletten et al. 2010: sleep onset averages ~2 h after melatonin onset. */
    val melatoninLeadH: Double = 2.0,
    /** Åkerstedt, Folkard & Portin 2004: 7 is the sleepiness threshold. */
    val sleepyThreshold: Double = 7.0,
)

/** Extra time in bed when debt is high (ASSUMPTION; bands from MODEL.md section 4). */
fun debtExtensionH(debtH: Double): Double = when {
    debtH > 5.0 -> 0.5
    debtH >= 2.0 -> 0.25
    else -> 0.0
}

/** Signed clock difference in (-12, 12]. */
fun wrap12(h: Double): Double = (h + 12.0).mod(24.0) - 12.0

/** Night k starts at bed on day k (day k+1 if after midnight) and lasts (wake - bed) mod 24 h. */
fun scheduleEpisodes(bedClock: Double, wakeClock: Double, nights: Int): List<Episode> {
    val dur = (wakeClock - bedClock).mod(24.0)
    return List(nights) { k ->
        val start = if (bedClock >= 12.0) 24.0 * k + bedClock else 24.0 * (k + 1) + bedClock
        Episode(start, start + dur)
    }
}

/** One day's orders in absolute hours. [curve] covers waking to lights out. */
data class DayPlan(
    val p: Double,
    val wake: Double,
    val lightUntil: Double,
    val groggyUntil: Double,
    val peakStart: Double,
    val peakAt: Double,
    val peakEnd: Double,
    val dipAt: Double?,
    val sleepyFrom: Double?,
    val caffeineCutoff: Double,
    val melatoninRise: Double,
    val windDown: Double,
    val idealLightsOut: Double,
    val lightsOut: Double,
    val bedStepLimited: Boolean,
    val targetWake: Double,
    val nextWake: Double,
    val wakeStepLimited: Boolean,
    val curve: List<CurvePoint>,
)

/** All times absolute hours. [episodes] must end with the night that ended at [wake]. */
fun planDay(
    episodes: List<Episode>,
    wake: Double,
    habitualOnset: Double,
    usualNextWake: Double,
    targetNextWake: Double,
    needH: Double,
    debtH: Double,
    prm: TpmParams,
    st: PlanSettings = PlanSettings(),
): DayPlan {
    val nextWake = usualNextWake + max(targetNextWake - usualNextWake, -st.maxShiftH)
    val wakeStepLimited = nextWake > targetNextWake + 1e-9
    val ideal = nextWake - needH - st.latencyH - debtExtensionH(debtH)
    val lightsOut = max(ideal, habitualOnset - st.maxShiftH)
    val bedStepLimited = lightsOut > ideal + 1e-9

    val groggyUntil = wake + ln(prm.wc / st.groggyW) / (-prm.wd)
    val all = simulate(episodes, lightsOut, prm)
    val window = all.filter { it.t in groggyUntil..lightsOut && it.alertness != null }
    check(window.isNotEmpty()) { "No waking time between grogginess and lights out" }

    var peakI = 0
    for (i in window.indices) if (window[i].alertness!! > window[peakI].alertness!!) peakI = i
    val floor = window[peakI].alertness!! - st.peakBand
    var lo = peakI
    while (lo > 0 && window[lo - 1].alertness!! >= floor) lo--
    var hi = peakI
    while (hi < window.size - 1 && window[hi + 1].alertness!! >= floor) hi++

    var dip: CurvePoint? = null
    for (i in 1 until window.size - 1) {
        val a = window[i].alertness!!
        if (a < window[i - 1].alertness!! && a < window[i + 1].alertness!!) {
            if (dip == null || a < dip.alertness!!) dip = window[i]
        }
    }
    val sleepy = window.subList(peakI, window.size).firstOrNull { it.alertness!! < st.sleepyThreshold }?.t

    return DayPlan(
        p = prm.p,
        wake = wake,
        lightUntil = wake + st.lightWindowH,
        groggyUntil = groggyUntil,
        peakStart = window[lo].t,
        peakAt = window[peakI].t,
        peakEnd = window[hi].t,
        dipAt = dip?.t,
        sleepyFrom = sleepy,
        caffeineCutoff = lightsOut - st.caffeineH,
        melatoninRise = habitualOnset - st.melatoninLeadH,
        windDown = lightsOut - st.windDownH,
        idealLightsOut = ideal,
        lightsOut = lightsOut,
        bedStepLimited = bedStepLimited,
        targetWake = targetNextWake,
        nextWake = nextWake,
        wakeStepLimited = wakeStepLimited,
        curve = all.filter { it.t >= wake && it.alertness != null },
    )
}

/**
 * Today's orders when the only data is the usual schedule (before detection exists).
 * Today is day [nights]; the circadian phase is the personal first guess from mid-sleep.
 */
fun planForSchedule(
    bedClock: Double,
    wakeClock: Double,
    needH: Double,
    debtH: Double = 0.0,
    targetWakeClock: Double? = null,
    nights: Int = 20,
    prm: TpmParams = TpmParams(),
    st: PlanSettings = PlanSettings(),
): DayPlan {
    val dur = (wakeClock - bedClock).mod(24.0)
    val mid = (bedClock + dur / 2.0).mod(24.0)
    val personal = prm.copy(p = personalAcrophase(mid, prm))
    val eps = scheduleEpisodes(bedClock, wakeClock, nights)
    val wake = eps.last().end
    val habitualOnset = if (bedClock >= 12.0) 24.0 * nights + bedClock else 24.0 * (nights + 1) + bedClock
    val usualNextWake = habitualOnset + dur
    val tw = targetWakeClock ?: wakeClock
    val targetNextWake = usualNextWake + wrap12(tw - wakeClock)
    return planDay(eps, wake, habitualOnset, usualNextWake, targetNextWake, needH, debtH, personal, st)
}
