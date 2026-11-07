package com.butaca13.puzzles.game.freecell

import com.butaca13.puzzles.game.cards.Card
import com.butaca13.puzzles.game.cards.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FreeCellGameTest {

    private fun card(suit: Suit, rank: Int) = Card.of(suit, rank)

    /** A column that only fills room: nothing on it moves, nothing goes on it. */
    private val filler = listOf(card(Suit.CLUBS, 13), card(Suit.SPADES, 13))

    private fun game(
        vararg columns: List<Card>,
        cells: List<Card?> = List(4) { null },
        foundations: List<List<Card>> = List(4) { emptyList() },
    ) = FreeCellGame(cells, foundations, columns.toList() + List(8 - columns.size) { emptyList() })

    @Test
    fun `a deal puts every card face up in eight columns, seven in the first four and six in the rest`() {
        val game = FreeCellGame.deal(4, Random(1))

        assertEquals(listOf(7, 7, 7, 7, 6, 6, 6, 6), game.columns.map { it.size })
        assertEquals(52, game.columns.flatten().map { it.id }.toSet().size)
        assertTrue(game.columns.flatten().all { it.faceUp })
        assertEquals(4, game.cells.size)
        assertEquals(2, FreeCellGame.deal(2, Random(1)).cells.size)
        assertEquals(FreeCellGame.deal(4, Random(3)), FreeCellGame.deal(4, Random(3)))
        assertNotEquals(FreeCellGame.deal(4, Random(3)), FreeCellGame.deal(4, Random(4)))
    }

    @Test
    fun `a cell holds one card, and any card goes on an empty column`() {
        val game = game(listOf(card(Suit.HEARTS, 5)), cells = listOf(card(Suit.SPADES, 9), null, null, null))

        assertFalse(game.canPlace(listOf(card(Suit.CLUBS, 2)), Place.Cell(0)))
        assertTrue(game.canPlace(listOf(card(Suit.CLUBS, 2)), Place.Cell(1)))
        assertTrue(game.canPlace(listOf(card(Suit.CLUBS, 2)), Place.Column(1)))
        assertTrue(game.canPlace(listOf(card(Suit.CLUBS, 4)), Place.Column(0)))
        assertFalse(game.canPlace(listOf(card(Suit.DIAMONDS, 4)), Place.Column(0)))
    }

    @Test
    fun `how many cards move together depends on free cells and empty columns`() {
        // Five cells' worth: no free cell and every column full but one.
        val full = List(4) { card(Suit.CLUBS, 1 + it) }
        val run = listOf(card(Suit.SPADES, 9), card(Suit.HEARTS, 8), card(Suit.CLUBS, 7))
        val game = game(run, listOf(card(Suit.DIAMONDS, 10)), *Array(5) { filler }, cells = full)

        // No cell and one empty column: two cards to a column with cards, one to the empty one.
        assertEquals(2, game.maxRun(toEmptyColumn = false))
        assertEquals(1, game.maxRun(toEmptyColumn = true))
        assertFalse(game.canPickUp(Place.Column(0), 0))
        assertTrue(game.canPickUp(Place.Column(0), 1))

        val roomy = game.copy(cells = listOf(null, null, full[2], full[3]))
        // Two cells and an empty column: (2 + 1) × 2 = 6.
        assertEquals(6, roomy.maxRun(toEmptyColumn = false))
        val moved = roomy.move(Place.Column(0), 0, Place.Column(1))!!
        assertEquals(listOf(10, 9, 8, 7), moved.columns[1].map { it.rank })
        assertEquals(1, moved.moves)
    }

    @Test
    fun `a tap goes home first, then to a column, then to a free cell`() {
        val ace = game(listOf(card(Suit.HEARTS, 1)))
        assertEquals(Place.Foundation(0), ace.autoTarget(Place.Column(0), 0))

        val seven = game(listOf(card(Suit.HEARTS, 7)), listOf(card(Suit.SPADES, 8)), *Array(6) { filler })
        assertEquals(Place.Column(1), seven.autoTarget(Place.Column(0), 0))

        val stuck = game(listOf(card(Suit.HEARTS, 7)), *Array(7) { filler })
        assertEquals(Place.Cell(0), stuck.autoTarget(Place.Column(0), 0))
        // From a cell, never to another cell.
        val inCell = game(*Array(8) { filler }, cells = listOf(card(Suit.HEARTS, 7), null, null, null))
        assertNull(inCell.autoTarget(Place.Cell(0), 0))
    }

    @Test
    fun `a card let go on a cell is moved there and back down`() {
        val game = game(listOf(card(Suit.HEARTS, 7)), listOf(card(Suit.SPADES, 8)), *Array(6) { filler })

        val parked = game.move(Place.Column(0), 0, Place.Cell(2))!!
        assertEquals(card(Suit.HEARTS, 7), parked.cells[2])
        assertTrue(parked.columns[0].isEmpty())
        val back = parked.move(Place.Cell(2), 0, Place.Column(1))!!
        assertNull(back.cells[2])
        assertEquals(listOf(8, 7), back.columns[1].map { it.rank })
    }

    @Test
    fun `possible moves leave out free cells and moves that gain nothing`() {
        val game = game(
            listOf(card(Suit.CLUBS, 8), card(Suit.HEARTS, 7)),
            listOf(card(Suit.SPADES, 8)),
            listOf(card(Suit.DIAMONDS, 1)),
            *Array(5) { filler },
        )

        // Only the ace home: the 7 already sits on a black 8.
        assertEquals(1, game.possibleMoves)
        assertFalse(game.isStuck)

        val stuck = game(*Array(8) { filler }, cells = List(4) { card(Suit.HEARTS, 2 + it) })
        assertEquals(0, stuck.possibleMoves)
        assertFalse(stuck.hasFreeCell)
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
        val dealt = FreeCellGame.deal(2, Random(9))
        val played = dealt.move(Place.Column(0), dealt.columns[0].lastIndex, Place.Cell(1))!!

        assertEquals(played, FreeCellGame.fromJson(played.toJson()))
        assertEquals("CELLS2", played.variant)
        assertEquals(2, FreeCellGame.cellCountOf(played.variant))
    }
}
