package com.adhavaa.commander

import android.app.Application
import android.content.Context
import com.adhavaa.commander.data.FoodCatalog
import com.adhavaa.commander.data.FuelRepository
import com.adhavaa.commander.data.ExerciseCatalog
import com.adhavaa.commander.data.SettingsStore
import com.adhavaa.commander.data.TrainRepository
import com.adhavaa.commander.data.SleepRepository
import com.adhavaa.commander.data.db.CommanderDb
import com.adhavaa.commander.orders.CommandData
import kotlinx.coroutines.flow.first

/** Holds the app's single instances. Plain constructor wiring instead of a DI framework (D-29, revised in D-46). */
class CommanderApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    val db: CommanderDb = CommanderDb.open(context)
    val sleep = SleepRepository(db.dao())
    val catalog: FoodCatalog = FoodCatalog.fromAssets(context)
    val fuel = FuelRepository(db, catalog)
    val exercises: ExerciseCatalog = ExerciseCatalog.fromAssets(context)
    val train = TrainRepository(db)

    suspend fun commandData(): CommandData =
        CommandData(settings.settings.first(), sleep.sleep().map { it.record }, sleep.skipped())
}

fun Context.container(): AppContainer = (applicationContext as CommanderApplication).container
