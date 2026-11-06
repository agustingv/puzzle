package com.butaca13.puzzles.game.sudoku

import com.butaca13.puzzles.game.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random
import kotlin.system.measureTimeMillis

class SudokuGameTest {

    private val solution = (
        "534678912" + "672195348" + "198342567" + "859761423" + "426853791" +
            "713924856" + "961537284" + "287419635" + "345286179"
        ).map { it.digitToInt() }

    /** Everything is given except the first two cells of the first row (5 and 3). */
    private val game = SudokuGame(Difficulty.EASY, solution, (2 until 81).toSet())

    @Test
    fun `the cursor starts on the first free cell and givens can not be selected`() {
        assertEquals(0, game.cursor)
        assertEquals(1, game.select(1).cursor)
        assertSame(game, game.select(40))
    }

    @Test
    fun `typing never touches a given and only accepts 1 to 9`() {
        assertEquals(7, game.type(7).valueAt(0))
        assertSame(game, game.type(0))
        assertSame(game, game.type(10))
        assertEquals(solution[40], game.type(7).valueAt(40))
    }

    @Test
    fun `mistakes are only revealed by checking and editing clears the mark`() {
        val typed = game.type(9)
        assertTrue(typed.markedWrong.isEmpty())

        val checked = typed.check()
        assertEquals(setOf(0), checked.markedWrong)

        assertTrue(checked.type(5).markedWrong.isEmpty())
        assertTrue(checked.clear().markedWrong.isEmpty())
    }

    @Test
    fun `a right digit is not marked, empty cells neither`() {
        assertTrue(game.type(5).check().markedWrong.isEmpty())
    }

    @Test
    fun `filling everything correctly completes the game and freezes it`() {
        val almost = game.type(5).select(1)
        assertFalse(almost.isComplete)
        assertEquals(80, almost.filledCount)

        val done = almost.type(3)
        assertTrue(done.isComplete)
        assertSame(done, done.type(1))
        assertSame(done, done.clear())
        assertSame(done, done.select(0))
    }

    @Test
    fun `a full board with a mistake is not complete`() {
        assertFalse(game.type(5).select(1).type(4).isComplete)
    }

    @Test
    fun `generated puzzles have one solution, valid and matching the difficulty as far as possible`() {
        for (difficulty in Difficulty.entries) {
            val tiers = mutableMapOf<Difficulty, Int>()
            var slowest = 0L
            var total = 0L

            repeat(25) { seed ->
                lateinit var game: SudokuGame
                val ms = measureTimeMillis { game = SudokuGame.generate(difficulty, Random(seed)) }
                slowest = maxOf(slowest, ms)
                total += ms

                assertValidGrid(game.solution)

                val puzzle = IntArray(81) { if (it in game.givens) game.solution[it] else 0 }
                assertEquals("$difficulty seed $seed", 1, independentCount(puzzle.copyOf()))
                assertEquals(1, SudokuGame.countSolutions(puzzle.copyOf(), 2))
                assertTrue("there must be something to play", game.givens.size < 81)

                val tier = SudokuSolver.rate(puzzle).tier
                tiers.merge(tier, 1, Int::plus)
            }

            println("sudoku $difficulty: tiers $tiers, avg ${total / 25} ms, slowest $slowest ms")

            // Easy puzzles must always be easy: nothing beyond singles may be needed.
            if (difficulty == Difficulty.EASY) assertEquals(setOf(Difficulty.EASY), tiers.keys)
        }
    }

    @Test
    fun `json round trip keeps the whole state`() {
        val played = SudokuGame.generate(Difficulty.MEDIUM, Random(1)).let { it.type(1).check() }

        assertEquals(played, SudokuGame.fromJson(played.toJson()))
    }

    private fun assertValidGrid(grid: List<Int>) {
        val units = List(9) { r -> List(9) { c -> r * 9 + c } } +
            List(9) { c -> List(9) { r -> r * 9 + c } } +
            List(9) { b -> List(9) { i -> (b / 3 * 3 + i / 3) * 9 + b % 3 * 3 + i % 3 } }

        for (unit in units) assertEquals((1..9).toList(), unit.map { grid[it] }.sorted())
    }

    /** Deliberately naive and unrelated to the production solver: counts solutions, stopping at 2. */
    private fun independentCount(grid: IntArray): Int {
        var best = -1
        var bestOptions = emptyList<Int>()

        for (cell in 0 until 81) {
            if (grid[cell] != 0) continue

            val row = cell / 9
            val col = cell % 9
            val seen = (0 until 9).flatMap { listOf(grid[row * 9 + it], grid[it * 9 + col]) } +
                (0 until 9).map { grid[(row / 3 * 3 + it / 3) * 9 + col / 3 * 3 + it % 3] }
            val options = (1..9).filter { it !in seen }

            if (best < 0 || options.size < bestOptions.size) {
                best = cell
                bestOptions = options
            }
        }

        if (best < 0) return 1

        var count = 0
        for (value in bestOptions) {
            grid[best] = value
            count += independentCount(grid)
            grid[best] = 0
            if (count >= 2) break
        }
        return count
    }
}
