package com.adhavaa.commander.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.adhavaa.commander.data.TrainSettings
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/** Train's plan settings: race date, weekly minutes now and at most, and the swimming pause. The week is fixed (D-76). */
@Composable
fun TrainSetupScreen(current: TrainSettings, today: LocalDate, onSave: (TrainSettings) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val first = !current.setUp
    var race by rememberSaveable { mutableStateOf(current.raceDate) }
    var now by rememberSaveable { mutableStateOf(numText(current.currentMinutes / 60.0)) }
    var most by rememberSaveable { mutableStateOf(numText(current.peakMinutes / 60.0)) }
    var swimPause by rememberSaveable { mutableStateOf(current.swimPauseKg) }
    val nowH = parseNumber(now)?.takeIf { it in 0.5..25.0 }
    val mostH = parseNumber(most)?.takeIf { it in 1.0..30.0 }
    val raceOk = race == null || race!!.isAfter(today)
    val ok = nowH != null && mostH != null && mostH >= nowH && raceOk
    val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Hud.bg).imePadding(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = insets.calculateTopPadding() + 8.dp, bottom = insets.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SquareButton("Back", onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = Hud.muted, modifier = Modifier.size(18.dp))
                }
                Text(if (first) "SET UP TRAIN" else "TRAIN PLAN", style = HudType.title, color = Hud.ink)
            }
        }
        item {
            HudPanel(
                title = "Before you start",
                background = Brush.verticalGradient(listOf(Hud.amberDeep, Hud.panel)),
                border = Hud.amber.copy(alpha = 0.6f),
            ) {
                Text(
                    "If you have a heart condition, chest pain, dizziness or a joint or bone problem, check with a doctor " +
                        "before training. Never train through pain. Stop and get help if you feel chest pain, faintness or " +
                        "unusual breathlessness.",
                    style = HudType.body,
                    color = Hud.ink,
                )
            }
        }
        item {
            HudPanel(title = "Race", subtitle = "IRONMAN 70.3: swim 1.9 km, bike 90 km, run 21.1 km") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Hud.panelHi)
                        .border(1.dp, Hud.line, RoundedCornerShape(6.dp))
                        .clickable(onClickLabel = "Pick race date", role = Role.Button) {
                            showDatePicker(context, race ?: today.plusWeeks(24)) { race = it }
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Race date", style = HudType.body, color = Hud.ink, modifier = Modifier.weight(1f))
                    Text(race?.format(fmt) ?: "Not set", style = HudType.bigValue, color = if (race != null) Hud.green else Hud.muted)
                }
                race?.let { r ->
                    Text(
                        if (r.isAfter(today)) "${ChronoUnit.WEEKS.between(today, r)} weeks away. Taper starts 2 weeks out." else "Pick a date in the future.",
                        style = HudType.small,
                        color = if (r.isAfter(today)) Hud.muted else Hud.amber,
                    )
                    TextButton(onClick = { race = null }) { Text("NO RACE YET") }
                }
                Hint("Without a date Commander keeps building base and never tapers.")
            }
        }
        item {
            HudPanel(title = "Hours a week", subtitle = "Swim + bike + run, not lifting") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberField("Now", now, { now = it }, Modifier.weight(1f), "h")
                    NumberField("Most you can give", most, { most = it }, Modifier.weight(1f), "h")
                }
                Hint(
                    "Week one starts from what you do now and grows 8% a week, every fourth week lighter, up to the most. " +
                        "Set \"now\" to what you really do, so the first weeks fit you. Bike Monday, run Tuesday, long " +
                        "bike Wednesday; lifting Thursday to Sunday: Lower A, Upper A, Lower B, Upper B.",
                )
            }
        }
        item {
            HudPanel(title = "Swimming", subtitle = "Paused until your Fuel trend weight is down by") {
                Choices(listOf(13.0, 15.0, null), { it == swimPause }, { it?.let { kg -> "${kg.toInt()} kg" } ?: "Swim now" }, { swimPause = it })
                Hint(
                    if (swimPause == null) "Swims are in the plan on Monday and Wednesday."
                    else "Until then the swim minutes go to Wednesday's long ride. Swimming comes back the Monday after " +
                        "your trend first reaches the mark, measured from your Fuel setup weight.",
                )
            }
        }
        item {
            HudButton(if (first) "Start Train" else "Save", {
                onSave(current.copy(raceDate = race, currentMinutes = nowH!! * 60.0, peakMinutes = mostH!! * 60.0, swimPauseKg = swimPause))
                onBack()
            }, enabled = ok)
        }
        if (!ok) {
            item {
                Hint(
                    when {
                        nowH == null -> "Hours now: between 0.5 and 25."
                        mostH == null -> "Most hours: between 1 and 30."
                        mostH < nowH -> "The most can't be less than what you do now."
                        else -> "Pick a race date in the future, or no race."
                    },
                )
            }
        }
    }
}
