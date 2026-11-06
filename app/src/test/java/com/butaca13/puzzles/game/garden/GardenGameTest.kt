package com.butaca13.puzzles.game.garden

import com.butaca13.puzzles.game.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random
import kotlin.system.measureTimeMillis

class GardenGameTest {

    /**
     * 5x5 with one bed per row, so only the rows' own rules and contact matter:
     * a valid planting is columns 0, 2, 4, 1, 3.
     */
    private val beds = List(25) { it / 5 }
    private val game = GardenGame(5, Difficulty.EASY, beds)

    private fun GardenGame.plant(vararg cells: Int) =
        cells.fold(this) { g, c -> g.tap(c).tap(c) }

    @Test
    fun `a tap goes round empty, marked, flower and back`() {
        val marked = game.tap(0)
        assertEquals(GardenCell.MARK, marked.cells[0])

        val flower = marked.tap(0)
        assertEquals(GardenCell.FLOWER, flower.cells[0])
        assertEquals(listOf(0), flower.flowers)

        assertEquals(GardenCell.EMPTY, flower.tap(0).cells[0])
    }

    @Test
    fun `out of range cells are ignored`() {
        assertSame(game, game.tap(-1))
        assertSame(game, game.tap(25))
        assertSame(game, game.mark(25))
    }

    @Test
    fun `a drag marks empty cells and leaves the rest alone`() {
        assertEquals(GardenCell.MARK, game.mark(3).cells[3])

        val planted = game.plant(3)
        assertSame(planted, planted.mark(3))

        val marked = game.tap(4)
        assertSame(marked, marked.mark(4))
    }

    @Test
    fun `two flowers in a row, column, bed or touching clash`() {
        assertEquals("row", setOf(0, 4), game.plant(0, 4).conflicts)
        assertEquals("column", setOf(0, 20), game.plant(0, 20).conflicts)
        assertEquals("touching sideways, also same bed", setOf(0, 1), game.plant(0, 1).conflicts)
        assertEquals("touching below", setOf(2, 7), game.plant(2, 7).conflicts)
        assertEquals("touching on a corner", setOf(0, 6), game.plant(0, 6).conflicts)
    }

    @Test
    fun `a bed clashes on its own, even far apart`() {
        val oneBig = GardenGame(5, Difficulty.EASY, List(25) { if (it == 0 || it == 24) 0 else 1 })

        assertEquals(setOf(0, 24), oneBig.plant(0, 24).conflicts)
    }

    @Test
    fun `flowers apart in different beds, rows and columns do not clash`() {
        assertTrue(game.plant(0, 7).conflicts.isEmpty())
    }

    @Test
    fun `marks never clash`() {
        assertTrue(game.tap(0).tap(1).conflicts.isEmpty())
    }

    @Test
    fun `the game is complete only with every flower in place and no clash`() {
        val almost = game.plant(0, 7, 14, 16)
        assertFalse(almost.isComplete)

        val done = almost.plant(23)
        assertTrue(done.isComplete)
    }

    @Test
    fun `enough flowers with a clash do not complete it`() {
        assertFalse(game.plant(0, 1, 2, 3, 4).isComplete)
    }

    @Test
    fun `a finished garden is frozen`() {
        val done = game.plant(0, 7, 14, 16, 23)

        assertSame(done, done.tap(1))
        assertSame(done, done.mark(1))
        assertSame(done, done.reset())
    }

    @Test
    fun `reset clears flowers and marks`() {
        val cleared = game.plant(0).tap(5).reset()

        assertTrue(cleared.cells.all { it == GardenCell.EMPTY })
    }

    @Test
    fun `generated gardens have one solution and well formed beds, at every difficulty`() {
        val sizes = mapOf(Difficulty.EASY to 6, Difficulty.MEDIUM to 7, Difficulty.HARD to 8, Difficulty.EXPERT to 9)

        for (difficulty in Difficulty.entries) {
            var slowest = 0L
            var total = 0L
            val rounds = 40

            repeat(rounds) { seed ->
                lateinit var result: Pair<GardenGame, Set<Int>>
                val ms = measureTimeMillis { result = GardenGame.generateWithSolution(difficulty, Random(seed)) }
                slowest = maxOf(slowest, ms)
                total += ms

                val (game, solution) = result
                val size = game.size
                assertEquals(sizes[difficulty], size)
                assertEquals((0 until size).toSet(), game.beds.toSet())

                // One flower per bed, row and column, none touching: checked here without the app's code.
                assertEquals(size, solution.size)
                assertEquals(size, solution.map { game.beds[it] }.toSet().size)
                assertEquals(size, solution.map { it / size }.toSet().size)
                assertEquals(size, solution.map { it % size }.toSet().size)
                for (a in solution) for (b in solution) {
                    if (a != b) assertFalse(abs(a / size - b / size) <= 1 && abs(a % size - b % size) <= 1)
                }

                val beds = game.beds
                for (bed in 0 until size) assertTrue("$difficulty seed $seed bed $bed", connected(beds, bed, size))

                assertEquals("$difficulty seed $seed", listOf(solution.sorted()), bruteForceSolutions(beds, size))

                val played = solution.fold(game) { g, cell -> g.tap(cell).tap(cell) }
                assertTrue(played.isComplete)
            }

            println("garden $difficulty: avg ${total / rounds} ms, slowest $slowest ms")
        }
    }

    @Test
    fun `json round trip keeps the whole state`() {
        val played = GardenGame.generate(Difficulty.HARD, Random(4)).tap(0).tap(5).tap(5)

        assertEquals(played, GardenGame.fromJson(played.toJson()))
    }

    private fun connected(beds: List<Int>, bed: Int, size: Int): Boolean {
        val cells = beds.indices.filter { beds[it] == bed }
        val seen = mutableSetOf(cells.first())
        val pending = ArrayDeque(seen)

        while (pending.isNotEmpty()) {
            val c = pending.removeLast()
            for (n in listOf(c - size, c + size, c - 1, c + 1)) {
                val sameRowOrColumn = (n == c - 1 && c % size > 0) || (n == c + 1 && c % size < size - 1) ||
                    n == c - size || n == c + size
                if (n in beds.indices && sameRowOrColumn && beds[n] == bed && seen.add(n)) pending.addLast(n)
            }
        }
        return seen.size == cells.size
    }

    /** Deliberately naive and unrelated to the production solver: every column order, checked whole. */
    private fun bruteForceSolutions(beds: List<Int>, size: Int): List<List<Int>> {
        val found = mutableListOf<List<Int>>()

        fun permute(prefix: List<Int>, left: List<Int>) {
            if (prefix.size >= 2 && abs(prefix[prefix.size - 1] - prefix[prefix.size - 2]) <= 1) return
            if (left.isEmpty()) {
                val cells = prefix.mapIndexed { row, column -> row * size + column }
                if (cells.map { beds[it] }.toSet().size == size) found += cells.sorted()
                return
            }
            for (column in left) permute(prefix + column, left - column)
        }

        permute(emptyList(), (0 until size).toList())
        return found
    }
}
