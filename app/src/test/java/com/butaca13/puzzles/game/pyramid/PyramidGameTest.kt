package com.butaca13.puzzles.game.pyramid

import com.butaca13.puzzles.game.cards.Card
import com.butaca13.puzzles.game.cards.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PyramidGameTest {

    private fun card(suit: Suit, rank: Int, faceUp: Boolean = true) = Card.of(suit, rank, faceUp)

    /** A pyramid with only the given places filled, by row and column. */
    private fun game(
        vararg placed: Pair<Pair<Int, Int>, Card>,
        stock: List<Card> = emptyList(),
        waste: List<Card> = emptyList(),
        passes: Int = 1,
    ): PyramidGame {
        val pyramid = MutableList<Card?>(PyramidGame.SIZE) { null }
        for ((at, card) in placed) pyramid[PyramidGame.indexOf(at.first, at.second)] = card
        return PyramidGame(pyramid, stock, waste, emptyList(), passes)
    }

    @Test
    fun `a deal puts 28 cards face up in the pyramid and 24 face down in the stock`() {
        val game = PyramidGame.deal(3, Random(2))

        assertEquals(28, game.pyramid.filterNotNull().size)
        assertTrue(game.pyramid.filterNotNull().all { it.faceUp })
        assertEquals(24, game.stock.size)
        assertTrue(game.stock.none { it.faceUp })
        assertEquals(52, (game.pyramid.filterNotNull() + game.stock).map { it.id }.toSet().size)
        assertEquals(PyramidGame.deal(3, Random(2)), PyramidGame.deal(3, Random(2)))
        assertNotEquals(PyramidGame.deal(3, Random(2)), PyramidGame.deal(3, Random(3)))
    }

    @Test
    fun `rows and columns map to places and back`() {
        assertEquals(0, PyramidGame.indexOf(0, 0))
        assertEquals(21, PyramidGame.indexOf(6, 0))
        assertEquals(6 to 6, PyramidGame.rowCol(27))
        assertEquals(3 to 1, PyramidGame.rowCol(7))
    }

    @Test
    fun `a card plays only once both cards over it are gone`() {
        val game = game(
            (5 to 0) to card(Suit.HEARTS, 6),
            (6 to 0) to card(Suit.SPADES, 7),
            (6 to 1) to card(Suit.CLUBS, 2),
        )

        val top = Spot.Pyramid(PyramidGame.indexOf(5, 0))
        fun without(vararg places: Int) = game.copy(pyramid = game.pyramid.toMutableList().also { p -> places.forEach { p[it] = null } })

        assertNull(game.playable(top))
        assertNull(without(PyramidGame.indexOf(6, 1)).playable(top))
        assertEquals(card(Suit.HEARTS, 6), without(PyramidGame.indexOf(6, 0), PyramidGame.indexOf(6, 1)).playable(top))
    }

    @Test
    fun `two cards that add up to 13 go together, a king alone`() {
        val game = game(
            (6 to 0) to card(Suit.HEARTS, 6),
            (6 to 1) to card(Suit.SPADES, 7),
            (6 to 2) to card(Suit.CLUBS, 13),
            (6 to 3) to card(Suit.CLUBS, 5),
        )

        val paired = game.pair(Spot.Pyramid(21), Spot.Pyramid(22))!!
        assertNull(paired.pyramid[21])
        assertNull(paired.pyramid[22])
        assertEquals(2, paired.discard.size)
        assertEquals(1, paired.moves)
        assertNull(game.pair(Spot.Pyramid(21), Spot.Pyramid(24)))
        assertNull(game.remove(Spot.Pyramid(21)))
        assertNull(game.remove(Spot.Pyramid(23))!!.pyramid[23])
    }

    @Test
    fun `the waste's top card pairs with the pyramid`() {
        val game = game((6 to 0) to card(Suit.HEARTS, 12), waste = listOf(card(Suit.SPADES, 5), card(Suit.CLUBS, 1)))

        val next = game.pair(Spot.Waste, Spot.Pyramid(21))!!
        assertTrue(next.isWon)
        assertEquals(listOf(card(Suit.SPADES, 5)), next.waste)
    }

    @Test
    fun `the stock goes round as many times as the level allows`() {
        var game = game((6 to 0) to card(Suit.HEARTS, 12), stock = listOf(card(Suit.SPADES, 3, faceUp = false)), passes = 2)

        game = game.draw()!!
        assertEquals(listOf(card(Suit.SPADES, 3)), game.waste)
        game = game.draw()!!
        assertEquals(2, game.pass)
        assertEquals(1, game.stock.size)
        game = game.draw()!!
        assertFalse(game.canDraw)
        assertNull(game.draw())
        assertTrue(game.isStuck)
    }

    @Test
    fun `possible moves count kings and pairs among the cards that play`() {
        val game = game(
            (6 to 0) to card(Suit.HEARTS, 6),
            (6 to 1) to card(Suit.SPADES, 7),
            (6 to 2) to card(Suit.CLUBS, 7),
            (6 to 3) to card(Suit.CLUBS, 13),
            (5 to 0) to card(Suit.DIAMONDS, 6),
        )

        // 6 with either 7, and the king; the covered 6 does not count.
        assertEquals(3, game.possibleMoves)
    }

    @Test
    fun `a game in progress is kept and restored whole`() {
        val dealt = PyramidGame.deal(3, Random(4)).draw()!!.draw()!!

        assertEquals(dealt, PyramidGame.fromJson(dealt.toJson()))
        assertEquals("PASS3", dealt.variant)
        assertEquals(3, PyramidGame.passesOf(dealt.variant))
        assertEquals(1, PyramidGame.passesOf("PASS1"))
    }
}
