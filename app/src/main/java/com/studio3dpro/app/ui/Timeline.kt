package com.studio3dpro.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studio3dpro.app.StudioViewModel
import com.studio3dpro.engine.anim.AnimationClip
import com.studio3dpro.engine.anim.ChannelType
import com.studio3dpro.engine.math.Quat
import com.studio3dpro.engine.math.Vec3
import kotlin.math.abs
import kotlin.math.roundToInt

private val labelWidth = 104.dp
private val rowHeight = 24.dp

@Composable
fun TimelinePanel(vm: StudioViewModel, compact: Boolean, modifier: Modifier = Modifier, onClose: () -> Unit) {
    val clip = vm.currentClip()
    Column(modifier.fillMaxSize().background(StudioColors.EditorBg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(StudioColors.Surface)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudioIconButton(Icons.Rounded.SkipPrevious, "Previous key", size = 30.dp, onClick = {
                val times = clip?.allKeyTimes().orEmpty()
                vm.seekTo(times.lastOrNull { it < vm.time - 0.001f } ?: 0f)
            })
            StudioIconButton(
                if (vm.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                "Play",
                size = 32.dp,
                active = vm.playing,
                tint = StudioColors.Mint,
                onClick = { vm.togglePlay() }
            )
            StudioIconButton(Icons.Rounded.SkipNext, "Next key", size = 30.dp, onClick = {
                val times = clip?.allKeyTimes().orEmpty()
                vm.seekTo(times.firstOrNull { it > vm.time + 0.001f } ?: (clip?.duration ?: 0f))
            })
            Text(
                String.format(java.util.Locale.US, "%05.2f", vm.time),
                style = MaterialTheme.typography.labelLarge,
                color = StudioColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            if (!compact) {
                Text(
                    "/ ${String.format(java.util.Locale.US, "%.2f", clip?.duration ?: 0f)} s",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioColors.TextMuted
                )
                Spacer(Modifier.width(12.dp))
            }
            if (clip != null && vm.scene.clips.size > 1 && !compact) {
                Box(Modifier.width(154.dp)) {
                    StudioDropdown(clip.name, vm.scene.clips.map { it.name }) { index -> vm.selectClip(index) }
                }
            }
            Spacer(Modifier.weight(1f))
            if (!compact) {
                Text(
                    "${clip?.keyCount() ?: 0} keys",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioColors.TextMuted,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Text(
                "KEY",
                style = MaterialTheme.typography.labelSmall,
                color = StudioColors.Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(StudioColors.Accent.copy(alpha = 0.12f))
                    .clickable { vm.insertKeyframe() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
            Spacer(Modifier.width(4.dp))
            StudioIconButton(
                Icons.Rounded.ShowChart,
                "Graph editor",
                size = 30.dp,
                active = vm.showGraphEditor,
                onClick = { vm.toggleGraphEditor() }
            )
            StudioIconButton(Icons.Rounded.Close, "Hide timeline", size = 30.dp, onClick = onClose)
        }
        Hairline()
        if (clip == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No clip · create one in the Animation inspector", style = MaterialTheme.typography.bodySmall, color = StudioColors.TextMuted)
            }
        } else if (vm.showGraphEditor) {
            GraphEditor(vm, clip)
        } else {
            TrackTimeline(vm, clip, compact)
        }
    }
}

@Composable
private fun TrackTimeline(vm: StudioViewModel, clip: AnimationClip, compact: Boolean) {
    Column(Modifier.fillMaxSize()) {
        TimelineRuler(vm, clip)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SummaryRow(vm, clip)
            val skeleton = vm.skeleton
            val boneRows = skeleton?.bones?.indices?.filter { index ->
                clip.tracks.any { it.bone == index }
            }.orEmpty()
            if (boneRows.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(54.dp), contentAlignment = Alignment.Center) {
                    Text("No keyed bones yet · pose a bone and press KEY", style = MaterialTheme.typography.bodySmall, color = StudioColors.TextMuted)
                }
            } else {
                boneRows.forEach { bone -> BoneTrackRow(vm, clip, bone) }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun TimelineRuler(vm: StudioViewModel, clip: AnimationClip) {
    Row(Modifier.fillMaxWidth().height(24.dp).background(StudioColors.EditorRuler)) {
        Box(Modifier.width(labelWidth).fillMaxSize().padding(start = 8.dp), contentAlignment = Alignment.CenterStart) {
            Text("TRACKS", style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted, fontSize = 9.5.sp)
        }
        Box(Modifier.weight(1f).fillMaxSize()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(vm, clip.duration) {
                        detectTapGestures { offset ->
                            vm.seekTo((offset.x / size.width * clip.duration).coerceIn(0f, clip.duration))
                        }
                    }
                    .pointerInput(vm, clip.duration) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            vm.seekTo((change.position.x / size.width * clip.duration).coerceIn(0f, clip.duration))
                        }
                    }
            ) {
                val step = niceTimeStep(clip.duration)
                var t = 0f
                while (t <= clip.duration + 0.001f) {
                    val x = (t / clip.duration.coerceAtLeast(0.001f)) * size.width
                    drawLine(StudioColors.Border, Offset(x, size.height * 0.45f), Offset(x, size.height), 1f)
                    drawContext.canvas.nativeCanvas.drawText(
                        if (t < 10f) String.format(java.util.Locale.US, "%.1f", t)
                        else String.format(java.util.Locale.US, "%.0f", t),
                        x + 3f,
                        11.dp.toPx(),
                        android.graphics.Paint().apply {
                            isAntiAlias = true
                            textSize = 8.dp.toPx()
                            color = StudioColors.TextMuted.toArgb()
                        }
                    )
                    t += step
                }
                val px = (vm.time / clip.duration.coerceAtLeast(0.001f)) * size.width
                drawLine(StudioColors.Amber, Offset(px, 0f), Offset(px, size.height), 1.6f)
                val path = Path().apply {
                    moveTo(px - 4.dp.toPx(), 0f)
                    lineTo(px + 4.dp.toPx(), 0f)
                    lineTo(px, 6.dp.toPx())
                    close()
                }
                drawPath(path, StudioColors.Amber)
            }
        }
    }
}

@Composable
private fun SummaryRow(vm: StudioViewModel, clip: AnimationClip) {
    val times = clip.allKeyTimes()
    Row(
        Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .background(StudioColors.EditorRow),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.width(labelWidth).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(4.dp).clip(CircleShape).background(StudioColors.Accent))
            Spacer(Modifier.width(6.dp))
            Text("All keys", style = MaterialTheme.typography.labelSmall, color = StudioColors.TextSecondary)
        }
        Box(Modifier.weight(1f).fillMaxSize()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(vm, clip.duration, times) {
                        detectTapGestures { offset ->
                            val t = (offset.x / size.width * clip.duration).coerceIn(0f, clip.duration)
                            vm.seekTo(times.minByOrNull { abs(it - t) } ?: t)
                        }
                    }
            ) {
                times.forEach { time ->
                    drawDiamond(
                        Offset((time / clip.duration.coerceAtLeast(0.001f)) * size.width, size.height / 2f),
                        3.2.dp.toPx(),
                        StudioColors.Accent.copy(alpha = 0.75f)
                    )
                }
                drawPlayhead(vm, clip)
            }
        }
    }
}

@Composable
private fun BoneTrackRow(vm: StudioViewModel, clip: AnimationClip, bone: Int) {
    val selected = vm.selection.bone == bone
    val name = vm.skeleton?.bones?.getOrNull(bone)?.name ?: "Bone $bone"
    val times = clip.tracks.filter { it.bone == bone }.flatMap { it.keys.map { k -> k.time } }.distinct().sorted()
    Row(
        Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .background(if (selected) StudioColors.Mint.copy(alpha = 0.06f) else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .width(labelWidth)
                .fillMaxSize()
                .clickable { vm.selectBone(bone) }
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                name,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) StudioColors.Mint else StudioColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(Modifier.weight(1f).fillMaxSize()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(vm, bone, clip.duration) {
                        var dragging = false
                        var currentFrom = 0f
                        detectDragGestures(
                            onDragStart = { offset ->
                                val current = vm.currentClip() ?: return@detectDragGestures
                                val viewTimes = current.tracks.filter { it.bone == bone }
                                    .flatMap { it.keys.map { k -> k.time } }.distinct()
                                val nearest = viewTimes.minByOrNull {
                                    abs((it / clip.duration.coerceAtLeast(0.001f)) * size.width - offset.x)
                                }
                                if (nearest != null &&
                                    abs((nearest / clip.duration.coerceAtLeast(0.001f)) * size.width - offset.x) < 16.dp.toPx()
                                ) {
                                    dragging = true
                                    currentFrom = nearest
                                    vm.seekTo(nearest)
                                    vm.selectBone(bone)
                                }
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                if (dragging) {
                                    val dt = drag.x / size.width * clip.duration
                                    val target = (currentFrom + dt).coerceIn(0f, clip.duration)
                                    vm.moveKeyTime(bone, currentFrom, target)
                                    currentFrom = target
                                    vm.seekTo(target)
                                }
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false }
                        )
                    }
                    .pointerInput(vm, bone, clip.duration) {
                        detectTapGestures { offset ->
                            val current = vm.currentClip()
                            val viewTimes = current?.tracks?.filter { it.bone == bone }
                                ?.flatMap { it.keys.map { k -> k.time } }?.distinct().orEmpty()
                            val nearest = viewTimes.minByOrNull {
                                abs((it / clip.duration.coerceAtLeast(0.001f)) * size.width - offset.x)
                            }
                            val t = (offset.x / size.width * clip.duration).coerceIn(0f, clip.duration)
                            vm.selectBone(bone)
                            vm.seekTo(
                                if (nearest != null && abs((nearest / clip.duration.coerceAtLeast(0.001f)) * size.width - offset.x) < 14.dp.toPx())
                                    nearest else t
                            )
                        }
                    }
            ) {
                drawLine(
                    StudioColors.BorderSoft,
                    Offset(0f, size.height / 2f),
                    Offset(size.width, size.height / 2f),
                    1f
                )
                times.forEach { time ->
                    drawDiamond(
                        Offset((time / clip.duration.coerceAtLeast(0.001f)) * size.width, size.height / 2f),
                        3.5.dp.toPx(),
                        if (selected) StudioColors.Mint else StudioColors.Accent.copy(alpha = 0.9f)
                    )
                }
                drawPlayhead(vm, clip)
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(StudioColors.EditorLine))
}

@Composable
private fun GraphEditor(vm: StudioViewModel, clip: AnimationClip) {
    val bone = vm.selection.bone
    val skeleton = vm.skeleton
    val boneName = skeleton?.bones?.getOrNull(bone)?.name
    val track = clip.tracks.firstOrNull { it.bone == bone && it.type == ChannelType.ROTATION }
    var showX by remember { mutableStateOf(true) }
    var showY by remember { mutableStateOf(true) }
    var showZ by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(30.dp).background(StudioColors.EditorRuler).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (boneName != null) "$boneName · rotation (deg)" else "Select a bone",
                style = MaterialTheme.typography.labelSmall,
                color = StudioColors.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            GraphToggle("X", StudioColors.AxisX, showX) { showX = !showX }
            GraphToggle("Y", StudioColors.AxisY, showY) { showY = !showY }
            GraphToggle("Z", StudioColors.AxisZ, showZ) { showZ = !showZ }
        }
        Box(Modifier.fillMaxSize()) {
            if (track == null || track.keys.isEmpty()) {
                EmptyState(
                    icon =                        Icons.Rounded.ShowChart,
                        title = "No rotation curves",
                    body = if (bone < 0) "Select a bone in the rig or timeline, then keyframe a rotation."
                    else "Keyframe a rotation on this bone to edit its curves."
                )
            } else {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .background(StudioColors.EditorBg)
                        .pointerInput(vm, bone, clip.duration, track.keys.size) {
                            var dragKeyTime = -1f
                            var dragComponent = 0
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val current = vm.currentClip()
                                    val currentTrack = current?.tracks?.firstOrNull { it.bone == bone && it.type == ChannelType.ROTATION }
                                    if (currentTrack != null && size.width > 0 && size.height > 0) {
                                        val key = currentTrack.keys.minByOrNull { k ->
                                            val x = (k.time / clip.duration.coerceAtLeast(0.001f)) * size.width.toFloat()
                                            val e = Quat(k.value.x, k.value.y, k.value.z, k.value.w).toEulerDegrees()
                                            val ys = listOf(e.x, e.y, e.z)
                                            var best = Float.MAX_VALUE
                                            for (i in 0..2) {
                                                val y = angleToY(ys[i], size.height.toFloat())
                                                best = minOf(best, abs(x - offset.x) + abs(y - offset.y) * 0.7f)
                                            }
                                            best
                                        }
                                        if (key != null) {
                                            val x = (key.time / clip.duration.coerceAtLeast(0.001f)) * size.width.toFloat()
                                            if (abs(x - offset.x) < 20.dp.toPx()) {
                                                dragKeyTime = key.time
                                                val e = Quat(key.value.x, key.value.y, key.value.z, key.value.w).toEulerDegrees()
                                                val ys = listOf(e.x, e.y, e.z)
                                                var bestDist = Float.MAX_VALUE
                                                for (i in 0..2) {
                                                    val y = angleToY(ys[i], size.height.toFloat())
                                                    val dist = abs(y - offset.y)
                                                    if (dist < bestDist) { bestDist = dist; dragComponent = i }
                                                }
                                                vm.seekTo(key.time)
                                                vm.selectBone(bone)
                                            }
                                        }
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    if (dragKeyTime >= 0f) {
                                        val current = vm.currentClip()
                                        val currentTrack = current?.tracks?.firstOrNull { it.bone == bone && it.type == ChannelType.ROTATION }
                                        val key = currentTrack?.keys?.minByOrNull { abs(it.time - dragKeyTime) }
                                        if (key != null && abs(key.time - dragKeyTime) < 0.03f) {
                                            val e = Quat(key.value.x, key.value.y, key.value.z, key.value.w).toEulerDegrees()
                                            val angle = yToAngle(change.position.y, size.height.toFloat())
                                            val next = when (dragComponent) {
                                                0 -> Vec3(angle, e.y, e.z)
                                                1 -> Vec3(e.x, angle, e.z)
                                                else -> Vec3(e.x, e.y, angle)
                                            }
                                            vm.updateRotationKeyEuler(bone, key.time, next)
                                            vm.seekTo(key.time)
                                        }
                                    }
                                },
                                onDragEnd = { dragKeyTime = -1f },
                                onDragCancel = { dragKeyTime = -1f }
                            )
                        }
                        .pointerInput(vm, clip.duration) {
                            detectTapGestures { offset ->
                                vm.seekTo((offset.x / size.width * clip.duration).coerceIn(0f, clip.duration))
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val labelPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        textSize = 8.dp.toPx()
                        color = StudioColors.TextMuted.toArgb()
                    }
                    // Horizontal value grid.
                    for (angle in -180..180 step 45) {
                        val y = angleToY(angle.toFloat(), h)
                        drawLine(
                            if (angle == 0) StudioColors.Border else StudioColors.BorderSoft.copy(alpha = 0.55f),
                            Offset(0f, y), Offset(w, y), 1f
                        )
                        drawContext.canvas.nativeCanvas.drawText("$angle°", 4.dp.toPx(), y - 2.dp.toPx(), labelPaint)
                    }
                    // Time grid.
                    val step = niceTimeStep(clip.duration)
                    var t = 0f
                    while (t <= clip.duration + 0.001f) {
                        val x = (t / clip.duration.coerceAtLeast(0.001f)) * w
                        drawLine(StudioColors.BorderSoft.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, h), 1f)
                        t += step
                    }
                    val components = listOf(
                        Triple(0, showX, StudioColors.AxisX),
                        Triple(1, showY, StudioColors.AxisY),
                        Triple(2, showZ, StudioColors.AxisZ)
                    )
                    for ((component, visible, color) in components) {
                        if (!visible) continue
                        val path = Path()
                        val samples = 120
                        for (i in 0..samples) {
                            val time = clip.duration * i / samples
                            val v = track.sample(time)
                            val e = Quat(v.x, v.y, v.z, v.w).toEulerDegrees()
                            val angle = when (component) { 0 -> e.x; 1 -> e.y; else -> e.z }
                            val x = (time / clip.duration.coerceAtLeast(0.001f)) * w
                            val y = angleToY(angle, h)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(1.8.dp.toPx()))
                    }
                    for (key in track.keys) {
                        val e = Quat(key.value.x, key.value.y, key.value.z, key.value.w).toEulerDegrees()
                        val x = (key.time / clip.duration.coerceAtLeast(0.001f)) * w
                        val ys = listOf(e.x, e.y, e.z)
                        for ((component, visible, color) in components) {
                            if (!visible) continue
                            drawCircle(color, 2.6.dp.toPx(), Offset(x, angleToY(ys[component], h)))
                        }
                    }
                    val px = (vm.time / clip.duration.coerceAtLeast(0.001f)) * w
                    drawLine(StudioColors.Amber, Offset(px, 0f), Offset(px, h), 1.4f)
                }
            }
        }
    }
}

@Composable
private fun GraphToggle(label: String, color: Color, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 3.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (active) color.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) color else StudioColors.TextMuted)
    }
}

private fun DrawScope.drawPlayhead(vm: StudioViewModel, clip: AnimationClip) {
    val px = (vm.time / clip.duration.coerceAtLeast(0.001f)) * size.width
    drawLine(StudioColors.Amber.copy(alpha = 0.7f), Offset(px, 0f), Offset(px, size.height), 1.2f)
}

private fun DrawScope.drawDiamond(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + radius, center.y)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - radius, center.y)
        close()
    }
    drawPath(path, color)
}

private fun angleToY(angle: Float, height: Float): Float {
    val t = ((angle.coerceIn(-180f, 180f) + 180f) / 360f)
    return height - t * height
}

private fun yToAngle(y: Float, height: Float): Float {
    val t = 1f - (y / height.coerceAtLeast(1f))
    return (t * 360f - 180f).coerceIn(-180f, 180f)
}

private fun niceTimeStep(duration: Float): Float = when {
    duration <= 1.5f -> 0.2f
    duration <= 3f -> 0.5f
    duration <= 6f -> 1f
    duration <= 12f -> 2f
    else -> (duration / 8f).roundToInt().coerceAtLeast(1).toFloat()
}
