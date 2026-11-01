package com.agustingv.puzzles.game.sliding

import com.agustingv.puzzles.game.Difficulty
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.random.Random

/**
 * Immutable board state. [tiles] holds the value per position in reading order; 0 is the blank.
 */
data class SlidingPuzzleGame(
    val tiles: List<Int>,
    val difficulty: Difficulty,
    val moves: Int = 0,
) {
    val size: Int = sizeFor(difficulty)

    val isSolved: Boolean =
        tiles.withIndex().all { (i, value) -> value == if (i == tiles.lastIndex) 0 else i + 1 }

    /** Slides the tile at [index] into the blank; returns this same state if the move is illegal. */
    fun move(index: Int): SlidingPuzzleGame {
        if (isSolved || index !in tiles.indices || tiles[index] == 0) return this

        val blank = tiles.indexOf(0)
        if (!areAdjacent(index, blank)) return this

        val next = tiles.toMutableList()
        next[blank] = tiles[index]
        next[index] = 0

        return copy(tiles = next, moves = moves + 1)
    }

    private fun areAdjacent(a: Int, b: Int): Boolean =
        abs(a / size - b / size) + abs(a % size - b % size) == 1

    fun toJson(): String = JSONObject()
        .put("tiles", JSONArray(tiles))
        .put("difficulty", difficulty.name)
        .put("moves", moves)
        .toString()

    companion object {
        private const val SHUFFLE_MULTIPLIER = 40

        fun fromJson(json: String): SlidingPuzzleGame {
            val root = JSONObject(json)
            val tiles = root.getJSONArray("tiles")

            return SlidingPuzzleGame(
                tiles = List(tiles.length()) { tiles.getInt(it) },
                difficulty = Difficulty.valueOf(root.getString("difficulty")),
                moves = root.getInt("moves"),
            )
        }

        fun sizeFor(difficulty: Difficulty): Int = when (difficulty) {
            Difficulty.EASY -> 3
            Difficulty.MEDIUM -> 4
            Difficulty.HARD -> 5
            Difficulty.EXPERT -> 6
        }

        /**
         * Starts from the solved board and performs many random legal slides, so the result is
         * always solvable — no parity checks needed.
         */
        fun generate(difficulty: Difficulty, random: Random = Random.Default): SlidingPuzzleGame {
            val size = sizeFor(difficulty)
            val total = size * size

            while (true) {
                val tiles = MutableList(total) { i -> if (i == total - 1) 0 else i + 1 }
                var blank = total - 1
                var lastBlank = -1

                repeat(total * SHUFFLE_MULTIPLIER) {
                    val next = neighbors(blank, size).filter { it != lastBlank }.random(random)
                    tiles[blank] = tiles[next]
                    tiles[next] = 0
                    lastBlank = blank
                    blank = next
                }

                val game = SlidingPuzzleGame(tiles, difficulty)
                if (!game.isSolved) return game
            }
        }

        private fun neighbors(index: Int, size: Int): List<Int> = buildList {
            val row = index / size
            val col = index % size
            if (row > 0) add(index - size)
            if (row < size - 1) add(index + size)
            if (col > 0) add(index - 1)
            if (col < size - 1) add(index + 1)
        }
    }
}
