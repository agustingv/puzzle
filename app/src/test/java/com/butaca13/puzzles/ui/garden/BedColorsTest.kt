package com.butaca13.puzzles.ui.garden

import com.butaca13.puzzles.game.Difficulty
import com.butaca13.puzzles.game.garden.GardenGame
import com.butaca13.puzzles.ui.theme.GardenBeds
import com.butaca13.puzzles.ui.theme.PastelCoral
import com.butaca13.puzzles.ui.theme.PastelMint
import com.butaca13.puzzles.ui.theme.PastelPeach
import com.butaca13.puzzles.ui.theme.PastelPink
import com.butaca13.puzzles.ui.theme.PastelSand
import com.butaca13.puzzles.ui.theme.PastelTeal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.random.Random

class BedColorsTest {

    private val similar = listOf(PastelMint to PastelTeal, PastelPeach to PastelSand, PastelPink to PastelCoral)
        .map { (a, b) -> setOf(GardenBeds.indexOf(a), GardenBeds.indexOf(b)) }

    @Test
    fun `on every level, each bed has its own colour and no two alike touch`() {
        for (difficulty in Difficulty.entries) {
            repeat(40) { seed ->
                val game = GardenGame.generate(difficulty, Random(seed))
                val size = game.size
                val colors = bedColors(game.beds, size)

                assertEquals("$difficulty $seed", colors.size, colors.toSet().size)
                for (cell in game.beds.indices) {
                    for (dr in -1..1) for (dc in -1..1) {
                        val r = cell / size + dr
                        val c = cell % size + dc
                        if (r !in 0 until size || c !in 0 until size) continue
                        val pair = setOf(colors[game.beds[cell]], colors[game.beds[r * size + c]])
                        assertFalse("$difficulty $seed: $pair touch at $cell", pair in similar)
                    }
                }
                // The same board, the same colours: a game picked up again looks the same.
                assertEquals(colors, bedColors(game.beds, size))
            }
        }
    }

    @Test
    fun `beds keep their own colour when nothing alike touches`() {
        // Six beds in rows: mint (0) and teal (5) are far apart, so nothing moves.
        val beds = List(36) { it / 6 }

        assertEquals(listOf(0, 1, 2, 3, 4, 5), bedColors(beds, 6))
    }

    @Test
    fun `mint and teal side by side are pulled apart`() {
        // Bed 0 (mint) next to bed 5 (teal) in a 6x6 of columns, ordered so that they touch.
        val order = listOf(0, 5, 1, 2, 3, 4)
        val beds = List(36) { order[it % 6] }
        val colors = bedColors(beds, 6)

        assertFalse(setOf(colors[0], colors[5]) == setOf(GardenBeds.indexOf(PastelMint), GardenBeds.indexOf(PastelTeal)))
    }
}
