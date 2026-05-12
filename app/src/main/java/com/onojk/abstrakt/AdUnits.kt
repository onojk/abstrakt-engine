package com.onojk.abstrakt

/**
 * Ad unit IDs for AdMob.
 *
 * In debug builds, uses Google's official TEST IDs. These ALWAYS
 * return ads and don't risk policy violations from clicking your
 * own ads.
 *
 * In release builds, uses the real production IDs from the
 * abstrakt engine AdMob account.
 *
 * NEVER click ads in your own debug or release build — AdMob's
 * fraud detection will permanently ban your account.
 */
object AdUnits {
    // Google official test IDs — public and safe to commit
    private const val TEST_BANNER       = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"

    // Real production IDs for abstrakt engine
    private const val REAL_BANNER       = "ca-app-pub-7313844831247942/5003874261"
    private const val REAL_INTERSTITIAL = "ca-app-pub-7313844831247942/4695313117"

    val bannerId: String
        get() = if (BuildConfig.DEBUG) TEST_BANNER else REAL_BANNER

    val interstitialId: String
        get() = if (BuildConfig.DEBUG) TEST_INTERSTITIAL else REAL_INTERSTITIAL
}
