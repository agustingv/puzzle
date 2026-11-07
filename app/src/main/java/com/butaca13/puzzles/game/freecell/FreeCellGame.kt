package com.butaca13.puzzles.game.freecell

import com.butaca13.puzzles.game.cards.Card
import com.butaca13.puzzles.game.cards.stacksOn
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/** Where cards lie: the free cells, the four foundations and the eight columns. */
sealed interface Place {
    data class Cell(val index: Int) : Place
    data class Foundation(val index: Int) : Place
    data class Column(val index: Int) : Place
}

/**
 * FreeCell: every card face up from the start, in eight columns, with free cells that hold one card
 * each. Columns build down in alternating colours, and any card goes on an empty one; the foundations
 * build up by suit from the ace. Each action gives a new game, or null when it is not allowed, so that
 * earlier ones can be kept to undo.
 */
data class FreeCellGame(
    /** The free cells, four or two, null where empty. */
    val cells: List<Card?>,
    val foundations: List<List<Card>>,
    /** Every column bottom first, so the last card is on top. */
    val columns: List<List<Card>>,
    val moves: Int = 0,
) {
    val variant: String get() = variantOf(cells.size)

    val isWon: Boolean get() = foundations.all { it.size == 13 }

    fun cards(place: Place): List<Card> = when (place) {
        is Place.Cell -> listOfNotNull(cells[place.index])
        is Place.Foundation -> foundations[place.index]
        is Place.Column -> columns[place.index]
    }

    private val freeCells: Int get() = cells.count { it == null }

    /**
     * How many cards can move together: one, and one more for each free cell, doubled for each empty
     * column they can pass through on the way, which an empty column they go to is not. It is what
     * moving them one at a time through those would do.
     */
    fun maxRun(toEmptyColumn: Boolean): Int {
        val empty = columns.count { it.isEmpty() } - if (toEmptyColumn) 1 else 0
        return (freeCells + 1) shl empty.coerceAtLeast(0)
    }

    /** Whether the cards from [index] to the top of [from] can be picked up together. */
    fun canPickUp(from: Place, index: Int): Boolean {
        val cards = cards(from)
        if (index !in cards.indices) return false
        return when (from) {
            is Place.Cell, is Place.Foundation -> index == cards.lastIndex
            is Place.Column -> (index + 1..cards.lastIndex).all { cards[it].stacksOn(cards[it - 1]) } &&
                cards.size - index <= maxRun(toEmptyColumn = false)
        }
    }

    fun canPlace(cards: List<Card>, to: Place): Boolean {
        val first = cards.firstOrNull() ?: return false
        val top = cards(to).lastOrNull()
        return when (to) {
            is Place.Cell -> cards.size == 1 && top == null
            is Place.Foundation -> cards.size == 1 &&
                if (top == null) first.rank == 1 else first.suit == top.suit && first.rank == top.rank + 1
            is Place.Column -> if (top == null) cards.size <= maxRun(toEmptyColumn = true) else first.stacksOn(top) && cards.size <= maxRun(toEmptyColumn = false)
        }
    }

    /** Moves the cards from [index] up in [from] onto [to]: one move however many go. */
    fun move(from: Place, index: Int, to: Place): FreeCellGame? {
        if (from == to || !canPickUp(from, index)) return null
        val cards = cards(from).drop(index)
        if (!canPlace(cards, to)) return null
        return with(from, cards(from).take(index)).with(to, cards(to) + cards).copy(moves = moves + 1)
    }

    /**
     * Where a tap sends the cards from [index] up: a foundation first, then a column with cards, then an
     * empty column, and a single card last of all to a free cell.
     */
    fun autoTarget(from: Place, index: Int): Place? {
        if (!canPickUp(from, index)) return null
        val cards = cards(from).drop(index)
        if (from !is Place.Foundation) {
            foundationFor(cards)?.let { return it }
        }
        val columns = columns.indices.map { Place.Column(it) }.filter { it != from && canPlace(cards, it) }
        columns.firstOrNull { this.columns[it.index].isNotEmpty() }?.let { return it }
        // A whole column gains nothing in another empty one.
        if (!(from is Place.Column && index == 0)) columns.firstOrNull()?.let { return it }
        if (from !is Place.Cell) cells.indices.map { Place.Cell(it) }.firstOrNull { canPlace(cards, it) }?.let { return it }
        return null
    }

    private fun foundationFor(cards: List<Card>): Place? =
        (0 until 4).map { Place.Foundation(it) }.firstOrNull { canPlace(cards, it) }

    /**
     * How many moves there are now that go somewhere useful, counted as in the patience: once per column
     * a card or run fits on, once for any foundation and once for any empty column. Moves to a free cell
     * are left out, as one is nearly always there to take; so are a whole column to an empty one, a run
     * that leaves on top the card it already sat on, unless that card can then go home, and cards taken
     * back off the foundations.
     */
    val possibleMoves: Int
        get() {
            var count = 0
            fun countFrom(from: Place, index: Int) {
                if (!canPickUp(from, index)) return
                val cards = cards(from).drop(index)
                if (foundationFor(cards) != null) count++
                if (from is Place.Column && index > 0) {
                    val below = columns[from.index][index - 1]
                    if (cards.first().stacksOn(below) && foundationFor(listOf(below)) == null) return
                }
                val targets = columns.indices.filter { it != (from as? Place.Column)?.index && canPlace(cards, Place.Column(it)) }
                count += targets.count { columns[it].isNotEmpty() }
                if (targets.any { columns[it].isEmpty() } && !(from is Place.Column && index == 0)) count++
            }
            cells.indices.forEach { countFrom(Place.Cell(it), 0) }
            columns.forEachIndexed { i, column -> column.indices.forEach { countFrom(Place.Column(i), it) } }
            return count
        }

    /** A free cell is empty: there is still somewhere to put a card while looking for a way on. */
    val hasFreeCell: Boolean get() = freeCells > 0

    /** No moves left, nor a free cell to make one: the game can not be won from here. */
    val isStuck: Boolean get() = !isWon && possibleMoves == 0 && !hasFreeCell

    /**
     * Whether the rest can be played out on its own: every column in order, each card lower than the
     * one under it, so that the lowest card left is always on top of one, or in a cell, ready to go home.
     */
    val canAutoComplete: Boolean
        get() = !isWon && columns.all { column -> (1..column.lastIndex).all { column[it].rank < column[it - 1].rank } }

    /** One step of playing out: the lowest card on top of a column or in a cell goes to its foundation. */
    fun autoCompleteStep(): FreeCellGame? {
        if (!canAutoComplete) return null
        val candidates = buildList {
            cells.forEachIndexed { i, card -> if (card != null) add(Place.Cell(i) to card) }
            columns.forEachIndexed { i, column -> column.lastOrNull()?.let { add(Place.Column(i) to it) } }
        }.sortedBy { it.second.rank }
        for ((from, card) in candidates) {
            val target = foundationFor(listOf(card)) ?: continue
            return move(from, cards(from).lastIndex, target)
        }
        return null
    }

    private fun with(place: Place, cards: List<Card>): FreeCellGame = when (place) {
        is Place.Cell -> copy(cells = this.cells.toMutableList().also { it[place.index] = cards.lastOrNull() })
        is Place.Foundation -> copy(foundations = foundations.toMutableList().also { it[place.index] = cards })
        is Place.Column -> copy(columns = columns.toMutableList().also { it[place.index] = cards })
    }

    fun toJson(): String = JSONObject()
        .put("moves", moves)
        .put("cells", JSONArray(cells.map { it?.id?.plus(1) ?: 0 }))
        .put("foundations", JSONArray(foundations.map(::encode)))
        .put("columns", JSONArray(columns.map(::encode)))
        .toString()

    companion object {
        const val FOUR_CELLS = "CELLS4"
        const val TWO_CELLS = "CELLS2"

        fun variantOf(cellCount: Int) = if (cellCount == 2) TWO_CELLS else FOUR_CELLS

        fun cellCountOf(variant: String) = if (variant == TWO_CELLS) 2 else 4

        /** A shuffled deal, face up, one card to each column in turn: the first four get seven, the rest six. */
        fun deal(cellCount: Int, random: Random = Random.Default): FreeCellGame {
            val deck = (0 until 52).shuffled(random).map { Card(it) }
            val columns = List(8) { column -> deck.filterIndexed { i, _ -> i % 8 == column } }
            return FreeCellGame(List(cellCount) { null }, List(4) { emptyList() }, columns)
        }

        fun fromJson(json: String): FreeCellGame {
            val o = JSONObject(json)
            fun piles(name: String) = o.getJSONArray(name).let { a -> List(a.length()) { decode(a.getJSONArray(it)) } }
            val cells = o.getJSONArray("cells")
            return FreeCellGame(
                cells = List(cells.length()) { cells.getInt(it).takeIf { n -> n > 0 }?.let { n -> Card(n - 1) } },
                foundations = piles("foundations"),
                columns = piles("columns"),
                moves = o.getInt("moves"),
            )
        }

        /** A card as one number: its id plus one. */
        private fun encode(cards: List<Card>) = JSONArray(cards.map { it.id + 1 })

        private fun decode(array: JSONArray) = List(array.length()) { Card(array.getInt(it) - 1) }
    }
}
