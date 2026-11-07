package com.butaca13.puzzles.game.tripeaks

import com.butaca13.puzzles.game.Difficulty
import com.butaca13.puzzles.game.cards.Card
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.random.Random

/** Where cards lie: a place in the peaks, the stock, and the waste the cards are played onto. */
sealed interface Place {
    data class Peak(val index: Int) : Place
    data object Stock : Place
    data object Waste : Place
}

/**
 * TriPeaks: 28 cards in three peaks of four rows, only the bottom row face up; a card turns over once
 * nothing lies on it. An uncovered card one rank above or below the waste's top goes onto the waste, and
 * the stock turns over one more when none does. Clearing the peaks wins. On [wraps], the easy level, the
 * king and the ace follow each other too. Each action gives a new game, or null when it is not allowed,
 * so that earlier ones can be kept to undo.
 */
data class TriPeaksGame(
    /** The peaks' places in [PLACES] order, null once played. */
    val peaks: List<Card?>,
    val stock: List<Card>,
    val waste: List<Card>,
    val wraps: Boolean,
    val moves: Int = 0,
) {
    val variant: String get() = variantOf(wraps)

    val isWon: Boolean get() = peaks.all { it == null }

    /** Whether nothing lies on the place at [index]: no card left on the row below that overlaps it. */
    fun uncovered(index: Int): Boolean = COVERS[index].all { peaks[it] == null }

    /** Whether [card] can go onto the waste: one rank from its top, round from king to ace on [wraps]. */
    fun fits(card: Card): Boolean {
        val top = waste.lastOrNull() ?: return false
        val gap = abs(card.rank - top.rank)
        return gap == 1 || (wraps && gap == 12)
    }

    /** The card at [index] if it can be played now. */
    fun playable(index: Int): Card? = peaks.getOrNull(index)?.takeIf { uncovered(index) && fits(it) }

    /** Plays the card at [index] onto the waste, turning over those it leaves uncovered. */
    fun play(index: Int): TriPeaksGame? {
        val card = playable(index) ?: return null
        val left = peaks.toMutableList().also { it[index] = null }
        val turned = left.mapIndexed { i, c -> if (c != null && !c.faceUp && COVERS[i].all { left[it] == null }) c.copy(faceUp = true) else c }
        return copy(peaks = turned, waste = waste + card, moves = moves + 1)
    }

    /** Turns the stock's next card onto the waste. */
    fun draw(): TriPeaksGame? {
        if (stock.isEmpty()) return null
        return copy(stock = stock.dropLast(1), waste = waste + stock.last().copy(faceUp = true), moves = moves + 1)
    }

    /** How many cards can be played now. */
    val possibleMoves: Int get() = peaks.indices.count { playable(it) != null }

    /** No card to play, nor any to turn over: the game can not be won from here. */
    val isStuck: Boolean get() = !isWon && possibleMoves == 0 && stock.isEmpty()

    fun toJson(): String = JSONObject()
        .put("wraps", wraps)
        .put("moves", moves)
        .put("peaks", JSONArray(peaks.map { card -> card?.let { if (it.faceUp) it.id + 1 else -(it.id + 1) } ?: 0 }))
        .put("stock", encode(stock))
        .put("waste", encode(waste))
        .toString()

    companion object {
        /** The easy level wraps round from king to ace; the hard one does not. */
        val EASY = Difficulty.EASY.name
        val HARD = Difficulty.HARD.name

        fun variantOf(wraps: Boolean) = if (wraps) EASY else HARD

        fun wrapsOf(variant: String) = variant != HARD

        /**
         * Every place in the peaks as its row, from the top, and where it lies across, in half cards:
         * three tops, two under each, then nine and ten in rows that join the peaks at the bottom.
         */
        val PLACES: List<Pair<Int, Int>> =
            listOf(3, 9, 15).map { 0 to it } +
                listOf(2, 4, 8, 10, 14, 16).map { 1 to it } +
                (0 until 9).map { 2 to 1 + it * 2 } +
                (0 until 10).map { 3 to it * 2 }

        val SIZE = PLACES.size

        /** For each place, those on the row below that lie half over it. */
        private val COVERS: List<List<Int>> = PLACES.map { (row, x) ->
            PLACES.indices.filter { PLACES[it].first == row + 1 && abs(PLACES[it].second - x) == 1 }
        }

        /** A shuffled deal: 28 cards in the peaks, the bottom row face up, one on the waste and 23 in the stock. */
        fun deal(wraps: Boolean, random: Random = Random.Default): TriPeaksGame {
            val deck = (0 until 52).shuffled(random)
            val peaks = deck.take(SIZE).mapIndexed { i, id -> Card(id, faceUp = PLACES[i].first == 3) }
            val rest = deck.drop(SIZE)
            return TriPeaksGame(
                peaks = peaks,
                stock = rest.dropLast(1).map { Card(it, faceUp = false) },
                waste = listOf(Card(rest.last())),
                wraps = wraps,
            )
        }

        fun fromJson(json: String): TriPeaksGame {
            val o = JSONObject(json)
            val peaks = o.getJSONArray("peaks")
            return TriPeaksGame(
                peaks = List(peaks.length()) { peaks.getInt(it).takeIf { n -> n != 0 }?.let { n -> Card(abs(n) - 1, faceUp = n > 0) } },
                stock = decode(o.getJSONArray("stock")),
                waste = decode(o.getJSONArray("waste")),
                wraps = o.getBoolean("wraps"),
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
