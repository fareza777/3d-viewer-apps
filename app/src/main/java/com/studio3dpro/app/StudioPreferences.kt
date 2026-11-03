package com.studio3dpro.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode(val label: String, val caption: String) {
    SYSTEM("System", "Match device"),
    LIGHT("Light", "Warm paper"),
    DARK("Dark", "Obsidian")
}

/**
 * Small, synchronous preference store. Everything here is UI-level, so
 * SharedPreferences keeps startup instant and survives process death without
 * pulling in a DataStore dependency.
 */
object StudioPreferences {

    private const val FILE = "studio_preferences"
    private var prefs: SharedPreferences? = null

    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set
    var onboardingComplete by mutableStateOf(false)
        private set
    var autosaveEnabled by mutableStateOf(true)
        private set
    var hapticsEnabled by mutableStateOf(true)
        private set
    var showFps by mutableStateOf(true)
        private set
    var defaultExport by mutableStateOf(SceneFormat.GLB)
        private set
    var exportAllClips by mutableStateOf(true)
        private set
    var exportTextures by mutableStateOf(true)
        private set
    var dynamicShadows by mutableStateOf(true)
        private set

    /** True once the one-time "Remove ads" purchase is owned. */
    var adsRemoved by mutableStateOf(false)
        private set

    /** End of a rewarded ad-free window; 0 when no boost is running. */
    var adFreeUntilMillis by mutableStateOf(0L)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        themeMode = runCatching {
            ThemeMode.valueOf(p.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)
        onboardingComplete = p.getBoolean("onboarding_complete", false)
        autosaveEnabled = p.getBoolean("autosave", true)
        hapticsEnabled = p.getBoolean("haptics", true)
        showFps = p.getBoolean("show_fps", true)
        defaultExport = runCatching {
            SceneFormat.valueOf(p.getString("export_format", SceneFormat.GLB.name) ?: SceneFormat.GLB.name)
        }.getOrDefault(SceneFormat.GLB)
        exportAllClips = p.getBoolean("export_clips", true)
        exportTextures = p.getBoolean("export_textures", true)
        dynamicShadows = p.getBoolean("dynamic_shadows", true)
        adsRemoved = p.getBoolean("ads_removed", false)
        adFreeUntilMillis = p.getLong("ad_free_until", 0L)
    }

    fun updateThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs?.edit()?.putString("theme_mode", mode.name)?.apply()
    }

    fun updateOnboardingComplete(value: Boolean) {
        onboardingComplete = value
        prefs?.edit()?.putBoolean("onboarding_complete", value)?.apply()
    }

    fun updateAutosave(value: Boolean) {
        autosaveEnabled = value
        prefs?.edit()?.putBoolean("autosave", value)?.apply()
    }

    fun updateHaptics(value: Boolean) {
        hapticsEnabled = value
        prefs?.edit()?.putBoolean("haptics", value)?.apply()
    }

    fun updateShowFps(value: Boolean) {
        showFps = value
        prefs?.edit()?.putBoolean("show_fps", value)?.apply()
    }

    fun updateDefaultExport(format: SceneFormat) {
        defaultExport = format
        prefs?.edit()?.putString("export_format", format.name)?.apply()
    }

    fun updateExportAllClips(value: Boolean) {
        exportAllClips = value
        prefs?.edit()?.putBoolean("export_clips", value)?.apply()
    }

    fun updateExportTextures(value: Boolean) {
        exportTextures = value
        prefs?.edit()?.putBoolean("export_textures", value)?.apply()
    }

    fun updateDynamicShadows(value: Boolean) {
        dynamicShadows = value
        prefs?.edit()?.putBoolean("dynamic_shadows", value)?.apply()
    }

    fun updateAdsRemoved(value: Boolean) {
        adsRemoved = value
        if (value) adFreeUntilMillis = 0L
        prefs?.edit()?.putBoolean("ads_removed", value)?.apply()
    }

    /** Grants (or extends) the rewarded ad-free window by [minutes]. */
    fun grantAdFree(minutes: Long) {
        val base = maxOf(System.currentTimeMillis(), adFreeUntilMillis)
        adFreeUntilMillis = base + minutes * 60_000L
        prefs?.edit()?.putLong("ad_free_until", adFreeUntilMillis)?.apply()
    }
}
