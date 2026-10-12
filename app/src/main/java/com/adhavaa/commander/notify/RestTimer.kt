package com.adhavaa.commander.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The rest timer between sets (ACSM 2009 rest periods, MODEL.md 11.1). An exact alarm posts "rest over" even with the
 * screen locked; the Train screen shows the same countdown.
 */
object RestTimer {
    private const val ACTION = "com.adhavaa.commander.REST_OVER"
    private const val EXTRA_TEXT = "text"
    const val NOTIFICATION_ID = 998

    private fun pending(context: Context, text: String = "", flags: Int = PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent? =
        PendingIntent.getBroadcast(
            context, 0,
            Intent(context, RestReceiver::class.java).setAction(ACTION).putExtra(EXTRA_TEXT, text),
            PendingIntent.FLAG_IMMUTABLE or flags,
        )

    fun start(context: Context, endAtMillis: Long, next: String) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, "Next: $next") ?: return
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMillis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMillis, pi)
        }
    }

    fun cancel(context: Context) {
        val pi = pending(context, flags = PendingIntent.FLAG_NO_CREATE) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
    }

    class RestReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION) return
            OrderNotifier.post(context, NOTIFICATION_ID, "Rest over", intent.getStringExtra(EXTRA_TEXT).orEmpty())
        }
    }
}
