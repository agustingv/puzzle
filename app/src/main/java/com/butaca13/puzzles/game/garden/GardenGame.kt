package com.butaca13.puzzles.game.garden

import com.butaca13.puzzles.game.Difficulty
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.random.Random

enum class GardenCell(val code: Char) { EMPTY('.'), MARK('x'), FLOWER('f') }

/**
 * Plant one flower in every bed, so that each row and column also holds exactly one and no two
 * flowers touch, not even corner to corner. Cells are indexes in reading order; [beds] says which
 * bed each one belongs to.
 *
 * The rules are the old Star Battle puzzle's, also known as Queens, and every level comes from this
 * app's own generator: see [generate].
 */
data class GardenGame(
    val size: Int,
    val difficulty: Difficulty,
    val beds: List<Int>,
    val cells: List<GardenCell> = List(size * size) { GardenCell.EMPTY },
) {
    val flowers: List<Int> = cells.indices.filter { cells[it] == GardenCell.FLOWER }

    /** Flowers breaking a rule. Worked out from the rules alone, so it never gives the solution away. */
    val conflicts: Set<Int> = buildSet {
        for (i in flowers.indices) {
            for (j in i + 1 until flowers.size) {
                if (clash(flowers[i], flowers[j])) {
                    add(flowers[i])
                    add(flowers[j])
                }
            }
        }
    }

    val isComplete: Boolean = flowers.size == size && conflicts.isEmpty()

    private fun clash(a: Int, b: Int): Boolean {
        val rowGap = abs(a / size - b / size)
        val columnGap = abs(a % size - b % size)
        return rowGap == 0 || columnGap == 0 || beds[a] == beds[b] || (rowGap <= 1 && columnGap <= 1)
    }

    /** A tap rules a cell out, or takes the mark away. A flower stays: a brush must not uproot it. */
    fun tap(cell: Int): GardenGame {
        if (isComplete || cell !in cells.indices) return this

        return when (cells[cell]) {
            GardenCell.EMPTY -> withCell(cell, GardenCell.MARK)
            GardenCell.MARK -> withCell(cell, GardenCell.EMPTY)
            GardenCell.FLOWER -> this
        }
    }

    /**
     * A double tap plants a flower, over a mark too, or takes one away. Its first tap has already come
     * in as a [tap], which changed only the mark: either way, the cell had no flower before.
     */
    fun toggleFlower(cell: Int): GardenGame {
        if (isComplete || cell !in cells.indices) return this

        return withCell(cell, if (cells[cell] == GardenCell.FLOWER) GardenCell.EMPTY else GardenCell.FLOWER)
    }

    /** A drag rules out the empty cells it crosses, and leaves flowers and marks alone. */
    fun mark(cell: Int): GardenGame {
        if (isComplete || cell !in cells.indices || cells[cell] != GardenCell.EMPTY) return this
        return withCell(cell, GardenCell.MARK)
    }

    /** A drag that started on a mark takes off the marks it crosses, and leaves flowers alone. */
    fun unmark(cell: Int): GardenGame {
        if (isComplete || cell !in cells.indices || cells[cell] != GardenCell.MARK) return this
        return withCell(cell, GardenCell.EMPTY)
    }

    fun reset(): GardenGame = if (isComplete) this else copy(cells = List(size * size) { GardenCell.EMPTY })

    private fun withCell(cell: Int, value: GardenCell) =
        copy(cells = cells.toMutableList().also { it[cell] = value })

    fun toJson(): String = JSONObject()
        .put("size", size)
        .put("difficulty", difficulty.name)
        .put("beds", JSONArray(beds))
        .put("cells", cells.map { it.code }.joinToString(""))
        .toString()

    companion object {
        private const val MAX_REPAIRS_PER_CELL = 6

        fun sizeFor(difficulty: Difficulty): Int = when (difficulty) {
            Difficulty.EASY -> 6
            Difficulty.MEDIUM -> 7
            Difficulty.HARD -> 8
            Difficulty.EXPERT -> 9
        }

        /** Can take noticeable time on a phone: call it away from the main thread. */
        fun generate(difficulty: Difficulty, random: Random = Random.Default): GardenGame =
            generateWithSolution(difficulty, random).first

        /**
         * Places the flowers first, then grows a bed around each one, and finally repairs the beds
         * until those flowers are the only solution. Each repair moves a cell into a neighbouring
         * bed so that a competing solution ends up with two flowers in that bed, which rules it out.
         */
        internal fun generateWithSolution(difficulty: Difficulty, random: Random): Pair<GardenGame, Set<Int>> {
            val size = sizeFor(difficulty)

            while (true) {
                val columns = placeFlowers(size, random)
                val seeds = List(size) { row -> row * size + columns[row] }
                val beds = growBeds(size, seeds, random)

                if (makeUnique(size, beds, seeds, columns, random)) {
                    return GardenGame(size, difficulty, beds.toList()) to seeds.toSet()
                }
            }
        }

        /** One flower per row: distinct columns, and never next to the one in the row above. */
        private fun placeFlowers(size: Int, random: Random): IntArray {
            val columns = IntArray(size)
            val used = BooleanArray(size)

            fun place(row: Int): Boolean {
                if (row == size) return true

                for (column in (0 until size).shuffled(random)) {
                    if (used[column] || (row > 0 && abs(columns[row - 1] - column) <= 1)) continue

                    columns[row] = column
                    used[column] = true
                    if (place(row + 1)) return true
                    used[column] = false
                }
                return false
            }

            check(place(0)) { "no hay colocación para un tablero de $size" }
            return columns
        }

        private fun neighbours(cell: Int, size: Int): List<Int> = buildList {
            if (cell >= size) add(cell - size)
            if (cell < size * (size - 1)) add(cell + size)
            if (cell % size > 0) add(cell - 1)
            if (cell % size < size - 1) add(cell + 1)
        }

        private fun growBeds(size: Int, seeds: List<Int>, random: Random): IntArray {
            val beds = IntArray(size * size) { -1 }
            seeds.forEachIndexed { bed, cell -> beds[cell] = bed }
            var unassigned = size * size - size

            while (unassigned > 0) {
                val bed = random.nextInt(size)
                val options = beds.indices
                    .filter { beds[it] == -1 && neighbours(it, size).any { n -> beds[n] == bed } }
                if (options.isEmpty()) continue

                beds[options.random(random)] = bed
                unassigned--
            }
            return beds
        }

        private fun makeUnique(
            size: Int,
            beds: IntArray,
            seeds: List<Int>,
            columns: IntArray,
            random: Random,
        ): Boolean {
            repeat(size * size * MAX_REPAIRS_PER_CELL) {
                val competing = solve(size, beds, 2).firstOrNull { !it.contentEquals(columns) } ?: return true

                val moved = (0 until size)
                    .map { row -> row * size + competing[row] }
                    .filter { it !in seeds }
                    .shuffled(random)
                    .any { cell -> moveToNeighbourBed(cell, size, beds, seeds, random) }

                if (!moved) return false
            }
            return false
        }

        /** Only if its old bed stays in one piece around its flower. */
        private fun moveToNeighbourBed(
            cell: Int,
            size: Int,
            beds: IntArray,
            seeds: List<Int>,
            random: Random,
        ): Boolean {
            val old = beds[cell]
            val target = neighbours(cell, size).map { beds[it] }.filter { it != old }.shuffled(random).firstOrNull()
                ?: return false

            beds[cell] = target
            if (isConnected(old, size, beds, seeds[old])) return true

            beds[cell] = old
            return false
        }

        private fun isConnected(bed: Int, size: Int, beds: IntArray, seed: Int): Boolean {
            val seen = mutableSetOf(seed)
            val pending = ArrayDeque(listOf(seed))

            while (pending.isNotEmpty()) {
                for (next in neighbours(pending.removeLast(), size)) {
                    if (beds[next] == bed && seen.add(next)) pending.addLast(next)
                }
            }
            return seen.size == beds.count { it == bed }
        }

        /** Solutions, up to [limit], each as the flower's column per row. */
        internal fun solve(size: Int, beds: IntArray, limit: Int): List<IntArray> {
            val found = mutableListOf<IntArray>()
            val columns = IntArray(size)

            fun place(row: Int, usedColumns: Int, usedBeds: Int) {
                if (row == size) {
                    found += columns.copyOf()
                    return
                }

                for (column in 0 until size) {
                    if (found.size >= limit) return
                    val bed = beds[row * size + column]
                    if (usedColumns and (1 shl column) != 0 || usedBeds and (1 shl bed) != 0) continue
                    if (row > 0 && abs(columns[row - 1] - column) <= 1) continue

                    columns[row] = column
                    place(row + 1, usedColumns or (1 shl column), usedBeds or (1 shl bed))
                }
            }

            place(0, 0, 0)
            return found
        }

        fun fromJson(json: String): GardenGame {
            val root = JSONObject(json)
            val beds = root.getJSONArray("beds")
            val cells = root.getString("cells")

            return GardenGame(
                size = root.getInt("size"),
                difficulty = Difficulty.valueOf(root.getString("difficulty")),
                beds = List(beds.length()) { beds.getInt(it) },
                cells = cells.map { code -> GardenCell.entries.first { it.code == code } },
            )
        }
    }
}
