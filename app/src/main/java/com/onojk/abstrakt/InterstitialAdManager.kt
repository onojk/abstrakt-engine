package com.onojk.abstrakt

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Manages interstitial ad lifecycle: preload, show, reload after dismissal.
 *
 * Usage:
 *   val manager = InterstitialAdManager(context)
 *   manager.preload()                  // call once on app start
 *   manager.show(activity)             // call when you want to show the ad
 *   // After show(), the ad auto-reloads in the background
 */
class InterstitialAdManager(private val context: Context) {
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false

    fun preload() {
        if (interstitialAd != null || isLoading) return
        isLoading = true

        InterstitialAd.load(
            context,
            AdUnits.interstitialId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d("InterstitialAd", "loaded")
                    interstitialAd = ad
                    isLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w("InterstitialAd", "load failed: ${error.message}")
                    interstitialAd = null
                    isLoading = false
                }
            },
        )
    }

    /**
     * Show the ad if one is loaded. After dismissal, automatically
     * triggers preload of the next ad. Safe to call when no ad
     * is loaded — silently no-ops.
     */
    fun show(activity: Activity) {
        val ad = interstitialAd
        if (ad == null) {
            Log.d("InterstitialAd", "show called but no ad loaded; preloading next")
            preload()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d("InterstitialAd", "dismissed; preloading next")
                interstitialAd = null
                preload()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w("InterstitialAd", "failed to show: ${error.message}")
                interstitialAd = null
                preload()
            }
        }

        ad.show(activity)
    }
}
