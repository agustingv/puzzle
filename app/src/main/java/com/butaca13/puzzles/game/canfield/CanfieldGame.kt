package com.butaca13.puzzles.game.canfield

import com.butaca13.puzzles.game.cards.Card
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.random.Random

/** Where cards lie: the stock, the waste, the reserve, the four foundations and the four columns. */
sealed interface Pile {
    data object Stock : Pile
    data object Waste : Pile
    data object Reserve : Pile
    data class Foundation(val index: Int) : Pile
    data class Column(val index: Int) : Pile
}

/**
 * Canfield: a reserve of thirteen cards to empty, four columns and four foundations. The foundations
 * start from the rank of the first card dealt to them, [base], and build up by suit, round from king to
 * ace. The columns build down in alternating colours, round from ace to king too, and a column left
 * empty is filled at once from the reserve, or by the player from the waste once the reserve is gone.
 * The stock turns over [drawCount] cards at a time and can be gone through again and again. Each action
 * gives a new game, or null when it is not allowed, so that earlier ones can be kept to undo.
 */
data class CanfieldGame(
    val stock: List<Card>,
    val waste: List<Card>,
    val reserve: List<Card>,
    val foundations: List<List<Card>>,
    /** Every column bottom first, so the last card is on top. */
    val columns: List<List<Card>>,
    /** The rank every foundation starts from. */
    val base: Int,
    val drawCount: Int,
    val moves: Int = 0,
) {
    val variant: String get() = variantOf(drawCount)

    val isWon: Boolean get() = foundations.all { it.size == 13 }

    fun cards(pile: Pile): List<Card> = when (pile) {
        Pile.Stock -> stock
        Pile.Waste -> waste
        Pile.Reserve -> reserve
        is Pile.Foundation -> foundations[pile.index]
        is Pile.Column -> columns[pile.index]
    }

    /** How far [rank] is above the base, round from king to ace: 0 for the base itself, 12 for the last. */
    fun relative(rank: Int) = (rank - base + 13) % 13

    /** Whether [card] goes on [onto] in a column: one rank lower, round from ace to king, of the other colour. */
    private fun stacks(card: Card, onto: Card) = card.suit.red != onto.suit.red && (onto.rank - card.rank + 13) % 13 == 1

    fun canPickUp(from: Pile, index: Int): Boolean {
        val cards = cards(from)
        if (index !in cards.indices) return false
        return when (from) {
            Pile.Stock -> false
            Pile.Waste, Pile.Reserve, is Pile.Foundation -> index == cards.lastIndex
            is Pile.Column -> (index + 1..cards.lastIndex).all { stacks(cards[it], cards[it - 1]) }
        }
    }

    fun canPlace(cards: List<Card>, to: Pile): Boolean {
        val first = cards.firstOrNull() ?: return false
        val top = cards(to).lastOrNull()
        return when (to) {
            Pile.Stock, Pile.Waste, Pile.Reserve -> false
            is Pile.Foundation -> cards.size == 1 &&
                if (top == null) first.rank == base else first.suit == top.suit && relative(first.rank) == relative(top.rank) + 1
            // An empty column waits for the reserve while there is one.
            is Pile.Column -> if (top == null) reserve.isEmpty() else stacks(first, top)
        }
    }

    fun move(from: Pile, index: Int, to: Pile): CanfieldGame? {
        if (from == to || !canPickUp(from, index)) return null
        val cards = cards(from).drop(index)
        if (!canPlace(cards, to)) return null
        return with(from, cards(from).take(index)).with(to, cards(to) + cards).copy(moves = moves + 1).fillFromReserve()
    }

    /** Turns over the next cards of the stock, or the waste back into the stock once it is empty. */
    fun draw(): CanfieldGame? {
        if (stock.isEmpty() && waste.isEmpty()) return null
        if (stock.isEmpty()) {
            return copy(stock = waste.reversed().map { it.copy(faceUp = false) }, waste = emptyList(), moves = moves + 1)
        }
        val turned = stock.takeLast(drawCount).reversed().map { it.copy(faceUp = true) }
        return copy(stock = stock.dropLast(turned.size), waste = waste + turned, moves = moves + 1)
    }

    /** Every empty column takes the reserve's top card, while there is one. */
    private fun fillFromReserve(): CanfieldGame {
        var game = this
        for (c in columns.indices) {
            if (game.columns[c].isNotEmpty() || game.reserve.isEmpty()) continue
            val card = game.reserve.last()
            game = game.copy(
                reserve = game.reserve.dropLast(1).let { left -> left.dropLast(1) + listOfNotNull(left.lastOrNull()?.copy(faceUp = true)) },
                columns = game.columns.toMutableList().also { it[c] = listOf(card) },
            )
        }
        return game
    }

    /** Where a tap sends the cards from [index] up: a foundation first, then a column with cards, then an empty one. */
    fun autoTarget(from: Pile, index: Int): Pile? {
        if (!canPickUp(from, index)) return null
        val cards = cards(from).drop(index)
        if (from !is Pile.Foundation) foundationFor(cards)?.let { return it }
        val targets = columns.indices.map { Pile.Column(it) }.filter { it != from && canPlace(cards, it) }
        targets.firstOrNull { columns[it.index].isNotEmpty() }?.let { return it }
        if (from is Pile.Column && index == 0) return null
        return targets.firstOrNull()
    }

    private fun foundationFor(cards: List<Card>): Pile? =
        (0 until 4).map { Pile.Foundation(it) }.firstOrNull { canPlace(cards, it) }

    /**
     * How many moves there are now that go somewhere useful, not counting a draw: home, once per column a
     * card or run fits on, and once for an empty column. Left out, as they gain nothing: a whole column
     * to an empty one, a run that already sits on a card it stacks on, unless that card can then go
     * home, and cards taken back off the foundations.
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
                    if (stacks(cards.first(), below) && foundationFor(listOf(below)) == null) return
                }
                val targets = columns.indices.filter { it != (from as? Pile.Column)?.index && canPlace(cards, Pile.Column(it)) }
                count += targets.count { columns[it].isNotEmpty() }
                if (targets.any { columns[it].isEmpty() } && !(from is Pile.Column && index == 0)) count++
            }
            if (reserve.isNotEmpty()) countFrom(Pile.Reserve, reserve.lastIndex)
            if (waste.isNotEmpty()) countFrom(Pile.Waste, waste.lastIndex)
            columns.forEachIndexed { c, column -> column.indices.forEach { countFrom(Pile.Column(c), it) } }
            return count
        }

    /** Whether going through the stock, without playing anything else, would turn up a card that can be played. */
    val drawingHelps: Boolean
        get() {
            var game = this
            repeat(stock.size + waste.size + 2) {
                game = game.draw() ?: return false
                val top = game.waste.lastOrNull() ?: return@repeat
                if (game.foundationFor(listOf(top)) != null || game.columns.indices.any { game.canPlace(listOf(top), Pile.Column(it)) }) return true
            }
            return false
        }

    /** No moves left, nor any to be found in the stock: the game can not be won from here. */
    val isStuck: Boolean get() = !isWon && possibleMoves == 0 && !drawingHelps

    /** Whether the rest can be played out on its own: everything left in the columns, which are always in order. */
    val canAutoComplete: Boolean
        get() = !isWon && reserve.isEmpty() && stock.isEmpty() && waste.isEmpty()

    /** One step of playing out: the lowest card, counted from the base, on top of a column goes home. */
    fun autoCompleteStep(): CanfieldGame? {
        if (!canAutoComplete) return null
        val candidates = columns.indices.mapNotNull { c -> columns[c].lastOrNull()?.let { c to it } }.sortedBy { relative(it.second.rank) }
        for ((c, card) in candidates) {
            val target = foundationFor(listOf(card)) ?: continue
            return move(Pile.Column(c), columns[c].lastIndex, target)
        }
        return null
    }

    private fun with(pile: Pile, cards: List<Card>): CanfieldGame = when (pile) {
        Pile.Stock -> copy(stock = cards)
        Pile.Waste -> copy(waste = cards)
        // The reserve's new top card turns over.
        Pile.Reserve -> copy(reserve = cards.dropLast(1) + listOfNotNull(cards.lastOrNull()?.copy(faceUp = true)))
        is Pile.Foundation -> copy(foundations = foundations.toMutableList().also { it[pile.index] = cards })
        is Pile.Column -> copy(columns = columns.toMutableList().also { it[pile.index] = cards })
    }

    fun toJson(): String = JSONObject()
        .put("draw", drawCount)
        .put("base", base)
        .put("moves", moves)
        .put("stock", encode(stock))
        .put("waste", encode(waste))
        .put("reserve", encode(reserve))
        .put("foundations", JSONArray(foundations.map(::encode)))
        .put("columns", JSONArray(columns.map(::encode)))
        .toString()

    companion object {
        /** The same as the patience's, so that they read the same: one card a draw, or three. */
        const val DRAW_ONE = "DRAW1"
        const val DRAW_THREE = "DRAW3"

        fun variantOf(drawCount: Int) = if (drawCount == 3) DRAW_THREE else DRAW_ONE

        fun drawCountOf(variant: String) = if (variant == DRAW_THREE) 3 else 1

        /**
         * A shuffled deal: thirteen cards to the reserve, only its top face up; the next to the first
         * foundation, setting the base rank; one face up to each column; the other 34 in the stock.
         */
        fun deal(drawCount: Int, random: Random = Random.Default): CanfieldGame {
            val deck = (0 until 52).shuffled(random).toMutableList()
            fun next() = deck.removeAt(deck.lastIndex)
            val reserve = List(13) { i -> Card(next(), faceUp = i == 12) }
            val first = Card(next())
            val columns = List(4) { listOf(Card(next())) }
            return CanfieldGame(
                stock = deck.map { Card(it, faceUp = false) },
                waste = emptyList(),
                reserve = reserve,
                foundations = listOf(listOf(first)) + List(3) { emptyList() },
                columns = columns,
                base = first.rank,
                drawCount = drawCount,
            )
        }

        fun fromJson(json: String): CanfieldGame {
            val o = JSONObject(json)
            fun piles(name: String) = o.getJSONArray(name).let { a -> List(a.length()) { decode(a.getJSONArray(it)) } }
            return CanfieldGame(
                stock = decode(o.getJSONArray("stock")),
                waste = decode(o.getJSONArray("waste")),
                reserve = decode(o.getJSONArray("reserve")),
                foundations = piles("foundations"),
                columns = piles("columns"),
                base = o.getInt("base"),
                drawCount = o.getInt("draw"),
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
