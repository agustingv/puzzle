package com.butaca13.puzzles.game.spider

import com.butaca13.puzzles.game.cards.Card
import com.butaca13.puzzles.game.cards.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SpiderGameTest {

    private fun card(suit: Suit, rank: Int, faceUp: Boolean = true, copy: Int = 0) = Card.of(suit, rank, faceUp, copy)

    /** A column that only fills room: a face-down card under an ace, which nothing goes on. */
    private fun filler(i: Int) = listOf(card(Suit.CLUBS, 5, faceUp = false, copy = i), card(Suit.DIAMONDS, 1, copy = i))

    private fun game(vararg columns: List<Card>, stock: List<Card> = emptyList(), done: List<List<Card>> = emptyList()) =
        SpiderGame(stock, columns.toList() + List(10 - columns.size) { filler(it + columns.size) }, done, 4)

    @Test
    fun `a deal puts 54 cards in ten columns and 50 in the stock, all told apart`() {
        for (suits in listOf(1, 2, 4)) {
            val game = SpiderGame.deal(suits, Random(suits))
            val all = game.stock + game.columns.flatten()

            assertEquals(listOf(6, 6, 6, 6, 5, 5, 5, 5, 5, 5), game.columns.map { it.size })
            assertEquals(50, game.stock.size)
            assertEquals(104, all.map { it.id }.toSet().size)
            assertEquals(suits, all.map { it.suit }.toSet().size)
            Suit.entries.forEach { suit -> all.count { it.suit == suit }.let { assertTrue(it == 0 || it == 104 / suits) } }
            for (column in game.columns) column.forEachIndexed { i, c -> assertEquals(i == column.lastIndex, c.faceUp) }
        }
        assertEquals(SpiderGame.deal(2, Random(5)), SpiderGame.deal(2, Random(5)))
        assertNotEquals(SpiderGame.deal(2, Random(5)), SpiderGame.deal(2, Random(6)))
    }

    @Test
    fun `any card goes on one a rank higher, but only runs of one suit move together`() {
        val game = game(
            listOf(card(Suit.SPADES, 9), card(Suit.HEARTS, 8), card(Suit.HEARTS, 7)),
            listOf(card(Suit.CLUBS, 9)),
        )

        assertTrue(game.canPickUp(Heap.Column(0), 1))
        assertFalse(game.canPickUp(Heap.Column(0), 0))
        val moved = game.move(Heap.Column(0), 1, Heap.Column(1))!!
        assertEquals(listOf(9, 8, 7), moved.columns[1].map { it.rank })
        assertNull(game.move(Heap.Column(0), 2, Heap.Column(1)))
    }

    @Test
    fun `a whole run of a suit leaves the table and the card under it turns over`() {
        val run = (13 downTo 2).map { card(Suit.HEARTS, it) }
        val game = game(
            listOf(card(Suit.CLUBS, 4, faceUp = false)) + run,
            listOf(card(Suit.SPADES, 3), card(Suit.HEARTS, 1)),
        )

        val next = game.move(Heap.Column(1), 1, Heap.Column(0))!!

        assertEquals(1, next.done.size)
        assertEquals(13, next.done[0].first().rank)
        assertEquals(listOf(card(Suit.CLUBS, 4)), next.columns[0])
        assertEquals(listOf(card(Suit.SPADES, 3)), next.columns[1])
    }

    @Test
    fun `the stock deals one card face up to every column, but not while one is empty`() {
        val stock = (1..10).map { card(Suit.SPADES, it, faceUp = false, copy = 7) }
        val game = game(stock = stock)

        val dealt = game.deal()!!
        assertTrue(dealt.stock.isEmpty())
        assertTrue(dealt.columns.all { it.size == 3 && it.last().faceUp })
        assertEquals(1, dealt.moves)
        assertNull(dealt.deal())

        val withEmpty = game.copy(columns = game.columns.toMutableList().also { it[3] = emptyList() })
        assertFalse(withEmpty.canDeal)
        assertNull(withEmpty.deal())
    }

    @Test
    fun `a tap prefers a card of the same suit`() {
        val game = game(
            listOf(card(Suit.HEARTS, 6)),
            listOf(card(Suit.SPADES, 7)),
            listOf(card(Suit.HEARTS, 7)),
        )

        assertEquals(Heap.Column(2), game.autoTarget(Heap.Column(0), 0))
    }

    @Test
    fun `possible moves leave out those that gain nothing`() {
        val game = game(
            listOf(card(Suit.CLUBS, 7), card(Suit.HEARTS, 6)),
            listOf(card(Suit.SPADES, 7)),
            listOf(card(Suit.HEARTS, 7)),
        )

        // The 6 already sits on a 7: only to the 7 of its own suit is worth it.
        assertEquals(1, game.possibleMoves)
        assertFalse(game.isStuck)

        val stuck = game(listOf(card(Suit.HEARTS, 6)), listOf(card(Suit.SPADES, 9)))
        assertEquals(0, stuck.possibleMoves)
        assertTrue(stuck.isStuck)
        assertFalse(stuck.copy(stock = List(10) { card(Suit.SPADES, it + 1, faceUp = false, copy = 7) }).isStuck)
    }

    @Test
    fun `eight runs done win`() {
        val runs = List(8) { copy -> (13 downTo 1).map { card(Suit.SPADES, it, copy = copy) } }

        assertTrue(SpiderGame(emptyList(), List(10) { emptyList() }, runs, 1).isWon)
        assertFalse(SpiderGame(emptyList(), List(10) { emptyList() }, runs.drop(1), 1).isWon)
    }

    @Test
    fun `a game in progress is kept and restored whole`() {
        val played = SpiderGame.deal(2, Random(3)).deal()!!

        assertEquals(played, SpiderGame.fromJson(played.toJson()))
        assertEquals("SUITS2", played.variant)
        assertEquals(2, SpiderGame.suitsOf(played.variant))
    }
}
