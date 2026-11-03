package com.studio3dpro.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.studio3dpro.app.StudioPreferences

/**
 * Lifecycle for the two full-screen ad formats.
 *
 * Rules baked in here:
 *  - nothing loads or shows once the user bought "Remove ads";
 *  - an interstitial is never shown twice within [AdConfig.INTERSTITIAL_COOLDOWN_MS];
 *  - a rewarded ad is always opt-in and only shown when the user asked for it.
 *
 * The banner lives in its own composable ([AdBannerSlot]) because it is a view,
 * not a full-screen overlay.
 */
object AdsManager {

    private const val TAG = "AdsManager"

    private var initialized = false

    /** True once UMP allows ad requests and the Mobile Ads SDK is initialised. */
    var ready by mutableStateOf(false)
        private set
    private var appContext: Context? = null

    private var interstitial: InterstitialAd? = null
    private var interstitialLoading = false
    private var rewarded: RewardedAd? = null
    private var rewardedLoading = false
    private var lastInterstitialAt = 0L

    /** Ads are suppressed permanently after purchase, temporarily after a reward. */
    fun adsAllowed(): Boolean =
        !StudioPreferences.adsRemoved && System.currentTimeMillis() >= StudioPreferences.adFreeUntilMillis

    /** Must only be called after [ConsentManager.canRequestAds] is true. */
    fun init(context: Context) {
        if (initialized || !ConsentManager.canRequestAds(context)) return
        initialized = true
        appContext = context.applicationContext
        MobileAds.initialize(context.applicationContext) {
            Log.i(TAG, "Mobile Ads initialised (test units=${AdConfig.usingTestUnits})")
            ready = true
            loadInterstitial()
            loadRewarded()
        }
    }

    // ------------------------------------------------------------- interstitial

    fun loadInterstitial() {
        val context = appContext ?: return
        if (!adsAllowed() || interstitial != null || interstitialLoading) return
        interstitialLoading = true
        InterstitialAd.load(
            context,
            AdConfig.INTERSTITIAL_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialLoading = false
                    interstitial = ad
                    Log.i(TAG, "Interstitial loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialLoading = false
                    interstitial = null
                    Log.w(TAG, "Interstitial failed to load: ${error.code} ${error.message}")
                }
            }
        )
    }

    /**
     * Shows a preloaded interstitial at a natural break. Silently does nothing
     * while the user is ad-free, the cooldown is active, or nothing is cached.
     */
    fun showInterstitial(activity: Activity?) {
        if (activity == null || !adsAllowed()) return
        val now = System.currentTimeMillis()
        if (now - lastInterstitialAt < AdConfig.INTERSTITIAL_COOLDOWN_MS) return
        val ad = interstitial
        if (ad == null) {
            loadInterstitial()
            return
        }
        lastInterstitialAt = now
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                loadInterstitial()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Interstitial failed to show: ${error.code} ${error.message}")
                interstitial = null
                loadInterstitial()
            }
        }
        ad.show(activity)
    }

    // ----------------------------------------------------------------- rewarded

    fun loadRewarded() {
        val context = appContext ?: return
        if (StudioPreferences.adsRemoved || rewarded != null || rewardedLoading) return
        rewardedLoading = true
        RewardedAd.load(
            context,
            AdConfig.REWARDED_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedLoading = false
                    rewarded = ad
                    Log.i(TAG, "Rewarded loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedLoading = false
                    rewarded = null
                    Log.w(TAG, "Rewarded failed to load: ${error.code} ${error.message}")
                }
            }
        )
    }

    /**
     * Opt-in rewarded ad. [onReward] fires only when the user earned the reward;
     * [onUnavailable] fires when nothing is cached or the ad failed to show.
     */
    fun showRewarded(
        activity: Activity?,
        onReward: () -> Unit,
        onUnavailable: () -> Unit
    ) {
        val ad = rewarded
        if (activity == null || StudioPreferences.adsRemoved) {
            onUnavailable()
            return
        }
        if (ad == null) {
            loadRewarded()
            onUnavailable()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewarded = null
                loadRewarded()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "Rewarded failed to show: ${error.code} ${error.message}")
                rewarded = null
                loadRewarded()
                onUnavailable()
            }
        }
        ad.show(activity) {
            StudioPreferences.grantAdFree(AdConfig.REWARDED_AD_FREE_MINUTES)
            onReward()
        }
    }
}
