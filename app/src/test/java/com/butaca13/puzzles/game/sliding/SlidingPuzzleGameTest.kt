package com.butaca13.puzzles.game.sliding

import com.butaca13.puzzles.game.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SlidingPuzzleGameTest {

    private fun easy(vararg tiles: Int) = SlidingPuzzleGame(tiles.toList(), Difficulty.EASY)

    @Test
    fun `sizes match the web version`() {
        val sizes = Difficulty.entries.map { SlidingPuzzleGame.sizeFor(it) }
        assertEquals(listOf(3, 4, 5, 6), sizes)
    }

    @Test
    fun `adjacent tile slides into the blank and counts the move`() {
        val game = easy(1, 2, 3, 4, 5, 6, 7, 0, 8)

        val moved = game.move(6)

        assertEquals(listOf(1, 2, 3, 4, 5, 6, 0, 7, 8), moved.tiles)
        assertEquals(1, moved.moves)
    }

    @Test
    fun `illegal moves leave the game untouched`() {
        val game = easy(1, 2, 3, 4, 5, 6, 7, 0, 8)

        assertSame("not adjacent", game, game.move(0))
        assertSame("diagonal", game, game.move(3))
        assertSame("the blank itself", game, game.move(7))
        assertSame("out of bounds", game, game.move(9))
        assertSame("negative", game, game.move(-1))
    }

    @Test
    fun `position at the end of a row is not adjacent to the start of the next`() {
        val game = easy(1, 2, 3, 0, 5, 6, 7, 8, 4)

        assertSame(game, game.move(2))
    }

    @Test
    fun `last move solves the board and further moves are ignored`() {
        val solved = easy(1, 2, 3, 4, 5, 6, 7, 0, 8).move(8)

        assertTrue(solved.isSolved)
        assertSame(solved, solved.move(7))
    }

    @Test
    fun `generated boards are permutations, unsolved and solvable at every difficulty`() {
        for (difficulty in Difficulty.entries) {
            repeat(200) { seed ->
                val game = SlidingPuzzleGame.generate(difficulty, Random(seed))
                val total = game.size * game.size

                assertEquals((0 until total).toList(), game.tiles.sorted())
                assertFalse(game.isSolved)
                assertEquals(0, game.moves)
                assertTrue("seed $seed, $difficulty: ${game.tiles}", isSolvable(game))
            }
        }
    }

    /** Standard parity rule for an N×N sliding puzzle whose goal has the blank in the last cell. */
    private fun isSolvable(game: SlidingPuzzleGame): Boolean {
        val values = game.tiles.filter { it != 0 }
        val inversions = values.indices.sumOf { i ->
            (i + 1 until values.size).count { j -> values[i] > values[j] }
        }
        val blankRowFromBottom = game.size - game.tiles.indexOf(0) / game.size

        return if (game.size % 2 == 1) {
            inversions % 2 == 0
        } else {
            (inversions + blankRowFromBottom) % 2 == 1
        }
    }

    @Test
    fun `json round trip keeps the whole state`() {
        val played = SlidingPuzzleGame.generate(Difficulty.HARD, Random(5)).let { g -> g.move((g.tiles.indexOf(0) + 1) % 25) }

        assertEquals(played, SlidingPuzzleGame.fromJson(played.toJson()))
    }
}
