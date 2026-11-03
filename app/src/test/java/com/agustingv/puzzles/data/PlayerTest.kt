package com.agustingv.puzzles.data

import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.util.UUID
import kotlin.random.Random

class PlayerTest {

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

    private fun account(seed: Int = 1) = PlayerAccount(store, Random(seed))

    // Names

    @Test
    fun `every made up name is a valid one`() {
        for (name in PlayerNames.all()) assertTrue(name, PlayerNames.isValid(name))
        repeat(200) { assertTrue(PlayerNames.isValid(PlayerNames.random(Random(it)))) }
    }

    @Test
    fun `adjectives agree with the animal`() {
        val names = PlayerNames.all()

        assertTrue("Ardilla Astuta 99" in names)
        assertTrue("Zorro Astuto 99" in names)
        assertFalse("Ardilla Astuto 99" in names)
    }

    @Test
    fun `names follow the server's rule`() {
        for (good in listOf("Ana", "Ñandú Ágil 7", "  con espacios  ", "guion-bajo_1", "x".repeat(24))) {
            assertTrue(good, PlayerNames.isValid(good))
        }
        for (bad in listOf("", "  ", "ab", "x".repeat(25), "-empieza", "con <html>", "emoji 😀", "a.b")) {
            assertFalse(bad, PlayerNames.isValid(bad))
        }
    }

    // The account

    @Test
    fun `the first launch makes up a player, waiting to reach the server`() {
        val account = account()

        assertTrue(UUID.fromString(account.id).version() == 4)
        assertEquals(64, account.secret.length)
        assertTrue(PlayerNames.isValid(account.name))
        assertTrue(account.pending)
        assertFalse(account.offline)
    }

    @Test
    fun `later launches find the same player`() {
        val first = account(1)
        val again = account(2)

        assertEquals(first.id, again.id)
        assertEquals(first.secret, again.secret)
        assertEquals(first.name, again.name)
    }

    @Test
    fun `a valid rename is kept and waits to be sent, an invalid one changes nothing`() {
        val account = account()
        account.markSynced(account.name)

        assertFalse(account.rename("x"))
        assertFalse(account.pending)

        assertTrue(account.rename("  Búho Sabio 3 "))
        assertEquals("Búho Sabio 3", account.name)
        assertTrue(account.pending)
        assertEquals("Búho Sabio 3", account(9).name)
    }

    @Test
    fun `offline mode is remembered`() {
        account().changeOffline(true)

        assertTrue(account().offline)
    }

    @Test
    fun `status says what matters most`() {
        val account = account()

        assertEquals(SyncStatus.PENDING, account.status(serverConfigured = true))
        assertEquals(SyncStatus.NO_SERVER, account.status(serverConfigured = false))
        account.changeOffline(true)
        assertEquals(SyncStatus.OFFLINE, account.status(serverConfigured = true))
        account.changeOffline(false)
        account.markSynced(account.name)
        assertEquals(SyncStatus.SYNCED, account.status(serverConfigured = true))
    }

    // The sync

    private class FakeApi(var answer: (String, String, String) -> SaveResult) : PlayerApi {
        val calls = mutableListOf<Triple<String, String, String>>()
        override fun save(id: String, name: String, secret: String): SaveResult {
            calls += Triple(id, name, secret)
            return answer(id, name, secret)
        }
    }

    private val saving = FakeApi { _, name, _ -> SaveResult.Saved(name) }

    @Test
    fun `a new player is sent once with its id, name and secret`() {
        val account = account()

        assertEquals(PlayerSync.Outcome.DONE, PlayerSync(account, saving).run())
        assertEquals(listOf(Triple(account.id, account.name, account.secret)), saving.calls)
        assertFalse(account.pending)

        PlayerSync(account, saving).run()
        assertEquals("nothing left to send", 1, saving.calls.size)
    }

    @Test
    fun `offline mode or no server means no connection at all`() {
        val account = account()
        account.changeOffline(true)

        assertEquals(PlayerSync.Outcome.DONE, PlayerSync(account, saving).run())
        assertEquals(PlayerSync.Outcome.DONE, PlayerSync(account(), null).run())
        assertTrue(saving.calls.isEmpty())
        assertTrue(account.pending)
    }

    @Test
    fun `no answer keeps it pending, to try later`() {
        val account = account()
        val down = FakeApi { _, _, _ -> SaveResult.Failed }

        assertEquals(PlayerSync.Outcome.TRY_LATER, PlayerSync(account, down).run())
        assertTrue(account.pending)

        down.answer = { _, name, _ -> SaveResult.Saved(name) }
        assertEquals(PlayerSync.Outcome.DONE, PlayerSync(account, down).run())
        assertFalse(account.pending)
    }

    @Test
    fun `a rename made while sending is sent too`() {
        val account = account()
        val renaming = FakeApi { _, name, _ ->
            if (name != "Nombre Nuevo") account.rename("Nombre Nuevo")
            SaveResult.Saved(name)
        }

        assertEquals(PlayerSync.Outcome.DONE, PlayerSync(account, renaming).run())
        assertEquals("Nombre Nuevo", renaming.calls.last().second)
        assertFalse(account.pending)
    }

    @Test
    fun `an id taken by someone else is replaced, with a new secret`() {
        val account = account()
        val oldId = account.id
        val oldSecret = account.secret
        val api = FakeApi { id, name, _ -> if (id == oldId) SaveResult.Taken else SaveResult.Saved(name) }

        assertEquals(PlayerSync.Outcome.DONE, PlayerSync(account, api).run())
        assertNotEquals(oldId, account.id)
        assertNotEquals(oldSecret, account.secret)
        assertEquals(account.id, account(5).id)
        assertFalse(account.pending)
    }

    @Test
    fun `a rejected name stops the retries until the player changes it`() {
        val account = account()
        val picky = FakeApi { _, _, _ -> SaveResult.Rejected }

        assertEquals(PlayerSync.Outcome.DONE, PlayerSync(account, picky).run())
        assertEquals(SyncStatus.NAME_REJECTED, account.status(serverConfigured = true))
        PlayerSync(account, picky).run()
        assertEquals(1, picky.calls.size)

        account.rename("Otro Nombre")
        assertEquals(SyncStatus.PENDING, account.status(serverConfigured = true))
    }

    // The real HTTP client, against a small local server

    private fun withServer(status: Int, body: String, test: (baseUrl: String, received: MutableList<String>) -> Unit) {
        val received = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            received += "${exchange.requestMethod} ${exchange.requestURI} ${exchange.requestHeaders.getFirst("Content-Type")}"
            received += exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            test("http://127.0.0.1:${server.address.port}/api/", received)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `the client puts the player at its own address`() {
        withServer(201, """{"id":"x","name":"Búho Sabio 3","registeredAt":"2026-11-03T00:00:00+00:00"}""") { url, received ->
            val result = HttpPlayerApi(url).save("abc", "Búho Sabio 3", "s".repeat(64))

            assertEquals(SaveResult.Saved("Búho Sabio 3"), result)
            assertEquals("PUT /api/players/abc application/json", received[0])
            val sent = JSONObject(received[1])
            assertEquals("Búho Sabio 3", sent.getString("name"))
            assertEquals("s".repeat(64), sent.getString("secret"))
        }
    }

    @Test
    fun `the client tells apart the server's answers`() {
        withServer(409, "{}") { url, _ -> assertEquals(SaveResult.Taken, HttpPlayerApi(url).save("a", "Ana", "s")) }
        withServer(422, "{}") { url, _ -> assertEquals(SaveResult.Rejected, HttpPlayerApi(url).save("a", "Ana", "s")) }
        withServer(500, "") { url, _ -> assertEquals(SaveResult.Failed, HttpPlayerApi(url).save("a", "Ana", "s")) }
        withServer(200, "not json") { url, _ -> assertEquals(SaveResult.Failed, HttpPlayerApi(url).save("a", "Ana", "s")) }
    }

    @Test
    fun `no server at the address is a failure to retry`() {
        assertEquals(SaveResult.Failed, HttpPlayerApi("http://127.0.0.1:9/api/").save("a", "Ana", "s"))
    }
}
