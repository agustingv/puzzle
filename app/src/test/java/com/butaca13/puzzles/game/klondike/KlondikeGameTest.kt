package com.butaca13.puzzles.game.klondike

import com.butaca13.puzzles.game.cards.Card
import com.butaca13.puzzles.game.cards.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Ported from the Tauri Klondike's tests (projects/tauri/Klondike/src/game.test.ts), less the score. */
class KlondikeGameTest {

    private fun card(suit: Suit, rank: Int, faceUp: Boolean = true) = Card.of(suit, rank, faceUp)

    private fun empty(
        stock: List<Card> = emptyList(),
        waste: List<Card> = emptyList(),
        foundations: List<List<Card>> = List(4) { emptyList() },
        tableau: List<List<Card>> = List(7) { emptyList() },
        drawCount: Int = 1,
    ) = KlondikeGame(stock, waste, foundations, tableau, drawCount)

    private fun columns(vararg first: List<Card>) = first.toList() + List(7 - first.size) { emptyList() }

    @Test
    fun `a deal puts 28 cards in the columns and 24 in the stock, only the top ones face up`() {
        val game = KlondikeGame.deal(1, Random(42))

        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), game.tableau.map { it.size })
        assertEquals(24, game.stock.size)
        assertEquals(52, (game.stock + game.tableau.flatten()).map { it.id }.toSet().size)
        for (column in game.tableau) column.forEachIndexed { i, c -> assertEquals(i == column.lastIndex, c.faceUp) }
        assertTrue(game.stock.none { it.faceUp })
    }

    @Test
    fun `a deal comes again from its seed`() {
        assertEquals(KlondikeGame.deal(3, Random(7)), KlondikeGame.deal(3, Random(7)))
        assertNotEquals(KlondikeGame.deal(3, Random(7)), KlondikeGame.deal(3, Random(8)))
    }

    @Test
    fun `foundations build up by suit from the ace`() {
        val game = empty(foundations = listOf(listOf(card(Suit.HEARTS, 1)), emptyList(), emptyList(), emptyList()))

        assertTrue(game.canPlace(listOf(card(Suit.HEARTS, 2)), Pile.Foundation(0)))
        assertFalse(game.canPlace(listOf(card(Suit.DIAMONDS, 2)), Pile.Foundation(0)))
        assertTrue(game.canPlace(listOf(card(Suit.SPADES, 1)), Pile.Foundation(1)))
        assertFalse(game.canPlace(listOf(card(Suit.SPADES, 2)), Pile.Foundation(1)))
    }

    @Test
    fun `columns build down in alternating colours, kings on empty ones`() {
        val game = empty(tableau = columns(listOf(card(Suit.SPADES, 8))))

        assertTrue(game.canPlace(listOf(card(Suit.HEARTS, 7)), Pile.Tableau(0)))
        assertFalse(game.canPlace(listOf(card(Suit.CLUBS, 7)), Pile.Tableau(0)))
        assertFalse(game.canPlace(listOf(card(Suit.HEARTS, 6)), Pile.Tableau(0)))
        assertFalse(game.canPlace(listOf(card(Suit.HEARTS, 12)), Pile.Tableau(1)))
        assertTrue(game.canPlace(listOf(card(Suit.HEARTS, 13)), Pile.Tableau(1)))
    }

    @Test
    fun `a run moves together and the card it leaves turns over`() {
        val game = empty(
            tableau = columns(
                listOf(card(Suit.CLUBS, 2, faceUp = false), card(Suit.SPADES, 9), card(Suit.HEARTS, 8)),
                listOf(card(Suit.DIAMONDS, 10)),
            ),
        )

        val next = game.move(Pile.Tableau(0), 1, Pile.Tableau(1))!!

        assertEquals(listOf(10, 9, 8), next.tableau[1].map { it.rank })
        assertTrue(next.tableau[0][0].faceUp)
        assertEquals(1, next.moves)
        assertEquals(3, game.tableau[0].size)
    }

    @Test
    fun `illegal moves are refused`() {
        val game = empty(waste = listOf(card(Suit.HEARTS, 5)), tableau = columns(listOf(card(Suit.DIAMONDS, 6))))

        assertNull(game.move(Pile.Waste, 0, Pile.Tableau(0)))
        // Face-down cards and broken runs can not be picked up.
        val hidden = empty(tableau = columns(listOf(card(Suit.CLUBS, 5, faceUp = false), card(Suit.HEARTS, 4))))
        assertFalse(hidden.canPickUp(Pile.Tableau(0), 0))
        assertNull(hidden.move(Pile.Tableau(0), 0, Pile.Tableau(1)))
    }

    @Test
    fun `three at a time are drawn, and the waste goes back over`() {
        var game = empty(
            drawCount = 3,
            stock = (1..4).map { card(Suit.SPADES, it, faceUp = false) },
        )

        game = game.draw()!!
        assertEquals(listOf(4, 3, 2), game.waste.map { it.rank })
        assertTrue(game.waste.all { it.faceUp })
        game = game.draw()!!
        assertTrue(game.stock.isEmpty())
        assertEquals(listOf(4, 3, 2, 1), game.waste.map { it.rank })
        game = game.draw()!!
        assertTrue(game.waste.isEmpty())
        assertEquals(listOf(1, 2, 3, 4), game.stock.map { it.rank })
        assertTrue(game.stock.none { it.faceUp })
        assertNull(empty().draw())
    }

    @Test
    fun `a tap prefers a foundation, then a column with cards`() {
        val game = empty(waste = listOf(card(Suit.HEARTS, 1)), tableau = columns(emptyList(), listOf(card(Suit.SPADES, 2))))
        assertEquals(Pile.Foundation(0), game.autoTarget(Pile.Waste, 0))

        val queen = empty(waste = listOf(card(Suit.HEARTS, 12)), tableau = columns(emptyList(), listOf(card(Suit.SPADES, 13))))
        assertEquals(Pile.Tableau(1), queen.autoTarget(Pile.Waste, 0))
    }

    @Test
    fun `a king at the bottom of its column is not shuffled to another empty one`() {
        val game = empty(tableau = columns(listOf(card(Suit.SPADES, 13))))

        assertNull(game.autoTarget(Pile.Tableau(0), 0))
    }

    @Test
    fun `a game with everything face up plays itself out to a win`() {
        val tableau = Suit.entries.map { suit -> (13 downTo 1).map { card(suit, it) } }
        var game = empty(tableau = columns(*tableau.toTypedArray()))

        assertTrue(game.canAutoComplete)
        var steps = 0
        while (true) {
            game = game.autoCompleteStep() ?: break
            steps++
        }

        assertEquals(52, steps)
        assertTrue(game.isWon)
        assertFalse(game.canAutoComplete)
    }

    @Test
    fun `with three a draw it plays out only once the stock is used up`() {
        val game = empty(drawCount = 3, stock = listOf(card(Suit.SPADES, 1, faceUp = false)))

        assertFalse(game.canAutoComplete)
        assertTrue(game.copy(drawCount = 1).canAutoComplete)
    }

    @Test
    fun `a game in progress is kept and restored whole`() {
        val played = KlondikeGame.deal(3, Random(5)).draw()!!.draw()!!

        assertEquals(played, KlondikeGame.fromJson(played.toJson()))
        assertEquals("DRAW3", played.variant)
        assertEquals(3, KlondikeGame.drawCountOf(played.variant))
    }

    @Test
    fun `possible moves count each useful move once`() {
        val game = empty(
            waste = listOf(card(Suit.HEARTS, 1)),
            tableau = columns(
                listOf(card(Suit.CLUBS, 3, faceUp = false), card(Suit.SPADES, 8)),
                listOf(card(Suit.HEARTS, 7)),
                listOf(card(Suit.CLUBS, 8)),
                listOf(card(Suit.SPADES, 13)),
            ),
        )

        // The ace home, the 7 onto either black 8, and nothing for the king already alone in its column.
        assertEquals(3, game.possibleMoves)
    }

    @Test
    fun `a run that would only move between equal places is not counted`() {
        val game = empty(
            tableau = columns(
                listOf(card(Suit.CLUBS, 8), card(Suit.HEARTS, 7)),
                listOf(card(Suit.SPADES, 8)),
            ),
        )

        assertEquals(0, game.possibleMoves)
        assertTrue(game.isStuck)
    }

    @Test
    fun `with nothing to play the stock is gone through before saying it is stuck`() {
        val tableau = columns(listOf(card(Suit.SPADES, 8)))
        val helps = empty(stock = listOf(card(Suit.HEARTS, 7, faceUp = false), card(Suit.CLUBS, 2, faceUp = false)), tableau = tableau)
        val useless = empty(stock = listOf(card(Suit.HEARTS, 3, faceUp = false)), waste = listOf(card(Suit.CLUBS, 2)), tableau = tableau)

        assertEquals(0, helps.possibleMoves)
        assertTrue(helps.drawingHelps)
        assertFalse(helps.isStuck)
        assertTrue(useless.isStuck)
    }
}
