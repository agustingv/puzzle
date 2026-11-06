package com.butaca13.puzzles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarPrizesTest {

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
    private val records = Records(store)

    private fun piece(id: String) = AvatarCatalog.accessory(id)!!

    /** A game won the way GameSession files it: the record first, then the prizes. */
    private fun AvatarPrizes.winGame(game: String, variant: String = "EASY", seconds: Int = 60): List<String> =
        recordWin(variant, records.add(game, variant, seconds)).map { it.id }

    @Test
    fun `every prize rule names a prize of the catalog, and every prize has a rule`() {
        val rules = setOf(AvatarPrizes.CROWN, AvatarPrizes.TROPHY, AvatarPrizes.CAP, AvatarPrizes.GLASSES, AvatarPrizes.TOP_HAT)
        assertEquals(AvatarCatalog.accessories.filter { it.prize }.map { it.id }.toSet(), rules)
    }

    @Test
    fun `a new player has no prizes, and a plain win earns none`() {
        val prizes = AvatarPrizes(store)

        assertTrue(AvatarCatalog.accessories.filter { it.prize }.none(prizes::has))
        assertEquals(emptyList<String>(), prizes.winGame("sudoku"))
        assertEquals(1, prizes.wins)
    }

    @Test
    fun `winning on expert earns the cap, once`() {
        val prizes = AvatarPrizes(store)

        assertEquals(listOf("birrete"), prizes.winGame("sudoku", "EXPERT"))
        assertEquals(emptyList<String>(), prizes.winGame("kakuro", "EXPERT"))
        assertTrue(prizes.has(piece("birrete")))
    }

    @Test
    fun `beating a best time earns the glasses, a first time or a slower one does not`() {
        val prizes = AvatarPrizes(store)

        assertEquals(emptyList<String>(), prizes.winGame("path", seconds = 60))
        assertEquals(emptyList<String>(), prizes.winGame("path", seconds = 70))
        assertEquals(emptyList<String>(), prizes.winGame("garden", seconds = 10))
        assertEquals(listOf("gafas"), prizes.winGame("path", seconds = 50))
    }

    @Test
    fun `winning every game earns the trophy, with the last one`() {
        val prizes = AvatarPrizes(store)
        val games = AvatarPrizes.GAMES

        games.dropLast(1).forEach { prizes.winGame(it) }
        prizes.winGame(games.first(), variant = "5")
        assertEquals(games.size - 1, prizes.gamesWon)
        assertFalse(prizes.has(piece("trofeo")))

        assertEquals(listOf("trofeo"), prizes.winGame(games.last()))
        assertEquals(games.size, prizes.gamesWon)
    }

    @Test
    fun `the fiftieth win earns the top hat`() {
        val prizes = AvatarPrizes(store)

        repeat(AvatarPrizes.WINS_FOR_TOP_HAT - 1) { assertFalse("chistera" in prizes.winGame("memorama", seconds = 100 + it)) }
        assertEquals(listOf("chistera"), prizes.winGame("memorama", seconds = 999))
        assertEquals(AvatarPrizes.WINS_FOR_TOP_HAT, prizes.wins)
    }

    @Test
    fun `several prizes at once come in the catalog's order`() {
        val prizes = AvatarPrizes(store)
        prizes.winGame("sudoku", "EXPERT", seconds = 60)
        store.values.remove("avatar/won")

        // Faster on expert: the glasses and the cap together.
        assertEquals(listOf("gafas", "birrete"), prizes.winGame("sudoku", "EXPERT", seconds = 50))
    }

    @Test
    fun `solving the message earns the crown, once`() {
        val prizes = AvatarPrizes(store)

        assertEquals("corona", prizes.recordSecretSolved()?.id)
        assertNull(prizes.recordSecretSolved())
    }

    @Test
    fun `a player from before prizes gets what their records and the message already earn`() {
        for (game in AvatarPrizes.GAMES) records.add(game, "MEDIUM", 30)
        records.add("kakuro", "EXPERT", 300)
        SecretProgress(store).solve("era la última")

        val prizes = AvatarPrizes(store)

        assertEquals(AvatarPrizes.GAMES.size + 1, prizes.wins)
        for (id in listOf("corona", "trofeo", "birrete")) assertTrue(id, prizes.has(piece(id)))
        assertFalse("not enough wins", prizes.has(piece("chistera")))
        assertFalse("a beaten time can not be told from records", prizes.has(piece("gafas")))
    }

    @Test
    fun `catching up counts the records only once`() {
        records.add("sudoku", "EASY", 30)
        AvatarPrizes(store).winGame("sudoku")

        assertEquals(2, AvatarPrizes(store).wins)
    }

    @Test
    fun `the session hands over the prizes a win earns, and forgets them on a new game`() {
        val rules = GameRules<String>(
            id = "sudoku",
            encode = { it },
            decode = { it },
            isOver = { it == "won" },
            isWon = { it == "won" },
            variantOf = { "EXPERT" },
        )
        val session = GameSession(store, rules)

        session.start("playing")
        session.update("won")
        assertEquals(listOf("birrete"), session.lastPrizes.map { it.id })

        session.start("playing")
        assertEquals(emptyList<AvatarPiece>(), session.lastPrizes)
    }
}
