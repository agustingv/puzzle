package com.butaca13.puzzles.game.yukon

import com.butaca13.puzzles.game.cards.Card
import com.butaca13.puzzles.game.cards.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class YukonGameTest {

    private fun card(suit: Suit, rank: Int, faceUp: Boolean = true) = Card.of(suit, rank, faceUp)

    private fun game(vararg columns: List<Card>, sameSuit: Boolean = false, foundations: List<List<Card>> = List(4) { emptyList() }) =
        YukonGame(foundations, columns.toList() + List(7 - columns.size) { emptyList() }, sameSuit)

    @Test
    fun `a deal puts all 52 cards in the columns, five face up on all but the first`() {
        val game = YukonGame.deal(false, Random(1))

        assertEquals(listOf(1, 6, 7, 8, 9, 10, 11), game.columns.map { it.size })
        assertEquals(listOf(1, 5, 5, 5, 5, 5, 5), game.columns.map { column -> column.count { it.faceUp } })
        assertTrue(game.columns.all { column -> column.dropWhile { !it.faceUp }.all { it.faceUp } })
        assertEquals(52, game.columns.flatten().map { it.id }.toSet().size)
        assertEquals(YukonGame.deal(true, Random(2)), YukonGame.deal(true, Random(2)))
        assertNotEquals(YukonGame.deal(true, Random(2)), YukonGame.deal(true, Random(3)))
    }

    @Test
    fun `any face-up card moves with everything on top of it, in order or not`() {
        val game = game(
            listOf(card(Suit.CLUBS, 4, faceUp = false), card(Suit.HEARTS, 7), card(Suit.SPADES, 2), card(Suit.DIAMONDS, 11)),
            listOf(card(Suit.SPADES, 8)),
        )

        assertTrue(game.canPickUp(Pile.Column(0), 1))
        assertFalse(game.canPickUp(Pile.Column(0), 0))
        val moved = game.move(Pile.Column(0), 1, Pile.Column(1))!!
        assertEquals(listOf(8, 7, 2, 11), moved.columns[1].map { it.rank })
        // The card left on top turns over.
        assertEquals(listOf(card(Suit.CLUBS, 4)), moved.columns[0])
        assertEquals(1, moved.moves)
    }

    @Test
    fun `the classic game builds by colour, the Russian by suit`() {
        val classic = game(listOf(card(Suit.HEARTS, 7)), listOf(card(Suit.SPADES, 8)), listOf(card(Suit.HEARTS, 8)))
        assertTrue(classic.canPlace(listOf(card(Suit.HEARTS, 7)), Pile.Column(1)))
        assertFalse(classic.canPlace(listOf(card(Suit.HEARTS, 7)), Pile.Column(2)))

        val russian = classic.copy(sameSuit = true)
        assertFalse(russian.canPlace(listOf(card(Suit.HEARTS, 7)), Pile.Column(1)))
        assertTrue(russian.canPlace(listOf(card(Suit.HEARTS, 7)), Pile.Column(2)))
        assertEquals("RUSSIAN", russian.variant)
        assertTrue(YukonGame.sameSuitOf(russian.variant))
    }

    @Test
    fun `only a king, with whatever is on it, goes to an empty column`() {
        val game = game(listOf(card(Suit.CLUBS, 5), card(Suit.HEARTS, 13), card(Suit.CLUBS, 2)), listOf(card(Suit.SPADES, 9)))

        assertTrue(game.canPlace(listOf(card(Suit.HEARTS, 13), card(Suit.CLUBS, 2)), Pile.Column(2)))
        assertFalse(game.canPlace(listOf(card(Suit.SPADES, 9)), Pile.Column(2)))
    }

    @Test
    fun `a tap goes home first, then to a column`() {
        val ace = game(listOf(card(Suit.HEARTS, 1)))
        assertEquals(Pile.Foundation(0), ace.autoTarget(Pile.Column(0), 0))

        val seven = game(listOf(card(Suit.CLUBS, 5), card(Suit.HEARTS, 7)), listOf(card(Suit.SPADES, 8)))
        assertEquals(Pile.Column(1), seven.autoTarget(Pile.Column(0), 1))
        // A king alone in its column stays.
        assertNull(game(listOf(card(Suit.HEARTS, 13))).autoTarget(Pile.Column(0), 0))
    }

    @Test
    fun `possible moves leave out those that gain nothing, and with none the game is stuck`() {
        val game = game(
            listOf(card(Suit.CLUBS, 8), card(Suit.HEARTS, 7)),
            listOf(card(Suit.SPADES, 8)),
            listOf(card(Suit.DIAMONDS, 1)),
        )
        // Only the ace home: the 7 already sits on a black 8.
        assertEquals(1, game.possibleMoves)
        assertFalse(game.isStuck)

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
        assertFalse(game(listOf(card(Suit.HEARTS, 3), card(Suit.CLUBS, 5))).canAutoComplete)
    }

    @Test
    fun `a game in progress is kept and restored whole`() {
        val dealt = YukonGame.deal(true, Random(4))
        val played = dealt.move(Pile.Column(6), dealt.columns[6].lastIndex, Pile.Column(0)) ?: dealt

        assertEquals(played, YukonGame.fromJson(played.toJson()))
    }
}
