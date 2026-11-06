package com.butaca13.puzzles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesTest {

    private val store = object : KeyValueStore {
        val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) {
            values[key] = value
        }
        override fun remove(key: String) {
            values.remove(key)
        }
    }

    @Test
    fun `games are added in order, taken away with the same star, and kept`() {
        val favorites = Favorites(store)
        favorites.toggle("sudoku")
        favorites.toggle("hangman")
        favorites.toggle("garden")
        favorites.toggle("hangman")

        assertEquals(listOf("sudoku", "garden"), favorites.ids)
        assertTrue("sudoku" in favorites)
        assertFalse("hangman" in favorites)
        assertEquals(listOf("sudoku", "garden"), Favorites(store).ids)
    }

    @Test
    fun `with nothing kept there are none`() {
        assertEquals(emptyList<String>(), Favorites(store).ids)
    }
}
