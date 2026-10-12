package com.adhavaa.commander.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adhavaa.commander.UiState
import com.adhavaa.commander.orders.Order
import com.adhavaa.commander.orders.buildOrders
import com.adhavaa.commander.orders.statusLine
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(state: UiState.Ready, onOpenSettings: () -> Unit) {
    val fmt = rememberTimeFormatter()
    val orders = state.orders
    val list = buildOrders(orders, fmt)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Today's orders") },
                actions = { TextButton(onClick = onOpenSettings) { Text("Settings") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        orders.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        statusLine(orders, state.now, fmt),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    EnergyChart(orders, state.now, fmt, Modifier.padding(16.dp))
                }
            }
            items(list, key = { it.kind.name }) { order ->
                OrderRow(order, fmt, isPast = order.at.isBefore(state.now) && (order.until?.isBefore(state.now) ?: true))
            }
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    "Sleep debt starts once Commander can see your real nights. Until then, orders use " +
                        "your usual schedule. Tap any order to see why.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OrderRow(order: Order, fmt: (LocalDateTime) -> String, isPast: Boolean) {
    var open by rememberSaveable(order.kind) { mutableStateOf(false) }
    val dim = if (isPast) 0.55f else 1f
    val ink = MaterialTheme.colorScheme.onSurface.copy(alpha = dim)
    val sub = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dim)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (open) "Hide reason" else "Show reason", role = Role.Button) { open = !open }
            .animateContentSize()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row {
            Text(
                fmt(order.at) + (order.until?.let { "\n–" + fmt(it) } ?: ""),
                modifier = Modifier.width(88.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = dim),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    order.title + if (order.estimate) "  · estimate" else "",
                    style = MaterialTheme.typography.titleMedium,
                    color = ink,
                )
                Text(order.detail, style = MaterialTheme.typography.bodyMedium, color = sub)
                if (open) {
                    Text(
                        "Why: " + order.why,
                        style = MaterialTheme.typography.bodySmall,
                        color = sub,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
