package com.adhavaa.commander.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adhavaa.commander.data.FuelSettings
import com.adhavaa.commander.fuel.goalLabel
import com.adhavaa.commander.model.Activity
import com.adhavaa.commander.model.restingEnergy
import com.adhavaa.commander.orders.kcalText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Fuel profile and weekly practices. On first setup it also takes today's weight, which becomes the start of the
 * trend. Later edits keep the start day so the expenditure estimate carries on (D-53).
 */
@Composable
fun FuelSetupScreen(current: FuelSettings, today: LocalDate, onSave: (FuelSettings, Double?) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val first = !current.setUp
    var male by rememberSaveable { mutableStateOf(current.male) }
    var birthYear by rememberSaveable { mutableStateOf(current.birthYear?.toString() ?: "") }
    var height by rememberSaveable { mutableStateOf(numText(current.heightCm)) }
    var weight by rememberSaveable { mutableStateOf("") }
    var activity by rememberSaveable { mutableStateOf(current.activity) }
    var rate by rememberSaveable { mutableStateOf(current.rate) }
    var checkInDay by rememberSaveable { mutableStateOf(current.checkInDay) }
    var fruitDays by rememberSaveable { mutableStateOf(current.fruitDays) }
    var veganDays by rememberSaveable { mutableStateOf(current.veganDays) }
    var fishOnly by rememberSaveable { mutableStateOf(current.fishOnly) }

    val year = birthYear.toIntOrNull()?.takeIf { it in (today.year - 90)..(today.year - 18) }
    val h = parseNumber(height)?.takeIf { it in 120.0..230.0 }
    val w = parseNumber(weight)?.takeIf { it in 30.0..300.0 }
    val ok = male != null && year != null && h != null && (!first || w != null)
    val ree = if (male != null && year != null && h != null) (w ?: current.startWeightKg)?.let { restingEnergy(it, h, (today.year - year).toDouble(), male!!) } else null

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
                Text(if (first) "SET UP FUEL" else "FUEL PROFILE", style = HudType.title, color = Hud.ink)
            }
        }
        item {
            HudPanel(
                title = "Before you start",
                background = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Hud.amberDeep, Hud.panel)),
                border = Hud.amber.copy(alpha = 0.6f),
            ) {
                Text(
                    "These targets are for a healthy adult. If you're pregnant, have a medical condition that affects " +
                        "what you eat, or have had an eating disorder, talk to a doctor or dietitian before following them.",
                    style = HudType.body,
                    color = Hud.ink,
                )
            }
        }
        item {
            HudPanel(title = "You", subtitle = "For the resting-energy equation (Mifflin–St Jeor)") {
                Choices(listOf(true, false), { it == male }, { if (it) "Male" else "Female" }, { male = it })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberField("Birth year", birthYear, { birthYear = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f))
                    NumberField("Height", height, { height = it }, Modifier.weight(1f), "cm")
                }
                if (first) {
                    NumberField("Weight this morning", weight, { weight = it }, Modifier.fillMaxWidth(), "kg")
                } else {
                    Hint("Weight is logged on the Fuel tab each morning.")
                }
                ree?.let { KeyValue("Resting energy", "${kcalText(it)} kcal/day", Hud.green) }
            }
        }
        item {
            HudPanel(title = "Activity", subtitle = "Only the first guess; your logs take over after ~3 weeks") {
                for (a in Activity.entries) {
                    Choices(listOf(a), { it == activity }, { "${it.label} · PAL ${it.band}" }, { activity = it })
                }
                Hint("Bands from FAO/WHO/UNU 2001. Commander starts at the middle of the band you pick.")
            }
        }
        item {
            HudPanel(title = "Goal", subtitle = "Share of body weight per week") {
                Choices(FuelSettings.RATES, { it == rate }, ::goalLabel, { rate = it }, scroll = false)
                Hint("Loss is kept between 0.25% and 1% a week (Helms et al. 2014 recommend 0.5–1%). Slower protects your training.")
            }
        }
        item {
            HudPanel(title = "Weekly check-in") {
                Choices(DayOfWeek.entries, { it == checkInDay }, { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }, { checkInDay = it })
                Hint("Targets update on this day and hold for the week.")
            }
        }
        item {
            HudPanel(title = "Your practices", subtitle = "Commander works around them") {
                Text("FRUIT DAYS · NO TARGETS", style = HudType.label, color = Hud.muted)
                Choices(DayOfWeek.entries, { it in fruitDays }, { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }, {
                    fruitDays = if (it in fruitDays) fruitDays - it else fruitDays + it
                })
                Text("VEGAN DAYS · SEARCH HIDES ANIMAL FOODS", style = HudType.label, color = Hud.muted)
                Choices(DayOfWeek.entries, { it in veganDays }, { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }, {
                    veganDays = if (it in veganDays) veganDays - it else veganDays + it
                })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Fish but no meat or poultry", style = HudType.body, color = Hud.ink, modifier = Modifier.weight(1f))
                    Switch(
                        checked = fishOnly,
                        onCheckedChange = { fishOnly = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Hud.onMint, checkedTrackColor = Hud.green),
                    )
                }
            }
        }
        item {
            HudButton(if (first) "Start Fuel" else "Save", {
                onSave(
                    current.copy(
                        male = male, birthYear = year, heightCm = h, activity = activity, rate = rate, checkInDay = checkInDay,
                        fruitDays = fruitDays, veganDays = veganDays, fishOnly = fishOnly,
                    ),
                    if (first) w else null,
                )
                onBack()
            }, enabled = ok)
        }
        if (!ok) {
            item {
                Hint(
                    when {
                        male == null -> "Pick male or female for the equation."
                        year == null -> "Birth year: adults 18 to 90."
                        h == null -> "Height in centimetres, 120 to 230."
                        else -> "Weight in kilograms, 30 to 300."
                    },
                )
            }
        }
    }
}
