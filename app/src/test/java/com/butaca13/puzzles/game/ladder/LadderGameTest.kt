package com.butaca13.puzzles.game.ladder

import com.butaca13.puzzles.game.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LadderGameTest {

    // pato - gato - galo - gala - sala, and rato off to the side; "ralo" is a rare one.
    private val words = LadderWords(
        words = listOf("pato", "gato", "galo", "gala", "sala", "rato", "ralo", "mesa"),
        common = listOf("pato", "gato", "galo", "gala", "sala", "rato", "mesa"),
    )

    @Test
    fun `words link when they differ in one letter`() {
        assertEquals(setOf("gato", "rato"), words.neighbours("pato").toSet())
        assertTrue(words.linked("gato", "galo"))
        assertFalse(words.linked("gato", "gala"))
        assertEquals(4, words.distances("pato")["sala"])
        assertNull(words.distances("pato")["mesa"])
    }

    @Test
    fun `a step must be a word one letter away, and a miss is counted otherwise`() {
        val game = LadderGame.of("pato", "sala", Difficulty.EASY, words)

        assertEquals(4, game.best)
        val moved = game.step("gato", words)
        assertEquals(listOf("pato", "gato"), moved.chain)
        assertEquals(1, moved.step("gata", words).misses)
        assertEquals(1, moved.step("gala", words).misses)
    }

    @Test
    fun `a word already on the way goes back to it, and the target wins`() {
        var game = LadderGame.of("pato", "sala", Difficulty.EASY, words)
        for (w in listOf("gato", "galo", "gala")) game = game.step(w, words)

        assertEquals(listOf("pato", "gato"), game.step("gato", words).chain)
        assertEquals(listOf("pato", "gato", "galo"), game.undo().chain)
        val won = game.step("sala", words)
        assertTrue(won.isWon)
        assertEquals(4, won.steps)
    }

    @Test
    fun `a hint takes the next step of a shortest way, preferring common words`() {
        val game = LadderGame.of("pato", "galo", Difficulty.EASY, words)

        val hinted = game.hint(words)
        assertEquals("gato", hinted.current)
        assertEquals(1, hinted.hints)
        // From rato both gato and ralo lead to galo in one more step: the common gato comes first.
        assertEquals("gato", words.nextStep("rato", "galo"))
    }

    @Test
    fun `a puzzle joins two common words the level's number of steps apart`() {
        val puzzle = words.puzzle(Difficulty.EASY, Random(1))
        assertNotNull(puzzle)
        val (start, target) = puzzle!!

        assertTrue(start in words.common && target in words.common)
        assertTrue(words.distances(start, words.common)[target]!! in 3..4)
    }

    @Test
    fun `a game in progress is kept and restored whole`() {
        val game = LadderGame.of("pato", "sala", Difficulty.MEDIUM, words).step("gato", words).step("xxxx", words)

        assertEquals(game, LadderGame.fromJson(game.toJson()))
    }
}
