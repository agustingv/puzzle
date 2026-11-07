package com.butaca13.puzzles.game.fortythieves

import com.butaca13.puzzles.game.cards.Card
import com.butaca13.puzzles.game.cards.Suit
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.random.Random

/** Where cards lie: the stock, the waste beside it, the eight foundations and the ten columns. */
sealed interface Pile {
    data object Stock : Pile
    data object Waste : Pile
    data class Foundation(val index: Int) : Pile
    data class Column(val index: Int) : Pile
}

/**
 * Forty Thieves: two decks, ten columns of four cards face up, and eight foundations that build up by
 * suit from the ace. Columns build down by suit, one card at a time, and any card goes on an empty one.
 * The stock turns over one card at a time onto the waste, whose top card plays too, and goes round
 * [passes] times. A run of one suit may move at once when there are empty columns enough to move it
 * card by card: the same as the one-card rule, without the chore. Each action gives a new game, or
 * null when it is not allowed, so that earlier ones can be kept to undo.
 */
data class FortyThievesGame(
    val stock: List<Card>,
    val waste: List<Card>,
    val foundations: List<List<Card>>,
    /** Every column bottom first, so the last card is on top. */
    val columns: List<List<Card>>,
    /** How many times the stock can be gone through: 1 or 2. */
    val passes: Int,
    val pass: Int = 1,
    val moves: Int = 0,
) {
    val variant: String get() = variantOf(passes)

    val isWon: Boolean get() = foundations.all { it.size == 13 }

    fun cards(pile: Pile): List<Card> = when (pile) {
        Pile.Stock -> stock
        Pile.Waste -> waste
        is Pile.Foundation -> foundations[pile.index]
        is Pile.Column -> columns[pile.index]
    }

    /** One rank lower, the same suit. */
    private fun fits(card: Card, onto: Card) = card.suit == onto.suit && card.rank == onto.rank - 1

    /**
     * How many cards can move together: one, doubled for each empty column they can pass through on the
     * way, which an empty column they go to is not.
     */
    fun maxRun(toEmptyColumn: Boolean): Int {
        val empty = columns.count { it.isEmpty() } - if (toEmptyColumn) 1 else 0
        return 1 shl empty.coerceAtLeast(0)
    }

    /** Whether the cards from [index] to the top of [from] can be picked up together: a run of one suit. */
    fun canPickUp(from: Pile, index: Int): Boolean {
        val cards = cards(from)
        if (index !in cards.indices) return false
        return when (from) {
            Pile.Stock -> false
            Pile.Waste, is Pile.Foundation -> index == cards.lastIndex
            is Pile.Column -> (index + 1..cards.lastIndex).all { fits(cards[it], cards[it - 1]) } &&
                cards.size - index <= maxRun(toEmptyColumn = false)
        }
    }

    fun canPlace(cards: List<Card>, to: Pile): Boolean {
        val first = cards.firstOrNull() ?: return false
        val top = cards(to).lastOrNull()
        return when (to) {
            Pile.Stock, Pile.Waste -> false
            is Pile.Foundation -> cards.size == 1 &&
                if (top == null) first.rank == 1 else first.suit == top.suit && first.rank == top.rank + 1
            is Pile.Column ->
                if (top == null) cards.size <= maxRun(toEmptyColumn = true) else fits(first, top) && cards.size <= maxRun(toEmptyColumn = false)
        }
    }

    /** Moves the cards from [index] up in [from] onto [to]: one move however many go. */
    fun move(from: Pile, index: Int, to: Pile): FortyThievesGame? {
        if (from == to || !canPickUp(from, index)) return null
        val cards = cards(from).drop(index)
        if (!canPlace(cards, to)) return null
        return with(from, cards(from).take(index)).with(to, cards(to) + cards).copy(moves = moves + 1)
    }

    /** Whether the stock can turn a card over, or the waste go back to be gone through again. */
    val canDraw: Boolean get() = stock.isNotEmpty() || (waste.isNotEmpty() && pass < passes)

    /** Turns the stock's next card onto the waste, or the waste back into the stock for another pass. */
    fun draw(): FortyThievesGame? = when {
        stock.isNotEmpty() -> copy(stock = stock.dropLast(1), waste = waste + stock.last().copy(faceUp = true), moves = moves + 1)
        waste.isNotEmpty() && pass < passes ->
            copy(stock = waste.reversed().map { it.copy(faceUp = false) }, waste = emptyList(), pass = pass + 1, moves = moves + 1)
        else -> null
    }

    /** How many passes are left after this one. */
    val passesLeft: Int get() = passes - pass

    /** Where a tap sends the cards from [index] up: a foundation first, then a column with cards, then an empty one. */
    fun autoTarget(from: Pile, index: Int): Pile? {
        if (!canPickUp(from, index)) return null
        val cards = cards(from).drop(index)
        if (from !is Pile.Foundation) foundationFor(cards)?.let { return it }
        val targets = columns.indices.map { Pile.Column(it) }.filter { it != from && canPlace(cards, it) }
        targets.firstOrNull { columns[it.index].isNotEmpty() }?.let { return it }
        // A whole column gains nothing in another empty one.
        if (from is Pile.Column && index == 0) return null
        return targets.firstOrNull()
    }

    private fun foundationFor(cards: List<Card>): Pile? =
        foundations.indices.map { Pile.Foundation(it) }.firstOrNull { canPlace(cards, it) }

    /**
     * How many moves there are now that go somewhere useful, not counting a draw: home, once per column a
     * card or run fits on, and once for any empty column. Left out, as they gain nothing: a whole column
     * to an empty one, a run that already sits on a card it fits on, unless that card can then go home,
     * and cards taken back off the foundations.
     */
    val possibleMoves: Int
        get() {
            var count = 0
            fun countFrom(from: Pile, index: Int) {
                if (!canPickUp(from, index)) return
                val cards = cards(from).drop(index)
                if (foundationFor(cards) != null) count++
                if (from is Pile.Column && index > 0) {
                    val below = columns[from.index][index - 1]
                    if (fits(cards.first(), below) && foundationFor(listOf(below)) == null) return
                }
                val targets = columns.indices.filter { it != (from as? Pile.Column)?.index && canPlace(cards, Pile.Column(it)) }
                count += targets.count { columns[it].isNotEmpty() }
                if (targets.any { columns[it].isEmpty() } && !(from is Pile.Column && index == 0)) count++
            }
            if (waste.isNotEmpty()) countFrom(Pile.Waste, waste.lastIndex)
            columns.forEachIndexed { c, column -> column.indices.forEach { countFrom(Pile.Column(c), it) } }
            return count
        }

    /** No moves left, nor any card to turn over: the game can not be won from here. */
    val isStuck: Boolean get() = !isWon && possibleMoves == 0 && !canDraw

    /**
     * Whether the rest can be played out on its own: the stock and the waste used up, and every column
     * in order, each card lower than the one under it, so that the lowest card left is always on top.
     */
    val canAutoComplete: Boolean
        get() = !isWon && stock.isEmpty() && waste.isEmpty() &&
            columns.all { column -> (1..column.lastIndex).all { column[it].rank < column[it - 1].rank } }

    /** One step of playing out: the lowest card on top of a column goes to a foundation. */
    fun autoCompleteStep(): FortyThievesGame? {
        if (!canAutoComplete) return null
        val candidates = columns.indices.mapNotNull { c -> columns[c].lastOrNull()?.let { c to it } }.sortedBy { it.second.rank }
        for ((c, card) in candidates) {
            val target = foundationFor(listOf(card)) ?: continue
            return move(Pile.Column(c), columns[c].lastIndex, target)
        }
        return null
    }

    private fun with(pile: Pile, cards: List<Card>): FortyThievesGame = when (pile) {
        Pile.Stock -> copy(stock = cards)
        Pile.Waste -> copy(waste = cards)
        is Pile.Foundation -> copy(foundations = foundations.toMutableList().also { it[pile.index] = cards })
        is Pile.Column -> copy(columns = columns.toMutableList().also { it[pile.index] = cards })
    }

    fun toJson(): String = JSONObject()
        .put("passes", passes)
        .put("pass", pass)
        .put("moves", moves)
        .put("stock", encode(stock))
        .put("waste", encode(waste))
        .put("foundations", JSONArray(foundations.map(::encode)))
        .put("columns", JSONArray(columns.map(::encode)))
        .toString()

    companion object {
        const val ONE_DEAL = "DEAL1"
        const val TWO_DEALS = "DEAL2"
        const val COLUMNS = 10
        const val FOUNDATIONS = 8

        fun variantOf(passes: Int) = if (passes == 2) TWO_DEALS else ONE_DEAL

        fun passesOf(variant: String) = if (variant == TWO_DEALS) 2 else 1

        /** A shuffled deal of two decks: four cards face up in each of the ten columns, the other 64 in the stock. */
        fun deal(passes: Int, random: Random = Random.Default): FortyThievesGame {
            val deck = (0 until 2).flatMap { copy -> Suit.entries.flatMap { suit -> (1..13).map { Card.of(suit, it, faceUp = false, copy = copy) } } }
                .shuffled(random).toMutableList()
            val columns = List(COLUMNS) { List(4) { deck.removeAt(deck.lastIndex).copy(faceUp = true) } }
            return FortyThievesGame(deck, emptyList(), List(FOUNDATIONS) { emptyList() }, columns, passes)
        }

        fun fromJson(json: String): FortyThievesGame {
            val o = JSONObject(json)
            fun piles(name: String) = o.getJSONArray(name).let { a -> List(a.length()) { decode(a.getJSONArray(it)) } }
            return FortyThievesGame(
                stock = decode(o.getJSONArray("stock")),
                waste = decode(o.getJSONArray("waste")),
                foundations = piles("foundations"),
                columns = piles("columns"),
                passes = o.getInt("passes"),
                pass = o.getInt("pass"),
                moves = o.getInt("moves"),
            )
        }

        /** A card as one number: its id plus one, negative when face down. */
        private fun encode(cards: List<Card>) = JSONArray(cards.map { if (it.faceUp) it.id + 1 else -(it.id + 1) })

        private fun decode(array: JSONArray) = List(array.length()) {
            val n = array.getInt(it)
            Card(abs(n) - 1, faceUp = n > 0)
        }
    }
}
