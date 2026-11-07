package com.butaca13.puzzles.game.klondike

import com.butaca13.puzzles.game.cards.Card
import com.butaca13.puzzles.game.cards.stacksOn
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/** Where cards lie: the stock, the waste beside it, the four foundations and the seven columns. */
sealed interface Pile {
    data object Stock : Pile
    data object Waste : Pile
    data class Foundation(val index: Int) : Pile
    data class Tableau(val index: Int) : Pile
}

/**
 * Klondike, the classic patience. Every pile lists its cards bottom first, so the last one is on top.
 * Each action gives a new game, or null when it is not allowed, so that earlier ones can be kept to
 * undo. Ported from the Tauri Klondike's rules (projects/tauri/Klondike/src/game.ts), without its
 * score: here, as in every game, a win is ranked by its time.
 */
data class KlondikeGame(
    val stock: List<Card>,
    val waste: List<Card>,
    val foundations: List<List<Card>>,
    val tableau: List<List<Card>>,
    /** How many cards each draw turns over: 1 or 3. */
    val drawCount: Int,
    val moves: Int = 0,
) {
    val variant: String get() = variantOf(drawCount)

    val isWon: Boolean get() = foundations.all { it.size == 13 }

    fun pile(pile: Pile): List<Card> = when (pile) {
        Pile.Stock -> stock
        Pile.Waste -> waste
        is Pile.Foundation -> foundations[pile.index]
        is Pile.Tableau -> tableau[pile.index]
    }

    /** Whether the cards from [index] to the top of [from] can be picked up together. */
    fun canPickUp(from: Pile, index: Int): Boolean {
        val cards = pile(from)
        if (index !in cards.indices || !cards[index].faceUp) return false
        return when (from) {
            Pile.Stock -> false
            Pile.Waste, is Pile.Foundation -> index == cards.lastIndex
            is Pile.Tableau -> (index + 1..cards.lastIndex).all { cards[it].stacksOn(cards[it - 1]) }
        }
    }

    fun canPlace(cards: List<Card>, to: Pile): Boolean {
        val first = cards.firstOrNull() ?: return false
        val top = pile(to).lastOrNull()
        return when (to) {
            Pile.Stock, Pile.Waste -> false
            is Pile.Foundation -> cards.size == 1 &&
                if (top == null) first.rank == 1 else first.suit == top.suit && first.rank == top.rank + 1
            is Pile.Tableau -> if (top == null) first.rank == 13 else first.stacksOn(top)
        }
    }

    /** Moves the cards from [index] up in [from] onto [to], turning over the card they leave on top. */
    fun move(from: Pile, index: Int, to: Pile): KlondikeGame? {
        if (from == to || !canPickUp(from, index)) return null
        val cards = pile(from).drop(index)
        if (!canPlace(cards, to)) return null

        val left = pile(from).take(index).toMutableList()
        left.lastOrNull()?.let { if (from is Pile.Tableau && !it.faceUp) left[left.lastIndex] = it.copy(faceUp = true) }
        return with(from, left).with(to, pile(to) + cards).copy(moves = moves + 1)
    }

    /** Turns over the next cards of the stock, or the waste back into the stock once it is empty. */
    fun draw(): KlondikeGame? {
        if (stock.isEmpty() && waste.isEmpty()) return null
        if (stock.isEmpty()) {
            return copy(stock = waste.reversed().map { it.copy(faceUp = false) }, waste = emptyList(), moves = moves + 1)
        }
        val turned = stock.takeLast(drawCount).reversed().map { it.copy(faceUp = true) }
        return copy(stock = stock.dropLast(turned.size), waste = waste + turned, moves = moves + 1)
    }

    /** Where a tap on a card sends it: a foundation first, then a column with cards, then an empty one. */
    fun autoTarget(from: Pile, index: Int): Pile? {
        if (!canPickUp(from, index)) return null
        val cards = pile(from).drop(index)
        if (from !is Pile.Foundation) {
            (0 until 4).map { Pile.Foundation(it) }.firstOrNull { canPlace(cards, it) }?.let { return it }
        }
        val columns = (0 until 7).map { Pile.Tableau(it) }.filter { it != from && canPlace(cards, it) }
        columns.firstOrNull { tableau[it.index].isNotEmpty() }?.let { return it }
        // A king already at the bottom of its column gains nothing in another empty one.
        if (from is Pile.Tableau && index == 0) return null
        return columns.firstOrNull()
    }

    /**
     * How many moves there are now, not counting drawing from the stock: each card or run that can go
     * somewhere useful, once per column it fits on. A card that fits on any empty foundation or any empty
     * column counts once for all of them, as they are the same move. Left out, as they gain nothing: a
     * king already at the bottom of its column to another empty one, a run that leaves on top a face-up
     * card it already sat on, unless that card can then go home, and cards taken back off the foundations.
     */
    val possibleMoves: Int
        get() {
            var count = 0
            fun countFrom(from: Pile, index: Int) {
                if (!canPickUp(from, index)) return
                val cards = pile(from).drop(index)
                if ((0 until 4).any { canPlace(cards, Pile.Foundation(it)) }) count++
                val columns = (0 until 7).filter { it != (from as? Pile.Tableau)?.index && canPlace(cards, Pile.Tableau(it)) }
                if (from is Pile.Tableau && index > 0) {
                    val below = tableau[from.index][index - 1]
                    if (below.faceUp && (0 until 4).none { canPlace(listOf(below), Pile.Foundation(it)) }) return
                }
                count += columns.count { tableau[it].isNotEmpty() }
                if (columns.any { tableau[it].isEmpty() } && !(from is Pile.Tableau && index == 0)) count++
            }
            if (waste.isNotEmpty()) countFrom(Pile.Waste, waste.lastIndex)
            tableau.forEachIndexed { i, column -> column.indices.forEach { countFrom(Pile.Tableau(i), it) } }
            return count
        }

    /**
     * Whether going through the stock, without playing anything else, would turn up a card that can be
     * played: through the stock and the waste once round, as each draw leaves them.
     */
    val drawingHelps: Boolean
        get() {
            var game = this
            repeat(stock.size + waste.size + 2) {
                game = game.draw() ?: return false
                if (game.waste.isNotEmpty() && game.possibleMovesOf(Pile.Waste) > 0) return true
            }
            return false
        }

    /** No moves left, nor any to be found in the stock: the game can not be won from here. */
    val isStuck: Boolean get() = !isWon && possibleMoves == 0 && !drawingHelps

    private fun possibleMovesOf(from: Pile): Int {
        val index = pile(from).lastIndex
        if (!canPickUp(from, index)) return 0
        val cards = pile(from).drop(index)
        return (0 until 4).count { canPlace(cards, Pile.Foundation(it)) }.coerceAtMost(1) +
            (0 until 7).count { canPlace(cards, Pile.Tableau(it)) }
    }

    /**
     * Whether the rest can be played out on its own: every card in the columns face up, and the stock
     * free to go through, as always with one card a draw and with three only once it is used up.
     */
    val canAutoComplete: Boolean
        get() = !isWon && tableau.all { column -> column.all { it.faceUp } } && (drawCount == 1 || stock.isEmpty() && waste.isEmpty())

    /** One step of playing out: the lowest card that can go to a foundation goes there. */
    fun autoCompleteStep(): KlondikeGame? {
        if (!canAutoComplete) return null
        val candidates = buildList {
            tableau.forEachIndexed { i, column -> if (column.isNotEmpty()) add(Triple(Pile.Tableau(i) as Pile, column.lastIndex, column.last())) }
            waste.forEachIndexed { i, card -> add(Triple(Pile.Waste, i, card)) }
            stock.forEachIndexed { i, card -> add(Triple(Pile.Stock, i, card)) }
        }.sortedBy { it.third.rank }

        for ((from, index, card) in candidates) {
            val target = (0 until 4).map { Pile.Foundation(it) }.firstOrNull { canPlace(listOf(card), it) } ?: continue
            val left = pile(from).toMutableList().apply { removeAt(index) }
            return with(from, left).with(target, pile(target) + card.copy(faceUp = true)).copy(moves = moves + 1)
        }
        return null
    }

    private fun with(pile: Pile, cards: List<Card>): KlondikeGame = when (pile) {
        Pile.Stock -> copy(stock = cards)
        Pile.Waste -> copy(waste = cards)
        is Pile.Foundation -> copy(foundations = foundations.toMutableList().also { it[pile.index] = cards })
        is Pile.Tableau -> copy(tableau = tableau.toMutableList().also { it[pile.index] = cards })
    }

    fun toJson(): String = JSONObject()
        .put("draw", drawCount)
        .put("moves", moves)
        .put("stock", encode(stock))
        .put("waste", encode(waste))
        .put("foundations", JSONArray(foundations.map(::encode)))
        .put("tableau", JSONArray(tableau.map(::encode)))
        .toString()

    companion object {
        const val DRAW_ONE = "DRAW1"
        const val DRAW_THREE = "DRAW3"

        fun variantOf(drawCount: Int) = if (drawCount == 3) DRAW_THREE else DRAW_ONE

        fun drawCountOf(variant: String) = if (variant == DRAW_THREE) 3 else 1

        /** A shuffled deal: one card more in each column, left to right, with only the top ones face up. */
        fun deal(drawCount: Int, random: Random = Random.Default): KlondikeGame {
            val deck = (0 until 52).shuffled(random).map { Card(it, faceUp = false) }.toMutableList()
            val tableau = List(7) { mutableListOf<Card>() }
            for (row in 0 until 7) for (col in row until 7) tableau[col].add(deck.removeAt(deck.lastIndex))
            for (column in tableau) column[column.lastIndex] = column.last().copy(faceUp = true)
            return KlondikeGame(deck, emptyList(), List(4) { emptyList() }, tableau, drawCount)
        }

        fun fromJson(json: String): KlondikeGame {
            val o = JSONObject(json)
            fun piles(name: String) = o.getJSONArray(name).let { a -> List(a.length()) { decode(a.getJSONArray(it)) } }
            return KlondikeGame(
                stock = decode(o.getJSONArray("stock")),
                waste = decode(o.getJSONArray("waste")),
                foundations = piles("foundations"),
                tableau = piles("tableau"),
                drawCount = o.getInt("draw"),
                moves = o.getInt("moves"),
            )
        }

        /** A card as one number: its id plus one, negative when face down. */
        private fun encode(cards: List<Card>) = JSONArray(cards.map { if (it.faceUp) it.id + 1 else -(it.id + 1) })

        private fun decode(array: JSONArray) = List(array.length()) {
            val n = array.getInt(it)
            Card(kotlin.math.abs(n) - 1, faceUp = n > 0)
        }
    }
}
