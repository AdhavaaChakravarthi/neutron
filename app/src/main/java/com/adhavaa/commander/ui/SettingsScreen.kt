package com.adhavaa.commander.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adhavaa.commander.BuildConfig
import com.adhavaa.commander.UiState
import com.adhavaa.commander.data.Settings
import com.adhavaa.commander.data.isReasonableNight
import com.adhavaa.commander.orders.OrderKind
import com.adhavaa.commander.orders.REMINDABLE
import com.adhavaa.commander.orders.formatHours
import com.adhavaa.commander.orders.reminderLabel
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val RULES = listOf(
    "Every order comes from published sleep research and tells you why.",
    "Bedtime aims for your full sleep need.",
    "Your schedule moves at most 30 minutes a day, so changes stay gradual. (The 30 minutes is a cautious " +
        "choice, not a measured limit.)",
    "Estimates are labelled as estimates.",
    "You report back and Commander adjusts. When its prediction doesn't match how you feel, the plan " +
        "changes, not you. (Morning check-ins arrive in a later update.)",
    "No guilt mechanics. A skipped order is just a skipped order.",
    "Wellness, not medicine. If you follow orders and still feel exhausted for weeks, see a doctor.",
)

private val SOURCES = listOf(
    "Ingre M, et al. Validating and extending the three process model of alertness in airline operations. " +
        "PLOS ONE 2014.",
    "Åkerstedt T, Folkard S, Portin C. Predictions from the three-process model of alertness. " +
        "Aviat Space Environ Med 2004.",
    "Watson NF, et al. Recommended amount of sleep for a healthy adult (AASM/SRS consensus). J Clin Sleep Med 2015.",
    "Kitamura S, et al. Estimating individual optimal sleep duration and potential sleep debt. Sci Rep 2016.",
    "Van Dongen HPA, et al. The cumulative cost of additional wakefulness. Sleep 2003.",
    "Drake C, et al. Caffeine effects on sleep taken 0, 3, or 6 hours before going to bed. J Clin Sleep Med 2013.",
    "Sletten TL, et al. Timing of sleep and its relationship with the endogenous melatonin rhythm. Front Neurol 2010.",
    "Monterastelli AJ, et al. The forbidden zone for sleep is more robust in adolescents compared to adults. " +
        "Front Sleep 2024.",
    "Khalsa SBS, et al. A phase response curve to single bright light pulses in human subjects. J Physiol 2003.",
)

@Immutable
data class SettingsActions(
    val onBack: () -> Unit = {},
    val onSchedule: (LocalTime, LocalTime) -> Unit = { _, _ -> },
    val onTargetWake: (LocalTime?) -> Unit = {},
    val onNeed: (Int) -> Unit = {},
    val onReminder: (OrderKind, Boolean) -> Unit = { _, _ -> },
    val onTestOrder: () -> Unit = {},
    val onCampaignStart: (LocalDate) -> Unit = {},
    val onEnableNotifications: () -> Unit = {},
    val onNeedAuto: (Boolean) -> Unit = {},
    val onFreeDays: (Set<DayOfWeek>) -> Unit = {},
    val onEnableDetection: () -> Unit = {},
)

@Composable
fun SettingsScreen(state: UiState.Ready, actions: SettingsActions, notificationsAllowed: Boolean, detectionOn: Boolean = false) {
    val s = state.settings
    val context = LocalContext.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

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
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SquareButton("Back to today", actions.onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = Hud.muted, modifier = Modifier.size(18.dp))
                }
                Text("SETTINGS", style = HudType.title, color = Hud.ink)
            }
        }
        item {
            HudPanel(title = "Order alerts", subtitle = "Notifications at the right minute") {
                if (!notificationsAllowed) {
                    Text(
                        "Notifications are off, so orders can't reach you.",
                        style = HudType.body,
                        color = Hud.amber,
                    )
                    HudButton("Allow notifications", actions.onEnableNotifications)
                }
                for (kind in REMINDABLE) {
                    ReminderSwitch(reminderLabel(kind), kind in s.reminders) { actions.onReminder(kind, it) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = actions.onTestOrder, enabled = notificationsAllowed) { Text("SEND TEST ORDER") }
                }
                Hint("Alerts use exact alarms and survive restarts. Commander isn't an alarm clock: use Set alarm on tomorrow's order for waking up.")
            }
        }
        item {
            HudPanel(title = "Usual night", subtitle = "Planned from these until real nights are tracked") {
                TimeRow("Fall asleep", s.bedtime) { if (isReasonableNight(it, s.wake)) actions.onSchedule(it, s.wake) }
                TimeRow("Wake up", s.wake) { if (isReasonableNight(s.bedtime, it)) actions.onSchedule(s.bedtime, it) }
                Hint("A change that makes the night shorter than 3 h or longer than 14 h is ignored. If you have to be up at a fixed time (class, work), make that your usual wake time.")
            }
        }
        item {
            HudPanel(title = "Heading", subtitle = "Target wake time") {
                TimeRow("Target wake", s.targetWake, placeholder = "Same as usual", initial = s.wake) { actions.onTargetWake(it) }
                if (s.targetWake != null) TextButton(onClick = { actions.onTargetWake(null) }) { Text("CLEAR TARGET") }
                Hint("Set an earlier target and Commander moves you there 30 minutes a day.")
            }
        }
        item {
            HudPanel(title = "Sleep need") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Learn from my nights", style = HudType.body, color = Hud.ink, modifier = Modifier.weight(1f))
                    Switch(
                        checked = s.needAuto, onCheckedChange = actions.onNeedAuto,
                        colors = SwitchDefaults.colors(checkedThumbColor = Hud.onMint, checkedTrackColor = Hud.green),
                    )
                }
                if (s.needAuto) {
                    KeyValue("Current estimate", formatHours(state.stats.needH), Hud.green)
                    Hint("Starts at 8 h and moves toward what you actually sleep on nights you wake without an alarm and aren't carrying much debt.")
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        OutlinedButton(onClick = { actions.onNeed(s.needMinutes - 15) }, enabled = s.needMinutes > Settings.MIN_NEED_MIN) { Text("− 15") }
                        Text(formatHours(s.needMinutes / 60.0), style = HudType.headline, color = Hud.green)
                        OutlinedButton(onClick = { actions.onNeed(s.needMinutes + 15) }, enabled = s.needMinutes < Settings.MAX_NEED_MIN) { Text("+ 15") }
                    }
                }
                Hint("Adults need 7 h or more (AASM/SRS consensus); in one study young men given 12 h in bed for 9 nights settled at about 8.4 h.")
            }
        }
        item {
            HudPanel(title = "Free days", subtitle = "Days without a fixed wake time") {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (d in DayOfWeek.entries) {
                        val on = d in s.freeDays
                        Text(
                            d.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
                            style = HudType.label,
                            color = if (on) Hud.onMint else Hud.muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (on) Hud.green else Hud.panelHi)
                                .clickable(role = Role.Checkbox) { actions.onFreeDays(if (on) s.freeDays - d else s.freeDays + d) }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
                Hint("Used for social jetlag and chronotype, which compare sleep before free days with sleep before workdays.")
            }
        }
        item {
            HudPanel(title = "Automatic detection") {
                KeyValue("Usage access", if (detectionOn) "On" else "Off", if (detectionOn) Hud.green else Hud.amber)
                if (!detectionOn) HudButton("Allow usage access", actions.onEnableDetection)
                Hint("Commander reads only when the screen turned on, to find when the phone went down and came back up. It never reads which apps you used.")
            }
        }
        item {
            val c = state.campaign
            HudPanel(title = "Campaign", subtitle = "Seven months of orders") {
                KeyValue("Started", c?.start?.format(dateFmt) ?: "Today", Hud.green)
                KeyValue("Ends", c?.end?.format(dateFmt) ?: "—")
                KeyValue("Day", c?.let { "${minOf(it.day, it.totalDays)} of ${it.totalDays}" } ?: "1", Hud.green)
                OutlinedButton(onClick = {
                    showDatePicker(context, c?.start ?: LocalDate.now()) { actions.onCampaignStart(it) }
                }) { Text("CHANGE START DATE") }
            }
        }
        item {
            HudPanel(title = "Rules of command") {
                RULES.forEachIndexed { i, rule -> Numbered(i + 1, rule) }
            }
        }
        item {
            HudPanel(title = "Why these orders", subtitle = "Sources") {
                SOURCES.forEach { Text("› $it", style = HudType.small, color = Hud.muted) }
                Hint("Full references, equations and every assumption are in MODEL.md in the project repo.")
            }
        }
        item {
            Text(
                "Commander ${BuildConfig.VERSION_NAME}. A wellness tool, not a medical device. No data leaves this phone.",
                style = HudType.small,
                color = Hud.dim,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}

@Composable
fun HudButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Hud.green, contentColor = Hud.onMint),
        shape = RoundedCornerShape(6.dp),
    ) { Text(text.uppercase()) }
}

@Composable
private fun ReminderSwitch(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = HudType.body, color = Hud.ink, modifier = Modifier.weight(1f))
        Switch(
            checked = on,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Hud.onMint,
                checkedTrackColor = Hud.green,
                uncheckedThumbColor = Hud.muted,
                uncheckedTrackColor = Hud.panelHi,
                uncheckedBorderColor = Hud.line,
            ),
        )
    }
}

@Composable
private fun Numbered(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(n.toString().padStart(2, '0'), style = HudType.value, color = Hud.green)
        Column { Text(text, style = HudType.body, color = Hud.ink) }
    }
}

@Composable
fun Hint(text: String) {
    Text(text, style = HudType.small, color = Hud.muted)
}
