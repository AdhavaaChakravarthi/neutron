package com.adhavaa.commander.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.adhavaa.commander.UiState
import com.adhavaa.commander.data.Settings
import com.adhavaa.commander.model.UsualSchedule
import com.adhavaa.commander.model.ordersFor
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
    private val day = LocalDate.of(2026, 10, 12)

    private fun ready(
        bed: LocalTime,
        wake: LocalTime,
        now: LocalDateTime,
        target: LocalTime? = null,
    ): UiState.Ready {
        val settings = Settings(
            onboarded = true, bedtime = bed, wake = wake, targetWake = target, needMinutes = 480,
            campaignStart = LocalDate.of(2026, 10, 11),
        )
        val orders = ordersFor(day, UsualSchedule(bed, wake), needH = 8.0, targetWake = target)
        return UiState.Ready(settings, now, orders, campaign(settings.campaignStart!!, now.toLocalDate()))
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

    private val usualBed = LocalTime.of(23, 30)
    private val usualWake = LocalTime.of(7, 30)

    @Test
    fun todayAfternoon() = render("1-today") {
        TodayScreen(ready(usualBed, usualWake, LocalDateTime.of(2026, 10, 12, 16, 0)), TodayActions())
    }

    @Test
    @Config(qualifiers = "w393dp-h3000dp-xhdpi")
    fun todayFull() = render("2-today-full") {
        TodayScreen(ready(usualBed, usualWake, LocalDateTime.of(2026, 10, 12, 14, 30)), TodayActions())
    }

    @Test
    fun todayMorningSilent() = render("3-today-morning-silent") {
        TodayScreen(
            ready(usualBed, usualWake, LocalDateTime.of(2026, 10, 12, 8, 5)),
            TodayActions(),
            banner = { SilentBanner {} },
        )
    }

    @Test
    fun todayNight() = render("4-today-night") {
        TodayScreen(ready(usualBed, usualWake, LocalDateTime.of(2026, 10, 12, 22, 40)), TodayActions())
    }

    @Test
    @Config(qualifiers = "w393dp-h3000dp-xhdpi")
    fun todayLateSleeperMovingEarlier() = render("5-today-late-sleeper") {
        TodayScreen(
            ready(LocalTime.of(1, 30), LocalTime.of(9, 0), LocalDateTime.of(2026, 10, 12, 11, 0), target = LocalTime.of(7, 0)),
            TodayActions(),
        )
    }

    @Test
    fun todayLargeText() = render("6-today-large-text", fontScale = 1.6f) {
        TodayScreen(ready(usualBed, usualWake, LocalDateTime.of(2026, 10, 12, 16, 0)), TodayActions())
    }

    @Test
    fun onboarding() = render("7-onboarding") {
        OnboardingScreen(Settings(false, Settings.DEFAULT_BEDTIME, Settings.DEFAULT_WAKE, null, 480), onDone = { _, _ -> })
    }

    @Test
    @Config(qualifiers = "w393dp-h3000dp-xhdpi")
    fun settings() = render("8-settings") {
        SettingsScreen(
            ready(usualBed, usualWake, LocalDateTime.of(2026, 10, 12, 16, 0)),
            SettingsActions(),
            notificationsAllowed = false,
        )
    }
}
