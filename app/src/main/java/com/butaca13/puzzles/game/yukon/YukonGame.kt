package com.butaca13.puzzles.game.yukon

import com.butaca13.puzzles.game.cards.Card
import com.butaca13.puzzles.game.cards.stacksOn
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.random.Random

/** Where cards lie: the four foundations and the seven columns. */
sealed interface Pile {
    data class Foundation(val index: Int) : Pile
    data class Column(val index: Int) : Pile
}

/**
 * Yukon: the patience's columns, but every card dealt into them and no stock. Any face-up card moves
 * with all those on top of it, in order or not, onto a card a rank higher of the other colour, or of
 * its own suit in the Russian game, [sameSuit]; only a king goes to an empty column. The foundations
 * build up by suit from the ace. Each action gives a new game, or null when it is not allowed, so that
 * earlier ones can be kept to undo.
 */
data class YukonGame(
    val foundations: List<List<Card>>,
    /** Every column bottom first, so the last card is on top. */
    val columns: List<List<Card>>,
    val sameSuit: Boolean,
    val moves: Int = 0,
) {
    val variant: String get() = variantOf(sameSuit)

    val isWon: Boolean get() = foundations.all { it.size == 13 }

    fun cards(pile: Pile): List<Card> = when (pile) {
        is Pile.Foundation -> foundations[pile.index]
        is Pile.Column -> columns[pile.index]
    }

    /** Whether [card] goes on [onto] in a column: one rank lower, of the other colour or the same suit. */
    private fun fits(card: Card, onto: Card): Boolean =
        if (sameSuit) onto.faceUp && card.suit == onto.suit && card.rank == onto.rank - 1 else card.stacksOn(onto)

    /** Whether the cards from [index] up in [from] can be picked up: any face-up card, with all on top of it. */
    fun canPickUp(from: Pile, index: Int): Boolean {
        val cards = cards(from)
        if (index !in cards.indices || !cards[index].faceUp) return false
        return from is Pile.Column || index == cards.lastIndex
    }

    fun canPlace(cards: List<Card>, to: Pile): Boolean {
        val first = cards.firstOrNull() ?: return false
        val top = cards(to).lastOrNull()
        return when (to) {
            is Pile.Foundation -> cards.size == 1 &&
                if (top == null) first.rank == 1 else first.suit == top.suit && first.rank == top.rank + 1
            is Pile.Column -> if (top == null) first.rank == 13 else fits(first, top)
        }
    }

    /** Moves the cards from [index] up in [from] onto [to], turning over the card they leave on top. */
    fun move(from: Pile, index: Int, to: Pile): YukonGame? {
        if (from == to || !canPickUp(from, index)) return null
        val cards = cards(from).drop(index)
        if (!canPlace(cards, to)) return null
        val left = cards(from).take(index).toMutableList()
        left.lastOrNull()?.let { if (!it.faceUp) left[left.lastIndex] = it.copy(faceUp = true) }
        return with(from, left).with(to, cards(to) + cards).copy(moves = moves + 1)
    }

    /** Where a tap sends the cards from [index] up: a foundation first, then a column with cards, then an empty one. */
    fun autoTarget(from: Pile, index: Int): Pile? {
        if (!canPickUp(from, index)) return null
        val cards = cards(from).drop(index)
        if (from !is Pile.Foundation) foundationFor(cards)?.let { return it }
        val targets = columns.indices.map { Pile.Column(it) }.filter { it != from && canPlace(cards, it) }
        targets.firstOrNull { columns[it.index].isNotEmpty() }?.let { return it }
        // A king already at the bottom of its column gains nothing in another empty one.
        if (from is Pile.Column && index == 0) return null
        return targets.firstOrNull()
    }

    private fun foundationFor(cards: List<Card>): Pile? =
        (0 until 4).map { Pile.Foundation(it) }.firstOrNull { canPlace(cards, it) }

    /**
     * How many moves there are now that go somewhere useful: home, once per column a card fits on, and
     * once for any empty column. Left out, as they gain nothing: a king already at the bottom of its
     * column to another empty one, a card that already sits on one it fits on, unless that one can then
     * go home, and cards taken back off the foundations.
     */
    val possibleMoves: Int
        get() {
            var count = 0
            columns.forEachIndexed { c, column ->
                column.indices.forEach { index ->
                    if (!canPickUp(Pile.Column(c), index)) return@forEach
                    val cards = column.drop(index)
                    if (foundationFor(cards) != null) count++
                    val below = column.getOrNull(index - 1)
                    if (below != null && fits(cards.first(), below) && foundationFor(listOf(below)) == null) return@forEach
                    val targets = columns.indices.filter { it != c && canPlace(cards, Pile.Column(it)) }
                    count += targets.count { columns[it].isNotEmpty() }
                    if (index > 0 && targets.any { columns[it].isEmpty() }) count++
                }
            }
            return count
        }

    /** No moves left, and no stock to turn to: the game can not be won from here. */
    val isStuck: Boolean get() = !isWon && possibleMoves == 0

    /**
     * Whether the rest can be played out on its own: every card face up and every column in order, each
     * card lower than the one under it, so that the lowest card left is always on top of one.
     */
    val canAutoComplete: Boolean
        get() = !isWon && columns.all { column ->
            column.all { it.faceUp } && (1..column.lastIndex).all { column[it].rank < column[it - 1].rank }
        }

    /** One step of playing out: the lowest card on top of a column goes to its foundation. */
    fun autoCompleteStep(): YukonGame? {
        if (!canAutoComplete) return null
        val candidates = columns.indices.mapNotNull { c -> columns[c].lastOrNull()?.let { c to it } }.sortedBy { it.second.rank }
        for ((c, card) in candidates) {
            val target = foundationFor(listOf(card)) ?: continue
            return move(Pile.Column(c), columns[c].lastIndex, target)
        }
        return null
    }

    private fun with(pile: Pile, cards: List<Card>): YukonGame = when (pile) {
        is Pile.Foundation -> copy(foundations = foundations.toMutableList().also { it[pile.index] = cards })
        is Pile.Column -> copy(columns = columns.toMutableList().also { it[pile.index] = cards })
    }

    fun toJson(): String = JSONObject()
        .put("sameSuit", sameSuit)
        .put("moves", moves)
        .put("foundations", JSONArray(foundations.map(::encode)))
        .put("columns", JSONArray(columns.map(::encode)))
        .toString()

    companion object {
        const val CLASSIC = "CLASSIC"
        const val RUSSIAN = "RUSSIAN"

        fun variantOf(sameSuit: Boolean) = if (sameSuit) RUSSIAN else CLASSIC

        fun sameSuitOf(variant: String) = variant == RUSSIAN

        /**
         * A shuffled deal of all 52 cards: one face up in the first column, and in each of the others one
         * more face down than in the last, under five face up.
         */
        fun deal(sameSuit: Boolean, random: Random = Random.Default): YukonGame {
            val deck = (0 until 52).shuffled(random).toMutableList()
            val columns = List(7) { c ->
                val down = c
                val up = if (c == 0) 1 else 5
                List(down) { Card(deck.removeAt(deck.lastIndex), faceUp = false) } + List(up) { Card(deck.removeAt(deck.lastIndex)) }
            }
            return YukonGame(List(4) { emptyList() }, columns, sameSuit)
        }

        fun fromJson(json: String): YukonGame {
            val o = JSONObject(json)
            fun piles(name: String) = o.getJSONArray(name).let { a -> List(a.length()) { decode(a.getJSONArray(it)) } }
            return YukonGame(piles("foundations"), piles("columns"), o.getBoolean("sameSuit"), o.getInt("moves"))
        }

        /** A card as one number: its id plus one, negative when face down. */
        private fun encode(cards: List<Card>) = JSONArray(cards.map { if (it.faceUp) it.id + 1 else -(it.id + 1) })

        private fun decode(array: JSONArray) = List(array.length()) {
            val n = array.getInt(it)
            Card(abs(n) - 1, faceUp = n > 0)
        }
    }
}
