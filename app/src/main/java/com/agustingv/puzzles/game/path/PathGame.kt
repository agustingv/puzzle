package com.agustingv.puzzles.game.path

import com.agustingv.puzzles.game.Difficulty
import kotlin.math.abs
import kotlin.random.Random

/**
 * One line must visit every cell once, passing the numbered checkpoints in order and ending on the
 * last one. Cells are indexes in reading order; [checkpoints] maps cell to number (1..N).
 */
data class PathGame(
    val size: Int,
    val difficulty: Difficulty,
    val checkpoints: Map<Int, Int>,
    val path: List<Int> = emptyList(),
) {
    val totalCells: Int = size * size

    val isComplete: Boolean = computeComplete()

    /** Every cell is covered, but the line did not end on the last checkpoint. */
    val isFullButWrong: Boolean = path.size == totalCells && !isComplete

    /** Drag step: extends the line into [cell], or undoes the last step if it is the previous cell. */
    fun extend(cell: Int): PathGame {
        if (isComplete || cell !in 0 until totalCells) return this

        if (path.isEmpty()) {
            return if (checkpoints[cell] == 1) copy(path = listOf(cell)) else this
        }

        if (cell in path) {
            return if (path.size >= 2 && path[path.size - 2] == cell) copy(path = path.dropLast(1)) else this
        }

        if (!areAdjacent(path.last(), cell)) return this

        val number = checkpoints[cell]
        if (number != null) {
            val reached = path.lastOrNull { it in checkpoints }?.let { checkpoints.getValue(it) } ?: 0
            if (number != reached + 1) return this
        }

        return copy(path = path + cell)
    }

    /**
     * Press on a cell already in the line: cuts the line back to it. Kept apart from [extend] so a
     * finger drifting over an earlier row mid-drag cannot wipe out a long stretch of the line.
     */
    fun rewindTo(cell: Int): PathGame {
        val index = path.indexOf(cell)
        return if (isComplete || index < 0) this else copy(path = path.take(index + 1))
    }

    fun reset(): PathGame = if (isComplete) this else copy(path = emptyList())

    private fun areAdjacent(a: Int, b: Int): Boolean =
        abs(a / size - b / size) + abs(a % size - b % size) == 1

    private fun computeComplete(): Boolean {
        if (path.size != totalCells || path.toSet().size != totalCells) return false
        if (path.zipWithNext().any { (a, b) -> !areAdjacent(a, b) }) return false

        val visited = path.mapNotNull { checkpoints[it] }
        return visited == (1..checkpoints.size).toList() && checkpoints[path.last()] == checkpoints.size
    }

    companion object {
        private const val STEP_BUDGET = 30_000
        private const val MAX_ATTEMPTS = 200

        private data class Settings(val size: Int, val checkpoints: Int)

        private fun settings(difficulty: Difficulty) = when (difficulty) {
            Difficulty.EASY -> Settings(5, 7)
            Difficulty.MEDIUM -> Settings(6, 6)
            Difficulty.HARD -> Settings(7, 6)
            Difficulty.EXPERT -> Settings(8, 5)
        }

        fun generate(difficulty: Difficulty, random: Random = Random.Default): PathGame =
            generateWithSolution(difficulty, random).first

        /** The solution is the line the checkpoints were picked from; other valid lines may exist. */
        internal fun generateWithSolution(difficulty: Difficulty, random: Random): Pair<PathGame, List<Int>> {
            val settings = settings(difficulty)
            val solution = generateLine(settings.size, random)
            val checkpoints = pickCheckpoints(solution, settings.checkpoints, random)

            return PathGame(settings.size, difficulty, checkpoints) to solution
        }

        /** Random line over the whole grid; retried from scratch whenever backtracking gets stuck. */
        private fun generateLine(size: Int, random: Random): List<Int> {
            repeat(MAX_ATTEMPTS) {
                val line = attemptLine(size, STEP_BUDGET, random)
                if (line.size == size * size) return line
            }

            return attemptLine(size, Int.MAX_VALUE, random)
        }

        private fun attemptLine(size: Int, budget: Int, random: Random): List<Int> {
            val total = size * size
            val visited = BooleanArray(total)
            val line = ArrayList<Int>(total)
            var remaining = budget

            fun unvisitedNeighbors(cell: Int): List<Int> = buildList {
                val row = cell / size
                val col = cell % size
                if (row > 0 && !visited[cell - size]) add(cell - size)
                if (row < size - 1 && !visited[cell + size]) add(cell + size)
                if (col > 0 && !visited[cell - 1]) add(cell - 1)
                if (col < size - 1 && !visited[cell + 1]) add(cell + 1)
            }

            // Warnsdorff's rule: most constrained neighbour first, shuffled to break ties randomly.
            fun extend(cell: Int): Boolean {
                if (--remaining < 0) return false

                visited[cell] = true
                line.add(cell)
                if (line.size == total) return true

                val neighbors = unvisitedNeighbors(cell)
                    .shuffled(random)
                    .sortedBy { unvisitedNeighbors(it).size }

                if (neighbors.any { extend(it) }) return true

                line.removeAt(line.lastIndex)
                visited[cell] = false
                return false
            }

            // On a checkerboard colouring, a line over an odd-sized grid must start on the corners'
            // colour: from the other colour the grid is provably impossible to cover.
            var start: Int
            do {
                start = random.nextInt(total)
            } while (size % 2 == 1 && (start / size + start % size) % 2 != 0)

            extend(start)
            return line
        }

        /** Roughly evenly spaced along the line, always including its first and last cell. */
        private fun pickCheckpoints(line: List<Int>, target: Int, random: Random): Map<Int, Int> {
            val total = line.size
            val slots = target - 2
            val indexes = sortedSetOf(0, total - 1)

            for (i in 1..slots) {
                val jitter = random.nextInt(-1, 2)
                indexes += (total * i / (slots + 1) + jitter).coerceIn(1, total - 2)
            }

            return indexes.withIndex().associate { (number, index) -> line[index] to number + 1 }
        }
    }
}
