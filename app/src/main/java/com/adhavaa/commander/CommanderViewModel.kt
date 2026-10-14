package com.adhavaa.commander

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adhavaa.commander.data.Settings
import com.adhavaa.commander.data.StoredSleep
import com.adhavaa.commander.fuel.FuelData
import com.adhavaa.commander.fuel.FuelPicture
import com.adhavaa.commander.fuel.Nutrients
import com.adhavaa.commander.fuel.fuelPicture
import com.adhavaa.commander.fuel.totals
import com.adhavaa.commander.stats.StatsPicture
import com.adhavaa.commander.stats.statsPicture
import com.adhavaa.commander.train.TrainData
import com.adhavaa.commander.train.swimStatus
import com.adhavaa.commander.train.TrainPicture
import com.adhavaa.commander.train.trainPicture
import com.adhavaa.commander.model.SleepKind
import com.adhavaa.commander.model.SleepPicture
import com.adhavaa.commander.model.SleepRecord
import com.adhavaa.commander.model.SleepStats
import com.adhavaa.commander.model.TodayOrders
import com.adhavaa.commander.notify.OrderNotifier
import com.adhavaa.commander.notify.OrderScheduler
import com.adhavaa.commander.notify.PhoneUsage
import com.adhavaa.commander.orders.Campaign
import com.adhavaa.commander.orders.CommandData
import com.adhavaa.commander.orders.DetectedNight
import com.adhavaa.commander.orders.OrderKind
import com.adhavaa.commander.orders.campaign
import com.adhavaa.commander.orders.detectNight
import com.adhavaa.commander.orders.detectionWindow
import com.adhavaa.commander.orders.needsMorningReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** What last night looks like on the morning report. */
data class MorningReport(
    val date: LocalDate,
    /** A saved night waiting for confirmation, if any. */
    val pending: StoredSleep?,
    /** What the phone saw, when nothing is saved yet. */
    val detected: DetectedNight?,
    val usageAccess: Boolean,
)

sealed interface UiState {
    data object Loading : UiState
    data class Ready(
        val settings: Settings,
        val now: LocalDateTime,
        val orders: TodayOrders,
        val campaign: Campaign?,
        val stats: SleepStats,
        val sleep: List<StoredSleep>,
        val skipped: Set<LocalDate>,
        val done: Set<OrderKind>,
        val report: MorningReport?,
        /** Today's fuel numbers, for the weigh-in order and the brief. */
        val fuel: FuelPicture? = null,
        val eaten: Nutrients = Nutrients.ZERO,
        /** Today's training, for the Train and Mobility orders. */
        val train: TrainPicture? = null,
        /** The last 21 days as a stats hexagon (MODEL.md section 12). */
        val hexagon: StatsPicture? = null,
    ) : UiState
    data class Failed(val message: String) : UiState
}

class CommanderViewModel(private val app: Application) : AndroidViewModel(app) {
    private val c = app.container()

    private val clock: Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(30_000)
        }
    }

    private val data: Flow<CommandData> = combine(c.settings.settings, c.sleep.observeSleep(), c.sleep.observeSkipped()) { s, sleep, skipped ->
        CommandData(s, sleep.map { it.record }, skipped)
    }.distinctUntilChanged()

    // The picture only changes when data or the plan day changes; the clock just moves "now".
    private var cacheKey: Pair<CommandData, LocalDate>? = null
    private var cached: SleepPicture? = null

    private fun picture(d: CommandData, date: LocalDate): SleepPicture {
        val key = d to date
        cached?.takeIf { cacheKey == key }?.let { return it }
        return d.picture(date).also {
            cacheKey = key
            cached = it
        }
    }

    private val detected = MutableStateFlow<Pair<LocalDate, DetectedNight?>?>(null)
    private val usageAccess = MutableStateFlow(PhoneUsage.hasAccess(app))

    private val fuelData: Flow<FuelData> = combine(
        c.settings.settings.map { it.fuel }, c.fuel.observeWeights(), c.fuel.observeDailyKcal(), c.fuel.observeIncomplete(),
    ) { s, w, k, inc -> FuelData(s, w, k, inc) }.distinctUntilChanged()

    // Swimming is paused until Fuel's trend reaches the mark (D-77), so Train reads the fuel data too.
    private val trainData: Flow<TrainData> = combine(
        c.settings.settings.map { it.train }, c.train.observeWorkouts(), c.train.observeSets(), c.train.observeStandDowns(), fuelData,
    ) { t, w, sets, sd, fd -> TrainData(t, w, sets, sd, swimStatus(fd, t.swimPauseKg, LocalDate.now())) }.distinctUntilChanged()

    private data class Extras(
        val detected: Pair<LocalDate, DetectedNight?>?,
        val access: Boolean,
        val fuel: Pair<FuelPicture, Nutrients>,
        val train: TrainData,
        val fuelData: FuelData,
    )

    // The hexagon changes once a day, or when a log changes; the 30-second clock doesn't rebuild it.
    private var hexKey: List<Any?>? = null
    private var hexCached: StatsPicture? = null

    private fun hexagonFor(d: CommandData, date: LocalDate, needH: Double, fuel: FuelData, train: TrainData): StatsPicture {
        val key = listOf(d, date, needH, fuel, train)
        hexCached?.takeIf { hexKey == key }?.let { return it }
        return statsPicture(date, d.sleep, d.skipped, needH, fuel, train).also {
            hexKey = key
            hexCached = it
        }
    }

    // Food days are calendar days. Today's picture is rebuilt when the date turns or fuel data changes.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val fuelToday: Flow<Pair<FuelPicture, Nutrients>> = clock.map { it.toLocalDate() }.distinctUntilChanged()
        .flatMapLatest { d -> combine(fuelData, c.fuel.observeLog(d)) { fd, log -> fuelPicture(fd, d, d) to totals(log) } }

    val state: StateFlow<UiState> = combine(
        data, c.sleep.observeSleep(), c.sleep.observeDone(), clock,
        combine(detected, usageAccess, fuelToday, trainData, fuelData) { d, a, f, t, fd -> Extras(d, a, f, t, fd) },
    ) { d, stored, done, now, x ->
        val det = x.detected
        val access = x.access
        val fuel = x.fuel
        try {
            val date = d.planDate(now)
            val p = picture(d, date)
            val pending = stored.firstOrNull { it.record.kind == SleepKind.NIGHT && it.record.end.toLocalDate() == date && !it.confirmed }
            val report = when {
                pending != null -> MorningReport(date, pending, null, access)
                needsMorningReport(d, now) -> MorningReport(date, null, det?.takeIf { it.first == date }?.second, access)
                else -> null
            }
            UiState.Ready(
                settings = d.settings,
                now = now,
                orders = p.orders,
                campaign = d.settings.campaignStart?.let { campaign(it, now.toLocalDate()) },
                stats = p.stats,
                sleep = stored,
                skipped = d.skipped,
                done = done[date].orEmpty(),
                report = report,
                fuel = fuel.first,
                eaten = fuel.second,
                train = trainPicture(x.train, now.toLocalDate(), now.toLocalDate(), p.stats.debtH),
                hexagon = hexagonFor(d, date, p.stats.needH, x.fuelData, x.train),
            )
        } catch (e: RuntimeException) {
            UiState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    init {
        // Any change to settings or sleep rebuilds the alarms. Installs from before the campaign existed start it today.
        viewModelScope.launch(Dispatchers.Default) {
            data.collect { d ->
                if (d.settings.onboarded && d.settings.campaignStart == null) c.settings.setCampaignStart(LocalDate.now())
                if (d.settings.onboarded) OrderScheduler.reschedule(app, d)
            }
        }
    }

    /** Looks at screen use for last night; called when the morning report is on screen with nothing saved. */
    fun detectLastNight(date: LocalDate) {
        if (detected.value?.first == date) return
        viewModelScope.launch(Dispatchers.IO) {
            val access = PhoneUsage.hasAccess(app)
            usageAccess.value = access
            if (!access) return@launch
            val d = c.commandData()
            val now = LocalDateTime.now()
            val w = detectionWindow(date, d.habitual(now))
            val night = detectNight(w, PhoneUsage.screenOnTimes(app, w.start, if (now.isBefore(w.end)) now else w.end), now)
            detected.value = date to night
        }
    }

    fun refreshAccess() {
        usageAccess.value = PhoneUsage.hasAccess(app)
        detected.value = null
    }

    /** Saves a night or nap; false if it overlaps another entry. */
    suspend fun saveSleep(record: SleepRecord, replaceId: Long?, source: String = "MANUAL"): Boolean =
        withContext(Dispatchers.IO) { c.sleep.save(record, source, confirmed = true, replaceId = replaceId) }

    fun confirmNight(report: MorningReport, start: LocalDateTime, end: LocalDateTime, wokeNaturally: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val source = if (report.pending != null || report.detected != null) "PHONE" else "MANUAL"
            c.sleep.save(SleepRecord(start, end, SleepKind.NIGHT, wokeNaturally), source, confirmed = true, replaceId = report.pending?.id)
        }
    }

    fun deleteSleep(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { c.sleep.delete(id) }
    }

    fun markSleepless(date: LocalDate, pendingId: Long?) {
        viewModelScope.launch(Dispatchers.IO) {
            pendingId?.let { c.sleep.delete(it) }
            c.sleep.markSleepless(date)
        }
    }

    fun clearSleepless(date: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) { c.sleep.clearSleepless(date) }
    }

    fun setDone(date: LocalDate, kind: OrderKind, done: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { c.sleep.setDone(date, kind, done) }
    }

    fun rate(kss: Int) {
        viewModelScope.launch(Dispatchers.IO) { c.sleep.rate(kss) }
    }

    fun completeOnboarding(bed: LocalTime, wake: LocalTime) {
        viewModelScope.launch { c.settings.completeOnboarding(bed, wake, LocalDate.now()) }
    }

    fun setSchedule(bed: LocalTime, wake: LocalTime) {
        viewModelScope.launch { c.settings.setSchedule(bed, wake) }
    }

    fun setTargetWake(target: LocalTime?) {
        viewModelScope.launch { c.settings.setTargetWake(target) }
    }

    fun setNeedMinutes(minutes: Int) {
        viewModelScope.launch { c.settings.setNeedMinutes(minutes) }
    }

    fun setNeedAuto(auto: Boolean) {
        viewModelScope.launch { c.settings.setNeedAuto(auto) }
    }

    fun setFreeDays(days: Set<DayOfWeek>) {
        viewModelScope.launch { c.settings.setFreeDays(days) }
    }

    fun setReminder(kind: OrderKind, on: Boolean) {
        val current = (state.value as? UiState.Ready)?.settings?.reminders ?: return
        viewModelScope.launch { c.settings.setReminder(kind, on, current) }
    }

    fun setCampaignStart(date: LocalDate) {
        viewModelScope.launch { c.settings.setCampaignStart(date) }
    }

    fun logWeight(date: LocalDate, kg: Double) {
        viewModelScope.launch(Dispatchers.IO) { c.fuel.setWeight(date, kg) }
    }

    fun sendTestOrder() = OrderNotifier.postTest(app)

    /** Called when notification permission changes, so alarms exist the moment orders can be shown. */
    fun refreshAlarms() {
        viewModelScope.launch(Dispatchers.Default) { OrderScheduler.reschedule(app) }
    }

    fun resetOnboarding() {
        viewModelScope.launch { c.settings.resetOnboarding() }
    }

    val accessFlow: StateFlow<Boolean> = usageAccess.asStateFlow()
}
