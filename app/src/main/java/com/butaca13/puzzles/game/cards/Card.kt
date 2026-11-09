package com.butaca13.puzzles.game.cards

enum class Suit(val red: Boolean) { SPADES(false), HEARTS(true), DIAMONDS(true), CLUBS(false) }

/**
 * One card: [id] says which, 13 per suit in [Suit]'s order, so 0 to 51 for one deck. Games that play
 * with several copies of a card give each copy its own id 52 further on, so that every
 * card on the table is still told apart. [rank] 1 is the ace, 13 the king.
 */
data class Card(val id: Int, val faceUp: Boolean = true) {
    val suit: Suit get() = Suit.entries[id / 13 % 4]
    val rank: Int get() = id % 13 + 1

    companion object {
        /** The card of [suit] and [rank], its [copy]-th one where a game has several. */
        fun of(suit: Suit, rank: Int, faceUp: Boolean = true, copy: Int = 0) = Card(copy * 52 + suit.ordinal * 13 + rank - 1, faceUp)
    }
}

/** The columns' rule in most patiences: one rank lower, the other colour. */
fun Card.stacksOn(onto: Card) = onto.faceUp && rank == onto.rank - 1 && suit.red != onto.suit.red
