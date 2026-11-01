package com.agustingv.puzzles.game

import com.agustingv.puzzles.game.kakuro.KakuroSolver
import com.agustingv.puzzles.game.sudoku.SudokuSolver
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The solvers are the delicate part of the port, so they are compared against the original.
 * web_solver_fixtures.json holds puzzles rated and counted by the web version's PHP solvers.
 */
class WebSolverParityTest {

    private val fixtures = JSONObject(File("src/test/resources/web_solver_fixtures.json").readText())

    @Test
    fun `sudoku ratings match the web version on every fixture`() {
        val puzzles = fixtures.getJSONArray("sudoku")
        val tiers = mutableMapOf<Difficulty, Int>()
        assertTrue(puzzles.length() >= 400)

        for (i in 0 until puzzles.length()) {
            val puzzle = puzzles.getJSONObject(i)
            val grid = puzzle.getString("grid")
            val rating = SudokuSolver.rate(IntArray(81) { grid[it].digitToInt() })
            val expectedTier = Difficulty.entries[puzzle.getInt("tier") - 1]

            assertEquals("solved, puzzle $i: $grid", puzzle.getBoolean("solved"), rating.solved)
            assertEquals("tier, puzzle $i: $grid", expectedTier, rating.tier)
            tiers.merge(expectedTier, 1, Int::plus)
        }

        // A comparison proves little unless it exercised every technique tier.
        assertEquals(Difficulty.entries.toSet(), tiers.keys)
        println("sudoku fixtures by tier: $tiers")
    }

    @Test
    fun `kakuro solution counts match the web version on every fixture`() {
        val puzzles = fixtures.getJSONArray("kakuro")
        val counts = mutableMapOf<Int, Int>()
        assertTrue(puzzles.length() >= 200)

        for (i in 0 until puzzles.length()) {
            val puzzle = puzzles.getJSONObject(i)
            val size = puzzle.getInt("size")
            val cellCount = size * size

            val blockedRows = puzzle.getJSONArray("blocked")
            val blocked = BooleanArray(cellCount) { cell ->
                blockedRows.getJSONArray(cell / size).getInt(cell % size) == 1
            }

            val expected = puzzle.getInt("count")
            val actual = KakuroSolver.countSolutions(
                blocked,
                clues(puzzle.getJSONArray("across"), size),
                clues(puzzle.getJSONArray("down"), size),
                size,
                5,
            )

            assertEquals("puzzle $i: $puzzle", expected, actual)
            counts.merge(expected, 1, Int::plus)
        }

        assertTrue("fixtures must include unique and ambiguous puzzles: $counts", 1 in counts && counts.keys.any { it > 1 })
        println("kakuro fixtures by solution count: $counts")
    }

    private fun clues(rows: JSONArray, size: Int): Map<Int, Int> = buildMap {
        for (cell in 0 until size * size) {
            val row = rows.getJSONArray(cell / size)
            if (!row.isNull(cell % size)) put(cell, row.getInt(cell % size))
        }
    }
}
