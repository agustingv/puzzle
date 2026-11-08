package com.butaca13.puzzles.game.panal

import com.butaca13.puzzles.game.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanalGameTest {

    private val words = PanalWords(
        words = listOf("cantor", "carta", "rata", "trono", "corto", "cota", "nota", "ratón", "arco", "pato", "cant", "toca"),
        common = listOf("cantor"),
    )

    private val game = PanalGame.of("cantor", 't', words.answers("cantor", 't'), Difficulty.EASY)

    @Test
    fun `the answers use only the seven letters and all have the centre one`() {
        val answers = words.answers("cantor", 't')

        assertTrue("cantor" in answers && "rata" in answers && "trono" in answers)
        // No t, a letter from outside, or one with an accent the list never has: none of them.
        assertFalse("arco" in answers)
        assertFalse("pato" in answers)
    }

    @Test
    fun `four letters score one, longer words their length, and a pangram seven more`() {
        assertEquals(1, game.points("rata"))
        assertEquals(5, game.points("carta"))
        // "cantor" has only six different letters: c a n t o r. A pangram needs the seven.
        val seven = PanalGame('t', "canorx".toList(), setOf("cantorx"), 1, Difficulty.EASY)
        assertEquals(7 + 7, seven.points("cantorx"))
        assertTrue(seven.isPangram("cantorx"))
    }

    @Test
    fun `a word that does not count says why, and counts a miss`() {
        assertEquals(PanalGame.Rejection.TOO_SHORT, game.check("rat"))
        assertEquals(PanalGame.Rejection.WRONG_LETTER, game.check("pato"))
        assertEquals(PanalGame.Rejection.NO_CENTER, game.check("arco"))
        assertEquals(PanalGame.Rejection.NOT_A_WORD, game.check("tatata"))
        assertNull(game.check("rata"))

        val found = game.submit("rata")
        assertEquals(listOf("rata"), found.found)
        assertEquals(PanalGame.Rejection.ALREADY_FOUND, found.check("rata"))
        assertEquals(1, found.submit("rata").misses)
    }

    @Test
    fun `the level sets the share of all the points to reach`() {
        val hard = PanalGame.of("cantor", 't', words.answers("cantor", 't'), Difficulty.HARD)

        assertEquals(Math.round(game.total * 0.06).toInt().coerceAtLeast(1), game.target)
        assertTrue(hard.target > game.target)
        var won = game
        for (w in game.answers) won = won.submit(w)
        assertTrue(won.isWon)
    }

    @Test
    fun `a game in progress is kept and restored whole`() {
        val played = game.submit("rata").submit("zzzz")

        assertEquals(played, PanalGame.fromJson(played.toJson()))
    }
}
