package com.agustingv.puzzles.game.sudoku

import com.agustingv.puzzles.game.Difficulty
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * 81 cells in reading order. [input] has the player's digits, 0 where empty. [markedWrong] only
 * changes on [check], so mistakes are not revealed while typing; editing a cell clears its mark.
 */
data class SudokuGame(
    val difficulty: Difficulty,
    val solution: List<Int>,
    val givens: Set<Int>,
    val input: List<Int> = List(81) { 0 },
    val markedWrong: Set<Int> = emptySet(),
    val cursor: Int = (0 until 81).firstOrNull { it !in givens } ?: 0,
) {
    val isComplete: Boolean = (0 until 81).all { valueAt(it) == solution[it] }
    val filledCount: Int = (0 until 81).count { valueAt(it) != 0 }

    fun valueAt(cell: Int): Int = if (cell in givens) solution[cell] else input[cell]

    fun select(cell: Int): SudokuGame =
        if (isComplete || cell !in 0 until 81 || cell in givens) this else copy(cursor = cell)

    fun type(digit: Int): SudokuGame {
        if (isComplete || cursor in givens || digit !in 1..9) return this
        return copy(input = input.toMutableList().also { it[cursor] = digit }, markedWrong = markedWrong - cursor)
    }

    fun clear(): SudokuGame {
        if (isComplete || cursor in givens) return this
        return copy(input = input.toMutableList().also { it[cursor] = 0 }, markedWrong = markedWrong - cursor)
    }

    fun check(): SudokuGame =
        copy(markedWrong = (0 until 81).filter { input[it] != 0 && it !in givens && input[it] != solution[it] }.toSet())

    fun toJson(): String = JSONObject()
        .put("difficulty", difficulty.name)
        .put("solution", JSONArray(solution))
        .put("givens", JSONArray(givens.toList()))
        .put("input", JSONArray(input))
        .put("markedWrong", JSONArray(markedWrong.toList()))
        .put("cursor", cursor)
        .toString()

    companion object {
        /** Attempts per difficulty; each one digs a fresh solution and rates the result. */
        private const val ATTEMPTS = 6

        /** Can take noticeable time on a phone: call it away from the main thread. */
        fun generate(difficulty: Difficulty, random: Random = Random.Default): SudokuGame {
            var best: Pair<IntArray, BooleanArray>? = null
            var bestDistance = Int.MAX_VALUE
            val attempts = if (difficulty == Difficulty.EASY) 1 else ATTEMPTS

            for (attempt in 0 until attempts) {
                val dug = dig(difficulty, random)
                val tier = SudokuSolver.rate(applyGivens(dug.first, dug.second)).tier
                val distance = kotlin.math.abs(tier.ordinal - difficulty.ordinal)

                if (distance < bestDistance) {
                    best = dug
                    bestDistance = distance
                }
                if (distance == 0) break
            }

            val (solution, givens) = checkNotNull(best)
            return SudokuGame(difficulty, solution.toList(), (0 until 81).filter { givens[it] }.toSet())
        }

        /**
         * Digs a fresh solved grid down to a near-minimal uniquely-solvable puzzle, a cheap check.
         * Short of Expert, clues then go back one at a time until it no longer needs a technique
         * harder than [difficulty]: a superset of a unique puzzle's clues is still unique, so this
         * takes far fewer ratings than limiting the difficulty during the dig itself.
         */
        private fun dig(difficulty: Difficulty, random: Random): Pair<IntArray, BooleanArray> {
            val solution = IntArray(81)
            fill(solution, 0, random)

            val givens = BooleanArray(81) { true }
            val removed = mutableListOf<Int>()

            for (cell in (0 until 81).shuffled(random)) {
                givens[cell] = false
                if (countSolutions(applyGivens(solution, givens), 2) == 1) {
                    removed += cell
                } else {
                    givens[cell] = true
                }
            }

            if (difficulty < Difficulty.EXPERT) {
                for (cell in removed.shuffled(random)) {
                    if (SudokuSolver.rate(applyGivens(solution, givens)).tier <= difficulty) break
                    givens[cell] = true
                }
            }

            return solution to givens
        }

        private fun fill(grid: IntArray, cell: Int, random: Random): Boolean {
            if (cell == 81) return true

            for (value in (1..9).shuffled(random)) {
                if (canPlace(grid, cell, value)) {
                    grid[cell] = value
                    if (fill(grid, cell + 1, random)) return true
                    grid[cell] = 0
                }
            }

            return false
        }

        private fun canPlace(grid: IntArray, cell: Int, value: Int): Boolean {
            val row = cell / 9
            val col = cell % 9

            for (i in 0 until 9) {
                if (grid[row * 9 + i] == value || grid[i * 9 + col] == value) return false
            }

            val boxRow = row / 3 * 3
            val boxCol = col / 3 * 3
            for (r in boxRow until boxRow + 3) {
                for (c in boxCol until boxCol + 3) {
                    if (grid[r * 9 + c] == value) return false
                }
            }

            return true
        }

        private fun applyGivens(solution: IntArray, givens: BooleanArray) =
            IntArray(81) { if (givens[it]) solution[it] else 0 }

        /**
         * Counts solutions up to [limit], always branching on the blank with fewest candidates:
         * scanning in reading order instead can blow up from milliseconds to seconds on some boards.
         */
        internal fun countSolutions(grid: IntArray, limit: Int): Int {
            var bestCell = -1
            var bestCount = 10

            for (cell in 0 until 81) {
                if (grid[cell] != 0) continue

                val count = (1..9).count { canPlace(grid, cell, it) }
                if (count < bestCount) {
                    bestCell = cell
                    bestCount = count
                    if (count <= 1) break
                }
            }

            if (bestCell < 0) return 1

            var found = 0
            for (value in 1..9) {
                if (!canPlace(grid, bestCell, value)) continue

                grid[bestCell] = value
                found += countSolutions(grid, limit - found)
                grid[bestCell] = 0

                if (found >= limit) return found
            }

            return found
        }

        fun fromJson(json: String): SudokuGame {
            val root = JSONObject(json)

            fun ints(key: String): List<Int> {
                val array = root.getJSONArray(key)
                return List(array.length()) { array.getInt(it) }
            }

            return SudokuGame(
                difficulty = Difficulty.valueOf(root.getString("difficulty")),
                solution = ints("solution"),
                givens = ints("givens").toSet(),
                input = ints("input"),
                markedWrong = ints("markedWrong").toSet(),
                cursor = root.getInt("cursor"),
            )
        }
    }
}
