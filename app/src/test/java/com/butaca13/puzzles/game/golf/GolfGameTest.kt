package com.butaca13.puzzles.game.golf

import com.butaca13.puzzles.game.cards.Card
import com.butaca13.puzzles.game.cards.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GolfGameTest {

    private fun card(suit: Suit, rank: Int, faceUp: Boolean = true) = Card.of(suit, rank, faceUp)

    private fun game(vararg columns: List<Card>, waste: Card, stock: List<Card> = emptyList(), wraps: Boolean = true) =
        GolfGame(columns.toList() + List(7 - columns.size) { emptyList() }, stock, listOf(waste), wraps)

    @Test
    fun `a deal makes seven columns of five face up, one card on the waste and 16 in the stock`() {
        val game = GolfGame.deal(true, Random(1))

        assertTrue(game.columns.all { it.size == 5 && it.all { c -> c.faceUp } })
        assertEquals(16, game.stock.size)
        assertEquals(1, game.waste.size)
        assertEquals(52, (game.columns.flatten() + game.stock + game.waste).map { it.id }.toSet().size)
        assertEquals(GolfGame.deal(false, Random(2)), GolfGame.deal(false, Random(2)))
        assertNotEquals(GolfGame.deal(false, Random(2)), GolfGame.deal(false, Random(3)))
    }

    @Test
    fun `the top card of a column one rank from the waste goes onto it, of any suit`() {
        val game = game(listOf(card(Suit.CLUBS, 9), card(Suit.HEARTS, 6)), listOf(card(Suit.CLUBS, 2)), waste = card(Suit.SPADES, 5))

        val next = game.play(0)!!
        assertEquals(listOf(card(Suit.CLUBS, 9)), next.columns[0])
        assertEquals(card(Suit.HEARTS, 6), next.waste.last())
        assertEquals(1, next.moves)
        assertNull(game.play(1))
    }

    @Test
    fun `nothing goes on a king in the hard game, while the easy one goes round`() {
        val hard = game(listOf(card(Suit.HEARTS, 12)), listOf(card(Suit.CLUBS, 1)), waste = card(Suit.SPADES, 13), wraps = false)
        assertNull(hard.play(0))
        assertNull(hard.play(1))

        val easy = hard.copy(wraps = true)
        assertTrue(easy.play(0) != null)
        assertTrue(easy.play(1) != null)
        assertEquals("EASY", easy.variant)
        assertFalse(GolfGame.wrapsOf("HARD"))
    }

    @Test
    fun `the stock turns one card at a time, and with none to play and no stock the game is stuck`() {
        val game = game(listOf(card(Suit.HEARTS, 9)), waste = card(Suit.SPADES, 2), stock = listOf(card(Suit.CLUBS, 8, faceUp = false)))

        assertEquals(0, game.possibleMoves)
        assertFalse(game.isStuck)
        val drawn = game.draw()!!
        assertEquals(1, drawn.possibleMoves)
        assertNull(drawn.draw())
        val stuck = game.copy(stock = emptyList())
        assertTrue(stuck.isStuck)
        assertEquals(1, stuck.left)
    }

    @Test
    fun `clearing the columns wins`() {
        val game = game(listOf(card(Suit.HEARTS, 4)), waste = card(Suit.SPADES, 3))

        assertTrue(game.play(0)!!.isWon)
    }

    @Test
    fun `a game in progress is kept and restored whole`() {
        val played = GolfGame.deal(false, Random(6)).draw()!!

        assertEquals(played, GolfGame.fromJson(played.toJson()))
    }
}
