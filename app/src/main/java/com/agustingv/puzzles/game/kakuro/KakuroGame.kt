package com.agustingv.puzzles.game.kakuro

import com.agustingv.puzzles.game.Difficulty
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.random.Random

/**
 * Cells in reading order. [across] and [down] map a blocked cell to the sum of the run starting
 * right after it. [input] has the player's digits, 0 where empty. [markedWrong] only changes on
 * [check], so mistakes are not revealed while typing; editing a cell clears its mark.
 */
data class KakuroGame(
    val size: Int,
    val difficulty: Difficulty,
    val blocked: List<Boolean>,
    val solution: List<Int>,
    val across: Map<Int, Int>,
    val down: Map<Int, Int>,
    val input: List<Int> = List(solution.size) { 0 },
    val markedWrong: Set<Int> = emptySet(),
    val cursor: Int = blocked.indexOfFirst { !it },
) {
    val activeCells: Int = blocked.count { !it }
    val filledCount: Int = blocked.indices.count { !blocked[it] && input[it] != 0 }
    val isComplete: Boolean = blocked.indices.all { blocked[it] || input[it] == solution[it] }

    fun select(cell: Int): KakuroGame =
        if (isComplete || cell !in blocked.indices || blocked[cell]) this else copy(cursor = cell)

    fun type(digit: Int): KakuroGame {
        if (isComplete || digit !in 1..9) return this
        return copy(input = input.toMutableList().also { it[cursor] = digit }, markedWrong = markedWrong - cursor)
    }

    fun clear(): KakuroGame {
        if (isComplete) return this
        return copy(input = input.toMutableList().also { it[cursor] = 0 }, markedWrong = markedWrong - cursor)
    }

    fun check(): KakuroGame =
        copy(markedWrong = blocked.indices.filter { !blocked[it] && input[it] != 0 && input[it] != solution[it] }.toSet())

    fun toJson(): String {
        fun clues(map: Map<Int, Int>) = JSONObject().also { json ->
            map.forEach { (cell, sum) -> json.put(cell.toString(), sum) }
        }

        return JSONObject()
            .put("size", size)
            .put("difficulty", difficulty.name)
            .put("blocked", JSONArray(blocked))
            .put("solution", JSONArray(solution))
            .put("across", clues(across))
            .put("down", clues(down))
            .put("input", JSONArray(input))
            .put("markedWrong", JSONArray(markedWrong.toList()))
            .put("cursor", cursor)
            .toString()
    }

    companion object {
        private const val MAX_SHAPE_ATTEMPTS = 4000
        private const val MAX_FILL_ATTEMPTS_PER_SHAPE = 8
        private const val FILL_STEP_BUDGET = 200_000

        /**
         * A random shape and fill is only uniquely solvable a small fraction of the time: sums
         * constrain far less than Sudoku's rows, columns and boxes, and bigger grids offer more room
         * for some local swap to be ambiguous. Interior 3x3 lands on one about 0.5-1% of the time,
         * 4x4 about 0.2-0.4%, and 5x5 and up essentially never, so size and density scale with
         * difficulty: bigger or sparser is both harder to reason through and harder to generate.
         */
        private data class Settings(val interior: Int, val density: Double)

        private fun settings(difficulty: Difficulty) = when (difficulty) {
            Difficulty.EASY -> Settings(3, 0.55)
            Difficulty.MEDIUM -> Settings(3, 0.40)
            Difficulty.HARD -> Settings(4, 0.55)
            Difficulty.EXPERT -> Settings(4, 0.45)
        }

        /** Can take noticeable time on a phone: call it away from the main thread. */
        fun generate(difficulty: Difficulty, random: Random = Random.Default): KakuroGame {
            val settings = settings(difficulty)
            val size = settings.interior + 1

            repeat(MAX_SHAPE_ATTEMPTS) {
                val blocked = generateShape(size, settings.density, random) ?: return@repeat

                repeat(MAX_FILL_ATTEMPTS_PER_SHAPE) {
                    val solution = fillDigits(blocked, size, random) ?: return@repeat
                    val (across, down) = computeClues(blocked, solution, size)

                    if (KakuroSolver.countSolutions(blocked, across, down, size, 2) == 1) {
                        return KakuroGame(size, difficulty, blocked.toList(), solution.toList(), across, down)
                    }
                }
            }

            error("No se pudo generar un Kakuro con solución única")
        }

        /**
         * Random pattern with a permanently blocked border (row 0, column 0) to host clues. Each row
         * is built from valid pieces, so rows are valid by construction: blocking cells one by one
         * and rejecting bad shapes works only 0.1-1% of the time. Columns still have to be checked.
         */
        private fun generateShape(size: Int, density: Double, random: Random): BooleanArray? {
            val blocked = BooleanArray(size * size)
            for (i in 0 until size) {
                blocked[i] = true
                blocked[i * size] = true
            }

            for (row in 1 until size) {
                val line = randomValidLine(size - 1, density, random)
                for (i in line.indices) blocked[row * size + i + 1] = line[i]
            }

            return blocked.takeIf { isValidShape(it, size) }
        }

        /** A blocked cell or a whole white run of 2-9 at each step, never leaving one lone cell. */
        private fun randomValidLine(length: Int, density: Double, random: Random): List<Boolean> {
            val line = ArrayList<Boolean>(length)

            while (line.size < length) {
                val remaining = length - line.size

                if (remaining == 1 || random.nextInt(1000) < (density * 1000).toInt()) {
                    line += true
                    continue
                }

                // Weighted to short runs: their sums constrain the puzzle far more than long ones.
                val weights = (2..minOf(9, remaining))
                    .filter { remaining - it != 1 }
                    .associateWith { maxOf(1, 10 - it) }

                repeat(weightedRandom(weights, random)) { line += false }
            }

            return line
        }

        private fun weightedRandom(weights: Map<Int, Int>, random: Random): Int {
            var pick = random.nextInt(weights.values.sum()) + 1

            for ((value, weight) in weights) {
                pick -= weight
                if (pick <= 0) return value
            }

            return weights.keys.last()
        }

        private fun isValidShape(blocked: BooleanArray, size: Int): Boolean {
            fun validLine(cellAt: (Int) -> Int): Boolean {
                var run = 0
                for (i in 0 until size) {
                    if (blocked[cellAt(i)]) {
                        if (run == 1 || run > 9) return false
                        run = 0
                    } else {
                        run++
                    }
                }
                return run != 1 && run <= 9
            }

            for (i in 0 until size) {
                if (!validLine { i * size + it } || !validLine { it * size + i }) return false
            }

            // Without this, a high density can produce a nearly empty, trivially solved board.
            val interior = (size - 1) * (size - 1)
            return blocked.count { !it } >= ceil(interior * 0.5).toInt()
        }

        private fun fillDigits(blocked: BooleanArray, size: Int, random: Random): IntArray? {
            val solution = IntArray(blocked.size)
            val cells = blocked.indices.filter { !blocked[it] }
            var budget = FILL_STEP_BUDGET

            fun conflicts(cell: Int, value: Int): Boolean {
                val row = cell / size
                val col = cell % size

                var c = col - 1
                while (c >= 0 && !blocked[row * size + c]) if (solution[row * size + c--] == value) return true
                c = col + 1
                while (c < size && !blocked[row * size + c]) if (solution[row * size + c++] == value) return true
                var r = row - 1
                while (r >= 0 && !blocked[r * size + col]) if (solution[r-- * size + col] == value) return true
                r = row + 1
                while (r < size && !blocked[r * size + col]) if (solution[r++ * size + col] == value) return true

                return false
            }

            fun extend(index: Int): Boolean {
                if (--budget < 0) return false
                if (index == cells.size) return true

                val cell = cells[index]
                for (value in (1..9).shuffled(random)) {
                    if (conflicts(cell, value)) continue

                    solution[cell] = value
                    if (extend(index + 1)) return true
                    solution[cell] = 0
                }

                return false
            }

            return if (extend(0)) solution else null
        }

        private fun computeClues(blocked: BooleanArray, solution: IntArray, size: Int): Pair<Map<Int, Int>, Map<Int, Int>> {
            val across = mutableMapOf<Int, Int>()
            val down = mutableMapOf<Int, Int>()

            for (cell in blocked.indices) {
                if (!blocked[cell]) continue

                val row = cell / size
                val col = cell % size

                if (col + 1 < size && !blocked[cell + 1]) {
                    across[cell] = (cell + 1 until (row + 1) * size).takeWhile { !blocked[it] }.sumOf { solution[it] }
                }
                if (row + 1 < size && !blocked[cell + size]) {
                    down[cell] = generateSequence(cell + size) { it + size }
                        .takeWhile { it < blocked.size && !blocked[it] }
                        .sumOf { solution[it] }
                }
            }

            return across to down
        }

        fun fromJson(json: String): KakuroGame {
            val root = JSONObject(json)

            fun ints(key: String): List<Int> {
                val array = root.getJSONArray(key)
                return List(array.length()) { array.getInt(it) }
            }

            fun clues(key: String): Map<Int, Int> {
                val map = root.getJSONObject(key)
                return map.keys().asSequence().associate { it.toInt() to map.getInt(it) }
            }

            val blocked = root.getJSONArray("blocked")

            return KakuroGame(
                size = root.getInt("size"),
                difficulty = Difficulty.valueOf(root.getString("difficulty")),
                blocked = List(blocked.length()) { blocked.getBoolean(it) },
                solution = ints("solution"),
                across = clues("across"),
                down = clues("down"),
                input = ints("input"),
                markedWrong = ints("markedWrong").toSet(),
                cursor = root.getInt("cursor"),
            )
        }
    }
}
