package com.butaca13.puzzles.game.crossletters

import com.butaca13.puzzles.game.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CrossLettersGameTest {

    // A square whose rows and columns are the same words: faro / amor / rosa / orar.
    private val words = SquareWords(words = listOf("faro", "amor", "rosa", "orar", "azar"), common = listOf("faro", "amor", "rosa", "orar"))
    private val solution = listOf("faro", "amor", "rosa", "orar")

    private fun columns(rows: List<String>) = rows.indices.map { c -> rows.joinToString("") { it[c].toString() } }

    @Test
    fun `a square has every row and column among the words`() {
        val square = words.square(4, words.common, words.common, Random(1))

        assertNotNull(square)
        assertTrue(square!!.all { it in words.common })
        assertTrue(columns(square).all { it in words.common })
    }

    @Test
    fun `the letters not given wait in the tray, and placing them all right wins`() {
        var game = CrossLettersGame.of(solution, setOf(0, 5, 10, 15), Difficulty.EASY, Random(2))

        assertEquals(12, game.tray.size)
        assertEquals('f', game.cells[0])
        val letters = solution.joinToString("")
        for (cell in game.cells.indices.filter { game.cells[it] == null }) {
            game = game.place(game.tray.indexOf(letters[cell]), cell, words)
        }
        assertTrue(game.isWon)
        assertTrue(game.tray.isEmpty())
    }

    @Test
    fun `a letter put on another goes back to the tray, and a filled place gives its letter back`() {
        val game = CrossLettersGame.of(solution, emptySet(), Difficulty.MEDIUM, Random(3))

        val one = game.place(0, 0, words)
        val two = one.place(0, 0, words)
        assertEquals(game.tray.size - 1, two.tray.size)
        assertEquals(game.tray.size, two.takeBack(0).tray.size)
        assertFalse(two.isWon)
    }

    @Test
    fun `a hint puts the right letter in, fixed, taking it from wherever it is`() {
        val game = CrossLettersGame.of(solution, emptySet(), Difficulty.HARD, Random(4))
        val wrong = game.place(game.tray.indexOf('f'), 5, words)

        val hinted = wrong.hint(0, words)
        assertEquals('f', hinted.cells[0])
        assertTrue(0 in hinted.given)
        assertEquals(1, hinted.hints)
        // The f that was at the wrong place moved to the right one.
        assertEquals(null, hinted.cells[5])
    }

    @Test
    fun `a game in progress is kept and restored whole`() {
        val game = CrossLettersGame.of(solution, setOf(1, 2), Difficulty.EASY, Random(5)).let { it.place(0, 0, words) }

        assertEquals(game, CrossLettersGame.fromJson(game.toJson()))
    }
}
