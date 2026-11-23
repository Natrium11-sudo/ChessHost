package org.example.chesshost.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FC7FF),
    onPrimary = Color(0xFF00314F),
    primaryContainer = Color(0xFF12455F),
    onPrimaryContainer = Color(0xFFD3E9FF),
    secondary = Color(0xFFB9C7D6),
    onSecondary = Color(0xFF23313D),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE3E6EA),
    surface = Color(0xFF161B21),
    onSurface = Color(0xFFE3E6EA),
    surfaceVariant = Color(0xFF232A32),
    onSurfaceVariant = Color(0xFFC2C9D1),
    outline = Color(0xFF8C9299),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E5B86),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE5FF),
    onPrimaryContainer = Color(0xFF001D31),
    secondary = Color(0xFF52606D),
    onSecondary = Color.White,
    background = Color(0xFFF8F9FB),
    onBackground = Color(0xFF191C1E),
    surface = Color.White,
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFE6EBF0),
    onSurfaceVariant = Color(0xFF43474B),
    outline = Color(0xFF73777C),
)

/** Überschriften in Serif – erinnert an Partienotation, Fließtext bleibt sachlich. */
private val SchachTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = FontFamily.Serif),
        displayMedium = base.displayMedium.copy(fontFamily = FontFamily.Serif),
        displaySmall = base.displaySmall.copy(fontFamily = FontFamily.Serif),
        headlineLarge = base.headlineLarge.copy(fontFamily = FontFamily.Serif),
        headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif),
        headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif),
    )
}

@Composable
fun SchachTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SchachTypography,
        content = content,
    )
}
