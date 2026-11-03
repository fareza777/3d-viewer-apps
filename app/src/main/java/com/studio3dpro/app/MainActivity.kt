package com.studio3dpro.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.studio3dpro.app.ui.StudioColors
import com.studio3dpro.app.ui.StudioScreen
import com.studio3dpro.app.ui.StudioTheme
import com.studio3dpro.app.ads.AdsManager
import com.studio3dpro.app.billing.RemoveAdsBilling

class MainActivity : ComponentActivity() {

    private var pendingImport by mutableStateOf<Uri?>(null)
    private val vm: StudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        StudioPreferences.init(applicationContext)
        StudioHaptics.init(applicationContext)
        // Ads and billing both need the Activity; starting them here means the
        // first banner and the remove-ads price are ready by the time the
        // studio draws.
        AdsManager.init(this)
        RemoveAdsBilling.ensureStarted(this)
        pendingImport = intent?.data
        enableEdgeToEdge()
        setContent {
            StudioTheme(StudioPreferences.themeMode) {
                ApplySystemBars()
                LaunchedEffect(pendingImport, vm.isLoading) {
                    if (!vm.isLoading) {
                        pendingImport?.let { uri ->
                            pendingImport = null
                            vm.importFile(uri)
                        }
                    }
                }
                StudioScreen(vm)
            }
        }
    }

    @Composable
    private fun ApplySystemBars() {
        // The loading surface is always dark ink, so keep light status icons
        // until the themed studio becomes visible.
        val light = StudioColors.isLight && !vm.isLoading
        val activity = LocalContext.current as? ComponentActivity
        LaunchedEffect(light) {
            if (activity == null) return@LaunchedEffect
            activity.enableEdgeToEdge(
                statusBarStyle = if (light) {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                } else {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                },
                navigationBarStyle = if (light) {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                } else {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                }
            )
            // Some OEM skins keep the previous icon tint after enableEdgeToEdge.
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingImport = intent.data
    }
}
