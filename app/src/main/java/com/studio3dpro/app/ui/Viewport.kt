package com.studio3dpro.app.ui

import android.opengl.GLSurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.BubbleChart
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.GridOff
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.studio3dpro.app.CameraView
import com.studio3dpro.app.EditorMode
import com.studio3dpro.app.ElementMode
import com.studio3dpro.app.SculptBrush
import com.studio3dpro.app.ToolMode
import com.studio3dpro.app.StudioPreferences
import com.studio3dpro.app.StudioViewModel
import com.studio3dpro.app.render.SceneRenderer
import com.studio3dpro.engine.math.Vec3
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * The viewport is a real GLES 3 surface with a hand-built PBR renderer.
 * Chrome floats above it and is deliberately sparse: the model is the hero.
 */
@Composable
fun StudioViewport(
    vm: StudioViewModel,
    modifier: Modifier = Modifier,
    compact: Boolean,
    propertiesVisible: Boolean,
    onOpenInspector: () -> Unit,
    onAddObject: () -> Unit,
    onImport: () -> Unit
) {
    val context = LocalContext.current
    val renderer = remember { SceneRenderer(vm.runtime) }
    // The GL surface keeps its own palettes, so push the theme in explicitly.
    LaunchedEffect(StudioColors.isLight) { renderer.setLightCanvas(StudioColors.isLight) }
    val glView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            setZOrderOnTop(false)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, glView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glView.onResume()
                Lifecycle.Event.ON_PAUSE -> glView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        glView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glView.onPause()
        }
    }

    var fps by remember { mutableIntStateOf(0) }
    var triangles by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(600)
            fps = vm.runtime.fps
            triangles = vm.runtime.triangleCount
        }
    }

    BoxWithConstraints(modifier.background(StudioColors.Ink)) {
        val wideLayout = maxWidth > 640.dp
        AndroidView(
            factory = { glView },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(vm) { viewportGestures(vm) }
        )

        // Top gradient scrim keeps HUD legible over bright metal.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Color(0x9906070A),
                        0.24f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1f to Color(0xAA06070A)
                    )
                )
        )

        Row(
            Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                SelectionHud(vm, compact)
                if (vm.mode == EditorMode.EDIT) ElementModeBar(vm)
                if (!compact && StudioPreferences.showFps) StatsHud(vm)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    HudIcon(
                        icon = Icons.Rounded.CenterFocusStrong,
                        label = "Frame",
                        onClick = { vm.frameSelection() }
                    )
                    HudIcon(
                        icon = if (vm.showGrid) Icons.Rounded.GridOn else Icons.Rounded.GridOff,
                        label = "Grid",
                        active = vm.showGrid,
                        onClick = { vm.changeGridVisibility(!vm.showGrid) }
                    )
                    HudIcon(
                        icon = Icons.Rounded.AccessibilityNew,
                        label = "Skeleton",
                        active = vm.showSkeleton,
                        onClick = { vm.changeSkeletonVisibility(!vm.showSkeleton) }
                    )
                    HudIcon(
                        icon = Icons.Rounded.BlurOn,
                        label = "Wireframe",
                        active = vm.wireframe,
                        onClick = { vm.changeWireframe(!vm.wireframe) }
                    )
                }
                if (StudioPreferences.showFps) {
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(StudioColors.CanvasGlass)
                            .border(1.dp, StudioColors.CanvasBorder, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("$fps FPS", style = MaterialTheme.typography.labelSmall, color = StudioColors.CanvasMint)
                        Box(Modifier.padding(horizontal = 5.dp).size(2.dp).background(StudioColors.OnCanvasMuted))
                        Text("${formatCount(triangles)} TRIS", style = MaterialTheme.typography.labelSmall, color = StudioColors.OnCanvasMuted)
                    }
                }
                if (wideLayout) CameraPresets(vm)
            }
        }

        if (vm.scene.objects.isEmpty() && vm.mode != EditorMode.RIG && vm.mode != EditorMode.ANIMATE) {
            EmptyViewportState(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 28.dp, vertical = 36.dp),
                onAddObject = onAddObject,
                onImport = onImport
            )
        }

        ToolDock(
            vm = vm,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
            showInspector = propertiesVisible,
            onOpenInspector = onOpenInspector,
            onAddObject = onAddObject
        )

        Row(
            Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AxisGizmo(vm)
            if (!compact) {
                Column(Modifier.padding(bottom = 6.dp)) {
                    Text(
                        if (vm.mode == EditorMode.SCULPT) "Tap-drag to sculpt · 2 fingers navigate"
                        else "1 finger orbit · 2 fingers pan & zoom · double-tap frames",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioColors.OnCanvasMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionHud(vm: StudioViewModel, compact: Boolean) {
    val obj = vm.selectedObject
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(StudioColors.CanvasGlass)
            .border(1.dp, StudioColors.CanvasBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(StudioColors.CanvasAccent.copy(alpha = 0.16f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(vm.mode.short, style = MaterialTheme.typography.labelSmall, color = StudioColors.CanvasAccent)
            }
            Spacer(Modifier.width(7.dp))
            Text(
                if (vm.mode == EditorMode.RIG || vm.mode == EditorMode.ANIMATE) vm.selectedBoneName else obj?.name ?: "No selection",
                style = MaterialTheme.typography.titleSmall,
                color = StudioColors.OnCanvasPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(if (compact) 80.dp else 190.dp)
            )
        }
        if (obj != null) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (obj.mesh != null) "${formatCount(obj.mesh!!.vertexCount)} V · ${formatCount(obj.mesh!!.faceCount)} F" else "EMPTY",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioColors.OnCanvasMuted,
                    maxLines = 1
                )
                if (!compact && obj.mesh?.skin != null) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(3.dp).background(StudioColors.CanvasMint, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text("SKINNED", style = MaterialTheme.typography.labelSmall, color = StudioColors.CanvasMint)
                }
            }
        }
        if (vm.mode == EditorMode.SCULPT) {
            Spacer(Modifier.height(5.dp))
            Text(
                "${vm.sculptBrush.label.uppercase()} · ${String.format(java.util.Locale.US, "%.2f", vm.sculptRadius)} m",
                style = MaterialTheme.typography.labelSmall,
                color = StudioColors.CanvasMint,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ElementModeBar(vm: StudioViewModel) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(StudioColors.CanvasGlass)
            .border(1.dp, StudioColors.CanvasBorder, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        listOf(ElementMode.VERTEX, ElementMode.EDGE, ElementMode.FACE).forEach { mode ->
            val active = vm.elementMode == mode
            Box(
                Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (active) StudioColors.CanvasAccent.copy(alpha = 0.22f) else Color.Transparent)
                    .clickable { vm.changeElementMode(mode) }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(
                    mode.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) StudioColors.CanvasAccent else StudioColors.OnCanvasMuted
                )
            }
        }
    }
}

@Composable
private fun StatsHud(vm: StudioViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MiniStat("OBJECTS", vm.scene.objects.size.toString())
        MiniStat("BONES", (vm.scene.skeleton?.size ?: 0).toString())
        MiniStat("CLIPS", vm.scene.clips.size.toString())
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(StudioColors.CanvasGlassSoft)
            .border(1.dp, StudioColors.CanvasBorder, RoundedCornerShape(9.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleSmall, color = StudioColors.OnCanvasPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = StudioColors.OnCanvasMuted, fontSize = 9.5.sp)
    }
}

@Composable
private fun HudIcon(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(33.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) StudioColors.CanvasAccent.copy(alpha = 0.18f) else StudioColors.CanvasGlass)
            .border(
                1.dp,
                if (active) StudioColors.CanvasAccent.copy(alpha = 0.5f) else StudioColors.CanvasBorder,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = if (active) StudioColors.CanvasAccent else StudioColors.OnCanvasSecondary, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun CameraPresets(vm: StudioViewModel) {
    var selected by remember { mutableStateOf(CameraView.PERSPECTIVE) }
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color(0x990B0C10))
            .border(1.dp, StudioColors.BorderSoft, CircleShape)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        CameraView.entries.forEach { view ->
            val active = selected == view
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (active) StudioColors.SurfaceHigh else Color.Transparent)
                    .clickable {
                        selected = view
                        vm.setCameraView(view)
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    view.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) StudioColors.TextPrimary else StudioColors.TextMuted
                )
            }
        }
    }
}

@Composable
private fun ToolDock(
    vm: StudioViewModel,
    modifier: Modifier = Modifier,
    showInspector: Boolean,
    onOpenInspector: () -> Unit,
    onAddObject: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(StudioColors.CanvasGlass)
            .border(1.dp, StudioColors.CanvasBorder, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (vm.mode == EditorMode.SCULPT) {
            SculptBrush.entries.forEach { brush ->
                DockButton(brushIcon(brush), brush.label, vm.sculptBrush == brush) { vm.sculptBrush = brush }
            }
            DockDivider()
            DockButton(Icons.Rounded.Flip, "X symmetry", vm.symmetryX) { vm.symmetryX = !vm.symmetryX }
            if (showInspector) {
                DockDivider()
                DockButton(Icons.Rounded.Tune, "Brush settings", false, onClick = onOpenInspector)
            }
        } else {
            val tools = buildList {
                add(ToolMode.MOVE to Icons.Rounded.OpenWith)
                add(ToolMode.ROTATE to Icons.Rounded.RotateRight)
                add(ToolMode.SCALE to Icons.Rounded.AspectRatio)
                if (vm.mode == EditorMode.RIG || vm.mode == EditorMode.ANIMATE) add(ToolMode.IK to Icons.Rounded.Gesture)
            }
            if (vm.mode == EditorMode.OBJECT) {
                DockButton(Icons.Rounded.Add, "Add object", false) { onAddObject() }
                DockDivider()
            }
            tools.forEach { (tool, icon) ->
                DockButton(icon, tool.name.lowercase(), vm.tool == tool) { vm.changeTool(tool) }
            }
            if (vm.mode == EditorMode.OBJECT || vm.mode == EditorMode.EDIT) {
                DockDivider()
                DockButton(Icons.Rounded.ContentCopy, "Duplicate", false) { vm.duplicateSelectedObject() }
                DockButton(Icons.Rounded.Delete, "Delete", false, danger = true) {
                    if (vm.mode == EditorMode.EDIT) vm.deleteSelectedFaces() else vm.deleteSelectedObject()
                }
            }
            if (showInspector) {
                DockDivider()
                DockButton(Icons.Rounded.Tune, "Properties", false, onClick = onOpenInspector)
            }
        }
    }
}

private fun brushIcon(brush: SculptBrush): ImageVector = when (brush) {
    SculptBrush.DRAW -> Icons.Rounded.Brush
    SculptBrush.INFLATE -> Icons.Rounded.BubbleChart
    SculptBrush.SMOOTH -> Icons.Rounded.Waves
    SculptBrush.FLATTEN -> Icons.Rounded.Remove
    SculptBrush.PINCH -> Icons.Rounded.Compress
}

@Composable
private fun EmptyViewportState(
    modifier: Modifier = Modifier,
    onAddObject: () -> Unit,
    onImport: () -> Unit
) {
    Column(
        modifier
            .width(300.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(StudioColors.CanvasGlass)
            .border(1.dp, StudioColors.CanvasBorder, RoundedCornerShape(20.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EmptyCubeGlyph(62.dp)
        Spacer(Modifier.height(10.dp))
        Text(
            "Your scene is empty",
            style = MaterialTheme.typography.titleMedium,
            color = StudioColors.OnCanvasPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Add a primitive or import a GLB character to start modelling.",
            style = MaterialTheme.typography.bodySmall,
            color = StudioColors.OnCanvasSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CanvasAction("Add object", Icons.Rounded.Add, true, Modifier.weight(1f), onAddObject)
            CanvasAction("Import", Icons.Rounded.FolderOpen, false, Modifier.weight(1f), onImport)
        }
    }
}

@Composable
private fun CanvasAction(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (primary) StudioColors.CanvasAccent else Color(0x1AFFFFFF))
            .border(
                1.dp,
                if (primary) Color.Transparent else StudioColors.CanvasBorder,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            null,
            tint = if (primary) StudioColors.CanvasInk else StudioColors.OnCanvasPrimary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (primary) StudioColors.CanvasInk else StudioColors.OnCanvasPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun EmptyCubeGlyph(size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val top = Offset(w * 0.5f, h * 0.06f)
        val right = Offset(w * 0.94f, h * 0.28f)
        val left = Offset(w * 0.06f, h * 0.28f)
        val middle = Offset(w * 0.5f, h * 0.5f)
        val bottomRight = Offset(w * 0.94f, h * 0.72f)
        val bottomLeft = Offset(w * 0.06f, h * 0.72f)
        val bottom = Offset(w * 0.5f, h * 0.94f)
        drawPath(
            androidx.compose.ui.graphics.Path().apply {
                moveTo(top.x, top.y); lineTo(right.x, right.y); lineTo(middle.x, middle.y); lineTo(left.x, left.y); close()
            },
            StudioColors.CanvasAccent.copy(alpha = 0.2f)
        )
        drawPath(
            androidx.compose.ui.graphics.Path().apply {
                moveTo(left.x, left.y); lineTo(middle.x, middle.y); lineTo(bottom.x, bottom.y); lineTo(bottomLeft.x, bottomLeft.y); close()
            },
            Color(0x10FFFFFF)
        )
        drawPath(
            androidx.compose.ui.graphics.Path().apply {
                moveTo(middle.x, middle.y); lineTo(right.x, right.y); lineTo(bottomRight.x, bottomRight.y); lineTo(bottom.x, bottom.y); close()
            },
            Color(0x06FFFFFF)
        )
        val line = Color(0x88FFFFFF)
        listOf(
            top to right, right to bottomRight, bottomRight to bottom,
            bottom to bottomLeft, bottomLeft to left, left to top,
            left to middle, middle to right, middle to bottom
        ).forEach { (a, b) ->
            drawLine(line, a, b, strokeWidth = 1.4f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
        val gem = 6f
        drawPath(
            androidx.compose.ui.graphics.Path().apply {
                moveTo(middle.x, middle.y - gem); lineTo(middle.x + gem, middle.y)
                lineTo(middle.x, middle.y + gem); lineTo(middle.x - gem, middle.y); close()
            },
            StudioColors.CanvasMint
        )
    }
}

@Composable
private fun DockButton(icon: ImageVector, label: String, active: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) StudioColors.CanvasAccent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            label,
            tint = when {
                danger -> StudioColors.CanvasDanger.copy(alpha = 0.8f)
                active -> StudioColors.CanvasAccent
                else -> StudioColors.OnCanvasSecondary
            },
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun DockDivider() {
    Box(Modifier.padding(vertical = 3.dp).width(22.dp).height(1.dp).background(StudioColors.CanvasBorder))
}

@Composable
private fun AxisGizmo(vm: StudioViewModel) {
    var yawDeg by remember { mutableStateOf(vm.runtime.camera.yawDeg) }
    var pitchDeg by remember { mutableStateOf(vm.runtime.camera.pitchDeg) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(140)
            yawDeg = vm.runtime.camera.yawDeg
            pitchDeg = vm.runtime.camera.pitchDeg
        }
    }
    val yaw = Math.toRadians(yawDeg.toDouble())
    val pitch = Math.toRadians(pitchDeg.toDouble())
    Canvas(
        Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(StudioColors.CanvasGlassSoft)
            .border(1.dp, StudioColors.CanvasBorder, CircleShape)
            .pointerInput(vm) {
                detectTapGestures { vm.frameScene() }
            }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.34f
        val eye = Vec3(
            (sin(yaw) * cos(pitch)).toFloat(),
            sin(pitch).toFloat(),
            (cos(yaw) * cos(pitch)).toFloat()
        )
        val right = Vec3(cos(yaw).toFloat(), 0f, -sin(yaw).toFloat())
        val up = Vec3(
            (-sin(pitch) * cos(yaw)).toFloat(),
            cos(pitch).toFloat(),
            (-sin(pitch) * sin(yaw)).toFloat()
        )
        data class Axis(val v: Vec3, val color: Color, val label: String)
        val axes = listOf(
            Axis(Vec3.RIGHT, StudioColors.CanvasAxisX, "X"),
            Axis(Vec3.UP, StudioColors.CanvasAxisY, "Y"),
            Axis(Vec3(0f, 0f, 1f), StudioColors.CanvasAxisZ, "Z")
        )
        axes.sortedBy { it.v dot eye }.forEach { axis ->
            val dx = axis.v dot right
            val dy = -(axis.v dot up)
            val tip = center + Offset(dx * radius, dy * radius)
            val negTip = center - Offset(dx * radius, dy * radius)
            drawLine(axis.color.copy(alpha = 0.25f), center, negTip, strokeWidth = 1.2f)
            drawLine(axis.color, center, tip, strokeWidth = 3f)
            drawCircle(axis.color, radius = 3.2f, center = tip)
            drawContext.canvas.nativeCanvas.drawText(
                axis.label,
                tip.x + dx * 5f - 2.5f,
                tip.y + dy * 5f + 3f,
                android.graphics.Paint().apply {
                    isAntiAlias = true
                    textSize = 7.5.dp.toPx()
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    color = axis.color.toArgb()
                }
            )
        }
        drawCircle(StudioColors.OnCanvasSecondary, radius = 2.2f, center = center)
    }
}

private fun formatCount(value: Int): String = when {
    value >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", value / 1_000_000f)
    value >= 10_000 -> String.format(java.util.Locale.US, "%.0fK", value / 1_000f)
    value >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", value / 1_000f)
    else -> value.toString()
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.viewportGestures(vm: StudioViewModel) {
    var lastTapAt = 0L
    var lastTapPos = Offset.Zero
    val camera = vm.runtime.camera
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val downPos = down.position
        var manipulating = vm.beginViewportManipulation(downPos.x, downPos.y)
        var twoFinger = false
        var totalDrag = 0f
        var mayOrbit = !manipulating
        try {
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    if (manipulating) {
                        vm.endViewportManipulation()
                        manipulating = false
                    }
                    twoFinger = true
                    mayOrbit = false
                    val a = pressed[0]
                    val b = pressed[1]
                    val prevDist = (a.previousPosition - b.previousPosition).getDistance()
                    val curDist = (a.position - b.position).getDistance()
                    if (prevDist > 1f && curDist > 1f) {
                        camera.zoom((curDist / prevDist).coerceIn(0.65f, 1.45f))
                    }
                    val center = (a.position + b.position) / 2f
                    val prevCenter = (a.previousPosition + b.previousPosition) / 2f
                    val pan = center - prevCenter
                    camera.pan(pan.x, pan.y, vm.runtime.viewportWidth, vm.runtime.viewportHeight)
                    pressed.forEach { it.consume() }
                } else if (pressed.size == 1) {
                    val change = pressed[0]
                    val drag = change.position - change.previousPosition
                    totalDrag += drag.getDistance()
                    if (manipulating) {
                        vm.dragViewportManipulation(change.position.x, change.position.y)
                        change.consume()
                    } else if (mayOrbit && totalDrag > viewConfiguration.touchSlop) {
                        camera.orbit(drag.x, drag.y)
                        change.consume()
                    }
                }
                if (event.changes.none { it.pressed }) break
            }
        } finally {
            if (manipulating) vm.endViewportManipulation()
        }
        if (!twoFinger && totalDrag < viewConfiguration.touchSlop) {
            val now = down.uptimeMillis
            if (now - lastTapAt < 320L && (downPos - lastTapPos).getDistance() < 48.dp.toPx()) {
                vm.frameSelection()
                lastTapAt = 0L
            } else {
                vm.onViewportTap(downPos.x, downPos.y)
                lastTapAt = now
                lastTapPos = downPos
            }
        }
    }
}
