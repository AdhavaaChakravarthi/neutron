package com.adhavaa.commander.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adhavaa.commander.BuildConfig
import com.adhavaa.commander.UiState
import com.adhavaa.commander.data.Settings
import com.adhavaa.commander.data.isReasonableNight
import com.adhavaa.commander.orders.formatHours
import java.time.LocalTime

private val RULES = listOf(
    "Every order comes from published sleep research and tells you why.",
    "Bedtime aims for your full sleep need.",
    "Your schedule moves at most 30 minutes a day. Your body clock can't shift faster than that.",
    "Estimates are labelled as estimates.",
    "You report back and Commander adjusts. When its prediction doesn't match how you feel, the plan " +
        "changes, not you. (Morning check-ins arrive in a later update.)",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: UiState.Ready,
    onBack: () -> Unit,
    onSchedule: (LocalTime, LocalTime) -> Unit,
    onTargetWake: (LocalTime?) -> Unit,
    onNeed: (Int) -> Unit,
) {
    val s = state.settings
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Section("Your usual night") }
            item {
                Column {
                    TimeRow("Usually fall asleep", s.bedtime) { if (isReasonableNight(it, s.wake)) onSchedule(it, s.wake) }
                    TimeRow("Usually wake up", s.wake) { if (isReasonableNight(s.bedtime, it)) onSchedule(s.bedtime, it) }
                    Hint("Commander plans from these until it can see your real nights. A change that makes the night shorter than 3 h or longer than 14 h is ignored.")
                }
            }
            item { Section("Where you're heading") }
            item {
                Column {
                    TimeRow("Target wake time", s.targetWake, placeholder = "Same as usual", initial = s.wake) { onTargetWake(it) }
                    if (s.targetWake != null) {
                        TextButton(onClick = { onTargetWake(null) }) { Text("Clear target") }
                    }
                    Hint("Set an earlier target and Commander moves you there 30 minutes a day.")
                }
            }
            item { Section("Sleep need") }
            item { NeedStepper(s.needMinutes, onNeed) }
            item {
                Hint(
                    "Starts at 8 h. Adults need 7 h or more (AASM/SRS consensus); in one study young men given " +
                        "12 h in bed for 9 nights settled at about 8.4 h. Commander will learn yours from nights you " +
                        "wake without an alarm.",
                )
            }
            item { Section("Rules of command") }
            items(RULES.size) { i -> Bullet(RULES[i]) }
            item { Section("Why these orders") }
            items(SOURCES.size) { i -> Bullet(SOURCES[i], small = true) }
            item { Hint("Full references, equations and every assumption are in MODEL.md in the project repo.") }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Hint("Commander ${BuildConfig.VERSION_NAME}. A wellness tool, not a medical device. No data leaves this phone.")
            }
        }
    }
}

@Composable
private fun NeedStepper(minutes: Int, onNeed: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OutlinedButton(
            onClick = { onNeed(minutes - 15) },
            enabled = minutes > Settings.MIN_NEED_MIN,
        ) { Text("− 15 min") }
        Text(formatHours(minutes / 60.0), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        OutlinedButton(
            onClick = { onNeed(minutes + 15) },
            enabled = minutes < Settings.MAX_NEED_MIN,
        ) { Text("+ 15 min") }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Bullet(text: String, small: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", color = MaterialTheme.colorScheme.primary)
        Column {
            Text(
                text,
                style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
