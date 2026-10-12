package com.adhavaa.commander.notify

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Reads only when the screen turned on (UsageEvents SCREEN_INTERACTIVE), never which apps were used. Needs the
 * "Usage access" special permission, which the user grants in system settings.
 */
object PhoneUsage {
    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun screenOnTimes(context: Context, from: LocalDateTime, to: LocalDateTime): List<LocalDateTime> {
        if (!to.isAfter(from)) return emptyList()
        val zone = ZoneId.systemDefault()
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val events = usm.queryEvents(from.atZone(zone).toInstant().toEpochMilli(), to.atZone(zone).toInstant().toEpochMilli())
        val e = UsageEvents.Event()
        val out = ArrayList<LocalDateTime>()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.SCREEN_INTERACTIVE) {
                out += LocalDateTime.ofInstant(Instant.ofEpochMilli(e.timeStamp), zone)
            }
        }
        return out
    }

    fun openSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
