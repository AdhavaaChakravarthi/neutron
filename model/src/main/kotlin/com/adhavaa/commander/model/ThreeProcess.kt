package com.adhavaa.commander.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.round

/**
 * Three-process model of alertness, A = S + C + U + W (MODEL.md section 2).
 * A line-for-line port of tools/model-reference/commander_model.py; the golden tests hold them together.
 * Times are absolute hours from an origin at local midnight; clock hour = t mod 24.
 */
data class TpmParams(
    // Process S, homeostatic. Ingre et al. 2014.
    val la: Double = 2.4,
    val ha: Double = 14.3,
    val d: Double = -0.0353,
    val g: Double = ln((14.3 - 14.0) / (14.3 - 7.96)) / 8.0,
    // Process C, circadian. Ingre et al. 2014.
    val ca: Double = 2.5,
    val cm: Double = 0.0,
    val p: Double = 16.8,
    // Process U, 12 h. Amplitude and mesor: Ingre et al. 2014. Phase RECONSTRUCTED (MODEL.md).
    val ua: Double = 0.5,
    val um: Double = -0.5,
    val uTroughBeforeP: Double = 3.0,
    // Process W, sleep inertia. Ingre et al. 2014.
    val wc: Double = -5.72,
    val wd: Double = -1.51,
    // Alertness -> KSS. Ingre et al. 2014.
    val kssA: Double = 10.6,
    val kssB: Double = -0.6,
)

/** A sleep episode in absolute hours. */
data class Episode(val start: Double, val end: Double) {
    init {
        require(end > start) { "Episode must end after it starts: $start..$end" }
    }
}

/** One step of the simulated curve. [alertness] is null while asleep. */
data class CurvePoint(val t: Double, val asleep: Boolean, val s: Double, val alertness: Double?)

fun processC(clockH: Double, prm: TpmParams): Double =
    prm.cm + prm.ca * cos(2 * PI * (clockH - prm.p) / 24.0)

fun processU(clockH: Double, prm: TpmParams): Double {
    val trough = prm.p - prm.uTroughBeforeP
    return prm.um - prm.ua * cos(2 * PI * (clockH - trough) / 12.0)
}

fun processW(hoursAwake: Double, prm: TpmParams): Double = prm.wc * exp(prm.wd * hoursAwake)

fun kssFromAlertness(a: Double, prm: TpmParams = TpmParams()): Double = prm.kssA + prm.kssB * a

/**
 * Steps Process S through wake and sleep and returns the curve from t = 0 to [tEnd].
 * Exact exponential update per step. [s0] is arbitrary; use several days of history so it washes out.
 */
fun simulate(
    episodes: List<Episode>,
    tEnd: Double,
    prm: TpmParams = TpmParams(),
    dtH: Double = 5.0 / 60.0,
    s0: Double = 12.0,
): List<CurvePoint> {
    val eps = episodes.sortedBy { it.start }
    val n = round(tEnd / dtH).toInt()
    val out = ArrayList<CurvePoint>(n + 1)
    var s = s0
    var lastWake = 0.0
    var ei = 0
    val sleepDecay = exp(prm.g * dtH)
    val wakeDecay = exp(prm.d * dtH)
    for (i in 0..n) {
        val t = i * dtH
        while (ei < eps.size && eps[ei].end <= t) ei++
        val asleep = ei < eps.size && eps[ei].start <= t && t < eps[ei].end
        if (asleep) {
            out.add(CurvePoint(t, true, s, null))
        } else {
            if (ei > 0 && eps[ei - 1].end <= t) lastWake = eps[ei - 1].end
            val clock = t.mod(24.0)
            val a = s + processC(clock, prm) + processU(clock, prm) + processW(t - lastWake, prm)
            out.add(CurvePoint(t, false, s, a))
        }
        s = if (asleep) prm.ha - (prm.ha - s) * sleepDecay else prm.la + (s - prm.la) * wakeDecay
    }
    return out
}
