package com.butaca13.puzzles.data

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONException
import kotlin.coroutines.resume

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

/**
 * Google Play's billing: the prices of the products, buying them, and what this account owns. Each
 * purchase is acknowledged, or Google refunds it after three days. Without Google Play, as on a phone
 * without it or a build installed by hand, nothing can be bought and what was bought stays as kept.
 */
@Stable
class PlayBilling private constructor(context: Context, private val entitlements: Entitlements) :
    PurchasesUpdatedListener {

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val refreshing = Mutex()
    private var details: Map<String, ProductDetails> = emptyMap()

    /** Each product's price as Google Play shows it, in the player's currency and with taxes. */
    var prices by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /** Products bought but not paid yet. */
    var pending by mutableStateOf<Set<String>>(emptySet())
        private set

    /**
     * Asks Google Play for the prices of [products] and for what is owned, as on starting or coming back
     * to the app. [products] are the games' own; the one for all of them is always asked for.
     */
    fun refresh(products: List<String>) {
        scope.launch {
            // One at a time: coming back to the app while a refresh is under way asks for nothing more.
            if (!refreshing.tryLock()) return@launch
            try {
                if (!connect()) return@launch
                loadPrices(products + Products.ALL_GAMES)
                loadPurchases()
            } finally {
                refreshing.unlock()
            }
        }
    }

    /** Opens Google Play's payment sheet. False when the product is not available to buy now. */
    fun buy(activity: Activity, productId: String): Boolean {
        val product = details[productId] ?: return false
        val offer = product.oneTimePurchaseOfferDetails ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        // A product with several purchase options needs the one chosen: its only one here.
                        .apply { offer.offerToken?.let(::setOfferToken) }
                        .build(),
                ),
            )
            .build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch { settle(purchases.orEmpty().map(::toStore), complete = false) }
            // Bought before, on this account, but not known here: the full list says so.
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { if (connect()) loadPurchases() }
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> Log.w(LOG_TAG, "Compra fallida: ${result.responseCode} ${result.debugMessage}")
        }
    }

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    val ready = result.responseCode == BillingClient.BillingResponseCode.OK
                    if (!ready) Log.w(LOG_TAG, "Google Play no disponible: ${result.responseCode} ${result.debugMessage}")
                    if (continuation.isActive) continuation.resume(ready)
                }

                // The client reconnects by itself (enableAutoServiceReconnection) on the next call.
                override fun onBillingServiceDisconnected() {
                    if (continuation.isActive) continuation.resume(false)
                }
            })
        }
    }

    private suspend fun loadPrices(ids: List<String>) {
        val products = ids.distinct().map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val result = client.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(products).build())
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return

        val found = result.productDetailsList.orEmpty()
        details = found.associateBy { it.productId }
        prices = found.mapNotNull { product ->
            product.oneTimePurchaseOfferDetails?.formattedPrice?.let { product.productId to it }
        }.toMap()
    }

    private suspend fun loadPurchases() {
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        )
        // Without an answer, what was kept stays: a game paid for does not lock for want of a connection.
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return
        settle(result.purchasesList.map(::toStore), complete = true)
    }

    /** [complete] when [purchases] is all this account owns, not just a purchase that has come in. */
    private suspend fun settle(purchases: List<StorePurchase>, complete: Boolean) {
        val state = PurchaseState.of(purchases)
        for (token in state.toAcknowledge) {
            // A failure is tried again on the next refresh: the purchase stays unacknowledged until then.
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build())
        }
        if (complete) {
            entitlements.replace(state.owned)
            pending = state.pending
        } else {
            entitlements.add(state.owned)
            pending = pending - state.owned + state.pending
        }
    }

    private fun toStore(purchase: Purchase) = StorePurchase(
        products = purchase.products,
        purchased = purchase.purchaseState == Purchase.PurchaseState.PURCHASED,
        pending = purchase.purchaseState == Purchase.PurchaseState.PENDING,
        acknowledged = purchase.isAcknowledged,
        token = purchase.purchaseToken,
    )

    companion object {
        private const val LOG_TAG = "Purchases"

        @Volatile
        private var instance: PlayBilling? = null

        /** One for the whole app, as Google recommends: a single connection to Google Play. */
        fun of(context: Context): PlayBilling = instance ?: synchronized(this) {
            instance ?: PlayBilling(context.applicationContext, entitlementsOf(context)).also { instance = it }
        }

        @Volatile
        private var entitlements: Entitlements? = null

        fun entitlementsOf(context: Context): Entitlements = entitlements ?: synchronized(this) {
            entitlements ?: Entitlements(AppStorage.of(context)).also { entitlements = it }
        }
    }
}
