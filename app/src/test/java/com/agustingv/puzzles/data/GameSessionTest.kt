package com.agustingv.puzzles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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

class GameSessionTest {

    /** A minimal game: progress as a string; "won" and "lost" finish it. */
    private val rules = GameRules<String>(
        id = "test",
        encode = { it },
        decode = { json -> json.also { require(it != "corrupt") } },
        isOver = { it == "won" || it == "lost" },
        isWon = { it == "won" },
        variantOf = { "EASY" },
    )

    private val store = MemoryStore()
    private val records = Records(store)

    private fun session() = GameSession(store, rules)

    @Test
    fun `a game and its time survive the app being closed`() {
        val first = session()
        first.start("playing")
        first.tick()
        first.tick()
        first.update("further")

        val reopened = session()
        assertEquals("further", reopened.game)
        assertEquals(2, reopened.elapsedSeconds)
    }

    @Test
    fun `time saved by a tick alone is kept too`() {
        val first = session()
        first.start("playing")
        repeat(5) { first.tick() }

        assertEquals(5, session().elapsedSeconds)
    }

    @Test
    fun `only an unfinished game is offered to continue`() {
        val session = session()
        assertNull(session.saved)

        session.start("playing")
        session.tick()
        assertEquals(SavedGame("EASY", 1), session.saved)

        session.update("lost")
        assertNull(session.saved)
    }

    @Test
    fun `the clock stops when the game is over, and before there is one`() {
        val session = session()
        session.tick()
        assertEquals(0, session.elapsedSeconds)

        session.start("playing")
        session.tick()
        session.update("won")
        session.tick()
        assertEquals(1, session.elapsedSeconds)
    }

    @Test
    fun `starting a new game resets time and result`() {
        val session = session()
        session.start("playing")
        session.tick()
        session.update("won")
        assertNotNull(session.lastRecord)

        session.start("again")
        assertEquals(0, session.elapsedSeconds)
        assertNull(session.lastRecord)
    }

    @Test
    fun `winning files one record with the elapsed time`() {
        val session = session()
        session.start("playing")
        repeat(30) { session.tick() }
        session.update("won")

        assertEquals(listOf(30), records.top("test", "EASY"))
        assertEquals(RecordResult(30, rank = 1, best = 30), session.lastRecord)
        assertTrue(session.lastRecord!!.isNewBest)
    }

    @Test
    fun `a change after winning does not file it twice`() {
        val session = session()
        session.start("playing")
        session.update("won")
        session.update("won")

        assertEquals(1, records.top("test", "EASY").size)
    }

    @Test
    fun `reopening a game already won does not file it again`() {
        val first = session()
        first.start("playing")
        first.update("won")

        val reopened = session()
        assertEquals("won", reopened.game)
        reopened.update("won")

        assertEquals(1, records.top("test", "EASY").size)
        assertNull(reopened.lastRecord)
    }

    @Test
    fun `losing files no record`() {
        val session = session()
        session.start("playing")
        session.update("lost")

        assertTrue(records.top("test", "EASY").isEmpty())
        assertNull(session.lastRecord)
    }

    @Test
    fun `a new game after a win can be won and filed again`() {
        val session = session()
        session.start("playing")
        session.update("won")
        session.start("playing")
        session.tick()
        session.update("won")

        assertEquals(listOf(0, 1), records.top("test", "EASY"))
    }

    @Test
    fun `corrupt saved data starts clean instead of crashing`() {
        store.values["game/test/state"] = "corrupt"

        assertNull(session().game)
        assertFalse("the bad data must not stay", "game/test/state" in store.values)
    }

    @Test
    fun `a generation interrupted by a kill can be finished, and a finished one is forgotten`() {
        val first = session()
        first.start("old")
        first.startGenerating("HARD")
        assertNull(first.game)

        val reopened = session()
        assertEquals("HARD", reopened.pendingVariant)
        assertNull(reopened.game)

        reopened.start("new")
        assertNull(reopened.pendingVariant)
    }

    @Test
    fun `extra state is kept and cleared with a new game`() {
        val first = session()
        first.start("playing")
        first.saveExtra("abc")
        assertEquals("abc", session().loadExtra())

        first.start("again")
        assertNull(session().loadExtra())
    }

    private fun eventsSession() = GameSession(
        store,
        GameRules<String>(
            id = "test",
            encode = { it },
            decode = { it },
            isOver = { it == "won" || it == "lost" },
            isWon = { it == "won" },
            variantOf = { "EASY" },
            eventFor = { previous, next -> GameEvent.HIT.takeIf { next.length > previous.length } },
        ),
    )

    @Test
    fun `a move reports what the game's rules say it means`() {
        val session = eventsSession()
        session.start("a")
        assertNull("starting a game is not an event", session.feedback)

        session.update("ab")
        assertEquals(GameEvent.HIT, session.feedback?.event)

        val before = session.feedback
        session.update("cd")
        assertEquals("nothing new to report", before, session.feedback)
    }

    @Test
    fun `winning and losing take priority over the move's own event`() {
        val winner = eventsSession()
        winner.start("a")
        winner.update("won")
        assertEquals(GameEvent.WIN, winner.feedback?.event)

        val loser = eventsSession()
        loser.start("a")
        loser.update("lost")
        assertEquals(GameEvent.LOSE, loser.feedback?.event)
    }

    @Test
    fun `equal events in a row can be told apart`() {
        val session = eventsSession()
        session.start("a")
        session.signal(GameEvent.MISS)
        val first = session.feedback

        session.signal(GameEvent.MISS)

        assertEquals(GameEvent.MISS, session.feedback?.event)
        assertFalse(first == session.feedback)
    }

    @Test
    fun `reopening a finished game celebrates nothing`() {
        val first = eventsSession()
        first.start("a")
        first.update("won")

        assertNull(eventsSession().feedback)
    }
}


class RecordsTest {

    private val records = Records(MemoryStore())

    private fun add(seconds: Int) = records.add("g", "v", seconds)

    @Test
    fun `times are kept fastest first`() {
        add(30)
        add(10)
        add(20)

        assertEquals(listOf(10, 20, 30), records.top("g", "v"))
    }

    @Test
    fun `ranks and best are reported`() {
        assertEquals(RecordResult(30, 1, 30), add(30))
        assertEquals(RecordResult(40, 2, 30), add(40))
        assertEquals(RecordResult(20, 1, 20), add(20))
    }

    @Test
    fun `equalling the best is not a new best`() {
        add(30)

        val tie = add(30)
        assertEquals(2, tie.rank)
        assertFalse(tie.isNewBest)
    }

    @Test
    fun `only the best ten are kept, slower times get no rank`() {
        (1..10).forEach { add(it) }

        val slow = add(99)
        assertNull(slow.rank)
        assertEquals(1, slow.best)
        assertEquals((1..10).toList(), records.top("g", "v"))

        val fast = add(0)
        assertEquals(1, fast.rank)
        assertEquals(listOf(0) + (1..9).toList(), records.top("g", "v"))
    }

    @Test
    fun `games and variants do not mix`() {
        records.add("a", "EASY", 1)
        records.add("a", "HARD", 2)
        records.add("b", "EASY", 3)

        assertEquals(listOf(1), records.top("a", "EASY"))
        assertEquals(listOf(2), records.top("a", "HARD"))
        assertEquals(listOf(3), records.top("b", "EASY"))
    }

    @Test
    fun `corrupt records read as empty`() {
        val store = MemoryStore()
        store.put("records/g/v", "not json")

        assertTrue(Records(store).top("g", "v").isEmpty())
    }
}
