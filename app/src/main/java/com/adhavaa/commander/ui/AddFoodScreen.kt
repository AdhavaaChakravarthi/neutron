package com.adhavaa.commander.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adhavaa.commander.fuel.DayType
import com.adhavaa.commander.fuel.Food
import com.adhavaa.commander.fuel.Meal
import com.adhavaa.commander.fuel.SearchFilter
import com.adhavaa.commander.orders.kcalText
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Search your foods and the USDA list, then pick an amount. In log mode ([meal] set) it stays open so a meal of
 * several foods is quick to enter; in ingredient mode it returns after one pick.
 */
@Composable
fun AddFoodScreen(
    title: String,
    meal: Meal?,
    initialFilter: SearchFilter,
    dayType: DayType,
    myFoods: List<Food>,
    search: suspend (String, SearchFilter) -> List<Food>,
    onChosen: (food: Food, grams: Double, amount: String, meal: Meal) -> Unit,
    onBack: () -> Unit,
    onNewFood: (() -> Unit)? = null,
    onNewRecipe: (() -> Unit)? = null,
    onEditFood: ((Food) -> Unit)? = null,
    initialQuery: String = "",
    initialResults: List<Food>? = null,
    /** Read after each search: a problem with the food list to show, if any. */
    notice: () -> String? = { null },
) {
    BackHandler(onBack = onBack)
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var filter by remember { mutableStateOf(initialFilter) }
    var results by remember { mutableStateOf(initialResults ?: emptyList()) }
    var loading by remember { mutableStateOf(initialResults == null) }
    var picked by remember { mutableStateOf<Food?>(null) }
    var lastLogged by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query, filter) {
        if (initialResults != null && query == initialQuery && filter == initialFilter) return@LaunchedEffect
        delay(150)
        results = search(query, filter)
        problem = notice()
        loading = false
    }
    val mine = if (query.isBlank()) myFoods.filter { filter.allows(it) } else emptyList()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Hud.bg)
            .imePadding(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = insets.calculateTopPadding() + 8.dp, bottom = insets.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SquareButton("Back", onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = Hud.muted, modifier = Modifier.size(18.dp))
                }
                Text(title.uppercase(), style = HudType.title, color = Hud.ink, modifier = Modifier.weight(1f))
                if (dayType != DayType.NORMAL) HudChip(dayType.label, Tone.AMBER)
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(60) },
                placeholder = { Text("Search: lentils, rice, banana…", color = Hud.dim) },
                singleLine = true,
                colors = hudFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Choices(
                    listOf("No meat", "Vegan", "Fruit only"),
                    {
                        when (it) {
                            "No meat" -> filter.fishOnly
                            "Vegan" -> filter.vegan
                            else -> filter.fruitOnly
                        }
                    },
                    { it },
                    {
                        filter = when (it) {
                            "No meat" -> filter.copy(fishOnly = !filter.fishOnly)
                            "Vegan" -> filter.copy(vegan = !filter.vegan)
                            else -> filter.copy(fruitOnly = !filter.fruitOnly)
                        }
                    },
                )
                Text(
                    "Filters go by USDA food group and the food's name, so check mixed dishes yourself.",
                    style = HudType.small,
                    color = Hud.dim,
                )
            }
        }
        problem?.let { item { Text(it, style = HudType.small, color = Hud.amber) } }
        lastLogged?.let { item { HudChip("Logged · $it", Tone.GREEN) } }
        if (onNewFood != null || onNewRecipe != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    onNewFood?.let { OutlinedButton(onClick = it) { Text("NEW FOOD") } }
                    onNewRecipe?.let { OutlinedButton(onClick = it) { Text("NEW RECIPE") } }
                }
            }
        }
        if (mine.isNotEmpty()) {
            item { Text("YOUR FOODS AND RECIPES", style = HudType.label, color = Hud.muted) }
            items(mine, key = { "mine-" + it.ref }) { f -> FoodRow(f, { picked = f }, onEditFood?.let { e -> { e(f) } }) }
        }
        item {
            Text(
                when {
                    loading -> "LOADING FOODS…"
                    query.isBlank() && results.isEmpty() -> "TYPE TO SEARCH 7,400 FOODS"
                    query.isBlank() -> "RECENT"
                    results.isEmpty() -> "NO MATCHES. TRY FEWER WORDS, OR QUICK ADD."
                    else -> "${results.size} MATCHES"
                },
                style = HudType.label,
                color = Hud.muted,
            )
        }
        items(results.filter { r -> mine.none { it.ref == r.ref } }, key = { it.ref }) { f ->
            FoodRow(f, { picked = f }, if (f.custom) onEditFood?.let { e -> { e(f) } } else null)
        }
    }

    picked?.let { f ->
        AmountDialog(
            food = f,
            initialMeal = meal,
            confirmLabel = if (meal != null) "Log" else "Add",
            onDone = { g, amount, m ->
                onChosen(f, g, amount, m)
                lastLogged = "${f.name.take(28)} · ${amount}"
            },
            onDismiss = { picked = null },
        )
    }
}

@Composable
private fun FoodRow(f: Food, onClick: () -> Unit, onEdit: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Hud.panel)
            .clickable(onClickLabel = "Choose ${f.name}", role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(f.name, style = HudType.small, color = Hud.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${kcalText(f.per100.kcal)} kcal · P ${f.per100.protein.roundToInt()} · F ${f.per100.fat.roundToInt()} · " +
                    "C ${f.per100.carb.roundToInt()} per 100 g",
                style = HudType.label,
                color = Hud.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (f.recipe) HudChip("Recipe", Tone.GREEN) else if (f.custom) HudChip("Yours", Tone.GREEN)
        if (onEdit != null) {
            Text(
                "EDIT",
                style = HudType.label,
                color = Hud.green,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClickLabel = "Edit ${f.name}", role = Role.Button, onClick = onEdit)
                    .padding(6.dp),
            )
        }
    }
}
