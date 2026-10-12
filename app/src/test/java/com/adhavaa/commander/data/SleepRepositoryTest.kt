package com.adhavaa.commander.data

import androidx.room.Room
import com.adhavaa.commander.data.db.CommanderDb
import com.adhavaa.commander.model.SleepKind
import com.adhavaa.commander.model.SleepRecord
import com.adhavaa.commander.orders.OrderKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SleepRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), CommanderDb::class.java)
        .allowMainThreadQueries().build()
    private val repo = SleepRepository(db.dao()) { ZoneId.of("America/Vancouver") }
    private val today = LocalDate.now()
    private fun at(daysAgo: Long, h: Int, m: Int = 0) = today.minusDays(daysAgo).atTime(h, m)

    @After
    fun close() = db.close()

    @Test
    fun savesReadsAndRoundTripsTimes() = runBlocking {
        val night = SleepRecord(at(1, 23, 40), at(0, 7, 10), SleepKind.NIGHT, wokeNaturally = true)
        assertTrue(repo.save(night, "MANUAL", confirmed = true))
        val stored = repo.sleep().single()
        assertEquals(night, stored.record)
        assertTrue(stored.confirmed)
        assertEquals(1, repo.observeSleep().first().size)
    }

    @Test
    fun rejectsOverlapsButAllowsEditingInPlace() = runBlocking {
        assertTrue(repo.save(SleepRecord(at(1, 23, 0), at(0, 7, 0), SleepKind.NIGHT), "MANUAL", true))
        assertFalse(repo.save(SleepRecord(at(0, 6, 0), at(0, 6, 30), SleepKind.NAP), "MANUAL", true))
        val id = repo.sleep().single().id
        assertTrue(repo.save(SleepRecord(at(1, 23, 30), at(0, 7, 0), SleepKind.NIGHT), "MANUAL", true, replaceId = id))
        assertEquals(at(1, 23, 30), repo.sleep().single().record.start)
    }

    @Test
    fun rejectsBackwardsTimes() = runBlocking {
        assertFalse(repo.save(SleepRecord(at(0, 7, 0), at(0, 6, 0), SleepKind.NIGHT), "MANUAL", true))
    }

    @Test
    fun sleeplessNightIsClearedByLoggingTheNight() = runBlocking {
        repo.markSleepless(today)
        assertEquals(setOf(today), repo.skipped())
        repo.save(SleepRecord(at(1, 23, 0), at(0, 6, 0), SleepKind.NIGHT), "MANUAL", true)
        assertTrue(repo.skipped().isEmpty())
    }

    @Test
    fun ordersCanBeTickedAndUnticked() = runBlocking {
        repo.setDone(today, OrderKind.CAFFEINE, true)
        assertEquals(setOf(OrderKind.CAFFEINE), repo.observeDone().first()[today])
        repo.setDone(today, OrderKind.CAFFEINE, false)
        assertTrue(repo.observeDone().first()[today].isNullOrEmpty())
    }

    @Test
    fun ratingsAreClampedToTheScale() = runBlocking {
        repo.rate(12)
        assertEquals(9, db.dao().observeRatings(0).first().single().kss)
    }
}
