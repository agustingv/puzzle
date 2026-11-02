package com.agustingv.puzzles.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG contrast of what each theme actually draws: 4.5 for text, 3 for large text and shapes. */
class ThemeContrastTest {

    private fun contrast(a: Color, b: Color): Float {
        val light = maxOf(a.luminance(), b.luminance())
        val dark = minOf(a.luminance(), b.luminance())
        return (light + 0.05f) / (dark + 0.05f)
    }

    private fun assertContrast(what: String, a: Color, b: Color, minimum: Float = 4.5f) {
        val value = contrast(a, b)
        assertTrue("$what: $value < $minimum", value >= minimum)
    }

    private fun check(name: String, c: PuzzleColors) {
        assertContrast("$name text on paper", c.ink, c.paper)
        assertContrast("$name text on card", c.ink, c.card)
        assertContrast("$name text on keys and tints", c.ink, c.border)
        assertContrast("$name secondary text on paper", c.inkSoft, c.paper)
        assertContrast("$name secondary text on card", c.inkSoft, c.card)
        assertContrast("$name player digits", c.playerDigit, c.card)
        assertContrast("$name alerts", c.alert, c.card)
        assertContrast("$name text buttons", c.primary, c.paper)
        assertContrast("$name filled buttons", c.onPrimary, c.primary)
        assertContrast("$name clue numbers on blocked cells", Color.White, c.blocked)
        assertContrast("$name blocked cells stand out from playable ones", c.blocked, c.card, 1.15f)
        assertContrast("$name text on selected options", c.ink, c.selection.compositeOver(c.card))
        assertContrast("$name text on the selected cell", c.ink, Pink.copy(alpha = 0.5f).compositeOver(c.card), 3f)
        assertContrast("$name white on used-up letters", Color.White, Absent)

        val selectedCell = Pink.copy(alpha = 0.22f).compositeOver(c.card)
        assertContrast("$name digit on the selected cell", c.playerDigit, selectedCell, 3f)
        assertContrast("$name wrong digit on the selected cell", c.alert, selectedCell, 3f)
        assertContrast("$name fixed digit on a tinted cell", c.ink, selectedCell)
    }

    @Test
    fun `light theme is readable`() = check("light", LightColors)

    @Test
    fun `dark theme is readable`() = check("dark", DarkColors)
}
