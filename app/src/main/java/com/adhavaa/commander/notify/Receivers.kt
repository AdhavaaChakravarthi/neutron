package com.adhavaa.commander.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.adhavaa.commander.container
import com.adhavaa.commander.model.SleepKind
import com.adhavaa.commander.model.SleepRecord
import com.adhavaa.commander.orders.OrderKind
import com.adhavaa.commander.orders.detectNight
import com.adhavaa.commander.orders.detectionWindow
import com.adhavaa.commander.orders.formatHours
import com.adhavaa.commander.orders.hoursBetween
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** An order alarm fired: show it (unless badly late), then schedule what comes next. */
class OrderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != OrderScheduler.ACTION) return
        val kind = intent.getStringExtra(OrderScheduler.EXTRA_KIND)?.let { name ->
            OrderKind.entries.firstOrNull { it.name == name }
        } ?: return
        val at = intent.getLongExtra(OrderScheduler.EXTRA_AT, 0L)
        val late = System.currentTimeMillis() - at
        if (kind == OrderKind.CHECK_IN) {
            runAsync(this, context) {
                if (late < STALE_MS) morningReport(context, at)
                OrderScheduler.reschedule(context.applicationContext)
            }
            return
        }
        if (kind == OrderKind.WEIGH_IN) {
            // Already weighed in today: nothing to say.
            runAsync(this, context) {
                val today = LocalDateTime.ofInstant(Instant.ofEpochMilli(at), ZoneId.systemDefault()).toLocalDate()
                if (late < STALE_MS && context.container().fuel.weightOn(today) == null) {
                    OrderNotifier.post(
                        context,
                        OrderNotifier.idFor(kind),
                        intent.getStringExtra(OrderScheduler.EXTRA_TITLE).orEmpty(),
                        intent.getStringExtra(OrderScheduler.EXTRA_TEXT).orEmpty(),
                        at,
                    )
                }
                OrderScheduler.reschedule(context.applicationContext)
            }
            return
        }
        if (late < STALE_MS) {
            OrderNotifier.post(
                context,
                OrderNotifier.idFor(kind),
                intent.getStringExtra(OrderScheduler.EXTRA_TITLE).orEmpty(),
                intent.getStringExtra(OrderScheduler.EXTRA_TEXT).orEmpty(),
                at,
            )
        }
        runAsync(this, context) { OrderScheduler.reschedule(context.applicationContext) }
    }

    /** Detects last night from screen use (saving it unconfirmed) and asks for a ten-second confirmation. */
    private suspend fun morningReport(context: Context, atMs: Long) {
        val c = context.container()
        val data = c.commandData()
        val now = LocalDateTime.now()
        val wakeDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(atMs), ZoneId.systemDefault()).toLocalDate()
        if (data.nightRecorded(wakeDate)) return
        val fmt = OrderScheduler.timeFormatter(context)
        val detected = if (PhoneUsage.hasAccess(context)) {
            val w = detectionWindow(wakeDate, data.habitual(now))
            detectNight(w, PhoneUsage.screenOnTimes(context, w.start, if (now.isBefore(w.end)) now else w.end), now)
        } else {
            null
        }
        val text = if (detected != null) {
            c.sleep.save(SleepRecord(detected.start, detected.end, SleepKind.NIGHT), "PHONE", confirmed = false)
            "Phone down ${fmt(detected.start)}, up ${fmt(detected.end)}: ${formatHours(hoursBetween(detected.start, detected.end))}. " +
                "Tap to confirm or fix it."
        } else {
            "When did you sleep last night? Tap to log it."
        }
        OrderNotifier.post(context, OrderNotifier.idFor(OrderKind.CHECK_IN), "Morning report", text, atMs)
    }

    companion object {
        /** An order more than 20 min late (phone was off) is skipped rather than shown out of context. */
        const val STALE_MS = 20 * 60 * 1000L
    }
}

/** Alarms don't survive reboots, and clock or time-zone changes move every order: rebuild them. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        runAsync(this, context) { OrderScheduler.reschedule(context.applicationContext) }
    }

    companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}

private fun runAsync(receiver: BroadcastReceiver, context: Context, block: suspend () -> Unit) {
    val pending: BroadcastReceiver.PendingResult? = receiver.goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            block()
        } catch (e: Exception) {
            // A failure here must never crash a broadcast; the next app open or order rebuilds everything.
            Log.w("Commander", "Order work failed for ${context.packageName}", e)
        } finally {
            pending?.finish()
        }
    }
}
