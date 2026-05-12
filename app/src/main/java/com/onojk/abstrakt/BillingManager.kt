package com.onojk.abstrakt

import android.app.Activity
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Google Play Billing for the abstrakt engine Pro IAP.
 *
 * ⚠️ SCAFFOLD ONLY — NOT READY FOR PRODUCTION ⚠️
 *
 * This file captures the architecture but does NOT implement the
 * actual BillingClient calls. All real implementation is marked TODO.
 *
 * Before this can ship to real users:
 *   1. Register `remove_ads_pro` product in Play Console
 *      (one-time, non-consumable, $2.99 USD)
 *   2. Implement BillingClient.startConnection() in `initialize()`
 *   3. Implement queryProductDetails() to fetch the real product
 *   4. Implement launchBillingFlow() in `launchPurchaseFlow()`
 *   5. Implement PurchasesUpdatedListener to handle results
 *   6. Implement acknowledgePurchase() — REQUIRED within 72 hours
 *      or Google auto-refunds the user
 *   7. Implement queryPurchasesAsync() on connection to restore
 *      previous purchases (essential for reinstalls)
 *   8. Persist purchase state to DataStore for fast app-startup
 *      check
 *   9. Test thoroughly on Play Console internal testing track
 *      with a license tester account
 *  10. Test the full lifecycle: purchase → reinstall → verify ads
 *      still hidden
 *
 * Until those TODOs are done, hasProState always emits false (user
 * always treated as "not paid"). The UI button is visible and
 * tappable but only Toasts "Not yet implemented."
 */
class BillingManager(private val context: Context) {

    companion object {
        const val PRODUCT_ID_REMOVE_ADS = "remove_ads_pro"
    }

    private val _hasProState = MutableStateFlow(false)

    /**
     * Public state: true if user has purchased the remove-ads IAP.
     * Consumed by MainActivity to control the showAds flag.
     *
     * SCAFFOLD: always false until real BillingClient is wired up.
     */
    val hasProState: StateFlow<Boolean> = _hasProState.asStateFlow()

    /**
     * Initialize the BillingClient connection. Called once from
     * AbstraktApp.onCreate().
     *
     * TODO: Implement actual BillingClient.startConnection()
     * TODO: On connection success, call queryPurchasesAsync() to
     *       restore any prior purchases
     */
    fun initialize() {
        Log.d("BillingManager", "initialize() called — SCAFFOLD, no-op")
        // TODO: real BillingClient initialization
    }

    /**
     * Launch the Google Play purchase flow.
     * Called from the "Remove ads — $2.99" button.
     *
     * TODO: Implement BillingClient.launchBillingFlow()
     * TODO: Handle the PurchasesUpdatedListener callback
     * TODO: Call acknowledgePurchase() on success
     * TODO: Update _hasProState to true on verified purchase
     */
    fun launchPurchaseFlow(activity: Activity) {
        Log.d("BillingManager", "launchPurchaseFlow() called — SCAFFOLD, no-op")
        android.widget.Toast.makeText(
            activity,
            "Remove ads coming soon — not yet implemented",
            android.widget.Toast.LENGTH_SHORT,
        ).show()
        // TODO: real purchase flow
    }

    /**
     * Manually trigger a restore-purchases check. Useful for users
     * who reinstalled the app or switched devices.
     *
     * TODO: Implement queryPurchasesAsync() and update _hasProState
     *       based on results
     */
    fun restorePurchases() {
        Log.d("BillingManager", "restorePurchases() called — SCAFFOLD, no-op")
        // TODO: real restore flow
    }

    /**
     * Clean up the BillingClient connection on app shutdown.
     *
     * TODO: Implement BillingClient.endConnection()
     */
    fun release() {
        Log.d("BillingManager", "release() called — SCAFFOLD, no-op")
        // TODO: real cleanup
    }
}
