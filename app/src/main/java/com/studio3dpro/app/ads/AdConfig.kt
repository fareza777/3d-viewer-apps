package com.studio3dpro.app.ads

import com.studio3dpro.app.BuildConfig

/**
 * Single place for every monetization id so the release build only needs one
 * edit (plus the AdMob app id in the manifest) before publishing.
 *
 * The ids below are Google's official **test** units. They always fill with a
 * "Test Ad" creative and never bill an advertiser, so a debug or sideloaded
 * build can be exercised end to end. Replace all four with the real units from
 * the AdMob console before uploading to Play.
 */
object AdConfig {

    // AdMob app id — must match the com.google.android.gms.ads.APPLICATION_ID
    // meta-data in AndroidManifest.xml.
    // Ids come from BuildConfig: Google test units for debug, real units for release
    // (see app/build.gradle.kts buildTypes).
    const val ADMOB_APP_ID = BuildConfig.ADMOB_APP_ID

    const val BANNER_UNIT_ID = BuildConfig.ADMOB_BANNER_ID
    const val INTERSTITIAL_UNIT_ID = BuildConfig.ADMOB_INTERSTITIAL_ID
    const val REWARDED_UNIT_ID = BuildConfig.ADMOB_REWARDED_ID

    const val PRIVACY_POLICY_URL = "https://fareza777.github.io/studio3dpro/privacy-policy.html"

    /** True while the ids above are still Google's test units. */
    val usingTestUnits: Boolean = ADMOB_APP_ID.startsWith("ca-app-pub-3940256099942544")

    /** One-time, non-consumable in-app product created in Play Console at $4.99. */
    const val REMOVE_ADS_PRODUCT_ID = "remove_ads"

    /** Shown until Play returns the localized, tax-inclusive price. */
    const val REMOVE_ADS_PRICE_FALLBACK = "$4.99"

    /** Rewarded ad grant: a temporary pause of banner + interstitial ads. */
    const val REWARDED_AD_FREE_MINUTES = 30L

    /** An interstitial is only offered after this much time has passed. */
    const val INTERSTITIAL_COOLDOWN_MS = 3 * 60 * 1000L
}
