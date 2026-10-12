package com.adhavaa.commander.model

import kotlin.math.max
import kotlin.math.min

/** MODEL.md sections 3-5. */
object SleepConstants {
    /** Prior for nightly need, hours. MODEL.md section 3. */
    const val DEFAULT_NEED_H = 8.0

    /** The prior counts as this many nights of evidence. ASSUMPTION. */
    const val NEED_PRIOR_WEIGHT = 5.0

    /** Nights in the debt window; matches the protocol of Van Dongen et al. 2003. */
    const val DEBT_WINDOW = 14

    /** Debt below this lets a natural-wake night count toward need. ASSUMPTION. */
    const val DEBT_LOW_H = 2.0

    /** The default acrophase is treated as belonging to a sleeper with mid-sleep at 03:00. ASSUMPTION. */
    const val REFERENCE_MIDSLEEP_H = 3.0
    const val P_MIN = 13.0
    const val P_MAX = 21.0
}

/** Linear recency weights: last night 1.0, the oldest night in the window 1/n. */
fun debtWeights(n: Int = SleepConstants.DEBT_WINDOW): List<Double> = List(n) { k -> (n - k).toDouble() / n }

/**
 * Weighted deficit vs. need over the last 14 nights with data, newest first. Surplus nights pay debt
 * down; the result is floored at 0. Nights without data must not be passed in.
 */
fun sleepDebt(tstHNewestFirst: List<Double>, needH: Double): Double {
    val w = debtWeights()
    var total = 0.0
    for ((k, tst) in tstHNewestFirst.take(SleepConstants.DEBT_WINDOW).withIndex()) {
        total += w[k] * (needH - tst)
    }
    return max(0.0, total)
}

/** Shrinks the mean of qualifying nights (natural wake, low debt) toward the prior. */
fun estimateNeed(naturalWakeTstH: List<Double>, priorH: Double = SleepConstants.DEFAULT_NEED_H): Double {
    if (naturalWakeTstH.isEmpty()) return priorH
    return (SleepConstants.NEED_PRIOR_WEIGHT * priorH + naturalWakeTstH.sum()) /
        (SleepConstants.NEED_PRIOR_WEIGHT + naturalWakeTstH.size)
}

/** First-guess circadian phase from habitual mid-sleep (MODEL.md section 5). Replaced by calibration later. */
fun personalAcrophase(habitualMidsleepH: Double, prm: TpmParams = TpmParams()): Double {
    val shift = (habitualMidsleepH - SleepConstants.REFERENCE_MIDSLEEP_H + 12.0).mod(24.0) - 12.0
    return min(SleepConstants.P_MAX, max(SleepConstants.P_MIN, prm.p + shift))
}
