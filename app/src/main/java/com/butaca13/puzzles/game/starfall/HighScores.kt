package com.butaca13.puzzles.game.starfall

import com.butaca13.puzzles.data.KeyValueStore
import org.json.JSONArray
import org.json.JSONObject

/** A run in the table: its score, the level it reached, and when it was played, in epoch milliseconds. */
data class ScoreEntry(val score: Int, val level: Int, val playedAt: Long)

/**
 * The ten best runs, kept on this phone only. There are no initials, as in the arcade original: the
 * phone has a single player.
 */
class HighScores(private val store: KeyValueStore) {

    var entries: List<ScoreEntry> = load()
        private set

    fun qualifies(score: Int) = score > 0 && (entries.size < CAP || score > entries.last().score)

    /**
     * Files a finished run if it makes the table, and gives the row it went in, or -1. A tie goes below
     * the earlier runs with the same score.
     */
    fun add(entry: ScoreEntry): Int {
        if (!qualifies(entry.score)) return -1
        val row = entries.indexOfFirst { it.score < entry.score }.let { if (it < 0) entries.size else it }
        entries = (entries.take(row) + entry + entries.drop(row)).take(CAP)
        store.put(KEY, JSONArray(entries.map { JSONObject().put("score", it.score).put("level", it.level).put("at", it.playedAt) }).toString())
        return row
    }

    private fun load(): List<ScoreEntry> {
        val json = store.get(KEY) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) {
                val o = array.getJSONObject(it)
                ScoreEntry(o.getInt("score"), o.getInt("level"), o.getLong("at"))
            }.sortedByDescending { it.score }.take(CAP)
        }.getOrDefault(emptyList())
    }

    companion object {
        const val CAP = 10
        private const val KEY = "starfall/scores"
    }
}
