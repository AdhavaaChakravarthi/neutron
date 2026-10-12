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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

/**
 * One of your own foods or recipes, nutrients per 100 g. [servings] is "label=grams|...". A recipe keeps its
 * ingredients as "ref=grams|..." plus the cooked weight and number of servings so it can be edited.
 */
@Entity(tableName = "food")
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carb: Double,
    val fiber: Double,
    val servings: String,
    val diet: String,
    val ingredients: String?,
    val cookedG: Double?,
    val portions: Int?,
    val archived: Boolean,
    val createdMs: Long,
)

/** A logged food. Its nutrients are a snapshot taken when it was logged. [grams] is null for a quick add. */
@Entity(tableName = "food_log")
data class FoodLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val meal: String,
    val ref: String,
    val name: String,
    val grams: Double?,
    val amount: String,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carb: Double,
    val fiber: Double,
    val atMs: Long,
)

/** One weigh-in per day; a second one that day replaces the first. */
@Entity(tableName = "weight")
data class WeightEntity(@PrimaryKey val epochDay: Long, val kg: Double, val atMs: Long)

/** A day you marked "not fully logged", left out of the expenditure estimate (D-60). */
@Entity(tableName = "fuel_day")
data class FuelDayEntity(@PrimaryKey val epochDay: Long, val incomplete: Boolean)

data class DayKcal(val epochDay: Long, val kcal: Double)

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

    // --- Fuel ---------------------------------------------------------------------------------

    @Query("SELECT * FROM food WHERE archived = 0 ORDER BY name COLLATE NOCASE")
    fun observeFoods(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food WHERE id = :id")
    suspend fun food(id: Long): FoodEntity?

    @Insert
    suspend fun insertFood(e: FoodEntity): Long

    @Update
    suspend fun updateFood(e: FoodEntity)

    @Query("UPDATE food SET archived = 1 WHERE id = :id")
    suspend fun archiveFood(id: Long)

    @Query("SELECT * FROM food_log WHERE epochDay = :epochDay ORDER BY atMs")
    fun observeLog(epochDay: Long): Flow<List<FoodLogEntity>>

    @Query("SELECT * FROM food_log WHERE epochDay = :epochDay ORDER BY atMs")
    suspend fun log(epochDay: Long): List<FoodLogEntity>

    @Query("SELECT epochDay, SUM(kcal) AS kcal FROM food_log GROUP BY epochDay ORDER BY epochDay")
    fun observeDailyKcal(): Flow<List<DayKcal>>

    @Query("SELECT ref FROM food_log WHERE grams IS NOT NULL GROUP BY ref ORDER BY MAX(atMs) DESC LIMIT :limit")
    fun observeRecentRefs(limit: Int): Flow<List<String>>

    @Insert
    suspend fun insertLog(e: FoodLogEntity): Long

    @Insert
    suspend fun insertLogs(e: List<FoodLogEntity>)

    @Update
    suspend fun updateLog(e: FoodLogEntity)

    @Query("DELETE FROM food_log WHERE id = :id")
    suspend fun deleteLog(id: Long)

    @Query("SELECT * FROM weight ORDER BY epochDay")
    fun observeWeights(): Flow<List<WeightEntity>>

    @Query("SELECT * FROM weight WHERE epochDay = :epochDay")
    suspend fun weight(epochDay: Long): WeightEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeight(e: WeightEntity)

    @Query("DELETE FROM weight WHERE epochDay = :epochDay")
    suspend fun deleteWeight(epochDay: Long)

    @Query("SELECT * FROM fuel_day WHERE incomplete = 1")
    fun observeIncomplete(): Flow<List<FuelDayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFuelDay(e: FuelDayEntity)
}

@Database(
    entities = [
        SleepEntity::class, SkippedNightEntity::class, OrderLogEntity::class, RatingEntity::class,
        FoodEntity::class, FoodLogEntity::class, WeightEntity::class, FuelDayEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class CommanderDb : RoomDatabase() {
    abstract fun dao(): CommanderDao

    companion object {
        fun open(context: Context, name: String = "commander.db"): CommanderDb =
            Room.databaseBuilder(context.applicationContext, CommanderDb::class.java, name)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

/** Version 2 adds Fuel's tables; nothing existing changes, so sleep history carries over. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `food` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`kcal` REAL NOT NULL, `protein` REAL NOT NULL, `fat` REAL NOT NULL, `carb` REAL NOT NULL, " +
                "`fiber` REAL NOT NULL, `servings` TEXT NOT NULL, `diet` TEXT NOT NULL, `ingredients` TEXT, " +
                "`cookedG` REAL, `portions` INTEGER, `archived` INTEGER NOT NULL, `createdMs` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `food_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`epochDay` INTEGER NOT NULL, `meal` TEXT NOT NULL, `ref` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`grams` REAL, `amount` TEXT NOT NULL, `kcal` REAL NOT NULL, `protein` REAL NOT NULL, " +
                "`fat` REAL NOT NULL, `carb` REAL NOT NULL, `fiber` REAL NOT NULL, `atMs` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `weight` (`epochDay` INTEGER NOT NULL, `kg` REAL NOT NULL, " +
                "`atMs` INTEGER NOT NULL, PRIMARY KEY(`epochDay`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `fuel_day` (`epochDay` INTEGER NOT NULL, `incomplete` INTEGER NOT NULL, " +
                "PRIMARY KEY(`epochDay`))",
        )
    }
}
