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
import androidx.lifecycle.ViewModelProvider
import com.adhavaa.commander.FuelUi
import com.adhavaa.commander.FuelViewModel
import com.adhavaa.commander.Rest
import com.adhavaa.commander.TrainUi
import com.adhavaa.commander.TrainViewModel
import com.adhavaa.commander.data.TrainSettings
import com.adhavaa.commander.model.Intensity
import com.adhavaa.commander.model.weekPlan
import com.adhavaa.commander.model.weekSessions
import com.adhavaa.commander.train.Exercise
import com.adhavaa.commander.train.PROGRAM
import com.adhavaa.commander.train.RECOVERY_STRETCH
import com.adhavaa.commander.train.SetRecord
import com.adhavaa.commander.train.TrainData
import com.adhavaa.commander.train.Workout
import com.adhavaa.commander.train.WorkoutKind
import com.adhavaa.commander.train.equipmentOf
import com.adhavaa.commander.train.liftDayFor
import com.adhavaa.commander.train.liftTargets
import com.adhavaa.commander.train.readExercises
import com.adhavaa.commander.train.trainPicture
import com.adhavaa.commander.train.weekIndex
import com.adhavaa.commander.MorningReport
import com.adhavaa.commander.UiState
import com.adhavaa.commander.data.FuelSettings
import com.adhavaa.commander.data.RecipeDraft
import com.adhavaa.commander.data.Settings
import com.adhavaa.commander.data.StoredSleep
import com.adhavaa.commander.fuel.Diet
import com.adhavaa.commander.fuel.Food
import com.adhavaa.commander.fuel.FuelData
import com.adhavaa.commander.fuel.Ingredient
import com.adhavaa.commander.fuel.LogEntry
import com.adhavaa.commander.fuel.Meal
import com.adhavaa.commander.fuel.Nutrients
import com.adhavaa.commander.fuel.Serving
import com.adhavaa.commander.fuel.SearchFilter
import com.adhavaa.commander.fuel.fuelPicture
import com.adhavaa.commander.fuel.index
import com.adhavaa.commander.fuel.parseCatalog
import com.adhavaa.commander.fuel.searchFoods
import com.adhavaa.commander.fuel.totals
import com.adhavaa.commander.model.Activity
import com.adhavaa.commander.model.WeighIn
import com.adhavaa.commander.model.SleepKind
import com.adhavaa.commander.model.SleepRecord
import com.adhavaa.commander.orders.CommandData
import com.adhavaa.commander.orders.DetectedNight
import com.adhavaa.commander.orders.OrderKind
import com.adhavaa.commander.orders.campaign
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
import java.util.zip.GZIPInputStream
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
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
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
        finish(controller)
        assertTrue("$name rendered blank (${colours.size} colours)", colours.size > 20)
    }

    /**
     * Tears the activity down and drains the main looper, so a running effect (the lift clock, a timer) is cancelled
     * cleanly. A dispatch left pending at teardown wedges Compose's UI dispatcher for every later test in the class.
     */
    private fun finish(controller: org.robolectric.android.controller.ActivityController<ComponentActivity>) {
        controller.pause().stop().destroy()
        shadowOf(Looper.getMainLooper()).idle()
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

    // --- Fuel ---------------------------------------------------------------------------------

    private val fuelSettings = FuelSettings(
        male = true, birthYear = 2004, heightCm = 178.0, startWeightKg = 86.0, start = LocalDate.of(2026, 9, 14), activity = Activity.ACTIVE,
    )

    /** Four weeks of weigh-ins (a few missed) drifting down and fully logged days around 2,350 kcal. */
    private fun fuelData(s: FuelSettings = fuelSettings): FuelData {
        val start = s.start!!
        val r = java.util.Random(4)
        val days = java.time.temporal.ChronoUnit.DAYS.between(start, day).toInt()
        val weighs = (0..days).filter { it % 6 != 5 }.map { WeighIn(start.plusDays(it.toLong()), 86.0 - it * 0.07 + r.nextGaussian() * 0.35) }
        val kcal = (0 until days).associate { start.plusDays(it.toLong()) to 2350.0 + r.nextGaussian() * 180 }
        return FuelData(s, weighs, kcal, emptySet())
    }

    private fun food(id: Long, name: String, kcal: Double, p: Double, f: Double, c: Double, recipe: Boolean = false) =
        Food("food:$id", name, "Your foods", Nutrients(kcal, p, f, c, 2.0), listOf(Serving("serving", 200.0)), Diet.PLANT, recipe)

    private fun entries(date: LocalDate): List<LogEntry> {
        fun e(id: Long, meal: Meal, name: String, amount: String, n: Nutrients) = LogEntry(id, date, meal, "food:$id", name, 100.0, amount, n)
        return listOf(
            e(1, Meal.BREAKFAST, "Idli (home)", "3 piece (120 g)", Nutrients(168.0, 5.0, 0.6, 36.0, 1.5)),
            e(2, Meal.BREAKFAST, "Sambar", "1 bowl (200 g)", Nutrients(142.0, 7.0, 4.0, 19.0, 5.0)),
            e(3, Meal.BREAKFAST, "Coffee with milk", "1 cup (150 g)", Nutrients(70.0, 3.0, 3.2, 7.0, 0.0)),
            e(4, Meal.LUNCH, "Rice, white, long-grain, regular, enriched, cooked", "1.5 cup (237 g)", Nutrients(308.0, 6.4, 0.7, 66.8, 0.9)),
            e(5, Meal.LUNCH, "Fish curry (seer fish)", "1 serving (180 g)", Nutrients(260.0, 28.0, 14.0, 6.0, 1.0)),
            e(6, Meal.LUNCH, "Beans poriyal", "1 serving (120 g)", Nutrients(110.0, 3.0, 6.0, 11.0, 4.0)),
            e(7, Meal.SNACKS, "Bananas, raw", "1 medium (118 g)", Nutrients(105.0, 1.3, 0.4, 27.0, 3.1)),
        )
    }

    private fun fuelUi(date: LocalDate = day, s: FuelSettings = fuelSettings, log: List<LogEntry> = entries(date)): FuelUi {
        val d = fuelData(s)
        return FuelUi(
            settings = s,
            picture = fuelPicture(d, date, day),
            entries = log,
            eaten = totals(log),
            weighIns = d.weighIns,
            trend = d.trend().entries.map { it.key to it.value },
            myFoods = listOf(food(1, "Idli (home)", 140.0, 4.0, 0.5, 30.0), food(2, "Sambar", 71.0, 3.5, 2.0, 9.5, recipe = true)),
            recent = emptyList(),
        )
    }

    @Test
    @Config(qualifiers = "w393dp-h2900dp-xhdpi")
    fun fuelTab() = render("11-fuel") {
        FuelScreen(fuelUi(), FuelActions())
    }

    @Test
    @Config(qualifiers = "w393dp-h1700dp-xhdpi")
    fun fuelNotSetUp() = render("12-fuel-setup-prompt") {
        val s = FuelSettings()
        FuelScreen(
            FuelUi(s, fuelPicture(FuelData(s, emptyList(), emptyMap(), emptySet()), day, day), emptyList(), Nutrients.ZERO, emptyList(), emptyList(), emptyList(), emptyList()),
            FuelActions(),
        )
    }

    @Test
    @Config(qualifiers = "w393dp-h1400dp-xhdpi")
    fun fuelFruitDay() = render("13-fuel-fruit-day") {
        val tuesday = LocalDate.of(2026, 10, 13)
        val fruit = listOf(
            LogEntry(1, tuesday, Meal.BREAKFAST, "usda:9040", "Bananas, raw", 236.0, "2 medium (236 g)", Nutrients(210.0, 2.6, 0.8, 53.9, 6.1)),
            LogEntry(2, tuesday, Meal.LUNCH, "usda:9176", "Mangos, raw", 330.0, "1 fruit (336 g)", Nutrients(198.0, 2.7, 1.3, 49.5, 5.3)),
        )
        FuelScreen(fuelUi(tuesday, log = fruit), FuelActions())
    }

    private fun usda(): List<Food> {
        val file = listOf(File("src/main/assets/foods.tsv.gz"), File("app/src/main/assets/foods.tsv.gz")).first { it.exists() }
        return GZIPInputStream(file.inputStream()).bufferedReader().useLines { parseCatalog(it) }
    }

    @Test
    fun addFood() = render("14-add-food") {
        val filter = SearchFilter(fishOnly = true)
        val results = searchFoods("lentils", index(usda()), filter)
        AddFoodScreen(
            title = "Add to Lunch", meal = Meal.LUNCH, initialFilter = filter, dayType = com.adhavaa.commander.fuel.DayType.NORMAL,
            myFoods = emptyList(), search = { _, _ -> results }, onChosen = { _, _, _, _ -> }, onBack = {},
            onNewFood = {}, onNewRecipe = {}, initialQuery = "lentils", initialResults = results,
        )
    }

    @Test
    @Config(qualifiers = "w393dp-h2200dp-xhdpi")
    fun fuelSetup() = render("15-fuel-setup") {
        FuelSetupScreen(FuelSettings(), day, { _, _ -> }, {})
    }

    @Test
    @Config(qualifiers = "w393dp-h1600dp-xhdpi")
    fun recipeEditor() = render("16-recipe") {
        val all = usda().associateBy { it.name }
        val draft = RecipeDraft(
            7, "Rasam",
            listOf(
                Ingredient(all.getValue("Tomatoes, red, ripe, raw, year round average"), 200.0),
                Ingredient(all.getValue("Pigeon peas (red gram), mature seeds, raw"), 40.0),
                Ingredient(all.getValue("Tamarinds, raw"), 15.0),
                Ingredient(all.getValue("Oil, sesame, salad or cooking"), 10.0),
            ),
            cookedG = 900.0, portions = 4,
        )
        RecipeEditor(draft, {}, {}, {}, {})
    }

    @Test
    @Config(qualifiers = "w393dp-h3400dp-xhdpi")
    fun todayWithFuel() = render("17-today-fuel") {
        val d = fuelData()
        val pic = fuelPicture(d.copy(weighIns = d.weighIns.filter { it.date != day }), day, day)
        val base = ready(day.atTime(7, 20), history())
        TodayScreen(base.copy(fuel = pic, eaten = totals(entries(day).take(3))), TodayActions())
    }

    /**
     * The real Fuel tab with its real ViewModel, database and packaged food list, as the app opens it. Catches
     * on-device problems the stateless renders can't, like the food list asset going missing.
     */

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

    @Test
    @Config(qualifiers = "w393dp-h1700dp-xhdpi")
    fun fuelTabLive() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
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
        finish(controller)
        assertTrue("live Fuel tab rendered blank (${colours.size} colours)", colours.size > 20)
    }

    // --- Train --------------------------------------------------------------------------------

    private val trainSettings = TrainSettings(
        start = LocalDate.of(2026, 9, 21), raceDate = LocalDate.of(2027, 3, 21), currentMinutes = 300.0, peakMinutes = 480.0,
    )
    private val thursday = LocalDate.of(2026, 10, 15)

    private fun exercises(): Map<String, Exercise> {
        val file = listOf(File("src/main/assets/exercises.tsv"), File("app/src/main/assets/exercises.tsv")).first { it.exists() }
        return file.inputStream().use { readExercises(it) }.associateBy { it.id }
    }

    private val startWeights = mapOf(
        "Barbell_Squat" to 80.0, "Romanian_Deadlift" to 70.0, "Lying_Leg_Curls" to 35.0, "Standing_Calf_Raises" to 60.0,
        "Barbell_Bench_Press_-_Medium_Grip" to 60.0, "Bent_Over_Barbell_Row" to 55.0, "Dumbbell_Shoulder_Press" to 18.0,
        "Wide-Grip_Lat_Pulldown" to 50.0, "Triceps_Pushdown" to 25.0, "Barbell_Curl" to 25.0, "Barbell_Deadlift" to 100.0,
        "Split_Squat_with_Dumbbells" to 16.0, "Leg_Extensions" to 40.0, "Seated_Calf_Raise" to 40.0, "Incline_Dumbbell_Press" to 20.0,
        "Seated_Cable_Rows" to 50.0, "Side_Lateral_Raise" to 8.0, "Face_Pull" to 20.0, "Hammer_Curls" to 12.0,
    )

    /** Three and a half weeks of following the plan: every lift at target+1 reps, every session logged. */
    private fun trainData(today: LocalDate, openLift: Boolean = false): TrainData {
        val workouts = ArrayList<Workout>()
        val sets = ArrayList<SetRecord>()
        var id = 1L
        var d = trainSettings.start!!
        while (d.isBefore(today) || (openLift && d == today)) {
            val week = weekPlan(weekIndex(trainSettings.start!!, d), 300.0, 480.0, weekIndex(trainSettings.start!!, trainSettings.raceDate!!), emptyMap())
            liftDayFor(d.dayOfWeek.value)?.let { lift ->
                val wid = id++
                val ended = d != today
                workouts += Workout(wid, d, WorkoutKind.LIFT, lift.label, ended, if (ended) 62.0 else 0.0, null, if (ended) 7.0 else null, "", false, startMs = wid)
                val items = if (ended) lift.items else lift.items.take(2)
                for (item in items) {
                    val w = startWeights[item.exerciseId] ?: 0.0
                    val n = if (ended) item.sets else if (item == items.first()) item.sets else 1
                    repeat(n) { k -> sets += SetRecord(id++, wid, d, item.exerciseId, k, w, item.hi + 1, 2) }
                }
            }
            if (d != today) {
                for (s in weekSessions(week).filter { it.day == d.dayOfWeek.value }) {
                    workouts += Workout(
                        id++, d, WorkoutKind.of(s.sport), if (s.brick) "Brick run" else s.sport.label, true, s.minutes - 5, null,
                        if (s.intensity == Intensity.QUALITY) 6.0 else 3.0, "", s.brick, startMs = id,
                    )
                }
                if (d.dayOfWeek.value in listOf(1, 3, 5)) workouts += Workout(id++, d, WorkoutKind.MOBILITY, "Recovery stretch", true, 8.0, null, null, "", false)
            }
            d = d.plusDays(1)
        }
        return TrainData(trainSettings, workouts, sets, emptyMap())
    }

    private fun trainUi(today: LocalDate, debtH: Double? = 1.5, openLift: Boolean = false, s: TrainSettings = trainSettings): TrainUi {
        val ex = exercises()
        val d = if (s.setUp) trainData(today, openLift) else TrainData(s, emptyList(), emptyList(), emptyMap())
        return TrainUi(
            s, trainPicture(d, today, today, debtH) { ex[it] }, d.workouts, d.sets, ex, debtH,
            PROGRAM.associate { day -> day.key to liftTargets(d, day, today) { id -> equipmentOf(ex[id]?.equipment ?: "") } },
        )
    }

    @Test
    @Config(qualifiers = "w393dp-h4600dp-xhdpi")
    fun trainTab() = render("19-train") {
        TrainScreen(trainUi(thursday), TrainActions())
    }

    @Test
    @Config(qualifiers = "w393dp-h1500dp-xhdpi")
    fun trainNotSetUp() = render("20-train-setup-prompt") {
        TrainScreen(trainUi(thursday, s = TrainSettings()), TrainActions())
    }

    @Test
    @Config(qualifiers = "w393dp-h2600dp-xhdpi")
    fun liftLogger() = render("21-lift") {
        val ui = trainUi(thursday, openLift = true)
        val w = ui.workouts.last { it.kind == WorkoutKind.LIFT }
        val start = 1_760_000_000_000L
        LiftScreen(ui, PROGRAM[2], w.id, start, Rest(start + 27 * 60_000L + 95_000L, 150, "Barbell Deadlift set 2"), LiftActions(), now = { start + 27 * 60_000L }, ticking = false)
    }

    @Test
    @Config(qualifiers = "w393dp-h1500dp-xhdpi")
    fun mobilityPlayer() = render("22-mobility") {
        MobilityPlayer(RECOVERY_STRETCH, exercises(), onDone = {}, onBack = {}, autoStart = false)
    }

    @Test
    @Config(qualifiers = "w393dp-h1500dp-xhdpi")
    fun trainSetup() = render("23-train-setup") {
        TrainSetupScreen(TrainSettings(raceDate = LocalDate.of(2027, 3, 21)), day, {}, {})
    }

    @Test
    @Config(qualifiers = "w393dp-h3400dp-xhdpi")
    fun todayWithTrain() = render("24-today-train") {
        val base = ready(thursday.atTime(7, 20), history())
        TodayScreen(base.copy(train = trainUi(thursday, debtH = 6.0).picture), TodayActions())
    }

    /** The real Train tab with its ViewModel, database and packaged exercise list. */
    @Test
    @Config(qualifiers = "w393dp-h1700dp-xhdpi")
    fun trainTabLive() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
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
        finish(controller)
        assertTrue("live Train tab rendered blank (${colours.size} colours)", colours.size > 20)
    }
}

