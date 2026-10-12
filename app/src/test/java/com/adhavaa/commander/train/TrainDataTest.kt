package com.adhavaa.commander.train

import com.adhavaa.commander.data.Settings
import com.adhavaa.commander.data.TrainSettings
import com.adhavaa.commander.model.Equipment
import com.adhavaa.commander.model.Intensity
import com.adhavaa.commander.model.LoadAction
import com.adhavaa.commander.model.Phase
import com.adhavaa.commander.model.Sport
import com.adhavaa.commander.model.UsualSchedule
import com.adhavaa.commander.model.ordersFor
import com.adhavaa.commander.orders.CommandData
import com.adhavaa.commander.orders.OrderKind
import com.adhavaa.commander.orders.trainOrders
import com.adhavaa.commander.orders.upcomingReminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class TrainDataTest {
    private val monday = LocalDate.of(2026, 10, 12)
    private val settings = TrainSettings(start = monday, raceDate = monday.plusWeeks(24), currentMinutes = 300.0, peakMinutes = 480.0)
    private val fmt: (LocalDateTime) -> String = { it.format(DateTimeFormatter.ofPattern("HH:mm")) }
    private val orders = ordersFor(monday, UsualSchedule(LocalTime.of(23, 30), LocalTime.of(7, 30)), 8.0)

    private val exercises: Map<String, Exercise> by lazy {
        val f = listOf(File("src/main/assets/exercises.tsv"), File("app/src/main/assets/exercises.tsv")).first { it.exists() }
        f.inputStream().use { readExercises(it) }.associateBy { it.id }
    }

    private fun data(
        w: List<Workout> = emptyList(),
        s: List<SetRecord> = emptyList(),
        sd: Map<LocalDate, StandDown> = emptyMap(),
        t: TrainSettings = settings,
    ) = TrainData(t, w, s, sd)

    @Test
    fun everyProgramExerciseIsInTheBundledList() {
        assertEquals(876, exercises.size)
        val missing = PROGRAM_EXERCISE_IDS - exercises.keys
        assertTrue("missing: $missing", missing.isEmpty())
        assertEquals(Equipment.BARBELL, equipmentOf(exercises.getValue("Barbell_Squat").equipment))
        assertEquals(Equipment.BODY, equipmentOf(exercises.getValue("Pullups").equipment))
        assertTrue(alternatives(exercises.getValue("Barbell_Squat"), exercises.values.toList()).all { it.primary.first() == "quadriceps" })
    }

    @Test
    fun everyMajorMuscleTwiceAWeek() {
        val days = HashMap<String, MutableSet<String>>()
        val sets = HashMap<String, Double>()
        for (d in PROGRAM) for (i in d.items) {
            val e = exercises.getValue(i.exerciseId)
            e.primary.forEach { days.getOrPut(it) { HashSet() } += d.key; sets[it] = (sets[it] ?: 0.0) + i.sets }
            e.secondary.forEach { days.getOrPut(it) { HashSet() } += d.key; sets[it] = (sets[it] ?: 0.0) + 0.5 * i.sets }
        }
        for (m in listOf("chest", "lats", "middle back", "shoulders", "quadriceps", "hamstrings", "glutes", "calves")) {
            assertTrue("$m on ${days[m]}", (days[m]?.size ?: 0) >= 2)
            assertTrue("$m ${sets[m]} sets", sets.getValue(m) in 5.0..13.0)
        }
        assertEquals(listOf(1, 2, 4, 5), PROGRAM.map { it.day })
        assertEquals(16, RECOVERY_STRETCH.steps().size)
        assertEquals(480, RECOVERY_STRETCH.totalSeconds)
        assertEquals(270, WARM_UP.totalSeconds)
    }

    @Test
    fun mondayIsLowerAAndAnEasySwim() {
        val p = trainPicture(data(), monday, monday, debtH = 1.0)
        assertTrue(p.setUp)
        assertEquals(0, p.week!!.index)
        assertEquals(Phase.BASE, p.week!!.phase)
        assertEquals("Lower A", p.day.lift!!.label)
        assertEquals(listOf(Sport.SWIM), p.day.sessions.map { it.sport })
        assertEquals("Lower A · Swim 35 min easy", dayLine(p.day))
        assertEquals(24, p.weeksToRace)
        assertTrue(p.days[2].rest)
        assertEquals("Rest day", dayLine(p.days[2]))
        assertEquals(26, p.plan.size)
    }

    @Test
    fun highSleepDebtEasesTheDay() {
        val thu = monday.plusDays(3)
        val p = trainPicture(data(), thu, thu, debtH = 6.0)
        assertTrue(p.day.eased)
        assertTrue(p.day.sessions.all { it.intensity == Intensity.EASY })
        assertEquals(2, p.day.setsFor(PROGRAM[2].items[1]))
        assertEquals(1, p.day.setsFor(PROGRAM[2].items[0]))
        assertFalse(trainPicture(data(), thu, thu, debtH = 4.0).day.eased)
    }

    @Test
    fun standDownReplacesTraining() {
        val p = trainPicture(data(sd = mapOf(monday to StandDown.PAIN)), monday, monday, null)
        assertNull(p.day.lift)
        assertTrue(p.day.sessions.isEmpty())
        val o = trainOrders(p, orders, fmt).first { it.kind == OrderKind.TRAIN }
        assertEquals("Stand down.", o.title)
        assertTrue(o.detail.contains("pain"))
        assertTrue(p.dayComplete(p.day))
    }

    private fun lift(id: Long, day: LocalDate, reps: List<Int>, weight: Double = 100.0, exercise: String = "Barbell_Squat"): Pair<Workout, List<SetRecord>> {
        val w = Workout(id, day, WorkoutKind.LIFT, "Lower A", true, 60.0, null, 7.0, "", false, startMs = id)
        val s = reps.mapIndexed { i, r -> SetRecord(id * 10 + i, id, day, exercise, i, weight, r, 2) }
        return w to s
    }

    @Test
    fun twoGoodSessionsPutTheSquatUp() {
        val (w1, s1) = lift(1, monday.minusDays(7), listOf(9, 9, 9))
        val (w2, s2) = lift(2, monday.minusDays(4), listOf(9, 9, 10))
        val d = data(listOf(w1, w2), s1 + s2)
        val squat = liftTargets(d, PROGRAM[0], monday) { id -> equipmentOf(exercises[id]?.equipment ?: "") }.first()
        assertEquals(LoadAction.UP, squat.next.action)
        assertEquals(105.0, squat.next.weight!!, 0.0)
        assertEquals(100.0 * (1 + 10 / 30.0), squat.bestE1rm!!, 1e-9)
        assertTrue(targetText(squat).startsWith("Up to 105 kg"))
        val one = liftTargets(data(listOf(w2), s2), PROGRAM[0], monday) { Equipment.BARBELL }.first()
        assertEquals(LoadAction.HOLD, one.next.action)
        assertEquals("100 kg · beat 9, 9, 10. Two sessions at 9+ on every set adds weight.", targetText(one))
        val fresh = liftTargets(data(), PROGRAM[0], monday) { Equipment.BARBELL }.first()
        assertEquals("First session: pick a weight you could lift 10–11 times, then do 6–8.", targetText(fresh))
    }

    @Test
    fun aSwapChangesTheExerciseButKeepsTheSlot() {
        val d = data(t = settings.copy(swaps = mapOf("LA1" to "Leg_Press")))
        val t = liftTargets(d, PROGRAM[0], monday) { id -> equipmentOf(exercises[id]?.equipment ?: "") }.first()
        assertEquals("Leg_Press", t.exerciseId)
        assertEquals(Equipment.MACHINE, t.equipment)
        assertEquals("LA1", t.item.slot)
    }

    @Test
    fun weekTotalsLoadAndMuscles() {
        val (w, s) = lift(1, monday, listOf(8, 8, 8))
        val swim = Workout(2, monday, WorkoutKind.SWIM, "Swim", true, 40.0, 1.5, 3.0, "", false)
        val bike = Workout(3, monday.minusDays(2), WorkoutKind.BIKE, "Bike", true, 90.0, 30.0, 6.0, "", false)
        val d = data(listOf(w, swim, bike), s)
        val p = trainPicture(d, monday, monday, null) { exercises[it] }
        assertEquals(40.0, p.weekDone[Sport.SWIM]!!, 0.0)
        assertNull(p.weekDone[Sport.BIKE])
        assertEquals(60 * 7.0 + 40 * 3.0, p.weekLoad, 1e-9)
        assertEquals(0.5, p.easyShare4w!!, 1e-9)
        assertEquals(3.0, p.setsPerMuscle["quadriceps"]!!, 0.0)
        assertEquals(1.5, p.setsPerMuscle["glutes"]!!, 0.0)
        assertTrue(p.dayComplete(p.day))
        assertEquals(mapOf(-1 to 90.0), actualMinutes(d, monday, monday.plusDays(1)))
    }

    @Test
    fun aLiftInProgressIsTodaysOnly() {
        val open = Workout(5, monday, WorkoutKind.LIFT, "Lower A", false, 0.0, null, null, "", false)
        assertNotNull(trainPicture(data(listOf(open)), monday, monday, null).inProgress)
        assertNull(trainPicture(data(listOf(open)), monday.plusDays(1), monday.plusDays(1), null).inProgress)
        assertFalse(trainPicture(data(listOf(open)), monday, monday, null).dayComplete(trainPicture(data(listOf(open)), monday, monday, null).day))
    }

    @Test
    fun ordersAndRemindersWaitForSetup() {
        val none = trainPicture(data(t = TrainSettings()), monday, monday, null)
        assertTrue(trainOrders(none, orders, fmt).isEmpty())
        val list = trainOrders(trainPicture(data(), monday, monday, null), orders, fmt)
        assertEquals(listOf(OrderKind.TRAIN, OrderKind.MOBILITY), list.map { it.kind })
        assertEquals(orders.groggyUntil, list[0].at)
        assertEquals(orders.windDown.minusMinutes(45), list[1].at)
        val banned = listOf("diagnos", "treat", "disorder", "cure", "injur")
        for (o in list + trainOrders(trainPicture(data(sd = mapOf(monday to StandDown.ILLNESS)), monday, monday, null), orders, fmt)) {
            assertTrue(o.why.length > 40)
            val text = (o.title + o.detail + o.why).lowercase()
            for (b in banned) assertFalse("${o.kind} mentions '$b'", text.contains(b))
        }
        val base = Settings(onboarded = true, bedtime = LocalTime.of(23, 30), wake = LocalTime.of(7, 30), targetWake = null, needMinutes = 480)
        val at = monday.atTime(6, 0)
        assertFalse(upcomingReminders(CommandData(base, emptyList(), emptySet()), at, fmt).any { it.kind == OrderKind.TRAIN })
        val withTrain = upcomingReminders(CommandData(base.copy(train = settings), emptyList(), emptySet()), at, fmt)
        assertTrue(withTrain.any { it.kind == OrderKind.TRAIN && it.text.startsWith("Lower A") })
        assertTrue(withTrain.any { it.kind == OrderKind.MOBILITY })
    }

    @Test
    fun textHelpers() {
        assertEquals("102.5", kg(102.5))
        assertEquals("100", kg(100.0))
        assertEquals("100 × 8, 8, 7", setsText(listOf(8, 8, 7).map { com.adhavaa.commander.model.LiftSet(100.0, it) }, timed = false, body = false))
        assertEquals("BW × 9, 8", setsText(listOf(9, 8).map { com.adhavaa.commander.model.LiftSet(0.0, it) }, timed = false, body = true))
        assertEquals("45, 40 s", setsText(listOf(45, 40).map { com.adhavaa.commander.model.LiftSet(0.0, it) }, timed = true, body = true))
        assertEquals("2:30", mmss(150))
        assertEquals(-1, weekIndex(monday, monday.minusDays(1)))
        assertEquals(0, weekIndex(monday.plusDays(3), monday))
    }
}
