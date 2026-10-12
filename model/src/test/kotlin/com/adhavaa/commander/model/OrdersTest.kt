package com.adhavaa.commander.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Behaviour of the wall-clock orders and the rules of command. */
class OrdersTest {
    private val day = LocalDate.of(2026, 10, 12)
    private val usual = UsualSchedule(LocalTime.of(23, 30), LocalTime.of(7, 30))

    @Test
    fun steadySleeperGetsExpectedOrders() {
        val o = ordersFor(day, usual, needH = 8.0)
        assertEquals(LocalDateTime.of(2026, 10, 12, 7, 30), o.wake)
        assertEquals(LocalDateTime.of(2026, 10, 12, 8, 30), o.lightUntil)
        assertEquals(LocalDateTime.of(2026, 10, 12, 15, 15), o.caffeineCutoff)
        assertEquals(LocalDateTime.of(2026, 10, 12, 21, 30), o.melatoninRise)
        assertEquals(LocalDateTime.of(2026, 10, 12, 22, 15), o.windDown)
        assertEquals(LocalDateTime.of(2026, 10, 12, 23, 15), o.lightsOut)
        assertEquals(LocalDateTime.of(2026, 10, 13, 7, 30), o.nextWake)
        assertFalse(o.bedStepLimited)
        assertFalse(o.wakeStepLimited)
        assertTrue("grogginess lasts about an hour", o.groggyUntil.isAfter(o.wake.plusMinutes(60)))
        assertTrue("grogginess lasts about an hour", o.groggyUntil.isBefore(o.wake.plusMinutes(75)))
        assertTrue("peak is inside the day", o.peakStart.isAfter(o.groggyUntil) && o.peakEnd.isBefore(o.lightsOut))
    }

    @Test
    fun bedtimeNeverMovesMoreThanThirtyMinutesEarlier() {
        val o = ordersFor(day, UsualSchedule(LocalTime.of(1, 0), LocalTime.of(7, 0)), needH = 8.0)
        assertEquals(LocalDateTime.of(2026, 10, 13, 0, 30), o.lightsOut)
        assertEquals(LocalDateTime.of(2026, 10, 12, 22, 45), o.idealLightsOut)
        assertTrue(o.bedStepLimited)
    }

    @Test
    fun wakeMovesTowardTargetThirtyMinutesADay() {
        val o = ordersFor(day, usual, needH = 8.0, targetWake = LocalTime.of(6, 0))
        assertEquals(LocalDateTime.of(2026, 10, 13, 7, 0), o.nextWake)
        assertEquals(LocalDateTime.of(2026, 10, 13, 6, 0), o.targetWake)
        assertTrue(o.wakeStepLimited)
        assertEquals(LocalDateTime.of(2026, 10, 12, 23, 0), o.lightsOut)
    }

    @Test
    fun laterTargetIsNotStepLimited() {
        val o = ordersFor(day, usual, needH = 8.0, targetWake = LocalTime.of(8, 30))
        assertEquals(LocalDateTime.of(2026, 10, 13, 8, 30), o.nextWake)
        assertFalse(o.wakeStepLimited)
    }

    @Test
    fun highDebtAsksForMoreTimeInBed() {
        val low = ordersFor(day, usual, needH = 8.0, debtH = 0.0)
        val high = ordersFor(day, usual, needH = 8.0, debtH = 6.0)
        assertTrue(high.idealLightsOut.isBefore(low.idealLightsOut))
    }

    @Test
    fun caffeineCutoffIsAlwaysEightHoursBeforeLightsOut() {
        for (bed in listOf(LocalTime.of(22, 0), LocalTime.of(0, 30), LocalTime.of(2, 0))) {
            val o = ordersFor(day, UsualSchedule(bed, bed.plusHours(8)), needH = 8.0)
            assertEquals(o.lightsOut.minusHours(8), o.caffeineCutoff)
        }
    }

    @Test
    fun ordersAreInChronologicalOrder() {
        for (bed in listOf(LocalTime.of(21, 30), LocalTime.of(23, 30), LocalTime.of(1, 0), LocalTime.of(3, 0))) {
            for (need in listOf(7.0, 8.0, 9.0)) {
                val o = ordersFor(day, UsualSchedule(bed, bed.plusHours(8)), needH = need)
                val seq = listOf(o.wake, o.groggyUntil, o.peakStart, o.peakEnd, o.windDown, o.lightsOut, o.nextWake)
                assertEquals("bed $bed need $need: $seq", seq.sorted(), seq)
                assertTrue(o.caffeineCutoff.isAfter(o.wake))
            }
        }
    }

    @Test
    fun curveCoversWakeToLightsOut() {
        val o = ordersFor(day, usual, needH = 8.0)
        assertEquals(o.wake, o.curve.first().at)
        assertTrue(!o.curve.last().at.isAfter(o.lightsOut))
        assertTrue(o.alertnessAt(o.wake)!! < 7.0)
        assertTrue(o.alertnessAt(o.peakAt)!! > 12.0)
        assertEquals(null, o.alertnessAt(o.wake.minusHours(1)))
    }

    @Test
    fun planDateSwitchesBeforeWake() {
        val at1am = LocalDateTime.of(2026, 10, 13, 1, 0)
        val at5am = LocalDateTime.of(2026, 10, 13, 5, 0)
        val noon = LocalDateTime.of(2026, 10, 13, 12, 0)
        assertEquals(LocalDate.of(2026, 10, 12), planDateFor(at1am, usual))
        assertEquals(LocalDate.of(2026, 10, 13), planDateFor(at5am, usual))
        assertEquals(LocalDate.of(2026, 10, 13), planDateFor(noon, usual))
    }

    @Test
    fun dstDayDoesNotBreak() {
        // America/Vancouver falls back on 2026-11-01; wall-clock arithmetic treats it as a normal day.
        val o = ordersFor(LocalDate.of(2026, 11, 1), usual, needH = 8.0)
        assertEquals(LocalDateTime.of(2026, 11, 1, 23, 15), o.lightsOut)
    }

    @Test
    fun usualDurationWrapsMidnight() {
        assertEquals(8.0, usual.durationH, 1e-9)
        assertEquals(6.0, UsualSchedule(LocalTime.of(1, 0), LocalTime.of(7, 0)).durationH, 1e-9)
    }
}
