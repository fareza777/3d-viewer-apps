package com.studio3dpro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studio3dpro.app.StudioViewModel
import com.studio3dpro.app.ToolMode
import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.rig.ConstraintType
import com.studio3dpro.engine.scene.Skeleton

@Composable
fun RigInspector(vm: StudioViewModel, onImport: () -> Unit) {
    val poseRev = vm.poseRevision
    var showConstraintEditor by remember { mutableStateOf(false) }
    val skeleton = vm.skeleton
    val obj = vm.selectedObject
    val scroll = rememberScrollState()
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (skeleton == null) {
            StudioPanel(padding = 14.dp) {
                SectionHeader("Rigging", caption = "No skeleton in this scene", trailing = { TinyBadge("EMPTY", StudioColors.Amber) })
                Spacer(Modifier.height(6.dp))
                EmptyState(
                    icon = Icons.Rounded.AccessibilityNew,
                    title = "Auto-rig a humanoid",
                    body = "Select a single character mesh. A 24-bone Unity/Unreal-compatible humanoid skeleton is fitted to its bounds and skinned automatically.",
                    action = {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            StudioPrimaryButton(
                                "Auto-rig selected mesh",
                                Modifier.fillMaxWidth(),
                                enabled = obj?.mesh != null
                            ) { vm.autoRigSelected() }
                            StudioGhostButton("Import rigged GLB / BVH", Modifier.fillMaxWidth(), icon = Icons.Rounded.FolderOpen, onClick = onImport)
                        }
                    }
                )
            }
            StudioPanel(padding = 12.dp) {
                SectionHeader("Workflow")
                Spacer(Modifier.height(7.dp))
                Text(
                    "A rigged character opens with its skeleton and skin. If you import the CesiumMan or Fox sample, select Model mode to move the character, then Rigging to pose bones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioColors.TextMuted
                )
            }
            return@Column
        }

        val boneIndex = vm.selection.bone
        val bone = skeleton.bones.getOrNull(boneIndex)
        StudioPanel(padding = 12.dp) {
            SectionHeader(
                "Skeleton",
                caption = "${skeleton.size} bones · ${if (obj?.mesh?.skin != null) "skinned" else "unbound mesh"}",
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        StudioIconButton(Icons.Rounded.Add, "Add bone", size = 30.dp) { vm.addBone() }
                        StudioIconButton(Icons.Rounded.Delete, "Delete bone", size = 30.dp, tint = StudioColors.Danger) {
                            vm.deleteBone(vm.selection.bone)
                        }
                    }
                }
            )
            Spacer(Modifier.height(7.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 190.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                boneTree(skeleton).forEach { (index, depth) ->
                    val active = index == boneIndex
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) StudioColors.Mint.copy(alpha = 0.11f) else Color.Transparent)
                            .clickable { vm.selectBone(index) }
                            .padding(start = (7 + depth * 11).dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(if (active) StudioColors.Mint else StudioColors.TextMuted.copy(alpha = 0.45f))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            skeleton.bones[index].name,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (active) StudioColors.TextPrimary else StudioColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${(skeleton.bones[index].length * 100).toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioColors.TextMuted,
                            fontSize = 9.5.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                StudioGhostButton("Bind skin", Modifier.weight(1f), icon = Icons.Rounded.Link, accent = StudioColors.Mint) { vm.bindWeights() }
                StudioGhostButton("Add bone", Modifier.weight(1f), icon = Icons.Rounded.Add) { vm.addBone() }
            }
        }

        if (bone != null) {
            StudioPanel(padding = 12.dp) {
                SectionHeader(
                    "Bone",
                    caption = "Rest transforms · rebuilds skin matrices",
                    trailing = { TinyBadge(boneIndex.toString(), StudioColors.Mint) }
                )
                Spacer(Modifier.height(8.dp))
                StudioTextField(bone.name, { vm.renameBone(boneIndex, it) }, placeholder = "Bone name")
                Spacer(Modifier.height(8.dp))
                Text("LOCAL HEAD", style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted)
                Spacer(Modifier.height(4.dp))
                VectorField(
                    bone.headLocal,
                    { vm.setBoneRestHead(boneIndex, it) }
                )
                Spacer(Modifier.height(8.dp))
                SliderField(
                    "Length", bone.length, 0.01f..1.5f,
                    valueLabel = "%.3f".format(bone.length),
                    onValueChange = { vm.setBoneLength(boneIndex, it) }
                )
                Text("REST ROLL", style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(4.dp))
                VectorField(
                    vm.boneRestEuler(boneIndex),
                    { vm.setBoneRestEuler(boneIndex, it) },
                    suffix = "°"
                )
            }

            StudioPanel(padding = 12.dp) {
                SectionHeader(
                    "Pose",
                    caption = "Rotate the selected bone · FK",
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            StudioIconButton(Icons.Rounded.Refresh, "Reset pose", size = 30.dp) { vm.resetPose() }
                            StudioIconButton(Icons.Rounded.Tune, "Rotate tool", size = 30.dp, active = vm.tool == ToolMode.ROTATE) { vm.changeTool(ToolMode.ROTATE) }
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                val euler = vm.bonePoseEuler()
                VectorField(
                    euler,
                    { vm.setBoneEuler(it.x, it.y, it.z) },
                    suffix = "°"
                )
                if (poseRev >= 0) Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    StudioGhostButton("Key pose", Modifier.weight(1f), icon = Icons.Rounded.Save, accent = StudioColors.Accent) { vm.insertKeyframe() }
                    StudioGhostButton("Capture", Modifier.weight(1f), accent = StudioColors.Mint) { vm.capturePose("") }
                }
            }

            StudioPanel(padding = 12.dp) {
                SectionHeader(
                    "IK Solver",
                    caption = "CCD target dragging",
                    trailing = { TinyBadge("REAL-TIME", StudioColors.Mint) }
                )
                Spacer(Modifier.height(8.dp))
                SegmentedOptions(listOf("FK", "IK"), if (vm.tool == ToolMode.IK) 1 else 0) { index ->
                    vm.changeTool(if (index == 1) ToolMode.IK else ToolMode.ROTATE)
                }
                Spacer(Modifier.height(8.dp))
                Text("CHAIN LENGTH", style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted)
                Spacer(Modifier.height(4.dp))
                SegmentedOptions(listOf("1", "2", "3", "4"), (vm.ikChainLength - 1).coerceIn(0, 3)) { index ->
                    vm.ikChainLength = index + 1
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    "Drag the mint target in the viewport. The solver respects the selected bone hierarchy and clamps joints to 4 influences.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioColors.TextMuted
                )
                Spacer(Modifier.height(8.dp))
                StudioGhostButton("Enable IK tool", Modifier.fillMaxWidth(), accent = StudioColors.Mint) { vm.changeTool(ToolMode.IK) }
            }

            StudioPanel(padding = 12.dp) {
                SectionHeader(
                    "Weight Painting",
                    caption = if (vm.weightPaintActive) "Painting bone ${bone.name}" else "Heat-map skin influence",
                    trailing = {
                        TinyBadge(if (vm.weightPaintActive) "ON" else "OFF", if (vm.weightPaintActive) StudioColors.Mint else StudioColors.TextMuted)
                    }
                )
                Spacer(Modifier.height(8.dp))
                StudioGhostButton(
                    if (vm.weightPaintActive) "Stop painting" else "Paint weights",
                    Modifier.fillMaxWidth(),
                    accent = if (vm.weightPaintActive) StudioColors.Danger else StudioColors.Mint
                ) { vm.toggleWeightPaint() }
                if (vm.weightPaintActive) {
                    Spacer(Modifier.height(7.dp))
                    SliderField("Brush radius", vm.weightRadius, 0.02f..0.5f, valueLabel = "%.2f m".format(vm.weightRadius)) { vm.weightRadius = it }
                    SliderField("Strength", vm.weightStrength, 0.05f..1f, valueLabel = "${(vm.weightStrength * 100).toInt()}%") { vm.weightStrength = it }
                    WeightLegend()
                }
                Spacer(Modifier.height(7.dp))
                DisabledHint("Tap-drag over the selected mesh. Weights are normalized to 4 influences per vertex.")
            }
        }

        StudioPanel(padding = 12.dp) {
            SectionHeader(
                "Constraints",
                caption = "${vm.scene.constraints.size} active",
                trailing = {
                    StudioIconButton(Icons.Rounded.Add, "Add constraint", size = 30.dp) { showConstraintEditor = true }
                }
            )
            Spacer(Modifier.height(6.dp))
            if (vm.scene.constraints.isEmpty()) {
                Text("No constraints. Add a rotation limit or copy rotation between bones.", style = MaterialTheme.typography.bodySmall, color = StudioColors.TextMuted)
            } else {
                vm.scene.constraints.forEachIndexed { index, constraint ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(StudioColors.Amber))
                        Spacer(Modifier.width(7.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                constraint.name.ifBlank { constraint.type.name.lowercase() },
                                style = MaterialTheme.typography.bodySmall,
                                color = StudioColors.TextPrimary
                            )
                            Text(
                                "${constraint.type.name.replace('_', ' ').lowercase()} · ${skeleton.bones.getOrNull(constraint.bone)?.name ?: "?"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = StudioColors.TextMuted
                            )
                        }
                        Icon(
                            Icons.Rounded.Close, "Remove",
                            tint = StudioColors.TextMuted,
                            modifier = Modifier.size(26.dp).clip(CircleShape).clickable { vm.removeConstraint(index) }.padding(7.dp)
                        )
                    }
                }
            }
        }

        StudioPanel(padding = 12.dp) {
            var poseName by remember { mutableStateOf("") }
            var applyWeight by remember { mutableFloatStateOf(1f) }
            SectionHeader(
                "Pose Library",
                caption = "${vm.scene.poseLibrary.size} poses",
                trailing = { TinyBadge("BLEND", StudioColors.Accent) }
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f)) {
                    StudioTextField(poseName, { poseName = it }, placeholder = "Pose name")
                }
                StudioGhostButton("Capture", icon = Icons.Rounded.Save, accent = StudioColors.Mint) {
                    vm.capturePose(poseName)
                    poseName = ""
                }
            }
            if (vm.scene.poseLibrary.isNotEmpty()) {
                Spacer(Modifier.height(7.dp))
                SliderField("Blend weight", applyWeight, 0f..1f, valueLabel = "${(applyWeight * 100).toInt()}%") { applyWeight = it }
                vm.scene.poseLibrary.forEachIndexed { index, preset ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                                .background(StudioColors.Accent.copy(alpha = 0.12f))
                                .border(1.dp, StudioColors.Accent.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.AccessibilityNew, null, tint = StudioColors.Accent, modifier = Modifier.size(13.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(preset.name, style = MaterialTheme.typography.bodySmall, color = StudioColors.TextSecondary, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(
                            "APPLY",
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioColors.Mint,
                            modifier = Modifier.clip(RoundedCornerShape(5.dp)).clickable { vm.applyPosePreset(index, applyWeight) }.padding(6.dp)
                        )
                        Icon(
                            Icons.Rounded.Delete, "Delete pose",
                            tint = StudioColors.TextMuted,
                            modifier = Modifier.size(24.dp).clip(CircleShape).clickable { vm.deletePosePreset(index) }.padding(6.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }

    if (showConstraintEditor && skeleton != null) {
        ConstraintDialog(
            vm = vm,
            skeleton = skeleton,
            onDismiss = { showConstraintEditor = false }
        )
    }
}

@Composable
private fun ConstraintDialog(vm: StudioViewModel, skeleton: Skeleton, onDismiss: () -> Unit) {
    var min by remember { mutableStateOf(Vec3(-45f, -90f, -45f)) }
    var max by remember { mutableStateOf(Vec3(45f, 90f, 45f)) }
    var source by remember { mutableStateOf(-1) }
    val candidates = skeleton.bones.indices.filter { it != vm.selection.bone }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(StudioColors.SurfaceRaised)
                .border(1.dp, StudioColors.Border, RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Text("ADD CONSTRAINT", style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted)
            Spacer(Modifier.height(4.dp))
            Text("Bone limits", style = MaterialTheme.typography.titleLarge, color = StudioColors.TextPrimary)
            Spacer(Modifier.height(14.dp))
            Text("LIMIT ROTATION (DEGREES)", style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted)
            Spacer(Modifier.height(5.dp))
            Text("Min", style = MaterialTheme.typography.bodySmall, color = StudioColors.TextSecondary)
            VectorField(min, { min = it }, suffix = "°")
            Spacer(Modifier.height(6.dp))
            Text("Max", style = MaterialTheme.typography.bodySmall, color = StudioColors.TextSecondary)
            VectorField(max, { max = it }, suffix = "°")
            Spacer(Modifier.height(9.dp))
            StudioGhostButton("Add rotation limit", Modifier.fillMaxWidth(), accent = StudioColors.Amber) {
                vm.addLimitConstraint(min, max)
                onDismiss()
            }
            Spacer(Modifier.height(14.dp))
            Hairline()
            Spacer(Modifier.height(12.dp))
            Text("COPY ROTATION FROM", style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted)
            Spacer(Modifier.height(5.dp))
            StudioDropdown(
                skeleton.bones.getOrNull(source)?.name ?: "Choose source bone",
                candidates.map { skeleton.bones[it].name }
            ) { index -> source = candidates.getOrElse(index) { -1 } }
            Spacer(Modifier.height(9.dp))
            StudioGhostButton("Add copy rotation", Modifier.fillMaxWidth(), enabled = source >= 0, accent = StudioColors.Mint) {
                vm.addCopyConstraint(source)
                onDismiss()
            }
            Spacer(Modifier.height(16.dp))
            StudioGhostButton("Cancel", Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}

@Composable
private fun WeightLegend() {
    Column(Modifier.padding(top = 8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(Color(0xFF0A2FE6), Color(0xFF19E68C), Color(0xFFFFCC33), Color(0xFFFF331F))
                    )
                )
        )
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0", style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted)
            Text("influence", style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted)
            Text("1", style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted)
        }
    }
}

private fun boneTree(skeleton: Skeleton): List<Pair<Int, Int>> {
    val out = ArrayList<Pair<Int, Int>>(skeleton.size)
    val visited = HashSet<Int>()
    fun walk(index: Int, depth: Int) {
        if (index !in skeleton.bones.indices || !visited.add(index)) return
        out.add(index to depth.coerceAtMost(6))
        skeleton.children(index).forEach { walk(it, depth + 1) }
    }
    skeleton.bones.indices.filter { skeleton.bones[it].parent == -1 }.forEach { walk(it, 0) }
    skeleton.bones.indices.forEach { if (it !in visited) walk(it, 0) }
    return out
}

