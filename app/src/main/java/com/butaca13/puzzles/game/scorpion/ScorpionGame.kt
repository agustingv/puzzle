package com.butaca13.puzzles.game.scorpion

import com.butaca13.puzzles.game.cards.Card
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.random.Random

/** Where cards lie: the reserve still to deal, the runs already done, and the seven columns. */
sealed interface Heap {
    data object Reserve : Heap
    data class Done(val index: Int) : Heap
    data class Column(val index: Int) : Heap
}

/**
 * Scorpion: one deck in seven columns, some cards face down, and three more in reserve. Any face-up card
 * moves with all those on top of it, in order or not, onto a card of its suit a rank higher; only a king
 * goes to an empty column, or any card in the Wasp game, [anyOnEmpty]. A run of a suit from the king
 * down to the ace leaves the table as soon as it is made, and four of them win. The reserve is dealt
 * once, a card onto each of the first three columns. Each action gives a new game, or null when it is
 * not allowed, so that earlier ones can be kept to undo.
 */
data class ScorpionGame(
    val reserve: List<Card>,
    /** Every column bottom first, so the last card is on top. */
    val columns: List<List<Card>>,
    /** The runs already done, the king first as they were on the table. */
    val done: List<List<Card>>,
    val anyOnEmpty: Boolean,
    val moves: Int = 0,
) {
    val variant: String get() = variantOf(anyOnEmpty)

    val isWon: Boolean get() = done.size == RUNS

    fun cards(heap: Heap): List<Card> = when (heap) {
        Heap.Reserve -> reserve
        is Heap.Done -> done.getOrElse(heap.index) { emptyList() }
        is Heap.Column -> columns[heap.index]
    }

    /** One rank lower, the same suit, both face up. */
    private fun fits(card: Card, onto: Card) = card.faceUp && onto.faceUp && card.suit == onto.suit && card.rank == onto.rank - 1

    /** Whether the cards from [index] up in [from] can be picked up: any face-up card, with all on top of it. */
    fun canPickUp(from: Heap, index: Int): Boolean =
        from is Heap.Column && columns[from.index].getOrNull(index)?.faceUp == true

    fun canPlace(cards: List<Card>, to: Heap): Boolean {
        val first = cards.firstOrNull() ?: return false
        if (to !is Heap.Column) return false
        val top = columns[to.index].lastOrNull() ?: return anyOnEmpty || first.rank == 13
        return fits(first, top)
    }

    /** Moves the cards from [index] up in [from] onto [to], turning over the card they leave on top. */
    fun move(from: Heap, index: Int, to: Heap): ScorpionGame? {
        if (from == to || !canPickUp(from, index) || to !is Heap.Column) return null
        from as Heap.Column
        val cards = columns[from.index].drop(index)
        if (!canPlace(cards, to)) return null
        val next = columns.toMutableList()
        next[from.index] = turnTop(columns[from.index].take(index))
        next[to.index] = columns[to.index] + cards
        return copy(columns = next, moves = moves + 1).clearRuns()
    }

    val canDeal: Boolean get() = reserve.isNotEmpty()

    /** The reserve, face up, a card onto each of the first three columns. */
    fun deal(): ScorpionGame? {
        if (!canDeal) return null
        val dealt = reserve.reversed()
        val next = columns.mapIndexed { i, column -> if (i < dealt.size) column + dealt[i].copy(faceUp = true) else column }
        return copy(reserve = emptyList(), columns = next, moves = moves + 1).clearRuns()
    }

    /** Where a tap sends the cards from [index] up: onto a card of its suit, else an empty column. */
    fun autoTarget(from: Heap, index: Int): Heap? {
        if (!canPickUp(from, index)) return null
        val cards = cards(from).drop(index)
        val targets = columns.indices.map { Heap.Column(it) }.filter { it != from && canPlace(cards, it) }
        targets.firstOrNull { columns[it.index].isNotEmpty() }?.let { return it }
        // A whole column gains nothing in another empty one.
        if (index == 0) return null
        return targets.firstOrNull()
    }

    /**
     * How many moves there are now that go somewhere useful, not counting the deal: once per column a
     * card fits on, and once for any empty column. Left out, as they gain nothing: a whole column to an
     * empty one, and a card that already sits on the card it fits on.
     */
    val possibleMoves: Int
        get() {
            var count = 0
            columns.forEachIndexed { c, column ->
                column.indices.forEach { index ->
                    if (!canPickUp(Heap.Column(c), index)) return@forEach
                    val cards = column.drop(index)
                    val below = column.getOrNull(index - 1)
                    if (below != null && fits(cards.first(), below)) return@forEach
                    val targets = columns.indices.filter { it != c && canPlace(cards, Heap.Column(it)) }
                    count += targets.count { columns[it].isNotEmpty() }
                    if (index > 0 && targets.any { columns[it].isEmpty() }) count++
                }
            }
            return count
        }

    /** No moves left, nor the reserve to deal: the game can not be won from here. */
    val isStuck: Boolean get() = !isWon && possibleMoves == 0 && !canDeal

    /** Takes off the table every finished run, the king down to the ace of one suit, on top of a column. */
    private fun clearRuns(): ScorpionGame {
        var game = this
        columns.forEachIndexed { i, column ->
            if (column.size < 13) return@forEachIndexed
            val run = column.takeLast(13)
            if (run.first().rank == 13 && (1 until 13).all { fits(run[it], run[it - 1]) }) {
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
        .put("anyOnEmpty", anyOnEmpty)
        .put("moves", moves)
        .put("reserve", encode(reserve))
        .put("columns", JSONArray(columns.map(::encode)))
        .put("done", JSONArray(done.map(::encode)))
        .toString()

    companion object {
        const val CLASSIC = "CLASSIC"
        const val WASP = "WASP"
        const val RUNS = 4

        fun variantOf(anyOnEmpty: Boolean) = if (anyOnEmpty) WASP else CLASSIC

        fun anyOnEmptyOf(variant: String) = variant == WASP

        /**
         * A shuffled deal: seven columns of seven cards, the first three with three of them face down, the
         * rest all face up; the other three in reserve.
         */
        fun deal(anyOnEmpty: Boolean, random: Random = Random.Default): ScorpionGame {
            val deck = (0 until 52).shuffled(random).toMutableList()
            val columns = List(7) { c -> List(7) { i -> Card(deck.removeAt(deck.lastIndex), faceUp = c >= 3 || i >= 3) } }
            return ScorpionGame(deck.map { Card(it, faceUp = false) }, columns, emptyList(), anyOnEmpty)
        }

        fun fromJson(json: String): ScorpionGame {
            val o = JSONObject(json)
            fun piles(name: String) = o.getJSONArray(name).let { a -> List(a.length()) { decode(a.getJSONArray(it)) } }
            return ScorpionGame(decode(o.getJSONArray("reserve")), piles("columns"), piles("done"), o.getBoolean("anyOnEmpty"), o.getInt("moves"))
        }

        /** A card as one number: its id plus one, negative when face down. */
        private fun encode(cards: List<Card>) = JSONArray(cards.map { if (it.faceUp) it.id + 1 else -(it.id + 1) })

        private fun decode(array: JSONArray) = List(array.length()) {
            val n = array.getInt(it)
            Card(abs(n) - 1, faceUp = n > 0)
        }
    }
}
