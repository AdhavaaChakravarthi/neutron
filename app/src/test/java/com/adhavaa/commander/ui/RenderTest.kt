package com.adhavaa.commander.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.adhavaa.commander.MorningReport
import com.adhavaa.commander.UiState
import com.adhavaa.commander.data.Settings
import com.adhavaa.commander.data.StoredSleep
import com.adhavaa.commander.model.SleepKind
import com.adhavaa.commander.model.SleepRecord
import com.adhavaa.commander.orders.CommandData
import com.adhavaa.commander.orders.DetectedNight
import com.adhavaa.commander.orders.OrderKind
import com.adhavaa.commander.orders.campaign
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Draws the real screens on the JVM and saves PNGs (CI attaches them to each release), so the UI can be
 * looked at without a phone. Each render must not be blank.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi")
class RenderTest {
    private val outDir = File(System.getProperty("screenshot.dir") ?: "build/screenshots").apply { mkdirs() }
    private val day = LocalDate.of(2026, 10, 14)

    private val settings = Settings(
        onboarded = true, bedtime = LocalTime.of(23, 30), wake = LocalTime.of(7, 30), targetWake = null,
        needMinutes = 480, campaignStart = LocalDate.of(2026, 10, 11),
    )

    /** Ten recorded nights drifting earlier, a short one, a nap and a sleepless night. */
    private fun history(): List<StoredSleep> {
        val out = ArrayList<StoredSleep>()
        var id = 1L
        for (k in 1L..10L) {
            val wake = day.minusDays(k)
            val bed = LocalTime.of(0, 30).minusMinutes(k * 6)
            val start = if (bed.hour >= 12) wake.minusDays(1).atTime(bed) else wake.atTime(bed)
            val natural = wake.dayOfWeek.value >= 6
            out += StoredSleep(id++, SleepRecord(start, wake.atTime(7, 0).plusMinutes(if (natural) 75 else 0), SleepKind.NIGHT, natural), if (k % 3 == 0L) "MANUAL" else "PHONE", true)
        }
        out += StoredSleep(id, SleepRecord(day.minusDays(2).atTime(14, 0), day.minusDays(2).atTime(14, 20), SleepKind.NAP), "MANUAL", true)
        return out
    }

    private fun ready(
        now: LocalDateTime,
        stored: List<StoredSleep> = emptyList(),
        skipped: Set<LocalDate> = emptySet(),
        done: Set<OrderKind> = emptySet(),
        report: MorningReport? = null,
        s: Settings = settings,
    ): UiState.Ready {
        val data = CommandData(s, stored.map { it.record }, skipped)
        val p = data.picture(data.planDate(now))
        return UiState.Ready(s, now, p.orders, campaign(s.campaignStart!!, now.toLocalDate()), p.stats, stored, skipped, done, report)
    }

    private fun render(name: String, fontScale: Float = 1f, content: @Composable () -> Unit) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                CommanderTheme { content() }
            }
        }
        repeat(5) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200)) }
        val view = activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        FileOutputStream(File(outDir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val colours = HashSet<Int>()
        for (y in 0 until bitmap.height step 7) for (x in 0 until bitmap.width step 7) colours += bitmap.getPixel(x, y)
        assertTrue("$name rendered blank (${colours.size} colours)", colours.size > 20)
    }

    @Test
    fun todayWithHistory() = render("01-today") {
        TodayScreen(ready(day.atTime(16, 0), history(), done = setOf(OrderKind.WAKE, OrderKind.CAFFEINE)), TodayActions())
    }

    @Test
    @Config(qualifiers = "w393dp-h3200dp-xhdpi")
    fun todayFull() = render("02-today-full") {
        TodayScreen(ready(day.atTime(14, 30), history(), done = setOf(OrderKind.WAKE)), TodayActions())
    }

    @Test
    @Config(qualifiers = "w393dp-h1400dp-xhdpi")
    fun morningReportFromPhone() = render("03-morning-report") {
        val now = day.atTime(8, 20)
        val r = MorningReport(day, null, DetectedNight(day.minusDays(1).atTime(23, 52), day.atTime(7, 18)), usageAccess = true)
        val state = ready(now, history().filter { it.record.end.toLocalDate() != day }, report = r)
        TodayScreen(state, TodayActions(), banner = {
            MorningReportCard(r, state.stats.habitualBed, state.stats.habitualWake, {}, { _, _, _ -> }, {}, {})
        })
    }

    @Test
    fun morningReportManualSilent() = render("04-morning-report-manual") {
        val now = day.atTime(8, 20)
        val r = MorningReport(day, null, null, usageAccess = false)
        val state = ready(now, report = r)
        TodayScreen(state, TodayActions(), banner = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SilentBanner {}
                MorningReportCard(r, state.stats.habitualBed, state.stats.habitualWake, {}, { _, _, _ -> }, {}, {})
            }
        })
    }

    @Test
    @Config(qualifiers = "w393dp-h2600dp-xhdpi")
    fun sleepTab() = render("05-sleep") {
        SleepScreen(ready(day.atTime(16, 0), history(), skipped = setOf(day.minusDays(11))), SleepActions(), detectionOn = true)
    }

    @Test
    fun sleepTabEmpty() = render("06-sleep-empty") {
        SleepScreen(ready(day.atTime(16, 0)), SleepActions(), detectionOn = false)
    }

    @Test
    fun bottomBar() = render("07-bottom-bar") {
        Column(Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                CompositionLocalProvider(LocalHasBottomBar provides true) {
                    TodayScreen(ready(day.atTime(22, 40), history()), TodayActions())
                }
            }
            BottomBar(Tab.TODAY) {}
        }
    }

    @Test
    fun todayLargeText() = render("08-today-large-text", fontScale = 1.6f) {
        TodayScreen(ready(day.atTime(16, 0), history()), TodayActions())
    }

    @Test
    fun onboarding() = render("09-onboarding") {
        OnboardingScreen(Settings(false, Settings.DEFAULT_BEDTIME, Settings.DEFAULT_WAKE, null, 480), onDone = { _, _ -> })
    }

    @Test
    @Config(qualifiers = "w393dp-h4200dp-xhdpi")
    fun settingsScreen() = render("10-settings") {
        SettingsScreen(ready(day.atTime(16, 0), history()), SettingsActions(), notificationsAllowed = false, detectionOn = false)
    }
}
