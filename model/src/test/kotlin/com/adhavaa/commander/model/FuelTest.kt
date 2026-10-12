package com.adhavaa.commander.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

/** Fuel against the golden vectors from make_golden.py, plus a few direct checks. */
class FuelTest {
    private val dir = File(System.getProperty("golden.dir") ?: "../tools/model-reference/golden")
    private val tol = 1e-6

    private fun lines(name: String) = File(dir, name).readLines().filter { it.isNotBlank() }

    private fun rows(name: String): List<List<String>> =
        lines(name).filter { !it.startsWith("#") }.drop(1).map { it.split(",") }

    private fun near(msg: String, expected: Double, actual: Double) =
        assertEquals(msg, expected, actual, tol * maxOf(1.0, kotlin.math.abs(expected)))

    private fun day(s: String): LocalDate = LocalDate.ofEpochDay(s.toLong())

    private class Case(val profile: FuelProfile, val weighIns: List<WeighIn>, val intake: Map<LocalDate, Double>, val through: LocalDate)

    private fun case(name: String): Case {
        val header = lines("fuel_input_$name.csv").first().removePrefix("#").trim().split(" ").associate {
            val (k, v) = it.split("="); k to v
        }
        val profile = FuelProfile(
            male = header.getValue("male") == "1",
            ageY = header.getValue("age").toDouble(),
            heightCm = header.getValue("height").toDouble(),
            startWeightKg = header.getValue("weight").toDouble(),
            activity = Activity.valueOf(header.getValue("activity").uppercase()),
            rate = header.getValue("rate").toDouble(),
            start = day(header.getValue("start")),
            checkInDay = DayOfWeek.of(header.getValue("weekday").toInt()),
        )
        val weigh = ArrayList<WeighIn>()
        val intake = HashMap<LocalDate, Double>()
        for (r in rows("fuel_input_$name.csv")) {
            if (r[0] == "w") weigh += WeighIn(day(r[1]), r[2].toDouble()) else intake[day(r[1])] = r[2].toDouble()
        }
        return Case(profile, weigh, intake, day(header.getValue("through")))
    }

    private val names = listOf("loss", "gain", "floor", "empty")

    @Test
    fun trendMatchesReference() {
        for (name in names) {
            val c = case(name)
            val trend = trendSeries(c.weighIns)
            val expected = rows("fuel_trend_$name.csv")
            assertEquals("$name trend days", expected.size, trend.size)
            for (r in expected) near("$name trend ${r[0]}", r[1].toDouble(), trend.getValue(day(r[0])))
        }
    }

    @Test
    fun checkInsMatchReference() {
        for (name in names) {
            val c = case(name)
            val got = fuelCheckIns(c.profile, c.weighIns, c.intake, c.through)
            val expected = rows("fuel_$name.csv")
            assertEquals("$name check-ins", expected.size, got.size)
            for ((r, ci) in expected.zip(got)) {
                val at = "$name ${r[0]}"
                assertEquals(at, day(r[0]), ci.date)
                near("$at trend", r[1].toDouble(), ci.trendKg)
                near("$at rest", r[2].toDouble(), ci.restKcal)
                near("$at expenditure", r[3].toDouble(), ci.expenditure)
                if (r[4].isBlank()) assertNull("$at observed", ci.observed) else near("$at observed", r[4].toDouble(), ci.observed!!)
                if (r[5].isBlank()) assertNull("$at weekly", ci.weeklyRate) else near("$at weekly", r[5].toDouble(), ci.weeklyRate!!)
                near("$at kcal", r[6].toDouble(), ci.kcal)
                near("$at protein", r[7].toDouble(), ci.proteinG)
                near("$at fat", r[8].toDouble(), ci.fatG)
                near("$at carb", r[9].toDouble(), ci.carbG)
                assertEquals("$at floor", r[10] == "1", ci.floorApplied)
                assertEquals("$at limited", r[11] == "1", ci.stepLimited)
            }
        }
    }

    @Test
    fun goldenCasesExerciseEveryBranch() {
        val all = names.flatMap { n -> case(n).let { fuelCheckIns(it.profile, it.weighIns, it.intake, it.through) } }
        assertTrue("an update from data", all.any { it.observed != null })
        assertTrue("the resting-energy floor", all.any { it.floorApplied })
        assertTrue("the 200 kcal step limit", all.any { it.stepLimited })
    }

    @Test
    fun mifflinWorkedExample() {
        assertEquals(1805.0, restingEnergy(80.0, 180.0, 25.0, true), 0.0)
        assertEquals(1639.0, restingEnergy(80.0, 180.0, 25.0, false), 0.0)
    }

    @Test
    fun targetsHoldUntilTheNextCheckIn() {
        val start = LocalDate.of(2026, 10, 14) // a Wednesday
        val p = FuelProfile(true, 22.0, 178.0, 85.0, Activity.ACTIVE, -0.005, start)
        val cis = fuelCheckIns(p, emptyList(), emptyMap(), start.plusDays(20))
        assertEquals(listOf(start, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 2)), cis.map { it.date })
        assertEquals(cis[0], targetsOn(cis, start.plusDays(4)))
        assertNull(targetsOn(cis, start.minusDays(1)))
        assertFalse(losingTooFast(cis.last()))
    }

    @Test
    fun goalRateIsCapped() {
        assertEquals(-0.01, clampRate(-0.03), 0.0)
        assertEquals(0.005, clampRate(0.02), 0.0)
        assertEquals(-0.005, clampRate(-0.005), 0.0)
    }
}
