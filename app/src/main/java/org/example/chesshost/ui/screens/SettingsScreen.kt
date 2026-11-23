package org.example.chesshost.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.chesshost.data.ThemeMode
import org.example.chesshost.ui.components.DifficultyDropdown
import org.example.chesshost.ui.components.SectionCard
import org.example.chesshost.ui.i18n.AppLanguage
import org.example.chesshost.ui.i18n.Text
import org.example.chesshost.ui.i18n.tr
import org.example.chesshost.ui.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenStore: () -> Unit,
    onOpenLlm: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val language = AppLanguage.fromCode(settings.languageCode)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr("Zurück"))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Sprache zuerst: sie wirkt auf alles andere auf diesem Screen.
            SectionCard {
                Text("Sprache", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Wird sofort übernommen – ohne Neustart.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    AppLanguage.entries.forEach { entry ->
                        FilterChip(
                            selected = language == entry,
                            onClick = { viewModel.setLanguage(entry.code) },
                            label = { Text(entry.label) },
                        )
                    }
                }
            }

            SectionCard {
                Text("Darstellung", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Farbschema",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                SwitchRow(
                    title = "Koordinaten anzeigen",
                    subtitle = "Datei- und Reihenbeschriftung auf dem Brett",
                    checked = settings.showCoordinates,
                    onCheckedChange = viewModel::setShowCoordinates,
                )
                SwitchRow(
                    title = "Legale Züge zeigen",
                    subtitle = "Markiert erlaubte Zielfelder der gewählten Figur",
                    checked = settings.showLegalMoves,
                    onCheckedChange = viewModel::setShowLegalMoves,
                )
                SwitchRow(
                    title = "Letzten Zug markieren",
                    subtitle = "Hebt Start- und Zielfeld hervor",
                    checked = settings.highlightLastMove,
                    onCheckedChange = viewModel::setHighlightLastMove,
                )
            }

            SectionCard {
                Text("Ton und Haptik", style = MaterialTheme.typography.titleSmall)
                SwitchRow(
                    title = "Ton",
                    subtitle = "Züge und Schach hörbar machen",
                    checked = settings.soundEnabled,
                    onCheckedChange = viewModel::setSoundEnabled,
                )
                SwitchRow(
                    title = "Haptik",
                    subtitle = "Vibration bei Zügen",
                    checked = settings.hapticsEnabled,
                    onCheckedChange = viewModel::setHapticsEnabled,
                )
            }

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Engine", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = tr(
                                "%s · %s Engines verfügbar",
                                viewModel.defaultEngineName,
                                viewModel.engineCount,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DifficultyDropdown(
                        label = "Standardstufe",
                        selected = settings.difficulty,
                        onSelected = viewModel::setDifficulty,
                    )
                }
                OutlinedButton(
                    onClick = onOpenStore,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("Engines verwalten")
                }
            }

            SectionCard {
                Text("LLM-Gegner", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (settings.llm.isConfigured) {
                        tr(
                            "Bereit: %s spielt über %s.",
                            settings.llm.packName,
                            settings.llm.model,
                        )
                    } else {
                        tr(
                            "Noch nicht eingerichtet. Mit einer API-Adresse und einem Modell " +
                                "spielt das LLM in Partie, Duell und Turnier mit.",
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedButton(
                    onClick = onOpenLlm,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("LLM einrichten")
                }
            }

            SectionCard {
                Text("Über", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Schach für Android · Version 1.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = "Engine: Stockfish 17.1 (GPLv3, stockfishchess.org). " +
                        "Figuren: Cburnett-Schachfiguren von Colin M. L. Burnett, CC BY-SA 3.0, " +
                        "via Wikimedia Commons.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp).widthIn(max = 560.dp),
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
