package org.example.chesshost.ui.store

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.example.chesshost.App
import org.example.chesshost.engine.packs.CatalogStatus
import org.example.chesshost.engine.packs.EnginePack
import org.example.chesshost.ui.i18n.trNow
import org.example.chesshost.engine.packs.InstalledPack
import org.example.chesshost.engine.packs.PackInstallState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoreEntry(
    val pack: EnginePack,
    val state: PackInstallState,
    /** Jetzt spielbar (installiert bzw. eingebaut und vom Gerät unterstützt). */
    val playable: Boolean,
    /** Kann über den Store heruntergeladen werden. */
    val canInstall: Boolean,
    val unavailableReason: String?,
)

data class StoreUiState(
    val entries: List<StoreEntry> = emptyList(),
    /** Alles, was im App-Speicher liegt – auch Packs ohne Katalogeintrag. */
    val installed: List<InstalledPack> = emptyList(),
    val installedBytes: Long = 0L,
    val catalogUrl: String = "",
    val catalogStatus: CatalogStatus = CatalogStatus(),
    val refreshing: Boolean = false,
    val message: String? = null,
) {
    /** Installierte Ordner, zu denen der Katalog keinen Eintrag mehr kennt. */
    val orphans: List<InstalledPack>
        get() {
            val known = entries.map { it.pack.id }.toSet()
            return installed.filterNot { it.id in known }
        }
}

class StoreViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as App).container
    private val catalog = container.engineCatalog
    private val packs = container.enginePacks
    private val registry = container.engineRegistry
    private val settings = container.settingsRepository

    private val _ui = MutableStateFlow(
        StoreUiState(
            catalogUrl = settings.state.value.catalogUrl,
            catalogStatus = catalog.status.value,
            installed = packs.installed.value,
            installedBytes = packs.installed.value.totalBytes(),
        ),
    )
    val ui: StateFlow<StoreUiState> = _ui.asStateFlow()

    private val _installing = MutableStateFlow<Set<String>>(emptySet())
    val installing: StateFlow<Set<String>> = _installing.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            catalog.packs.collect { list ->
                packs.sync(list)
                refreshEntries()
            }
        }
        viewModelScope.launch {
            catalog.status.collect { status ->
                _ui.update { it.copy(catalogStatus = status) }
            }
        }
        viewModelScope.launch {
            packs.installed.collect { installed ->
                _ui.update {
                    it.copy(installed = installed, installedBytes = installed.totalBytes())
                }
                refreshEntries()
            }
        }
        refreshEntries()
    }

    fun install(pack: EnginePack) {
        if (pack.id in _installing.value) return
        _installing.update { it + pack.id }
        jobs[pack.id] = viewModelScope.launch {
            try {
                val success = packs.install(pack)
                if (!success) {
                    _ui.update {
                        it.copy(message = trNow("Installation von %s fehlgeschlagen.", pack.title))
                    }
                    return@launch
                }

                // Ein Netz der falschen Architektur startet die Engine nicht.
                // Dann bleibt nichts Halbes liegen.
                val runs = registry.probe(pack)
                if (!runs) {
                    packs.remove(pack)
                    _ui.update {
                        it.copy(
                            message = trNow(
                                "%s läuft auf diesem Gerät nicht und wurde wieder entfernt.",
                                pack.title,
                            ),
                        )
                    }
                } else {
                    _ui.update {
                        it.copy(
                            message = if (pack.isOptional) {
                                trNow("%s ist aktiviert.", pack.title)
                            } else {
                                trNow("%s ist installiert.", pack.title)
                            },
                        )
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                _ui.update { it.copy(message = trNow("Download von %s abgebrochen.", pack.title)) }
            } finally {
                _installing.update { it - pack.id }
                jobs.remove(pack.id)
                refreshEntries()
            }
        }
    }

    fun cancelInstall(pack: EnginePack) {
        jobs[pack.id]?.cancel()
    }

    fun remove(pack: EnginePack) = removeEntry(pack.id, pack.title) { packs.remove(pack) }

    fun removeOrphan(installed: InstalledPack) =
        removeEntry(installed.id, installed.id) { packs.removeDownload(installed.id) }

    private fun removeEntry(packId: String, label: String, action: suspend () -> Boolean) {
        if (packId in _installing.value) return
        _installing.update { it + packId }
        viewModelScope.launch {
            try {
                val deleted = action()
                if (deleted) clearEngineSelection(packId)
                _ui.update {
                    it.copy(
                        message = if (deleted) {
                            trNow("%s wurde entfernt.", label)
                        } else {
                            trNow("%s konnte nicht vollständig gelöscht werden.", label)
                        },
                    )
                }
            } finally {
                _installing.update { it - packId }
                refreshEntries()
            }
        }
    }

    fun removeAllDownloads() {
        if (_installing.value.isNotEmpty()) return
        viewModelScope.launch {
            val optionalIds = catalog.packs.value.filter { it.isOptional }.map { it.id }
            val ids = packs.installed.value.map { it.id } + optionalIds
            val count = packs.removeAll(optionalIds)
            ids.forEach { clearEngineSelection(it) }
            _ui.update { it.copy(message = trNow("%s Paket(e) entfernt.", count)) }
            refreshEntries()
        }
    }

    fun setCatalogUrl(url: String) {
        _ui.update { it.copy(catalogUrl = url) }
    }

    fun applyCatalogUrl() {
        val url = _ui.value.catalogUrl
        settings.setCatalogUrl(url)
        refreshCatalog()
    }

    fun refreshCatalog() {
        if (_ui.value.refreshing) return
        _ui.update { it.copy(refreshing = true, message = null) }
        viewModelScope.launch {
            val status = catalog.refresh(settings.state.value.catalogUrl)
            packs.sync(catalog.packs.value)
            refreshEntries()
            _ui.update {
                it.copy(
                    refreshing = false,
                    message = status.error ?: if (status.remoteCount > 0) {
                        trNow("%s Einträge aus dem Netzkatalog geladen.", status.remoteCount)
                    } else {
                        "Eingebauter Katalog aktiv."
                    },
                )
            }
        }
    }

    fun clearMessage() = _ui.update { it.copy(message = null) }

    /** Gewählte Engine zurücksetzen, damit keine gelöschte Engine aktiv bleibt. */
    private fun clearEngineSelection(packId: String) {
        val current = settings.state.value
        if (current.playEngineId == packId) settings.setPlayEngineId(null)
        if (current.whiteEngineId == packId) settings.setWhiteEngineId(null)
        if (current.blackEngineId == packId) settings.setBlackEngineId(null)
    }

    private fun refreshEntries() {
        val states = packs.states.value
        val installedIds = packs.installed.value.map { it.id }.toSet()
        val list = catalog.packs.value
            .sortedByDescending { registry.isSupported(it) || it.id in installedIds }
            .map { pack ->
                val canInstall = registry.canInstall(pack)
                StoreEntry(
                    pack = pack,
                    state = states[pack.id] ?: PackInstallState.NotInstalled,
                    playable = registry.isSupported(pack),
                    canInstall = canInstall,
                    unavailableReason = if (canInstall) null else registry.unavailableReason(pack),
                )
            }
        _ui.update { it.copy(entries = list) }
    }
}

private fun List<InstalledPack>.totalBytes(): Long = sumOf { it.bytes }
