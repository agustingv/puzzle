package com.butaca13.puzzles.game.fortythieves

import com.butaca13.puzzles.game.cards.Card
import com.butaca13.puzzles.game.cards.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FortyThievesGameTest {

    private fun card(suit: Suit, rank: Int, faceUp: Boolean = true, copy: Int = 0) = Card.of(suit, rank, faceUp, copy)

    /** A column that only fills room: a king of clubs, which nothing goes on and which goes nowhere. */
    private fun filler(i: Int) = listOf(card(Suit.CLUBS, 13, copy = i % 2), card(Suit.CLUBS, 3, copy = i % 2))

    private fun game(
        vararg columns: List<Card>,
        stock: List<Card> = emptyList(),
        waste: List<Card> = emptyList(),
        foundations: List<List<Card>> = List(8) { emptyList() },
        passes: Int = 1,
    ) = FortyThievesGame(stock, waste, foundations, columns.toList() + List(10 - columns.size) { filler(it) }, passes)

    @Test
    fun `a deal puts four cards face up in each of ten columns and the other 64 in the stock`() {
        val game = FortyThievesGame.deal(1, Random(1))

        assertTrue(game.columns.all { it.size == 4 && it.all { c -> c.faceUp } })
        assertEquals(64, game.stock.size)
        assertTrue(game.stock.none { it.faceUp })
        assertEquals(104, (game.stock + game.columns.flatten()).map { it.id }.toSet().size)
        assertEquals(8, game.foundations.size)
        assertEquals(FortyThievesGame.deal(2, Random(5)), FortyThievesGame.deal(2, Random(5)))
        assertNotEquals(FortyThievesGame.deal(2, Random(5)), FortyThievesGame.deal(2, Random(6)))
    }

    @Test
    fun `columns build down by suit, and any card goes on an empty one`() {
        val game = game(listOf(card(Suit.HEARTS, 8)), emptyList())

        assertTrue(game.canPlace(listOf(card(Suit.HEARTS, 7)), Pile.Column(0)))
        assertFalse(game.canPlace(listOf(card(Suit.DIAMONDS, 7)), Pile.Column(0)))
        assertFalse(game.canPlace(listOf(card(Suit.HEARTS, 6)), Pile.Column(0)))
        assertTrue(game.canPlace(listOf(card(Suit.SPADES, 4)), Pile.Column(1)))
    }

    @Test
    fun `a run moves at once only with empty columns enough to move it card by card`() {
        val run = listOf(card(Suit.HEARTS, 6), card(Suit.HEARTS, 5))
        val full = game(run, listOf(card(Suit.HEARTS, 7)))

        // No empty column: one card at a time.
        assertEquals(1, full.maxRun(toEmptyColumn = false))
        assertFalse(full.canPickUp(Pile.Column(0), 0))
        assertTrue(full.canPickUp(Pile.Column(0), 1))

        val roomy = full.copy(columns = full.columns.toMutableList().also { it[9] = emptyList() })
        assertEquals(2, roomy.maxRun(toEmptyColumn = false))
        val moved = roomy.move(Pile.Column(0), 0, Pile.Column(1))!!
        assertEquals(listOf(7, 6, 5), moved.columns[1].map { it.rank })
        assertEquals(1, moved.moves)
    }

    @Test
    fun `eight foundations take either deck's cards, by suit from the ace`() {
        val game = game(listOf(card(Suit.HEARTS, 1)), listOf(card(Suit.HEARTS, 1, copy = 1)))

        val one = game.move(Pile.Column(0), 0, Pile.Foundation(0))!!
        val two = one.move(Pile.Column(1), 0, Pile.Foundation(1))!!
        assertEquals(listOf(1, 1), two.foundations.take(2).map { it.size })
        assertFalse(two.canPlace(listOf(card(Suit.SPADES, 2)), Pile.Foundation(0)))
        assertTrue(two.canPlace(listOf(card(Suit.HEARTS, 2)), Pile.Foundation(1)))
    }

    @Test
    fun `the stock turns one card at a time, and goes round as many times as the level allows`() {
        var game = game(stock = listOf(card(Suit.SPADES, 9, faceUp = false)), passes = 2)

        game = game.draw()!!
        assertEquals(listOf(card(Suit.SPADES, 9)), game.waste)
        game = game.draw()!!
        assertEquals(2, game.pass)
        game = game.draw()!!
        assertFalse(game.canDraw)
        assertNull(game.draw())
    }

    @Test
    fun `a tap goes home first, then onto a card of its suit`() {
        val game = game(listOf(card(Suit.CLUBS, 9), card(Suit.HEARTS, 1)), listOf(card(Suit.HEARTS, 8)), waste = listOf(card(Suit.HEARTS, 7)))

        assertEquals(Pile.Foundation(0), game.autoTarget(Pile.Column(0), 1))
        assertEquals(Pile.Column(1), game.autoTarget(Pile.Waste, 0))
    }

    @Test
    fun `possible moves leave out those that gain nothing, and the game is stuck with none and no stock`() {
        val game = game(
            listOf(card(Suit.HEARTS, 8), card(Suit.HEARTS, 7)),
            listOf(card(Suit.HEARTS, 8, copy = 1)),
            listOf(card(Suit.DIAMONDS, 1)),
        )
        // Only the ace home: the 7 already sits on an 8 of hearts.
        assertEquals(1, game.possibleMoves)

        val stuck = game(listOf(card(Suit.HEARTS, 5)), listOf(card(Suit.SPADES, 9)))
        assertEquals(0, stuck.possibleMoves)
        assertTrue(stuck.isStuck)
        assertFalse(stuck.copy(stock = listOf(card(Suit.SPADES, 2, faceUp = false))).isStuck)
    }

    @Test
    fun `with the stock used up and the columns in order, the rest plays itself out`() {
        val columns = (0 until 2).flatMap { copy -> Suit.entries.map { suit -> (13 downTo 1).map { card(suit, it, copy = copy) } } }
        var game = FortyThievesGame(emptyList(), emptyList(), List(8) { emptyList() }, columns + List(2) { emptyList() }, 1)

        assertTrue(game.canAutoComplete)
        var steps = 0
        while (true) {
            game = game.autoCompleteStep() ?: break
            steps++
        }
        assertEquals(104, steps)
        assertTrue(game.isWon)
    }

    @Test
    fun `a game in progress is kept and restored whole`() {
        val played = FortyThievesGame.deal(2, Random(3)).draw()!!.draw()!!

        assertEquals(played, FortyThievesGame.fromJson(played.toJson()))
        assertEquals("DEAL2", played.variant)
        assertEquals(2, FortyThievesGame.passesOf(played.variant))
    }
}
