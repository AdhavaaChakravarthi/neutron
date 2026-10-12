package com.adhavaa.commander.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalTime

data class Settings(
    val onboarded: Boolean,
    val bedtime: LocalTime,
    val wake: LocalTime,
    val targetWake: LocalTime?,
    val needMinutes: Int,
) {
    companion object {
        val DEFAULT_BEDTIME: LocalTime = LocalTime.of(23, 30)
        val DEFAULT_WAKE: LocalTime = LocalTime.of(7, 30)
        const val DEFAULT_NEED_MIN = 480
        const val MIN_NEED_MIN = 360
        const val MAX_NEED_MIN = 600
    }
}

/** Usual nights must be 3-14 h so the model has something sensible to work with. */
fun isReasonableNight(bed: LocalTime, wake: LocalTime): Boolean {
    val minutes = Math.floorMod(toMinutes(wake) - toMinutes(bed), 24 * 60)
    return minutes in 180..840
}

private fun toMinutes(t: LocalTime): Int = t.hour * 60 + t.minute
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
            )
        }

    suspend fun completeOnboarding(bed: LocalTime, wake: LocalTime) {
        store.edit {
            it[Keys.bedtime] = toMinutes(bed)
            it[Keys.wake] = toMinutes(wake)
            it[Keys.onboarded] = true
        }
    }

    suspend fun setSchedule(bed: LocalTime, wake: LocalTime) {
        store.edit {
            it[Keys.bedtime] = toMinutes(bed)
            it[Keys.wake] = toMinutes(wake)
        }
    }

    suspend fun setTargetWake(target: LocalTime?) {
        store.edit { if (target == null) it.remove(Keys.targetWake) else it[Keys.targetWake] = toMinutes(target) }
    }

    suspend fun setNeedMinutes(minutes: Int) {
        store.edit { it[Keys.need] = minutes.coerceIn(Settings.MIN_NEED_MIN, Settings.MAX_NEED_MIN) }
    }

    suspend fun resetOnboarding() {
        store.edit { it[Keys.onboarded] = false }
    }
}
