package com.butaca13.puzzles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SecretMessageTest {

    private class MemoryStore : KeyValueStore {
        val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) {
            values[key] = value
        }
        override fun remove(key: String) {
            values.remove(key)
        }
    }

    private val store = MemoryStore()
    private val progress = SecretProgress(store)

    private val strings = File("src/main/res/values/strings.xml").readText()
    private val catalog = File("src/main/java/com/butaca13/puzzles/ui/menu/GameCatalog.kt").readText()

    private fun titleOf(gameId: String): String {
        val resource = Regex("""\("$gameId",\s*"[^"]*",\s*R\.string\.(\w+),""").find(catalog)!!.groupValues[1]
        return Regex("""<string name="$resource">([^<]*)</string>""").find(strings)!!.groupValues[1]
    }

    @Test
    fun `the lit letters spell the message, in order`() {
        assertEquals("ERALAULTIMA", SecretMessage.clues.joinToString("") { it.letter.toString() })
        assertEquals((1..11).toList(), SecretMessage.clues.map { it.position })
    }

    @Test
    fun `every game but the newer ones has exactly one letter, and its title is the one the app shows`() {
        val games = Regex("""^\s+[A-Z_]+\("(\w+)",""", RegexOption.MULTILINE).findAll(catalog).map { it.groupValues[1] }.toList()
        // The message was made for the first eleven games: those that came later have no letter.
        val outside = setOf("klondike", "freecell", "spider", "pyramid")

        assertEquals((games - outside).sorted(), SecretMessage.clues.map { it.gameId }.sorted())
        for (clue in SecretMessage.clues) assertEquals(clue.gameId, titleOf(clue.gameId), clue.title)
    }

    @Test
    fun `a clue never uses the last letter of a title, which has its own style`() {
        for (clue in SecretMessage.clues) {
            val letters = clue.title.count { it != ' ' }
            assertTrue(clue.title, clue.letterIndex in 0 until letters - 1)
        }
    }

    @Test
    fun `the second letter stays the exception`() {
        assertTrue(SecretMessage.clues.count { it.letterIndex == 1 } <= 2)
    }

    @Test
    fun `a title that is not a game's lights no letter, so that it is not taken for a clue`() {
        assertEquals(-1, SecretMessage.letterIndexOf("Letras"))
        assertEquals(1, SecretMessage.letterIndexOf("Sudoku"))
    }

    @Test
    fun `spaces, capitals and accents do not matter in the answer`() {
        for (guess in listOf("ERA LA ÚLTIMA", "era la última", "eralaultima", "  Era  la Ultima ")) {
            assertTrue(guess, SecretMessage.isAnswer(guess))
        }
        for (guess in listOf("", "era la", "era la ultimas", "es la ultima")) {
            assertFalse(guess, SecretMessage.isAnswer(guess))
        }
    }

    @Test
    fun `only the first win of each game gives its clue, and it is remembered`() {
        val clue = progress.recordWin("sudoku")

        assertEquals('U', clue?.letter)
        assertEquals(6, clue?.position)
        assertNull(progress.recordWin("sudoku"))
        assertEquals(listOf("sudoku"), SecretProgress(store).found.map { it.gameId })
    }

    @Test
    fun `clues come back in the message's order, however they were won`() {
        progress.recordWin("kakuro")
        progress.recordWin("race")
        progress.recordWin("sudoku")

        assertEquals(listOf(1, 6, 11), progress.found.map { it.position })
    }

    @Test
    fun `unknown games give no clue`() {
        assertNull(progress.recordWin("test"))
        assertTrue(progress.found.isEmpty())
    }

    @Test
    fun `a wrong guess changes nothing and a right one stays solved`() {
        assertFalse(progress.solve("no es"))
        assertFalse(progress.solved)

        assertTrue(progress.solve("era la última"))
        assertTrue(SecretProgress(store).solved)
    }

    @Test
    fun `once solved, wins give no more clues`() {
        progress.solve("eralaultima")

        assertNull(progress.recordWin("sudoku"))
    }

    @Test
    fun `corrupt progress reads as no clues found`() {
        store.values["secret/found"] = ",,nothing,"

        assertTrue(progress.found.isEmpty())
        assertNotNull(progress.recordWin("sudoku"))
    }

    private val sessionRules = GameRules<String>(
        id = "sudoku",
        encode = { it },
        decode = { it },
        isOver = { it == "won" || it == "lost" },
        isWon = { it == "won" },
        variantOf = { "EASY" },
    )

    @Test
    fun `winning a game reveals its clue, and a new game clears it`() {
        val session = GameSession(store, sessionRules)
        session.start("playing")
        assertNull(session.lastClue)

        session.update("won")
        assertEquals("Sudoku", session.lastClue?.title)

        session.start("playing")
        assertNull(session.lastClue)

        session.update("won")
        assertNull("only the first win of each game", session.lastClue)
    }

    @Test
    fun `losing reveals nothing`() {
        val session = GameSession(store, sessionRules)
        session.start("playing")

        session.update("lost")

        assertNull(session.lastClue)
        assertTrue(progress.found.isEmpty())
    }
}
