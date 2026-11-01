package com.agustingv.puzzles.game.word

import com.agustingv.puzzles.game.word.LetterState.ABSENT
import com.agustingv.puzzles.game.word.LetterState.CORRECT
import com.agustingv.puzzles.game.word.LetterState.PRESENT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.Normalizer

class WordGuessGameTest {

    private fun states(word: String, guess: String) =
        WordGuessGame(word).feedback(guess).map { it.state }

    @Test
    fun `marks exact, misplaced and missing letters`() {
        assertEquals(listOf(PRESENT, CORRECT, PRESENT, ABSENT), states("pato", "tapa"))
    }

    @Test
    fun `a repeated letter is only marked as many times as it appears`() {
        // "perro" has one "e", already at position 1: of the two in the guess, only the first counts.
        assertEquals(listOf(ABSENT, ABSENT, PRESENT, ABSENT, ABSENT), states("perro", "xxeex"))
    }

    @Test
    fun `an exact match takes priority over an earlier misplaced copy`() {
        // Only one "a" in "pato", at position 1: the guess's exact "a" wins, the first one is absent.
        assertEquals(listOf(ABSENT, CORRECT, ABSENT, ABSENT), states("pato", "aaxx"))
    }

    @Test
    fun `accents are ignored but enye is its own letter`() {
        assertTrue(WordGuessGame("ágil").guess("agil").isWon)
        assertFalse(WordGuessGame("año").guess("ano").isWon)
        assertEquals(listOf(CORRECT, ABSENT, CORRECT), states("año", "ano"))
    }

    @Test
    fun `feedback keeps the letters as typed`() {
        assertEquals("agil", WordGuessGame("ágil").feedback("agil").map { it.char }.joinToString(""))
    }

    @Test
    fun `wrong length guesses are rejected without spending an attempt`() {
        val game = WordGuessGame("casa")
        assertSame(game, game.guess("cas"))
        assertSame(game, game.guess("casas"))
    }

    @Test
    fun `six wrong guesses lose and further guesses are ignored`() {
        var game = WordGuessGame("casa")
        repeat(WordGuessGame.MAX_ATTEMPTS) {
            assertFalse(game.isOver)
            game = game.guess("mesa")
        }

        assertTrue(game.isLost)
        assertSame(game, game.guess("casa"))
    }

    @Test
    fun `winning on the last attempt is a win, not a loss`() {
        var game = WordGuessGame("casa")
        repeat(WordGuessGame.MAX_ATTEMPTS - 1) { game = game.guess("mesa") }
        game = game.guess("casa")

        assertTrue(game.isWon)
        assertFalse(game.isLost)
    }

    @Test
    fun `keyboard keeps the best state seen, folding accents`() {
        val game = WordGuessGame("casa").guess("sacó").guess("cosa")
        val keys = game.keyboardStates()

        assertEquals(CORRECT, keys['c'])
        assertEquals(CORRECT, keys['s'])
        assertEquals(CORRECT, keys['a'])
        assertEquals(ABSENT, keys['o'])
    }

    @Test
    fun `bundled word list has the expected shape`() {
        val words = WordList.parse(File("src/main/assets/words.json").readText())

        assertEquals(WordList.LENGTHS, words.keys.sorted())
        assertEquals(1692, words.values.sumOf { it.size })

        val keyboard = "abcdefghijklmnñopqrstuvwxyz"
        for ((length, list) in words) {
            for (word in list) {
                assertEquals(word, length, word.length)
                assertTrue("not NFC: $word", Normalizer.isNormalized(word, Normalizer.Form.NFC))
                assertTrue("not typeable: $word", WordGuessGame.fold(word).all { it in keyboard })
            }
        }
    }
}
