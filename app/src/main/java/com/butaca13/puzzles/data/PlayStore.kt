package com.butaca13.puzzles.data

import android.app.Activity
import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Google Play's billing, the store of release builds. Products are one-time ones, set up in Play Console. */
class PlayStore(context: Context) : Store, PurchasesUpdatedListener {

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private var listener: (StoreUpdate) -> Unit = {}

    /** The products last priced, needed to buy them. */
    private var details: Map<String, ProductDetails> = emptyMap()

    override fun setListener(listener: (StoreUpdate) -> Unit) {
        this.listener = listener
    }

    override suspend fun connect(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    val ready = result.responseCode == BillingClient.BillingResponseCode.OK
                    if (!ready) Log.w(Billing.LOG_TAG, "Google Play no disponible: ${result.responseCode} ${result.debugMessage}")
                    if (continuation.isActive) continuation.resume(ready)
                }

                // The client reconnects by itself (enableAutoServiceReconnection) on the next call.
                override fun onBillingServiceDisconnected() {
                    if (continuation.isActive) continuation.resume(false)
                }
            })
        }
    }

    override suspend fun prices(products: List<String>): Map<String, String>? {
        val query = products.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val result = client.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(query).build())
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return null

        val found = result.productDetailsList.orEmpty()
        details = found.associateBy { it.productId }
        return found.mapNotNull { product ->
            product.oneTimePurchaseOfferDetails?.formattedPrice?.let { product.productId to it }
        }.toMap()
    }

    override suspend fun purchases(): List<StorePurchase>? {
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        )
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return null
        return result.purchasesList.map(::toStore)
    }

    override suspend fun acknowledge(token: String) {
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build())
    }

    override fun launchPurchase(activity: Activity, productId: String): Boolean {
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
        listener(
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> StoreUpdate.Purchased(purchases.orEmpty().map(::toStore))
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> StoreUpdate.AlreadyOwned
                BillingClient.BillingResponseCode.USER_CANCELED -> StoreUpdate.Canceled
                else -> StoreUpdate.Failed("${result.responseCode} ${result.debugMessage}")
            },
        )
    }

    private fun toStore(purchase: Purchase) = StorePurchase(
        products = purchase.products,
        purchased = purchase.purchaseState == Purchase.PurchaseState.PURCHASED,
        pending = purchase.purchaseState == Purchase.PurchaseState.PENDING,
        acknowledged = purchase.isAcknowledged,
        token = purchase.purchaseToken,
    )
}
