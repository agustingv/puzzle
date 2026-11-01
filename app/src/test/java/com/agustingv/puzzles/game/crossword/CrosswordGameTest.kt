package com.agustingv.puzzles.game.crossword

import com.agustingv.puzzles.game.Difficulty
import com.agustingv.puzzles.game.foldAccents
import com.agustingv.puzzles.game.word.WordList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class CrosswordGameTest {

    private val pool = WordList.parse(File("src/main/assets/words.json").readText()).values.flatten()
    private val clues = WordList.parseClues(File("src/main/assets/clues.json").readText())

    /**
     * 4x4 grid:   s o l #      "sol" across (1) and "sal" down (1) share the "s" at cell 0.
     *             a # # #
     *             l # # #
     *             # # # #
     */
    private val game = CrosswordGame(
        size = 4,
        difficulty = Difficulty.EASY,
        solution = "sol#" + "a###" + "l###" + "####",
        words = listOf(
            CrosswordWord("sol", listOf(0, 1, 2), CrosswordDirection.ACROSS, 1, "Astro"),
            CrosswordWord("sal", listOf(0, 4, 8), CrosswordDirection.DOWN, 1, "Condimento"),
        ),
    )

    private fun CrosswordGame.typed(text: String) = text.fold(this) { g, c -> g.type(c) }

    @Test
    fun `starts empty, on the first letter of the first word`() {
        assertEquals("   #" + " ###" + " ###" + "####", game.input)
        assertEquals(0, game.cursorCell)
        assertEquals(0, game.cursorWord)
    }

    @Test
    fun `typing fills the cell and advances, staying on the last one`() {
        val typed = game.typed("so")
        assertEquals("so #", typed.input.take(4))
        assertEquals(2, typed.cursorCell)

        val end = typed.typed("lx")
        assertEquals("sox#", end.input.take(4))
        assertEquals(2, end.cursorCell)
    }

    @Test
    fun `accents are folded when typed`() {
        assertEquals('o', game.type('ó').input[0])
    }

    @Test
    fun `delete clears the cell, or steps back when it is already empty`() {
        val typed = game.typed("so")

        val stepped = typed.delete()
        assertEquals("s  #", stepped.input.take(4))
        assertEquals(1, stepped.cursorCell)

        val back = stepped.delete()
        assertEquals("   #", back.input.take(4))
        assertEquals(0, back.cursorCell)
    }

    @Test
    fun `delete on the first cell of an empty word stays put`() {
        val deleted = game.delete()

        assertEquals(0, deleted.cursorCell)
        assertEquals(game.input, deleted.input)
    }

    @Test
    fun `tapping another cell selects its word, blocked cells are ignored`() {
        assertEquals(1, game.tap(4).cursorWord)
        assertEquals(4, game.tap(4).cursorCell)
        assertSame(game, game.tap(3))
    }

    @Test
    fun `tapping the selected shared cell toggles direction`() {
        assertEquals(1, game.tap(0).cursorWord)
        assertEquals(0, game.tap(0).tap(0).cursorWord)
    }

    @Test
    fun `tapping the selected cell of a single word keeps that word`() {
        val onOne = game.tap(1)

        assertEquals(0, onOne.tap(1).cursorWord)
    }

    @Test
    fun `moving between words wraps and lands on the first empty cell`() {
        val next = game.type('s').moveWord(1)
        assertEquals(1, next.cursorWord)
        assertEquals(4, next.cursorCell)

        assertEquals(0, next.moveWord(1).cursorWord)
        assertEquals(1, game.moveWord(-1).cursorWord)
    }

    @Test
    fun `typing reveals nothing until checked`() {
        val typed = game.typed("sol")

        assertTrue(typed.solved.isEmpty())
        assertEquals(ClueState.PENDING, typed.clueState(0))
    }

    @Test
    fun `check marks right and filled-wrong words, leaving incomplete ones pending`() {
        val checked = game.typed("sol").check()
        assertEquals(setOf(0), checked.solved)
        assertEquals(ClueState.SOLVED, checked.clueState(0))
        assertEquals(ClueState.PENDING, checked.clueState(1))

        // "sal" down filled as "xal": complete and wrong.
        val wrong = game.tap(0).tap(0).tap(0).type('x').typed("al").check()
        assertEquals(1, wrong.cursorWord)
        assertEquals(ClueState.WRONG, wrong.clueState(1))
    }

    @Test
    fun `solving every word completes the game and freezes it`() {
        val done = game.typed("sol").tap(4).typed("al").check()

        assertTrue(done.isComplete)
        assertTrue(done.isOver)
        assertSame(done, done.type('x'))
        assertSame(done, done.delete())
        assertSame(done, done.tap(1))
        assertSame(done, done.check())
        assertSame(done, done.finish())
    }

    @Test
    fun `finishing early is a loss, unsolved words read as wrong`() {
        val finished = game.typed("sol").finish()

        assertTrue(finished.isOver)
        assertFalse(finished.isComplete)
        assertEquals(ClueState.SOLVED, finished.clueState(0))
        assertEquals(ClueState.WRONG, finished.clueState(1))
    }

    @Test
    fun `generated crosswords are consistent at every difficulty`() {
        val sizes = mapOf(
            Difficulty.EASY to 8, Difficulty.MEDIUM to 10, Difficulty.HARD to 12, Difficulty.EXPERT to 15,
        )

        for (difficulty in Difficulty.entries) {
            var fewest = Int.MAX_VALUE
            var most = 0

            repeat(100) { seed ->
                val game = CrosswordGame.generate(difficulty, pool, clues, Random(seed))
                fewest = minOf(fewest, game.words.size)
                most = maxOf(most, game.words.size)

                assertEquals(sizes[difficulty], game.size)
                assertEquals(game.size * game.size, game.solution.length)

                val letterCells = game.solution.indices.filter { !game.isBlocked(it) }.toSet()
                assertEquals(letterCells, game.words.flatMap { it.cells }.toSet())

                for (word in game.words) {
                    assertEquals(foldAccents(word.word), word.cells.map { game.solution[it] }.joinToString(""))
                    assertEquals(word.number, game.numbers[word.cells.first()])
                    assertEquals(clues[word.word], word.clue)
                }

                // Every word must be connected to the rest: one component across the whole board.
                val seen = mutableSetOf(game.words.first().cells.first())
                val stack = ArrayDeque(seen)
                while (stack.isNotEmpty()) {
                    val cell = stack.removeLast()
                    val row = cell / game.size
                    val neighbours = listOfNotNull(
                        (cell - game.size).takeIf { row > 0 },
                        (cell + game.size).takeIf { row < game.size - 1 },
                        (cell - 1).takeIf { cell % game.size > 0 },
                        (cell + 1).takeIf { cell % game.size < game.size - 1 },
                    )
                    for (n in neighbours) if (n in letterCells && seen.add(n)) stack.addLast(n)
                }
                assertEquals("$difficulty seed $seed", letterCells, seen)

                // Solvable through the same operations the player has.
                val solved = game.words.indices.fold(game) { g, i ->
                    game.words[i].cells.fold(g.selectWord(i).let { it.copy(cursorCell = game.words[i].cells.first()) }) { acc, cell ->
                        acc.type(game.solution[cell])
                    }
                }.check()
                assertTrue("$difficulty seed $seed", solved.isComplete)
            }

            println("crossword $difficulty: $fewest..$most words")
            assertTrue("$difficulty placed only $fewest words", fewest >= sizes.getValue(difficulty) * 2 / 3)
        }
    }

    @Test
    fun `json round trip keeps the whole state`() {
        val played = CrosswordGame.generate(Difficulty.MEDIUM, pool, clues, Random(3)).typed("ab").moveWord(1).check()

        assertEquals(played, CrosswordGame.fromJson(played.toJson()))
    }
}
