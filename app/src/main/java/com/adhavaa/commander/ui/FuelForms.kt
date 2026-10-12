package com.adhavaa.commander.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adhavaa.commander.fuel.Food
import com.adhavaa.commander.fuel.LogEntry
import com.adhavaa.commander.fuel.Meal
import com.adhavaa.commander.fuel.Nutrients
import com.adhavaa.commander.fuel.Serving
import com.adhavaa.commander.fuel.amountLabel
import com.adhavaa.commander.fuel.nutrientsFor
import com.adhavaa.commander.fuel.servingChoices
import com.adhavaa.commander.orders.kcalText
import com.adhavaa.commander.orders.kgText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/** Parses what someone typed as a number; accepts a comma as the decimal mark. */
fun parseNumber(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

fun numText(v: Double?): String = when {
    v == null -> ""
    v == v.roundToInt().toDouble() -> v.roundToInt().toString()
    else -> String.format(java.util.Locale.US, "%.1f", v)
}

@Composable
fun hudFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Hud.green,
    unfocusedBorderColor = Hud.line,
    focusedLabelColor = Hud.green,
    unfocusedLabelColor = Hud.muted,
    cursorColor = Hud.green,
    focusedTextColor = Hud.ink,
    unfocusedTextColor = Hud.ink,
)

@Composable
fun NumberField(label: String, value: String, onValue: (String) -> Unit, modifier: Modifier = Modifier, suffix: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onValue(v.filter { it.isDigit() || it == '.' || it == ',' }.take(8)) },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it, color = Hud.muted) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = hudFieldColors(),
        modifier = modifier,
    )
}

@Composable
fun TextInput(label: String, value: String, onValue: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(it.take(120)) },
        label = { Text(label) },
        singleLine = true,
        colors = hudFieldColors(),
        modifier = modifier.fillMaxWidth(),
    )
}

/** A row of mono option tiles; the chosen ones are signal green. Scrolls sideways when it doesn't fit. */
@Composable
fun <T> Choices(options: List<T>, isOn: (T) -> Boolean, label: (T) -> String, onPick: (T) -> Unit, scroll: Boolean = true) {
    val content: @Composable () -> Unit = {
        for (o in options) {
            val on = isOn(o)
            Text(
                label(o).uppercase(),
                style = HudType.label,
                color = if (on) Hud.onMint else Hud.muted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (on) Hud.green else Hud.panelHi)
                    .border(1.dp, if (on) Hud.green else Hud.line, RoundedCornerShape(4.dp))
                    .clickable(onClickLabel = label(o), role = Role.RadioButton) { onPick(o) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
    if (scroll) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) { content() }
    } else {
        FlowRowCompat { content() }
    }
}

/** Wrapping row for option tiles. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRowCompat(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

/** "245 kcal · P 12 · F 8 · C 30 g". */
fun macroLine(n: Nutrients): String =
    "${kcalText(n.kcal)} kcal · P ${n.protein.roundToInt()} · F ${n.fat.roundToInt()} · C ${n.carb.roundToInt()} g"

/** A labelled bar: what's eaten against the target. Over target turns amber. */
@Composable
fun TargetBar(label: String, eaten: Double, target: Double?, unit: String, color: Color = Hud.green) {
    val frac = if (target != null && target > 0) (eaten / target).toFloat() else 0f
    val over = target != null && eaten > target * 1.05
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label.uppercase(), style = HudType.label, color = Hud.muted, modifier = Modifier.weight(1f))
            val num: (Double) -> String = { if (unit == "kcal") kcalText(it) else it.roundToInt().toString() }
            Text(
                if (target != null) "${num(eaten)} / ${num(target)} $unit" else "${num(eaten)} $unit",
                style = HudType.value,
                color = if (over) Hud.amber else Hud.ink,
            )
        }
        if (target != null) Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Hud.panelHi),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(frac.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (over) Hud.amber else color),
            )
        }
    }
}

/** Pick an amount of a food: a serving, how many, and which meal. Also edits a logged entry. */
@Composable
fun AmountDialog(
    food: Food,
    initialMeal: Meal?,
    onDone: (grams: Double, amount: String, meal: Meal) -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "Log",
    initialGrams: Double? = null,
    onDelete: (() -> Unit)? = null,
) {
    val choices = servingChoices(food)
    var serving by remember { mutableStateOf(if (initialGrams != null) choices.first() else choices.getOrElse(1) { choices.first() }) }
    var qty by remember { mutableStateOf(numText(initialGrams ?: if (serving.label == "g") 100.0 else 1.0)) }
    var meal by remember { mutableStateOf(initialMeal ?: Meal.SNACKS) }
    val q = parseNumber(qty)
    val grams = q?.let { it * serving.grams }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Hud.panel,
        title = { Text(food.name, style = HudType.value, color = Hud.ink, maxLines = 3) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Choices(choices, { it == serving }, { it.label }, { s: Serving ->
                    val g = grams
                    serving = s
                    if (g != null) qty = numText(if (s.label == "g") g else (g / s.grams * 4).roundToInt() / 4.0)
                })
                NumberField(if (serving.label == "g") "Grams" else "How many", qty, { qty = it }, Modifier.fillMaxWidth(), suffix = if (serving.label == "g") "g" else serving.label)
                if (initialMeal != null) Choices(Meal.entries, { it == meal }, { it.label }, { meal = it })
                Text(
                    grams?.let { macroLine(nutrientsFor(food, it)) + " · ${it.roundToInt()} g" } ?: "Enter an amount",
                    style = HudType.value,
                    color = if (grams != null) Hud.green else Hud.amber,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = grams != null && grams > 0 && grams < 5000, onClick = {
                onDone(grams!!, amountLabel(q!!, serving), meal)
                onDismiss()
            }) { Text(confirmLabel.uppercase()) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { onDelete(); onDismiss() }) { Text("DELETE", color = Hud.amber) }
                TextButton(onClick = onDismiss) { Text("CANCEL") }
            }
        },
    )
}

/** Calories (and optionally macros) without a food: for a meal out or a label you read. */
@Composable
fun QuickAddDialog(
    initialMeal: Meal,
    existing: LogEntry? = null,
    onDone: (label: String, n: Nutrients, meal: Meal) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var label by remember { mutableStateOf(existing?.name ?: "") }
    var kcal by remember { mutableStateOf(numText(existing?.n?.kcal)) }
    var protein by remember { mutableStateOf(numText(existing?.n?.protein?.takeIf { it > 0 })) }
    var fat by remember { mutableStateOf(numText(existing?.n?.fat?.takeIf { it > 0 })) }
    var carb by remember { mutableStateOf(numText(existing?.n?.carb?.takeIf { it > 0 })) }
    var meal by remember { mutableStateOf(existing?.meal ?: initialMeal) }
    val k = parseNumber(kcal)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Hud.panel,
        title = { Text(if (existing == null) "QUICK ADD" else "EDIT QUICK ADD", style = HudType.title, color = Hud.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput("What (optional)", label, { label = it })
                NumberField("Calories", kcal, { kcal = it }, Modifier.fillMaxWidth(), "kcal")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberField("Protein", protein, { protein = it }, Modifier.weight(1f), "g")
                    NumberField("Fat", fat, { fat = it }, Modifier.weight(1f), "g")
                    NumberField("Carbs", carb, { carb = it }, Modifier.weight(1f), "g")
                }
                Choices(Meal.entries, { it == meal }, { it.label }, { meal = it })
            }
        },
        confirmButton = {
            TextButton(enabled = k != null && k in 0.0..10000.0, onClick = {
                onDone(label, Nutrients(k!!, parseNumber(protein) ?: 0.0, parseNumber(fat) ?: 0.0, parseNumber(carb) ?: 0.0), meal)
                onDismiss()
            }) { Text("SAVE") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { onDelete(); onDismiss() }) { Text("DELETE", color = Hud.amber) }
                TextButton(onClick = onDismiss) { Text("CANCEL") }
            }
        },
    )
}

/** Today's weigh-in (or another day's). One per day; saving again replaces it. */
@Composable
fun WeightDialog(
    date: LocalDate,
    existing: Double?,
    lastKg: Double?,
    onSave: (Double) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var kg by remember { mutableStateOf(numText(existing)) }
    val v = parseNumber(kg)
    val ok = v != null && v in 25.0..300.0
    val jump = ok && lastKg != null && kotlin.math.abs(v!! - lastKg) > 3.0
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Hud.panel,
        title = { Text("WEIGH-IN · ${date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}", style = HudType.title, color = Hud.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Weight", kg, { kg = it }, Modifier.fillMaxWidth(), "kg")
                Text(
                    when {
                        jump -> "That's more than 3 kg from your trend (${kgText(lastKg!!)} kg). Check it's right."
                        else -> "After the toilet, before food or drink. Same scale each time."
                    },
                    style = HudType.small,
                    color = if (jump) Hud.amber else Hud.muted,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = { onSave(v!!); onDismiss() }) { Text("SAVE") }
        },
        dismissButton = {
            Row {
                if (onDelete != null && existing != null) TextButton(onClick = { onDelete(); onDismiss() }) { Text("DELETE", color = Hud.amber) }
                TextButton(onClick = onDismiss) { Text("CANCEL") }
            }
        },
    )
}
