package com.studio3dpro.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studio3dpro.app.BuildConfig
import com.studio3dpro.app.SceneFormat
import com.studio3dpro.app.StudioPreferences
import com.studio3dpro.app.StudioViewModel
import com.studio3dpro.app.ThemeMode
import com.studio3dpro.app.ads.AdConfig
import com.studio3dpro.app.ads.AdsManager
import com.studio3dpro.app.ads.ConsentManager
import com.studio3dpro.app.ads.findActivity
import com.studio3dpro.app.ads.rememberAdFreeClock
import com.studio3dpro.app.billing.RemoveAdsBilling

@Composable
fun SettingsScreen(vm: StudioViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val activity = context.findActivity()
    val now = rememberAdFreeClock()
    LaunchedEffect(Unit) { RemoveAdsBilling.ensureStarted(context) }
    Column(
        Modifier
            .fillMaxSize()
            .background(StudioColors.Ink)
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudioIconButton(Icons.Rounded.ArrowBack, "Back", size = 38.dp, onClick = onClose)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text("Settings", style = MaterialTheme.typography.headlineSmall, color = StudioColors.TextPrimary)
                Text(
                    "Studio preferences · v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioColors.TextMuted
                )
            }
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(StudioColors.SurfaceRaised)
                    .border(1.dp, StudioColors.BorderSoft, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text("ON-DEVICE", style = MaterialTheme.typography.labelSmall, color = StudioColors.Mint)
            }
        }
        Hairline()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionHeader("Appearance", caption = "Chrome, 3D canvas and overlays")
            SettingsCard {
                Text(
                        "Choose how the studio looks, including the 3D viewport background. Changes apply immediately and persist across launches.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioColors.TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        ThemeTile(
                            mode = mode,
                            selected = StudioPreferences.themeMode == mode,
                            modifier = Modifier.weight(1f)
                        ) {
                            StudioPreferences.updateThemeMode(mode)
                        }
                    }
                }
            }

            SectionHeader("Canvas", caption = "Viewport overlays and navigation")
            SettingsCard {
                ToggleRow(
                    title = "Show FPS and triangle count",
                    subtitle = "Live performance readout in the top-right HUD",
                    checked = StudioPreferences.showFps,
                    onCheckedChange = { StudioPreferences.updateShowFps(it) }
                )
                Hairline(Modifier.padding(vertical = 6.dp))
                ToggleRow(
                    title = "Show grid",
                    subtitle = "Ground grid in the viewport",
                    checked = vm.showGrid,
                    onCheckedChange = { vm.changeGridVisibility(it) }
                )
                Hairline(Modifier.padding(vertical = 6.dp))
                ToggleRow(
                    title = "Show skeleton",
                    subtitle = "Bone overlay in Rig and Animate modes",
                    checked = vm.showSkeleton,
                    onCheckedChange = { vm.changeSkeletonVisibility(it) }
                )
                Hairline(Modifier.padding(vertical = 6.dp))
                ToggleRow(
                    title = "Wireframe overlay",
                    subtitle = "Skinned edge overlay on the whole scene",
                    checked = vm.wireframe,
                    onCheckedChange = { vm.changeWireframe(it) }
                )
                Hairline(Modifier.padding(vertical = 6.dp))
                ToggleRow(
                    title = "Dynamic shadows",
                    subtitle = "Soft key-light shadows on the ground and on the model",
                    checked = StudioPreferences.dynamicShadows,
                    onCheckedChange = { vm.changeDynamicShadows(it) }
                )
            }

            SectionHeader("Workflow", caption = "Save safety and feedback")
            SettingsCard {
                ToggleRow(
                    title = "Autosave",
                    subtitle = "Writes a recovery snapshot 1.8 s after edits",
                    checked = StudioPreferences.autosaveEnabled,
                    onCheckedChange = { StudioPreferences.updateAutosave(it) }
                )
                Hairline(Modifier.padding(vertical = 6.dp))
                ToggleRow(
                    title = "Haptic feedback",
                    subtitle = "Short taps for modes, tools and keyframes",
                    checked = StudioPreferences.hapticsEnabled,
                    onCheckedChange = { StudioPreferences.updateHaptics(it) }
                )
            }

            SectionHeader("Ads & rewards", caption = "Keeps the studio free")
            SettingsCard {
                if (StudioPreferences.adsRemoved) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(StudioColors.Mint.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Check, null, tint = StudioColors.Mint, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Ads removed", style = MaterialTheme.typography.titleSmall, color = StudioColors.TextPrimary)
                            Text(
                                "Thank you. No banner or interstitial is shown on this device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = StudioColors.TextMuted
                            )
                        }
                    }
                } else {
                    Text(
                        "The banner sits in its own row under the viewport, so it never covers the mode dock or the Android navigation bar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioColors.TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    StudioPrimaryButton(
                        text = "Remove ads · ${RemoveAdsBilling.priceLabel}",
                        icon = Icons.Rounded.Block,
                        enabled = !RemoveAdsBilling.busy,
                        onClick = {
                            if (activity == null) vm.showMessage("Purchases need a visible screen")
                            else RemoveAdsBilling.purchase(activity)
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    Hairline()
                    SettingsAction(
                        icon = Icons.Rounded.PlayArrow,
                        title = "Watch a rewarded ad",
                        subtitle = "Unlock 30 minutes with no banner or interstitial"
                    ) {
                        AdsManager.showRewarded(
                            activity,
                            onReward = { vm.showMessage("Ad-free for 30 minutes") },
                            onUnavailable = { vm.showMessage("No rewarded ad ready yet — try again shortly") }
                        )
                    }
                    val boostLeft = StudioPreferences.adFreeUntilMillis - now
                    if (boostLeft > 0L) {
                        Text(
                            "Ad-free boost active · ${(boostLeft / 60_000L) + 1} min left",
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioColors.Mint
                        )
                    }
                }
                RemoveAdsBilling.statusMessage?.let { status ->
                    Spacer(Modifier.height(8.dp))
                    Text(status, style = MaterialTheme.typography.bodySmall, color = StudioColors.Amber)
                }
            }

            SectionHeader("Export defaults", caption = "Used by the Export sheet")
            SettingsCard {
                Text("Default format", style = MaterialTheme.typography.titleSmall, color = StudioColors.TextPrimary)
                Spacer(Modifier.height(8.dp))
                SegmentedOptions(
                    options = listOf("GLB", "OBJ", "STL", "BVH"),
                    selectedIndex = listOf(
                        SceneFormat.GLB, SceneFormat.OBJ, SceneFormat.STL, SceneFormat.BVH
                    ).indexOf(StudioPreferences.defaultExport).coerceAtLeast(0),
                    onSelect = { index ->
                        val format = listOf(
                            SceneFormat.GLB, SceneFormat.OBJ, SceneFormat.STL, SceneFormat.BVH
                        )[index]
                        StudioPreferences.updateDefaultExport(format)
                    }
                )
                Spacer(Modifier.height(10.dp))
                ToggleRow(
                    title = "Include every animation clip",
                    subtitle = "GLB exports Walk, Run and Idle in one file",
                    checked = StudioPreferences.exportAllClips,
                    onCheckedChange = { StudioPreferences.updateExportAllClips(it) }
                )
                ToggleRow(
                    title = "Include textures",
                    subtitle = "Embed packed images in GLB projects",
                    checked = StudioPreferences.exportTextures,
                    onCheckedChange = { StudioPreferences.updateExportTextures(it) }
                )
            }

            SectionHeader("First run & data", caption = "Replay or reset")
            SettingsCard {
                SettingsAction(
                    icon = Icons.Rounded.RestartAlt,
                    title = "Replay onboarding",
                    subtitle = "See the three-page studio tour again"
                ) { vm.replayOnboarding() }
                Hairline(Modifier.padding(vertical = 5.dp))
                SettingsAction(
                    icon = Icons.Rounded.Refresh,
                    title = "Reset ATLAS demo",
                    subtitle = "Restore the rigged character and its three clips"
                ) { vm.resetDemoScene() }
                Hairline(Modifier.padding(vertical = 5.dp))
                SettingsAction(
                    icon = Icons.Rounded.DeleteOutline,
                    title = "Clear autosave",
                    subtitle = "Remove the recovery snapshot from this device",
                    danger = true
                ) { vm.clearAutosave() }
            }

            SectionHeader("About", caption = "Privacy and build")
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudioMark(46.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("3D Studio Pro", style = MaterialTheme.typography.headlineSmall, color = StudioColors.TextPrimary)
                        Text(
                            "Model · Rig · Animate",
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioColors.Accent
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · OpenGL ES 3.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioColors.TextMuted
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Hairline()
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Shield, null, tint = StudioColors.Mint, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "Your scenes stay on this device. No account, no cloud upload and no analytics. Ads are served by Google AdMob with your consent. Exports are written only to the folder you choose.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioColors.TextSecondary
                    )
                }
                Spacer(Modifier.height(10.dp))
                SettingsAction(
                    icon = Icons.Rounded.Policy,
                    title = "Privacy policy",
                    subtitle = "fareza777.github.io/studio3dpro"
                ) {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(AdConfig.PRIVACY_POLICY_URL)
                            )
                        )
                    }
                }
                if (ConsentManager.privacyOptionsRequired && activity != null) {
                    Hairline(Modifier.padding(vertical = 5.dp))
                    SettingsAction(
                        icon = Icons.Rounded.PrivacyTip,
                        title = "Privacy options",
                        subtitle = "Review or change your ad consent choices"
                    ) { ConsentManager.showPrivacyOptions(activity) }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Info, null, tint = StudioColors.TextMuted, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "24-bone humanoid rig, GPU skinning, PBR materials, skinned GLB with animation clips. Designed for phones and tablets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioColors.TextMuted
                    )
                }
            }

            Text(
                "© 2026 3D Studio Pro · Made for creators on the move",
                style = MaterialTheme.typography.labelSmall,
                color = StudioColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(StudioColors.Surface)
            .border(1.dp, StudioColors.BorderSoft, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        content = content
    )
}

@Composable
private fun ThemeTile(
    mode: ThemeMode,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) StudioColors.Accent.copy(alpha = 0.13f) else StudioColors.SurfaceRaised)
            .border(
                1.dp,
                if (selected) StudioColors.Accent.copy(alpha = 0.7f) else StudioColors.BorderSoft,
                RoundedCornerShape(13.dp)
            )
            .clickable(onClick = onClick)
            .padding(7.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when (mode) {
                        ThemeMode.LIGHT -> Brush.linearGradient(listOf(Color(0xFFF5F1E8), Color(0xFFF5F1E8)))
                        ThemeMode.DARK -> Brush.linearGradient(listOf(Color(0xFF111318), Color(0xFF111318)))
                        else -> Brush.linearGradient(
                            listOf(Color(0xFFF5F1E8), Color(0xFF111318)),
                            start = androidx.compose.ui.geometry.Offset.Zero,
                            end = androidx.compose.ui.geometry.Offset(400f, 400f)
                        )
                    }
                )
        ) {
            Column(Modifier.padding(6.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth(0.62f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (mode == ThemeMode.DARK) Color(0x66FFFFFF) else Color(0x6617171B))
                )
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) {
                        Box(
                            Modifier
                                .width(9.dp)
                                .height(9.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (mode == ThemeMode.DARK) Color(0xFFB9A7FF) else Color(0xFF6246D8))
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                mode.label,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) StudioColors.Accent else StudioColors.TextPrimary
            )
            Spacer(Modifier.weight(1f))
            if (selected) {
                Icon(Icons.Rounded.Check, null, tint = StudioColors.Accent, modifier = Modifier.size(13.dp))
            }
        }
        Text(
            mode.caption,
            style = MaterialTheme.typography.labelSmall,
            color = StudioColors.TextMuted,
            maxLines = 1
        )
    }
}

@Composable
private fun SettingsAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background((if (danger) StudioColors.Danger else StudioColors.Accent).copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                null,
                tint = if (danger) StudioColors.Danger else StudioColors.Accent,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (danger) StudioColors.Danger else StudioColors.TextPrimary
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = StudioColors.TextMuted)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = StudioColors.TextMuted, modifier = Modifier.size(16.dp))
    }
}
