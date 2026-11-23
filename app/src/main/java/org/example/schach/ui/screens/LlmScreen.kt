package org.example.schach.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.example.schach.engine.llm.LlmConfig
import org.example.schach.ui.components.SectionCard
import org.example.schach.ui.i18n.Text
import org.example.schach.ui.i18n.tr
import org.example.schach.ui.llm.LlmViewModel

/**
 * Einrichtung des LLM-Gegners. Alles, was das Modell braucht, steht auf einer
 * Seite: Adresse, Modell und Schlüssel. Danach erscheint der Gegner in Partie,
 * Duell und Turnier wie jede andere Engine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmScreen(
    onBack: () -> Unit,
    viewModel: LlmViewModel = viewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val test by viewModel.test.collectAsStateWithLifecycle()
    var draft by remember(settings.llm) { mutableStateOf(settings.llm) }
    var timeoutText by remember(settings.llm) { mutableStateOf(settings.llm.timeoutSeconds.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LLM-Gegner") },
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
            SectionCard {
                Text("API-Zugang", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Ein OpenAI-kompatibler Endpunkt genügt: OpenAI, Ollama, LM Studio oder " +
                        "ein eigener Server. Der LLM-Gegner erscheint danach in der Gegnerwahl " +
                        "von Partie, Duell und Turnier.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )

                OutlinedTextField(
                    value = draft.displayName,
                    onValueChange = { draft = draft.copy(displayName = it) },
                    label = { Text("Anzeigename") },
                    placeholder = { Text("z. B. GPT-4o mini") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.baseUrl,
                    onValueChange = { draft = draft.copy(baseUrl = it) },
                    label = { Text("API-Adresse") },
                    placeholder = { Text(LlmConfig.DEFAULT_BASE_URL) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = draft.model,
                    onValueChange = { draft = draft.copy(model = it) },
                    label = { Text("Modell") },
                    placeholder = { Text(LlmConfig.DEFAULT_MODEL) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = draft.apiKey,
                    onValueChange = { draft = draft.copy(apiKey = it) },
                    label = { Text("API-Schlüssel (optional)") },
                    supportingText = { Text("Bleibt nur auf diesem Gerät gespeichert.") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = timeoutText,
                    onValueChange = { value ->
                        timeoutText = value.filter { it.isDigit() }.take(3)
                        draft = draft.copy(
                            timeoutSeconds = timeoutText.toIntOrNull()?.coerceIn(5, 300) ?: 5,
                        )
                    },
                    label = { Text("Zeitlimit (Sekunden)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )

                Text(
                    text = if (draft.isConfigured) {
                        tr("Bereit: erscheint als „%s“ bei der Gegnerwahl.", draft.packName)
                    } else {
                        tr("Noch nicht eingerichtet: API-Adresse und Modell fehlen.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (draft.isConfigured) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(top = 12.dp),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Button(onClick = { viewModel.save(draft) }) {
                        Text("Speichern")
                    }
                    OutlinedButton(
                        onClick = { viewModel.testConnection(draft) },
                        enabled = !test.running,
                    ) {
                        Text(if (test.running) "Prüfe …" else "Verbindung testen")
                    }
                }

                if (test.running) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Frage das Modell …", style = MaterialTheme.typography.bodySmall)
                    }
                }
                test.message?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (test.failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            SectionCard {
                Text("So spielst du dagegen", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Partie: Unter „Partie starten“ den LLM-Gegner als Gegner wählen. " +
                        "Duell: Im Engine-Duell eine Seite auf das LLM stellen – etwa Engine " +
                        "gegen LLM oder LLM gegen LLM. Turnier: Das LLM als Teilnehmer auswählen, " +
                        "auch zusammen mit lokalen Engines.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = "Pro Zug geht eine Anfrage an die API. Die Antwortzeit zählt wie " +
                        "Bedenkzeit; die gewählte Stufe wird dem Modell als Spielstärke mitgegeben.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
