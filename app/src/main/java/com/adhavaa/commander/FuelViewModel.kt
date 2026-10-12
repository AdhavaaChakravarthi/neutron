package com.adhavaa.commander

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adhavaa.commander.data.FuelSettings
import com.adhavaa.commander.data.RecipeDraft
import com.adhavaa.commander.fuel.Diet
import com.adhavaa.commander.fuel.Food
import com.adhavaa.commander.fuel.FuelData
import com.adhavaa.commander.fuel.FuelPicture
import com.adhavaa.commander.fuel.LogEntry
import com.adhavaa.commander.fuel.Meal
import com.adhavaa.commander.fuel.Nutrients
import com.adhavaa.commander.fuel.SearchFilter
import com.adhavaa.commander.fuel.fuelPicture
import com.adhavaa.commander.fuel.index
import com.adhavaa.commander.fuel.searchFoods
import com.adhavaa.commander.fuel.totals
import com.adhavaa.commander.model.WeighIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Everything the Fuel tab draws. */
data class FuelUi(
    val settings: FuelSettings,
    val picture: FuelPicture,
    val entries: List<LogEntry>,
    val eaten: Nutrients,
    val weighIns: List<WeighIn>,
    val trend: List<Pair<LocalDate, Double>>,
    val myFoods: List<Food>,
    val recent: List<String>,
)

class FuelViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container()

    private val today: Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000)
        }
    }.distinctUntilChanged()

    /** The day on screen; null follows today. */
    private val chosen = MutableStateFlow<LocalDate?>(null)

    private val data: Flow<FuelData> = combine(
        c.settings.settings.map { it.fuel }, c.fuel.observeWeights(), c.fuel.observeDailyKcal(), c.fuel.observeIncomplete(),
    ) { s, w, k, inc -> FuelData(s, w, k, inc) }.distinctUntilChanged()

    private val myFoods = c.fuel.observeFoods()
    private val recent = c.fuel.observeRecent()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<FuelUi?> = combine(today, chosen) { t, d -> t to (d ?: t) }
        .flatMapLatest { (t, d) ->
            combine(data, c.fuel.observeLog(d), myFoods, recent) { fd, log, foods, rec ->
                FuelUi(
                    settings = fd.settings,
                    picture = fuelPicture(fd, d, t),
                    entries = log,
                    eaten = totals(log),
                    weighIns = fd.weighIns,
                    trend = fd.trend().entries.map { it.key to it.value },
                    myFoods = foods,
                    recent = rec,
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectDate(date: LocalDate) {
        chosen.value = if (date == LocalDate.now()) null else date
    }

    /** Your foods and recipes first, then USDA foods, filtered and ranked (MODEL.md section 10). */
    suspend fun search(query: String, filter: SearchFilter): List<Food> = withContext(Dispatchers.Default) {
        val mine = index(myFoods.first())
        val recentRefs = recent.first()
        searchFoods(query, mine + c.catalog.foods(), filter, recentRefs)
    }

    /** Why the USDA list isn't available, if it isn't. */
    fun catalogError(): String? = c.catalog.error

    /** Warms the food list so the first search is quick. */
    fun preload() {
        viewModelScope.launch { c.catalog.foods() }
    }

    fun log(date: LocalDate, meal: Meal, food: Food, grams: Double, amount: String) = io { c.fuel.log(date, meal, food, grams, amount) }

    fun quickAdd(date: LocalDate, meal: Meal, label: String, n: Nutrients) = io { c.fuel.quickAdd(date, meal, label, n) }

    fun updateEntry(e: LogEntry, meal: Meal, grams: Double?, amount: String) = io { c.fuel.updateEntry(e, meal, grams, amount) }

    fun updateQuick(e: LogEntry, meal: Meal, label: String, n: Nutrients) = io { c.fuel.updateQuick(e, meal, label, n) }

    fun deleteEntry(id: Long) = io { c.fuel.deleteEntry(id) }

    fun copyDay(from: LocalDate, to: LocalDate) = io { c.fuel.copyDay(from, to) }

    fun setWeight(date: LocalDate, kg: Double) = io { c.fuel.setWeight(date, kg) }

    fun deleteWeight(date: LocalDate) = io { c.fuel.deleteWeight(date) }

    fun setIncomplete(date: LocalDate, incomplete: Boolean) = io { c.fuel.setIncomplete(date, incomplete) }

    fun saveFood(id: Long?, name: String, per100: Nutrients, servings: String, diet: Diet) =
        io { c.fuel.saveFood(id, name, per100, servings, diet) }

    fun saveRecipe(r: RecipeDraft) = io { c.fuel.saveRecipe(r) }

    suspend fun recipe(id: Long): RecipeDraft? = withContext(Dispatchers.IO) { c.fuel.recipe(id) }

    fun archiveFood(id: Long) = io { c.fuel.archiveFood(id) }

    /** The food behind a log entry, with the entry's own numbers (a snapshot) and the food's servings. */
    suspend fun resolve(e: LogEntry): Food? = withContext(Dispatchers.IO) {
        val g = e.grams ?: return@withContext null
        c.fuel.resolve(e.ref)?.copy(per100 = e.n * (100.0 / g))
    }

    /** Saves the profile; on first setup the start weight is also today's weigh-in. */
    fun saveSetup(f: FuelSettings, weightKg: Double?) = io {
        val today = LocalDate.now()
        val first = !c.settings.settings.first().fuel.setUp
        c.settings.setFuel(f.copy(startWeightKg = weightKg ?: f.startWeightKg), today)
        if (first && weightKg != null) c.fuel.setWeight(today, weightKg)
    }

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { block() }
    }
}
