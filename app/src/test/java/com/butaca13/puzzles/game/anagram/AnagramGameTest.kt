package com.butaca13.puzzles.game.anagram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AnagramGameTest {

    /** Places the tiles that spell [text], one by one. */
    private fun AnagramGame.spell(text: String): AnagramGame {
        var game = this
        for (char in text) {
            val tile = game.pool.first { game.tiles[it] == char }
            game = game.place(tile)
        }
        return game
    }

    @Test
    fun `the letters come shuffled, plain, and never already spelling a word that wins`() {
        repeat(30) { seed ->
            val game = AnagramGame.of("árbol", listOf("árbol", "labor"), Random(seed))

            assertEquals("arbol".toList().sorted(), game.tiles.sorted())
            assertFalse(game.tiles.joinToString("") in game.accepted)
        }
    }

    @Test
    fun `the word wins, and so does another word of the list made of the same letters`() {
        val game = AnagramGame.of("árbol", listOf("árbol", "labor", "barco"), Random(1))

        assertEquals(setOf("arbol", "labor"), game.accepted)
        assertTrue(game.spell("arbol").isWon)
        assertTrue(game.spell("labor").isWon)
    }

    @Test
    fun `a full answer that spells no word counts a miss, and letters come back with a tap`() {
        val game = AnagramGame.of("casa", listOf("casa"), Random(2))

        val wrong = game.spell("saca")
        assertFalse(wrong.isWon)
        assertEquals(1, wrong.misses)
        val back = wrong.takeBack(1)
        assertEquals(1, back.answer.size)
        assertEquals(3, back.pool.size)
        assertTrue(back.clear().answer.isEmpty())
        assertTrue(back.clear().spell("casa").isWon)
    }

    @Test
    fun `shuffling moves only the letters still in the pool`() {
        val game = AnagramGame.of("ventana", listOf("ventana"), Random(3)).let { it.place(it.pool.first()) }

        val shuffled = game.shuffle(Random(9))
        assertEquals(game.answer, shuffled.answer)
        assertEquals(game.typed, shuffled.typed)
        assertEquals(game.tiles.sorted(), shuffled.tiles.sorted())
    }

    @Test
    fun `a game in progress is kept and restored whole`() {
        val game = AnagramGame.of("pájaro", listOf("pájaro"), Random(4)).let { it.place(it.pool.first()) }.showClue()

        assertEquals(game, AnagramGame.fromJson(game.toJson()))
        assertTrue(game.clueShown)
    }
}
