package com.adhavaa.commander.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Compares the Kotlin model with golden vectors written by tools/model-reference/make_golden.py.
 * Gradle passes the directory in the golden.dir system property.
 */
class GoldenTest {
    private val dir = File(System.getProperty("golden.dir") ?: "../tools/model-reference/golden")
    private val tol = 1e-6

    private fun rows(name: String): List<List<String>> =
        File(dir, name).readLines().filter { it.isNotBlank() && !it.startsWith("#") }.drop(1).map { it.split(",") }

    private fun nums(s: String): List<Double> = if (s.isBlank()) emptyList() else s.trim().split(" ").map { it.toDouble() }

    private fun near(msg: String, expected: Double, actual: Double) =
        assertEquals(msg, expected, actual, tol * maxOf(1.0, kotlin.math.abs(expected)))

    @Test
    fun curvesMatchReference() {
        for (name in listOf("baseline", "restricted", "late")) {
            val eps = rows("episodes_$name.csv").map { Episode(it[0].toDouble(), it[1].toDouble()) }
            val header = File(dir, "curve_$name.csv").readLines().first()
            val params = header.removePrefix("#").trim().split(" ").associate {
                val (k, v) = it.split("="); k to v.toDouble()
            }
            val pts = simulate(eps, params.getValue("t_end"), TpmParams(p = params.getValue("p")))
            val expected = rows("curve_$name.csv")
            assertTrue("$name: no golden rows", expected.isNotEmpty())
            for (r in expected) {
                val i = r[0].toInt()
                val pt = pts[i]
                near("$name[$i].t", r[1].toDouble(), pt.t)
                assertEquals("$name[$i].asleep", r[2] == "1", pt.asleep)
                near("$name[$i].s", r[3].toDouble(), pt.s)
                if (r[4].isBlank()) assertNull("$name[$i].alertness", pt.alertness)
                else near("$name[$i].alertness", r[4].toDouble(), pt.alertness!!)
            }
        }
    }

    @Test
    fun debtMatchesReference() {
        for (r in rows("debt.csv")) near("debt $r", r[2].toDouble(), sleepDebt(nums(r[1]), r[0].toDouble()))
    }

    @Test
    fun needMatchesReference() {
        for (r in rows("need.csv")) near("need $r", r[2].toDouble(), estimateNeed(nums(r[1]), r[0].toDouble()))
    }

    @Test
    fun acrophaseMatchesReference() {
        for (r in rows("acrophase.csv")) near("p for mid ${r[0]}", r[1].toDouble(), personalAcrophase(r[0].toDouble()))
    }

    @Test
    fun detectorMatchesReference() {
        for ((n, r) in rows("detector.csv").withIndex()) {
            val counts = r[4].trim().split(" ").map { it.toInt() }.toIntArray()
            val got = detectRestPeriod(counts, r[0].toDouble(), r[1].toDouble())
            val start = r[2].toInt()
            if (start < 0) assertNull("detector case $n", got)
            else {
                assertEquals("detector case $n start", start, got?.first)
                assertEquals("detector case $n end", r[3].toInt(), got?.let { it.last + 1 })
            }
        }
    }

    @Test
    fun dailyOrdersMatchReference() {
        val lines = File(dir, "plan.csv").readLines()
        val cols = lines.first().split(",")
        for (line in lines.drop(1).filter { it.isNotBlank() }) {
            // inputs are the first seven columns; "wake" appears again among the outputs
            val f = line.split(",")
            val plan = planForSchedule(
                bedClock = f[1].toDouble(), wakeClock = f[2].toDouble(), needH = f[3].toDouble(),
                debtH = f[4].toDouble(), targetWakeClock = f[5].ifBlank { null }?.toDouble(),
                nights = f[6].toInt(),
            )
            val name = f[0]
            val got = mapOf(
                "p" to plan.p, "wake" to plan.wake, "light_until" to plan.lightUntil,
                "groggy_until" to plan.groggyUntil, "peak_start" to plan.peakStart, "peak_at" to plan.peakAt,
                "peak_end" to plan.peakEnd, "dip_at" to plan.dipAt, "sleepy_from" to plan.sleepyFrom,
                "caffeine_cutoff" to plan.caffeineCutoff, "melatonin_rise" to plan.melatoninRise,
                "wind_down" to plan.windDown, "ideal_lights_out" to plan.idealLightsOut,
                "lights_out" to plan.lightsOut, "target_wake" to plan.targetWake, "next_wake" to plan.nextWake,
            )
            val outputs = cols.drop(cols.indexOf("p")).zip(f.drop(cols.indexOf("p"))).toMap()
            for ((k, value) in got) {
                val exp = outputs.getValue(k)
                if (exp.isBlank()) assertNull("$name.$k", value) else near("$name.$k", exp.toDouble(), value!!)
            }
            assertEquals("$name.bed_step_limited", outputs.getValue("bed_step_limited") == "1", plan.bedStepLimited)
            assertEquals("$name.wake_step_limited", outputs.getValue("wake_step_limited") == "1", plan.wakeStepLimited)
        }
    }
}
