package com.butaca13.puzzles.game.cards

enum class Suit(val red: Boolean) { SPADES(false), HEARTS(true), DIAMONDS(true), CLUBS(false) }

/** One of the 52 cards: [id] says which, 13 per suit in [Suit]'s order. [rank] 1 is the ace, 13 the king. */
data class Card(val id: Int, val faceUp: Boolean = true) {
    val suit: Suit get() = Suit.entries[id / 13]
    val rank: Int get() = id % 13 + 1

    companion object {
        fun of(suit: Suit, rank: Int, faceUp: Boolean = true) = Card(suit.ordinal * 13 + rank - 1, faceUp)
    }
}

/** The columns' rule in most patiences: one rank lower, the other colour. */
fun Card.stacksOn(onto: Card) = onto.faceUp && rank == onto.rank - 1 && suit.red != onto.suit.red
