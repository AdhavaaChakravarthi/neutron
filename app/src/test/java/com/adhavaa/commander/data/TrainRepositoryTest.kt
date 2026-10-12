package com.adhavaa.commander.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.adhavaa.commander.data.db.CommanderDb
import com.adhavaa.commander.train.StandDown
import com.adhavaa.commander.train.WorkoutKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrainRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), CommanderDb::class.java).allowMainThreadQueries().build()
    private val repo = TrainRepository(db)
    private val day = LocalDate.of(2026, 10, 12)

    @After
    fun close() = db.close()

    @Test
    fun liftSetsAndFinish() = runBlocking {
        val id = repo.startLift(day, "Lower A")
        assertEquals(id, repo.startLift(day, "Lower A"))
        repo.logSet(id, day, "Barbell_Squat", 100.0, 8, 2)
        repo.logSet(id, day, "Barbell_Squat", 100.0, 8, 1)
        repo.logSet(id, day, "Romanian_Deadlift", 80.0, 10, null)
        val sets = repo.observeSets().first()
        assertEquals(listOf(0, 1, 0), sets.map { it.setIndex })
        assertFalse(repo.observeWorkouts().first().single().ended)
        repo.finishLift(id, 7.0)
        val w = repo.observeWorkouts().first().single()
        assertTrue(w.ended)
        assertEquals(7.0, w.rpe!!, 0.0)
        assertTrue(w.minutes >= 1.0)
        repo.updateSet(sets[1], 102.5, 6, 0)
        assertEquals(102.5, repo.observeSets().first()[1].weight, 0.0)
        repo.deleteWorkout(id)
        assertTrue(repo.observeSets().first().isEmpty())
        assertTrue(repo.observeWorkouts().first().isEmpty())
    }

    @Test
    fun aStaleLiftIsClosedWhenTheNextStarts() = runBlocking {
        val old = repo.startLift(day.minusDays(3), "Upper B")
        repo.logSet(old, day.minusDays(3), "Pullups", 0.0, 8, null)
        val new = repo.startLift(day, "Lower A")
        val all = repo.observeWorkouts().first()
        assertTrue(all.first { it.id == old }.ended)
        assertFalse(all.first { it.id == new }.ended)
    }

    @Test
    fun sessionsAndStandDown() = runBlocking {
        val id = repo.logSession(day, WorkoutKind.BIKE, "Bike", 45.0, 18.5, 6.0, false)
        val w = repo.observeWorkouts().first().single()
        assertEquals(WorkoutKind.BIKE, w.kind)
        repo.updateSession(w, 50.0, 20.0, 5.0, WorkoutKind.RUN, true)
        val u = repo.observeWorkouts().first().single()
        assertEquals(WorkoutKind.RUN, u.kind)
        assertTrue(u.brick)
        assertEquals(id, u.id)
        assertFalse(repo.mobilityDoneOn(day))
        repo.logSession(day, WorkoutKind.MOBILITY, "Recovery stretch", 8.0, null, null, false)
        assertTrue(repo.mobilityDoneOn(day))
        repo.setStandDown(day, StandDown.ILLNESS)
        assertEquals(mapOf(day to StandDown.ILLNESS), repo.observeStandDowns().first())
        repo.setStandDown(day, null)
        assertTrue(repo.observeStandDowns().first().isEmpty())
    }
}

/** A version-2 database (sleep and Fuel) opens in this build with its data kept and Train's tables added. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration2To3Test {
    @Test
    fun version2To3KeepsFuel() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val name = "migration-v2-test.db"
        app.deleteDatabase(name)
        val schema = listOf(
            File("schemas/com.adhavaa.commander.data.db.CommanderDb/2.json"),
            File("app/schemas/com.adhavaa.commander.data.db.CommanderDb/2.json"),
        ).first { it.exists() }
        val entities = JSONObject(schema.readText()).getJSONObject("database").getJSONArray("entities")
        val file = app.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { v2 ->
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                v2.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
            }
            v2.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            v2.execSQL("INSERT INTO weight (epochDay, kg, atMs) VALUES (20738, 85.0, 1)")
            v2.version = 2
        }
        val db = CommanderDb.open(app, name)
        try {
            assertEquals(85.0, FuelRepository(db, FoodCatalog { emptyList() }).weightOn(LocalDate.ofEpochDay(20738))!!, 0.0)
            val train = TrainRepository(db)
            train.logSession(LocalDate.ofEpochDay(20738), WorkoutKind.SWIM, "Swim", 30.0, 1.2, 3.0, false)
            assertEquals(1, train.observeWorkouts().first().size)
        } finally {
            db.close()
            app.deleteDatabase(name)
        }
    }
}

/** The exercise list as the app loads it from the packaged assets. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExerciseCatalogTest {
    @Test
    fun loadsFromPackagedAssets() = runBlocking {
        val c = ExerciseCatalog.fromAssets(RuntimeEnvironment.getApplication())
        val all = c.all()
        assertNull(c.error)
        assertEquals(876, all.size)
        assertTrue(com.adhavaa.commander.train.PROGRAM_EXERCISE_IDS.all { it in all })
    }

    @Test
    fun aBrokenListDoesNotCrash() = runBlocking {
        val c = ExerciseCatalog { throw java.io.IOException("disk") }
        assertTrue(c.all().isEmpty())
        assertTrue(c.error != null)
    }
}
