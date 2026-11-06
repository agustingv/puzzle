package com.butaca13.puzzles.game.wordsearch

import com.butaca13.puzzles.game.Difficulty
import com.butaca13.puzzles.game.foldAccents
import com.butaca13.puzzles.game.word.WordList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class WordSearchGameTest {

    private val pool = WordList.parse(File("src/main/assets/words.json").readText()).values.flatten()

    /** 4x4 board with "sol" on the top row and "mar" on part of the main diagonal. */
    private val game = WordSearchGame(
        size = 4,
        difficulty = Difficulty.EASY,
        grid = "solx" + "xmxx" + "xxax" + "xxxr",
        words = listOf(PlacedWord("sol", listOf(0, 1, 2)), PlacedWord("mar", listOf(5, 10, 15))),
    )

    @Test
    fun `selecting a word finds it, in either direction`() {
        assertTrue(game.select(0, 2).words[0].found)
        assertTrue(game.select(2, 0).words[0].found)
        assertTrue(game.select(15, 5).words[1].found)
    }

    @Test
    fun `partial, longer or crooked selections find nothing`() {
        assertEquals(0, game.select(0, 1).foundCount)
        assertEquals(0, game.select(0, 3).foundCount)
        assertEquals(0, game.select(0, 6).foundCount)
    }

    @Test
    fun `finding every word completes the game`() {
        val done = game.select(0, 2).select(5, 15)

        assertEquals(2, done.foundCount)
        assertTrue(done.isComplete)
    }

    @Test
    fun `two taps select, tapping the same cell twice cancels`() {
        val started = game.tap(0)
        assertEquals(0, started.pendingStart)

        val found = started.tap(2)
        assertNull(found.pendingStart)
        assertTrue(found.words[0].found)

        assertNull(started.tap(0).pendingStart)
        assertEquals(0, started.tap(0).foundCount)
    }

    @Test
    fun `a straight path does not wrap around the row end`() {
        assertEquals(listOf(0, 1, 2), WordSearchGame.pathBetween(0, 2, 4))
        assertEquals(listOf(3, 6, 9, 12), WordSearchGame.pathBetween(3, 12, 4))
        assertNull(WordSearchGame.pathBetween(3, 4, 4))
        assertNull(WordSearchGame.pathBetween(5, 5, 4))
    }

    @Test
    fun `a crooked drag snaps to the nearest direction`() {
        val size = 10
        val start = 44 // row 4, column 4

        assertEquals("nearly horizontal", 48, WordSearchGame.snapEnd(start, 5, 8, size))
        assertEquals("nearly vertical", 84, WordSearchGame.snapEnd(start, 8, 5, size))
        assertEquals("nearly diagonal", 88, WordSearchGame.snapEnd(start, 8, 7, size))
        assertEquals("up and left", 0, WordSearchGame.snapEnd(start, 0, 1, size))
        assertEquals("no movement", start, WordSearchGame.snapEnd(start, 4, 4, size))
    }

    @Test
    fun `a snapped drag stays on the board`() {
        // From row 8, column 8 a down-right diagonal only has one more cell.
        assertEquals(99, WordSearchGame.snapEnd(88, 9, 9, 10))
        assertEquals(99, WordSearchGame.snapEnd(88, 12, 12, 10))
    }

    @Test
    fun `generated boards contain every word on a straight line, at every difficulty`() {
        val sizes = mapOf(
            Difficulty.EASY to 8, Difficulty.MEDIUM to 10, Difficulty.HARD to 12, Difficulty.EXPERT to 15,
        )
        val counts = mapOf(
            Difficulty.EASY to 7, Difficulty.MEDIUM to 10, Difficulty.HARD to 12, Difficulty.EXPERT to 15,
        )

        for (difficulty in Difficulty.entries) {
            repeat(100) { seed ->
                val game = WordSearchGame.generate(difficulty, pool, Random(seed))

                assertEquals(sizes[difficulty], game.size)
                assertEquals(game.size * game.size, game.grid.length)
                assertEquals("$difficulty seed $seed", counts[difficulty], game.words.size)
                assertTrue(game.grid.all { it.isLetter() && foldAccents(it.toString())[0] == it })

                for (word in game.words) {
                    val letters = word.cells.map { game.grid[it] }.joinToString("")
                    assertEquals(foldAccents(word.word), letters)
                    assertEquals(word.cells, WordSearchGame.pathBetween(word.cells.first(), word.cells.last(), game.size))
                }
            }
        }
    }

    @Test
    fun `every generated game can be finished by selecting its words`() {
        val game = WordSearchGame.generate(Difficulty.EXPERT, pool, Random(7))
        val done = game.words.fold(game) { g, w -> g.select(w.cells.first(), w.cells.last()) }

        assertTrue(done.isComplete)
        assertFalse(game.isComplete)
    }

    @Test
    fun `json round trip keeps the whole state`() {
        val played = WordSearchGame.generate(Difficulty.MEDIUM, pool, Random(3)).let { g ->
            g.select(g.words[0].cells.first(), g.words[0].cells.last()).tap(5)
        }

        assertEquals(played, WordSearchGame.fromJson(played.toJson()))
    }
}
