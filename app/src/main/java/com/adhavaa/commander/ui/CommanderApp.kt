package com.adhavaa.commander.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adhavaa.commander.CommanderViewModel
import com.adhavaa.commander.UiState

private enum class Screen { TODAY, SETTINGS }

@Composable
fun CommanderApp(vm: CommanderViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val s = state) {
            UiState.Loading -> Unit
            is UiState.Failed -> FailedScreen(s.message, onReset = vm::resetOnboarding)
            is UiState.Ready ->
                if (!s.settings.onboarded) {
                    OnboardingScreen(s.settings, onDone = vm::completeOnboarding)
                } else {
                    var screen by rememberSaveable { mutableStateOf(Screen.TODAY) }
                    when (screen) {
                        Screen.TODAY -> TodayScreen(s, onOpenSettings = { screen = Screen.SETTINGS })
                        Screen.SETTINGS -> {
                            BackHandler { screen = Screen.TODAY }
                            SettingsScreen(
                                state = s,
                                onBack = { screen = Screen.TODAY },
                                onSchedule = vm::setSchedule,
                                onTargetWake = vm::setTargetWake,
                                onNeed = vm::setNeedMinutes,
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun FailedScreen(message: String, onReset: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Commander couldn't plan today.", style = MaterialTheme.typography.headlineSmall)
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onReset) { Text("Set my schedule again") }
    }
}
