package com.adhavaa.commander.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.adhavaa.commander.data.db.CommanderDb
import com.adhavaa.commander.fuel.Diet
import com.adhavaa.commander.fuel.Food
import com.adhavaa.commander.fuel.Ingredient
import com.adhavaa.commander.fuel.Meal
import com.adhavaa.commander.fuel.Nutrients
import com.adhavaa.commander.fuel.Serving
import com.adhavaa.commander.model.WeighIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FuelRepositoryTest {
    private val app = RuntimeEnvironment.getApplication()
    private val db = Room.inMemoryDatabaseBuilder(app, CommanderDb::class.java).allowMainThreadQueries().build()
    private val rice = Food("usda:20044", "Rice, white, long-grain, regular, enriched, cooked", "Cereal Grains and Pasta",
        Nutrients(130.0, 2.69, 0.28, 28.17, 0.4), listOf(Serving("cup", 158.0)), Diet.PLANT)
    private val dal = Food("usda:16070", "Lentils, mature seeds, cooked, boiled, without salt", "Legumes and Legume Products",
        Nutrients(116.0, 9.02, 0.38, 20.13, 7.9), listOf(Serving("cup", 198.0)), Diet.PLANT)
    private val repo = FuelRepository(db, FoodCatalog { listOf(rice, dal) })
    private val day = LocalDate.of(2026, 10, 14)

    @After
    fun close() = db.close()

    @Test
    fun logsTotalsAndEdits() = runBlocking {
        repo.log(day, Meal.LUNCH, rice, 158.0, "1 cup (158 g)")
        repo.log(day, Meal.LUNCH, dal, 198.0, "1 cup (198 g)")
        repo.quickAdd(day, Meal.SNACKS, "Tea", Nutrients(60.0, 2.0, 2.0, 8.0))
        val log = repo.observeLog(day).first()
        assertEquals(3, log.size)
        assertEquals(130.0 * 1.58 + 116.0 * 1.98 + 60.0, repo.observeDailyKcal().first().getValue(day), 1e-9)

        val r = log.first { it.ref == rice.ref }
        repo.updateEntry(r, Meal.DINNER, 79.0, "0.5 cup (79 g)")
        val changed = repo.observeLog(day).first().first { it.id == r.id }
        assertEquals(Meal.DINNER, changed.meal)
        assertEquals(130.0 * 0.79, changed.n.kcal, 1e-9)

        repo.deleteEntry(log.first { it.ref == "quick" }.id)
        assertEquals(2, repo.observeLog(day).first().size)
    }

    @Test
    fun copiesYesterday() = runBlocking {
        repo.log(day, Meal.BREAKFAST, rice, 100.0, "100 g")
        repo.log(day, Meal.LUNCH, dal, 200.0, "200 g")
        assertEquals(2, repo.copyDay(day, day.plusDays(1)))
        val copied = repo.observeLog(day.plusDays(1)).first()
        assertEquals(listOf(Meal.BREAKFAST, Meal.LUNCH), copied.map { it.meal })
        assertEquals(2, repo.observeLog(day).first().size)
    }

    @Test
    fun oneWeighInPerDay() = runBlocking {
        repo.setWeight(day, 85.2)
        repo.setWeight(day, 84.9)
        repo.setWeight(day.plusDays(1), 84.7)
        assertEquals(listOf(WeighIn(day, 84.9), WeighIn(day.plusDays(1), 84.7)), repo.observeWeights().first())
        assertEquals(84.9, repo.weightOn(day)!!, 0.0)
        repo.deleteWeight(day)
        assertNull(repo.weightOn(day))
    }

    @Test
    fun notFullyLoggedDays() = runBlocking {
        repo.setIncomplete(day, true)
        assertEquals(setOf(day), repo.observeIncomplete().first())
        repo.setIncomplete(day, false)
        assertTrue(repo.observeIncomplete().first().isEmpty())
    }

    @Test
    fun yourFoodsAndRecipes() = runBlocking {
        val id = repo.saveFood(null, "Idli (home)", Nutrients(140.0, 4.0, 0.5, 30.0, 1.0), "piece=40", Diet.PLANT)
        val idli = repo.observeFoods().first().single()
        assertEquals("food:$id", idli.ref)
        assertEquals(40.0, idli.servings.single().grams, 0.0)
        assertEquals(idli, repo.resolve("food:$id"))
        assertEquals(rice, repo.resolve(rice.ref))

        val rid = repo.saveRecipe(RecipeDraft(null, "Dal rice", listOf(Ingredient(rice, 300.0), Ingredient(dal, 200.0), Ingredient(idli, 40.0)), 600.0, 3))
        val back = repo.recipe(rid)!!
        assertEquals(listOf(300.0, 200.0, 40.0), back.ingredients.map { it.grams })
        assertEquals(listOf(rice.ref, dal.ref, idli.ref), back.ingredients.map { it.food.ref })
        val recipe = repo.observeFoods().first().first { it.recipe }
        assertEquals((130.0 * 3 + 116.0 * 2 + 140.0 * 0.4) * 100 / 600, recipe.per100.kcal, 1e-9)
        assertEquals(200.0, recipe.servings.first().grams, 1e-9)

        repo.archiveFood(id)
        assertEquals(listOf("Dal rice"), repo.observeFoods().first().map { it.name })
        assertNull(repo.recipe(id))
    }

    @Test
    fun recentFoodsAreMostRecentFirst() = runBlocking {
        repo.log(day, Meal.LUNCH, rice, 100.0, "100 g")
        Thread.sleep(5)
        repo.log(day, Meal.LUNCH, dal, 100.0, "100 g")
        repo.quickAdd(day, Meal.SNACKS, "Tea", Nutrients(60.0))
        assertEquals(listOf(dal.ref, rice.ref), repo.observeRecent().first())
    }
}

/** Opening a version-1 database (sleep only) with this build keeps the sleep data and adds Fuel's tables. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    @Test
    fun version1To2KeepsSleep() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val name = "migration-test.db"
        app.deleteDatabase(name)
        val file = app.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { v1 ->
            // Exactly the tables Room created for version 1.
            v1.execSQL("CREATE TABLE IF NOT EXISTS `sleep` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, `kind` TEXT NOT NULL, `source` TEXT NOT NULL, `confirmed` INTEGER NOT NULL, `wokeNaturally` INTEGER, `createdMs` INTEGER NOT NULL)")
            v1.execSQL("CREATE TABLE IF NOT EXISTS `skipped_night` (`epochDay` INTEGER NOT NULL, PRIMARY KEY(`epochDay`))")
            v1.execSQL("CREATE TABLE IF NOT EXISTS `order_log` (`epochDay` INTEGER NOT NULL, `kind` TEXT NOT NULL, `status` TEXT NOT NULL, `atMs` INTEGER NOT NULL, PRIMARY KEY(`epochDay`, `kind`))")
            v1.execSQL("CREATE TABLE IF NOT EXISTS `rating` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `atMs` INTEGER NOT NULL, `kss` INTEGER NOT NULL)")
            v1.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            v1.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'version-1')")
            v1.execSQL("INSERT INTO sleep (startMs, endMs, kind, source, confirmed, wokeNaturally, createdMs) VALUES (1000, 29801000, 'NIGHT', 'MANUAL', 1, 1, 1000)")
            v1.version = 1
        }
        val db = CommanderDb.open(app, name)
        try {
            val dao = db.dao()
            assertEquals(1, dao.sleepSince(0).size)
            val repo = FuelRepository(db, FoodCatalog { emptyList() })
            repo.setWeight(LocalDate.of(2026, 10, 14), 85.0)
            repo.quickAdd(LocalDate.of(2026, 10, 14), Meal.LUNCH, "Lunch", Nutrients(700.0))
            assertNotNull(repo.weightOn(LocalDate.of(2026, 10, 14)))
            assertEquals(700.0, repo.observeDailyKcal().first().values.single(), 0.0)
        } finally {
            db.close()
            app.deleteDatabase(name)
        }
    }
}
