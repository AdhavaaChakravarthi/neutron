package com.adhavaa.commander.fuel

import com.adhavaa.commander.data.FuelSettings
import com.adhavaa.commander.model.Activity
import com.adhavaa.commander.model.WeighIn
import com.adhavaa.commander.model.restingEnergy
import com.adhavaa.commander.orders.fuelBrief
import com.adhavaa.commander.orders.fuelOrders
import com.adhavaa.commander.orders.OrderKind
import com.adhavaa.commander.model.UsualSchedule
import com.adhavaa.commander.model.ordersFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class FuelDataTest {
    private val monday = LocalDate.of(2026, 10, 12)
    private val set = FuelSettings(
        male = true, birthYear = 2004, heightCm = 178.0, startWeightKg = 85.0, start = monday, activity = Activity.ACTIVE,
    )
    private val fmt: (LocalDateTime) -> String = { it.format(DateTimeFormatter.ofPattern("HH:mm")) }

    private fun data(s: FuelSettings = set, w: List<WeighIn> = listOf(WeighIn(monday, 85.0)), kcal: Map<LocalDate, Double> = emptyMap(), inc: Set<LocalDate> = emptySet()) =
        FuelData(s, w, kcal, inc)

    @Test
    fun yourWeekDefaultsToFruitTuesdayAndVeganSaturday() {
        val s = FuelSettings()
        assertEquals(DayType.NORMAL, dayType(s, monday))
        assertEquals(DayType.FRUIT, dayType(s, monday.plusDays(1)))
        assertEquals(DayType.VEGAN, dayType(s, monday.plusDays(5)))
        assertTrue(defaultFilter(s, monday).fishOnly)
        assertTrue(defaultFilter(s, monday.plusDays(1)).fruitOnly)
        assertTrue(defaultFilter(s, monday.plusDays(5)).vegan)
        assertFalse(s.setUp)
    }

    @Test
    fun noTargetsUntilSetUp() {
        val p = fuelPicture(data(FuelSettings()), monday, monday)
        assertFalse(p.setUp)
        assertNull(p.targets)
        assertTrue(p.checkIns.isEmpty())
        assertTrue(fuelOrders(p, ordersFor(monday, UsualSchedule(LocalTime.of(23, 30), LocalTime.of(7, 30)), 8.0), fmt).isEmpty())
        assertNull(fuelBrief(p, Nutrients.ZERO))
    }

    @Test
    fun firstTargetsComeFromTheProfile() {
        val p = fuelPicture(data(), monday, monday)
        val ree = restingEnergy(85.0, 178.0, 22.0, true)
        val t = p.targets!!
        assertEquals(ree * 1.85 - 0.005 * 85.0 * 7700.0 / 7.0, t.kcal, 1e-9)
        assertEquals(1.6 * 85.0, t.proteinG, 1e-9)
        assertEquals(0.25 * t.kcal / 9.0, t.fatG, 1e-9)
        assertEquals(85.0, p.weightOnDate)
        assertFalse(p.checkInToday)
        assertEquals(monday.plusDays(7), p.nextCheckIn)
    }

    @Test
    fun fruitDayHasNoTargetsButStillCounts() {
        val tue = monday.plusDays(1)
        val p = fuelPicture(data(), tue, tue)
        assertEquals(DayType.FRUIT, p.dayType)
        assertNull(p.targets)
        assertTrue(fuelBrief(p, Nutrients(600.0))!!.startsWith("Fruit day"))
        val d = data(kcal = mapOf(tue to 700.0, monday to 2300.0), inc = setOf(monday))
        assertEquals(mapOf(tue to 700.0), d.intake())
    }

    @Test
    fun checkInDayAndFutureDays() {
        val next = monday.plusDays(7)
        val p = fuelPicture(data(), next, next)
        assertTrue(p.checkInToday)
        assertEquals(2, p.checkIns.size)
        val future = fuelPicture(data(), monday.plusDays(20), monday.plusDays(2))
        assertEquals(monday, future.current!!.date)
    }

    @Test
    fun weighInOrderAndBrief() {
        val o = ordersFor(monday, UsualSchedule(LocalTime.of(23, 30), LocalTime.of(7, 30)), 8.0)
        val notWeighed = fuelPicture(data(w = emptyList()), monday, monday)
        val w = fuelOrders(notWeighed, o, fmt).single { it.kind == OrderKind.WEIGH_IN }
        assertEquals(LocalDateTime.of(2026, 10, 12, 7, 45), w.at)
        assertTrue(fuelBrief(notWeighed, Nutrients(1200.0, 80.0))!!.endsWith("Weigh in."))
        val weighed = fuelPicture(data(), monday, monday)
        assertTrue(fuelOrders(weighed, o, fmt).single { it.kind == OrderKind.WEIGH_IN }.detail.startsWith("Done: 85.0 kg"))
        assertFalse(fuelOrders(weighed, o, fmt).any { it.kind == OrderKind.EAT_TO_TARGET })
    }

    @Test
    fun losingTooFastOrdersEatingToTarget() {
        // 1.5 kg a week down for three weeks, fully logged at 1,500 kcal.
        val w = (0..21).map { WeighIn(monday.plusDays(it.toLong()), 85.0 - it * 1.5 / 7) }
        val kcal = (0..20).associate { monday.plusDays(it.toLong()) to 1500.0 }
        val day = monday.plusDays(21)
        val p = fuelPicture(data(w = w, kcal = kcal), day, day)
        assertTrue(p.losingTooFast)
        val o = ordersFor(day, UsualSchedule(LocalTime.of(23, 30), LocalTime.of(7, 30)), 8.0)
        assertTrue(fuelOrders(p, o, fmt).any { it.kind == OrderKind.EAT_TO_TARGET })
        // The same numbers on a gain goal aren't flagged: the alert is for loss phases.
        assertFalse(fuelPicture(data(set.copy(rate = 0.0025), w = w, kcal = kcal), day, day).losingTooFast)
    }

    @Test
    fun fuelOrdersSayWhyWithoutMedicalClaims() {
        val w = (0..21).map { WeighIn(monday.plusDays(it.toLong()), 85.0 - it * 1.5 / 7) }
        val kcal = (0..20).associate { monday.plusDays(it.toLong()) to 1500.0 }
        val day = monday.plusDays(21)
        val o = ordersFor(day, UsualSchedule(LocalTime.of(23, 30), LocalTime.of(7, 30)), 8.0)
        val list = fuelOrders(fuelPicture(data(w = w, kcal = kcal), day, day), o, fmt)
        assertEquals(setOf(OrderKind.WEIGH_IN, OrderKind.EAT_TO_TARGET), list.map { it.kind }.toSet())
        val banned = listOf("diagnos", "treat", "disorder", "cure", "detox", "starv")
        for (order in list) {
            assertTrue(order.why.length > 40)
            val text = (order.title + order.detail + order.why).lowercase()
            for (b in banned) assertFalse("${order.kind} mentions '$b'", text.contains(b))
        }
    }

    @Test
    fun goalWords() {
        assertEquals("Lose 0.5% a week", goalLabel(-0.005))
        assertEquals("Lose 1% a week", goalLabel(-0.01))
        assertEquals("Gain 0.25% a week", goalLabel(0.0025))
        assertEquals("Maintain", goalLabel(0.0))
    }
}
