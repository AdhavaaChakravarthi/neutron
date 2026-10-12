package com.adhavaa.commander.notify

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import com.adhavaa.commander.data.Settings
import com.adhavaa.commander.orders.CommandData
import com.adhavaa.commander.orders.OrderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The reminder pipeline end to end on a simulated phone: alarms get set, and a fired order becomes a notification. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotifyTest {
    private val app = RuntimeEnvironment.getApplication()
    private val settings = Settings(
        onboarded = true, bedtime = LocalTime.of(23, 30), wake = LocalTime.of(7, 30), targetWake = null,
        needMinutes = 480, campaignStart = LocalDate.of(2026, 10, 11),
    )

    @Before
    fun grant() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun alarms() = shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms
    private fun notifications() = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications

    @Test
    fun reschedulingSetsOneAlarmPerUpcomingOrder() {
        val upcoming = OrderScheduler.reschedule(app, CommandData(settings, emptyList(), emptySet()), LocalDateTime.of(2026, 10, 12, 10, 0))
        assertTrue("expected upcoming orders", upcoming.size >= 5)
        assertEquals(upcoming.size, alarms().size)
        assertEquals(OrderKind.PEAK, upcoming.first().kind)
    }

    @Test
    fun reschedulingReplacesRatherThanStacks() {
        OrderScheduler.reschedule(app, CommandData(settings, emptyList(), emptySet()), LocalDateTime.of(2026, 10, 12, 10, 0))
        val second = OrderScheduler.reschedule(app, CommandData(settings, emptyList(), emptySet()), LocalDateTime.of(2026, 10, 12, 22, 30))
        assertEquals(second.size, alarms().size)
    }

    @Test
    fun noRemindersMeansNoAlarms() {
        OrderScheduler.reschedule(app, CommandData(settings.copy(reminders = emptySet()), emptyList(), emptySet()), LocalDateTime.of(2026, 10, 12, 10, 0))
        assertEquals(0, alarms().size)
    }

    @Test
    fun firedOrderPostsANotification() {
        app.sendBroadcast(orderIntent(System.currentTimeMillis()))
        shadowOf(Looper.getMainLooper()).idle()
        val posted = notifications()
        assertEquals(1, posted.size)
        assertEquals("LIGHTS OUT", posted.single().extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(OrderNotifier.CHANNEL, posted.single().channelId)
    }

    @Test
    fun badlyLateOrderIsSkipped() {
        app.sendBroadcast(orderIntent(System.currentTimeMillis() - 45 * 60 * 1000L))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, notifications().size)
    }

    @Test
    fun testOrderArrives() {
        OrderNotifier.postTest(app)
        assertEquals("TEST ORDER", notifications().single().extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }

    private fun orderIntent(at: Long) = Intent(app, OrderReceiver::class.java)
        .setAction(OrderScheduler.ACTION)
        .putExtra(OrderScheduler.EXTRA_KIND, OrderKind.LIGHTS_OUT.name)
        .putExtra(OrderScheduler.EXTRA_TITLE, "Lights out")
        .putExtra(OrderScheduler.EXTRA_TEXT, "Now. Up at 7:30 AM.")
        .putExtra(OrderScheduler.EXTRA_AT, at)
}
