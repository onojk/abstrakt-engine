package com.onojk.abstrakt

import android.app.Activity
import android.content.Context
import com.onojk.abstrakt.BuildConfig
import android.content.SharedPreferences
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BillingManager(private val context: Context) {

    companion object {
        const val PRODUCT_ID_REMOVE_ADS = "remove_ads_pro"
        private const val TAG = "BillingManager"
        private const val PREFS_NAME = "abstrakt_prefs"
        private const val PREFS_KEY_HAS_PRO = "has_pro_purchase"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Seeded from SharedPreferences so paying users never see a cold-start ad flash.
    // Play validates / corrects this value once the billing client connects.
    private val _hasProState = MutableStateFlow(prefs.getBoolean(PREFS_KEY_HAS_PRO, false))
    val hasProState: StateFlow<Boolean> = _hasProState.asStateFlow()

    // Populated by queryProductDetails() after billing client connects.
    // UI falls back to a hardcoded string until this resolves.
    private val _formattedPrice = MutableStateFlow<String?>(null)
    val formattedPrice: StateFlow<String?> = _formattedPrice.asStateFlow()

    private var billingClient: BillingClient? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null) scope.launch { handlePurchases(purchases) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                if (BuildConfig.DEBUG) Log.d(TAG, "User cancelled purchase flow")
            }
            else -> {
                Log.w(TAG, "Purchase update error ${billingResult.responseCode}: ${billingResult.debugMessage}")
            }
        }
    }

    fun initialize() {
        val client = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .enableAutoServiceReconnection()   // BL 8.0.0+: reconnects automatically on drop
            .build()
        billingClient = client

        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Billing client connected")
                    scope.launch {
                        queryAndRestorePurchases()
                        queryProductDetails()
                    }
                } else {
                    Log.w(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                // BillingClient stays alive; subsequent API calls will surface SERVICE_DISCONNECTED
                // and callers can retry. We do not null-out billingClient here so isReady checks work.
                Log.w(TAG, "Billing service disconnected")
            }
        })
    }

    // Called at startup and from restorePurchases(). Validates entitlement against Play.
    private suspend fun queryAndRestorePurchases() {
        val client = billingClient ?: return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val result = client.queryPurchasesAsync(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            handlePurchases(result.purchasesList)
        } else {
            Log.w(TAG, "queryPurchasesAsync failed: ${result.billingResult.debugMessage}")
        }
    }

    // Fetches the Play-localised price for the remove-ads product.
    private suspend fun queryProductDetails() {
        val client = billingClient ?: return
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID_REMOVE_ADS)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        val result = client.queryProductDetails(
            QueryProductDetailsParams.newBuilder().setProductList(productList).build()
        )
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val price = result.productDetailsList
                ?.firstOrNull { it.productId == PRODUCT_ID_REMOVE_ADS }
                ?.oneTimePurchaseOfferDetails
                ?.formattedPrice
            _formattedPrice.value = price
        } else {
            Log.w(TAG, "queryProductDetails failed: ${result.billingResult.debugMessage}")
        }
    }

    // Central purchase handler — called from PurchasesUpdatedListener AND queryPurchasesAsync().
    // Only grants Pro for PURCHASED state; PENDING stays false until Play confirms payment.
    private suspend fun handlePurchases(purchases: List<Purchase>) {
        val hasPro = purchases.any { purchase ->
            purchase.products.contains(PRODUCT_ID_REMOVE_ADS) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        _hasProState.value = hasPro
        // Write-through cache so the next cold start seeds the correct state immediately.
        prefs.edit().putBoolean(PREFS_KEY_HAS_PRO, hasPro).apply()

        // Acknowledge every unacknowledged PURCHASED item.
        // Google auto-refunds any purchase not acknowledged within 72 hours.
        purchases
            .filter { purchase ->
                purchase.products.contains(PRODUCT_ID_REMOVE_ADS) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                    !purchase.isAcknowledged
            }
            .forEach { purchase -> acknowledgePurchase(purchase) }
    }

    private suspend fun acknowledgePurchase(purchase: Purchase) {
        val client = billingClient ?: return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        val result = client.acknowledgePurchase(params)
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            // orderId not logged — purchase identifiers must not reach logcat
        } else {
            Log.w(TAG, "Acknowledge failed (will retry next launch): ${result.debugMessage}")
        }
    }

    fun launchPurchaseFlow(activity: Activity) {
        val client = billingClient
        if (client == null || !client.isReady) {
            Log.w(TAG, "launchPurchaseFlow called before billing client ready")
            android.widget.Toast.makeText(
                activity,
                "Connecting to Play Store… please try again in a moment.",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
            return
        }
        scope.launch {
            val productList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID_REMOVE_ADS)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
            val result = client.queryProductDetails(
                QueryProductDetailsParams.newBuilder().setProductList(productList).build()
            )
            val productDetails = result.productDetailsList
                ?.firstOrNull { it.productId == PRODUCT_ID_REMOVE_ADS }
            if (productDetails == null) {
                Log.w(TAG, "Product details not found for $PRODUCT_ID_REMOVE_ADS")
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        activity,
                        "Could not load product — check your Play Store connection.",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
                return@launch
            }
            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(productDetails)
                            .build()
                    )
                )
                .build()
            withContext(Dispatchers.Main) {
                client.launchBillingFlow(activity, billingFlowParams)
            }
        }
    }

    fun restorePurchases() {
        scope.launch { queryAndRestorePurchases() }
    }

    fun release() {
        billingClient?.endConnection()
        scope.cancel()
    }
}
