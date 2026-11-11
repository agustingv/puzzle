package com.butaca13.puzzles.data

import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import kotlin.random.Random

class ScoresTest {

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
    private var now = 1_000_000L
    private var ids = 0
    private val records = Records(store, clock = { now }, newId = { "id-${++ids}" })
    private val uploads = ScoreUploads(store)

    private fun registeredAccount(): PlayerAccount {
        val account = PlayerAccount(store, Random(1))
        PlayerSync(account) { _, name, _, _ -> SaveResult.Saved(name) }.run()
        assertTrue(account.registered)
        return account
    }

    private class FakeScoreApi(var answer: (PendingScore) -> SubmitResult = { SubmitResult.Filed(1) }) : ScoreApi {
        val sent = mutableListOf<Pair<String, PendingScore>>()
        var board: List<RankingEntry>? = emptyList()
        var asked = 0

        override fun submit(secret: String, score: PendingScore): SubmitResult {
            sent += secret to score
            return answer(score)
        }

        override fun ranking(game: String, variant: String): List<RankingEntry>? {
            asked++
            return board
        }
    }

    // Records

    @Test
    fun `each time gets an id and the moment it was won`() {
        records.add("sudoku", "EASY", 90)
        now += 5_000
        records.add("sudoku", "EASY", 60)

        assertEquals(
            listOf(RecordEntry("id-2", 60, 1_005_000L), RecordEntry("id-1", 90, 1_000_000L)),
            records.entries("sudoku", "EASY"),
        )
        assertEquals(listOf(60, 90), records.top("sudoku", "EASY"))
    }

    @Test
    fun `only a time that makes the ten best is queued for the rankings`() {
        repeat(10) { records.add("sudoku", "EASY", 10 + it) }
        val outside = records.add("sudoku", "EASY", 99)

        assertNull(outside.rank)
        assertEquals(10, uploads.all().size)
        assertTrue(uploads.all().none { it.seconds == 99 })

        records.add("sudoku", "EASY", 5)
        assertEquals(PendingScore("id-12", "sudoku", "EASY", 5, now), uploads.all().last())
    }

    @Test
    fun `times from before the rankings get ids and are queued once, without a date`() {
        store.put("records/race/5", "[30,45,60]")

        records.upgradeAll(listOf("race", "sudoku"), listOf("5", "EASY"))
        records.upgradeAll(listOf("race", "sudoku"), listOf("5", "EASY"))

        assertEquals(listOf(30, 45, 60), records.top("race", "5"))
        assertTrue(records.entries("race", "5").all { it.playedAt == null })
        assertEquals(listOf(30, 45, 60), uploads.all().map { it.seconds })
        assertTrue(uploads.all().all { it.game == "race" && it.variant == "5" && it.playedAt == null })
        assertEquals(records.entries("race", "5").map { it.id }, uploads.all().map { it.id })
    }

    @Test
    fun `an upgraded list keeps ranking new times the same way`() {
        store.put("records/race/5", "[30,45]")

        val result = records.add("race", "5", 45)

        assertEquals(3, result.rank)
        assertEquals(listOf(30, 45, 45), records.top("race", "5"))
    }

    // The queue

    @Test
    fun `the queue survives a restart and does not take the same time twice`() {
        val score = PendingScore("a", "sudoku", "EASY", 30, null)
        uploads.add(score)
        uploads.add(score)

        assertEquals(listOf(score), ScoreUploads(store).all())
        ScoreUploads(store).remove("a")
        assertFalse(uploads.isNotEmpty())
    }

    // Sending

    @Test
    fun `nothing is sent until the server knows the player`() {
        val api = FakeScoreApi()
        val account = PlayerAccount(store, Random(1))
        records.add("sudoku", "EASY", 30)

        assertFalse(account.registered)
        assertEquals(PlayerSync.Outcome.DONE, ScoreSync(account, uploads, api).run())
        assertTrue(api.sent.isEmpty())
        assertTrue(uploads.isNotEmpty())
    }

    @Test
    fun `the queue is sent with the player's secret and emptied as the server answers`() {
        val account = registeredAccount()
        val api = FakeScoreApi { if (it.seconds == 40) SubmitResult.Filed(null) else SubmitResult.Filed(1) }
        records.add("sudoku", "EASY", 30)
        records.add("sudoku", "HARD", 40)

        assertEquals(PlayerSync.Outcome.DONE, ScoreSync(account, uploads, api).run())

        assertEquals(listOf(30, 40), api.sent.map { it.second.seconds })
        assertTrue(api.sent.all { it.first == account.secret })
        assertFalse(uploads.isNotEmpty())
    }

    @Test
    fun `without an answer the rest waits, in order, for the next try`() {
        val account = registeredAccount()
        val api = FakeScoreApi { if (it.seconds == 40) SubmitResult.Failed else SubmitResult.Filed(1) }
        records.add("sudoku", "EASY", 30)
        records.add("sudoku", "EASY", 40)
        records.add("sudoku", "EASY", 50)

        assertEquals(PlayerSync.Outcome.TRY_LATER, ScoreSync(account, uploads, api).run())
        assertEquals(listOf(40, 50), uploads.all().map { it.seconds })

        api.answer = { SubmitResult.Filed(2) }
        assertEquals(PlayerSync.Outcome.DONE, ScoreSync(account, uploads, api).run())
        assertFalse(uploads.isNotEmpty())
    }

    @Test
    fun `a time the server will never take is dropped`() {
        val account = registeredAccount()
        records.add("sudoku", "EASY", 30)

        ScoreSync(account, uploads, FakeScoreApi { SubmitResult.Rejected }).run()

        assertFalse(uploads.isNotEmpty())
    }

    @Test
    fun `each time the server files is noted, for the rankings on screen to reload`() {
        val account = registeredAccount()
        records.add("sudoku", "EASY", 30)
        records.add("race", "EASY", 40)
        records.add("hangman", "EASY", 50)
        val before = ScoreUploads.filed

        ScoreSync(account, uploads, FakeScoreApi { if (it.seconds == 50) SubmitResult.Rejected else SubmitResult.Filed(1) }).run()

        assertEquals(before + 2, ScoreUploads.filed)
    }

    @Test
    fun `without connections nothing is sent and the queue stays`() {
        val account = registeredAccount()
        val api = FakeScoreApi()
        records.add("sudoku", "EASY", 30)
        account.changeOffline(true)

        ScoreSync(account, uploads, api).run()
        ScoreSync(account, uploads, null).run()

        assertTrue(api.sent.isEmpty())
        assertTrue(uploads.isNotEmpty())
    }

    @Test
    fun `a player synced before this version counts as known to the server`() {
        store.put("player/id", "0032b129-1fa9-407a-903b-4372772a5718")
        store.put("player/pending", "no")

        assertTrue(PlayerAccount(store).registered)
    }

    @Test
    fun `a new identity is unknown to the server again`() {
        val account = registeredAccount()
        account.rename("Otro Nombre")

        PlayerSync(account) { _, _, _, _ -> SaveResult.Taken }.run()

        assertFalse(account.registered)
    }

    // A server that lost the player

    /** A server that forgets players, like a database emptied by hand, and knows them again when sent. */
    private class ForgetfulServer {
        val known = mutableSetOf<String>()
        val players = mutableListOf<String>()
        val filed = mutableListOf<Int>()
        var reachable = true

        val playerApi = PlayerApi { id, name, _, secret ->
            if (!reachable) return@PlayerApi SaveResult.Failed
            players += id
            known += secret
            SaveResult.Saved(name)
        }

        val scoreApi = object : ScoreApi {
            override fun submit(secret: String, score: PendingScore): SubmitResult = when {
                !reachable -> SubmitResult.Failed
                secret !in known -> SubmitResult.PlayerUnknown
                else -> SubmitResult.Filed(1).also { filed += score.seconds }
            }

            override fun ranking(game: String, variant: String): List<RankingEntry>? = null
        }
    }

    @Test
    fun `a player the server lost is sent again, with the same id, and its times after it`() {
        val server = ForgetfulServer()
        val account = PlayerAccount(store, Random(1))
        assertEquals(PlayerSync.Outcome.DONE, FullSync(account, server.playerApi, uploads, server.scoreApi).run())
        val id = account.id
        server.known.clear()
        records.add("sudoku", "EASY", 30)

        assertEquals(PlayerSync.Outcome.DONE, FullSync(account, server.playerApi, uploads, server.scoreApi).run())

        assertEquals(listOf(id, id), server.players)
        assertEquals(listOf(30), server.filed)
        assertTrue(account.registered)
        assertFalse(account.pending)
        assertFalse(uploads.isNotEmpty())
    }

    @Test
    fun `a lost player that can not be sent again keeps everything for later`() {
        val server = ForgetfulServer()
        val account = PlayerAccount(store, Random(1))
        FullSync(account, server.playerApi, uploads, server.scoreApi).run()
        server.known.clear()
        records.add("sudoku", "EASY", 30)

        // The time finds the player lost; then the connection drops before it is sent again.
        val flaky = object : ScoreApi by server.scoreApi {
            override fun submit(secret: String, score: PendingScore) =
                server.scoreApi.submit(secret, score).also { server.reachable = false }
        }

        assertEquals(PlayerSync.Outcome.TRY_LATER, FullSync(account, server.playerApi, uploads, flaky).run())
        assertFalse(account.registered)
        assertTrue(account.pending)
        assertTrue(uploads.isNotEmpty())

        server.reachable = true
        assertEquals(PlayerSync.Outcome.DONE, FullSync(account, server.playerApi, uploads, server.scoreApi).run())
        assertTrue(account.registered)
        assertFalse(uploads.isNotEmpty())
    }

    @Test
    fun `nothing to do is done, not tried again and again`() {
        val account = PlayerAccount(store, Random(1))
        records.add("sudoku", "EASY", 30)

        // No server in this build, or no connections wanted: the player is never sent, and that is fine.
        assertEquals(PlayerSync.Outcome.DONE, FullSync(account, null, uploads, null).run())
        account.changeOffline(true)
        assertEquals(PlayerSync.Outcome.DONE, FullSync(account, ForgetfulServer().playerApi, uploads, ForgetfulServer().scoreApi).run())
    }

    // The global rankings

    private val entry = RankingEntry(1, "s1", 42, "p1", "Búho Sabio 3", "buho:lila:lazo")

    @Test
    fun `a ranking is not fetched for a player the server does not know, nor without connections`() {
        val api = FakeScoreApi().apply { board = listOf(entry) }
        val rankings = GlobalRankings(store, api, clock = { now })

        assertNull(rankings.refresh(PlayerAccount(store, Random(1)), "sudoku", "EASY"))
        val account = registeredAccount().apply { changeOffline(true) }
        assertNull(rankings.refresh(account, "sudoku", "EASY"))

        assertEquals(0, api.asked)
        assertNull(rankings.cached("sudoku", "EASY"))
    }

    @Test
    fun `a ranking is only waited for when it will be asked for`() {
        val rankings = GlobalRankings(store, FakeScoreApi(), clock = { now })

        assertFalse(rankings.canRefresh(PlayerAccount(store, Random(1))))
        val account = registeredAccount()
        assertTrue(rankings.canRefresh(account))
        assertFalse(GlobalRankings(store, null).canRefresh(account))
        account.changeOffline(true)
        assertFalse(rankings.canRefresh(account))
    }

    @Test
    fun `a ranking fetched is kept for when there is no connection`() {
        val api = FakeScoreApi().apply { board = listOf(entry, entry.copy(rank = 2, id = "s2", seconds = 50, playerAvatar = null)) }
        val rankings = GlobalRankings(store, api, clock = { now })
        val account = registeredAccount()

        val fetched = rankings.refresh(account, "sudoku", "EASY")

        assertEquals(Ranking(api.board!!, now), fetched)
        assertEquals(fetched, GlobalRankings(store, null).cached("sudoku", "EASY"))

        api.board = null
        assertNull(rankings.refresh(account, "sudoku", "EASY"))
        assertEquals(fetched, rankings.cached("sudoku", "EASY"))
        assertNull(rankings.cached("sudoku", "HARD"))
    }

    // The real HTTP client, against a small local server

    private fun withServer(status: Int, body: String, test: (baseUrl: String, received: MutableList<String>) -> Unit) {
        val received = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            received += "${exchange.requestMethod} ${exchange.requestURI} ${exchange.requestHeaders.getFirst("Authorization")}"
            received += exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            test("http://127.0.0.1:${server.address.port}/api/v1/", received)
        } finally {
            server.stop(0)
        }
    }

    private val score = PendingScore("s-1", "sudoku", "EASY", 95, 1_793_782_800_000L)

    @Test
    fun `the client puts a time at its own address with the player's secret`() {
        withServer(200, """{"id":"s-1","game":"sudoku","variant":"EASY","seconds":95,"rank":3}""") { url, received ->
            assertEquals(SubmitResult.Filed(3), HttpScoreApi(url).submit("secreto", score))

            assertEquals("PUT /api/v1/scores/s-1 Bearer secreto", received[0])
            val sent = JSONObject(received[1])
            assertEquals("sudoku", sent.getString("game"))
            assertEquals("EASY", sent.getString("variant"))
            assertEquals(95, sent.getInt("seconds"))
            assertEquals("2026-11-04T09:00:00Z", sent.getString("playedAt"))
        }
        withServer(200, """{"rank":null}""") { url, received ->
            assertEquals(SubmitResult.Filed(null), HttpScoreApi(url).submit("s", score.copy(playedAt = null)))
            assertFalse(JSONObject(received[1]).has("playedAt"))
        }
    }

    @Test
    fun `the client tells apart the server's answers to a time`() {
        for (refused in listOf(400, 409, 422)) {
            withServer(refused, "{}") { url, _ -> assertEquals(SubmitResult.Rejected, HttpScoreApi(url).submit("s", score)) }
        }
        withServer(401, "") { url, _ -> assertEquals(SubmitResult.PlayerUnknown, HttpScoreApi(url).submit("s", score)) }
        for (later in listOf(500, 503)) {
            withServer(later, "") { url, _ -> assertEquals(SubmitResult.Failed, HttpScoreApi(url).submit("s", score)) }
        }
        withServer(200, "no es json") { url, _ -> assertEquals(SubmitResult.Failed, HttpScoreApi(url).submit("s", score)) }
        assertEquals(SubmitResult.Failed, HttpScoreApi("http://127.0.0.1:9/api/v1/").submit("s", score))
    }

    @Test
    fun `the client reads a ranking`() {
        val body = """{"game":"sudoku","variant":"EASY","entries":[
            {"rank":1,"id":"s1","seconds":42,"playedAt":"2026-11-04T09:00:00+00:00","playerId":"p1","playerName":"Búho Sabio 3","playerAvatar":"buho:lila:lazo"},
            {"rank":2,"id":"s2","seconds":50,"playedAt":"2026-11-04T09:00:00+00:00","playerId":"p2","playerName":"Sin Avatar","playerAvatar":null}]}"""
        withServer(200, body) { url, received ->
            val ranking = HttpScoreApi(url).ranking("sudoku", "EASY")

            assertEquals("GET /api/v1/rankings/sudoku/EASY null", received[0])
            assertEquals(listOf(entry, RankingEntry(2, "s2", 50, "p2", "Sin Avatar", null)), ranking)
        }
        withServer(500, "") { url, _ -> assertNull(HttpScoreApi(url).ranking("sudoku", "EASY")) }
        withServer(200, "{}") { url, _ -> assertNull(HttpScoreApi(url).ranking("sudoku", "EASY")) }
    }
}
