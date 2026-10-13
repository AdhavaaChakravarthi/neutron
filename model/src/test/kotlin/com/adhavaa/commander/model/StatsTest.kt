package com.adhavaa.commander.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The stats hexagon against the golden vectors from make_golden.py, plus the rule-of-command checks. */
class StatsTest {
    private val dir = File(System.getProperty("golden.dir") ?: "../tools/model-reference/golden")
    private val tol = 1e-6

    private fun lines(name: String): List<String> = File(dir, name).readLines().filter { it.isNotBlank() && !it.startsWith("#") }

    private fun near(msg: String, expected: Double, actual: Double) =
        assertEquals(msg, expected, actual, tol * maxOf(1.0, kotlin.math.abs(expected)))

    private fun opt(s: String): Double? = if (s == "-" || s.isBlank()) null else s.toDouble()

    @Test
    fun hexagonMatchesReference() {
        val rows = lines("stats.csv").drop(1).map { it.split(",") }
        assertTrue(rows.size >= 10)
        for (r in rows) {
            val days = r[3].split(";").map { d ->
                val t = d.trim().split(" ")
                StatDay(opt(t[0]), opt(t[1]), opt(t[2]), t[3] == "1", t[4] == "1", t[5].toInt(), t[6].toDouble(), t[7].toDouble(), t[8] == "1")
            }
            assertEquals(r[0], StatsConstants.WINDOW.takeIf { r[0] != "six_days" } ?: 6, days.size)
            val h = hexagon(days, r[1].toDouble(), opt(r[2]))
            Stat.entries.forEachIndexed { i, s ->
                val want = opt(r[4 + i])
                val got = h.scores[s]
                if (want == null) assertNull("${r[0]} $s", got) else near("${r[0]} $s", want, got!!)
            }
            assertEquals(r[0], r[10].split(" ").map { it.toInt() }, Stat.entries.map { h.counted.getValue(it) })
            val overall = opt(r[11])
            if (overall == null) assertNull(r[0], h.overall) else near("${r[0]} overall", overall, h.overall!!)
        }
    }

    @Test
    fun regularityOver21NightsMatchesReference() {
        val eps = lines("history_episodes.csv").drop(1).map { it.split(",") }
        val rows = lines("stats_sri.csv").drop(1).map { it.split(",") }
        assertEquals(3, rows.size)
        for (r in rows) {
            val mine = eps.filter { it[0] == r[0] }
            val nights = mine.filter { it[1] == "N" }.map { Episode(it[2].toDouble(), it[3].toDouble()) }
            val naps = mine.filter { it[1] == "P" }.map { Episode(it[2].toDouble(), it[3].toDouble()) }
            val got = sleepRegularityIndex(nights, naps, r[1].toInt(), StatsConstants.WINDOW, minPairs = StatsConstants.MIN_DAYS)
            val want = opt(r.getOrElse(2) { "" })
            if (want == null) assertNull(r[0], got) else near(r[0], want, got!!)
        }
    }

    private fun ideal(k: Int): StatDay {
        val lift = k % 7 in setOf(0, 1, 3, 4)
        return StatDay(8.0, 2500.0, 2480.0, true, lift, if (lift) 1 else 0, 45.0, 45.0, true)
    }

    private val full = (0 until 21).map(::ideal)

    @Test
    fun rulesOfCommand() {
        val perfect = hexagon(full, 8.0, 100.0)
        Stat.entries.forEach { near("perfect $it", 100.0, perfect.scores[it]!!) }
        // One missed day costs only its share (rule 7: no streak cliff).
        val missed = full.toMutableList().also { it[10] = it[10].copy(sleepH = 6.0, kcal = null, lifts = 0, enduranceDone = 0.0, mobility = false) }
        val one = hexagon(missed, 8.0, 100.0)
        near("sleep", 100.0 - 25.0 / 21, one.scores[Stat.SLEEP]!!)
        near("fuel", 100.0 - 100.0 / 21, one.scores[Stat.FUEL]!!)
        near("strength", 100.0 - 100.0 / 12, one.scores[Stat.STRENGTH]!!)
        // Over-delivery earns nothing extra (rule 3).
        val more = hexagon(full.map { it.copy(sleepH = 10.0, lifts = it.lifts * 2, enduranceDone = 90.0) }, 8.0, 100.0)
        assertTrue(more.scores.values.all { it!! <= 100.0 + 1e-9 })
        // Stand-down days don't count against Train (rule 6).
        val rested = full.mapIndexed { k, d -> if (k in 5..7) d.copy(train = false, lifts = 0, enduranceDone = 0.0, mobility = false) else d }
        val sd = hexagon(rested, 8.0, 100.0)
        listOf(Stat.STRENGTH, Stat.ENDURANCE, Stat.MOBILITY).forEach { near("stand-down $it", 100.0, sd.scores[it]!!) }
        // Too little data shows nothing; off-target logging gets half.
        assertTrue(hexagon(full.take(6), 8.0, null).scores.values.all { it == null })
        near("half credit", 50.0, hexagon(full.map { it.copy(kcal = 3000.0) }, 8.0, 100.0).scores[Stat.FUEL]!!)
        near("rhythm clamp", 0.0, hexagon(full, 8.0, -20.0).scores[Stat.RHYTHM]!!)
    }
}
