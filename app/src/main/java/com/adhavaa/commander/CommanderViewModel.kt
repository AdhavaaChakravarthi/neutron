package com.adhavaa.commander

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adhavaa.commander.data.Settings
import com.adhavaa.commander.data.SettingsStore
import com.adhavaa.commander.model.TodayOrders
import com.adhavaa.commander.model.UsualSchedule
import com.adhavaa.commander.model.ordersFor
import com.adhavaa.commander.model.planDateFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

sealed interface UiState {
    data object Loading : UiState
    data class Ready(val settings: Settings, val now: LocalDateTime, val orders: TodayOrders) : UiState
    data class Failed(val message: String) : UiState
}

class CommanderViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)

    private val clock: Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(30_000)
        }
    }

    // Orders only change when settings or the plan date change; the clock just moves the "now" marker.
    private var cacheKey: Pair<Settings, LocalDate>? = null
    private var cached: TodayOrders? = null

    private fun todayOrders(s: Settings, now: LocalDateTime): TodayOrders {
        val usual = UsualSchedule(s.bedtime, s.wake)
        val date = planDateFor(now, usual)
        val key = s to date
        cached?.takeIf { cacheKey == key }?.let { return it }
        return ordersFor(date, usual, needH = s.needMinutes / 60.0, targetWake = s.targetWake).also {
            cacheKey = key
            cached = it
        }
    }

    val state: StateFlow<UiState> = combine(store.settings, clock) { s, now ->
        try {
            UiState.Ready(s, now, todayOrders(s, now))
        } catch (e: RuntimeException) {
            UiState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun completeOnboarding(bed: LocalTime, wake: LocalTime) {
        viewModelScope.launch { store.completeOnboarding(bed, wake) }
    }

    fun setSchedule(bed: LocalTime, wake: LocalTime) {
        viewModelScope.launch { store.setSchedule(bed, wake) }
    }

    fun setTargetWake(target: LocalTime?) {
        viewModelScope.launch { store.setTargetWake(target) }
    }

    fun setNeedMinutes(minutes: Int) {
        viewModelScope.launch { store.setNeedMinutes(minutes) }
    }

    fun resetOnboarding() {
        viewModelScope.launch { store.resetOnboarding() }
    }
}
