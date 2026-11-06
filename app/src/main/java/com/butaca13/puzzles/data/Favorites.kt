package com.butaca13.puzzles.data

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The games the player keeps at hand on the home screen, by id, in the order they were added. Kept on
 * the phone. Compose state, so that the home screen changes the moment one is added or taken away.
 */
@Stable
class Favorites(private val store: KeyValueStore) {

    var ids by mutableStateOf(load())
        private set

    operator fun contains(id: String): Boolean = id in ids

    /** Adds the game, or takes it away if it was there already. */
    fun toggle(id: String) {
        ids = if (id in ids) ids - id else ids + id
        store.put(KEY, ids.joinToString(","))
    }

    private fun load(): List<String> =
        store.get(KEY).orEmpty().split(",").filter { it.isNotEmpty() }.distinct()

    private companion object {
        const val KEY = "favorites"
    }
}
