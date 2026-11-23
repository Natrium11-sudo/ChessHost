package org.example.schach

import android.app.Application
import android.content.Context
import org.example.schach.data.GameRepository
import org.example.schach.data.PositionDatabase
import org.example.schach.data.PositionRepository
import org.example.schach.data.SchachDatabase
import org.example.schach.data.SettingsRepository
import org.example.schach.data.TournamentDatabase
import org.example.schach.data.TournamentRepository
import org.example.schach.engine.EngineManager
import org.example.schach.engine.EngineRegistry
import org.example.schach.engine.packs.EngineCatalog
import org.example.schach.engine.packs.EnginePackRepository
import org.example.schach.engine.tournament.TournamentController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Einfacher, zentraler Zugriff auf Datenbank, Einstellungen und Engines. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: SchachDatabase by lazy { SchachDatabase.build(appContext) }

    val gameRepository: GameRepository by lazy { GameRepository(database.savedGameDao()) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    val engineCatalog: EngineCatalog by lazy { EngineCatalog(appContext) }

    val enginePacks: EnginePackRepository by lazy { EnginePackRepository(appContext) }

    val engineRegistry: EngineRegistry by lazy {
        // Das LLM ist kein Katalog-Eintrag, sondern wird aus den Einstellungen
        // gebaut – die Registry fragt die Konfiguration bei jedem Zugriff ab.
        EngineRegistry(appContext, engineCatalog, enginePacks) { settingsRepository.state.value.llm }
    }

    val engineManager: EngineManager by lazy { EngineManager(engineRegistry) }

    private val positionDatabase: PositionDatabase by lazy { PositionDatabase.build(appContext) }

    val positionRepository: PositionRepository by lazy {
        PositionRepository(positionDatabase.positionDao())
    }

    private val tournamentDatabase: TournamentDatabase by lazy { TournamentDatabase.build(appContext) }

    val tournamentRepository: TournamentRepository by lazy {
        TournamentRepository(tournamentDatabase.tournamentDao())
    }

    /** Läuft app-weit weiter, auch wenn der Turnier-Screen geschlossen wird. */
    val tournamentController: TournamentController by lazy {
        TournamentController(appContext, tournamentRepository, engineRegistry)
    }

    init {
        // Eingebaute Engines kennen keine Installation, Netzkatalog optional nachladen.
        enginePacks.sync(engineCatalog.packs.value)
        scope.launch {
            engineCatalog.refresh(settingsRepository.state.value.catalogUrl)
            enginePacks.sync(engineCatalog.packs.value)
            // Ein Turnier, das beim letzten App-Ende noch lief, ist abgebrochen.
            tournamentController.recoverInterrupted()
        }
    }
}
