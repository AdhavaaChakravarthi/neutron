package com.adhavaa.commander.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adhavaa.commander.orders.DEFAULT_REMINDERS
import com.adhavaa.commander.orders.OrderKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

private fun fromMinutes(m: Int): LocalTime = LocalTime.of((m / 60) % 24, m % 60)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    private object Keys {
        val onboarded = booleanPreferencesKey("onboarded")
        val bedtime = intPreferencesKey("bedtime_min")
        val wake = intPreferencesKey("wake_min")
        val targetWake = intPreferencesKey("target_wake_min")
        val need = intPreferencesKey("need_min")
        val reminders = stringSetPreferencesKey("reminders")
        val campaignStart = longPreferencesKey("campaign_start_epoch_day")
        val needAuto = booleanPreferencesKey("need_auto")
        val freeDays = stringSetPreferencesKey("free_days")
    }

    val settings: Flow<Settings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            Settings(
                onboarded = p[Keys.onboarded] ?: false,
                bedtime = p[Keys.bedtime]?.let(::fromMinutes) ?: Settings.DEFAULT_BEDTIME,
                wake = p[Keys.wake]?.let(::fromMinutes) ?: Settings.DEFAULT_WAKE,
                targetWake = p[Keys.targetWake]?.let(::fromMinutes),
                needMinutes = (p[Keys.need] ?: Settings.DEFAULT_NEED_MIN)
                    .coerceIn(Settings.MIN_NEED_MIN, Settings.MAX_NEED_MIN),
                reminders = p[Keys.reminders]
                    ?.mapNotNull { name -> OrderKind.entries.firstOrNull { it.name == name } }
                    ?.toSet()
                    ?: DEFAULT_REMINDERS,
                campaignStart = p[Keys.campaignStart]?.let(LocalDate::ofEpochDay),
                needAuto = p[Keys.needAuto] ?: true,
                freeDays = p[Keys.freeDays]
                    ?.mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name } }
                    ?.toSet()
                    ?: Settings.DEFAULT_FREE_DAYS,
            )
        }

    suspend fun completeOnboarding(bed: LocalTime, wake: LocalTime, today: LocalDate) {
        store.edit {
            it[Keys.bedtime] = minutesOf(bed)
            it[Keys.wake] = minutesOf(wake)
            it[Keys.onboarded] = true
            if (it[Keys.campaignStart] == null) it[Keys.campaignStart] = today.toEpochDay()
        }
    }

    suspend fun setSchedule(bed: LocalTime, wake: LocalTime) {
        store.edit {
            it[Keys.bedtime] = minutesOf(bed)
            it[Keys.wake] = minutesOf(wake)
        }
    }

    suspend fun setTargetWake(target: LocalTime?) {
        store.edit { if (target == null) it.remove(Keys.targetWake) else it[Keys.targetWake] = minutesOf(target) }
    }

    suspend fun setNeedMinutes(minutes: Int) {
        store.edit { it[Keys.need] = minutes.coerceIn(Settings.MIN_NEED_MIN, Settings.MAX_NEED_MIN) }
    }

    suspend fun setReminder(kind: OrderKind, on: Boolean, current: Set<OrderKind>) {
        val next = if (on) current + kind else current - kind
        store.edit { it[Keys.reminders] = next.map { k -> k.name }.toSet() }
    }

    suspend fun setCampaignStart(date: LocalDate) {
        store.edit { it[Keys.campaignStart] = date.toEpochDay() }
    }

    suspend fun setNeedAuto(auto: Boolean) {
        store.edit { it[Keys.needAuto] = auto }
    }

    suspend fun setFreeDays(days: Set<DayOfWeek>) {
        store.edit { it[Keys.freeDays] = days.map { d -> d.name }.toSet() }
    }

    suspend fun resetOnboarding() {
        store.edit { it[Keys.onboarded] = false }
    }
}
