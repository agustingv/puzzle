package com.butaca13.puzzles.game.canfield

import com.butaca13.puzzles.game.cards.Card
import com.butaca13.puzzles.game.cards.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CanfieldGameTest {

    private fun card(suit: Suit, rank: Int, faceUp: Boolean = true) = Card.of(suit, rank, faceUp)

    private fun game(
        vararg columns: List<Card>,
        reserve: List<Card> = emptyList(),
        stock: List<Card> = emptyList(),
        waste: List<Card> = emptyList(),
        foundations: List<List<Card>> = List(4) { emptyList() },
        base: Int = 5,
    ) = CanfieldGame(stock, waste, reserve, foundations, columns.toList() + FILLERS.drop(columns.size), base, 1)

    /** Columns that only fill room, so that the reserve has none to fill: queens, nothing fits on them here. */
    private val FILLERS = listOf(Suit.CLUBS, Suit.DIAMONDS, Suit.SPADES, Suit.HEARTS).map { listOf(card(it, 12)) }

    @Test
    fun `a deal puts thirteen in the reserve, one at home setting the base, one in each column and 34 in the stock`() {
        val game = CanfieldGame.deal(3, Random(1))

        assertEquals(13, game.reserve.size)
        assertEquals(listOf(12), game.reserve.indices.filter { game.reserve[it].faceUp })
        assertEquals(1, game.foundations[0].size)
        assertEquals(game.foundations[0][0].rank, game.base)
        assertTrue(game.columns.all { it.size == 1 && it[0].faceUp })
        assertEquals(34, game.stock.size)
        assertEquals(52, (game.stock + game.reserve + game.foundations.flatten() + game.columns.flatten()).map { it.id }.toSet().size)
        assertEquals(CanfieldGame.deal(1, Random(2)), CanfieldGame.deal(1, Random(2)))
        assertNotEquals(CanfieldGame.deal(1, Random(2)), CanfieldGame.deal(1, Random(3)))
    }

    @Test
    fun `foundations start from the base and go round from king to ace`() {
        val game = game(foundations = listOf(listOf(card(Suit.HEARTS, 12), card(Suit.HEARTS, 13))) + List(3) { emptyList() }, base = 12)

        assertTrue(game.canPlace(listOf(card(Suit.HEARTS, 1)), Pile.Foundation(0)))
        assertFalse(game.canPlace(listOf(card(Suit.SPADES, 1)), Pile.Foundation(0)))
        assertTrue(game.canPlace(listOf(card(Suit.SPADES, 12)), Pile.Foundation(1)))
        assertFalse(game.canPlace(listOf(card(Suit.SPADES, 1)), Pile.Foundation(1)))
    }

    @Test
    fun `columns build down in alternating colours, round from ace to king`() {
        val game = game(listOf(card(Suit.SPADES, 1)), listOf(card(Suit.HEARTS, 8)))

        assertTrue(game.canPlace(listOf(card(Suit.HEARTS, 13)), Pile.Column(0)))
        assertFalse(game.canPlace(listOf(card(Suit.CLUBS, 13)), Pile.Column(0)))
        assertTrue(game.canPlace(listOf(card(Suit.CLUBS, 7)), Pile.Column(1)))
    }

    @Test
    fun `a column left empty is filled from the reserve at once, and the reserve's next card turns over`() {
        val reserve = listOf(card(Suit.CLUBS, 3, faceUp = false), card(Suit.DIAMONDS, 9))
        val game = game(listOf(card(Suit.HEARTS, 8)), listOf(card(Suit.SPADES, 9)), reserve = reserve)

        // Nothing goes on an empty column while there is a reserve.
        assertFalse(game.copy(columns = game.columns.toMutableList().also { it[2] = emptyList() }).canPlace(listOf(card(Suit.CLUBS, 2)), Pile.Column(2)))
        val moved = game.move(Pile.Column(0), 0, Pile.Column(1))!!
        assertEquals(listOf(card(Suit.DIAMONDS, 9)), moved.columns[0])
        assertEquals(listOf(card(Suit.CLUBS, 3)), moved.reserve)
    }

    @Test
    fun `with the reserve gone, any card goes on an empty column`() {
        val game = game(listOf(card(Suit.HEARTS, 8)), emptyList(), waste = listOf(card(Suit.CLUBS, 4)))

        assertTrue(game.canPlace(listOf(card(Suit.CLUBS, 4)), Pile.Column(1)))
    }

    @Test
    fun `the reserve's top card plays, and a tap sends it home first`() {
        val game = game(listOf(card(Suit.HEARTS, 8)), reserve = listOf(card(Suit.CLUBS, 2, false), card(Suit.SPADES, 5)), base = 5)

        assertEquals(Pile.Foundation(0), game.autoTarget(Pile.Reserve, 1))
        val next = game.move(Pile.Reserve, 1, Pile.Foundation(0))!!
        assertTrue(next.reserve.last().faceUp)
    }

    @Test
    fun `the stock goes round again and again`() {
        var game = game(stock = listOf(card(Suit.SPADES, 9, faceUp = false)))

        game = game.draw()!!
        assertEquals(1, game.waste.size)
        game = game.draw()!!
        assertEquals(1, game.stock.size)
        assertNotNull(game.draw())
    }

    @Test
    fun `possible moves, and a game with none to make or find is stuck`() {
        val game = game(listOf(card(Suit.HEARTS, 8)), listOf(card(Suit.SPADES, 9)), base = 5)
        assertEquals(1, game.possibleMoves)

        val stuck = game(listOf(card(Suit.HEARTS, 8)), listOf(card(Suit.HEARTS, 2)), reserve = listOf(card(Suit.CLUBS, 3)), base = 5)
        assertEquals(0, stuck.possibleMoves)
        assertTrue(stuck.isStuck)
        assertFalse(stuck.copy(stock = listOf(card(Suit.CLUBS, 5, faceUp = false))).isStuck)
    }

    @Test
    fun `with only the columns left, the rest plays itself out from the base up`() {
        val base = 5
        val columns = Suit.entries.map { suit -> (12 downTo 0).map { card(suit, (base - 1 + it) % 13 + 1) } }
        var game = CanfieldGame(emptyList(), emptyList(), emptyList(), List(4) { emptyList() }, columns, base, 3)

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
        val played = CanfieldGame.deal(3, Random(4)).draw()!!

        assertEquals(played, CanfieldGame.fromJson(played.toJson()))
        assertEquals("DRAW3", played.variant)
    }

    private fun assertNotNull(value: Any?) = assertTrue(value != null)
}
