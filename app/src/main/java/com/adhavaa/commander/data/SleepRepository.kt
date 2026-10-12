package com.adhavaa.commander.data

import com.adhavaa.commander.data.db.CommanderDao
import com.adhavaa.commander.data.db.OrderLogEntity
import com.adhavaa.commander.data.db.RatingEntity
import com.adhavaa.commander.data.db.SkippedNightEntity
import com.adhavaa.commander.data.db.SleepEntity
import com.adhavaa.commander.model.LOOKBACK_DAYS
import com.adhavaa.commander.model.SleepKind
import com.adhavaa.commander.model.SleepRecord
import com.adhavaa.commander.orders.OrderKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** A stored sleep with its id, so the UI can edit or delete it. */
data class StoredSleep(val id: Long, val record: SleepRecord, val source: String, val confirmed: Boolean)

/** Sleep, sleepless nights, ticked orders and alertness ratings. Times are stored as instants and read in the
 * phone's current time zone (DECISIONS.md D-33). */
class SleepRepository(private val dao: CommanderDao, private val zone: () -> ZoneId = { ZoneId.systemDefault() }) {

    private fun ms(t: LocalDateTime): Long = t.atZone(zone()).toInstant().toEpochMilli()
    private fun ldt(ms: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone())
    private fun from(): Long = System.currentTimeMillis() - (LOOKBACK_DAYS + 2) * 86_400_000L

    private fun toStored(e: SleepEntity) = StoredSleep(
        id = e.id,
        record = SleepRecord(ldt(e.startMs), ldt(e.endMs), SleepKind.valueOf(e.kind), e.wokeNaturally),
        source = e.source,
        confirmed = e.confirmed,
    )

    fun observeSleep(): Flow<List<StoredSleep>> = dao.observeSleep(from()).map { list -> list.map(::toStored) }

    suspend fun sleep(): List<StoredSleep> = dao.sleepSince(from()).map(::toStored)

    fun observeSkipped(): Flow<Set<LocalDate>> =
        dao.observeSkipped(LocalDate.now().minusDays(LOOKBACK_DAYS + 2L).toEpochDay())
            .map { list -> list.map { LocalDate.ofEpochDay(it.epochDay) }.toSet() }

    suspend fun skipped(): Set<LocalDate> =
        dao.skippedSince(LocalDate.now().minusDays(LOOKBACK_DAYS + 2L).toEpochDay()).map { LocalDate.ofEpochDay(it.epochDay) }.toSet()

    /** Saves a sleep, replacing [replaceId] if given. Returns false (and saves nothing) if it would overlap another. */
    suspend fun save(record: SleepRecord, source: String, confirmed: Boolean, replaceId: Long? = null): Boolean {
        if (!record.end.isAfter(record.start)) return false
        val s = ms(record.start)
        val e = ms(record.end)
        if (dao.overlapping(s, e, replaceId ?: -1L).isNotEmpty()) return false
        val entity = SleepEntity(
            id = replaceId ?: 0, startMs = s, endMs = e, kind = record.kind.name, source = source,
            confirmed = confirmed, wokeNaturally = record.wokeNaturally, createdMs = System.currentTimeMillis(),
        )
        if (replaceId == null) dao.insertSleep(entity) else dao.updateSleep(entity)
        if (record.kind == SleepKind.NIGHT) dao.deleteSkipped(record.end.toLocalDate().toEpochDay())
        return true
    }

    suspend fun delete(id: Long) = dao.deleteSleep(id)

    suspend fun markSleepless(wakeDate: LocalDate) = dao.insertSkipped(SkippedNightEntity(wakeDate.toEpochDay()))

    suspend fun clearSleepless(wakeDate: LocalDate) = dao.deleteSkipped(wakeDate.toEpochDay())

    fun observeDone(): Flow<Map<LocalDate, Set<OrderKind>>> =
        dao.observeOrderLog(LocalDate.now().minusDays(14).toEpochDay()).map { list ->
            list.filter { it.status == "DONE" }
                .groupBy { LocalDate.ofEpochDay(it.epochDay) }
                .mapValues { (_, v) -> v.mapNotNull { e -> OrderKind.entries.firstOrNull { it.name == e.kind } }.toSet() }
        }

    suspend fun setDone(day: LocalDate, kind: OrderKind, done: Boolean) {
        if (done) {
            dao.upsertOrderLog(OrderLogEntity(day.toEpochDay(), kind.name, "DONE", System.currentTimeMillis()))
        } else {
            dao.deleteOrderLog(day.toEpochDay(), kind.name)
        }
    }

    suspend fun rate(kss: Int) {
        dao.insertRating(RatingEntity(atMs = System.currentTimeMillis(), kss = kss.coerceIn(1, 9)))
    }
}
