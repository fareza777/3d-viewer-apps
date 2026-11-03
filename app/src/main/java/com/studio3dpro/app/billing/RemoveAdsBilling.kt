package com.studio3dpro.app.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.studio3dpro.app.StudioPreferences
import com.studio3dpro.app.ads.AdConfig

/**
 * Google Play Billing for the one-time "Remove ads" product.
 *
 * The purchase is verified locally against Play (and acknowledged immediately,
 * as Play requires within three days). Ad-free state is persisted, so it
 * survives reinstalling only while Play still reports the purchase — which is
 * exactly the behaviour Play expects for a non-consumable.
 *
 * Sideloaded APKs have no Play Billing service binding, so every entry point
 * degrades to a readable status message instead of throwing.
 */
object RemoveAdsBilling {

    private const val TAG = "RemoveAdsBilling"

    private const val UNAVAILABLE =
        "Google Play Billing is not available in this build. Remove-ads purchases " +
            "work only for installs published through Google Play."

    private const val NO_PRODUCT =
        "This install has no Google Play listing for the $4.99 remove-ads product, " +
            "so it cannot be bought here. Purchases work in the build downloaded from Google Play."

    private var client: BillingClient? = null
    private var starting = false
    private var product: ProductDetails? = null
    private var productQueryDone = false
    private var pendingOnPurchased: (() -> Unit)? = null
    private var pendingOnMessage: ((String) -> Unit)? = null

    /** Localized price from Play, e.g. "Rp 62.000". Falls back to $4.99. */
    var priceLabel by mutableStateOf(AdConfig.REMOVE_ADS_PRICE_FALLBACK)
        private set
    var busy by mutableStateOf(false)
        private set
    var statusMessage by mutableStateOf<String?>(null)
        private set

    val available: Boolean get() = client?.isReady == true

    fun ensureStarted(context: Context) {
        if (client != null || starting) return
        starting = true
        val listener = PurchasesUpdatedListener { result, purchases ->
            handlePurchaseResult(result, purchases)
        }
        val billing = BillingClient.newBuilder(context.applicationContext)
            .setListener(listener)
            .enablePendingPurchases()
            .build()
        client = billing
        billing.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                starting = false
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    statusMessage = null
                    queryProduct()
                    restorePurchases()
                } else {
                    Log.w(TAG, "Billing setup failed: ${billingResult.responseCode} ${billingResult.debugMessage}")
                    statusMessage = UNAVAILABLE
                }
            }

            override fun onBillingServiceDisconnected() {
                starting = false
                statusMessage = UNAVAILABLE
            }
        })
    }

    fun clearStatus() {
        statusMessage = null
    }

    private fun queryProduct() {
        val billing = client ?: return
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(AdConfig.REMOVE_ADS_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        billing.queryProductDetailsAsync(params) { result, details ->
            productQueryDone = true
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val match = details.firstOrNull { it.productId == AdConfig.REMOVE_ADS_PRODUCT_ID }
                product = match
                match?.oneTimePurchaseOfferDetails?.formattedPrice?.let { priceLabel = it }
            } else {
                Log.w(TAG, "Product query failed: ${result.responseCode} ${result.debugMessage}")
            }
        }
    }

    private fun restorePurchases() {
        val billing = client ?: return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billing.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            purchases
                .firstOrNull { it.products.contains(AdConfig.REMOVE_ADS_PRODUCT_ID) }
                ?.let { purchase ->
                    grantRemoveAds(purchase)
                }
        }
    }

    fun purchase(activity: Activity) {
        ensureStarted(activity)
        val billing = client
        val details = product
        if (billing == null || !billing.isReady) {
            statusMessage = UNAVAILABLE
            return
        }
        if (details == null) {
            if (productQueryDone) {
                statusMessage = NO_PRODUCT
            } else {
                statusMessage = "Play is still loading the product. Try again in a moment."
                queryProduct()
            }
            return
        }
        busy = true
        clearStatus()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        val result = billing.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            busy = false
            statusMessage = "Purchase could not start (${result.responseCode}). $UNAVAILABLE"
        }
    }

    private fun handlePurchaseResult(result: BillingResult, purchases: List<Purchase>?) {
        busy = false
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val purchase = purchases?.firstOrNull { it.products.contains(AdConfig.REMOVE_ADS_PRODUCT_ID) }
                if (purchase == null) {
                    statusMessage = "Play did not return the remove-ads purchase. Nothing was charged."
                    return
                }
                if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
                    statusMessage = "Purchase is pending. Ads stop as soon as Play confirms it."
                    return
                }
                grantRemoveAds(purchase)
                statusMessage = null
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                statusMessage = "Purchase cancelled. Ads stay on."
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restorePurchases()
            else -> {
                Log.w(TAG, "Purchase failed: ${result.responseCode} ${result.debugMessage}")
                statusMessage = "Purchase failed (${result.responseCode}). $UNAVAILABLE"
            }
        }
    }

    private fun grantRemoveAds(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val ack = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client?.acknowledgePurchase(ack) { ackResult ->
                if (ackResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "Acknowledge failed: ${ackResult.responseCode} ${ackResult.debugMessage}")
                }
            }
        }
        StudioPreferences.updateAdsRemoved(true)
        pendingOnPurchased?.invoke()
        pendingOnPurchased = null
        pendingOnMessage = null
    }
}
