package com.agustingv.puzzles.data

import org.json.JSONArray

/**
 * How a finished game ranks among this device's best times. [rank] is 1-based, null if outside.
 * [previousBest] is the best before this one, null if it is the first.
 */
data class RecordResult(val seconds: Int, val rank: Int?, val best: Int, val previousBest: Int? = null) {
    val isNewBest: Boolean = rank == 1

    /** A best time beaten, not just a first one. */
    val improved: Boolean = previousBest != null && seconds < previousBest
}

/** Best times per game and variant (a difficulty or a word length), fastest first. */
class Records(private val store: KeyValueStore) {

    fun top(game: String, variant: String): List<Int> {
        val json = store.get(key(game, variant)) ?: return emptyList()

        return try {
            val array = JSONArray(json)
            List(array.length()) { array.getInt(it) }
        } catch (_: org.json.JSONException) {
            emptyList()
        }
    }

    /** A time equal to an existing one ranks below it: the earlier run got there first. */
    fun add(game: String, variant: String, seconds: Int): RecordResult {
        val times = top(game, variant)
        val position = times.count { it <= seconds }
        val updated = (times.take(position) + seconds + times.drop(position)).take(MAX_ENTRIES)

        store.put(key(game, variant), JSONArray(updated).toString())

        return RecordResult(
            seconds = seconds,
            rank = (position + 1).takeIf { it <= MAX_ENTRIES },
            best = updated.first(),
            previousBest = times.firstOrNull(),
        )
    }

    private fun key(game: String, variant: String) = "records/$game/$variant"

    companion object {
        const val MAX_ENTRIES = 10
    }
}
