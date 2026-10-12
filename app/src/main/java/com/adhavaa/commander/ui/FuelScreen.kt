package com.adhavaa.commander.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adhavaa.commander.FuelUi
import com.adhavaa.commander.fuel.DayType
import com.adhavaa.commander.fuel.LogEntry
import com.adhavaa.commander.fuel.Meal
import com.adhavaa.commander.fuel.goalLabel
import com.adhavaa.commander.fuel.pct
import com.adhavaa.commander.fuel.totals
import com.adhavaa.commander.fuel.trendChange
import com.adhavaa.commander.model.CheckIn
import com.adhavaa.commander.model.FuelParams
import com.adhavaa.commander.orders.kcalText
import com.adhavaa.commander.orders.kgText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.TreeMap
import kotlin.math.abs
import kotlin.math.roundToInt

@Immutable
data class FuelActions(
    val onDate: (LocalDate) -> Unit = {},
    val onAddFood: (Meal) -> Unit = {},
    val onQuickAdd: (Meal) -> Unit = {},
    val onEditEntry: (LogEntry) -> Unit = {},
    val onCopyYesterday: () -> Unit = {},
    val onLogWeight: () -> Unit = {},
    val onIncomplete: (Boolean) -> Unit = {},
    val onSetup: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

private val DAY_FMT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

@Composable
fun FuelScreen(ui: FuelUi, actions: FuelActions) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val p = ui.picture
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Hud.bg),
        contentPadding = PaddingValues(
            start = 14.dp, end = 14.dp,
            top = insets.calculateTopPadding() + 8.dp,
            bottom = bottomPadding(insets),
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { FuelTopBar(ui, actions) }
        if (!p.setUp) item { SetupPrompt(actions.onSetup) }
        item { TargetsPanel(ui) }
        item { LogPanel(ui, actions) }
        item { WeightPanel(ui, actions.onLogWeight) }
        if (p.setUp) item { CoachPanel(ui, actions.onSetup) }
        item {
            Text(
                "Food data: USDA SR Legacy (public domain), built into the app. Wellness tool, not medical or " +
                    "dietary advice. No data leaves this phone.",
                style = HudType.small,
                color = Hud.dim,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun FuelTopBar(ui: FuelUi, actions: FuelActions) {
    val p = ui.picture
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Mark()
            Column(Modifier.weight(1f)) {
                Text("FUEL", style = HudType.title.copy(fontSize = 14.sp), color = Hud.ink)
                Text(
                    (if (p.setUp) goalLabel(ui.settings.rate) else "Not set up").uppercase(),
                    style = HudType.label,
                    color = Hud.green,
                )
            }
            HudChip(p.dayType.label, if (p.dayType == DayType.NORMAL) Tone.MUTED else Tone.AMBER)
            SquareButton("Open settings", actions.onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = null, tint = Hud.muted, modifier = Modifier.size(18.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SquareButton("Previous day", { actions.onDate(p.date.minusDays(1)) }) { Text("‹", style = HudType.bigValue, color = Hud.ink) }
            val rel = when (ChronoUnit.DAYS.between(p.today, p.date)) {
                0L -> "Today · "
                -1L -> "Yesterday · "
                1L -> "Tomorrow · "
                else -> ""
            }
            Text(
                (rel + p.date.format(DAY_FMT)).uppercase(),
                style = HudType.value,
                color = if (p.date == p.today) Hud.green else Hud.ink,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Hud.panel)
                    .clickable(onClickLabel = "Pick a day", role = Role.Button) { showDatePicker(context, p.date, actions.onDate) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            )
            SquareButton("Next day", { actions.onDate(p.date.plusDays(1)) }) { Text("›", style = HudType.bigValue, color = Hud.ink) }
            if (p.date != p.today) {
                SquareButton("Back to today", { actions.onDate(p.today) }) { Text("•", style = HudType.bigValue, color = Hud.green) }
            }
        }
    }
}

@Composable
private fun SetupPrompt(onSetup: () -> Unit) {
    HudPanel(
        title = "Set up Fuel",
        subtitle = "Targets that follow what you actually burn",
        decoration = Modifier.contours(seed = 2f),
        background = Brush.verticalGradient(listOf(Hud.panel, Hud.bg)),
    ) {
        Text(
            "Log food and weigh in each morning. Every Monday Commander compares what you ate with how your " +
                "weight moved, works out what you really burn, and sets your calories and macros for the week.",
            style = HudType.body,
            color = Hud.ink,
        )
        HudButton("Set up Fuel", onSetup)
    }
}

@Composable
private fun TargetsPanel(ui: FuelUi) {
    val p = ui.picture
    val t = p.targets
    val eaten = ui.eaten
    HudPanel(
        background = Brush.verticalGradient(listOf(Hud.mint, Hud.mintDeep)),
        border = Hud.mint,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    p.dayType == DayType.FRUIT -> "◆ FRUIT DAY"
                    t == null -> "◆ EATEN"
                    else -> "◆ RATIONS"
                },
                style = HudType.title,
                color = Hud.onMint,
                modifier = Modifier.weight(1f),
            )
            if (p.incomplete) Text("NOT FULLY LOGGED", style = HudType.label, color = Hud.onMint.copy(alpha = 0.7f))
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (t != null) kcalText(abs(t.kcal - eaten.kcal)) else kcalText(eaten.kcal),
                style = HudType.headline.copy(fontSize = 34.sp, lineHeight = 38.sp),
                color = Hud.onMint,
            )
            Text(
                when {
                    t == null -> "KCAL EATEN"
                    eaten.kcal <= t.kcal -> "KCAL LEFT OF ${kcalText(t.kcal)}"
                    else -> "KCAL OVER ${kcalText(t.kcal)}"
                },
                style = HudType.label,
                color = Hud.onMint.copy(alpha = 0.75f),
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Column(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Hud.panel)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TargetBar("Calories", eaten.kcal, t?.kcal, "kcal")
            TargetBar("Protein", eaten.protein, t?.proteinG, "g")
            TargetBar("Fat", eaten.fat, t?.fatG, "g")
            TargetBar("Carbs", eaten.carb, t?.carbG, "g")
            Text("Fiber ${eaten.fiber.roundToInt()} g", style = HudType.label, color = Hud.muted)
        }
        Text(
            when {
                p.dayType == DayType.FRUIT -> "Your fruit day: log what you eat. It counts toward your expenditure, but there are no targets today and nothing to make up tomorrow."
                !p.setUp -> "Set up Fuel to get targets."
                t == null -> "Targets start once Fuel is set up."
                else -> carbNote(t.carbG, p.trendKg ?: ui.settings.startWeightKg)
            },
            style = HudType.small,
            color = Hud.onMint.copy(alpha = 0.8f),
        )
    }
}

/** Carbohydrate per kg beside the Burke et al. 2011 daily bands (MODEL.md section 10). */
fun carbNote(carbG: Double, kg: Double?): String {
    if (kg == null || kg <= 0) return "Protein 1.6 g/kg, fat 25% of calories, carbs the rest."
    val gkg = String.format(Locale.US, "%.1f", carbG / kg)
    return "Protein 1.6 g/kg, fat 25%, carbs the rest: $gkg g/kg. For reference (Burke et al. 2011): 3–5 g/kg for " +
        "light training, 5–7 for about an hour a day, 6–10 for 1–3 h of endurance work."
}

@Composable
private fun LogPanel(ui: FuelUi, actions: FuelActions) {
    val p = ui.picture
    HudPanel(
        title = "Log",
        subtitle = if (ui.entries.isEmpty()) "Nothing logged yet" else "${ui.entries.size} entries · tap one to change it",
        trailing = { HudChip("${kcalText(ui.eaten.kcal)} kcal", Tone.GREEN) },
    ) {
        for (meal in Meal.entries) {
            val items = ui.entries.filter { it.meal == meal }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(meal.label.uppercase(), style = HudType.label, color = Hud.muted, modifier = Modifier.weight(1f))
                    if (items.isNotEmpty()) Text("${kcalText(totals(items).kcal)} kcal", style = HudType.label, color = Hud.muted)
                    Text(
                        "+ ADD",
                        style = HudType.label,
                        color = Hud.green,
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClickLabel = "Add food to ${meal.label}", role = Role.Button) { actions.onAddFood(meal) }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
                for (e in items) EntryRow(e) { actions.onEditEntry(e) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            OutlinedButton(onClick = { actions.onAddFood(defaultMeal()) }) { Text("+ FOOD") }
            OutlinedButton(onClick = { actions.onQuickAdd(defaultMeal()) }) { Text("QUICK ADD") }
            OutlinedButton(onClick = actions.onCopyYesterday) { Text("COPY YESTERDAY") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Not fully logged", style = HudType.body, color = Hud.ink)
                Text("Leaves this day out of the expenditure estimate.", style = HudType.small, color = Hud.muted)
            }
            Switch(
                checked = p.incomplete,
                onCheckedChange = actions.onIncomplete,
                colors = SwitchDefaults.colors(checkedThumbColor = Hud.onMint, checkedTrackColor = Hud.amber),
            )
        }
    }
}

/** The meal that fits the time of day, for the general "+ food" button. */
fun defaultMeal(now: java.time.LocalTime = java.time.LocalTime.now()): Meal = when (now.hour) {
    in 4..10 -> Meal.BREAKFAST
    in 11..15 -> Meal.LUNCH
    in 18..22 -> Meal.DINNER
    else -> Meal.SNACKS
}

@Composable
private fun EntryRow(e: LogEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Hud.panelHi)
            .clickable(onClickLabel = "Change ${e.name}", role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(e.name, style = HudType.small, color = Hud.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                e.amount + " · P ${e.n.protein.roundToInt()} F ${e.n.fat.roundToInt()} C ${e.n.carb.roundToInt()}",
                style = HudType.label,
                color = Hud.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(kcalText(e.n.kcal), style = HudType.value, color = Hud.ink)
    }
}

@Composable
private fun WeightPanel(ui: FuelUi, onLogWeight: () -> Unit) {
    val p = ui.picture
    val trend = TreeMap<LocalDate, Double>().apply { ui.trend.forEach { put(it.first, it.second) } }
    val change = trendChange(trend, p.today)
    HudPanel(
        title = "Weight",
        subtitle = "Dots: weigh-ins · line: 10% trend",
        trailing = { HudChip(if (p.weightOnDate != null) "Logged" else "Not logged", if (p.weightOnDate != null) Tone.GREEN else Tone.AMBER) },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            StatCell(if (p.date == p.today) "Today" else "This day", p.weightOnDate?.let { "${kgText(it)} kg" } ?: "—", accent = Hud.green)
            StatCell("Trend", p.trendKg?.let { "${kgText(it)} kg" } ?: "—")
            StatCell("7 days", change?.let { (if (it > 0) "+" else if (it < 0) "−" else "") + "${kgText(abs(it))} kg" } ?: "—")
        }
        WeightChart(ui, Modifier.fillMaxWidth().height(120.dp))
        HudButton(if (p.weightOnDate == null) "Log weight" else "Change weight", onLogWeight)
    }
}

@Composable
private fun WeightChart(ui: FuelUi, modifier: Modifier) {
    val end = ui.picture.today
    val start = end.minusDays(41)
    val pts = ui.weighIns.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
    val line = ui.trend.filter { !it.first.isBefore(start) && !it.first.isAfter(end) }
    val all = pts.map { it.kg } + line.map { it.second }
    val desc = if (all.isEmpty()) "No weigh-ins yet" else "Weight over 6 weeks, ${kgText(all.min())} to ${kgText(all.max())} kg"
    if (all.isEmpty()) {
        Text("No weigh-ins in the last 6 weeks.", style = HudType.small, color = Hud.muted)
        return
    }
    val lo = all.min() - 0.5
    val hi = all.max() + 0.5
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.height(120.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(kgText(hi), style = HudType.label, color = Hud.dim)
            Text(kgText(lo), style = HudType.label, color = Hud.dim)
        }
        Canvas(modifier.semantics { contentDescription = desc }) {
            val days = ChronoUnit.DAYS.between(start, end).toFloat()
            fun x(d: LocalDate) = ChronoUnit.DAYS.between(start, d) / days * size.width
            fun y(kg: Double) = ((hi - kg) / (hi - lo)).toFloat() * size.height
            for (k in 0..3) {
                val yy = size.height * k / 3f
                drawLine(Hud.grid, Offset(0f, yy), Offset(size.width, yy), 1.dp.toPx())
            }
            var d = start
            while (!d.isAfter(end)) {
                if (d.dayOfWeek == ui.settings.checkInDay) drawLine(Hud.grid, Offset(x(d), 0f), Offset(x(d), size.height), 1.dp.toPx())
                d = d.plusDays(1)
            }
            if (line.size >= 2) {
                val path = Path()
                line.forEachIndexed { i, (day, kg) -> if (i == 0) path.moveTo(x(day), y(kg)) else path.lineTo(x(day), y(kg)) }
                drawPath(path, Hud.green, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
            for (w in pts) drawCircle(Hud.muted, 2.5.dp.toPx(), Offset(x(w.date), y(w.kg)))
        }
    }
}

@Composable
private fun CoachPanel(ui: FuelUi, onSetup: () -> Unit) {
    val p = ui.picture
    val ci = p.current ?: return
    val learned = p.checkIns.any { it.observed != null }
    HudPanel(
        title = "Coach",
        subtitle = "Weekly check-in · ${ui.settings.checkInDay.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())}",
        trailing = { HudChip(if (learned) "Learned" else "First guess", if (learned) Tone.GREEN else Tone.AMBER) },
    ) {
        if (p.losingTooFast) {
            Text(
                "You're losing about ${pct(abs(ci.weeklyRate ?: 0.0))} of body weight a week, faster than 1%. Eat to " +
                    "your target. If you are and it keeps happening, talk to a doctor.",
                style = HudType.body,
                color = Hud.amber,
            )
        }
        KeyValue("You burn about", "${kcalText(ci.expenditure)} kcal/day", Hud.green)
        KeyValue("Goal", goalLabel(ui.settings.rate))
        KeyValue("Daily target", "${kcalText(ci.kcal)} kcal" + if (ci.floorApplied) " (floor)" else "")
        KeyValue("Resting energy", "${kcalText(ci.restKcal)} kcal")
        KeyValue(
            "Measured change",
            ci.weeklyRate?.let { (if (it > 0) "+" else if (it < 0) "−" else "") + pct(abs(it)) + " a week" } ?: "Needs 2 weeks of weigh-ins",
            if (p.losingTooFast) Hud.amber else Hud.ink,
        )
        KeyValue("Next check-in", p.nextCheckIn?.format(DAY_FMT) ?: "—")
        Text(checkInNote(p.checkIns), style = HudType.small, color = Hud.muted)
        OutlinedButton(onClick = onSetup) { Text("PROFILE AND PRACTICES") }
    }
}

/** Why the latest check-in landed where it did, in a sentence or two. */
fun checkInNote(cis: List<CheckIn>): String {
    val cur = cis.lastOrNull() ?: return ""
    if (cis.size == 1) {
        return "Your first estimate is resting energy (Mifflin–St Jeor) times your activity level (FAO/WHO/UNU). " +
            "It can be a few hundred kcal off; check-ins correct it once there are 3 weeks of logs and weigh-ins."
    }
    val prev = cis[cis.size - 2]
    val parts = mutableListOf<String>()
    val obs = cur.observed
    if (obs != null) {
        parts += "Your last 3 weeks of logs and weigh-ins point to about ${kcalText(obs)} kcal a day, so the estimate moved halfway there."
    } else {
        parts += "Not enough data in the last 3 weeks (it needs ${FuelParams.MIN_LOGGED_DAYS} fully logged days and " +
            "${FuelParams.MIN_WEIGHED_DAYS} weigh-ins spread over 2 weeks), so the estimate held."
    }
    val delta = (cur.kcal - prev.kcal).roundToInt()
    parts += when {
        delta > 0 -> "Target up $delta kcal."
        delta < 0 -> "Target down ${-delta} kcal."
        else -> "Target unchanged."
    }
    if (cur.stepLimited) parts += "It can drop at most ${FuelParams.MAX_STEP_KCAL.roundToInt()} kcal a week."
    if (cur.floorApplied) parts += "It's at your resting energy, the lowest Commander will set."
    return parts.joinToString(" ")
}
