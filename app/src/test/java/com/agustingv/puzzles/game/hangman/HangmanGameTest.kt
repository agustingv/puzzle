package com.agustingv.puzzles.game.hangman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HangmanGameTest {

    private fun HangmanGame.play(letters: String) = letters.fold(this) { g, c -> g.guess(c) }

    @Test
    fun `a right letter reveals every copy without counting as a miss`() {
        val game = HangmanGame("casa").guess('a')

        assertEquals(listOf(null, 'a', null, 'a'), game.revealed)
        assertEquals(0, game.wrongCount)
    }

    @Test
    fun `a wrong letter counts as a miss`() {
        assertEquals(1, HangmanGame("casa").guess('x').wrongCount)
    }

    @Test
    fun `repeating a letter changes nothing`() {
        val game = HangmanGame("casa").guess('x')

        assertSame(game, game.guess('x'))
    }

    @Test
    fun `plain vowels reveal accented ones, showing the accent`() {
        val game = HangmanGame("ágil").guess('a')

        assertEquals(listOf('á', null, null, null), game.revealed)
        assertEquals(HangmanLetterState.CORRECT, game.letterState('a'))
    }

    @Test
    fun `enye is not the same letter as n`() {
        val game = HangmanGame("año").guess('n')

        assertEquals(1, game.wrongCount)
        assertEquals(listOf(null, 'ñ', null), game.guess('ñ').revealed)
    }

    @Test
    fun `revealing every letter wins`() {
        val game = HangmanGame("casa").play("csa")

        assertTrue(game.isWon)
        assertFalse(game.isLost)
    }

    @Test
    fun `six misses lose and freeze the game`() {
        val five = HangmanGame("casa").play("xyzwq")
        assertFalse(five.isOver)

        val lost = five.guess('k')
        assertTrue(lost.isLost)
        assertSame(lost, lost.guess('c'))
    }

    @Test
    fun `letter states`() {
        val game = HangmanGame("casa").play("cx")

        assertEquals(HangmanLetterState.CORRECT, game.letterState('c'))
        assertEquals(HangmanLetterState.INCORRECT, game.letterState('x'))
        assertEquals(HangmanLetterState.UNUSED, game.letterState('s'))
    }
}
