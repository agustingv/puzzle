package com.agustingv.puzzles.game.path

import com.agustingv.puzzles.game.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PathGameTest {

    /**
     * 3x3 grid, reading-order cells:   0 1 2
     *                                  3 4 5
     *                                  6 7 8
     * Snake 0 1 2 5 4 3 6 7 8, with checkpoints 1 at cell 0, 2 at cell 4 and 3 at cell 8.
     */
    private val game = PathGame(3, Difficulty.EASY, mapOf(0 to 1, 4 to 2, 8 to 3))
    private val snake = listOf(0, 1, 2, 5, 4, 3, 6, 7, 8)

    private fun PathGame.draw(vararg cells: Int) = cells.fold(this) { g, c -> g.extend(c) }

    @Test
    fun `the line must start on checkpoint 1`() {
        assertSame(game, game.extend(1))
        assertEquals(listOf(0), game.extend(0).path)
    }

    @Test
    fun `the line only moves to orthogonal neighbours`() {
        val started = game.extend(0)

        assertSame("diagonal", started, started.extend(4))
        assertSame("far away", started, started.extend(2))
        assertSame("out of bounds", started, started.extend(9))
    }

    @Test
    fun `row wrap is not adjacency`() {
        val atRowEnd = game.draw(0, 1, 2)

        assertSame(atRowEnd, atRowEnd.extend(3))
    }

    @Test
    fun `checkpoints can not be skipped`() {
        val nearLast = game.draw(0, 1, 2, 5)

        assertSame("3 before 2", nearLast, nearLast.extend(8))
        assertEquals(5, nearLast.extend(4).path.size)
    }

    @Test
    fun `dragging onto the previous cell undoes one step, older cells are ignored`() {
        val line = game.draw(0, 1, 2, 5)

        assertEquals(listOf(0, 1, 2), line.extend(2).path)
        assertSame(line, line.extend(0))
    }

    @Test
    fun `pressing an earlier cell rewinds to it`() {
        assertEquals(listOf(0, 1), game.draw(0, 1, 2, 5).rewindTo(1).path)
    }

    @Test
    fun `reset empties the line`() {
        assertTrue(game.draw(0, 1, 2).reset().path.isEmpty())
    }

    @Test
    fun `covering every cell in order completes the game and freezes it`() {
        val done = snake.fold(game) { g, c -> g.extend(c) }

        assertTrue(done.isComplete)
        assertFalse(done.isFullButWrong)
        assertSame(done, done.extend(7))
        assertSame(done, done.rewindTo(0))
        assertSame(done, done.reset())
    }

    @Test
    fun `covering every cell without ending on the last checkpoint is not a win`() {
        // The line reaches checkpoint 2, the last one, at cell 8 and then carries on to cell 6.
        val endsElsewhere = PathGame(3, Difficulty.EASY, mapOf(0 to 1, 8 to 2))
        val line = listOf(0, 1, 2, 5, 8, 7, 4, 3, 6)
        val full = line.fold(endsElsewhere) { g, c -> g.extend(c) }

        assertEquals(9, full.path.size)
        assertFalse(full.isComplete)
        assertTrue(full.isFullButWrong)
    }

    @Test
    fun `every generated puzzle is solvable by drawing its own solution`() {
        for (difficulty in Difficulty.entries) {
            repeat(100) { seed ->
                val (puzzle, solution) = PathGame.generateWithSolution(difficulty, Random(seed))

                assertEquals(puzzle.totalCells, solution.size)
                assertEquals(1, puzzle.checkpoints[solution.first()])
                assertEquals(puzzle.checkpoints.size, puzzle.checkpoints[solution.last()])

                val played = solution.fold(puzzle) { g, c -> g.extend(c) }
                assertTrue("$difficulty seed $seed", played.isComplete)
            }
        }
    }

    @Test
    fun `grid sizes match the web version`() {
        val sizes = Difficulty.entries.map { PathGame.generate(it, Random(1)).size }
        assertEquals(listOf(5, 6, 7, 8), sizes)
    }

    @Test
    fun `json round trip keeps the whole state`() {
        val (puzzle, solution) = PathGame.generateWithSolution(Difficulty.HARD, Random(5))
        val played = solution.take(10).fold(puzzle) { g, c -> g.extend(c) }

        assertEquals(played, PathGame.fromJson(played.toJson()))
    }
}
