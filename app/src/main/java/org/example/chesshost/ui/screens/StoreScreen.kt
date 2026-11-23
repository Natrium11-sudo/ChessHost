package org.example.chesshost.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import org.example.chesshost.ui.i18n.LocalLanguage
import org.example.chesshost.ui.i18n.engineTitle
import org.example.chesshost.ui.i18n.Text
import org.example.chesshost.ui.i18n.translate
import org.example.chesshost.ui.i18n.tr
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.chesshost.engine.packs.InstalledPack
import org.example.chesshost.engine.packs.PackInstallState
import org.example.chesshost.ui.components.SectionCard
import org.example.chesshost.ui.store.StoreEntry
import org.example.chesshost.ui.store.StoreViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreScreen(
    onBack: () -> Unit,
    viewModel: StoreViewModel = viewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val installing by viewModel.installing.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.clearMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Engines") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        ) {
            item {
                Text(
                    text = "Verfügbare Engines",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Jede Engine kann im Spiel gegen dich oder im Duell gegen eine andere Engine antreten. " +
                        "Neue Einträge kommen über den Katalog, ohne dass die App neu gebaut werden muss.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            items(items = state.entries, key = { it.pack.id }) { entry ->
                EnginePackCard(
                    entry = entry,
                    busy = entry.pack.id in installing,
                    onInstall = { viewModel.install(entry.pack) },
                    onCancel = { viewModel.cancelInstall(entry.pack) },
                    onRemove = { viewModel.remove(entry.pack) },
                    onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } },
                )
            }

            if (state.installed.isNotEmpty()) {
                item {
                    InstalledHeader(
                        count = state.installed.size,
                        bytes = state.installedBytes,
                        busy = installing.isNotEmpty(),
                        onRemoveAll = viewModel::removeAllDownloads,
                    )
                }
                items(items = state.orphans, key = { "orphan-${it.id}" }) { orphan ->
                    OrphanCard(
                        installed = orphan,
                        busy = orphan.id in installing,
                        onRemove = { viewModel.removeOrphan(orphan) },
                    )
                }
            }

            item {
                CatalogCard(
                    url = state.catalogUrl,
                    refreshing = state.refreshing,
                    error = state.catalogStatus.error,
                    remoteCount = state.catalogStatus.remoteCount,
                    onUrlChange = viewModel::setCatalogUrl,
                    onApply = viewModel::applyCatalogUrl,
                    onReset = {
                        viewModel.setCatalogUrl("")
                        viewModel.applyCatalogUrl()
                    },
                )
            }
        }
    }
}

@Composable
private fun EnginePackCard(
    entry: StoreEntry,
    busy: Boolean,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val pack = entry.pack
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = engineTitle(pack.name, pack.version),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (pack.tags.isNotEmpty()) {
                    val language = LocalLanguage.current
                    Text(
                        text = pack.tags.joinToString(" · ") { translate(language, it) },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (!pack.isBuiltin) {
                Text(
                    text = statusLabel(entry),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (entry.playable) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }

        if (pack.description.isNotBlank()) {
            Text(
                text = pack.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Text(
            text = buildList {
                pack.elo?.let { add("≈ $it Elo") }
                if (pack.license.isNotBlank()) add(tr(pack.license))
                if (pack.downloadBytes > 0) add(formatBytes(pack.downloadBytes))
                if (entry.state is PackInstallState.Installed) add(tr("installiert"))
            }.joinToString("  ·  "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        entry.unavailableReason?.let { reason ->
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                pack.homepage?.let { url ->
                    TextButton(onClick = { onOpenUrl(url) }) { Text("Website") }
                }
                pack.sourceUrl?.let { url ->
                    TextButton(onClick = { onOpenUrl(url) }) { Text("GitHub") }
                }
            }
            InstallAction(
                entry = entry,
                busy = busy,
                onInstall = onInstall,
                onCancel = onCancel,
                onRemove = onRemove,
            )
        }

        val installState = entry.state
        if (installState is PackInstallState.Downloading) {
            Column(Modifier.padding(top = 6.dp)) {
                LinearProgressIndicator(
                    progress = { installState.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = tr(
                        "%s von %s",
                        formatBytes(installState.doneBytes),
                        formatBytes(installState.totalBytes),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun InstallAction(
    entry: StoreEntry,
    busy: Boolean,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    when {
        entry.pack.isBuiltin -> Text(
            text = "Enthalten",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        entry.state is PackInstallState.Installed -> OutlinedButton(
            onClick = onRemove,
            enabled = !busy,
        ) {
            Text(if (entry.pack.isOptional) "Deaktivieren" else "Entfernen")
        }

        busy -> Button(onClick = onCancel) {
            Text("Abbrechen")
        }

        entry.canInstall -> Button(onClick = onInstall) {
            Text(if (entry.pack.isOptional) "Aktivieren" else "Installieren")
        }

        else -> Button(onClick = {}, enabled = false) {
            Text("Nicht verfügbar")
        }
    }
}

@Composable
private fun InstalledHeader(
    count: Int,
    bytes: Long,
    busy: Boolean,
    onRemoveAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Auf diesem Gerät",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = tr("%s Paket(e) · %s belegt", count, formatBytes(bytes)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onRemoveAll, enabled = !busy) {
            Text("Alle löschen")
        }
    }
}

@Composable
private fun OrphanCard(
    installed: InstalledPack,
    busy: Boolean,
    onRemove: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = installed.id,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = tr("%s · %s", tr("Nicht mehr im Katalog"), formatBytes(installed.bytes)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onRemove, enabled = !busy) {
                Text("Entfernen")
            }
        }
    }
}

@Composable
private fun CatalogCard(
    url: String,
    refreshing: Boolean,
    error: String?,
    remoteCount: Int,
    onUrlChange: (String) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
) {
    SectionCard {
        Text("Katalog erweitern", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "Trage die URL einer JSON-Datei mit derselben Struktur wie " +
                "engines/catalog.json ein. Einträge mit gleicher ID überschreiben den " +
                "eingebauten Eintrag, neue IDs werden ergänzt.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text("Katalog-URL") },
            placeholder = { Text("https://…/engines.json") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onApply, enabled = !refreshing) {
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Laden")
                }
            }
            OutlinedButton(onClick = onReset, enabled = !refreshing) {
                Text("Standard")
            }
        }
        when {
            error != null -> Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )

            remoteCount > 0 -> Text(
                text = tr("%s Einträge aus dem Netzkatalog aktiv.", remoteCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun statusLabel(entry: StoreEntry): String = when {
    entry.pack.isBuiltin -> "Enthalten"
    entry.state is PackInstallState.Installed -> if (entry.pack.isOptional) "Aktiv" else "Installiert"
    entry.state is PackInstallState.Failed -> "Fehlgeschlagen"
    entry.canInstall -> if (entry.pack.isOptional) "Inaktiv" else "Zum Download"
    else -> "Nicht verfügbar"
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "–"
    bytes >= 1024L * 1024L * 1024L -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format("%.0f kB", bytes / 1024.0)
    else -> "$bytes B"
}
