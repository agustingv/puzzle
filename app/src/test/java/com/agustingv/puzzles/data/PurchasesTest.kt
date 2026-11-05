package com.agustingv.puzzles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchasesTest {

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
    private val solitaire = Products.forGame("solitaire")
    private val chess = Products.forGame("chess")

    @Test
    fun `a free game is always open, a paid one only once bought`() {
        val entitlements = Entitlements(store)

        assertTrue(entitlements.unlocked(null))
        assertFalse(entitlements.unlocked(solitaire))

        entitlements.add(setOf(solitaire))
        assertTrue(entitlements.unlocked(solitaire))
        assertFalse(entitlements.unlocked(chess))
    }

    @Test
    fun `all the games at once open every paid game, even one added later`() {
        val entitlements = Entitlements(store)
        entitlements.add(setOf(Products.ALL_GAMES))

        assertTrue(entitlements.unlocked(solitaire))
        assertTrue(entitlements.unlocked(Products.forGame("a-game-not-made-yet")))
    }

    @Test
    fun `what is owned is kept for when Google Play can not be reached`() {
        Entitlements(store).add(setOf(solitaire))

        assertTrue(Entitlements(store).unlocked(solitaire))
    }

    @Test
    fun `the full list from Google Play takes away what it no longer lists, as a refund`() {
        val entitlements = Entitlements(store)
        entitlements.add(setOf(solitaire, chess))

        entitlements.replace(setOf(chess))

        assertFalse(entitlements.unlocked(solitaire))
        assertTrue(entitlements.unlocked(chess))
        assertFalse(Entitlements(store).unlocked(solitaire))
    }

    @Test
    fun `only paid purchases open games, pending ones wait, and new ones are acknowledged`() {
        val state = PurchaseState.of(
            listOf(
                StorePurchase(listOf(solitaire), purchased = true, pending = false, acknowledged = false, token = "new"),
                StorePurchase(listOf(chess), purchased = true, pending = false, acknowledged = true, token = "old"),
                StorePurchase(listOf(Products.ALL_GAMES), purchased = false, pending = true, acknowledged = false, token = "cash"),
            ),
        )

        assertEquals(setOf(solitaire, chess), state.owned)
        assertEquals(setOf(Products.ALL_GAMES), state.pending)
        assertEquals(listOf("new"), state.toAcknowledge)
    }

    @Test
    fun `stored data that can not be read owns nothing, rather than failing`() {
        store.put("purchases/owned", "not json")

        assertFalse(Entitlements(store).unlocked(solitaire))
    }
}
