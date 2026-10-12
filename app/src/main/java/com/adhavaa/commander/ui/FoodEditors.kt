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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adhavaa.commander.data.RecipeDraft
import com.adhavaa.commander.fuel.Diet
import com.adhavaa.commander.fuel.Food
import com.adhavaa.commander.fuel.Ingredient
import com.adhavaa.commander.fuel.Nutrients
import com.adhavaa.commander.fuel.Serving
import com.adhavaa.commander.fuel.formatServings
import com.adhavaa.commander.fuel.nutrientsFor
import com.adhavaa.commander.fuel.recipeFood
import com.adhavaa.commander.orders.kcalText
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
private fun EditorFrame(title: String, onBack: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    BackHandler(onBack = onBack)
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Hud.bg)
            .imePadding(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = insets.calculateTopPadding() + 8.dp, bottom = insets.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SquareButton("Back", onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = Hud.muted, modifier = Modifier.size(18.dp))
                }
                Text(title.uppercase(), style = HudType.title, color = Hud.ink)
            }
        }
        content()
    }
}

/**
 * One of your own foods, typed from a label. Nutrients can be entered per 100 g or per serving; they're stored per
 * 100 g. [existing] edits a saved food.
 */
@Composable
fun FoodEditor(
    existing: Food?,
    onSave: (name: String, per100: Nutrients, servings: String, diet: Diet) -> Unit,
    onDelete: (() -> Unit)?,
    onBack: () -> Unit,
) {
    val firstServing = existing?.servings?.firstOrNull()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var perServing by remember { mutableStateOf(false) }
    var servingName by remember { mutableStateOf(firstServing?.label ?: "") }
    var servingG by remember { mutableStateOf(numText(firstServing?.grams)) }
    var kcal by remember { mutableStateOf(numText(existing?.per100?.kcal)) }
    var protein by remember { mutableStateOf(numText(existing?.per100?.protein)) }
    var fat by remember { mutableStateOf(numText(existing?.per100?.fat)) }
    var carb by remember { mutableStateOf(numText(existing?.per100?.carb)) }
    var fiber by remember { mutableStateOf(numText(existing?.per100?.fiber)) }
    var diet by remember { mutableStateOf(existing?.diet ?: Diet.PLANT) }

    val sg = parseNumber(servingG)
    val basis = if (perServing) sg else 100.0
    val entered = Nutrients(parseNumber(kcal) ?: -1.0, parseNumber(protein) ?: 0.0, parseNumber(fat) ?: 0.0, parseNumber(carb) ?: 0.0, parseNumber(fiber) ?: 0.0)
    val per100 = basis?.takeIf { it > 0 }?.let { entered * (100.0 / it) }
    val energyFromMacros = 4 * entered.protein + 4 * entered.carb + 9 * entered.fat
    val mismatch = entered.kcal > 0 && energyFromMacros > 0 && abs(energyFromMacros - entered.kcal) > 0.25 * entered.kcal + 20
    val ok = name.isNotBlank() && per100 != null && entered.kcal in 0.0..5000.0 && (!perServing || servingName.isNotBlank())

    EditorFrame(if (existing == null) "New food" else "Edit food", onBack) {
        item {
            HudPanel(title = "Food") {
                TextInput("Name", name, { name = it })
                Text("NUTRITION ON THE LABEL IS", style = HudType.label, color = Hud.muted)
                Choices(listOf(false, true), { it == perServing }, { if (it) "Per serving" else "Per 100 g" }, { perServing = it })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextInput("Serving name (cup, piece…)", servingName, { servingName = it }, Modifier.weight(1.4f))
                    NumberField("Weighs", servingG, { servingG = it }, Modifier.weight(1f), "g")
                }
                Text(
                    if (perServing) "Enter the numbers for one serving." else "Enter the numbers for 100 g. A serving is optional.",
                    style = HudType.small,
                    color = Hud.muted,
                )
                NumberField("Calories", kcal, { kcal = it }, Modifier.fillMaxWidth(), "kcal")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberField("Protein", protein, { protein = it }, Modifier.weight(1f), "g")
                    NumberField("Fat", fat, { fat = it }, Modifier.weight(1f), "g")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberField("Carbs", carb, { carb = it }, Modifier.weight(1f), "g")
                    NumberField("Fiber", fiber, { fiber = it }, Modifier.weight(1f), "g")
                }
                if (mismatch) {
                    Text(
                        "Protein, carbs and fat add up to about ${energyFromMacros.roundToInt()} kcal, not ${entered.kcal.roundToInt()}. Check the label.",
                        style = HudType.small,
                        color = Hud.amber,
                    )
                }
                Text("CONTAINS", style = HudType.label, color = Hud.muted)
                Choices(Diet.entries, { it == diet }, { it.label }, { diet = it })
                Text("Used by the no-meat and vegan filters.", style = HudType.small, color = Hud.muted)
            }
        }
        item {
            HudButton("Save food", {
                val servings = if (servingName.isNotBlank() && sg != null && sg > 0) listOf(Serving(servingName.trim(), sg)) else emptyList()
                onSave(name, per100!!, formatServings(servings), diet)
                onBack()
            }, enabled = ok)
        }
        if (onDelete != null) {
            item { TextButton(onClick = { onDelete(); onBack() }) { Text("REMOVE FROM MY FOODS", color = Hud.amber) } }
            item { Hint("Past entries keep their numbers.") }
        }
    }
}

/**
 * A recipe from ingredients. Weighing the cooked pot makes per-gram numbers right when water boils off or is
 * absorbed; without it the raw total is used (MODEL.md section 10).
 */
@Composable
fun RecipeEditor(
    existing: RecipeDraft?,
    onPickIngredient: (onPicked: (Ingredient) -> Unit) -> Unit,
    onSave: (RecipeDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    val items = remember { mutableStateListOf<Ingredient>().apply { existing?.ingredients?.let { addAll(it) } } }
    var cooked by remember { mutableStateOf(numText(existing?.cookedG)) }
    var portions by remember { mutableStateOf((existing?.portions ?: 1).toString()) }
    var editing by remember { mutableStateOf<Int?>(null) }

    val cookedG = parseNumber(cooked)?.takeIf { it > 0 }
    val n = portions.toIntOrNull()?.takeIf { it in 1..50 }
    val preview = recipeFood(0, name, items.toList(), cookedG, n ?: 1)
    val total = items.fold(Nutrients.ZERO) { acc, i -> acc + nutrientsFor(i.food, i.grams) }
    val ok = name.isNotBlank() && items.isNotEmpty() && n != null

    EditorFrame(if (existing == null) "New recipe" else "Edit recipe", onBack) {
        item {
            HudPanel(title = "Recipe") {
                TextInput("Name (e.g. Rasam, Beans poriyal)", name, { name = it })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberField("Cooked weight", cooked, { cooked = it }, Modifier.weight(1.3f), "g")
                    NumberField("Servings", portions, { portions = it.filter(Char::isDigit) }, Modifier.weight(1f))
                }
                Hint("Weigh the finished pot (minus the pot) so a 200 g bowl is counted right. Leave it blank to use the raw total.")
            }
        }
        item {
            HudPanel(
                title = "Ingredients",
                subtitle = "${items.size} · ${kcalText(total.kcal)} kcal in total",
                trailing = { HudChip("${items.sumOf { it.grams }.roundToInt()} g raw") },
            ) {
                items.forEachIndexed { i, ing ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Hud.panelHi)
                            .clickable(onClickLabel = "Change ${ing.food.name}", role = Role.Button) { editing = i }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(ing.food.name, style = HudType.small, color = Hud.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${ing.grams.roundToInt()} g", style = HudType.value, color = Hud.muted)
                        Text(kcalText(nutrientsFor(ing.food, ing.grams).kcal), style = HudType.value, color = Hud.ink)
                    }
                }
                OutlinedButton(onClick = { onPickIngredient { items += it } }) { Text("+ INGREDIENT") }
            }
        }
        item {
            HudPanel(title = "Per serving") {
                val s = preview.servings.firstOrNull()
                if (s != null && items.isNotEmpty()) {
                    KeyValue("Serving", "${s.grams.roundToInt()} g")
                    Text(macroLine(nutrientsFor(preview, s.grams)), style = HudType.value, color = Hud.green)
                    Text("Per 100 g: " + macroLine(preview.per100), style = HudType.small, color = Hud.muted)
                    KeyValue("Contains", preview.diet.label)
                } else {
                    Hint("Add ingredients to see the numbers.")
                }
            }
        }
        item {
            HudButton("Save recipe", {
                onSave(RecipeDraft(existing?.id, name.trim(), items.toList(), cookedG, n!!))
                onBack()
            }, enabled = ok)
        }
        if (onDelete != null) item { TextButton(onClick = { onDelete(); onBack() }) { Text("REMOVE RECIPE", color = Hud.amber) } }
    }

    editing?.let { i ->
        items.getOrNull(i)?.let { ing ->
            AmountDialog(
                food = ing.food,
                initialMeal = null,
                initialGrams = ing.grams,
                confirmLabel = "Save",
                onDone = { g, _, _ -> items[i] = ing.copy(grams = g) },
                onDelete = { items.removeAt(i) },
                onDismiss = { editing = null },
            )
        }
    }
}
