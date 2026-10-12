package com.adhavaa.commander.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Train against the golden vectors from make_golden.py, plus direct checks. */
class TrainTest {
    private val dir = File(System.getProperty("golden.dir") ?: "../tools/model-reference/golden")
    private val tol = 1e-6

    private fun rows(name: String): List<List<String>> =
        File(dir, name).readLines().filter { it.isNotBlank() && !it.startsWith("#") }.drop(1).map { it.split(",") }

    private fun near(msg: String, expected: Double, actual: Double) =
        assertEquals(msg, expected, actual, tol * maxOf(1.0, kotlin.math.abs(expected)))

    @Test
    fun e1rmMatchesReference() {
        val r = rows("train_e1rm.csv")
        assertTrue(r.size > 50)
        for (row in r) {
            val got = e1rm(row[0].toDouble(), row[1].toInt())
            if (row[2].isBlank()) assertNull(row.toString(), got) else near(row.toString(), row[2].toDouble(), got!!)
        }
    }

    @Test
    fun progressionMatchesReference() {
        val r = rows("train_progression.csv")
        assertTrue(r.size > 60)
        for (row in r) {
            val p = Prescription(row[0].toInt(), row[1].toInt(), row[2].toInt(), row[3] == "1", Equipment.valueOf(row[4].uppercase()))
            val history = if (row[5].isBlank()) emptyList() else row[5].split(";").map { s ->
                s.trim().split(" ").map { set -> set.split("x").let { LiftSet(it[0].toDouble(), it[1].toInt()) } }
            }
            val got = nextLoad(p, history)
            assertEquals(row.toString(), LoadAction.valueOf(row[6].uppercase()), got.action)
            if (row[7].isBlank()) assertNull(row.toString(), got.weight) else near(row.toString(), row[7].toDouble(), got.weight!!)
        }
    }

    @Test
    fun planMatchesReference() {
        val r = rows("train_plan.csv")
        val actuals = mapOf("missed" to mapOf(0 to 310.0, 1 to 330.0, 2 to 0.0, 3 to 0.0, 4 to 0.0, 5 to 120.0, 6 to 200.0))
        assertTrue(r.size > 100)
        for (row in r) {
            val at = "${row[0]} week ${row[4]}"
            val week = weekPlan(row[4].toInt(), row[1].toDouble(), row[2].toDouble(), row[3].toIntOrNull(), actuals[row[0]].orEmpty())
            near("$at minutes", row[5].toDouble(), week.minutes)
            assertEquals(at, Phase.valueOf(row[6]), week.phase)
            assertEquals("$at recovery", row[7] == "1", week.recovery)
            assertEquals("$at capped", row[8] == "1", week.capped)
            val expected = if (row[9].isBlank()) emptyList() else row[9].split(" ")
            val got = weekSessions(week)
            assertEquals("$at sessions", expected.size, got.size)
            for ((e, s) in expected.zip(got)) {
                val (day, sport, minutes, intensity) = e.split(":")
                assertEquals(at, day.toInt(), s.day)
                assertEquals(at, Sport.valueOf(sport), s.sport)
                near(at, minutes.toDouble(), s.minutes)
                assertEquals(at, Intensity.valueOf(intensity.uppercase()), s.intensity)
            }
        }
    }

    @Test
    fun loadAndEasyShare() {
        assertEquals(360.0, sessionLoad(60.0, 6.0), 0.0)
        assertEquals(0.8, easyShare(listOf(2.0, 3.0, 4.0, 6.0, 3.0))!!, 1e-12)
        assertNull(easyShare(emptyList()))
    }

    @Test
    fun progressionWorkedExamples() {
        val squat = Prescription(3, 6, 8, true, Equipment.BARBELL)
        val good = List(3) { LiftSet(100.0, 9) }
        assertEquals(NextLoad(LoadAction.UP, 105.0), nextLoad(squat, listOf(good, good)))
        assertEquals(NextLoad(LoadAction.HOLD, 100.0), nextLoad(squat, listOf(good)))
        assertEquals(NextLoad(LoadAction.START, null), nextLoad(squat, emptyList()))
        val curl = Prescription(2, 8, 10, false, Equipment.DUMBBELL)
        assertEquals(NextLoad(LoadAction.UP, 22.0), nextLoad(curl, listOf(List(2) { LiftSet(20.0, 11) }, List(2) { LiftSet(20.0, 11) })))
    }
}
