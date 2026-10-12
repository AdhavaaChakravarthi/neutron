package com.adhavaa.commander.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import com.adhavaa.commander.container
import com.adhavaa.commander.orders.CommandData
import com.adhavaa.commander.orders.MAX_SCHEDULED
import com.adhavaa.commander.orders.Reminder
import com.adhavaa.commander.orders.upcomingReminders
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Keeps one exact alarm per upcoming reminder (next two plan days, at most [MAX_SCHEDULED]). Every change of
 * settings, every fired order, boot, clock change and app update calls [reschedule], so the set is always current.
 */
object OrderScheduler {
    const val ACTION = "com.adhavaa.commander.ORDER"
    const val EXTRA_KIND = "kind"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"
    const val EXTRA_AT = "at"

    fun timeFormatter(context: Context): (LocalDateTime) -> String {
        val f = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", Locale.getDefault())
        return { it.format(f) }
    }

    suspend fun reschedule(context: Context) {
        reschedule(context, context.container().commandData())
    }

    fun reschedule(context: Context, data: CommandData, now: LocalDateTime = LocalDateTime.now()): List<Reminder> {
        val am = context.getSystemService(AlarmManager::class.java)
        cancelAll(context, am)
        val upcoming = upcomingReminders(data, now, timeFormatter(context))
        upcoming.forEachIndexed { slot, r ->
            val millis = r.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val intent = baseIntent(context)
                .putExtra(EXTRA_KIND, r.kind.name)
                .putExtra(EXTRA_TITLE, r.title)
                .putExtra(EXTRA_TEXT, r.text)
                .putExtra(EXTRA_AT, millis)
            val pi = PendingIntent.getBroadcast(
                context, slot, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            // USE_EXACT_ALARM is granted at install for a sideloaded app; fall back to inexact if it ever isn't.
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        }
        return upcoming
    }

    private fun baseIntent(context: Context) = Intent(context, OrderReceiver::class.java).setAction(ACTION)

    private fun cancelAll(context: Context, am: AlarmManager) {
        for (slot in 0 until MAX_SCHEDULED) {
            val pi = PendingIntent.getBroadcast(
                context, slot, baseIntent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
            ) ?: continue
            am.cancel(pi)
            pi.cancel()
        }
    }
}
