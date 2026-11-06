package com.butaca13.puzzles.data

import android.app.Activity
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

/**
 * A simulated Google Play, for debug builds: purchases can be tried on any phone or emulator before the
 * products exist in Play Console. It keeps its own purchases, apart from what the app believes it owns,
 * as Google Play does, so the app still has to ask for them; and what Google Play does on its own, as a
 * payment in cash completed days later or a refund, is done by hand from the settings.
 */
@Stable
class FakeStore(private val storage: KeyValueStore) : Store {

    /** A purchase on the simulated account. */
    data class Purchase(val product: String, val paid: Boolean, val acknowledged: Boolean, val token: String) {
        fun toStore() = StorePurchase(listOf(product), purchased = paid, pending = !paid, acknowledged, token)
    }

    /** How the payment sheet is answered. */
    enum class Answer { PAY, PAY_LATER, FAIL, CANCEL }

    private var listener: (StoreUpdate) -> Unit = {}

    var purchases by mutableStateOf(load())
        private set

    /** Whether the store can be reached; off, as without a connection or without Google Play. */
    var available by mutableStateOf(storage.get(KEY_AVAILABLE) != "false")
        private set

    /** The product whose payment sheet is open, if any. */
    var sheet by mutableStateOf<String?>(null)
        private set

    fun price(product: String): String = if (product == Products.ALL_GAMES) "4,99 €" else "1,99 €"

    fun purchaseOf(product: String): Purchase? = purchases.firstOrNull { it.product == product }

    override fun setListener(listener: (StoreUpdate) -> Unit) {
        this.listener = listener
    }

    override suspend fun connect(): Boolean = available

    override suspend fun prices(products: List<String>): Map<String, String>? =
        if (available) products.associateWith(::price) else null

    override suspend fun purchases(): List<StorePurchase>? =
        if (available) purchases.map { it.toStore() } else null

    override suspend fun acknowledge(token: String) {
        if (available) change(token) { it.copy(acknowledged = true) }
    }

    override fun launchPurchase(activity: Activity, productId: String): Boolean = open(productId)

    /** [launchPurchase] without the activity it does not need, for the tests. */
    internal fun open(product: String): Boolean {
        if (!available) return false
        // Google Play does not sell twice what is owned, or being paid for.
        if (purchaseOf(product) != null) listener(StoreUpdate.AlreadyOwned) else sheet = product
        return true
    }

    fun answer(answer: Answer) {
        val product = sheet ?: return
        sheet = null
        when (answer) {
            Answer.PAY, Answer.PAY_LATER -> {
                val purchase = Purchase(product, paid = answer == Answer.PAY, acknowledged = false, UUID.randomUUID().toString())
                save(purchases + purchase)
                listener(StoreUpdate.Purchased(listOf(purchase.toStore())))
            }
            Answer.FAIL -> listener(StoreUpdate.Failed("simulada"))
            Answer.CANCEL -> listener(StoreUpdate.Canceled)
        }
    }

    /** A payment waiting, made now: Google Play tells the app at once, if it is running. */
    fun completePayment(product: String) {
        val purchase = purchaseOf(product)?.takeIf { !it.paid } ?: return
        change(purchase.token) { it.copy(paid = true) }
        listener(StoreUpdate.Purchased(listOf(purchaseOf(product)!!.toStore())))
    }

    /**
     * A payment never made, or a purchase refunded: gone from the account. Google Play does not tell the
     * app; it finds out the next time it asks, on coming back to it or on restoring the purchases.
     */
    fun remove(product: String) = save(purchases.filterNot { it.product == product })

    fun changeAvailable(available: Boolean) {
        this.available = available
        storage.put(KEY_AVAILABLE, available.toString())
    }

    private fun change(token: String, update: (Purchase) -> Purchase) =
        save(purchases.map { if (it.token == token) update(it) else it })

    private fun save(purchases: List<Purchase>) {
        this.purchases = purchases
        val array = JSONArray()
        for (purchase in purchases) {
            array.put(
                JSONObject()
                    .put("product", purchase.product)
                    .put("paid", purchase.paid)
                    .put("acknowledged", purchase.acknowledged)
                    .put("token", purchase.token),
            )
        }
        storage.put(KEY_PURCHASES, array.toString())
    }

    private fun load(): List<Purchase> {
        val json = storage.get(KEY_PURCHASES) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            List(array.length()) {
                val item = array.getJSONObject(it)
                Purchase(item.getString("product"), item.getBoolean("paid"), item.getBoolean("acknowledged"), item.getString("token"))
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private companion object {
        const val KEY_PURCHASES = "fake-store/purchases"
        const val KEY_AVAILABLE = "fake-store/available"
    }
}
