package com.butaca13.puzzles.game.bakersdozen

import com.butaca13.puzzles.game.cards.Card
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/** Where cards lie: the four foundations and the thirteen columns. */
sealed interface Pile {
    data class Foundation(val index: Int) : Pile
    data class Column(val index: Int) : Pile
}

/**
 * Baker's Dozen: thirteen columns of four cards, all face up, every king sent to the bottom of its
 * column at the deal. One card moves at a time, onto a card a rank higher of any suit, and the
 * foundations build up by suit from the ace. There is no stock and nowhere else to put a card: an empty
 * column stays empty, or takes a king in the Portuguese game, [kingsOnEmpty]. Nearly every deal can be
 * won, by thinking it through. Each action gives a new game, or null when it is not allowed, so that
 * earlier ones can be kept to undo.
 */
data class BakersDozenGame(
    val foundations: List<List<Card>>,
    /** Every column bottom first, so the last card is on top. */
    val columns: List<List<Card>>,
    val kingsOnEmpty: Boolean,
    val moves: Int = 0,
) {
    val variant: String get() = variantOf(kingsOnEmpty)

    val isWon: Boolean get() = foundations.all { it.size == 13 }

    fun cards(pile: Pile): List<Card> = when (pile) {
        is Pile.Foundation -> foundations[pile.index]
        is Pile.Column -> columns[pile.index]
    }

    /** Only the top card of a pile moves. */
    fun canPickUp(from: Pile, index: Int): Boolean = index == cards(from).lastIndex && index >= 0

    fun canPlace(cards: List<Card>, to: Pile): Boolean {
        val card = cards.singleOrNull() ?: return false
        val top = cards(to).lastOrNull()
        return when (to) {
            is Pile.Foundation -> if (top == null) card.rank == 1 else card.suit == top.suit && card.rank == top.rank + 1
            is Pile.Column -> if (top == null) kingsOnEmpty && card.rank == 13 else card.rank == top.rank - 1
        }
    }

    fun move(from: Pile, index: Int, to: Pile): BakersDozenGame? {
        if (from == to || !canPickUp(from, index)) return null
        val cards = cards(from).drop(index)
        if (!canPlace(cards, to)) return null
        return with(from, cards(from).take(index)).with(to, cards(to) + cards).copy(moves = moves + 1)
    }

    /** Where a tap sends a top card: home first, then onto a column with cards, then an empty one. */
    fun autoTarget(from: Pile, index: Int): Pile? {
        if (!canPickUp(from, index)) return null
        val cards = cards(from).drop(index)
        if (from !is Pile.Foundation) foundationFor(cards)?.let { return it }
        val targets = columns.indices.map { Pile.Column(it) }.filter { it != from && canPlace(cards, it) }
        targets.firstOrNull { columns[it.index].isNotEmpty() }?.let { return it }
        // A king alone in its column gains nothing in another empty one.
        if (from is Pile.Column && index == 0) return null
        return targets.firstOrNull()
    }

    private fun foundationFor(cards: List<Card>): Pile? =
        (0 until 4).map { Pile.Foundation(it) }.firstOrNull { canPlace(cards, it) }

    /**
     * How many moves there are now that go somewhere useful: home, once per column a top card fits on,
     * and once for an empty column. Left out, as they gain nothing: a card that already sits on one a
     * rank higher onto another, unless the one under it can then go home, and a lone king to an empty
     * column.
     */
    val possibleMoves: Int
        get() {
            var count = 0
            columns.forEachIndexed { c, column ->
                val card = column.lastOrNull() ?: return@forEachIndexed
                if (foundationFor(listOf(card)) != null) count++
                val below = column.getOrNull(column.lastIndex - 1)
                if (below != null && card.rank == below.rank - 1 && foundationFor(listOf(below)) == null) return@forEachIndexed
                val targets = columns.indices.filter { it != c && canPlace(listOf(card), Pile.Column(it)) }
                count += targets.count { columns[it].isNotEmpty() }
                if (column.size > 1 && targets.any { columns[it].isEmpty() }) count++
            }
            return count
        }

    /** No moves left, and nothing to turn to: the game can not be won from here. */
    val isStuck: Boolean get() = !isWon && possibleMoves == 0

    /** Whether the rest can be played out on its own: every column in order, the lowest card always on top. */
    val canAutoComplete: Boolean
        get() = !isWon && columns.all { column -> (1..column.lastIndex).all { column[it].rank < column[it - 1].rank } }

    /** One step of playing out: the lowest card on top of a column goes to its foundation. */
    fun autoCompleteStep(): BakersDozenGame? {
        if (!canAutoComplete) return null
        val candidates = columns.indices.mapNotNull { c -> columns[c].lastOrNull()?.let { c to it } }.sortedBy { it.second.rank }
        for ((c, card) in candidates) {
            val target = foundationFor(listOf(card)) ?: continue
            return move(Pile.Column(c), columns[c].lastIndex, target)
        }
        return null
    }

    private fun with(pile: Pile, cards: List<Card>): BakersDozenGame = when (pile) {
        is Pile.Foundation -> copy(foundations = foundations.toMutableList().also { it[pile.index] = cards })
        is Pile.Column -> copy(columns = columns.toMutableList().also { it[pile.index] = cards })
    }

    fun toJson(): String = JSONObject()
        .put("kingsOnEmpty", kingsOnEmpty)
        .put("moves", moves)
        .put("foundations", JSONArray(foundations.map(::encode)))
        .put("columns", JSONArray(columns.map(::encode)))
        .toString()

    companion object {
        const val CLASSIC = "CLASSIC"
        const val PORTUGUESE = "PORTUGUESE"
        const val COLUMNS = 13

        fun variantOf(kingsOnEmpty: Boolean) = if (kingsOnEmpty) PORTUGUESE else CLASSIC

        fun kingsOnEmptyOf(variant: String) = variant == PORTUGUESE

        /** A shuffled deal: thirteen columns of four, face up, with any kings moved to the bottom of their column. */
        fun deal(kingsOnEmpty: Boolean, random: Random = Random.Default): BakersDozenGame {
            val deck = (0 until 52).shuffled(random).map { Card(it) }
            val columns = deck.chunked(4).map { column -> column.filter { it.rank == 13 } + column.filter { it.rank != 13 } }
            return BakersDozenGame(List(4) { emptyList() }, columns, kingsOnEmpty)
        }

        fun fromJson(json: String): BakersDozenGame {
            val o = JSONObject(json)
            fun piles(name: String) = o.getJSONArray(name).let { a -> List(a.length()) { decode(a.getJSONArray(it)) } }
            return BakersDozenGame(piles("foundations"), piles("columns"), o.getBoolean("kingsOnEmpty"), o.getInt("moves"))
        }

        /** A card as one number: its id plus one. */
        private fun encode(cards: List<Card>) = JSONArray(cards.map { it.id + 1 })

        private fun decode(array: JSONArray) = List(array.length()) { Card(array.getInt(it) - 1) }
    }
}
