package com.adhavaa.commander.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A night or nap. Times are epoch milliseconds; [kind] is NIGHT or NAP; [source] PHONE or MANUAL. */
@Entity(tableName = "sleep")
data class SleepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMs: Long,
    val endMs: Long,
    val kind: String,
    val source: String,
    val confirmed: Boolean,
    val wokeNaturally: Boolean?,
    val createdMs: Long,
)

/** "I didn't sleep": counts as zero sleep for the night ending on that day. */
@Entity(tableName = "skipped_night")
data class SkippedNightEntity(@PrimaryKey val epochDay: Long)

/** An order ticked off (DONE) for a plan day. No streaks are derived from this (rules of command). */
@Entity(tableName = "order_log", primaryKeys = ["epochDay", "kind"])
data class OrderLogEntity(val epochDay: Long, val kind: String, val status: String, val atMs: Long)

/** A Karolinska Sleepiness Scale rating, 1 (extremely alert) to 9 (extremely sleepy). */
@Entity(tableName = "rating")
data class RatingEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val atMs: Long, val kss: Int)

@Dao
interface CommanderDao {
    @Query("SELECT * FROM sleep WHERE endMs >= :fromMs ORDER BY endMs")
    fun observeSleep(fromMs: Long): Flow<List<SleepEntity>>

    @Query("SELECT * FROM sleep WHERE endMs >= :fromMs ORDER BY endMs")
    suspend fun sleepSince(fromMs: Long): List<SleepEntity>

    @Query("SELECT * FROM sleep WHERE startMs < :endMs AND endMs > :startMs AND id != :exceptId")
    suspend fun overlapping(startMs: Long, endMs: Long, exceptId: Long): List<SleepEntity>

    @Insert
    suspend fun insertSleep(e: SleepEntity): Long

    @Update
    suspend fun updateSleep(e: SleepEntity)

    @Query("DELETE FROM sleep WHERE id = :id")
    suspend fun deleteSleep(id: Long)

    @Query("SELECT * FROM skipped_night WHERE epochDay >= :fromDay")
    fun observeSkipped(fromDay: Long): Flow<List<SkippedNightEntity>>

    @Query("SELECT * FROM skipped_night WHERE epochDay >= :fromDay")
    suspend fun skippedSince(fromDay: Long): List<SkippedNightEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkipped(e: SkippedNightEntity)

    @Query("DELETE FROM skipped_night WHERE epochDay = :epochDay")
    suspend fun deleteSkipped(epochDay: Long)

    @Query("SELECT * FROM order_log WHERE epochDay >= :fromDay")
    fun observeOrderLog(fromDay: Long): Flow<List<OrderLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrderLog(e: OrderLogEntity)

    @Query("DELETE FROM order_log WHERE epochDay = :epochDay AND kind = :kind")
    suspend fun deleteOrderLog(epochDay: Long, kind: String)

    @Insert
    suspend fun insertRating(e: RatingEntity): Long

    @Query("SELECT * FROM rating WHERE atMs >= :fromMs ORDER BY atMs")
    fun observeRatings(fromMs: Long): Flow<List<RatingEntity>>
}

@Database(
    entities = [SleepEntity::class, SkippedNightEntity::class, OrderLogEntity::class, RatingEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CommanderDb : RoomDatabase() {
    abstract fun dao(): CommanderDao

    companion object {
        fun open(context: Context): CommanderDb =
            Room.databaseBuilder(context.applicationContext, CommanderDb::class.java, "commander.db").build()
    }
}
