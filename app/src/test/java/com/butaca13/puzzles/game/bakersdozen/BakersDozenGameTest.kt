package com.butaca13.puzzles.game.bakersdozen

import com.butaca13.puzzles.game.cards.Card
import com.butaca13.puzzles.game.cards.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BakersDozenGameTest {

    private fun card(suit: Suit, rank: Int) = Card.of(suit, rank)

    private fun game(vararg columns: List<Card>, kingsOnEmpty: Boolean = false) =
        BakersDozenGame(List(4) { emptyList() }, columns.toList() + List(13 - columns.size) { emptyList() }, kingsOnEmpty)

    @Test
    fun `a deal makes thirteen columns of four, face up, every king at the bottom`() {
        val game = BakersDozenGame.deal(false, Random(1))

        assertTrue(game.columns.all { it.size == 4 && it.all { c -> c.faceUp } })
        assertEquals(52, game.columns.flatten().map { it.id }.toSet().size)
        for (column in game.columns) {
            val kings = column.count { it.rank == 13 }
            assertTrue(column.take(kings).all { it.rank == 13 })
        }
        assertEquals(BakersDozenGame.deal(true, Random(2)), BakersDozenGame.deal(true, Random(2)))
        assertNotEquals(BakersDozenGame.deal(true, Random(2)), BakersDozenGame.deal(true, Random(3)))
    }

    @Test
    fun `only the top card moves, onto one a rank higher of any suit`() {
        val game = game(listOf(card(Suit.CLUBS, 4), card(Suit.HEARTS, 7)), listOf(card(Suit.CLUBS, 8)))

        assertFalse(game.canPickUp(Pile.Column(0), 0))
        assertTrue(game.canPickUp(Pile.Column(0), 1))
        val moved = game.move(Pile.Column(0), 1, Pile.Column(1))!!
        assertEquals(listOf(8, 7), moved.columns[1].map { it.rank })
        assertNull(game.move(Pile.Column(0), 0, Pile.Column(1)))
    }

    @Test
    fun `an empty column stays empty, or takes a king in the Portuguese game`() {
        val game = game(listOf(card(Suit.CLUBS, 4), card(Suit.HEARTS, 13)), listOf(card(Suit.SPADES, 9)))

        assertFalse(game.canPlace(listOf(card(Suit.HEARTS, 13)), Pile.Column(2)))
        val portuguese = game.copy(kingsOnEmpty = true)
        assertTrue(portuguese.canPlace(listOf(card(Suit.HEARTS, 13)), Pile.Column(2)))
        assertFalse(portuguese.canPlace(listOf(card(Suit.SPADES, 9)), Pile.Column(2)))
        assertEquals("PORTUGUESE", portuguese.variant)
        assertTrue(BakersDozenGame.kingsOnEmptyOf(portuguese.variant))
    }

    @Test
    fun `a tap goes home first, then onto a column`() {
        val game = game(listOf(card(Suit.CLUBS, 5), card(Suit.HEARTS, 1)), listOf(card(Suit.SPADES, 6), card(Suit.CLUBS, 4)), listOf(card(Suit.DIAMONDS, 5)))

        assertEquals(Pile.Foundation(0), game.autoTarget(Pile.Column(0), 1))
        assertEquals(Pile.Column(2), game.autoTarget(Pile.Column(1), 1))
    }

    @Test
    fun `possible moves leave out those that gain nothing, and with none the game is stuck`() {
        val game = game(
            listOf(card(Suit.CLUBS, 8), card(Suit.HEARTS, 7)),
            listOf(card(Suit.SPADES, 8)),
            listOf(card(Suit.DIAMONDS, 1)),
        )
        // Only the ace home: the 7 already sits on an 8.
        assertEquals(1, game.possibleMoves)

        val stuck = game(listOf(card(Suit.HEARTS, 5)), listOf(card(Suit.SPADES, 9)))
        assertEquals(0, stuck.possibleMoves)
        assertTrue(stuck.isStuck)
    }

    @Test
    fun `a game with every column in order plays itself out to a win`() {
        val columns = Suit.entries.map { suit -> (13 downTo 1).map { card(suit, it) } }
        var game = game(*columns.toTypedArray())

        assertTrue(game.canAutoComplete)
        var steps = 0
        while (true) {
            game = game.autoCompleteStep() ?: break
            steps++
        }
        assertEquals(52, steps)
        assertTrue(game.isWon)
    }

    @Test
    fun `a game in progress is kept and restored whole`() {
        val game = BakersDozenGame.deal(true, Random(4))

        assertEquals(game, BakersDozenGame.fromJson(game.toJson()))
    }
}
