package com.adhavaa.commander.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.time.LocalTime

/** A labelled time that opens the system time picker when tapped. */
@Composable
fun TimeRow(
    label: String,
    value: LocalTime?,
    placeholder: String = "Not set",
    initial: LocalTime = LocalTime.of(7, 0),
    onPicked: (LocalTime) -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Hud.panelHi)
            .border(1.dp, Hud.line, RoundedCornerShape(6.dp))
            .clickable(onClickLabel = "Change $label", role = Role.Button) {
                showTimePicker(context, value ?: initial, onPicked)
            }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = HudType.body, color = Hud.ink, modifier = Modifier.weight(1f))
        Text(
            value?.let { formatTime(context, it) } ?: placeholder,
            style = HudType.bigValue,
            color = if (value != null) Hud.green else Hud.muted,
        )
    }
}
