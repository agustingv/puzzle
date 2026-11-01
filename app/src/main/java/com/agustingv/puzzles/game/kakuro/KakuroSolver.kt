package com.agustingv.puzzles.game.kakuro

/**
 * Counts the digit assignments of a puzzle, to verify a generated one has exactly one solution.
 * Backtracks on the most constrained cell, pruning candidates by per-run distinctness and by whether
 * the run's remaining cells could still reach its target sum with the digits left.
 */
internal object KakuroSolver {
    /**
     * Safety cap on the search. Running out counts as "not unique", unlike the web version, which
     * could accept a puzzle whose search it had given up on: only a verified puzzle may be used.
     */
    private const val MAX_STEPS = 300_000

    private class Run(val cells: List<Int>, val sum: Int) {
        var used = 0
        var usedSum = 0
        var filled = 0
    }

    fun countSolutions(blocked: BooleanArray, across: Map<Int, Int>, down: Map<Int, Int>, size: Int, limit: Int): Int {
        val runs = buildList {
            for (cell in blocked.indices) {
                if (!blocked[cell]) continue

                across[cell]?.let { sum ->
                    val row = cell / size
                    val cells = (cell + 1 until (row + 1) * size).takeWhile { !blocked[it] }
                    add(Run(cells, sum) to true)
                }
                down[cell]?.let { sum ->
                    val cells = generateSequence(cell + size) { it + size }
                        .takeWhile { it < blocked.size && !blocked[it] }
                        .toList()
                    add(Run(cells, sum) to false)
                }
            }
        }

        val acrossOf = arrayOfNulls<Run>(blocked.size)
        val downOf = arrayOfNulls<Run>(blocked.size)
        for ((run, isAcross) in runs) {
            for (cell in run.cells) {
                if (isAcross) acrossOf[cell] = run else downOf[cell] = run
            }
        }

        val free = blocked.indices.filter { !blocked[it] }
        val filled = BooleanArray(blocked.size)
        var steps = 0

        fun search(remaining: Int): Int {
            if (++steps > MAX_STEPS) return remaining

            var best = -1
            var bestCandidates = 0
            var bestCount = 10

            for (cell in free) {
                if (filled[cell]) continue

                var candidates = 0
                for (value in 1..9) {
                    if (feasible(acrossOf[cell]!!, value) && feasible(downOf[cell]!!, value)) {
                        candidates = candidates or (1 shl value)
                    }
                }

                val count = Integer.bitCount(candidates)
                if (count < bestCount) {
                    best = cell
                    bestCandidates = candidates
                    bestCount = count
                    if (count == 0) return 0
                }
            }

            if (best < 0) return 1

            val across = acrossOf[best]!!
            val down = downOf[best]!!
            filled[best] = true
            var found = 0

            for (value in 1..9) {
                if (bestCandidates and (1 shl value) == 0) continue

                across.add(value)
                down.add(value)
                found += search(remaining - found)
                across.remove(value)
                down.remove(value)

                if (found >= remaining) break
            }

            filled[best] = false
            return found
        }

        return search(limit)
    }

    private fun Run.add(value: Int) {
        used = used or (1 shl value)
        usedSum += value
        filled++
    }

    private fun Run.remove(value: Int) {
        used = used and (1 shl value).inv()
        usedSum -= value
        filled--
    }

    /** Runs thousands of times per generated puzzle, so it avoids allocating anything. */
    private fun feasible(run: Run, value: Int): Boolean {
        val used = run.used or (1 shl value)
        if (run.used and (1 shl value) != 0) return false

        val cellsLeft = run.cells.size - run.filled - 1
        val sumLeft = run.sum - run.usedSum - value

        if (cellsLeft == 0) return sumLeft == 0
        if (sumLeft < 0) return false

        // Smallest and largest sums the remaining cells could still reach with the digits left.
        var min = 0
        var taken = 0
        var digit = 1
        while (digit <= 9 && taken < cellsLeft) {
            if (used and (1 shl digit) == 0) {
                min += digit
                taken++
            }
            digit++
        }
        if (taken < cellsLeft || sumLeft < min) return false

        var max = 0
        taken = 0
        digit = 9
        while (digit >= 1 && taken < cellsLeft) {
            if (used and (1 shl digit) == 0) {
                max += digit
                taken++
            }
            digit--
        }

        return sumLeft <= max
    }
}
