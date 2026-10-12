package com.adhavaa.commander.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
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

fun showDatePicker(context: Context, initial: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).show()
}

/** Opens the clock app with an alarm filled in; Commander itself is not an alarm clock. */
fun openSetAlarm(context: Context, at: LocalDateTime) {
    val intent = Intent(AlarmClock.ACTION_SET_ALARM)
        .putExtra(AlarmClock.EXTRA_HOUR, at.hour)
        .putExtra(AlarmClock.EXTRA_MINUTES, at.minute)
        .putExtra(AlarmClock.EXTRA_MESSAGE, "Commander: up")
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No clock app found to set the alarm.", Toast.LENGTH_SHORT).show()
    }
}

/** System screen where a denied notification permission can be turned back on. */
fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )
}
