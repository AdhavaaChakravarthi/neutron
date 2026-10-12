package com.adhavaa.commander.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.adhavaa.commander.orders.OrderKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** An order alarm fired: show it (unless it is badly late), then schedule what comes next. */
class OrderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != OrderScheduler.ACTION) return
        val kind = intent.getStringExtra(OrderScheduler.EXTRA_KIND)?.let { name ->
            OrderKind.entries.firstOrNull { it.name == name }
        } ?: return
        val at = intent.getLongExtra(OrderScheduler.EXTRA_AT, 0L)
        val late = System.currentTimeMillis() - at
        if (late < STALE_MS) {
            OrderNotifier.post(
                context,
                OrderNotifier.idFor(kind),
                intent.getStringExtra(OrderScheduler.EXTRA_TITLE).orEmpty(),
                intent.getStringExtra(OrderScheduler.EXTRA_TEXT).orEmpty(),
                at,
            )
        }
        rescheduleAsync(this, context)
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
        rescheduleAsync(this, context)
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

private fun rescheduleAsync(receiver: BroadcastReceiver, context: Context) {
    val pending: BroadcastReceiver.PendingResult? = receiver.goAsync()
    val app = context.applicationContext
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            OrderScheduler.reschedule(app)
        } catch (e: Exception) {
            // A failed reschedule must never crash a broadcast; the next app open or order rebuilds the alarms.
            Log.w("Commander", "Rescheduling orders failed", e)
        } finally {
            pending?.finish()
        }
    }
}
