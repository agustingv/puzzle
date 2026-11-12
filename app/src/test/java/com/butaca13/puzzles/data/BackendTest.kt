package com.butaca13.puzzles.data

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import kotlin.random.Random

class BackendTest {

    /** Only a build for Firebase may start it. */
    private val noFirebase: () -> FirebaseCalls = { throw AssertionError("Firebase arrancado sin pedirlo") }

    @Test
    fun kindsAreNamedAsGradleTakesThem() {
        assertEquals(BackendKind.NONE, BackendKind.of("none"))
        assertEquals(BackendKind.API, BackendKind.of("api"))
        assertEquals(BackendKind.FIREBASE, BackendKind.of("firebase"))
        assertThrows(IllegalArgumentException::class.java) { BackendKind.of("supabase") }
    }

    @Test
    fun noServerWhenNoneOrTheApiHasNoAddress() {
        assertNull(Backends.create(BackendKind.NONE, "https://example.com/api/v1/", noFirebase))
        assertNull(Backends.create(BackendKind.API, "", noFirebase))
    }

    @Test
    fun theApiIsOneServerForPlayersDeletionAndScores() {
        val backend = Backends.create(BackendKind.API, "https://example.com/api/v1/", noFirebase)

        assertTrue(backend is HttpBackend)
        assertEquals("api", backend!!.id)
        assertTrue(backend.players is HttpPlayerApi)
        assertSame(backend.players, backend.deletion)
        assertTrue(backend.scores is HttpScoreApi)
    }

    @Test
    fun theApiBackendTalksToItsAddress() {
        val paths = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            paths += "${exchange.requestMethod} ${exchange.requestURI}"
            exchange.sendResponseHeaders(500, -1)
            exchange.close()
        }
        server.start()
        try {
            val failures = mutableListOf<String>()
            val backend = Backends.create(BackendKind.API, "http://127.0.0.1:${server.address.port}/api/v1/", noFirebase) { reason, _ ->
                failures += reason
            }!!

            assertNull(backend.scores.ranking("sudoku", "EASY"))
            assertEquals(DeleteResult.FAILED, backend.deletion.delete("abc", "s"))

            assertEquals(listOf("GET /api/v1/rankings/sudoku/EASY", "DELETE /api/v1/players/abc"), paths)
            assertEquals(2, failures.size)
        } finally {
            server.stop(0)
        }
    }

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

    private val games = listOf("sudoku", "hangman")
    private val variants = listOf("EASY", "4")

    private class Fixture {
        val store = MemoryStore()
        private var ids = 0
        val records = Records(store, clock = { 1_000L }, newId = { "id-${++ids}" })
        val uploads = ScoreUploads(store)
        val account = PlayerAccount(store, Random(1))
        private val oldServer = object : ScoreApi {
            override fun submit(secret: String, score: PendingScore) = SubmitResult.Filed(1)
            override fun ranking(game: String, variant: String) =
                listOf(RankingEntry(1, "x", 9, "otro", "Otro", null))
        }
        val rankings = GlobalRankings(store, oldServer)

        /** A player on the API with two best times sent and one waiting, and a ranking downloaded. */
        init {
            PlayerSync(account) { _, name, _, _ -> SaveResult.Saved(name) }.run()
            records.add("sudoku", "EASY", 100)
            records.add("hangman", "4", 30)
            uploads.clear()
            records.add("sudoku", "EASY", 90)
            assertNotNull(rankings.refresh(account, "sudoku", "EASY"))
        }
    }

    @Test
    fun theSameServerChangesNothing() {
        val fixture = Fixture()
        val change = ServerChange(fixture.store, fixture.account)

        // Players from before this was noted were on the API.
        assertFalse(change.follow("api", games, variants))
        assertFalse(change.follow("api", games, variants))

        assertTrue(fixture.account.registered)
        assertEquals(listOf(90), fixture.uploads.all().map { it.seconds })
        assertNotNull(fixture.rankings.cached("sudoku", "EASY"))
    }

    @Test
    fun anotherServerGetsThePlayerAndEveryBestTimeAgain() {
        val fixture = Fixture()
        val account = fixture.account
        val id = account.id
        val secret = account.secret
        account.markRejected()

        assertTrue(ServerChange(fixture.store, account).follow("firebase", games, variants))

        assertFalse(account.registered)
        assertTrue(account.pending)
        assertFalse(account.nameRejected)
        assertEquals(id, account.id)
        assertEquals(secret, account.secret)
        assertEquals(
            listOf("sudoku" to 90, "sudoku" to 100, "hangman" to 30),
            fixture.uploads.all().map { it.game to it.seconds },
        )
        assertEquals(1_000L, fixture.uploads.all().first().playedAt)
        assertNull(fixture.rankings.cached("sudoku", "EASY"))

        // Once is enough: the next start is on the same server.
        assertFalse(ServerChange(fixture.store, account).follow("firebase", games, variants))
    }

    @Test
    fun aBuildWithoutServerLeavesEverythingForTheNextOne() {
        val fixture = Fixture()
        val change = ServerChange(fixture.store, fixture.account)

        assertFalse(change.follow(null, games, variants))
        assertFalse(change.follow("api", games, variants))

        assertTrue(fixture.account.registered)
        assertNotNull(fixture.rankings.cached("sudoku", "EASY"))
    }

    @Test
    fun goingBackIsAChangeToo() {
        val fixture = Fixture()
        val change = ServerChange(fixture.store, fixture.account)
        change.follow("firebase", games, variants)
        PlayerSync(fixture.account) { _, name, _, _ -> SaveResult.Saved(name) }.run()
        fixture.uploads.clear()

        assertTrue(change.follow("api", games, variants))
        assertFalse(fixture.account.registered)
        assertEquals(3, fixture.uploads.all().size)
    }
}
