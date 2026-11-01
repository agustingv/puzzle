package com.agustingv.puzzles.game.kakuro

import com.agustingv.puzzles.game.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random
import kotlin.system.measureTimeMillis

class KakuroGameTest {

    /**
     * 3x3 grid with a 2x2 white interior, cells 4 5 / 7 8, filled in as 1 3 / 2 4:
     * across sums 4 and 6, down sums 3 and 7.
     */
    private val game = KakuroGame(
        size = 3,
        difficulty = Difficulty.EASY,
        blocked = listOf(true, true, true, true, false, false, true, false, false),
        solution = listOf(0, 0, 0, 0, 1, 3, 0, 2, 4),
        across = mapOf(3 to 4, 6 to 6),
        down = mapOf(1 to 3, 2 to 7),
    )

    @Test
    fun `the cursor starts on the first white cell and blocked cells can not be selected`() {
        assertEquals(4, game.cursor)
        assertEquals(8, game.select(8).cursor)
        assertSame(game, game.select(0))
        assertSame(game, game.select(99))
    }

    @Test
    fun `mistakes are only revealed by checking and editing clears the mark`() {
        val typed = game.type(9)
        assertTrue(typed.markedWrong.isEmpty())

        val checked = typed.check()
        assertEquals(setOf(4), checked.markedWrong)
        assertTrue(checked.type(1).markedWrong.isEmpty())
        assertTrue(checked.clear().markedWrong.isEmpty())
    }

    @Test
    fun `only digits 1 to 9 are accepted`() {
        assertSame(game, game.type(0))
        assertSame(game, game.type(10))
    }

    @Test
    fun `filling every cell correctly completes the game and freezes it`() {
        val almost = game.type(1).select(5).type(3).select(7).type(2).select(8)
        assertFalse(almost.isComplete)
        assertEquals(3, almost.filledCount)

        val done = almost.type(4)
        assertTrue(done.isComplete)
        assertSame(done, done.type(1))
        assertSame(done, done.clear())
        assertSame(done, done.select(4))
    }

    @Test
    fun `the solver counts solutions`() {
        fun count(across: Map<Int, Int>, down: Map<Int, Int>) =
            KakuroSolver.countSolutions(game.blocked.toBooleanArray(), across, down, 3, 5)

        assertEquals("the example is unique", 1, count(game.across, game.down))
        // All sums 5: a b / b a, with (a, b) any of (1,4) (2,3) (3,2) (4,1).
        assertEquals("ambiguous", 4, count(mapOf(3 to 5, 6 to 5), mapOf(1 to 5, 2 to 5)))
        assertEquals("impossible sums", 0, count(mapOf(3 to 2, 6 to 6), mapOf(1 to 3, 2 to 5)))
    }

    @Test
    fun `generated puzzles are unique and their clues match the solution, at every difficulty`() {
        val sizes = mapOf(
            Difficulty.EASY to 4, Difficulty.MEDIUM to 4, Difficulty.HARD to 5, Difficulty.EXPERT to 5,
        )

        for (difficulty in Difficulty.entries) {
            var slowest = 0L
            var total = 0L
            val rounds = 25

            repeat(rounds) { seed ->
                lateinit var game: KakuroGame
                val ms = measureTimeMillis { game = KakuroGame.generate(difficulty, Random(seed)) }
                slowest = maxOf(slowest, ms)
                total += ms

                assertEquals(sizes[difficulty], game.size)
                assertTrue(game.activeCells >= 2)

                val runs = runsOf(game)
                for ((cells, sum) in runs) {
                    assertEquals(sum, cells.sumOf { game.solution[it] })
                    assertEquals("repeated digit", cells.size, cells.map { game.solution[it] }.distinct().size)
                    assertTrue(cells.size in 2..9)
                }

                val whites = game.blocked.indices.filter { !game.blocked[it] }
                for (cell in whites) {
                    assertEquals("every white cell is in two runs", 2, runs.count { cell in it.first })
                    assertTrue(game.solution[cell] in 1..9)
                }

                assertEquals("$difficulty seed $seed", 1, bruteForce(whites, runs, IntArray(game.solution.size), 0))
            }

            println("kakuro $difficulty: avg ${total / rounds} ms, slowest $slowest ms")
        }
    }

    @Test
    fun `json round trip keeps the whole state`() {
        val played = KakuroGame.generate(Difficulty.EASY, Random(2)).type(1).check()

        assertEquals(played, KakuroGame.fromJson(played.toJson()))
    }

    private fun runsOf(game: KakuroGame): List<Pair<List<Int>, Int>> = buildList {
        val size = game.size
        for ((cell, sum) in game.across) {
            add((cell + 1 until (cell / size + 1) * size).takeWhile { !game.blocked[it] } to sum)
        }
        for ((cell, sum) in game.down) {
            add(generateSequence(cell + size) { it + size }.takeWhile { it < game.blocked.size && !game.blocked[it] }.toList() to sum)
        }
    }

    /** Deliberately naive and unrelated to the production solver: counts solutions, stopping at 2. */
    private fun bruteForce(whites: List<Int>, runs: List<Pair<List<Int>, Int>>, grid: IntArray, index: Int): Int {
        if (index == whites.size) return 1

        val cell = whites[index]
        var count = 0

        for (value in 1..9) {
            grid[cell] = value
            val fine = runs.filter { cell in it.first }.all { (cells, sum) ->
                val placed = cells.map { grid[it] }.filter { it != 0 }
                placed.distinct().size == placed.size &&
                    placed.sum() <= sum &&
                    (placed.size < cells.size || placed.sum() == sum)
            }
            if (fine) count += bruteForce(whites, runs, grid, index + 1)
            grid[cell] = 0
            if (count >= 2) break
        }

        return count
    }
}
