package com.butaca13.puzzles.data

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.butaca13.puzzles.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.json.JSONArray
import org.json.JSONException

/**
 * What can be bought, as one-time products in Google Play: each paid game on its own, and all of them,
 * those there are and those to come, at once. The ids must match the products set up in Play Console.
 */
object Products {
    const val ALL_GAMES = "all_games"

    fun forGame(gameId: String): String = "game_$gameId"
}

/**
 * The products this player owns, as Google Play last said, kept on the phone so that what was bought
 * works without a connection. Compose state, so that the menu unlocks a game the moment it is paid.
 */
@Stable
class Entitlements(private val store: KeyValueStore) {

    var owned by mutableStateOf(load())
        private set

    /** A free game is always open; a paid one, once bought on its own or with all of them. */
    fun unlocked(productId: String?): Boolean =
        productId == null || productId in owned || Products.ALL_GAMES in owned

    /** Everything Google Play says is owned now: what it no longer lists, refunded say, is gone. */
    internal fun replace(products: Set<String>) {
        if (products == owned) return
        owned = products
        store.put(KEY, JSONArray(products.sorted()).toString())
    }

    /** A purchase just made: added to what is owned, without waiting for the full list. */
    internal fun add(products: Set<String>) = replace(owned + products)

    private fun load(): Set<String> {
        val json = store.get(KEY) ?: return emptySet()
        return try {
            val array = JSONArray(json)
            List(array.length()) { array.getString(it) }.toSet()
        } catch (_: JSONException) {
            emptySet()
        }
    }

    private companion object {
        const val KEY = "purchases/owned"
    }
}

/** A purchase as Google Play reports it, reduced to what matters here, so that it can be tested. */
data class StorePurchase(
    val products: List<String>,
    val purchased: Boolean,
    val pending: Boolean,
    val acknowledged: Boolean,
    val token: String,
)

/** What a list of purchases means: what is owned, what is waiting for its payment, and what to acknowledge. */
data class PurchaseState(val owned: Set<String>, val pending: Set<String>, val toAcknowledge: List<String>) {
    companion object {
        fun of(purchases: List<StorePurchase>) = PurchaseState(
            owned = purchases.filter { it.purchased }.flatMap { it.products }.toSet(),
            // A payment in cash at a shop, say, can take days: nothing opens until it is made.
            pending = purchases.filter { it.pending }.flatMap { it.products }.toSet(),
            toAcknowledge = purchases.filter { it.purchased && !it.acknowledged }.map { it.token },
        )
    }
}


/** What the store says about a purchase flow once it ends, or about a purchase that changes meanwhile. */
sealed interface StoreUpdate {
    /** Bought, or waiting for its payment: [purchases] are only the ones that changed. */
    data class Purchased(val purchases: List<StorePurchase>) : StoreUpdate

    /** Bought before, on this account, but not known here. */
    data object AlreadyOwned : StoreUpdate

    data object Canceled : StoreUpdate

    data class Failed(val reason: String) : StoreUpdate
}

/**
 * The store's side of buying: Google Play, or a simulated one to try purchases without it, see
 * [FakeStore]. Each call that asks the store something gives null when it can not answer.
 */
interface Store {
    /** Where [StoreUpdate]s go; set once, before anything is bought. */
    fun setListener(listener: (StoreUpdate) -> Unit)

    suspend fun connect(): Boolean

    /** Each product's price as the store shows it, for those it sells. */
    suspend fun prices(products: List<String>): Map<String, String>?

    /** Everything this account owns or is paying for. */
    suspend fun purchases(): List<StorePurchase>?

    suspend fun acknowledge(token: String)

    /** Opens the store's payment sheet. False when the product is not available to buy now. */
    fun launchPurchase(activity: Activity, productId: String): Boolean
}

/**
 * Buying games, whatever the store: the prices, what is paid and what is waiting, and what this account
 * owns, kept in [entitlements]. Each purchase is acknowledged, or Google refunds it after three days.
 * Without the store, as on a phone without Google Play, nothing can be bought and what was bought stays
 * as kept.
 */
@Stable
class Billing(
    private val store: Store,
    private val entitlements: Entitlements,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val refreshing = Mutex()

    /** Each product's price as the store shows it, in the player's currency and with taxes. */
    var prices by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /** Products bought but not paid yet. */
    var pending by mutableStateOf<Set<String>>(emptySet())
        private set

    init {
        store.setListener(::onUpdate)
    }

    /**
     * Asks the store for the prices of [products] and for what is owned, as on starting or coming back
     * to the app. [products] are the games' own; the one for all of them is always asked for.
     */
    fun refresh(products: List<String>) {
        scope.launch {
            // One at a time: coming back to the app while a refresh is under way asks for nothing more.
            if (!refreshing.tryLock()) return@launch
            try {
                if (!store.connect()) return@launch
                store.prices((products + Products.ALL_GAMES).distinct())?.let { prices = it }
                loadPurchases()
            } finally {
                refreshing.unlock()
            }
        }
    }

    /** Opens the store's payment sheet. False when the product is not available to buy now. */
    fun buy(activity: Activity, productId: String): Boolean = store.launchPurchase(activity, productId)

    private fun onUpdate(update: StoreUpdate) {
        when (update) {
            is StoreUpdate.Purchased -> scope.launch { settle(update.purchases, complete = false) }
            // The full list says so.
            StoreUpdate.AlreadyOwned -> scope.launch { if (store.connect()) loadPurchases() }
            StoreUpdate.Canceled -> Unit
            is StoreUpdate.Failed -> Log.w(LOG_TAG, "Compra fallida: ${update.reason}")
        }
    }

    private suspend fun loadPurchases() {
        // Without an answer, what was kept stays: a game paid for does not lock for want of a connection.
        val purchases = store.purchases() ?: return
        settle(purchases, complete = true)
    }

    /** [complete] when [purchases] is all this account owns, not just a purchase that has come in. */
    private suspend fun settle(purchases: List<StorePurchase>, complete: Boolean) {
        val state = PurchaseState.of(purchases)
        // A failure is tried again on the next refresh: the purchase stays unacknowledged until then.
        for (token in state.toAcknowledge) store.acknowledge(token)
        if (complete) {
            entitlements.replace(state.owned)
            pending = state.pending
        } else {
            entitlements.add(state.owned)
            pending = pending - state.owned + state.pending
        }
    }

    companion object {
        internal const val LOG_TAG = "Purchases"

        @Volatile
        private var instance: Billing? = null

        /** The simulated store, when this build uses it; null with Google Play. */
        @Volatile
        var fakeStore: FakeStore? = null
            private set

        /**
         * One for the whole app, as Google recommends: a single connection to Google Play. Debug builds
         * use a simulated store instead, unless built with -PfakeStore=false.
         */
        fun of(context: Context): Billing = instance ?: synchronized(this) {
            instance ?: run {
                val app = context.applicationContext
                val store = if (BuildConfig.FAKE_STORE) {
                    FakeStore(AppStorage.of(app)).also { fakeStore = it }
                } else {
                    PlayStore(app)
                }
                Billing(store, entitlementsOf(app)).also { instance = it }
            }
        }

        @Volatile
        private var entitlements: Entitlements? = null

        fun entitlementsOf(context: Context): Entitlements = entitlements ?: synchronized(this) {
            entitlements ?: Entitlements(AppStorage.of(context)).also { entitlements = it }
        }
    }
}
