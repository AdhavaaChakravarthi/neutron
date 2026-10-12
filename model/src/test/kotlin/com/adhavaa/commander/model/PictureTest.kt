package com.adhavaa.commander.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The wall-clock layer the app uses: real nights in, orders and stats out. */
class PictureTest {
    private val today = LocalDate.of(2026, 10, 20)
    private val usual = UsualSchedule(LocalTime.of(23, 30), LocalTime.of(7, 30))
    private val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    private fun night(wakeDate: LocalDate, bed: LocalTime, wake: LocalTime, natural: Boolean? = null): SleepRecord {
        val start = if (bed.hour >= 12) wakeDate.minusDays(1).atTime(bed) else wakeDate.atTime(bed)
        return SleepRecord(start, wakeDate.atTime(wake), SleepKind.NIGHT, natural)
    }

    @Test
    fun noRecordsMatchesTheSchedulePlan() {
        val p = sleepPicture(today, emptyList(), emptySet(), usual, null, null, weekend)
        val expected = ordersFor(today, usual, needH = 8.0)
        // Same orders; curve values differ only in floating-point noise from a different time origin.
        assertEquals(expected.copy(curve = emptyList()), p.orders.copy(curve = emptyList()))
        assertEquals(expected.curve.size, p.orders.curve.size)
        expected.curve.zip(p.orders.curve).forEach { (e, a) ->
            assertEquals(e.at, a.at)
            assertEquals(e.alertness, a.alertness, 1e-9)
        }
        assertNull(p.stats.debtH)
        assertEquals(0, p.stats.recordedNights)
    }

    @Test
    fun shortNightsBuildDebtAndPullBedtimeEarlier() {
        val recs = (0L..6L).map { night(today.minusDays(it), LocalTime.of(0, 30), LocalTime.of(6, 30)) }
        val p = sleepPicture(today, recs, emptySet(), usual, null, null, weekend)
        assertEquals(7, p.stats.recordedNights)
        assertTrue("debt ${p.stats.debtH}", p.stats.debtH!! > 5.0)
        assertEquals(LocalTime.of(0, 30), p.stats.habitualBed)
        // Earlier than habitual, but by no more than 30 min.
        assertEquals(LocalDateTime.of(2026, 10, 21, 0, 0), p.orders.lightsOut)
        assertTrue(p.orders.bedStepLimited)
    }

    @Test
    fun followingOrdersAdvancesThirtyMinutesANight() {
        val base = (1L..7L).map { night(today.minusDays(it), LocalTime.of(0, 30), LocalTime.of(6, 30)) }
        val tonight1 = sleepPicture(today.minusDays(1), base, emptySet(), usual, 8.0, null, weekend).orders.lightsOut
        val followed = base + night(today, tonight1.toLocalTime(), LocalTime.of(6, 30))
        val tonight2 = sleepPicture(today, followed, emptySet(), usual, 8.0, null, weekend).orders.lightsOut
        assertEquals(30L, java.time.Duration.between(tonight2.toLocalTime().atDate(today), tonight1.toLocalTime().atDate(today)).toMinutes().let { if (it < 0) it + 1440 else it })
    }

    @Test
    fun aSleeplessNightCountsAsZero() {
        val recs = (1L..3L).map { night(today.minusDays(it), LocalTime.of(23, 30), LocalTime.of(7, 30)) }
        val withSkip = sleepPicture(today, recs, setOf(today), usual, 8.0, null, weekend)
        assertEquals(0.0, withSkip.stats.daily.first().second, 1e-9)
        assertEquals(today, withSkip.stats.daily.first().first)
        assertEquals(8.0, withSkip.stats.debtH!!, 1e-9)
    }

    @Test
    fun needIsLearnedFromNaturalWakesUnlessOverridden() {
        val recs = (0L..9L).map { night(today.minusDays(it), LocalTime.of(23, 0), LocalTime.of(8, 0), natural = true) }
        val learned = sleepPicture(today, recs, emptySet(), usual, null, null, weekend).stats
        assertTrue(learned.needLearned)
        assertTrue("need ${learned.needH}", learned.needH > 8.4 && learned.needH < 9.0)
        val manual = sleepPicture(today, recs, emptySet(), usual, 7.5, null, weekend).stats
        assertFalse(manual.needLearned)
        assertEquals(7.5, manual.needH, 1e-9)
    }

    @Test
    fun regularityAndChronotypeAppearWithEnoughNights() {
        val recs = (0L..13L).map {
            val d = today.minusDays(it)
            if (d.dayOfWeek in weekend) night(d, LocalTime.of(0, 30), LocalTime.of(9, 0), natural = true)
            else night(d, LocalTime.of(23, 30), LocalTime.of(7, 0), natural = false)
        }
        val s = sleepPicture(today, recs, emptySet(), usual, null, null, weekend).stats
        assertNotNull(s.sri)
        assertTrue("sri ${s.sri}", s.sri!! in 50.0..100.0)
        assertEquals(1.5, s.socialJetlagH!!, 1e-9)
        assertNotNull(s.chronotype)
    }

    @Test
    fun napsAddToTheDay() {
        val recs = listOf(
            night(today, LocalTime.of(23, 30), LocalTime.of(5, 30)),
            SleepRecord(today.atTime(14, 0), today.atTime(14, 30), SleepKind.NAP),
        )
        val s = sleepPicture(today, recs, emptySet(), usual, 8.0, null, weekend).stats
        assertEquals(6.5, s.daily.first().second, 1e-9)
    }

    @Test
    fun habitualScheduleFallsBackToUsual() {
        assertEquals(usual, habitualSchedule(emptyList(), usual, today.atTime(12, 0)))
    }
}
