package com.agustingv.puzzles.game.memorama

import com.agustingv.puzzles.game.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MemoramaGameTest {

    private val game = MemoramaGame(listOf("a", "b", "a", "b"), Difficulty.EASY)

    @Test
    fun `the first card turns face up without spending a move`() {
        val one = game.flip(0)

        assertEquals(listOf(0), one.flipped)
        assertEquals(0, one.moves)
        assertTrue(one.isFaceUp(0))
        assertFalse(one.isFaceUp(1))
    }

    @Test
    fun `a matching pair stays up and counts one move`() {
        val matched = game.flip(0).flip(2)

        assertEquals(setOf(0, 2), matched.matched)
        assertTrue(matched.flipped.isEmpty())
        assertEquals(1, matched.moves)
        assertEquals(1, matched.matchedPairs)
    }

    @Test
    fun `a mismatched pair stays visible until the next card hides it`() {
        val missed = game.flip(0).flip(1)
        assertEquals(listOf(0, 1), missed.flipped)
        assertEquals(1, missed.moves)

        val next = missed.flip(2)
        assertEquals(listOf(2), next.flipped)
        assertEquals(1, next.moves)
    }

    @Test
    fun `turning the same card twice does nothing`() {
        val one = game.flip(0)

        assertEquals(one, one.flip(0))
    }

    @Test
    fun `tapping a matched card only hides a pending mismatch`() {
        val matched = game.flip(0).flip(2)
        assertEquals(matched, matched.flip(0))

        val missed = MemoramaGame(listOf("a", "a", "b", "c", "b", "c"), Difficulty.EASY).flip(0).flip(1).flip(2).flip(3)
        assertEquals(listOf(2, 3), missed.flipped)
        assertTrue(missed.flip(0).flipped.isEmpty())
    }

    @Test
    fun `out of range cards are ignored`() {
        assertSame(game, game.flip(-1))
        assertSame(game, game.flip(4))
    }

    @Test
    fun `matching everything wins and freezes the game`() {
        val won = game.flip(0).flip(2).flip(1).flip(3)

        assertTrue(won.isWon)
        assertEquals(2, won.moves)
        assertSame(won, won.flip(0))
    }

    @Test
    fun `generated decks match the web version's sizes, with exactly two of each symbol`() {
        val expected = mapOf(
            Difficulty.EASY to (12 to 4),
            Difficulty.MEDIUM to (16 to 4),
            Difficulty.HARD to (24 to 6),
            Difficulty.EXPERT to (30 to 6),
        )

        for ((difficulty, shape) in expected) {
            repeat(50) { seed ->
                val game = MemoramaGame.generate(difficulty, Random(seed))

                assertEquals(shape.first, game.cards.size)
                assertEquals(shape.second, game.columns)
                assertEquals(0, game.cards.size % game.columns)
                assertTrue(game.cards.groupingBy { it }.eachCount().values.all { it == 2 })
            }
        }
    }

    @Test
    fun `json round trip keeps the whole state`() {
        val played = MemoramaGame.generate(Difficulty.HARD, Random(4)).flip(0).flip(1)

        assertEquals(played, MemoramaGame.fromJson(played.toJson()))
    }
}
