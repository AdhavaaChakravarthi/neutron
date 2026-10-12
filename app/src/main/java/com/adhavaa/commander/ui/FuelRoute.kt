package com.adhavaa.commander.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adhavaa.commander.FuelViewModel
import com.adhavaa.commander.data.RecipeDraft
import com.adhavaa.commander.fuel.Food
import com.adhavaa.commander.fuel.Ingredient
import com.adhavaa.commander.fuel.LogEntry
import com.adhavaa.commander.fuel.Meal
import com.adhavaa.commander.fuel.defaultFilter
import kotlinx.coroutines.launch

/** Full-screen pages that open over the Fuel tab. */
private sealed interface FuelPage {
    data object Setup : FuelPage
    data class Add(val meal: Meal) : FuelPage
    data class EditFood(val food: Food?) : FuelPage
    data class EditRecipe(val draft: RecipeDraft?) : FuelPage
}

/** The Fuel tab with its state, pages and dialogs. [onOpenSettings] opens Commander's settings. */
@Composable
fun FuelRoute(onOpenSettings: () -> Unit, onFullScreen: (Boolean) -> Unit, vm: FuelViewModel = viewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf<FuelPage?>(null) }
    var pickIngredient by remember { mutableStateOf<((Ingredient) -> Unit)?>(null) }
    var quickAdd by remember { mutableStateOf<Meal?>(null) }
    var editEntry by remember { mutableStateOf<LogEntry?>(null) }
    var editEntryFood by remember { mutableStateOf<Food?>(null) }
    var weightOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.preload() }
    LaunchedEffect(page, pickIngredient) { onFullScreen(page != null || pickIngredient != null) }

    val u = ui ?: run {
        Column(Modifier.fillMaxSize().background(Hud.bg)) {}
        return
    }
    val date = u.picture.date

    // Ingredient picking sits on top of the recipe editor, which keeps its state underneath.
    val picking = pickIngredient
    if (picking != null) {
        AddFoodScreen(
            title = "Add ingredient",
            meal = null,
            initialFilter = defaultFilter(u.settings, date).copy(fruitOnly = false),
            dayType = com.adhavaa.commander.fuel.DayType.NORMAL,
            myFoods = u.myFoods.filter { !it.recipe },
            search = vm::search,
            onChosen = { f, g, _, _ ->
                picking(Ingredient(f, g))
                pickIngredient = null
            },
            onBack = { pickIngredient = null },
        )
        return
    }

    when (val p = page) {
        FuelPage.Setup -> FuelSetupScreen(u.settings, u.picture.today, { s, w -> vm.saveSetup(s, w) }, { page = null })
        is FuelPage.Add -> AddFoodScreen(
            title = "Add to ${p.meal.label}",
            meal = p.meal,
            initialFilter = defaultFilter(u.settings, date),
            dayType = u.picture.dayType,
            myFoods = u.myFoods,
            search = vm::search,
            onChosen = { f, g, amount, m -> vm.log(date, m, f, g, amount) },
            onBack = { page = null },
            onNewFood = { page = FuelPage.EditFood(null) },
            onNewRecipe = { page = FuelPage.EditRecipe(null) },
            onEditFood = { f ->
                if (f.recipe) {
                    scope.launch { page = FuelPage.EditRecipe(vm.recipe(f.ref.removePrefix("food:").toLong())) }
                } else {
                    page = FuelPage.EditFood(f)
                }
            },
        )
        is FuelPage.EditFood -> FoodEditor(
            existing = p.food,
            onSave = { name, per100, servings, diet -> vm.saveFood(p.food?.ref?.removePrefix("food:")?.toLong(), name, per100, servings, diet) },
            onDelete = p.food?.let { f -> { vm.archiveFood(f.ref.removePrefix("food:").toLong()) } },
            onBack = { page = FuelPage.Add(defaultMeal()) },
        )
        is FuelPage.EditRecipe -> RecipeEditor(
            existing = p.draft,
            onPickIngredient = { cb -> pickIngredient = cb },
            onSave = vm::saveRecipe,
            onDelete = p.draft?.id?.let { id -> { vm.archiveFood(id) } },
            onBack = { page = FuelPage.Add(defaultMeal()) },
        )
        null -> FuelScreen(
            u,
            FuelActions(
                onDate = vm::selectDate,
                onAddFood = { page = FuelPage.Add(it) },
                onQuickAdd = { quickAdd = it },
                onEditEntry = { e ->
                    if (e.grams == null) {
                        editEntry = e
                    } else {
                        scope.launch {
                            editEntryFood = vm.resolve(e) ?: snapshotFood(e)
                            editEntry = e
                        }
                    }
                },
                onCopyYesterday = { vm.copyDay(date.minusDays(1), date) },
                onLogWeight = { weightOpen = true },
                onIncomplete = { vm.setIncomplete(date, it) },
                onSetup = { page = FuelPage.Setup },
                onOpenSettings = onOpenSettings,
            ),
        )
    }

    quickAdd?.let { meal ->
        QuickAddDialog(meal, onDone = { label, n, m -> vm.quickAdd(date, m, label, n) }, onDismiss = { quickAdd = null })
    }
    editEntry?.let { e ->
        if (e.grams == null) {
            QuickAddDialog(
                e.meal, existing = e,
                onDone = { label, n, m -> vm.updateQuick(e, m, label, n) },
                onDismiss = { editEntry = null },
                onDelete = { vm.deleteEntry(e.id) },
            )
        } else {
            editEntryFood?.let { f ->
                AmountDialog(
                    food = f,
                    initialMeal = e.meal,
                    initialGrams = e.grams,
                    confirmLabel = "Save",
                    onDone = { g, amount, m -> vm.updateEntry(e, m, g, amount) },
                    onDelete = { vm.deleteEntry(e.id) },
                    onDismiss = { editEntry = null; editEntryFood = null },
                )
            }
        }
    }
    if (weightOpen) {
        WeightDialog(
            date = date,
            existing = u.picture.weightOnDate,
            lastKg = u.picture.trendKg,
            onSave = { vm.setWeight(date, it) },
            onDelete = { vm.deleteWeight(date) },
            onDismiss = { weightOpen = false },
        )
    }
}

/** When a logged food no longer exists (a removed custom food), edit it from the entry's own numbers. */
private fun snapshotFood(e: LogEntry): Food {
    val g = e.grams ?: 100.0
    return Food(e.ref, e.name, "", e.n * (100.0 / g), emptyList(), com.adhavaa.commander.fuel.Diet.PLANT)
}
