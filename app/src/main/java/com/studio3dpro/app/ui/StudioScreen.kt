package com.studio3dpro.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studio3dpro.app.EditorMode
import com.studio3dpro.app.SceneFormat
import com.studio3dpro.app.StudioViewModel
import com.studio3dpro.app.ads.AdBannerSlot
import com.studio3dpro.app.ads.AdsManager
import com.studio3dpro.app.ads.findActivity
import kotlinx.coroutines.delay

private data class ModeItem(val mode: EditorMode, val icon: ImageVector, val label: String)

private val modeItems = listOf(
    ModeItem(EditorMode.OBJECT, Icons.Rounded.ViewInAr, "Object"),
    ModeItem(EditorMode.EDIT, Icons.Rounded.Edit, "Model"),
    ModeItem(EditorMode.SCULPT, Icons.Rounded.Brush, "Sculpt"),
    ModeItem(EditorMode.RIG, Icons.Rounded.AccessibilityNew, "Rig"),
    ModeItem(EditorMode.ANIMATE, Icons.Rounded.Movie, "Animate")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(vm: StudioViewModel) {
    var showAddSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showNewDialog by remember { mutableStateOf(false) }
    var showInspectorSheet by remember { mutableStateOf(false) }
    var pendingFormat by remember { mutableStateOf(SceneFormat.GLB) }
    var pendingAllClips by remember { mutableStateOf(true) }
    var pendingTextures by remember { mutableStateOf(true) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile(uri)
    }
    val textureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.importTexture(uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.exportToUri(uri, pendingFormat, pendingAllClips, pendingTextures)
    }
    val screenshotLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) vm.captureScreenshot(uri)
    }
    val openImport = {
        importLauncher.launch(arrayOf("*/*"))
    }
    val requestExport: (SceneFormat, Boolean, Boolean) -> Unit = { format, all, textures ->
        pendingFormat = format
        pendingAllClips = all
        pendingTextures = textures
        val filename = vm.suggestedFileName(format)
        if (format == SceneFormat.PNG) screenshotLauncher.launch(filename.replace(".png", "_viewport.png"))
        else exportLauncher.launch(filename)
    }

    if (vm.isLoading) {
        StudioLoadingScreen()
        return
    }

    if (vm.showOnboarding) {
        OnboardingScreen(
            onExploreDemo = { vm.completeOnboarding(startEmpty = false) },
            onStartEmpty = { vm.completeOnboarding(startEmpty = true) },
            onSkip = { vm.completeOnboarding(startEmpty = false) }
        )
        return
    }

    if (vm.showSettings) {
        SettingsScreen(vm, onClose = { vm.closeSettings() })
        return
    }

    val message = vm.message
    LaunchedEffect(message?.id) {
        if (message != null) {
            delay(2600)
            vm.consumeMessage()
        }
    }

    // Interstitial at a completion milestone (a finished export), never during
    // an edit gesture. AdsManager applies the ad-free state and cooldown.
    val hostActivity = LocalContext.current.findActivity()
    val exportSignal = vm.exportCompleted
    LaunchedEffect(exportSignal) {
        if (exportSignal > 0) AdsManager.showInterstitial(hostActivity)
    }
    BackHandler(enabled = showInspectorSheet) { showInspectorSheet = false }

    // Studio playback uses the Compose frame clock; the GL thread samples
    // runtime.time independently, so the viewport stays smooth even when the
    // inspector is not recomposing.
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
                last = now
                vm.tick(dt)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(StudioColors.Ink).statusBarsPadding()) {
        val persistentInspector = maxWidth >= 1000.dp && maxHeight >= 560.dp
        val verticalRail = maxWidth >= 640.dp
        val topBarWide = maxWidth >= 760.dp
        val timelineHeight: Dp = when {
            maxHeight < 500.dp -> 150.dp
            maxHeight < 720.dp -> 205.dp
            maxHeight > 1000.dp -> 280.dp
            else -> 240.dp
        }
        Column(Modifier.fillMaxSize()) {
            StudioTopBar(
                vm = vm,
                wide = topBarWide,
                onImport = openImport,
                onExport = { showExportSheet = true },
                onNew = { showNewDialog = true },
                onHelp = { showHelp = true },
                onAbout = { showAbout = true }
            )
            Hairline()
            Row(Modifier.weight(1f)) {
                if (verticalRail) {
                    ModeRail(
                        vm = vm,
                        onHelp = { showHelp = true }
                    )
                    Box(Modifier.width(1.dp).fillMaxHeight().background(StudioColors.BorderSoft))
                }
                Column(Modifier.weight(1f)) {
                    StudioViewport(
                        vm = vm,
                        modifier = Modifier.weight(1f),
                        compact = !verticalRail,
                        propertiesVisible = !persistentInspector,
                        onOpenInspector = { showInspectorSheet = true },
                        onAddObject = { showAddSheet = true },
                        onImport = openImport
                    )
                    if (vm.showTimeline) {
                        TimelinePanel(
                            vm = vm,
                            compact = !verticalRail,
                            modifier = Modifier.height(timelineHeight),
                            onClose = { vm.toggleTimeline() }
                        )
                    }
                }
                if (persistentInspector) {
                    Box(Modifier.width(336.dp).fillMaxHeight().border(1.dp, StudioColors.BorderSoft).background(StudioColors.Surface)) {
                        InspectorPanel(
                            vm = vm,
                            narrow = false,
                            onClose = {},
                            onAddObject = { showAddSheet = true },
                            onImport = openImport,
                            onPickTexture = { textureLauncher.launch("image/*") },
                            onOpenGraphEditor = { vm.openGraphEditor() }
                        )
                    }
                }
            }
            if (!verticalRail) {
                // Reserved row directly above the dock: the banner can never
                // overlap the mode buttons or the system navigation bar.
                AdBannerSlot(Modifier.fillMaxWidth())
                ModeDock(vm)
            } else {
                AdBannerSlot(Modifier.fillMaxWidth().navigationBarsPadding())
            }
        }

        MessageToast(
            message = message,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp)
        )

        if (vm.busy) {
            BusyOverlay(vm.busyLabel)
        }
    }

    if (showAddSheet) {
        AddObjectSheet(vm, onDismiss = { showAddSheet = false }, onImport = openImport)
    }
    if (showExportSheet) {
        ExportSheet(vm, onDismiss = { showExportSheet = false }, onExport = requestExport)
    }
    vm.importProposal?.let { proposal ->
        ImportProposalDialog(vm, proposal, onDismiss = { vm.cancelImport() })
    }
    if (showHelp) {
        HelpDialog(onDismiss = { showHelp = false })
    }
    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
    if (showNewDialog) {
        NewProjectDialog(
            onDismiss = { showNewDialog = false },
            onNew = { empty ->
                showNewDialog = false
                vm.newScene(empty)
            }
        )
    }
    if (showInspectorSheet) {
        ModalBottomSheet(
            onDismissRequest = { showInspectorSheet = false },
            containerColor = StudioColors.Surface,
            contentColor = StudioColors.TextPrimary,
            dragHandle = {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(StudioColors.Border))
                }
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(0.86f).navigationBarsPadding()) {
                InspectorPanel(
                    vm = vm,
                    narrow = true,
                    onClose = { showInspectorSheet = false },
                    onAddObject = {
                        showInspectorSheet = false
                        showAddSheet = true
                    },
                    onImport = openImport,
                    onPickTexture = { textureLauncher.launch("image/*") },
                    onOpenGraphEditor = {
                        showInspectorSheet = false
                        vm.openGraphEditor()
                    }
                )
            }
        }
    }
}

// The launch surface always uses the brand ink palette so the transition from
// the system splash is seamless, whatever theme the project is set to.
@Composable
private fun StudioLoadingScreen() {
    val ink = Color(0xFF07080B)
    val cream = Color(0xFFF4F1EA)
    val lavender = Color(0xFFB9A7FF)
    val muted = Color(0xFF8E8A83)
    Column(
        Modifier.fillMaxSize().background(ink),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        StudioMark(64.dp)
        Spacer(Modifier.height(22.dp))
        Text("3D STUDIO PRO", style = MaterialTheme.typography.labelMedium, color = lavender)
        Spacer(Modifier.height(5.dp))
        Text("Preparing your studio", style = MaterialTheme.typography.headlineSmall, color = cream)
        Spacer(Modifier.height(20.dp))
        androidx.compose.material3.CircularProgressIndicator(
            color = lavender,
            strokeWidth = 2.dp,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text("Loading rig, skin and animation", style = MaterialTheme.typography.bodySmall, color = muted)
    }
}

@Composable
private fun StudioTopBar(
    vm: StudioViewModel,
    wide: Boolean,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onNew: () -> Unit,
    onHelp: () -> Unit,
    onAbout: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(StudioColors.Base)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StudioMark(30.dp)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.widthIn(max = if (wide) 230.dp else 118.dp)) {
            Text(
                "3D STUDIO PRO",
                style = MaterialTheme.typography.labelSmall,
                color = StudioColors.Accent,
                maxLines = 1
            )
            Text(
                vm.scene.name,
                style = MaterialTheme.typography.titleSmall,
                color = StudioColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (wide) {
            Spacer(Modifier.width(16.dp))
            StatusPill(
                text = vm.lastAutosave?.let { "AUTO-SAVED $it" } ?: "ON-DEVICE · READY",
                color = if (vm.lastAutosave != null) StudioColors.Green else StudioColors.TextMuted,
                icon = Icons.Rounded.Save
            )
        }
        Spacer(Modifier.weight(1f))
        if (wide) {
            StudioGhostButton("Import", icon = Icons.Rounded.FolderOpen, onClick = onImport, modifier = Modifier.height(34.dp))
            Spacer(Modifier.width(6.dp))
            StudioGhostButton("Export", icon = Icons.Rounded.FileDownload, onClick = onExport, modifier = Modifier.height(34.dp), accent = StudioColors.Accent)
            Spacer(Modifier.width(6.dp))
        }
        StudioIconButton(Icons.Rounded.Undo, "Undo", enabled = vm.canUndo, onClick = { vm.undo() })
        StudioIconButton(Icons.Rounded.Redo, "Redo", enabled = vm.canRedo, onClick = { vm.redo() })
        if (!wide) {
            StudioIconButton(Icons.Rounded.FolderOpen, "Import", onClick = onImport)
        }
        Box {
            StudioIconButton(Icons.Rounded.MoreVert, "More", onClick = { menu = true })
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                modifier = Modifier
                    .background(StudioColors.SurfaceRaised)
                    .border(1.dp, StudioColors.Border, RoundedCornerShape(12.dp))
            ) {
                TopMenuItem(Icons.Rounded.Add, "New project") {
                    menu = false
                    onNew()
                }
                TopMenuItem(Icons.Rounded.FolderOpen, "Import model or project") {
                    menu = false
                    onImport()
                }
                TopMenuItem(Icons.Rounded.FileDownload, "Export…") {
                    menu = false
                    onExport()
                }
                TopMenuItem(Icons.Rounded.Save, "Save snapshot") {
                    menu = false
                    vm.saveNow()
                }
                TopMenuItem(Icons.Rounded.Timeline, if (vm.showTimeline) "Hide timeline" else "Show timeline") {
                    menu = false
                    vm.toggleTimeline()
                }
                TopMenuItem(Icons.Rounded.Settings, "Settings") {
                    menu = false
                    vm.openSettings()
                }
                TopMenuItem(Icons.Rounded.HelpOutline, "Help & shortcuts") {
                    menu = false
                    onHelp()
                }
                TopMenuItem(Icons.Rounded.Info, "About 3D Studio Pro") {
                    menu = false
                    onAbout()
                }
            }
        }
    }
}

@Composable
private fun TopMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodySmall, color = StudioColors.TextPrimary) },
        leadingIcon = { Icon(icon, null, tint = StudioColors.TextMuted, modifier = Modifier.size(17.dp)) },
        onClick = onClick
    )
}

@Composable
private fun ModeRail(vm: StudioViewModel, onHelp: () -> Unit) {
    Column(
        Modifier
            .width(72.dp)
            .fillMaxHeight()
            .background(StudioColors.Base)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        modeItems.forEach { item ->
            val active = vm.mode == item.mode
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) StudioColors.Accent.copy(alpha = 0.10f) else Color.Transparent)
                    .clickable { vm.changeMode(item.mode) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    item.icon,
                    item.label,
                    tint = if (active) StudioColors.Accent else StudioColors.TextMuted,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    item.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) StudioColors.Accent else StudioColors.TextMuted,
                    fontSize = 9.sp
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.width(24.dp).height(1.dp).background(StudioColors.BorderSoft))
        Spacer(Modifier.height(6.dp))
        RailUtil(Icons.Rounded.Timeline, "Time", vm.showTimeline) { vm.toggleTimeline() }
        RailUtil(Icons.Rounded.HelpOutline, "Help", false, onHelp)
        Spacer(Modifier.height(4.dp))
        Text("1.0", style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted.copy(alpha = 0.5f), fontSize = 9.sp)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun RailUtil(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 7.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, tint = if (active) StudioColors.Mint else StudioColors.TextMuted, modifier = Modifier.size(17.dp))
        Spacer(Modifier.height(2.dp))
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = if (active) StudioColors.Mint else StudioColors.TextMuted, fontSize = 9.sp)
    }
}

@Composable
private fun ModeDock(vm: StudioViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(58.dp)
            .background(StudioColors.Base)
            .border(1.dp, StudioColors.BorderSoft)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        modeItems.forEach { item ->
            val active = vm.mode == item.mode
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) StudioColors.Accent.copy(alpha = 0.11f) else Color.Transparent)
                    .clickable { vm.changeMode(item.mode) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    item.icon,
                    item.label,
                    tint = if (active) StudioColors.Accent else StudioColors.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) StudioColors.Accent else StudioColors.TextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}
