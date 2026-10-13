package com.adhavaa.commander.stats

import com.adhavaa.commander.data.FuelSettings
import com.adhavaa.commander.data.TrainSettings
import com.adhavaa.commander.fuel.FuelData
import com.adhavaa.commander.model.Activity
import com.adhavaa.commander.model.SleepKind
import com.adhavaa.commander.model.SleepRecord
import com.adhavaa.commander.model.Stat
import com.adhavaa.commander.model.WeighIn
import com.adhavaa.commander.train.SetRecord
import com.adhavaa.commander.train.StandDown
import com.adhavaa.commander.train.TrainData
import com.adhavaa.commander.train.Workout
import com.adhavaa.commander.train.WorkoutKind
import com.adhavaa.commander.train.dayPlans
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class StatsDataTest {
    /** Monday: the window is the 21 days to Sunday 1 Nov, with the nights up to this morning. */
    private val today = LocalDate.of(2026, 11, 2)
    private val start = today.minusDays(21)

    private fun night(wake: LocalDate, hours: Double) = SleepRecord(
        wake.minusDays(1).atTime(LocalTime.of(23, 0)), wake.minusDays(1).atTime(23, 0).plusMinutes((hours * 60).toLong()), SleepKind.NIGHT,
    )

    private val fuelSettings = FuelSettings(
        male = true, birthYear = 2004, heightCm = 178.0, startWeightKg = 80.0, start = start, activity = Activity.ACTIVE, rate = 0.0,
    )

    @Test
    fun sleepByWakeDayAddsNapsAndCountsSleeplessNights() {
        val d = LocalDate.of(2026, 10, 20)
        val nap = SleepRecord(d.atTime(14, 0), d.atTime(14, 30), SleepKind.NAP)
        val onlyNap = SleepRecord(d.plusDays(1).atTime(14, 0), d.plusDays(1).atTime(14, 30), SleepKind.NAP)
        val m = sleepByWakeDay(listOf(night(d, 7.0), nap, onlyNap), setOf(d.plusDays(2)))
        assertEquals(7.5, m.getValue(d), 1e-9)
        assertFalse(d.plusDays(1) in m)
        assertEquals(0.0, m.getValue(d.plusDays(2)), 1e-9)
    }

    @Test
    fun followingEveryOrderScores100() {
        val records = (0L..21L).map { night(start.plusDays(it + 1), 8.0) }
        val kcal = HashMap<LocalDate, Double>()
        val workouts = ArrayList<Workout>()
        var id = 1L
        val train = TrainData(TrainSettings(start = start.minusDays(14), currentMinutes = 300.0), emptyList(), emptyList(), emptyMap())
        val fuel0 = FuelData(fuelSettings, (0L..21L).map { WeighIn(start.plusDays(it), 80.0) }, emptyMap(), emptySet())
        val cis = fuel0.checkIns(today.minusDays(1))
        val plans = dayPlans(train, start, today.minusDays(1), today)
        for (p in plans) {
            val target = com.adhavaa.commander.model.targetsOn(cis, p.date)!!.kcal
            kcal[p.date] = target
            if (p.lift != null) workouts += Workout(id++, p.date, WorkoutKind.LIFT, p.lift!!.label, true, 60.0, null, 7.0, "", false)
            for (s in p.sessions) workouts += Workout(id++, p.date, WorkoutKind.of(s.sport), s.sport.label, true, s.minutes, null, 3.0, "", false)
            workouts += Workout(id++, p.date, WorkoutKind.MOBILITY, "Recovery stretch", true, 8.0, null, null, "", false)
        }
        val pic = statsPicture(today, records, emptySet(), 8.0, fuel0.copy(dailyKcal = kcal), train.copy(workouts = workouts))
        assertEquals(today.minusDays(1), pic.end)
        assertEquals(start, pic.start)
        for (s in Stat.entries) assertEquals(s.name, 100.0, pic.now.scores[s]!!, 1e-9)
        assertEquals(18, pic.now.counted[Stat.FUEL])  // three Tuesdays are fruit days
        assertEquals(100.0, pic.now.overall!!, 1e-9)
    }

    @Test
    fun standDownsAndSetupAreRespected() {
        // Train set up a week into the window; one stand-down; an open lift with sets counts, an empty one doesn't.
        val setup = start.plusDays(7)
        val sd = setup.plusDays(2)
        val t = TrainData(
            TrainSettings(start = setup, currentMinutes = 300.0),
            listOf(
                Workout(1, sd, WorkoutKind.LIFT, "Upper A", true, 50.0, null, 7.0, "", false),
                Workout(2, setup, WorkoutKind.LIFT, "Lower A", false, 0.0, null, null, "", false),
                Workout(3, setup.plusDays(1), WorkoutKind.LIFT, "Upper A", false, 0.0, null, null, "", false),
            ),
            listOf(SetRecord(1, 2, setup, "Barbell_Squat", 0, 80.0, 8, 2)),
            mapOf(sd to StandDown.ILLNESS),
        )
        val days = statDays(today.minusDays(1), today, emptyMap(), null, t)
        assertEquals(21, days.size)
        assertTrue(days.take(7).none { it.train })
        assertFalse(days[9].train)            // the stand-down day
        assertEquals(0, days[9].lifts)        // a lift logged on it doesn't count
        assertEquals(1, days[7].lifts)        // open lift with a set
        assertEquals(0, days[8].lifts)        // open lift with nothing logged
        assertEquals(13, days.count { it.train })
        assertTrue(days.all { it.kcalTarget == null })
    }

    @Test
    fun fuelScoresHalfForLoggedButOffTargetAndSkipsFruitDays() {
        val fuel = FuelData(fuelSettings, listOf(WeighIn(start, 80.0)), emptyMap(), emptySet())
        val target = com.adhavaa.commander.model.targetsOn(fuel.checkIns(today), start)!!.kcal
        val kcal = (0L..20L).associate { start.plusDays(it) to target * 1.3 }
        val days = statDays(today.minusDays(1), today, emptyMap(), fuel.copy(dailyKcal = kcal), null)
        assertTrue(days.withIndex().filter { start.plusDays(it.index.toLong()).dayOfWeek == DayOfWeek.TUESDAY }.all { it.value.kcalTarget == null })
        assertEquals(18, days.count { it.kcalTarget != null })
        val incomplete = fuel.copy(dailyKcal = kcal, incomplete = setOf(start))
        assertNull(statDays(today.minusDays(1), today, emptyMap(), incomplete, null)[0].kcal)
        val pic = statsPicture(today, emptyList(), emptySet(), 8.0, fuel.copy(dailyKcal = kcal), null)
        assertEquals(50.0, pic.now.scores[Stat.FUEL]!!, 1e-9)
        assertNull(pic.now.scores[Stat.SLEEP])
        assertNotNull(pic.now.overall)
    }

    @Test
    fun weekAgoIsTheSameScoreSevenDaysEarlier() {
        // Seven short nights, then fourteen at need: sleep was lower a week ago.
        val records = (1L..21L).map { k -> night(start.plusDays(k), if (k <= 7) 6.0 else 8.0) }
        val pic = statsPicture(today, records, emptySet(), 8.0, null, null)
        assertTrue(pic.now.scores[Stat.SLEEP]!! > pic.weekAgo.scores[Stat.SLEEP]!!)
        assertNotNull(pic.now.scores[Stat.RHYTHM])
        assertEquals("▲6", deltaText(91.7, 85.7))
        assertEquals("▼2", deltaText(80.0, 82.4))
        assertEquals("", deltaText(null, 50.0))
        assertEquals("—", scoreText(null))
        // Both weeks have sleep and rhythm, so the outline is drawn; a corner missing a week ago hides it.
        assertTrue(weekAgoComparable(pic.now, pic.weekAgo))
        assertFalse(weekAgoComparable(pic.now, pic.weekAgo.copy(scores = pic.weekAgo.scores + (Stat.RHYTHM to null))))
    }

    @Test
    fun copyStaysWellnessOnly() {
        val words = Stat.entries.joinToString(" ") { statMeaning(it, 8.0) }.lowercase()
        for (bad in listOf("diagnos", "disorder", "treat", "insomnia", "apnea")) assertFalse(bad, bad in words)
    }
}
