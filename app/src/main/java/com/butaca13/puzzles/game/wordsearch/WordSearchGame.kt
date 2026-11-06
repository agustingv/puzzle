package com.butaca13.puzzles.game.wordsearch

import com.butaca13.puzzles.game.Difficulty
import com.butaca13.puzzles.game.foldAccents
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlin.random.nextInt

/** [cells] are board indexes in reading order, from the word's first letter to its last. */
data class PlacedWord(val word: String, val cells: List<Int>, val found: Boolean = false)

/**
 * [grid] has one folded letter per cell. Unlike the web version accents are not kept on the board:
 * filler letters never have them, so an "á" would give its word away at a glance.
 */
data class WordSearchGame(
    val size: Int,
    val difficulty: Difficulty,
    val grid: String,
    val words: List<PlacedWord>,
    val pendingStart: Int? = null,
) {
    val foundCount: Int = words.count { it.found }
    val isComplete: Boolean = foundCount == words.size

    /** Marks the word running from [start] to [end], read in either direction, as found. */
    fun select(start: Int, end: Int): WordSearchGame {
        val base = copy(pendingStart = null)
        val path = pathBetween(start, end, size) ?: return base
        val index = words.indexOfFirst {
            !it.found && (it.cells == path || it.cells == path.reversed())
        }
        if (index < 0) return base

        return base.copy(words = words.toMutableList().also { it[index] = it[index].copy(found = true) })
    }

    /** Two-tap selection: the first tap marks the start, the second resolves it (or cancels it). */
    fun tap(cell: Int): WordSearchGame = when (pendingStart) {
        null -> copy(pendingStart = cell)
        cell -> copy(pendingStart = null)
        else -> select(pendingStart, cell)
    }

    fun toJson(): String = JSONObject()
        .put("size", size)
        .put("difficulty", difficulty.name)
        .put("grid", grid)
        .put("pendingStart", pendingStart ?: -1)
        .put(
            "words",
            JSONArray(
                words.map {
                    JSONObject()
                        .put("word", it.word)
                        .put("cells", JSONArray(it.cells))
                        .put("found", it.found)
                },
            ),
        )
        .toString()

    companion object {
        private const val ATTEMPTS_PER_WORD = 200
        private const val ALPHABET = "abcdefghijklmnñopqrstuvwxyz"
        private val DIRECTIONS = listOf(
            1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, 1 to -1, -1 to 1, -1 to -1,
        )

        private data class Settings(val size: Int, val words: Int)

        private fun settings(difficulty: Difficulty) = when (difficulty) {
            Difficulty.EASY -> Settings(8, 7)
            Difficulty.MEDIUM -> Settings(10, 10)
            Difficulty.HARD -> Settings(12, 12)
            Difficulty.EXPERT -> Settings(15, 15)
        }

        fun generate(difficulty: Difficulty, pool: List<String>, random: Random = Random.Default): WordSearchGame {
            val settings = settings(difficulty)
            val size = settings.size
            val grid = arrayOfNulls<Char>(size * size)
            val placed = mutableListOf<PlacedWord>()

            for (word in pool.shuffled(random)) {
                if (placed.size >= settings.words) break
                tryPlace(grid, word, size, random)?.let { placed += PlacedWord(word, it) }
            }

            val letters = grid.map { it ?: ALPHABET.random(random) }.joinToString("")
            return WordSearchGame(size, difficulty, letters, placed)
        }

        private fun tryPlace(grid: Array<Char?>, word: String, size: Int, random: Random): List<Int>? {
            val chars = foldAccents(word)
            val length = chars.length
            if (length > size) return null

            repeat(ATTEMPTS_PER_WORD) {
                val (dr, dc) = DIRECTIONS.random(random)
                val row = random.nextInt(startRange(dr, length, size))
                val col = random.nextInt(startRange(dc, length, size))
                val cells = List(length) { i -> (row + dr * i) * size + col + dc * i }

                if (cells.indices.all { grid[cells[it]] == null || grid[cells[it]] == chars[it] }) {
                    cells.forEachIndexed { i, cell -> grid[cell] = chars[i] }
                    return cells
                }
            }

            return null
        }

        private fun startRange(direction: Int, length: Int, size: Int): IntRange = when {
            direction == 0 -> 0 until size
            direction > 0 -> 0..size - length
            else -> length - 1 until size
        }

        /** Straight line of cells, or null if the two cells are not on a row, column or diagonal. */
        fun pathBetween(start: Int, end: Int, size: Int): List<Int>? {
            val dr = end / size - start / size
            val dc = end % size - start % size
            if (dr == 0 && dc == 0) return null
            if (dr != 0 && dc != 0 && abs(dr) != abs(dc)) return null

            val steps = max(abs(dr), abs(dc))
            return List(steps + 1) { i ->
                (start / size + dr / steps * i) * size + start % size + dc / steps * i
            }
        }

        /**
         * The cell a drag from [start] towards ([row], [col]) really means: a finger never moves in
         * a perfectly straight line, so the end snaps to the nearest of the eight directions and
         * stays on the board.
         */
        fun snapEnd(start: Int, row: Int, col: Int, size: Int): Int {
            val startRow = start / size
            val startCol = start % size
            val dr = row - startRow
            val dc = col - startCol
            if (dr == 0 && dc == 0) return start

            val octant = (atan2(dr.toDouble(), dc.toDouble()) / (PI / 4)).roundToInt()
            val stepR = sin(octant * PI / 4).roundToInt()
            val stepC = cos(octant * PI / 4).roundToInt()
            val wanted = if (stepR != 0 && stepC != 0) (abs(dr) + abs(dc) + 1) / 2 else max(abs(dr), abs(dc))

            var steps = 0
            while (steps < wanted &&
                startRow + stepR * (steps + 1) in 0 until size &&
                startCol + stepC * (steps + 1) in 0 until size
            ) {
                steps++
            }

            return (startRow + stepR * steps) * size + startCol + stepC * steps
        }

        fun fromJson(json: String): WordSearchGame {
            val root = JSONObject(json)
            val words = root.getJSONArray("words")

            return WordSearchGame(
                size = root.getInt("size"),
                difficulty = Difficulty.valueOf(root.getString("difficulty")),
                grid = root.getString("grid"),
                words = List(words.length()) { i ->
                    val word = words.getJSONObject(i)
                    val cells = word.getJSONArray("cells")
                    PlacedWord(
                        word = word.getString("word"),
                        cells = List(cells.length()) { cells.getInt(it) },
                        found = word.getBoolean("found"),
                    )
                },
                pendingStart = root.getInt("pendingStart").takeIf { it >= 0 },
            )
        }
    }
}
