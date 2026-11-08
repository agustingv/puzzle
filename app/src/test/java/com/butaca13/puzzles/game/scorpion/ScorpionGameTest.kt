package com.butaca13.puzzles.game.scorpion

import com.butaca13.puzzles.game.cards.Card
import com.butaca13.puzzles.game.cards.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ScorpionGameTest {

    private fun card(suit: Suit, rank: Int, faceUp: Boolean = true) = Card.of(suit, rank, faceUp)

    private fun game(vararg columns: List<Card>, reserve: List<Card> = emptyList(), anyOnEmpty: Boolean = false) =
        ScorpionGame(reserve, columns.toList() + List(7 - columns.size) { emptyList() }, emptyList(), anyOnEmpty)

    @Test
    fun `a deal puts seven cards in each column, three face down in the first three, and three in reserve`() {
        val game = ScorpionGame.deal(false, Random(1))

        assertTrue(game.columns.all { it.size == 7 })
        assertEquals(listOf(4, 4, 4, 7, 7, 7, 7), game.columns.map { column -> column.count { it.faceUp } })
        assertEquals(3, game.reserve.size)
        assertEquals(52, (game.reserve + game.columns.flatten()).map { it.id }.toSet().size)
        assertEquals(ScorpionGame.deal(true, Random(2)), ScorpionGame.deal(true, Random(2)))
        assertNotEquals(ScorpionGame.deal(true, Random(2)), ScorpionGame.deal(true, Random(3)))
    }

    @Test
    fun `any face-up card moves with what is on it onto one of its suit a rank higher`() {
        val game = game(
            listOf(card(Suit.CLUBS, 2, faceUp = false), card(Suit.HEARTS, 7), card(Suit.SPADES, 3)),
            listOf(card(Suit.HEARTS, 8)),
            listOf(card(Suit.DIAMONDS, 8)),
        )

        assertTrue(game.canPlace(listOf(card(Suit.HEARTS, 7)), Heap.Column(1)))
        assertFalse(game.canPlace(listOf(card(Suit.HEARTS, 7)), Heap.Column(2)))
        val moved = game.move(Heap.Column(0), 1, Heap.Column(1))!!
        assertEquals(listOf(8, 7, 3), moved.columns[1].map { it.rank })
        assertEquals(listOf(card(Suit.CLUBS, 2)), moved.columns[0])
    }

    @Test
    fun `an empty column takes only a king, or any card in the Wasp`() {
        val game = game(listOf(card(Suit.HEARTS, 5), card(Suit.SPADES, 9)))

        assertFalse(game.canPlace(listOf(card(Suit.SPADES, 9)), Heap.Column(1)))
        assertTrue(game.canPlace(listOf(card(Suit.SPADES, 13)), Heap.Column(1)))
        val wasp = game.copy(anyOnEmpty = true)
        assertTrue(wasp.canPlace(listOf(card(Suit.SPADES, 9)), Heap.Column(1)))
        assertEquals("WASP", wasp.variant)
        assertTrue(ScorpionGame.anyOnEmptyOf(wasp.variant))
    }

    @Test
    fun `a whole run of a suit leaves the table, and four of them win`() {
        val run = (13 downTo 2).map { card(Suit.HEARTS, it) }
        val game = game(listOf(card(Suit.CLUBS, 4, faceUp = false)) + run, listOf(card(Suit.SPADES, 6), card(Suit.HEARTS, 1)))

        val next = game.move(Heap.Column(1), 1, Heap.Column(0))!!
        assertEquals(1, next.done.size)
        assertEquals(listOf(card(Suit.CLUBS, 4)), next.columns[0])
        assertFalse(next.isWon)
        assertTrue(next.copy(done = List(4) { next.done[0] }).isWon)
    }

    @Test
    fun `the reserve is dealt once, a card face up onto each of the first three columns`() {
        val reserve = listOf(card(Suit.CLUBS, 5, false), card(Suit.CLUBS, 6, false), card(Suit.CLUBS, 7, false))
        val game = game(listOf(card(Suit.HEARTS, 2)), listOf(card(Suit.HEARTS, 3)), listOf(card(Suit.HEARTS, 4)), reserve = reserve)

        val dealt = game.deal()!!
        assertTrue(dealt.reserve.isEmpty())
        assertEquals(listOf(7, 6, 5), dealt.columns.take(3).map { it.last().rank })
        assertTrue(dealt.columns.take(3).all { it.last().faceUp })
        assertNull(dealt.deal())
    }

    @Test
    fun `a tap goes onto a card of its suit, and a king alone stays`() {
        val game = game(listOf(card(Suit.CLUBS, 5), card(Suit.HEARTS, 7)), listOf(card(Suit.HEARTS, 8)))

        assertEquals(Heap.Column(1), game.autoTarget(Heap.Column(0), 1))
        assertNull(game(listOf(card(Suit.HEARTS, 13))).autoTarget(Heap.Column(0), 0))
    }

    @Test
    fun `possible moves leave out a card already on its own, and with none and no reserve the game is stuck`() {
        val game = game(
            listOf(card(Suit.HEARTS, 8), card(Suit.HEARTS, 7)),
            listOf(card(Suit.SPADES, 4)),
            listOf(card(Suit.SPADES, 3)),
        )
        // Only the 3 onto the 4: the 7 already sits on the 8 of hearts.
        assertEquals(1, game.possibleMoves)

        val stuck = game(listOf(card(Suit.HEARTS, 5)), listOf(card(Suit.SPADES, 9)))
        assertEquals(0, stuck.possibleMoves)
        assertTrue(stuck.isStuck)
        assertFalse(stuck.copy(reserve = listOf(card(Suit.CLUBS, 2, false))).isStuck)
    }

    @Test
    fun `a game in progress is kept and restored whole`() {
        val played = ScorpionGame.deal(true, Random(4)).deal()!!

        assertEquals(played, ScorpionGame.fromJson(played.toJson()))
    }
}
