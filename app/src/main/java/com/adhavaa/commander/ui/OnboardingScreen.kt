package com.adhavaa.commander.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.adhavaa.commander.data.Settings
import com.adhavaa.commander.data.isReasonableNight
import java.time.LocalTime

@Composable
fun OnboardingScreen(settings: Settings, onDone: (LocalTime, LocalTime) -> Unit) {
    // Saved as minutes so the choice survives rotation.
    var bedMin by rememberSaveable { mutableStateOf(settings.bedtime.hour * 60 + settings.bedtime.minute) }
    var wakeMin by rememberSaveable { mutableStateOf(settings.wake.hour * 60 + settings.wake.minute) }
    val bed = LocalTime.of(bedMin / 60, bedMin % 60)
    val wake = LocalTime.of(wakeMin / 60, wakeMin % 60)
    val ok = isReasonableNight(bed, wake)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Commander", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        Text(
            "Orders for your day, from published sleep research. Each order tells you why. " +
                "Everything stays on this phone.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "A wellness tool, not a medical device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text("Your usual night", style = MaterialTheme.typography.titleMedium)
        TimeRow("I usually fall asleep at", bed) { bedMin = it.hour * 60 + it.minute }
        TimeRow("I usually wake up at", wake) { wakeMin = it.hour * 60 + it.minute }
        if (!ok) {
            Text(
                "That night is shorter than 3 h or longer than 14 h. Check the times.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            "Until Commander can see your real nights, it plans from this. You can change it any time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { onDone(bed, wake) }, enabled = ok, modifier = Modifier.fillMaxWidth()) {
            Text("Report for duty")
        }
    }
}
