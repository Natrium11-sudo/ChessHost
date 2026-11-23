package org.example.schach.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import org.example.schach.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.schach.R
import org.example.schach.ui.navigation.Routes

@Composable
fun MenuScreen(onNavigate: (String) -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            Text(
                text = "Schach",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Spielen, zusehen, analysieren – mit Stockfish an Bord.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(28.dp))

            Card(
                onClick = { onNavigate(Routes.PLAY_ROUTE) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(148.dp).padding(start = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Partie starten",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "Gegen Stockfish – normal oder Schach960",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Image(
                        painter = painterResource(R.drawable.piece_nlt45),
                        contentDescription = null,
                        modifier = Modifier.size(120.dp).padding(end = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            MenuSection("Spielen") {
                MenuRow(
                    title = "Engine gegen Engine",
                    subtitle = "Zwei Gegner automatisch gegeneinander – auch mit LLM",
                    onClick = { onNavigate(Routes.ENGINE_MATCH_ROUTE) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                MenuRow(
                    title = "LLM als Gegner",
                    subtitle = "API-Modell einrichten – für Partie, Duell und Turnier",
                    onClick = { onNavigate(Routes.LLM) },
                )
            }

            MenuSection("Wettkampf") {
                MenuRow(
                    title = "Turniere",
                    subtitle = "Engines automatisch gegeneinander – Tabelle und Diagramme",
                    onClick = { onNavigate(Routes.TOURNAMENTS) },
                )
            }

            MenuSection("Werkzeuge") {
                MenuRow(
                    title = "Stellungen",
                    subtitle = "Stellung bauen, speichern und von dort spielen – inklusive Schach960",
                    onClick = { onNavigate(Routes.POSITIONS) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                MenuRow(
                    title = "Partien",
                    subtitle = "Gespeicherte PGN-Partien ansehen und nachspielen",
                    onClick = { onNavigate(Routes.HISTORY) },
                )
            }

            MenuSection("App") {
                MenuRow(
                    title = "Engines",
                    subtitle = "Katalog ansehen, auswählen und nachinstallieren",
                    onClick = { onNavigate(Routes.STORE) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                MenuRow(
                    title = "Einstellungen",
                    subtitle = "Sprache, Darstellung, Gegner, Ton",
                    onClick = { onNavigate(Routes.SETTINGS) },
                )
            }
        }
    }
}

/** Überschrift plus Karte mit den Einträgen einer Gruppe. */
@Composable
private fun MenuSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.padding(top = 18.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun MenuRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
