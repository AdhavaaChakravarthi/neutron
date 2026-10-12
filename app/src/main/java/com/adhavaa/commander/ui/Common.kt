package com.adhavaa.commander.ui

import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formats times in the phone's 12/24-hour setting. */
@Composable
fun rememberTimeFormatter(): (LocalDateTime) -> String {
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    return remember(is24) {
        val f = DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", Locale.getDefault())
        val format: (LocalDateTime) -> String = { it.format(f) }
        format
    }
}

fun formatTime(context: Context, t: LocalTime): String {
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return t.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

fun showTimePicker(context: Context, initial: LocalTime, onPicked: (LocalTime) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) },
        initial.hour,
        initial.minute,
        DateFormat.is24HourFormat(context),
    ).show()
}
