package com.studio3dpro.app.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.studio3dpro.app.StudioPreferences
import com.studio3dpro.app.ui.StudioColors
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Anchored adaptive banner that occupies its own row in the layout.
 *
 * It is deliberately **not** drawn as an overlay: it is placed above the mode
 * dock (and above the system navigation bar) so it can never cover the app's
 * bottom controls or the Android gesture/navigation buttons. The row reserves
 * exactly the height the ad reports, so the viewport does not jump when the
 * creative arrives.
 */
@Composable
fun AdBannerSlot(modifier: Modifier = Modifier) {
    val removed = StudioPreferences.adsRemoved
    val boostUntil = StudioPreferences.adFreeUntilMillis
    val now = rememberAdFreeClock()

    if (removed || now < boostUntil) return

    val context = LocalContext.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .background(StudioColors.Base)
    ) {
        val screenWidthDp = context.resources.configuration.screenWidthDp.coerceAtLeast(320)
        val widthDp = maxWidth.value.roundToInt().coerceIn(320, screenWidthDp)
        val adSize = remember(widthDp) {
            // Adaptive width banner: the SDK returns the exact height for this
            // width, so the reserved row always matches the creative.
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
        }
        // AdSize dimensions are already dp, so reserve them directly; converting
        // them as pixels would clip the creative.
        var rowHeight by remember(adSize) { mutableIntStateOf(adSize.height) }
        Box(
            Modifier
                .fillMaxWidth()
                .height(rowHeight.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    AdView(ctx).apply {
                        setAdSize(adSize)
                        adUnitId = AdConfig.BANNER_UNIT_ID
                        var fellBack = false
                        adListener = object : AdListener() {
                            override fun onAdFailedToLoad(error: LoadAdError) {
                                Log.w(
                                    "AdBannerSlot",
                                    "Banner failed (${error.code}) at ${adSize.width}dp wide: ${error.message}"
                                )
                                // Portrait adaptive requests fill, but wide layouts
                                // can be rejected as an invalid request. Retry once
                                // with the standard 320x50 banner so the reserved
                                // row still fills with a creative.
                                if (!fellBack) {
                                    fellBack = true
                                    setAdSize(AdSize.BANNER)
                                    rowHeight = AdSize.BANNER.height
                                    loadAd(AdRequest.Builder().build())
                                }
                            }
                        }
                        loadAd(AdRequest.Builder().build())
                    }
                },
                modifier = Modifier.wrapContentSize(),
                onRelease = { view ->
                    view.destroy()
                }
            )
        }
    }
}

/**
 * Coarse clock used to expire the rewarded "ad-free" window while the studio is
 * on screen. It only recomposes once per second and only while a boost exists.
 */
@Composable
fun rememberAdFreeClock(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val boostUntil = StudioPreferences.adFreeUntilMillis
    LaunchedEffect(boostUntil) {
        if (boostUntil <= System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            return@LaunchedEffect
        }
        while (true) {
            delay(1000L)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** Walks the context chain so Compose can reach the hosting Activity for ads. */
fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
