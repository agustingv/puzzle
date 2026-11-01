package com.agustingv.puzzles.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Same palette as the web version (public/css/game.css).
val Pink = Color(0xFFEA4C89)
val PinkDark = Color(0xFFC1366C)
val Purple = Color(0xFF6C5CE7)
val Ink = Color(0xFF1C1C28)
val InkSoft = Color(0xFF6B6B7F)
val Paper = Color(0xFFFBF9FD)
val Border = Color(0xFFECECF3)
val Accent = Color(0xFF17C793)
val Warn = Color(0xFFFFA63E)

private val Colors = lightColorScheme(
    primary = PinkDark,
    onPrimary = Color.White,
    secondary = Purple,
    onSecondary = Color.White,
    secondaryContainer = Pink.copy(alpha = 0.18f),
    onSecondaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceContainerLow = Color.White,
    onSurfaceVariant = InkSoft,
    outline = Border,
    outlineVariant = Border,
)

@Composable
fun PuzzlesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
