package com.adhavaa.commander.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adhavaa.commander.UiState
import com.adhavaa.commander.model.TodayOrders
import com.adhavaa.commander.orders.Order
import com.adhavaa.commander.orders.OrderKind
import com.adhavaa.commander.orders.REMINDABLE
import com.adhavaa.commander.orders.brief
import com.adhavaa.commander.orders.buildOrders
import com.adhavaa.commander.orders.TICKABLE
import com.adhavaa.commander.orders.countdown
import com.adhavaa.commander.orders.debtBand
import com.adhavaa.commander.orders.doneCount
import com.adhavaa.commander.orders.kssLabel
import com.adhavaa.commander.orders.formatHours
import com.adhavaa.commander.orders.fuelBrief
import com.adhavaa.commander.orders.fuelOrders
import com.adhavaa.commander.orders.trainOrders
import com.adhavaa.commander.train.dayLine
import com.adhavaa.commander.orders.nextOrder
import com.adhavaa.commander.orders.phase
import com.adhavaa.commander.orders.shortName
import com.adhavaa.commander.orders.statusLine
import java.time.Duration
import java.time.LocalDateTime

@Immutable
data class TodayActions(
    val onOpenSettings: () -> Unit = {},
    val onToggleReminder: (OrderKind, Boolean) -> Unit = { _, _ -> },
    val onSetAlarm: (LocalDateTime) -> Unit = {},
    val onToggleDone: (OrderKind, Boolean) -> Unit = { _, _ -> },
    val onRate: (Int) -> Unit = {},
    val onLogWeight: (Double) -> Unit = {},
    val onOpenFuel: () -> Unit = {},
    val onOpenTrain: () -> Unit = {},
)

@Composable
fun TodayScreen(state: UiState.Ready, actions: TodayActions, banner: @Composable () -> Unit = {}) {
    val fmt = rememberTimeFormatter()
    val orders = state.orders
    val now = state.now
    val list = (buildOrders(orders, fmt) + fuelOrders(state.fuel, orders, fmt) + trainOrders(state.train, orders, fmt))
        .sortedWith(compareBy<Order> { it.at }.thenBy { it.kind.ordinal })
    val next = nextOrder(list, now)
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    var weighing by rememberSaveable { mutableStateOf(false) }
    if (weighing) {
        WeightDialog(
            date = now.toLocalDate(),
            existing = state.fuel?.weightOnDate,
            lastKg = state.fuel?.trendKg,
            onSave = actions.onLogWeight,
            onDelete = null,
            onDismiss = { weighing = false },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Hud.bg),
        contentPadding = PaddingValues(
            start = 14.dp, end = 14.dp,
            top = insets.calculateTopPadding() + 8.dp,
            bottom = bottomPadding(insets),
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TopBar(state, fmt, actions.onOpenSettings) }
        item { banner() }
        item { TelemetryStrip(orders, next, now, fmt) }
        item { MissionPanel(state, list, next, fmt, actions.onRate) }
        item {
            BriefPanel(
                orders, now, fmt,
                listOfNotNull(fuelBrief(state.fuel, state.eaten), state.train?.takeIf { it.setUp }?.let { "Train: ${dayLine(it.day)}." })
                    .joinToString("\n").ifBlank { null },
            )
        }
        state.hexagon?.let { h -> item { HexagonPanel(h) } }
        item {
            HudPanel(
                title = "Day radar",
                subtitle = "24 h · 00:00 at top",
                trailing = { HudChip("Now ${fmt(now)}", Tone.GREEN) },
            ) {
                DayRadar(orders, list, next, now, fmt, Modifier.padding(horizontal = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    HudChip("Sleep ${fmt(orders.lightsOut)}–${fmt(orders.nextWake)}")
                    HudChip("Sharpest", Tone.GREEN)
                    HudChip("Wind down", Tone.AMBER)
                    HudChip("● Orders")
                }
            }
        }
        item {
            HudPanel(
                title = "Energy telemetry",
                subtitle = "Predicted alertness · three-process model",
                trailing = { HudChip("Estimate", Tone.AMBER) },
            ) {
                EnergyChart(orders, now, fmt)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    HudChip("Peak ~${fmt(orders.peakAt)}", Tone.GREEN)
                    HudChip("Groggy to ${fmt(orders.groggyUntil)}")
                    HudChip("Sleepy line 7", Tone.AMBER)
                }
            }
        }
        item { TimelinePanel(list, state, now, fmt, actions) { weighing = true } }
        item {
            Text(
                "Wellness tool, not a medical device. No data leaves this phone.",
                style = HudType.small,
                color = Hud.dim,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun TopBar(state: UiState.Ready, fmt: (LocalDateTime) -> String, onOpenSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Mark()
        Column(Modifier.weight(1f)) {
            Text("COMMANDER", style = HudType.title.copy(fontSize = 14.sp), color = Hud.ink)
            val c = state.campaign
            Text(
                when {
                    c == null -> "Campaign starts today"
                    c.complete -> "Campaign complete · ${c.totalDays} days"
                    else -> "Day ${c.day.toString().padStart(3, '0')} / ${c.totalDays}"
                }.uppercase(),
                style = HudType.label,
                color = Hud.green,
            )
        }
        HudChip(fmt(state.now))
        SquareButton("Open settings", onOpenSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = null, tint = Hud.muted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TelemetryStrip(orders: TodayOrders, next: Order?, now: LocalDateTime, fmt: (LocalDateTime) -> String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        if (next != null) {
            StatCell("Next order", "${shortName(next.kind)} ${fmt(next.at)}", accent = Hud.green)
            StatCell("ETA", countdown(now, next.at), accent = Hud.green)
        }
        StatCell("Lights out", fmt(orders.lightsOut), accent = Hud.amber)
        StatCell("Need", formatHours(orders.needH))
        StatCell("Up next", fmt(orders.nextWake))
    }
}

@Composable
private fun MissionPanel(
    state: UiState.Ready,
    list: List<Order>,
    next: Order?,
    fmt: (LocalDateTime) -> String,
    onRate: (Int) -> Unit,
) {
    val o = state.orders
    HudPanel(
        decoration = Modifier.contours(),
        background = Brush.verticalGradient(listOf(Hud.panel, Hud.bg)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("MISSION", style = HudType.label, color = Hud.muted)
            HudChip("Phase · ${phase(o, state.now)}", Tone.GREEN)
        }
        Text(statusLine(o, state.now, fmt), style = HudType.headline, color = Hud.ink)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyValue("Next order", next?.let { "${shortName(it.kind)} · ${fmt(it.at)}" } ?: "None today", Hud.green)
            KeyValue("Lights out", fmt(o.lightsOut), Hud.amber)
            KeyValue("Sleep need", formatHours(o.needH))
            val debt = state.stats.debtH
            KeyValue(
                "Sleep debt",
                debt?.let { "${formatHours(it)} · ${debtBand(it)}" } ?: "No nights recorded yet",
                when {
                    debt == null -> Hud.dim
                    debt >= 2.0 -> Hud.amber
                    else -> Hud.green
                },
            )
            val (done, total) = doneCount(list, state.done)
            KeyValue("Orders done", "$done / $total")
        }
        AlertnessRow(onRate)
    }
}

/** One tap: how alert do you feel right now? Feeds future calibration of your curve (MODEL.md section 5). */
@Composable
private fun AlertnessRow(onRate: (Int) -> Unit) {
    var last by rememberSaveable { mutableStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (last == 0) "HOW ALERT ARE YOU? 1 = VERY ALERT · 9 = FIGHTING SLEEP" else "LOGGED $last · ${kssLabel(last).ifBlank { "between anchors" }.uppercase()}",
            style = HudType.label,
            color = if (last == 0) Hud.muted else Hud.green,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (v in 1..9) {
                val chosen = v == last
                Box(
                    Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (chosen) Hud.green else Hud.panelHi)
                        .clickable(onClickLabel = "Alertness $v ${kssLabel(v)}", role = Role.Button) {
                            last = v
                            onRate(v)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(v.toString(), style = HudType.value, color = if (chosen) Hud.onMint else Hud.ink)
                }
            }
        }
    }
}

@Composable
private fun BriefPanel(orders: TodayOrders, now: LocalDateTime, fmt: (LocalDateTime) -> String, fuel: String?) {
    HudPanel(
        background = Brush.verticalGradient(listOf(Hud.mint, Hud.mintDeep)),
        border = Hud.mint,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("◆ MISSION BRIEF", style = HudType.title, color = Hud.onMint, modifier = Modifier.weight(1f))
            Text(fmt(now), style = HudType.label, color = Hud.onMint.copy(alpha = 0.7f))
        }
        Wireframe(orders, Modifier.fillMaxWidth().height(72.dp))
        Text(brief(orders, now, fmt), style = HudType.body, color = Hud.onMint)
        if (fuel != null) Text(fuel, style = HudType.body, color = Hud.onMint)
        Text(
            "Planned from your recorded nights (your usual schedule fills gaps). The sharpest window is an estimate.",
            style = HudType.small,
            color = Hud.onMint.copy(alpha = 0.7f),
        )
    }
}

/** Today's energy curve as a wireframe ribbon, in the spirit of the reference's line-art panel. */
@Composable
private fun Wireframe(orders: TodayOrders, modifier: Modifier) {
    Canvas(modifier) {
        val pts = orders.curve
        if (pts.size < 2) return@Canvas
        val t0 = pts.first().at
        val total = Duration.between(t0, pts.last().at).toMinutes().toFloat().coerceAtLeast(1f)
        val lo = pts.minOf { it.alertness }.toFloat()
        val hi = pts.maxOf { it.alertness }.toFloat()
        val layers = 6
        val depth = size.height * 0.35f
        fun at(i: Int, layer: Int): Offset {
            val p = pts[i]
            val fx = Duration.between(t0, p.at).toMinutes() / total
            val fy = (p.alertness.toFloat() - lo) / (hi - lo).coerceAtLeast(0.01f)
            val shift = layer * (depth / layers)
            return Offset(fx * (size.width - depth) + shift, (1f - fy) * (size.height - depth) + shift)
        }
        for (layer in 0 until layers) {
            val path = Path()
            for (i in pts.indices) {
                val p = at(i, layer)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(path, Hud.onMint.copy(alpha = 0.75f - layer * 0.1f), style = Stroke(1.dp.toPx(), cap = StrokeCap.Round))
        }
        for (i in pts.indices step 12) {
            drawLine(Hud.onMint.copy(alpha = 0.35f), at(i, 0), at(i, layers - 1), 1.dp.toPx())
        }
    }
}

@Composable
private fun TimelinePanel(
    list: List<Order>,
    state: UiState.Ready,
    now: LocalDateTime,
    fmt: (LocalDateTime) -> String,
    actions: TodayActions,
    onWeigh: () -> Unit,
) {
    val t = state.train
    val auto = buildSet {
        if (state.fuel?.weightOnDate != null) add(OrderKind.WEIGH_IN)
        if (t != null && !t.day.rest && t.dayComplete(t.day)) add(OrderKind.TRAIN)
        if (t != null && t.mobilityDone(t.today)) add(OrderKind.MOBILITY)
    }
    val done = state.done + auto
    HudPanel(
        title = "Mission timeline",
        subtitle = "Tap an order for the reason",
        trailing = { HudChip("${list.size} entries") },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (order in list) {
                TimelineRow(
                    order = order,
                    fmt = fmt,
                    now = now,
                    reminderOn = order.kind in state.settings.reminders,
                    done = order.kind in done,
                    onToggleReminder = actions.onToggleReminder,
                    onToggleDone = actions.onToggleDone,
                    onSetAlarm = actions.onSetAlarm,
                    onWeigh = onWeigh,
                    onOpenFuel = actions.onOpenFuel,
                    onOpenTrain = actions.onOpenTrain,
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(
    order: Order,
    fmt: (LocalDateTime) -> String,
    now: LocalDateTime,
    reminderOn: Boolean,
    done: Boolean,
    onToggleReminder: (OrderKind, Boolean) -> Unit,
    onToggleDone: (OrderKind, Boolean) -> Unit,
    onSetAlarm: (LocalDateTime) -> Unit,
    onWeigh: () -> Unit = {},
    onOpenFuel: () -> Unit = {},
    onOpenTrain: () -> Unit = {},
) {
    var open by rememberSaveable(order.kind) { mutableStateOf(false) }
    val past = order.at.isBefore(now) && (order.until?.isBefore(now) ?: true)
    val current = !order.at.isAfter(now) && order.until?.isAfter(now) == true
    val warn = order.kind == OrderKind.CAFFEINE || order.kind == OrderKind.LIGHTS_OUT || order.kind == OrderKind.WIND_DOWN ||
        order.kind == OrderKind.EAT_TO_TARGET
    val accent = when {
        past -> Hud.dim
        warn -> Hud.amber
        order.kind in REMINDABLE -> Hud.green
        else -> Hud.muted
    }
    val alpha = if (past) 0.5f else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(6.dp))
            .background(if (current) Hud.panelHi else Hud.panel)
            .clickable(onClickLabel = if (open) "Hide reason" else "Show reason", role = Role.Button) { open = !open }
            .animateContentSize(),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accent),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                HudChip(fmt(order.at) + (order.until?.let { "–" + fmt(it) } ?: ""), if (past) Tone.MUTED else if (warn) Tone.AMBER else Tone.GREEN)
                HudChip(if (order.kind in REMINDABLE) "Order" else "Info")
                if (order.estimate) HudChip("Est", Tone.AMBER)
                if (current) HudChip("Now", Tone.SOLID)
                if (done) HudChip("Done", Tone.GREEN)
            }
            Text(
                order.title.trimEnd('.').uppercase(),
                style = HudType.value,
                color = Hud.ink.copy(alpha = alpha),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(order.detail, style = HudType.small, color = Hud.muted.copy(alpha = alpha))
            if (open) Text("Why: ${order.why}", style = HudType.small, color = Hud.ink.copy(alpha = 0.8f))
            if (order.kind == OrderKind.TOMORROW) {
                Spacer(Modifier.height(2.dp))
                RowAction("SET ALARM FOR ${fmt(order.at)}") { onSetAlarm(order.at) }
            }
            if (order.kind == OrderKind.WEIGH_IN && !done) {
                Spacer(Modifier.height(2.dp))
                RowAction("LOG WEIGHT", onWeigh)
            }
            if (order.kind == OrderKind.EAT_TO_TARGET) {
                Spacer(Modifier.height(2.dp))
                RowAction("OPEN FUEL", onOpenFuel)
            }
            if ((order.kind == OrderKind.TRAIN || order.kind == OrderKind.MOBILITY) && !done) {
                Spacer(Modifier.height(2.dp))
                RowAction("OPEN TRAIN", onOpenTrain)
            }
        }
        Column(Modifier.padding(vertical = 4.dp)) {
        if (order.kind in TICKABLE) {
            SquareButton(
                contentDescription = if (done) "Mark ${order.title} not done" else "Mark ${order.title} done",
                onClick = { onToggleDone(order.kind, !done) },
                active = done,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = if (done) Hud.green else Hud.dim, modifier = Modifier.size(17.dp))
            }
        }
        if (order.kind in REMINDABLE) {
            SquareButton(
                contentDescription = if (reminderOn) "Turn off reminder for ${order.title}" else "Turn on reminder for ${order.title}",
                onClick = { onToggleReminder(order.kind, !reminderOn) },
                active = reminderOn,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    Icons.Outlined.Notifications,
                    contentDescription = null,
                    tint = if (reminderOn) Hud.green else Hud.dim,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        }
    }
}

/** A small solid action inside a timeline row. */
@Composable
private fun RowAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = HudType.label,
        color = Hud.onMint,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Hud.green)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

