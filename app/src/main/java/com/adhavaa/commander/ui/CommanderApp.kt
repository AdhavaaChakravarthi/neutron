package com.adhavaa.commander.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adhavaa.commander.CommanderViewModel
import com.adhavaa.commander.UiState
import com.adhavaa.commander.notify.OrderNotifier
import com.adhavaa.commander.notify.PhoneUsage

/** Top-level destinations, in bottom-bar order. Later modules add tabs here. */
enum class Tab(val label: String) { TODAY("Today"), SLEEP("Sleep"), FUEL("Fuel") }

@Composable
fun CommanderApp(vm: CommanderViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val detectionOn by vm.accessFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Notification permission: checked on every resume, asked once, then sent to system settings.
    var allowed by remember { mutableStateOf(OrderNotifier.permitted(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        val now = OrderNotifier.permitted(context)
        if (now != allowed) vm.refreshAlarms()
        allowed = now
        vm.refreshAccess()
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted && OrderNotifier.permitted(context)
        asked = true
        vm.refreshAlarms()
    }
    val requestNotifications: () -> Unit = {
        if (asked) openNotificationSettings(context) else launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val enableDetection: () -> Unit = { PhoneUsage.openSettings(context) }

    when (val s = state) {
        UiState.Loading -> Column(Modifier.fillMaxSize().background(Hud.bg)) {}
        is UiState.Failed -> FailedScreen(s.message, onReset = vm::resetOnboarding)
        is UiState.Ready ->
            if (!s.settings.onboarded) {
                OnboardingScreen(s.settings, onDone = { bed, wake ->
                    vm.completeOnboarding(bed, wake)
                    if (!allowed) requestNotifications()
                })
            } else {
                var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
                var settingsOpen by rememberSaveable { mutableStateOf(false) }
                var fuelFullScreen by remember { mutableStateOf(false) }
                if (settingsOpen) {
                    BackHandler { settingsOpen = false }
                    SettingsScreen(
                        state = s,
                        notificationsAllowed = allowed,
                        detectionOn = detectionOn,
                        actions = SettingsActions(
                            onBack = { settingsOpen = false },
                            onSchedule = vm::setSchedule,
                            onTargetWake = vm::setTargetWake,
                            onNeed = vm::setNeedMinutes,
                            onReminder = vm::setReminder,
                            onTestOrder = vm::sendTestOrder,
                            onCampaignStart = vm::setCampaignStart,
                            onEnableNotifications = requestNotifications,
                            onNeedAuto = vm::setNeedAuto,
                            onFreeDays = vm::setFreeDays,
                            onEnableDetection = enableDetection,
                        ),
                    )
                } else {
                    if (tab != Tab.TODAY) BackHandler { tab = Tab.TODAY }
                    Column(Modifier.fillMaxSize().background(Hud.bg)) {
                        Box(Modifier.weight(1f)) {
                            CompositionLocalProvider(LocalHasBottomBar provides true) {
                                when (tab) {
                                    Tab.TODAY -> TodayScreen(
                                        state = s,
                                        actions = TodayActions(
                                            onOpenSettings = { settingsOpen = true },
                                            onToggleReminder = vm::setReminder,
                                            onSetAlarm = { openSetAlarm(context, it) },
                                            onToggleDone = { kind, done -> vm.setDone(s.orders.date, kind, done) },
                                            onRate = vm::rate,
                                            onLogWeight = { kg -> vm.logWeight(s.now.toLocalDate(), kg) },
                                            onOpenFuel = { tab = Tab.FUEL },
                                        ),
                                        banner = {
                                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                if (!allowed) SilentBanner(requestNotifications)
                                                s.report?.let { r ->
                                                    MorningReportCard(
                                                        report = r,
                                                        habitualBed = s.stats.habitualBed,
                                                        habitualWake = s.stats.habitualWake,
                                                        onDetect = vm::detectLastNight,
                                                        onConfirm = { start, end, natural -> vm.confirmNight(r, start, end, natural) },
                                                        onSleepless = { vm.markSleepless(r.date, r.pending?.id) },
                                                        onEnableDetection = enableDetection,
                                                    )
                                                }
                                            }
                                        },
                                    )
                                    Tab.SLEEP -> SleepScreen(
                                        state = s,
                                        detectionOn = detectionOn,
                                        actions = SleepActions(
                                            onOpenSettings = { settingsOpen = true },
                                            onSave = { record, id -> vm.saveSleep(record, id) },
                                            onDelete = vm::deleteSleep,
                                            onSleepless = { vm.markSleepless(it, null) },
                                            onClearSleepless = vm::clearSleepless,
                                            onEnableDetection = enableDetection,
                                        ),
                                    )
                                    Tab.FUEL -> FuelRoute(onOpenSettings = { settingsOpen = true }, onFullScreen = { fuelFullScreen = it })
                                }
                            }
                        }
                        if (!(tab == Tab.FUEL && fuelFullScreen)) BottomBar(tab) { tab = it }
                    }
                }
            }
    }
}

/** HUD tab strip along the bottom; the active tab carries a signal-green bar. */
@Composable
fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Hud.panel)
            .border(1.dp, Hud.line)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp),
    ) {
        for (t in Tab.entries) {
            val on = t == current
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClickLabel = "Open ${t.label}", role = Role.Tab) { onSelect(t) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.width(28.dp).height(3.dp).background(if (on) Hud.green else Hud.panel))
                Text(t.label.uppercase(), style = HudType.title, color = if (on) Hud.green else Hud.muted)
            }
        }
    }
}

/** Shown on Today while orders can't be delivered. */
@Composable
fun SilentBanner(onAllow: () -> Unit) {
    HudPanel(
        title = "⚠ Orders are silent",
        background = Brush.verticalGradient(listOf(Hud.amberDeep, Hud.panel)),
        border = Hud.amber.copy(alpha = 0.6f),
    ) {
        Text(
            "Notifications are off, so Commander can't tell you when it's time to wind down or turn the lights out.",
            style = HudType.body,
            color = Hud.ink,
        )
        HudButton("Allow notifications", onAllow)
    }
}

@Composable
private fun FailedScreen(message: String, onReset: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Hud.bg).safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("COMMANDER COULDN'T PLAN TODAY", style = HudType.title, color = Hud.amber)
        Text(message, style = HudType.body, color = Hud.ink)
        HudButton("Set my schedule again", onReset)
    }
}
