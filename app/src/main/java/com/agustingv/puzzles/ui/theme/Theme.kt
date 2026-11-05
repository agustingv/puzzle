package com.agustingv.puzzles.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.agustingv.puzzles.R

// The same in both themes: tiles and cards with white text on top, from the web's palette.
val Pink = Color(0xFFEA4C89)
val Purple = Color(0xFF6C5CE7)
val Accent = Color(0xFF17C793)
val AccentDark = Color(0xFF0FA87B)
val Warn = Color(0xFFFFA63E)
val Danger = Color(0xFFFF5470)
val Teal = Color(0xFF2DD4BF)

/** One colour per flower bed. Pastel in both themes: [BedDim] darkens them on the dark one. */
val GardenBeds = listOf(
    Color(0xFFA8E6CF), Color(0xFFA9D6F5), Color(0xFFCDB4F6), Color(0xFFF9B8CF), Color(0xFFFFD0A6),
    Color(0xFF8FD9D1), Color(0xFFE5D3B3), Color(0xFFCFE89A), Color(0xFFF7A89A),
)

/** Marks and lines drawn on a bed, which is light in both themes. */
val GardenInk = Color(0xFF1C1C28)

/** Fill of used-up letters, with white text. Not [InkSoft], which turns light in dark theme. */
val Absent = Color(0xFF6B6B7F)

/** Colours that change with the theme. The light values are the web's palette; it has no dark one. */
@Immutable
internal data class PuzzleColors(
    val ink: Color,
    val inkSoft: Color,
    val paper: Color,
    val card: Color,
    val border: Color,
    /** Cells that can not be played. Always dark, with white text on top in Kakuro and Camino. */
    val blocked: Color,
    /** Digits the player typed, set apart from a puzzle's fixed ones. */
    val playerDigit: Color,
    /** Text that flags something wrong. */
    val alert: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val selection: Color,
    val bedDim: Float,
)

internal val LightColors = PuzzleColors(
    ink = Color(0xFF1C1C28),
    inkSoft = Color(0xFF6B6B7F),
    paper = Color(0xFFFBF9FD),
    card = Color(0xFFFFFFFF),
    border = Color(0xFFECECF3),
    blocked = Color(0xFF1C1C28),
    playerDigit = Purple,
    alert = Color(0xFFC1366C),
    primary = Color(0xFFC1366C),
    onPrimary = Color.White,
    secondary = Purple,
    onSecondary = Color.White,
    selection = Pink.copy(alpha = 0.18f),
    bedDim = 0f,
)

internal val DarkColors = PuzzleColors(
    ink = Color(0xFFECECF3),
    inkSoft = Color(0xFFA9A9BB),
    paper = Color(0xFF14141C),
    card = Color(0xFF1F1F2B),
    border = Color(0xFF34344A),
    blocked = Color(0xFF0B0B10),
    playerDigit = Color(0xFFB3A9FF),
    alert = Color(0xFFFF8FB8),
    primary = Color(0xFFFF8FB8),
    onPrimary = Color(0xFF3B0A1F),
    secondary = Color(0xFFB3A9FF),
    onSecondary = Color(0xFF17123F),
    selection = Pink.copy(alpha = 0.32f),
    bedDim = 0.2f,
)

private val LocalColors = staticCompositionLocalOf { LightColors }

val Ink: Color
    @Composable @ReadOnlyComposable get() = LocalColors.current.ink
val InkSoft: Color
    @Composable @ReadOnlyComposable get() = LocalColors.current.inkSoft
val Paper: Color
    @Composable @ReadOnlyComposable get() = LocalColors.current.paper
val Card: Color
    @Composable @ReadOnlyComposable get() = LocalColors.current.card
val Border: Color
    @Composable @ReadOnlyComposable get() = LocalColors.current.border
val Blocked: Color
    @Composable @ReadOnlyComposable get() = LocalColors.current.blocked
val PlayerDigit: Color
    @Composable @ReadOnlyComposable get() = LocalColors.current.playerDigit
val Alert: Color
    @Composable @ReadOnlyComposable get() = LocalColors.current.alert
val BedDim: Float
    @Composable @ReadOnlyComposable get() = LocalColors.current.bedDim

private val PlusJakartaSans = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold).map {
        // A single variable font. Android 7 can not apply the weight axis and fakes bold instead.
        Font(
            resId = R.font.plus_jakarta_sans,
            weight = it,
            variationSettings = FontVariation.Settings(FontVariation.weight(it.weight)),
        )
    },
)

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = PlusJakartaSans),
        displayMedium = displayMedium.copy(fontFamily = PlusJakartaSans),
        displaySmall = displaySmall.copy(fontFamily = PlusJakartaSans),
        headlineLarge = headlineLarge.copy(fontFamily = PlusJakartaSans),
        headlineMedium = headlineMedium.copy(fontFamily = PlusJakartaSans),
        headlineSmall = headlineSmall.copy(fontFamily = PlusJakartaSans),
        titleLarge = titleLarge.copy(fontFamily = PlusJakartaSans),
        titleMedium = titleMedium.copy(fontFamily = PlusJakartaSans),
        titleSmall = titleSmall.copy(fontFamily = PlusJakartaSans),
        bodyLarge = bodyLarge.copy(fontFamily = PlusJakartaSans),
        bodyMedium = bodyMedium.copy(fontFamily = PlusJakartaSans),
        bodySmall = bodySmall.copy(fontFamily = PlusJakartaSans),
        labelLarge = labelLarge.copy(fontFamily = PlusJakartaSans),
        labelMedium = labelMedium.copy(fontFamily = PlusJakartaSans),
        labelSmall = labelSmall.copy(fontFamily = PlusJakartaSans),
    )
}

private fun PuzzleColors.toScheme(dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = selection,
        onSecondaryContainer = ink,
        background = paper,
        onBackground = ink,
        surface = card,
        onSurface = ink,
        surfaceContainerLow = card,
        surfaceContainerHigh = card,
        surfaceContainerHighest = card,
        onSurfaceVariant = inkSoft,
        outline = border,
        outlineVariant = border,
    )
} else {
    lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = selection,
        onSecondaryContainer = ink,
        background = paper,
        onBackground = ink,
        surface = card,
        onSurface = ink,
        surfaceContainerLow = card,
        surfaceContainerHigh = card,
        surfaceContainerHighest = card,
        onSurfaceVariant = inkSoft,
        outline = border,
        outlineVariant = border,
    )
}

/** Follows the system's light or dark setting. */
@Composable
fun PuzzlesTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors

    CompositionLocalProvider(LocalColors provides colors) {
        MaterialTheme(colorScheme = colors.toScheme(dark), typography = AppTypography) {
            // Text outside a Surface takes this colour: left unset it would be black on dark paper.
            CompositionLocalProvider(LocalContentColor provides colors.ink, content = content)
        }
    }
}
