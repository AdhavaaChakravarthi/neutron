package com.adhavaa.commander.model

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.round

/**
 * Finds the single interval [start, end) of bins with a lower Poisson rate of screen-on events than
 * outside it, maximising the likelihood (MODEL.md section 6, after Cuttone et al. SensibleSleep).
 * Returns null when no rest period of at least [minRestH] exists.
 */
fun detectRestPeriod(screenOnCounts: IntArray, binH: Double, minRestH: Double = 3.0): IntRange? {
    val n = screenOnCounts.size
    val prefix = IntArray(n + 1)
    for (i in 0 until n) prefix[i + 1] = prefix[i] + screenOnCounts[i]
    val total = prefix[n]
    val minBins = round(minRestH / binH).toInt()
    val eps = 1e-3

    fun ll(k: Int, length: Int): Double {
        val lam = max(k.toDouble() / length, eps)
        return k * ln(lam) - lam * length
    }

    var best = Double.NEGATIVE_INFINITY
    var bestI = -1
    var bestJ = -1
    for (i in 0 until n) {
        for (j in (i + minBins)..n) {
            val kIn = prefix[j] - prefix[i]
            val kOut = total - kIn
            val lenIn = j - i
            val lenOut = n - lenIn
            if (lenOut == 0) continue
            if (kIn.toDouble() / lenIn >= kOut.toDouble() / lenOut) continue
            val score = ll(kIn, lenIn) + ll(kOut, lenOut)
            if (score > best) {
                best = score
                bestI = i
                bestJ = j
            }
        }
    }
    return if (bestI < 0) null else bestI until bestJ
}
