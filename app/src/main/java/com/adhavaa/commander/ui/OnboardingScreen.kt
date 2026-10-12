package com.adhavaa.commander.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adhavaa.commander.data.Settings
import com.adhavaa.commander.data.isReasonableNight
import java.time.LocalTime

@Composable
fun OnboardingScreen(settings: Settings, onDone: (LocalTime, LocalTime) -> Unit) {
    // Saved as minutes so the choice survives rotation.
    var bedMin by rememberSaveable { mutableIntStateOf(settings.bedtime.hour * 60 + settings.bedtime.minute) }
    var wakeMin by rememberSaveable { mutableIntStateOf(settings.wake.hour * 60 + settings.wake.minute) }
    val bed = LocalTime.of(bedMin / 60, bedMin % 60)
    val wake = LocalTime.of(wakeMin / 60, wakeMin % 60)
    val ok = isReasonableNight(bed, wake)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Hud.bg)
            .contours()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Mark(40.dp)
            Text("COMMANDER", style = HudType.headline.copy(fontSize = 28.sp, letterSpacing = 2.sp), color = Hud.ink)
        }
        Text(
            "Orders for your day from published sleep research: when to wake, when you're sharpest, when to stop " +
                "caffeine, when to wind down, when to sleep. Each order tells you why.",
            style = HudType.body,
            color = Hud.ink,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HudChip("7-month campaign", Tone.GREEN)
            HudChip("Stays on this phone")
        }
        Spacer(Modifier.height(4.dp))
        HudPanel(title = "Your usual night", subtitle = "Commander plans from this until it can see real nights") {
            TimeRow("I fall asleep at", bed) { bedMin = it.hour * 60 + it.minute }
            TimeRow("I wake up at", wake) { wakeMin = it.hour * 60 + it.minute }
            if (!ok) Text("That night is shorter than 3 h or longer than 14 h. Check the times.", style = HudType.small, color = Hud.amber)
        }
        Hint("Next, Android asks to allow notifications. That's how orders reach you at the right minute.")
        HudButton("Report for duty", onClick = { onDone(bed, wake) }, enabled = ok)
        Text("A wellness tool, not a medical device.", style = HudType.small, color = Hud.dim)
    }
}
