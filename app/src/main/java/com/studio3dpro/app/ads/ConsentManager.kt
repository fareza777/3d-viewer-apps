package com.studio3dpro.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Google User Messaging Platform (UMP) consent flow. Consent info is refreshed
 * on every start; the form is shown only when required. Ads are initialised
 * only once [canRequestAds] is true.
 */
object ConsentManager {

    private const val TAG = "ConsentManager"
    private val started = AtomicBoolean(false)

    /** Whether Settings should show a "Privacy options" entry. */
    var privacyOptionsRequired by mutableStateOf(false)
        private set

    fun canRequestAds(context: Context): Boolean =
        UserMessagingPlatform.getConsentInformation(context).canRequestAds()

    fun gather(activity: Activity, onCanRequestAds: () -> Unit) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()
        info.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) Log.w(TAG, "Consent form: ${error.errorCode} ${error.message}")
                    updatePrivacyStatus(info)
                    if (info.canRequestAds()) startAds(onCanRequestAds)
                }
            },
            { error ->
                Log.w(TAG, "Consent info update failed: ${error.errorCode} ${error.message}")
                updatePrivacyStatus(info)
                if (info.canRequestAds()) startAds(onCanRequestAds)
            }
        )
        // Consent from a previous session lets ads start in parallel.
        if (info.canRequestAds()) startAds(onCanRequestAds)
    }

    fun showPrivacyOptions(activity: Activity, onDone: () -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Log.w(TAG, "Privacy options: ${error.errorCode} ${error.message}")
            val info = UserMessagingPlatform.getConsentInformation(activity)
            updatePrivacyStatus(info)
            if (info.canRequestAds()) AdsManager.init(activity)
            onDone()
        }
    }

    private fun updatePrivacyStatus(info: ConsentInformation) {
        privacyOptionsRequired = info.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun startAds(block: () -> Unit) {
        if (started.compareAndSet(false, true)) block()
    }
}