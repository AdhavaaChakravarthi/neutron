package com.adhavaa.commander.data

import android.content.Context
import androidx.room.withTransaction
import com.adhavaa.commander.data.db.CommanderDb
import com.adhavaa.commander.data.db.FoodEntity
import com.adhavaa.commander.data.db.FoodLogEntity
import com.adhavaa.commander.data.db.FuelDayEntity
import com.adhavaa.commander.data.db.WeightEntity
import com.adhavaa.commander.fuel.Diet
import com.adhavaa.commander.fuel.Food
import com.adhavaa.commander.fuel.Indexed
import com.adhavaa.commander.fuel.Ingredient
import com.adhavaa.commander.fuel.LogEntry
import com.adhavaa.commander.fuel.Meal
import com.adhavaa.commander.fuel.Nutrients
import com.adhavaa.commander.fuel.CUSTOM_GROUP
import com.adhavaa.commander.fuel.formatServings
import com.adhavaa.commander.fuel.index
import com.adhavaa.commander.fuel.readCatalog
import com.adhavaa.commander.fuel.parseServings
import com.adhavaa.commander.fuel.recipeFood
import com.adhavaa.commander.model.WeighIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.io.FileNotFoundException
import kotlin.coroutines.cancellation.CancellationException

/** A recipe as stored: its ingredients by reference, so it can be opened and edited again. */
data class RecipeDraft(
    val id: Long?,
    val name: String,
    val ingredients: List<Ingredient>,
    val cookedG: Double?,
    val portions: Int,
)

/** Food log, your foods and recipes, weigh-ins and "not fully logged" days. */
class FuelRepository(private val db: CommanderDb, private val catalog: FoodCatalog) {
    private val dao = db.dao()

    private fun toEntry(e: FoodLogEntity) = LogEntry(
        id = e.id,
        date = LocalDate.ofEpochDay(e.epochDay),
        meal = Meal.entries.firstOrNull { it.name == e.meal } ?: Meal.SNACKS,
        ref = e.ref,
        name = e.name,
        grams = e.grams,
        amount = e.amount,
        n = Nutrients(e.kcal, e.protein, e.fat, e.carb, e.fiber),
    )

    private fun toFood(e: FoodEntity) = Food(
        ref = "food:${e.id}",
        name = e.name,
        group = CUSTOM_GROUP,
        per100 = Nutrients(e.kcal, e.protein, e.fat, e.carb, e.fiber),
        servings = parseServings(e.servings),
        diet = Diet.entries.firstOrNull { it.name == e.diet } ?: Diet.PLANT,
        recipe = e.ingredients != null,
    )

    fun observeLog(date: LocalDate): Flow<List<LogEntry>> = dao.observeLog(date.toEpochDay()).map { l -> l.map(::toEntry) }

    fun observeDailyKcal(): Flow<Map<LocalDate, Double>> =
        dao.observeDailyKcal().map { l -> l.associate { LocalDate.ofEpochDay(it.epochDay) to it.kcal } }

    fun observeWeights(): Flow<List<WeighIn>> =
        dao.observeWeights().map { l -> l.map { WeighIn(LocalDate.ofEpochDay(it.epochDay), it.kg) } }

    fun observeIncomplete(): Flow<Set<LocalDate>> =
        dao.observeIncomplete().map { l -> l.mapTo(HashSet()) { LocalDate.ofEpochDay(it.epochDay) } }

    fun observeFoods(): Flow<List<Food>> = dao.observeFoods().map { l -> l.map(::toFood) }

    fun observeRecent(limit: Int = 40): Flow<List<String>> = dao.observeRecentRefs(limit)

    suspend fun log(date: LocalDate, meal: Meal, food: Food, grams: Double, amount: String) {
        val n = food.per100 * (grams / 100.0)
        dao.insertLog(entity(0, date, meal, food.ref, food.name, grams, amount, n))
    }

    suspend fun quickAdd(date: LocalDate, meal: Meal, label: String, n: Nutrients) {
        dao.insertLog(entity(0, date, meal, "quick", label.ifBlank { "Quick add" }, null, "${n.kcal.toInt()} kcal", n))
    }

    /** Changes an entry's amount and meal; nutrients scale with the grams. */
    suspend fun updateEntry(e: LogEntry, meal: Meal, grams: Double?, amount: String) {
        val n = if (grams != null && e.grams != null && e.grams > 0) e.n * (grams / e.grams) else e.n
        dao.updateLog(entity(e.id, e.date, meal, e.ref, e.name, grams ?: e.grams, amount, n))
    }

    suspend fun updateQuick(e: LogEntry, meal: Meal, label: String, n: Nutrients) {
        dao.updateLog(entity(e.id, e.date, meal, e.ref, label.ifBlank { "Quick add" }, null, "${n.kcal.toInt()} kcal", n))
    }

    suspend fun deleteEntry(id: Long) = dao.deleteLog(id)

    /** Copies every entry from [from] onto [to]. Returns how many were copied. */
    suspend fun copyDay(from: LocalDate, to: LocalDate): Int = db.withTransaction {
        val src = dao.log(from.toEpochDay())
        val now = System.currentTimeMillis()
        dao.insertLogs(src.mapIndexed { i, e -> e.copy(id = 0, epochDay = to.toEpochDay(), atMs = now + i) })
        src.size
    }

    suspend fun setWeight(date: LocalDate, kg: Double) =
        dao.upsertWeight(WeightEntity(date.toEpochDay(), kg, System.currentTimeMillis()))

    suspend fun deleteWeight(date: LocalDate) = dao.deleteWeight(date.toEpochDay())

    suspend fun weightOn(date: LocalDate): Double? = dao.weight(date.toEpochDay())?.kg

    suspend fun setIncomplete(date: LocalDate, incomplete: Boolean) =
        dao.upsertFuelDay(FuelDayEntity(date.toEpochDay(), incomplete))

    /** Saves one of your foods (nutrients per 100 g). Returns its id. */
    suspend fun saveFood(id: Long?, name: String, per100: Nutrients, servings: String, diet: Diet): Long {
        val e = FoodEntity(
            id = id ?: 0, name = name.trim(), kcal = per100.kcal, protein = per100.protein, fat = per100.fat,
            carb = per100.carb, fiber = per100.fiber, servings = servings, diet = diet.name, ingredients = null,
            cookedG = null, portions = null, archived = false, createdMs = System.currentTimeMillis(),
        )
        return if (id == null) dao.insertFood(e) else e.id.also { dao.updateFood(e) }
    }

    suspend fun saveRecipe(r: RecipeDraft): Long {
        val id = r.id ?: dao.insertFood(
            FoodEntity(
                name = r.name, kcal = 0.0, protein = 0.0, fat = 0.0, carb = 0.0, fiber = 0.0, servings = "", diet = Diet.PLANT.name,
                ingredients = "", cookedG = null, portions = null, archived = false, createdMs = System.currentTimeMillis(),
            ),
        )
        val f = recipeFood(id, r.name.trim(), r.ingredients, r.cookedG, r.portions)
        dao.updateFood(
            FoodEntity(
                id = id, name = f.name, kcal = f.per100.kcal, protein = f.per100.protein, fat = f.per100.fat,
                carb = f.per100.carb, fiber = f.per100.fiber, servings = formatServings(f.servings), diet = f.diet.name,
                ingredients = r.ingredients.joinToString("|") { "${it.food.ref}=${it.grams}" },
                cookedG = r.cookedG, portions = r.portions, archived = false, createdMs = System.currentTimeMillis(),
            ),
        )
        return id
    }

    /** A saved recipe with its ingredients resolved, or null if it isn't a recipe. */
    suspend fun recipe(id: Long): RecipeDraft? {
        val e = dao.food(id) ?: return null
        val raw = e.ingredients ?: return null
        val list = raw.split('|').filter { it.isNotBlank() }.mapNotNull { part ->
            val ref = part.substringBeforeLast('=')
            val grams = part.substringAfterLast('=').toDoubleOrNull() ?: return@mapNotNull null
            resolve(ref)?.let { Ingredient(it, grams) }
        }
        return RecipeDraft(id, e.name, list, e.cookedG, e.portions ?: 1)
    }

    suspend fun archiveFood(id: Long) = dao.archiveFood(id)

    suspend fun resolve(ref: String): Food? = when {
        ref.startsWith("usda:") -> catalog.foods().firstOrNull { it.food.ref == ref }?.food
        ref.startsWith("food:") -> ref.removePrefix("food:").toLongOrNull()?.let { dao.food(it) }?.let(::toFood)
        else -> null
    }

    private fun entity(id: Long, date: LocalDate, meal: Meal, ref: String, name: String, grams: Double?, amount: String, n: Nutrients) =
        FoodLogEntity(
            id = id, epochDay = date.toEpochDay(), meal = meal.name, ref = ref, name = name, grams = grams, amount = amount,
            kcal = n.kcal, protein = n.protein, fat = n.fat, carb = n.carb, fiber = n.fiber, atMs = System.currentTimeMillis(),
        )
}

/** Asset names the food list may have in the APK (the build unpacks .gz assets), plain first. */
val CATALOG_ASSETS = listOf("foods.tsv", "foods.tsv.gz")

/**
 * The USDA foods bundled with the app (about 7,400 rows), loaded once on first use. A failed load never crashes:
 * search falls back to your own foods and [error] says why.
 */
class FoodCatalog(private val load: () -> List<Food>) {
    private val lock = Mutex()
    @Volatile private var cache: List<Indexed>? = null

    @Volatile
    var error: String? = null
        private set

    // The lock is taken and released on an IO thread, so a holder never needs the main thread to let go (a caller
    // blocking the main thread can't deadlock against a load started from viewModelScope).
    suspend fun foods(): List<Indexed> = cache ?: withContext(Dispatchers.IO) {
        lock.withLock {
            cache ?: try {
                index(load()).also {
                    cache = it
                    error = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "The food list couldn't load (${e.javaClass.simpleName}). Your own foods and quick add still work."
                emptyList()
            }
        }
    }

    companion object {
        fun fromAssets(context: Context): FoodCatalog = FoodCatalog { loadAsset(context) }

        private fun loadAsset(context: Context): List<Food> {
            for (name in CATALOG_ASSETS) {
                val stream = try {
                    context.assets.open(name)
                } catch (e: FileNotFoundException) {
                    null
                } ?: continue
                return stream.use { readCatalog(it) }
            }
            throw FileNotFoundException(CATALOG_ASSETS.joinToString(" or "))
        }
    }
}
