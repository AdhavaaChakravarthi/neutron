package com.adhavaa.commander.orders

import com.adhavaa.commander.model.UsualSchedule
import com.adhavaa.commander.model.ordersFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class OrderListTest {
    private val fmt: (LocalDateTime) -> String = { it.format(DateTimeFormatter.ofPattern("HH:mm")) }
    private val day = LocalDate.of(2026, 10, 12)
    private val steady = ordersFor(day, UsualSchedule(LocalTime.of(23, 30), LocalTime.of(7, 30)), needH = 8.0)

    @Test
    fun ordersAreSortedAndComplete() {
        val orders = buildOrders(steady, fmt)
        assertEquals(orders.sortedBy { it.at }, orders)
        val kinds = orders.map { it.kind }.toSet()
        for (k in listOf(OrderKind.WAKE, OrderKind.GROGGY, OrderKind.PEAK, OrderKind.CAFFEINE,
            OrderKind.MELATONIN, OrderKind.WIND_DOWN, OrderKind.LIGHTS_OUT, OrderKind.TOMORROW)) {
            assertTrue("missing $k", k in kinds)
        }
    }

    @Test
    fun everyOrderSaysWhy() {
        for (o in buildOrders(steady, fmt)) assertTrue("${o.kind} has no reason", o.why.length > 20)
    }

    @Test
    fun lightsOutExplainsTheStepLimit() {
        val late = ordersFor(day, UsualSchedule(LocalTime.of(1, 0), LocalTime.of(7, 0)), needH = 8.0)
        val lightsOut = buildOrders(late, fmt).first { it.kind == OrderKind.LIGHTS_OUT }
        assertEquals("00:30", fmt(lightsOut.at))
        assertTrue(lightsOut.detail, lightsOut.detail.contains("toward 22:45"))
    }

    @Test
    fun steadyLightsOutStatesTheNeed() {
        val lightsOut = buildOrders(steady, fmt).first { it.kind == OrderKind.LIGHTS_OUT }
        assertEquals("23:15", fmt(lightsOut.at))
        assertTrue(lightsOut.detail, lightsOut.detail.contains("8 h before 07:30"))
    }

    @Test
    fun estimatesAreMarked() {
        val byKind = buildOrders(steady, fmt).associateBy { it.kind }
        assertTrue(byKind.getValue(OrderKind.PEAK).estimate)
        assertTrue(byKind.getValue(OrderKind.MELATONIN).estimate)
        assertFalse(byKind.getValue(OrderKind.CAFFEINE).estimate)
    }

    @Test
    fun noMedicalClaims() {
        val banned = listOf("diagnos", "treat", "disorder", "insomnia", "apnea", "cure")
        for (o in buildOrders(steady, fmt)) {
            val text = (o.title + o.detail + o.why).lowercase()
            for (b in banned) assertFalse("${o.kind} mentions '$b'", text.contains(b))
        }
    }

    @Test
    fun statusFollowsTheDay() {
        fun at(h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, 12, h, m)
        assertTrue(statusLine(steady, at(6), fmt).startsWith("Asleep"))
        assertTrue(statusLine(steady, at(8), fmt).startsWith("Groggy"))
        assertTrue(statusLine(steady, at(10), fmt).startsWith("Warming up"))
        assertTrue(statusLine(steady, at(16), fmt).startsWith("Sharpest"))
        assertTrue(statusLine(steady, at(20), fmt).startsWith("Steady"))
        assertTrue(statusLine(steady, at(22, 30), fmt).startsWith("Wind down"))
        assertTrue(statusLine(steady, at(23, 45), fmt).startsWith("Lights out"))
    }

    @Test
    fun hoursFormat() {
        assertEquals("8 h", formatHours(8.0))
        assertEquals("7 h 45 min", formatHours(7.75))
    }
}
