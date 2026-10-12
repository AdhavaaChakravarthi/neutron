package com.adhavaa.commander.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adhavaa.commander.CommanderViewModel
import com.adhavaa.commander.UiState
import com.adhavaa.commander.notify.OrderNotifier

private enum class Screen { TODAY, SETTINGS }

@Composable
fun CommanderApp(vm: CommanderViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Notification permission: checked on every resume, asked once, then sent to system settings.
    var allowed by remember { mutableStateOf(OrderNotifier.permitted(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        val now = OrderNotifier.permitted(context)
        if (now != allowed) vm.refreshAlarms()
        allowed = now
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
                var screen by rememberSaveable { mutableStateOf(Screen.TODAY) }
                when (screen) {
                    Screen.TODAY -> TodayScreen(
                        state = s,
                        actions = TodayActions(
                            onOpenSettings = { screen = Screen.SETTINGS },
                            onToggleReminder = vm::setReminder,
                            onSetAlarm = { openSetAlarm(context, it) },
                        ),
                        banner = { if (!allowed) SilentBanner(requestNotifications) },
                    )
                    Screen.SETTINGS -> {
                        BackHandler { screen = Screen.TODAY }
                        SettingsScreen(
                            state = s,
                            notificationsAllowed = allowed,
                            actions = SettingsActions(
                                onBack = { screen = Screen.TODAY },
                                onSchedule = vm::setSchedule,
                                onTargetWake = vm::setTargetWake,
                                onNeed = vm::setNeedMinutes,
                                onReminder = vm::setReminder,
                                onTestOrder = vm::sendTestOrder,
                                onCampaignStart = vm::setCampaignStart,
                                onEnableNotifications = requestNotifications,
                            ),
                        )
                    }
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
