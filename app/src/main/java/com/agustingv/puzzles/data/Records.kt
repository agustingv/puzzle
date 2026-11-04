package com.agustingv.puzzles.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * How a finished game ranks among this device's best times. [rank] is 1-based, null if outside.
 * [previousBest] is the best before this one, null if it is the first.
 */
data class RecordResult(val seconds: Int, val rank: Int?, val best: Int, val previousBest: Int? = null) {
    val isNewBest: Boolean = rank == 1

    /** A best time beaten, not just a first one. */
    val improved: Boolean = previousBest != null && seconds < previousBest
}

/**
 * One of the player's best times. [id] is made up when the game is won, and names it on the server
 * too, so that sending it twice files it once. [playedAt] is in epoch milliseconds, null for the times
 * kept from before the app noted when.
 */
data class RecordEntry(val id: String, val seconds: Int, val playedAt: Long?)

/**
 * Best times per game and variant (a difficulty or a word length), fastest first. Each one that makes
 * the ten best is also queued for the global rankings, see [ScoreUploads]: a time outside this
 * player's ten best can not make the ten best of everyone, so nothing else is worth sending.
 */
class Records(
    private val store: KeyValueStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val uploads = ScoreUploads(store)

    fun top(game: String, variant: String): List<Int> = entries(game, variant).map { it.seconds }

    fun entries(game: String, variant: String): List<RecordEntry> {
        val json = store.get(key(game, variant)) ?: return emptyList()

        return try {
            val array = JSONArray(json)
            if (array.length() > 0 && array.opt(0) is Number) return upgrade(game, variant, array)
            List(array.length()) { index ->
                val entry = array.getJSONObject(index)
                RecordEntry(
                    id = entry.getString(ID),
                    seconds = entry.getInt(SECONDS),
                    playedAt = if (entry.has(PLAYED_AT)) entry.getLong(PLAYED_AT) else null,
                )
            }
        } catch (_: org.json.JSONException) {
            emptyList()
        }
    }

    /** A time equal to an existing one ranks below it: the earlier run got there first. */
    fun add(game: String, variant: String, seconds: Int): RecordResult {
        val entries = entries(game, variant)
        val position = entries.count { it.seconds <= seconds }
        val entry = RecordEntry(newId(), seconds, clock())
        val updated = (entries.take(position) + entry + entries.drop(position)).take(MAX_ENTRIES)

        save(game, variant, updated)
        val rank = (position + 1).takeIf { it <= MAX_ENTRIES }
        if (rank != null) uploads.add(PendingScore(entry.id, game, variant, seconds, entry.playedAt))

        return RecordResult(
            seconds = seconds,
            rank = rank,
            best = updated.first().seconds,
            previousBest = entries.firstOrNull()?.seconds,
        )
    }

    /**
     * Times saved before they had an id are plain numbers. They get one each, and, being this
     * player's best, go to the rankings like any new one, without a date.
     */
    fun upgradeAll(games: List<String>, variants: List<String>) {
        for (game in games) for (variant in variants) entries(game, variant)
    }

    private fun upgrade(game: String, variant: String, array: JSONArray): List<RecordEntry> {
        val entries = List(array.length()) { RecordEntry(newId(), array.getInt(it), null) }
        save(game, variant, entries)
        entries.forEach { uploads.add(PendingScore(it.id, game, variant, it.seconds, null)) }
        return entries
    }

    private fun save(game: String, variant: String, entries: List<RecordEntry>) {
        val array = JSONArray()
        for (entry in entries) {
            val json = JSONObject().put(ID, entry.id).put(SECONDS, entry.seconds)
            entry.playedAt?.let { json.put(PLAYED_AT, it) }
            array.put(json)
        }
        store.put(key(game, variant), array.toString())
    }

    private fun key(game: String, variant: String) = "records/$game/$variant"

    companion object {
        const val MAX_ENTRIES = 10
        private const val ID = "id"
        private const val SECONDS = "s"
        private const val PLAYED_AT = "at"
    }
}
