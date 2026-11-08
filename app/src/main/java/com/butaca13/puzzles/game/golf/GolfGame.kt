package com.butaca13.puzzles.game.golf

import com.butaca13.puzzles.game.cards.Card
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.random.Random

/** Where cards lie: a column, the stock, and the waste the cards are played onto. */
sealed interface Place {
    data class Column(val index: Int) : Place
    data object Stock : Place
    data object Waste : Place
}

/**
 * Golf: seven columns of five cards, all face up. The top card of a column goes onto the waste when it
 * is a rank above or below the waste's top, of any suit; the stock turns over one more when none fits.
 * Clearing the columns wins. In the hard game nothing goes on a king; in the easy one, [wraps], the king
 * and the ace follow each other. Each action gives a new game, or null when it is not allowed, so that
 * earlier ones can be kept to undo.
 */
data class GolfGame(
    /** Every column bottom first, so the last card is on top. */
    val columns: List<List<Card>>,
    val stock: List<Card>,
    val waste: List<Card>,
    val wraps: Boolean,
    val moves: Int = 0,
) {
    val variant: String get() = variantOf(wraps)

    val isWon: Boolean get() = columns.all { it.isEmpty() }

    /** Whether [card] goes onto the waste: a rank from its top, round from king to ace on [wraps]. */
    fun fits(card: Card): Boolean {
        val top = waste.lastOrNull() ?: return false
        if (!wraps && top.rank == 13) return false
        val gap = abs(card.rank - top.rank)
        return gap == 1 || (wraps && gap == 12)
    }

    /** The top card of column [index] if it can be played now. */
    fun playable(index: Int): Card? = columns.getOrNull(index)?.lastOrNull()?.takeIf(::fits)

    /** Plays the top card of column [index] onto the waste. */
    fun play(index: Int): GolfGame? {
        val card = playable(index) ?: return null
        val next = columns.toMutableList().also { it[index] = columns[index].dropLast(1) }
        return copy(columns = next, waste = waste + card, moves = moves + 1)
    }

    /** Turns the stock's next card onto the waste. */
    fun draw(): GolfGame? {
        if (stock.isEmpty()) return null
        return copy(stock = stock.dropLast(1), waste = waste + stock.last().copy(faceUp = true), moves = moves + 1)
    }

    /** How many cards can be played now. */
    val possibleMoves: Int get() = columns.indices.count { playable(it) != null }

    /** No card to play, nor any to turn over: the game can not be won from here. */
    val isStuck: Boolean get() = !isWon && possibleMoves == 0 && stock.isEmpty()

    /** Cards still in the columns: golf's score, the fewer the better, shown when the game is lost. */
    val left: Int get() = columns.sumOf { it.size }

    fun toJson(): String = JSONObject()
        .put("wraps", wraps)
        .put("moves", moves)
        .put("columns", JSONArray(columns.map(::encode)))
        .put("stock", encode(stock))
        .put("waste", encode(waste))
        .toString()

    companion object {
        const val EASY = "EASY"
        const val HARD = "HARD"
        const val COLUMNS = 7

        fun variantOf(wraps: Boolean) = if (wraps) EASY else HARD

        fun wrapsOf(variant: String) = variant != HARD

        /** A shuffled deal: seven columns of five, face up, one card on the waste and the other 16 in the stock. */
        fun deal(wraps: Boolean, random: Random = Random.Default): GolfGame {
            val deck = (0 until 52).shuffled(random)
            val columns = List(COLUMNS) { c -> deck.subList(c * 5, c * 5 + 5).map { Card(it) } }
            val rest = deck.drop(COLUMNS * 5)
            return GolfGame(columns, rest.dropLast(1).map { Card(it, faceUp = false) }, listOf(Card(rest.last())), wraps)
        }

        fun fromJson(json: String): GolfGame {
            val o = JSONObject(json)
            val columns = o.getJSONArray("columns").let { a -> List(a.length()) { decode(a.getJSONArray(it)) } }
            return GolfGame(columns, decode(o.getJSONArray("stock")), decode(o.getJSONArray("waste")), o.getBoolean("wraps"), o.getInt("moves"))
        }

        /** A card as one number: its id plus one, negative when face down. */
        private fun encode(cards: List<Card>) = JSONArray(cards.map { if (it.faceUp) it.id + 1 else -(it.id + 1) })

        private fun decode(array: JSONArray) = List(array.length()) {
            val n = array.getInt(it)
            Card(abs(n) - 1, faceUp = n > 0)
        }
    }
}
