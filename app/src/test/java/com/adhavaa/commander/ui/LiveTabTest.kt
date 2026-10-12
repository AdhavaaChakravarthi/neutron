package com.adhavaa.commander.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.adhavaa.commander.FuelViewModel
import com.adhavaa.commander.TrainViewModel
import com.adhavaa.commander.fuel.SearchFilter
import kotlinx.coroutines.flow.first
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

/**
 * The real tabs with their ViewModels, database and packaged data, as the app opens them. Renders must not leave
 * effects running (see LiftScreen's ticking flag): they share this sandbox, and a stuck UI dispatcher stalls these.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi")
class LiveTabTest {
    private val outDir = File(System.getProperty("screenshot.dir") ?: "build/screenshots").apply { mkdirs() }

    /**
     * Idles the main looper until [ready] or ~13 s. On timeout, says which data source didn't answer and any
     * exception a coroutine threw, since those otherwise vanish into the thread's uncaught-exception handler.
     */
    private fun waitLive(activity: ComponentActivity, what: String, ready: () -> Boolean) {
        val errors = java.util.Collections.synchronizedList(ArrayList<String>())
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e -> errors += "${t.name}: ${e.javaClass.name}: ${e.message}" }
        val main = Thread.currentThread()
        val mainPrevious = main.uncaughtExceptionHandler
        main.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { t, e -> errors += "${t.name}: ${e.javaClass.name}: ${e.message}" }
        try {
            var waited = 0
            while (!ready() && waited < 100) {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
                Thread.sleep(30)
                waited++
            }
            if (!ready()) {
                val c = (activity.application as com.adhavaa.commander.CommanderApplication).container
                val probes = kotlinx.coroutines.runBlocking {
                    listOf(
                        "settings" to kotlinx.coroutines.withTimeoutOrNull(3000) { c.settings.settings.first() },
                        "weights" to kotlinx.coroutines.withTimeoutOrNull(3000) { c.fuel.observeWeights().first() },
                        "workouts" to kotlinx.coroutines.withTimeoutOrNull(3000) { c.train.observeWorkouts().first() },
                        "sleep" to kotlinx.coroutines.withTimeoutOrNull(3000) { c.sleep.observeSleep().first() },
                    ).joinToString { (k, v) -> "$k=" + if (v == null) "TIMEOUT" else "ok" }
                }
                throw AssertionError("$what never loaded. Probes: $probes. Errors: $errors")
            }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
            main.uncaughtExceptionHandler = mainPrevious
        }
    }

    /**
     * The real Fuel tab with its real ViewModel, database and packaged food list, as the app opens it. Catches
     * on-device problems the stateless renders can't, like the food list asset going missing.
     */
    @Test
    @Config(qualifiers = "w393dp-h1700dp-xhdpi")
    fun fuelTabLive() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { CommanderTheme { FuelRoute(onOpenSettings = {}, onFullScreen = {}) } }
        val vm = ViewModelProvider(activity)[FuelViewModel::class.java]
        waitLive(activity, "Fuel state") { vm.state.value != null }
        val found = kotlinx.coroutines.runBlocking { vm.search("banana", SearchFilter(fishOnly = true)) }
        assertTrue("search found nothing: ${vm.catalogError()}", found.any { it.name == "Bananas, raw" })
        repeat(5) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200)) }
        val view = activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        FileOutputStream(File(outDir, "18-fuel-live.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val colours = HashSet<Int>()
        for (y in 0 until bitmap.height step 7) for (x in 0 until bitmap.width step 7) colours += bitmap.getPixel(x, y)
        assertTrue("live Fuel tab rendered blank (${colours.size} colours)", colours.size > 20)
    }

    /** The real Train tab with its ViewModel, database and packaged exercise list. */
    @Test
    @Config(qualifiers = "w393dp-h1700dp-xhdpi")
    fun trainTabLive() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { CommanderTheme { TrainRoute(onOpenSettings = {}, onFullScreen = {}) } }
        val vm = ViewModelProvider(activity)[TrainViewModel::class.java]
        waitLive(activity, "Train state") { vm.state.value != null }
        val state = vm.state.value
        assertTrue("exercise list: ${vm.catalogError()}", state!!.exercises.size == 876)
        repeat(5) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200)) }
        val view = activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        FileOutputStream(File(outDir, "25-train-live.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val colours = HashSet<Int>()
        for (y in 0 until bitmap.height step 7) for (x in 0 until bitmap.width step 7) colours += bitmap.getPixel(x, y)
        assertTrue("live Train tab rendered blank (${colours.size} colours)", colours.size > 20)
    }
}
