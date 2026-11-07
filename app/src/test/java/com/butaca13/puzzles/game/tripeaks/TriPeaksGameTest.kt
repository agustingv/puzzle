package com.butaca13.puzzles.game.tripeaks

import com.butaca13.puzzles.game.cards.Card
import com.butaca13.puzzles.game.cards.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TriPeaksGameTest {

    private fun card(suit: Suit, rank: Int, faceUp: Boolean = true) = Card.of(suit, rank, faceUp)

    /** The place in [TriPeaksGame.PLACES] at [row], [half] half cards across. */
    private fun at(row: Int, half: Int) = TriPeaksGame.PLACES.indexOf(row to half)

    private fun game(
        vararg placed: Pair<Int, Card>,
        waste: Card,
        stock: List<Card> = emptyList(),
        wraps: Boolean = true,
    ): TriPeaksGame {
        val peaks = MutableList<Card?>(TriPeaksGame.SIZE) { null }
        for ((i, c) in placed) peaks[i] = c
        return TriPeaksGame(peaks, stock, listOf(waste), wraps)
    }

    @Test
    fun `a deal makes three peaks of 28 cards, only the bottom row face up, with one card on the waste`() {
        val game = TriPeaksGame.deal(true, Random(1))

        assertEquals(28, game.peaks.filterNotNull().size)
        assertEquals(10, game.peaks.count { it!!.faceUp })
        assertEquals(23, game.stock.size)
        assertEquals(1, game.waste.size)
        assertEquals(52, (game.peaks.filterNotNull() + game.stock + game.waste).map { it.id }.toSet().size)
        assertEquals(listOf(3, 6, 9, 10), (0..3).map { row -> TriPeaksGame.PLACES.count { it.first == row } })
        assertEquals(TriPeaksGame.deal(true, Random(1)), TriPeaksGame.deal(true, Random(1)))
        assertNotEquals(TriPeaksGame.deal(true, Random(1)), TriPeaksGame.deal(true, Random(2)))
    }

    @Test
    fun `a card one rank from the waste's top goes onto it, of any suit`() {
        val game = game(at(3, 0) to card(Suit.HEARTS, 6), at(3, 2) to card(Suit.CLUBS, 9), waste = card(Suit.SPADES, 5))

        val next = game.play(at(3, 0))!!
        assertNull(next.peaks[at(3, 0)])
        assertEquals(card(Suit.HEARTS, 6), next.waste.last())
        assertEquals(1, next.moves)
        assertNull(game.play(at(3, 2)))
    }

    @Test
    fun `a card plays and turns over only once the cards over it are gone`() {
        val game = game(
            at(2, 1) to card(Suit.HEARTS, 4, faceUp = false),
            at(3, 0) to card(Suit.SPADES, 6),
            at(3, 2) to card(Suit.CLUBS, 7),
            waste = card(Suit.DIAMONDS, 5),
        )

        assertFalse(game.uncovered(at(2, 1)))
        val one = game.play(at(3, 0))!!
        assertFalse(one.peaks[at(2, 1)]!!.faceUp)
        val both = one.play(at(3, 2))!!
        assertTrue(both.peaks[at(2, 1)]!!.faceUp)
        assertTrue(both.uncovered(at(2, 1)))
    }

    @Test
    fun `the king and the ace follow each other only on the easy level`() {
        val easy = game(at(3, 0) to card(Suit.HEARTS, 1), waste = card(Suit.SPADES, 13))

        assertTrue(easy.play(at(3, 0))!!.isWon)
        assertNull(easy.copy(wraps = false).play(at(3, 0)))
        assertEquals("EASY", easy.variant)
        assertEquals("HARD", easy.copy(wraps = false).variant)
        assertFalse(TriPeaksGame.wrapsOf("HARD"))
    }

    @Test
    fun `the stock turns over one card at a time until it is used up`() {
        val game = game(at(3, 0) to card(Suit.HEARTS, 9), waste = card(Suit.SPADES, 2), stock = listOf(card(Suit.CLUBS, 8, faceUp = false)))

        assertEquals(0, game.possibleMoves)
        assertFalse(game.isStuck)
        val drawn = game.draw()!!
        assertEquals(card(Suit.CLUBS, 8), drawn.waste.last())
        assertEquals(1, drawn.possibleMoves)
        assertNull(drawn.draw())
        assertTrue(game.copy(stock = emptyList()).isStuck)
    }

    @Test
    fun `a game in progress is kept and restored whole`() {
        val played = TriPeaksGame.deal(false, Random(6)).draw()!!

        assertEquals(played, TriPeaksGame.fromJson(played.toJson()))
    }
}
