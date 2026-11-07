package com.butaca13.puzzles.game.pyramid

import com.butaca13.puzzles.game.cards.Card
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.random.Random

/** Where cards lie: a place in the pyramid, the stock, the waste beside it and the cards taken away. */
sealed interface Spot {
    data class Pyramid(val index: Int) : Spot
    data object Stock : Spot
    data object Waste : Spot
    data object Discard : Spot
}

/**
 * Pyramid: 28 cards in seven rows, each row half covering the one above. Two uncovered cards that add up
 * to 13 are taken away together, a king on its own; the jack counts 11 and the queen 12. The stock turns
 * over one card at a time onto the waste, whose top card plays too, and goes round again as many
 * [passes] as the level allows. Clearing the pyramid wins. Each action gives a new game, or null when it
 * is not allowed, so that earlier ones can be kept to undo.
 */
data class PyramidGame(
    /** The pyramid row by row from the top, 1 + 2 + … + 7 places, null once taken. */
    val pyramid: List<Card?>,
    val stock: List<Card>,
    val waste: List<Card>,
    /** The cards taken away, the last on top. */
    val discard: List<Card>,
    /** How many times the stock can be gone through: 1 or 3. */
    val passes: Int,
    /** Which time round the stock is: the first is 1. */
    val pass: Int = 1,
    val moves: Int = 0,
) {
    val variant: String get() = variantOf(passes)

    val isWon: Boolean get() = pyramid.all { it == null }

    /** The card at [spot] that can be played, if any: an uncovered one in the pyramid, or the waste's top. */
    fun playable(spot: Spot): Card? = when (spot) {
        is Spot.Pyramid -> pyramid.getOrNull(spot.index)?.takeIf { uncovered(spot.index) }
        Spot.Waste -> waste.lastOrNull()
        Spot.Stock, Spot.Discard -> null
    }

    /** Whether nothing lies on the place at [index]: the bottom row, or both places below it empty. */
    fun uncovered(index: Int): Boolean {
        val (row, col) = rowCol(index)
        if (row == ROWS - 1) return true
        return pyramid[indexOf(row + 1, col)] == null && pyramid[indexOf(row + 1, col + 1)] == null
    }

    /** Every card that can be played now, with where it lies. */
    val playables: List<Pair<Spot, Card>>
        get() = buildList {
            pyramid.indices.forEach { i -> playable(Spot.Pyramid(i))?.let { add(Spot.Pyramid(i) to it) } }
            playable(Spot.Waste)?.let { add(Spot.Waste to it) }
        }

    /** Takes away a king on its own. */
    fun remove(spot: Spot): PyramidGame? {
        val card = playable(spot) ?: return null
        if (card.rank != 13) return null
        return take(spot).let { it.copy(discard = it.discard + card, moves = moves + 1) }
    }

    /** Takes away two cards that add up to 13. */
    fun pair(a: Spot, b: Spot): PyramidGame? {
        if (a == b) return null
        val first = playable(a) ?: return null
        val second = playable(b) ?: return null
        if (first.rank + second.rank != 13) return null
        // The waste gives only its top card, so it can be one of the pair at most.
        return take(a).take(b).let { it.copy(discard = it.discard + first + second, moves = moves + 1) }
    }

    private fun take(spot: Spot): PyramidGame = when (spot) {
        is Spot.Pyramid -> copy(pyramid = pyramid.toMutableList().also { it[spot.index] = null })
        Spot.Waste -> copy(waste = waste.dropLast(1))
        Spot.Stock, Spot.Discard -> this
    }

    /** Whether the stock can turn a card over, or the waste go back to be gone through again. */
    val canDraw: Boolean get() = stock.isNotEmpty() || (waste.isNotEmpty() && pass < passes)

    /** Turns the stock's next card onto the waste, or the waste back into the stock for another pass. */
    fun draw(): PyramidGame? = when {
        stock.isNotEmpty() -> copy(stock = stock.dropLast(1), waste = waste + stock.last().copy(faceUp = true), moves = moves + 1)
        waste.isNotEmpty() && pass < passes ->
            copy(stock = waste.reversed().map { it.copy(faceUp = false) }, waste = emptyList(), pass = pass + 1, moves = moves + 1)
        else -> null
    }

    /** How many passes are left after this one. */
    val passesLeft: Int get() = passes - pass

    /** How many plays there are now: each king, and each pair that adds up to 13. */
    val possibleMoves: Int
        get() {
            val cards = playables.map { it.second }
            val kings = cards.count { it.rank == 13 }
            val pairs = cards.indices.sumOf { i -> (i + 1..cards.lastIndex).count { j -> cards[i].rank + cards[j].rank == 13 } }
            return kings + pairs
        }

    /** No plays left, nor any card to turn over: the game can not be won from here. */
    val isStuck: Boolean get() = !isWon && possibleMoves == 0 && !canDraw

    fun toJson(): String = JSONObject()
        .put("passes", passes)
        .put("pass", pass)
        .put("moves", moves)
        .put("pyramid", JSONArray(pyramid.map { it?.let { c -> c.id + 1 } ?: 0 }))
        .put("stock", encode(stock))
        .put("waste", encode(waste))
        .put("discard", encode(discard))
        .toString()

    companion object {
        const val ONE_PASS = "PASS1"
        const val THREE_PASSES = "PASS3"
        const val ROWS = 7
        const val SIZE = ROWS * (ROWS + 1) / 2

        fun variantOf(passes: Int) = if (passes == 1) ONE_PASS else THREE_PASSES

        fun passesOf(variant: String) = if (variant == ONE_PASS) 1 else 3

        fun indexOf(row: Int, col: Int) = row * (row + 1) / 2 + col

        fun rowCol(index: Int): Pair<Int, Int> {
            var row = 0
            while (indexOf(row + 1, 0) <= index) row++
            return row to index - indexOf(row, 0)
        }

        /** A shuffled deal: 28 cards face up in the pyramid, the other 24 face down in the stock. */
        fun deal(passes: Int, random: Random = Random.Default): PyramidGame {
            val deck = (0 until 52).shuffled(random)
            return PyramidGame(
                pyramid = deck.take(SIZE).map { Card(it) },
                stock = deck.drop(SIZE).map { Card(it, faceUp = false) },
                waste = emptyList(),
                discard = emptyList(),
                passes = passes,
            )
        }

        fun fromJson(json: String): PyramidGame {
            val o = JSONObject(json)
            val pyramid = o.getJSONArray("pyramid")
            return PyramidGame(
                pyramid = List(pyramid.length()) { pyramid.getInt(it).takeIf { n -> n > 0 }?.let { n -> Card(n - 1) } },
                stock = decode(o.getJSONArray("stock")),
                waste = decode(o.getJSONArray("waste")),
                discard = decode(o.getJSONArray("discard")),
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
