package com.butaca13.puzzles.game.spider

import com.butaca13.puzzles.game.cards.Card
import com.butaca13.puzzles.game.cards.Suit
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.random.Random

/** Where cards lie: the stock still to deal, the runs already done, and the ten columns. */
sealed interface Heap {
    data object Stock : Heap
    data class Done(val index: Int) : Heap
    data class Column(val index: Int) : Heap
}

/**
 * Spider: two decks' worth of cards, of one, two or four suits, in ten columns. Any card goes on one a
 * rank higher, but only a run of one suit moves together; a run of a suit from the king down to the ace
 * leaves the table as soon as it is made. The stock deals one more card onto every column, as long as
 * none is empty. Eight runs done win. Each action gives a new game, or null when it is not allowed, so
 * that earlier ones can be kept to undo.
 */
data class SpiderGame(
    val stock: List<Card>,
    /** Every column bottom first, so the last card is on top. */
    val columns: List<List<Card>>,
    /** The runs already done, the king first as they were on the table. */
    val done: List<List<Card>>,
    /** How many suits are in play: 1, 2 or 4. */
    val suits: Int,
    val moves: Int = 0,
) {
    val variant: String get() = variantOf(suits)

    val isWon: Boolean get() = done.size == RUNS

    fun cards(heap: Heap): List<Card> = when (heap) {
        Heap.Stock -> stock
        is Heap.Done -> done.getOrElse(heap.index) { emptyList() }
        is Heap.Column -> columns[heap.index]
    }

    /** Whether the cards from [index] to the top of [from] can be picked up together: a run of one suit. */
    fun canPickUp(from: Heap, index: Int): Boolean {
        if (from !is Heap.Column) return false
        val cards = columns[from.index]
        if (index !in cards.indices || !cards[index].faceUp) return false
        return (index + 1..cards.lastIndex).all { follows(cards[it], cards[it - 1]) }
    }

    fun canPlace(cards: List<Card>, to: Heap): Boolean {
        val first = cards.firstOrNull() ?: return false
        if (to !is Heap.Column) return false
        val top = columns[to.index].lastOrNull() ?: return true
        return top.faceUp && first.rank == top.rank - 1
    }

    /** Moves the cards from [index] up in [from] onto [to], turning over the card they leave on top. */
    fun move(from: Heap, index: Int, to: Heap): SpiderGame? {
        if (from == to || !canPickUp(from, index) || to !is Heap.Column) return null
        from as Heap.Column
        val cards = columns[from.index].drop(index)
        if (!canPlace(cards, to)) return null
        val next = columns.toMutableList()
        next[from.index] = turnTop(columns[from.index].take(index))
        next[to.index] = columns[to.index] + cards
        return copy(columns = next, moves = moves + 1).clearRuns()
    }

    /** Dealing is allowed while there is stock and no column is empty. */
    val canDeal: Boolean get() = stock.isNotEmpty() && columns.none { it.isEmpty() }

    /** One card from the stock face up onto every column. */
    fun deal(): SpiderGame? {
        if (!canDeal) return null
        val dealt = stock.takeLast(COLUMNS).reversed()
        val next = columns.mapIndexed { i, column -> column + dealt[i].copy(faceUp = true) }
        return copy(stock = stock.dropLast(COLUMNS), columns = next, moves = moves + 1).clearRuns()
    }

    /** How many deals the stock has left. */
    val dealsLeft: Int get() = stock.size / COLUMNS

    /**
     * Where a tap sends the cards from [index] up: onto a card a rank higher of the same suit first, so
     * that runs grow, then onto one of any suit, then onto an empty column.
     */
    fun autoTarget(from: Heap, index: Int): Heap? {
        if (!canPickUp(from, index)) return null
        val cards = cards(from).drop(index)
        val targets = columns.indices.map { Heap.Column(it) }.filter { it != from && canPlace(cards, it) }
        targets.firstOrNull { columns[it.index].lastOrNull()?.suit == cards.first().suit }?.let { return it }
        targets.firstOrNull { columns[it.index].isNotEmpty() }?.let { return it }
        // A whole column gains nothing in another empty one.
        if (index == 0) return null
        return targets.firstOrNull()
    }

    /**
     * How many moves there are now that go somewhere useful, not counting a deal: once per column a run
     * fits on, and once for any empty column. Left out, as they gain nothing: a whole column to an empty
     * one, and a run that already sits on a card a rank higher onto another such card, unless that one
     * is of its suit and the one it leaves is not.
     */
    val possibleMoves: Int
        get() {
            var count = 0
            columns.forEachIndexed { c, column ->
                column.indices.forEach { index ->
                    if (!canPickUp(Heap.Column(c), index)) return@forEach
                    val cards = column.drop(index)
                    val first = cards.first()
                    val below = column.getOrNull(index - 1)?.takeIf { it.faceUp && it.rank == first.rank + 1 }
                    for (t in columns.indices) {
                        val top = columns[t].lastOrNull()
                        if (t == c || top == null || !canPlace(cards, Heap.Column(t))) continue
                        if (below != null && (below.suit == first.suit || top.suit != first.suit)) continue
                        count++
                    }
                    if (index > 0 && below == null && columns.any { it.isEmpty() }) count++
                }
            }
            return count
        }

    /** No moves left, nor any deal: the game can not be won from here. */
    val isStuck: Boolean get() = !isWon && possibleMoves == 0 && stock.isEmpty()

    /** Takes off the table every finished run, the king down to the ace of one suit, on top of a column. */
    private fun clearRuns(): SpiderGame {
        var game = this
        columns.forEachIndexed { i, column ->
            if (column.size < 13) return@forEachIndexed
            val run = column.takeLast(13)
            val complete = run.first().rank == 13 && run.all { it.faceUp } &&
                (1 until 13).all { follows(run[it], run[it - 1]) }
            if (complete) {
                val next = game.columns.toMutableList()
                next[i] = turnTop(column.dropLast(13))
                game = game.copy(columns = next, done = game.done + listOf(run))
            }
        }
        return game
    }

    private fun turnTop(cards: List<Card>): List<Card> =
        if (cards.lastOrNull()?.faceUp == false) cards.dropLast(1) + cards.last().copy(faceUp = true) else cards

    fun toJson(): String = JSONObject()
        .put("suits", suits)
        .put("moves", moves)
        .put("stock", encode(stock))
        .put("columns", JSONArray(columns.map(::encode)))
        .put("done", JSONArray(done.map(::encode)))
        .toString()

    companion object {
        const val ONE_SUIT = "SUITS1"
        const val TWO_SUITS = "SUITS2"
        const val FOUR_SUITS = "SUITS4"
        const val COLUMNS = 10
        const val RUNS = 8
        const val DEALS = 5

        fun variantOf(suits: Int) = when (suits) {
            1 -> ONE_SUIT
            2 -> TWO_SUITS
            else -> FOUR_SUITS
        }

        fun suitsOf(variant: String) = when (variant) {
            ONE_SUIT -> 1
            TWO_SUITS -> 2
            else -> 4
        }

        /**
         * A shuffled deal of 104 cards: eight runs' worth of the suits in play, spades first, then hearts,
         * then the rest. Six cards in each of the first four columns and five in the others, only the top
         * ones face up; the other 50 make the stock, five deals.
         */
        fun deal(suits: Int, random: Random = Random.Default): SpiderGame {
            val inPlay = listOf(Suit.SPADES, Suit.HEARTS, Suit.DIAMONDS, Suit.CLUBS).take(suits)
            val deck = (0 until RUNS).flatMap { run ->
                val suit = inPlay[run % suits]
                (1..13).map { rank -> Card.of(suit, rank, faceUp = false, copy = run / suits) }
            }.shuffled(random).toMutableList()
            val columns = List(COLUMNS) { column ->
                val size = if (column < 4) 6 else 5
                List(size) { deck.removeAt(deck.lastIndex) }.let { it.dropLast(1) + it.last().copy(faceUp = true) }
            }
            return SpiderGame(deck, columns, emptyList(), suits)
        }

        fun fromJson(json: String): SpiderGame {
            val o = JSONObject(json)
            fun piles(name: String) = o.getJSONArray(name).let { a -> List(a.length()) { decode(a.getJSONArray(it)) } }
            return SpiderGame(
                stock = decode(o.getJSONArray("stock")),
                columns = piles("columns"),
                done = piles("done"),
                suits = o.getInt("suits"),
                moves = o.getInt("moves"),
            )
        }

        /** Within a run: one rank lower, the same suit. */
        private fun follows(card: Card, onto: Card) = card.faceUp && onto.faceUp && card.suit == onto.suit && card.rank == onto.rank - 1

        /** A card as one number: its id plus one, negative when face down. */
        private fun encode(cards: List<Card>) = JSONArray(cards.map { if (it.faceUp) it.id + 1 else -(it.id + 1) })

        private fun decode(array: JSONArray) = List(array.length()) {
            val n = array.getInt(it)
            Card(abs(n) - 1, faceUp = n > 0)
        }
    }
}
