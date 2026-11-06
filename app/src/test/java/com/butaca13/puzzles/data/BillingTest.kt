package com.butaca13.puzzles.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [Billing] with the simulated store: what the app does with what Google Play would say. */
class BillingTest {

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

    private val sliding = Products.forGame("sliding")

    // The phone and the store keep their data apart, as the app and Google Play do.
    private val phone = MemoryStore()
    private val account = MemoryStore()
    private val store = FakeStore(account)
    private val entitlements = Entitlements(phone)

    // Unconfined, and a store that never waits: every call is over when it returns.
    private val billing = Billing(store, entitlements, CoroutineScope(Dispatchers.Unconfined))

    private fun buy(product: String, answer: FakeStore.Answer) {
        assertTrue(store.open(product))
        assertEquals(product, store.sheet)
        store.answer(answer)
    }

    @Test
    fun `the prices come from the store, the one for all the games too`() {
        billing.refresh(listOf(sliding))

        assertEquals(mapOf(sliding to "1,99 €", Products.ALL_GAMES to "4,99 €"), billing.prices)
    }

    @Test
    fun `a payment opens the game at once and is acknowledged`() {
        buy(sliding, FakeStore.Answer.PAY)

        assertTrue(entitlements.unlocked(sliding))
        assertTrue(store.purchaseOf(sliding)!!.acknowledged)
    }

    @Test
    fun `a payment cancelled leaves everything as it was`() {
        buy(sliding, FakeStore.Answer.CANCEL)

        assertFalse(entitlements.unlocked(sliding))
        assertNull(store.purchaseOf(sliding))
        assertNull(store.sheet)
    }

    @Test
    fun `a payment waiting opens nothing until it is made`() {
        buy(sliding, FakeStore.Answer.PAY_LATER)

        assertFalse(entitlements.unlocked(sliding))
        assertEquals(setOf(sliding), billing.pending)
        assertFalse(store.purchaseOf(sliding)!!.acknowledged)

        store.completePayment(sliding)

        assertTrue(entitlements.unlocked(sliding))
        assertEquals(emptySet<String>(), billing.pending)
        assertTrue(store.purchaseOf(sliding)!!.acknowledged)
    }

    @Test
    fun `a payment waiting that is never made is gone on the next refresh`() {
        buy(sliding, FakeStore.Answer.PAY_LATER)
        store.remove(sliding)

        billing.refresh(emptyList())

        assertEquals(emptySet<String>(), billing.pending)
        assertFalse(entitlements.unlocked(sliding))
    }

    @Test
    fun `a refund locks the game again on the next refresh`() {
        buy(sliding, FakeStore.Answer.PAY)
        store.remove(sliding)
        assertTrue(entitlements.unlocked(sliding))

        billing.refresh(emptyList())

        assertFalse(entitlements.unlocked(sliding))
    }

    @Test
    fun `without the store nothing can be bought, and what was bought stays open`() {
        buy(sliding, FakeStore.Answer.PAY)
        store.remove(sliding)
        store.changeAvailable(false)

        billing.refresh(emptyList())

        assertTrue(entitlements.unlocked(sliding))
        assertFalse(store.open(Products.ALL_GAMES))
        assertNull(store.sheet)
    }

    @Test
    fun `a purchase made on another phone is found on refreshing`() {
        val elsewhere = FakeStore(account)
        Billing(elsewhere, Entitlements(MemoryStore()), CoroutineScope(Dispatchers.Unconfined))
        elsewhere.open(Products.ALL_GAMES)
        elsewhere.answer(FakeStore.Answer.PAY)

        // This phone, opening the app afterwards with the same account.
        val here = Entitlements(phone)
        Billing(FakeStore(account), here, CoroutineScope(Dispatchers.Unconfined)).refresh(emptyList())

        assertTrue(here.unlocked(sliding))
    }

    @Test
    fun `buying again what is owned opens no sheet, and the app learns it owns it`() {
        // Bought, and the phone then forgot it, as after clearing the app's data.
        buy(sliding, FakeStore.Answer.PAY)
        phone.values.clear()
        val forgetful = Entitlements(phone)
        val again = Billing(store, forgetful, CoroutineScope(Dispatchers.Unconfined))
        assertFalse(forgetful.unlocked(sliding))

        assertTrue(store.open(sliding))

        assertNull(store.sheet)
        assertTrue(forgetful.unlocked(sliding))
        assertEquals(emptyMap<String, String>(), again.prices)
    }

    @Test
    fun `the simulated account keeps its purchases and whether it can be reached`() {
        buy(sliding, FakeStore.Answer.PAY_LATER)
        store.changeAvailable(false)

        val reopened = FakeStore(account)

        assertEquals(store.purchases, reopened.purchases)
        assertFalse(reopened.available)
    }
}
