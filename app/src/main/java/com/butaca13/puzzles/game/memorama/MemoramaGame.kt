package com.butaca13.puzzles.game.memorama

import com.butaca13.puzzles.game.Difficulty
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * Pairs of cards face down. A mismatched pair stays face up until the next card is turned, which
 * hides it. [cards] holds the symbol per position; [flipped] the 0 to 2 unresolved face-up cards.
 */
data class MemoramaGame(
    val cards: List<String>,
    val difficulty: Difficulty,
    val matched: Set<Int> = emptySet(),
    val flipped: List<Int> = emptyList(),
    val moves: Int = 0,
) {
    val columns: Int = columnsFor(difficulty)
    val rows: Int = cards.size / columns
    val pairs: Int = cards.size / 2
    val matchedPairs: Int = matched.size / 2
    val isWon: Boolean = matched.size == cards.size

    fun isFaceUp(index: Int): Boolean = index in matched || index in flipped

    fun flip(index: Int): MemoramaGame {
        if (isWon || index !in cards.indices) return this

        // Turning a card first hides a mismatched pair left over from the previous turn.
        val hidden = if (flipped.size >= 2) copy(flipped = emptyList()) else this
        if (index in hidden.matched || index in hidden.flipped) return hidden

        val up = hidden.flipped + index
        if (up.size < 2) return hidden.copy(flipped = up)

        val (a, b) = up
        return if (cards[a] == cards[b]) {
            hidden.copy(matched = hidden.matched + a + b, flipped = emptyList(), moves = moves + 1)
        } else {
            hidden.copy(flipped = up, moves = moves + 1)
        }
    }

    fun toJson(): String = JSONObject()
        .put("cards", JSONArray(cards))
        .put("difficulty", difficulty.name)
        .put("matched", JSONArray(matched.toList()))
        .put("flipped", JSONArray(flipped))
        .put("moves", moves)
        .toString()

    companion object {
        private val EMOJI = listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯",
            "🦁", "🐮", "🐷", "🐸", "🐵", "🍎", "🍌", "🍇", "🍉", "🍒",
            "🍕", "🍔", "🍩", "🍪", "🎈", "⚽", "🚗", "🎵", "⭐", "🌈",
        )

        private fun pairsFor(difficulty: Difficulty) = when (difficulty) {
            Difficulty.EASY -> 6
            Difficulty.MEDIUM -> 8
            Difficulty.HARD -> 12
            Difficulty.EXPERT -> 15
        }

        private fun columnsFor(difficulty: Difficulty) = when (difficulty) {
            Difficulty.EASY, Difficulty.MEDIUM -> 4
            Difficulty.HARD, Difficulty.EXPERT -> 6
        }

        fun generate(difficulty: Difficulty, random: Random = Random.Default): MemoramaGame {
            val symbols = EMOJI.shuffled(random).take(pairsFor(difficulty))
            return MemoramaGame((symbols + symbols).shuffled(random), difficulty)
        }

        fun fromJson(json: String): MemoramaGame {
            val root = JSONObject(json)
            val cards = root.getJSONArray("cards")

            fun ints(array: JSONArray) = List(array.length()) { array.getInt(it) }

            return MemoramaGame(
                cards = List(cards.length()) { cards.getString(it) },
                difficulty = Difficulty.valueOf(root.getString("difficulty")),
                matched = ints(root.getJSONArray("matched")).toSet(),
                flipped = ints(root.getJSONArray("flipped")),
                moves = root.getInt("moves"),
            )
        }
    }
}
