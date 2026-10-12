package com.adhavaa.commander.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.adhavaa.commander.MainActivity
import com.adhavaa.commander.R
import com.adhavaa.commander.orders.OrderKind

/** Posts Commander's orders as notifications on one high-importance channel. */
object OrderNotifier {
    const val CHANNEL = "orders"
    private const val TEST_ID = 999
    private const val GREEN = 0xFF6BEA8F.toInt()

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val channel = NotificationChannel(CHANNEL, "Orders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Commander's orders through the day: wake, caffeine cutoff, wind-down, lights out."
            enableVibration(true)
        }
        nm.createNotificationChannel(channel)
    }

    fun permitted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED && NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun idFor(kind: OrderKind): Int = 100 + kind.ordinal

    fun post(context: Context, id: Int, title: String, text: String, whenMillis: Long = System.currentTimeMillis()) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_order)
            .setColor(GREEN)
            .setContentTitle(title.uppercase())
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSubText("Order")
            .setWhen(whenMillis)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun postTest(context: Context) =
        post(context, TEST_ID, "Test order", "Orders are reaching you. Stand by for the next one.")
}
