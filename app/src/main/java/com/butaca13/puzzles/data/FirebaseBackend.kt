package com.butaca13.puzzles.data

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * The player and the rankings on Firebase. The player keeps the id and secret the phone made up, as with
 * the API: every write goes through a Cloud Function, which checks the secret against the hash it keeps,
 * and Firestore holds the rankings, one document per game and level, that the functions keep up to date
 * and anyone can read. Reading one costs a single document read, and needs no function to wake up.
 *
 * What the functions take and answer ([FirebaseCalls.call]):
 * - `savePlayer` {id, name, avatar, secret} → {name}. Errors: ALREADY_EXISTS, the id is another
 *   player's (wrong secret); INVALID_ARGUMENT, a name or avatar it does not take.
 * - `deletePlayer` {id, secret} → anything. NOT_FOUND or UNAUTHENTICATED: no such player (any more).
 * - `submitScore` {secret, id, game, variant, seconds, playedAt?} → {rank: number or null}, with
 *   playedAt in epoch milliseconds. Errors: UNAUTHENTICATED, no player with that secret;
 *   INVALID_ARGUMENT or ALREADY_EXISTS, a bad time or an id that is another player's.
 *
 * And Firestore: `rankings/{game}_{variant}` = {entries: [{id, seconds, playerId, playerName,
 * playerAvatar?}]}, fastest first, with the player's current name and avatar. No document: no times yet.
 */
class FirebaseBackend(
    private val firebase: FirebaseCalls,
    private val onFailure: (String, Throwable?) -> Unit = { _, _ -> },
) : Backend {

    override val id = BackendKind.FIREBASE.id

    override val players = PlayerApi { id, name, avatar, secret ->
        try {
            val answer = firebase.call("savePlayer", mapOf("id" to id, "name" to name, "avatar" to avatar, "secret" to secret), WRITE_TIMEOUT_MILLIS)
            val saved = (answer as? Map<*, *>)?.get("name") as? String
            if (saved != null) {
                SaveResult.Saved(saved)
            } else {
                SaveResult.Failed.also { onFailure("Respuesta de Firebase ilegible al guardar el jugador", null) }
            }
        } catch (e: FirebaseCallError) {
            when (e.code) {
                FirebaseCallError.Code.ALREADY_EXISTS -> SaveResult.Taken
                FirebaseCallError.Code.INVALID_ARGUMENT -> SaveResult.Rejected
                else -> SaveResult.Failed.also { onFailure("Firebase falló al guardar el jugador", e) }
            }
        }
    }

    override val deletion = PlayerDeletionApi { id, secret ->
        try {
            firebase.call("deletePlayer", mapOf("id" to id, "secret" to secret), WRITE_TIMEOUT_MILLIS)
            DeleteResult.GONE
        } catch (e: FirebaseCallError) {
            when (e.code) {
                FirebaseCallError.Code.NOT_FOUND, FirebaseCallError.Code.UNAUTHENTICATED -> DeleteResult.GONE
                else -> DeleteResult.FAILED.also { onFailure("Firebase falló al borrar el jugador", e) }
            }
        }
    }

    override val scores = object : ScoreApi {
        override fun submit(secret: String, score: PendingScore): SubmitResult = try {
            val data = buildMap {
                put("secret", secret)
                put("id", score.id)
                put("game", score.game)
                put("variant", score.variant)
                put("seconds", score.seconds)
                score.playedAt?.let { put("playedAt", it) }
            }
            val answer = firebase.call("submitScore", data, WRITE_TIMEOUT_MILLIS) as? Map<*, *>
            if (answer == null || "rank" !in answer) {
                SubmitResult.Failed.also { onFailure("Respuesta de Firebase ilegible a un tiempo", null) }
            } else {
                SubmitResult.Filed((answer["rank"] as? Number)?.toInt())
            }
        } catch (e: FirebaseCallError) {
            when (e.code) {
                FirebaseCallError.Code.UNAUTHENTICATED -> SubmitResult.PlayerUnknown
                FirebaseCallError.Code.INVALID_ARGUMENT, FirebaseCallError.Code.ALREADY_EXISTS -> SubmitResult.Rejected
                else -> SubmitResult.Failed.also { onFailure("Firebase falló al enviar un tiempo", e) }
            }
        }

        override fun ranking(game: String, variant: String): List<RankingEntry>? = try {
            val document = firebase.document("rankings", "${game}_$variant", RANKING_TIMEOUT_MILLIS)
            if (document == null) emptyList() else entries(document["entries"])
        } catch (e: FirebaseCallError) {
            onFailure("Firebase falló al pedir un ranking", e)
            null
        }
    }

    /** Null, and logged, when they are not as the functions write them. */
    private fun entries(value: Any?): List<RankingEntry>? {
        val list = value as? List<*>
        val entries = list?.mapIndexedNotNull { index, item ->
            val entry = item as? Map<*, *> ?: return@mapIndexedNotNull null
            RankingEntry(
                rank = index + 1,
                id = entry["id"] as? String ?: return@mapIndexedNotNull null,
                seconds = (entry["seconds"] as? Number)?.toInt() ?: return@mapIndexedNotNull null,
                playerId = entry["playerId"] as? String ?: return@mapIndexedNotNull null,
                playerName = entry["playerName"] as? String ?: return@mapIndexedNotNull null,
                playerAvatar = entry["playerAvatar"] as? String,
            )
        }
        if (entries == null || entries.size != list.size) {
            onFailure("Ranking de Firebase ilegible", null)
            return null
        }
        return entries
    }

    private companion object {
        /** As the API's: the writes go in the background, with no one waiting. */
        const val WRITE_TIMEOUT_MILLIS = 15_000L

        /** As the API's, about 8 seconds: someone is looking at a spinner. */
        const val RANKING_TIMEOUT_MILLIS = 8_000L
    }
}

/** A call to Firebase that got no good answer: [code] says why, as far as [FirebaseBackend] cares. */
class FirebaseCallError(val code: Code, message: String, cause: Throwable? = null) : Exception(message, cause) {
    enum class Code {
        INVALID_ARGUMENT,
        ALREADY_EXISTS,
        NOT_FOUND,
        UNAUTHENTICATED,

        /** No connection, a timeout, a fault: worth trying again later. */
        UNAVAILABLE,
    }
}

/** What [FirebaseBackend] needs of Firebase, behind an interface so that it can be tested without it. Calls block. */
interface FirebaseCalls {
    /** The Cloud Function [name]'s answer to [data], or [FirebaseCallError]. */
    fun call(name: String, data: Map<String, Any?>, timeoutMillis: Long): Any?

    /** The fields of the document, null if there is none, or [FirebaseCallError]. Always from the server. */
    fun document(collection: String, id: String, timeoutMillis: Long): Map<String, Any?>?
}

/**
 * Firebase's own SDK, set up on first use: the app does not start Firebase by itself (the manifest
 * removes its init provider), so builds for another server never touch it.
 */
class SdkFirebaseCalls(context: Context) : FirebaseCalls {
    private val context = context.applicationContext

    private val app: FirebaseApp by lazy {
        FirebaseApp.initializeApp(context) ?: error("Falta app/google-services.json")
    }

    private val functions by lazy { FirebaseFunctions.getInstance(app, REGION) }

    private val firestore by lazy {
        FirebaseFirestore.getInstance(app, DATABASE).apply {
            // The app keeps its own copy of the rankings (GlobalRankings): Firestore needs none on disk.
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
    }

    override fun call(name: String, data: Map<String, Any?>, timeoutMillis: Long): Any? {
        val callable = functions.getHttpsCallable(name).apply { setTimeout(timeoutMillis, TimeUnit.MILLISECONDS) }
        return await(callable.call(data), timeoutMillis).getData()
    }

    override fun document(collection: String, id: String, timeoutMillis: Long): Map<String, Any?>? {
        val snapshot = await(firestore.collection(collection).document(id).get(Source.SERVER), timeoutMillis)
        return if (snapshot.exists()) snapshot.data else null
    }

    private fun <T> await(task: Task<T>, timeoutMillis: Long): T = try {
        Tasks.await(task, timeoutMillis, TimeUnit.MILLISECONDS)
    } catch (e: ExecutionException) {
        val cause = e.cause
        val code = when ((cause as? FirebaseFunctionsException)?.code) {
            FirebaseFunctionsException.Code.INVALID_ARGUMENT -> FirebaseCallError.Code.INVALID_ARGUMENT
            FirebaseFunctionsException.Code.ALREADY_EXISTS -> FirebaseCallError.Code.ALREADY_EXISTS
            FirebaseFunctionsException.Code.NOT_FOUND -> FirebaseCallError.Code.NOT_FOUND
            FirebaseFunctionsException.Code.UNAUTHENTICATED -> FirebaseCallError.Code.UNAUTHENTICATED
            else -> FirebaseCallError.Code.UNAVAILABLE
        }
        throw FirebaseCallError(code, cause?.message ?: "Fallo de Firebase", cause)
    } catch (e: TimeoutException) {
        throw FirebaseCallError(FirebaseCallError.Code.UNAVAILABLE, "Sin respuesta de Firebase", e)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw FirebaseCallError(FirebaseCallError.Code.UNAVAILABLE, "Interrumpido", e)
    }

    private companion object {
        // The project's Firestore database (a named one, not "(default)") and the functions, both in Belgium.
        const val DATABASE = "puzzles"
        const val REGION = "europe-west1"
    }
}
