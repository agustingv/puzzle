package com.butaca13.puzzles.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** A won game on its way to the global rankings. [playedAt] in epoch milliseconds, null if unknown. */
data class PendingScore(val id: String, val game: String, val variant: String, val seconds: Int, val playedAt: Long?)

/**
 * The times waiting to be sent, oldest first. They stay here, through restarts and without a
 * connection, until the server has answered for each one.
 */
class ScoreUploads(private val store: KeyValueStore) {

    fun all(): List<PendingScore> = synchronized(LOCK) { read() }

    fun isNotEmpty(): Boolean = all().isNotEmpty()

    fun add(score: PendingScore) = synchronized(LOCK) {
        val pending = read()
        if (pending.none { it.id == score.id }) write(pending + score)
    }

    fun remove(id: String) = synchronized(LOCK) {
        write(read().filterNot { it.id == id })
    }

    /** Drops every time waiting, as when the player is deleted from the server. */
    fun clear() = synchronized(LOCK) {
        store.remove(KEY)
    }

    private fun read(): List<PendingScore> {
        val json = store.get(KEY) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                PendingScore(
                    id = item.getString("id"),
                    game = item.getString("game"),
                    variant = item.getString("variant"),
                    seconds = item.getInt("s"),
                    playedAt = if (item.has("at")) item.getLong("at") else null,
                )
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private fun write(pending: List<PendingScore>) {
        val array = JSONArray()
        for (score in pending) {
            val item = JSONObject()
                .put("id", score.id)
                .put("game", score.game)
                .put("variant", score.variant)
                .put("s", score.seconds)
            score.playedAt?.let { item.put("at", it) }
            array.put(item)
        }
        store.put(KEY, array.toString())
    }

    companion object {
        /**
         * How many times sent since the app started have made a global ranking. Compose state, so that a
         * ranking on screen downloads again once a time sent in the background has changed it; one that
         * did not make it changed nothing, and is not counted.
         */
        var ranked by mutableIntStateOf(0)
            private set

        internal fun noteRanked() {
            ranked++
        }

        private const val KEY = "scores/pending"

        /** The game files times on the main thread while the sync sends them in the background. */
        private val LOCK = Any()
    }
}

/** What the server said to a time sent. */
sealed interface SubmitResult {
    /** Answered: [rank] is its place among the ten best of everyone, or null if it did not make them. */
    data class Filed(val rank: Int?) : SubmitResult

    /** The server will never take it, so it is dropped: a bad time, or an id that is someone else's. */
    data object Rejected : SubmitResult

    /**
     * The server does not know the player: it never got it, or has lost it, say to a database emptied
     * or restored. Sending the player again, with the same id and secret, sets that right.
     */
    data object PlayerUnknown : SubmitResult

    /** No answer, or a fault on the server's side: try again later. */
    data object Failed : SubmitResult
}

/** One place in a global ranking, as the server tells it. */
data class RankingEntry(
    val rank: Int,
    val id: String,
    val seconds: Int,
    val playerId: String,
    val playerName: String,
    /** As [Avatar.encode] writes it; null for a player from before avatars. */
    val playerAvatar: String?,
)

/** A global ranking as last downloaded, with when, in epoch milliseconds. */
data class Ranking(val entries: List<RankingEntry>, val fetchedAt: Long)

/** The rankings on the server, behind an interface so that they can be tested without one. Calls block. */
interface ScoreApi {
    /** [playerId] is the player the [secret] is for: Firebase finds it by id; the API, by the secret alone. */
    fun submit(playerId: String, secret: String, score: PendingScore): SubmitResult

    /** Null when it could not be had: no connection, or no sensible answer. */
    fun ranking(game: String, variant: String): List<RankingEntry>?
}

/**
 * Sends a time with PUT /scores/{id}, the player proving who it is with its secret, and reads a
 * ranking with GET /rankings/{game}/{variant}.
 */
class HttpScoreApi(
    private val baseUrl: String,
    private val onFailure: (String, Throwable?) -> Unit = { _, _ -> },
) : ScoreApi {

    override fun submit(playerId: String, secret: String, score: PendingScore): SubmitResult {
        val connection = open("scores/${score.id}") ?: return SubmitResult.Failed

        return try {
            connection.requestMethod = "PUT"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $secret")

            val body = JSONObject()
                .put("game", score.game)
                .put("variant", score.variant)
                .put("seconds", score.seconds)
            score.playedAt?.let { body.put("playedAt", isoDate(it)) }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            when (connection.responseCode) {
                in 200..299 -> {
                    val answer = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    SubmitResult.Filed(if (answer.isNull("rank")) null else answer.getInt("rank"))
                }
                400, 409, 422 -> SubmitResult.Rejected
                401 -> SubmitResult.PlayerUnknown
                else -> SubmitResult.Failed.also { onFailure("El API respondió ${connection.responseCode} a un tiempo", null) }
            }
        } catch (e: IOException) {
            onFailure("Sin respuesta del API al enviar un tiempo", e)
            SubmitResult.Failed
        } catch (e: JSONException) {
            onFailure("Respuesta del API ilegible al enviar un tiempo", e)
            SubmitResult.Failed
        } finally {
            connection.disconnect()
        }
    }

    override fun ranking(game: String, variant: String): List<RankingEntry>? {
        val connection = open("rankings/$game/$variant", RANKING_TIMEOUT_MILLIS) ?: return null

        return try {
            if (connection.responseCode != 200) {
                onFailure("El API respondió ${connection.responseCode} a un ranking", null)
                return null
            }
            val entries = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONArray("entries")
            List(entries.length()) { index ->
                val entry = entries.getJSONObject(index)
                RankingEntry(
                    rank = entry.getInt("rank"),
                    id = entry.getString("id"),
                    seconds = entry.getInt("seconds"),
                    playerId = entry.getString("playerId"),
                    playerName = entry.getString("playerName"),
                    playerAvatar = if (entry.isNull("playerAvatar")) null else entry.getString("playerAvatar"),
                )
            }
        } catch (e: IOException) {
            onFailure("Sin respuesta del API al pedir un ranking", e)
            null
        } catch (e: JSONException) {
            onFailure("Ranking ilegible", e)
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun open(path: String, timeoutMillis: Int = TIMEOUT_MILLIS): HttpURLConnection? = try {
        (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            setRequestProperty("Accept", "application/json")
        }
    } catch (e: IOException) {
        onFailure("Dirección del API no válida", e)
        null
    }

    private companion object {
        /** For the times, sent in the background: no one is waiting for them. */
        const val TIMEOUT_MILLIS = 15_000

        /**
         * For a ranking, which someone is looking at a spinner for: as much to connect as to read the
         * answer, so that it says it failed within about 8 seconds.
         */
        const val RANKING_TIMEOUT_MILLIS = 4_000

        fun isoDate(millis: Long): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date(millis))
    }
}

/**
 * Sends the queued times, once the server knows the player: before that, it could not tell whose they
 * are. With the mode without connection on, nothing goes out, as with the player.
 */
class ScoreSync(
    private val account: PlayerAccount,
    private val uploads: ScoreUploads,
    private val api: ScoreApi?,
    /** The rankings as last downloaded: what a time can not make is not sent, and what it makes is marked out of date. */
    private val rankings: GlobalRankings? = null,
) {

    fun run(): PlayerSync.Outcome {
        if (account.offline || api == null || !account.registered) return PlayerSync.Outcome.DONE

        for (score in uploads.all()) {
            if (rankings?.cannotEnter(score) == true) {
                uploads.remove(score.id)
                continue
            }
            when (val result = api.submit(account.id, account.secret, score)) {
                is SubmitResult.Filed -> {
                    uploads.remove(score.id)
                    if (result.rank != null) {
                        rankings?.markStale(score.game, score.variant)
                        ScoreUploads.noteRanked()
                    }
                }
                SubmitResult.Rejected -> uploads.remove(score.id)
                SubmitResult.Failed -> return PlayerSync.Outcome.TRY_LATER
                SubmitResult.PlayerUnknown -> {
                    account.markUnknownToServer()
                    return PlayerSync.Outcome.TRY_LATER
                }
            }
        }
        return PlayerSync.Outcome.DONE
    }
}

/**
 * Everything the server should have: first the player, then the times. If the server turns out to have
 * lost the player, it is registered again and the times go after it, in the same run.
 */
class FullSync(
    private val account: PlayerAccount,
    private val playerApi: PlayerApi?,
    private val uploads: ScoreUploads,
    private val scoreApi: ScoreApi?,
    private val rankings: GlobalRankings? = null,
) {
    fun run(): PlayerSync.Outcome {
        repeat(ROUNDS) {
            val player = PlayerSync(account, playerApi).run()
            val wasRegistered = account.registered
            val scores = ScoreSync(account, uploads, scoreApi, rankings).run()
            // Only the times can find out that the server lost the player: they unregister it.
            val lostPlayer = wasRegistered && !account.registered
            if (!lostPlayer) {
                return if (player == PlayerSync.Outcome.TRY_LATER || scores == PlayerSync.Outcome.TRY_LATER) {
                    PlayerSync.Outcome.TRY_LATER
                } else {
                    PlayerSync.Outcome.DONE
                }
            }
        }
        return PlayerSync.Outcome.TRY_LATER
    }

    private companion object {
        /** One round, and one more after sending a lost player again: never a loop. */
        const val ROUNDS = 2
    }
}

/**
 * The global rankings, each kept as last downloaded so that it can still be seen without a
 * connection. One that was never downloaded is not shown at all. A copy is good for [MAX_AGE_MILLIS]:
 * every download is a read paid for on the server, and a ranking seldom changes from one minute to the next.
 */
class GlobalRankings(
    private val store: KeyValueStore,
    private val api: ScoreApi?,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun cached(game: String, variant: String): Ranking? {
        val json = store.get(key(game, variant)) ?: return null
        return try {
            val saved = JSONObject(json)
            val entries = saved.getJSONArray("entries")
            Ranking(
                entries = List(entries.length()) { index ->
                    val entry = entries.getJSONObject(index)
                    RankingEntry(
                        rank = entry.getInt("rank"),
                        id = entry.getString("id"),
                        seconds = entry.getInt("s"),
                        playerId = entry.getString("player"),
                        playerName = entry.getString("name"),
                        playerAvatar = if (entry.has("avatar")) entry.getString("avatar") else null,
                    )
                },
                fetchedAt = saved.getLong("at"),
            )
        } catch (_: JSONException) {
            null
        }
    }

    /**
     * Whether [refresh] would ask the server: not if the player has never been on it, which is when the
     * rankings are not theirs to see yet, nor if it wants no connections.
     */
    fun canRefresh(account: PlayerAccount): Boolean = api != null && !account.offline && account.registered

    /**
     * Whether the copy kept is recent enough to show without asking again: downloaded less than
     * [MAX_AGE_MILLIS] ago, after any time of this player's made it ([markStale]), and with this player's
     * current name and avatar on its times, once the server has them.
     */
    fun isFresh(account: PlayerAccount, game: String, variant: String): Boolean {
        val ranking = cached(game, variant) ?: return false
        if (clock() - ranking.fetchedAt !in 0 until MAX_AGE_MILLIS) return false
        val staleAt = store.get(staleKey(game, variant))?.toLongOrNull()
        if (staleAt != null && staleAt >= ranking.fetchedAt) return false
        if (account.pending) return true
        val avatar = account.avatar.encode()
        return ranking.entries.none { it.playerId == account.id && (it.playerName != account.name || it.playerAvatar != avatar) }
    }

    /** Marks a ranking out of date, as when a time of this player's has just made it. */
    fun markStale(game: String, variant: String) {
        store.put(staleKey(game, variant), clock().toString())
    }

    /**
     * Whether [score] can not make its ranking, going by the copy kept: ten times there already, each
     * as fast or faster (a tie ranks after the one that got there first). Rankings only get faster, so
     * an old copy will do; the one way to lose a place is a player deleted, too rare to send for.
     */
    fun cannotEnter(score: PendingScore): Boolean {
        val entries = cached(score.game, score.variant)?.entries ?: return false
        return entries.none { it.id == score.id } && entries.count { it.seconds <= score.seconds } >= RANKING_SIZE
    }

    /**
     * Downloads a ranking and keeps it, when [canRefresh]. Blocks: call it off the main thread. Null if
     * nothing new could be had: not allowed, no answer, or a fault on the server's side.
     */
    fun refresh(account: PlayerAccount, game: String, variant: String): Ranking? {
        if (!canRefresh(account)) return null
        // When it was asked for, not answered: a time made meanwhile may be missing from the answer.
        val askedAt = clock()
        val entries = api?.ranking(game, variant) ?: return null

        val ranking = Ranking(entries, askedAt)
        val array = JSONArray()
        for (entry in entries) {
            val item = JSONObject()
                .put("rank", entry.rank)
                .put("id", entry.id)
                .put("s", entry.seconds)
                .put("player", entry.playerId)
                .put("name", entry.playerName)
            entry.playerAvatar?.let { item.put("avatar", it) }
            array.put(item)
        }
        store.put(key(game, variant), JSONObject().put("at", ranking.fetchedAt).put("entries", array).toString())
        return ranking
    }

    /** Drops the downloaded rankings, as when they are another server's. */
    fun forgetAll(games: List<String>, variants: List<String>) {
        for (game in games) for (variant in variants) {
            store.remove(key(game, variant))
            store.remove(staleKey(game, variant))
        }
    }

    private fun key(game: String, variant: String) = "ranking/$game/$variant"

    private fun staleKey(game: String, variant: String) = "ranking-stale/$game/$variant"

    companion object {
        /** How long a downloaded ranking is shown without asking for it again. */
        const val MAX_AGE_MILLIS = 10 * 60 * 1000L

        /** As on the server: the ten best of everyone. */
        const val RANKING_SIZE = 10
    }
}
