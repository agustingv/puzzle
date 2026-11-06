package com.butaca13.puzzles.game.sudoku

import com.butaca13.puzzles.game.Difficulty

/**
 * Rates a puzzle by the hardest human technique it needs, trying techniques from easiest to hardest
 * at each step, as a person would. Scan orders match the web version on purpose: which technique
 * fires first can change the rating, and web_solver_fixtures.json checks the results stay the same.
 *
 * Grids are 81 cells in reading order, 0 for blank. Candidates are bitmasks: bit v means digit v.
 */
internal object SudokuSolver {
    private const val MAX_STEPS = 500

    data class Rating(val solved: Boolean, val tier: Difficulty)

    private val rows = List(9) { r -> List(9) { c -> r * 9 + c } }
    private val columns = List(9) { c -> List(9) { r -> r * 9 + c } }
    private val boxes = List(9) { b ->
        val startRow = b / 3 * 3
        val startCol = b % 3 * 3
        List(9) { i -> (startRow + i / 3) * 9 + startCol + i % 3 }
    }
    private val units = rows + columns + boxes
    private val lines = rows + columns
    private val peers = List(81) { cell ->
        (rows[cell / 9] + columns[cell % 9] + boxes[boxOf(cell)]).distinct().filter { it != cell }
    }

    private fun boxOf(cell: Int) = cell / 9 / 3 * 3 + cell % 9 / 3

    fun rate(puzzle: IntArray): Rating {
        val grid = puzzle.copyOf()
        val candidates = computeCandidates(grid)
        var tier = Difficulty.EASY

        for (step in 0 until MAX_STEPS) {
            val single = findNakedSingle(grid, candidates) ?: findHiddenSingle(grid, candidates)
            when {
                single != null -> place(grid, candidates, single.first, single.second)
                applyLockedCandidates(grid, candidates) -> tier = maxOf(tier, Difficulty.MEDIUM)
                applySubsets(grid, candidates, 2) ||
                    applySubsets(grid, candidates, 3) ||
                    applyFish(grid, candidates, 2) -> tier = maxOf(tier, Difficulty.HARD)
                applyFish(grid, candidates, 3) -> tier = Difficulty.EXPERT
                else -> break
            }
        }

        val solved = grid.none { it == 0 }

        // Stuck with blanks left means it needs chains or colouring, which are not implemented.
        return Rating(solved, if (solved) tier else Difficulty.EXPERT)
    }

    private fun computeCandidates(grid: IntArray) = IntArray(81) { cell ->
        if (grid[cell] != 0) {
            0
        } else {
            var used = 0
            for (peer in peers[cell]) used = used or (1 shl grid[peer])
            ALL_DIGITS and used.inv()
        }
    }

    private fun place(grid: IntArray, candidates: IntArray, cell: Int, value: Int) {
        grid[cell] = value
        candidates[cell] = 0
        for (peer in peers[cell]) candidates[peer] = candidates[peer] and (1 shl value).inv()
    }

    private fun eliminate(candidates: IntArray, cell: Int, value: Int): Boolean {
        val bit = 1 shl value
        if (candidates[cell] and bit == 0) return false
        candidates[cell] = candidates[cell] and bit.inv()
        return true
    }

    private fun has(candidates: IntArray, cell: Int, value: Int) = candidates[cell] and (1 shl value) != 0

    private fun findNakedSingle(grid: IntArray, candidates: IntArray): Pair<Int, Int>? {
        for (cell in 0 until 81) {
            if (grid[cell] == 0 && Integer.bitCount(candidates[cell]) == 1) {
                return cell to Integer.numberOfTrailingZeros(candidates[cell])
            }
        }
        return null
    }

    private fun findHiddenSingle(grid: IntArray, candidates: IntArray): Pair<Int, Int>? {
        for (unit in units) {
            for (value in 1..9) {
                val cells = unit.filter { grid[it] == 0 && has(candidates, it, value) }
                if (cells.size == 1) return cells[0] to value
            }
        }
        return null
    }

    /** Pointing (a box confines a digit to one line) and claiming (a line confines it to one box). */
    private fun applyLockedCandidates(grid: IntArray, candidates: IntArray): Boolean {
        for (box in boxes) {
            for (value in 1..9) {
                val cells = box.filter { grid[it] == 0 && has(candidates, it, value) }
                if (cells.isEmpty()) continue

                val rowsUsed = cells.map { it / 9 }.distinct()
                val columnsUsed = cells.map { it % 9 }.distinct()

                if (rowsUsed.size == 1 && eliminateFrom(grid, candidates, rows[rowsUsed[0]], cells, value)) return true
                if (columnsUsed.size == 1 && eliminateFrom(grid, candidates, columns[columnsUsed[0]], cells, value)) return true
            }
        }

        for (line in lines) {
            for (value in 1..9) {
                val cells = line.filter { grid[it] == 0 && has(candidates, it, value) }
                if (cells.isEmpty()) continue

                val boxesUsed = cells.map(::boxOf).distinct()
                if (boxesUsed.size == 1 && eliminateFrom(grid, candidates, boxes[boxesUsed[0]], cells, value)) return true
            }
        }

        return false
    }

    private fun eliminateFrom(grid: IntArray, candidates: IntArray, unit: List<Int>, keep: List<Int>, value: Int): Boolean {
        var changed = false
        for (cell in unit) {
            if (cell !in keep && grid[cell] == 0 && eliminate(candidates, cell, value)) changed = true
        }
        return changed
    }

    private fun applySubsets(grid: IntArray, candidates: IntArray, size: Int): Boolean =
        units.any { unit ->
            applyNakedSubset(grid, candidates, unit, size) || applyHiddenSubset(grid, candidates, unit, size)
        }

    private fun applyNakedSubset(grid: IntArray, candidates: IntArray, unit: List<Int>, size: Int): Boolean {
        val empty = unit.filter { grid[it] == 0 }

        return combinations(empty, size).any { combo ->
            val valid = combo.all { Integer.bitCount(candidates[it]) in 2..size }
            val union = combo.fold(0) { acc, cell -> acc or candidates[cell] }
            if (!valid || Integer.bitCount(union) != size) return@any false

            var changed = false
            for (cell in empty) {
                if (cell in combo) continue
                for (value in 1..9) {
                    if (union and (1 shl value) != 0 && eliminate(candidates, cell, value)) changed = true
                }
            }
            changed
        }
    }

    private fun applyHiddenSubset(grid: IntArray, candidates: IntArray, unit: List<Int>, size: Int): Boolean {
        val empty = unit.filter { grid[it] == 0 }
        if (empty.size <= size) return false

        // Only digits still missing from the unit can form a hidden subset: a placed digit has no
        // candidate cells and would otherwise look confined, producing bogus eliminations.
        val active = (1..9).filter { value -> empty.any { has(candidates, it, value) } }

        return combinations(active, size).any { values ->
            val cells = empty.filter { cell -> values.any { has(candidates, cell, it) } }
            if (cells.size != size) return@any false

            var changed = false
            for (cell in cells) {
                for (value in 1..9) {
                    if (value !in values && eliminate(candidates, cell, value)) changed = true
                }
            }
            changed
        }
    }

    /** X-Wing ([size] 2) and Swordfish ([size] 3), by rows and then by columns. */
    private fun applyFish(grid: IntArray, candidates: IntArray, size: Int): Boolean =
        applyFishOriented(grid, candidates, size, rowBased = true) ||
            applyFishOriented(grid, candidates, size, rowBased = false)

    private fun applyFishOriented(grid: IntArray, candidates: IntArray, size: Int, rowBased: Boolean): Boolean {
        fun cellAt(i: Int, j: Int) = if (rowBased) i * 9 + j else j * 9 + i

        for (value in 1..9) {
            val positions = (0 until 9).associateWith { i ->
                (0 until 9).filter { j -> grid[cellAt(i, j)] == 0 && has(candidates, cellAt(i, j), value) }
            }.filterValues { it.size in 2..size }

            if (positions.size < size) continue

            val found = combinations(positions.keys.toList(), size).any { primary ->
                val union = primary.flatMap { positions.getValue(it) }.distinct()
                if (union.size != size) return@any false

                var changed = false
                for (j in union) {
                    for (i in 0 until 9) {
                        if (i in primary) continue
                        val cell = cellAt(i, j)
                        if (grid[cell] == 0 && eliminate(candidates, cell, value)) changed = true
                    }
                }
                changed
            }
            if (found) return true
        }

        return false
    }

    /** All [k]-combinations in lexicographic order, lazily, so a technique can stop at the first hit. */
    private fun <T> combinations(items: List<T>, k: Int): Sequence<List<T>> = sequence {
        val n = items.size
        if (k > n || k <= 0) return@sequence

        val indexes = IntArray(k) { it }
        while (true) {
            yield(indexes.map { items[it] })

            var i = k - 1
            while (i >= 0 && indexes[i] == i + n - k) i--
            if (i < 0) break

            indexes[i]++
            for (j in i + 1 until k) indexes[j] = indexes[j - 1] + 1
        }
    }

    private const val ALL_DIGITS = 0b11_1111_1110
}
