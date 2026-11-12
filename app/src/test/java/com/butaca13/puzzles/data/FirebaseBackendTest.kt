package com.butaca13.puzzles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseBackendTest {

    /** Answers as the functions and Firestore would, and notes what it was asked. */
    private class FakeFirebase(
        var answer: (name: String, data: Map<String, Any?>) -> Any? = { _, _ -> null },
        var documents: (collection: String, id: String) -> Map<String, Any?>? = { _, _ -> null },
    ) : FirebaseCalls {
        val calls = mutableListOf<Pair<String, Map<String, Any?>>>()
        val reads = mutableListOf<String>()

        override fun call(name: String, data: Map<String, Any?>, timeoutMillis: Long): Any? {
            calls += name to data
            return answer(name, data)
        }

        override fun document(collection: String, id: String, timeoutMillis: Long): Map<String, Any?>? {
            reads += "$collection/$id"
            return documents(collection, id)
        }
    }

    private fun failing(code: FirebaseCallError.Code): (String, Map<String, Any?>) -> Any? =
        { _, _ -> throw FirebaseCallError(code, "falla") }

    private val firebase = FakeFirebase()
    private val failures = mutableListOf<String>()
    private val backend = FirebaseBackend(firebase) { reason, _ -> failures += reason }

    private val score = PendingScore("t1", "sudoku", "EASY", 95, 1_700_000_000_000L)

    @Test
    fun isNamedForTheFactory() {
        assertEquals("firebase", backend.id)
        assertTrue(Backends.create(BackendKind.FIREBASE, "", firebase = { firebase }) is FirebaseBackend)
    }

    @Test
    fun savesThePlayerWithItsSecret() {
        firebase.answer = { _, data -> mapOf("name" to data["name"]) }

        assertEquals(SaveResult.Saved("Búho Sabio 3"), backend.players.save("abc", "Búho Sabio 3", "buho:lila", "s3cr3t"))
        assertEquals(
            "savePlayer" to mapOf("id" to "abc", "name" to "Búho Sabio 3", "avatar" to "buho:lila", "secret" to "s3cr3t"),
            firebase.calls.single(),
        )
    }

    @Test
    fun savingAPlayerAnswersAsTheApiDoes() {
        firebase.answer = failing(FirebaseCallError.Code.ALREADY_EXISTS)
        assertEquals(SaveResult.Taken, backend.players.save("a", "Ana", "zorro", "s"))
        firebase.answer = failing(FirebaseCallError.Code.INVALID_ARGUMENT)
        assertEquals(SaveResult.Rejected, backend.players.save("a", "Ana", "zorro", "s"))
        firebase.answer = failing(FirebaseCallError.Code.UNAVAILABLE)
        assertEquals(SaveResult.Failed, backend.players.save("a", "Ana", "zorro", "s"))
        firebase.answer = { _, _ -> "no es un mapa" }
        assertEquals(SaveResult.Failed, backend.players.save("a", "Ana", "zorro", "s"))
        assertEquals(2, failures.size)
    }

    @Test
    fun aPlayerNotThereIsGone() {
        assertEquals(DeleteResult.GONE, backend.deletion.delete("abc", "s"))
        assertEquals("deletePlayer" to mapOf("id" to "abc", "secret" to "s"), firebase.calls.single())

        firebase.answer = failing(FirebaseCallError.Code.NOT_FOUND)
        assertEquals(DeleteResult.GONE, backend.deletion.delete("abc", "s"))
        firebase.answer = failing(FirebaseCallError.Code.UNAUTHENTICATED)
        assertEquals(DeleteResult.GONE, backend.deletion.delete("abc", "s"))
        firebase.answer = failing(FirebaseCallError.Code.UNAVAILABLE)
        assertEquals(DeleteResult.FAILED, backend.deletion.delete("abc", "s"))
    }

    @Test
    fun sendsATimeAndHearsItsPlace() {
        firebase.answer = { _, _ -> mapOf("rank" to 3L) }
        assertEquals(SubmitResult.Filed(3), backend.scores.submit("s3cr3t", score))
        assertEquals(
            "submitScore" to mapOf(
                "secret" to "s3cr3t", "id" to "t1", "game" to "sudoku", "variant" to "EASY",
                "seconds" to 95, "playedAt" to 1_700_000_000_000L,
            ),
            firebase.calls.single(),
        )

        firebase.answer = { _, _ -> mapOf("rank" to null) }
        assertEquals(SubmitResult.Filed(null), backend.scores.submit("s", score.copy(playedAt = null)))
        assertTrue("playedAt" !in firebase.calls.last().second)
    }

    @Test
    fun sendingATimeAnswersAsTheApiDoes() {
        firebase.answer = failing(FirebaseCallError.Code.UNAUTHENTICATED)
        assertEquals(SubmitResult.PlayerUnknown, backend.scores.submit("s", score))
        firebase.answer = failing(FirebaseCallError.Code.INVALID_ARGUMENT)
        assertEquals(SubmitResult.Rejected, backend.scores.submit("s", score))
        firebase.answer = failing(FirebaseCallError.Code.ALREADY_EXISTS)
        assertEquals(SubmitResult.Rejected, backend.scores.submit("s", score))
        firebase.answer = failing(FirebaseCallError.Code.UNAVAILABLE)
        assertEquals(SubmitResult.Failed, backend.scores.submit("s", score))
        // No rank at all is not "outside the ten best".
        firebase.answer = { _, _ -> emptyMap<String, Any?>() }
        assertEquals(SubmitResult.Failed, backend.scores.submit("s", score))
    }

    @Test
    fun readsARankingFromItsDocument() {
        firebase.documents = { _, _ ->
            mapOf(
                "entries" to listOf(
                    mapOf("id" to "t1", "seconds" to 40L, "playerId" to "p1", "playerName" to "Ana", "playerAvatar" to "zorro:menta"),
                    mapOf("id" to "t2", "seconds" to 52L, "playerId" to "p2", "playerName" to "Luis"),
                ),
            )
        }

        assertEquals(
            listOf(
                RankingEntry(1, "t1", 40, "p1", "Ana", "zorro:menta"),
                RankingEntry(2, "t2", 52, "p2", "Luis", null),
            ),
            backend.scores.ranking("sudoku", "EASY"),
        )
        assertEquals(listOf("rankings/sudoku_EASY"), firebase.reads)
    }

    @Test
    fun noDocumentIsARankingWithNoTimesYet() {
        assertEquals(emptyList<RankingEntry>(), backend.scores.ranking("sudoku", "EASY"))
    }

    @Test
    fun aRankingThatCanNotBeHadOrReadIsNull() {
        firebase.documents = { _, _ -> throw FirebaseCallError(FirebaseCallError.Code.UNAVAILABLE, "sin red") }
        assertNull(backend.scores.ranking("sudoku", "EASY"))

        firebase.documents = { _, _ -> mapOf("entries" to listOf(mapOf("id" to "t1", "seconds" to "mucho"))) }
        assertNull(backend.scores.ranking("sudoku", "EASY"))

        firebase.documents = { _, _ -> mapOf("otra" to 1) }
        assertNull(backend.scores.ranking("sudoku", "EASY"))
        assertEquals(3, failures.size)
    }
}
