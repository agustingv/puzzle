package com.butaca13.puzzles.game.cryptogram

import com.butaca13.puzzles.game.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CryptogramGameTest {

    private val game = CryptogramGame.of("Ver para creer.", Difficulty.HARD, Random(1)).copy(given = emptySet())

    private fun codeOf(letter: Char) = game.code.getValue(letter)

    @Test
    fun `no letter stands for itself, and the code keeps punctuation and spaces`() {
        repeat(20) { seed ->
            val code = CryptogramGame.randomCode(Random(seed))
            assertTrue(code.all { (p, c) -> p != c })
            assertEquals(ALPHABET.toSet(), code.values.toSet())
        }
        assertEquals(' ', game.coded[3])
        assertEquals('.', game.coded.last())
    }

    @Test
    fun `a guess reads its code letter everywhere, and a letter stands for one code letter only`() {
        val e = codeOf('e')
        val r = codeOf('r')

        val one = game.guess(e, 'e')
        assertEquals('e', one.reading(e))
        val moved = one.guess(r, 'e')
        assertNull(moved.reading(e))
        assertEquals('e', moved.reading(r))
    }

    @Test
    fun `reading every code letter right wins, and accents do not count`() {
        var g = CryptogramGame.of("Más vale tarde que nunca.", Difficulty.HARD, Random(2)).copy(given = emptySet())
        for (letter in "masvletrdqunc".toSet()) g = g.guess(g.code.getValue(letter), letter)

        assertTrue(g.isWon)
        assertEquals(0, g.left)
    }

    @Test
    fun `given letters can not be changed nor taken, and a hint gives one more`() {
        val v = codeOf('v')
        val withGiven = game.copy(given = setOf(v))
        assertEquals('v', withGiven.reading(v))
        assertEquals(withGiven, withGiven.guess(v, 'x'))
        assertEquals(withGiven, withGiven.guess(codeOf('p'), 'v'))

        val hinted = withGiven.guess(codeOf('a'), 'e').hint(codeOf('e'))
        assertEquals('e', hinted.reading(codeOf('e')))
        // The wrong guess of that letter elsewhere is gone.
        assertNull(hinted.reading(codeOf('a')))
        assertEquals(1, hinted.hints)
    }

    @Test
    fun `easier levels give more letters, and even the hardest gives one`() {
        val phrase = "Quien tiene un amigo tiene un tesoro."
        val easy = CryptogramGame.of(phrase, Difficulty.EASY, Random(3))
        val hard = CryptogramGame.of(phrase, Difficulty.HARD, Random(3))

        assertTrue(easy.given.size > hard.given.size)
        assertEquals(1, hard.given.size)
        assertFalse(easy.isWon)
    }

    @Test
    fun `a game in progress is kept and restored whole`() {
        val played = CryptogramGame.of("Cría cuervos y te sacarán los ojos.", Difficulty.MEDIUM, Random(4)).let { it.guess(it.code.getValue('c'), 'c') }.hint()

        assertEquals(played, CryptogramGame.fromJson(played.toJson()))
    }
}
