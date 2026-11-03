package com.studio3dpro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.studio3dpro.app.InspectorTab
import com.studio3dpro.app.StudioViewModel
import com.studio3dpro.engine.anim.AnimationLayer

@Composable
fun AnimationInspector(vm: StudioViewModel, onImport: () -> Unit, onOpenGraphEditor: () -> Unit) {
    val clip = vm.currentClip()
    val skeleton = vm.skeleton
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StudioPanel(padding = 12.dp) {
            SectionHeader(
                "Clips",
                caption = "${vm.scene.clips.size} animation${if (vm.scene.clips.size == 1) "" else "s"}",
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        StudioIconButton(Icons.Rounded.Add, "New clip", size = 30.dp) { vm.createClip() }
                        StudioIconButton(Icons.Rounded.ContentCopy, "Duplicate clip", size = 30.dp) { vm.duplicateClip() }
                        StudioIconButton(Icons.Rounded.Delete, "Delete clip", size = 30.dp, tint = StudioColors.Danger) { vm.deleteClip() }
                    }
                }
            )
            Spacer(Modifier.height(7.dp))
            if (vm.scene.clips.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.Movie,
                    title = "No animation clips",
                    body = "Create a clip, pose the rig, then key frames.",
                    action = { StudioPrimaryButton("Create clip", onClick = { vm.createClip() }) }
                )
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    vm.scene.clips.forEachIndexed { index, c ->
                        val active = index == vm.scene.activeClip
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) StudioColors.Accent.copy(alpha = 0.14f) else StudioColors.Base)
                                .border(
                                    1.dp,
                                    if (active) StudioColors.Accent.copy(alpha = 0.4f) else StudioColors.BorderSoft,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { vm.selectClip(index) }
                                .padding(horizontal = 9.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(5.dp).clip(CircleShape).background(if (active) StudioColors.Mint else StudioColors.TextMuted))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                c.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) StudioColors.TextPrimary else StudioColors.TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        if (clip != null) {
            StudioPanel(padding = 12.dp) {
                SectionHeader(
                    "Clip",
                    caption = "${clip.keyCount()} keys · ${"%.2f".format(clip.duration)} s",
                    trailing = {
                        StudioIconButton(
                            if (vm.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            "Play/pause",
                            active = vm.playing,
                            size = 30.dp
                        ) { vm.togglePlay() }
                    }
                )
                Spacer(Modifier.height(8.dp))
                StudioTextField(clip.name, { vm.renameClip(it) }, placeholder = "Clip name")
                Spacer(Modifier.height(7.dp))
                SliderField(
                    "Duration", clip.duration, 0.2f..20f,
                    valueLabel = "%.2f s".format(clip.duration),
                    onValueChange = { vm.setClipDuration(it) }
                )
                ToggleRow("Loop", clip.loop, { vm.setClipLoop(it) }, subtitle = "Cycles playback and exports as a loop")
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    StudioGhostButton("Start", Modifier.weight(1f)) { vm.seekTo(0f) }
                    StudioGhostButton("Previous", Modifier.weight(1f), icon = Icons.Rounded.SkipPrevious) {
                        val times = clip.allKeyTimes()
                        vm.seekTo(times.lastOrNull { it < vm.time } ?: 0f)
                    }
                    StudioGhostButton("Key", Modifier.weight(1f), icon = Icons.Rounded.Save, accent = StudioColors.Accent) { vm.insertKeyframe() }
                }
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    StudioGhostButton("Key all bones", Modifier.weight(1f)) { vm.insertKeyframe(allBones = true) }
                    StudioGhostButton("Delete key", Modifier.weight(1f), accent = StudioColors.Danger) { vm.deleteKeyAtCurrent() }
                    StudioGhostButton("Reset pose", Modifier.weight(1f), icon = Icons.Rounded.Refresh) { vm.resetPose() }
                }
                Spacer(Modifier.height(8.dp))
                SliderField(
                    "Playback speed", vm.speed, 0.1f..3f,
                    valueLabel = "%.2fx".format(vm.speed),
                    onValueChange = { vm.setPlaybackSpeed(it) }
                )
                StudioGhostButton("Open timeline & graph editor", Modifier.fillMaxWidth(), icon = Icons.Rounded.Timeline, accent = StudioColors.Mint) {
                    onOpenGraphEditor()
                }
            }

            StudioPanel(padding = 12.dp) {
                SectionHeader("Cycle Generator", caption = "Editable keyframes on the current humanoid", trailing = { TinyBadge("HUMANOID", StudioColors.Mint) })
                Spacer(Modifier.height(7.dp))
                Text(
                    "Procedural locomotion authored as a real 12-frame cycle: hips bob, opposing arm swing, knee lift and heel contact.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioColors.TextMuted
                )
                Spacer(Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    StudioPrimaryButton("Walk cycle", Modifier.weight(1f), enabled = skeleton != null) { vm.generateCycle(false) }
                    StudioPrimaryButton("Run cycle", Modifier.weight(1f), enabled = skeleton != null) { vm.generateCycle(true) }
                }
            }

            StudioPanel(padding = 12.dp) {
                SectionHeader(
                    "Animation Layers",
                    caption = "Blend, add or mute clips",
                    trailing = {
                        StudioIconButton(Icons.Rounded.Add, "Add layer", size = 30.dp) {
                            vm.addAnimationLayer(vm.scene.activeClip)
                        }
                    }
                )
                Spacer(Modifier.height(7.dp))
                if (vm.scene.layers.isEmpty()) {
                    DisabledHint("Layers stack on top of the active clip. Add one to blend a second motion.")
                } else {
                    vm.scene.layers.forEachIndexed { index, layer ->
                        LayerEditor(vm, layer, index)
                    }
                }
            }

            StudioPanel(padding = 12.dp) {
                SectionHeader(
                    "Retargeting",
                    caption = "Mixamo / Unity / Unreal humanoids",
                    trailing = { TinyBadge("${vm.externalRigs.size} SOURCES", StudioColors.Accent) }
                )
                Spacer(Modifier.height(7.dp))
                var rigIndex by remember { mutableIntStateOf(0) }
                var clipIndex by remember { mutableIntStateOf(0) }
                val rig = vm.externalRigs.getOrNull(rigIndex)
                if (rig == null) {
                    Text(
                        "Import another rigged GLB or BVH to transfer its motion onto this character. Bone names are matched against Unity, Unreal and Mixamo conventions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioColors.TextMuted
                    )
                    Spacer(Modifier.height(9.dp))
                    StudioGhostButton("Import animation source", Modifier.fillMaxWidth(), icon = Icons.Rounded.FolderOpen, onClick = onImport)
                } else {
                    StudioDropdown(rig.name, vm.externalRigs.map { it.name }) { index ->
                        rigIndex = index
                        clipIndex = 0
                    }
                    Spacer(Modifier.height(7.dp))
                    StudioDropdown(
                        rig.clips.getOrNull(clipIndex)?.name ?: "Clip",
                        rig.clips.map { it.name }
                    ) { index -> clipIndex = index }
                    Spacer(Modifier.height(7.dp))
                    StudioPrimaryButton("Retarget to current rig", Modifier.fillMaxWidth(), enabled = skeleton != null) {
                        vm.retargetExternal(rigIndex, clipIndex)
                    }
                    Spacer(Modifier.height(7.dp))
                    StudioGhostButton("Add another source", Modifier.fillMaxWidth(), icon = Icons.Rounded.Add, onClick = onImport)
                }
            }

            StudioPanel(padding = 12.dp) {
                SectionHeader("Workflow")
                Spacer(Modifier.height(7.dp))
                Text(
                    "1 · Enter Animation mode and pick a bone. 2 · Rotate the bone with FK or IK. 3 · Insert a key at the playhead. 4 · Scrub and refine in the timeline or graph editor. 5 · Export GLB with all clips for Unity.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioColors.TextMuted
                )
                Spacer(Modifier.height(8.dp))
                StudioGhostButton("Open animation inspector", Modifier.fillMaxWidth(), accent = StudioColors.Accent) {
                    vm.openInspectorTab(InspectorTab.ANIM)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun LayerEditor(vm: StudioViewModel, layer: AnimationLayer, index: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(StudioColors.Base)
            .border(1.dp, StudioColors.BorderSoft, RoundedCornerShape(9.dp))
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(if (layer.muted) StudioColors.TextMuted else StudioColors.Mint))
            Spacer(Modifier.width(6.dp))
            Text(
                layer.name,
                style = MaterialTheme.typography.bodySmall,
                color = StudioColors.TextPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (layer.muted) "MUTED" else if (layer.additive) "ADD" else "BLEND",
                style = MaterialTheme.typography.labelSmall,
                color = if (layer.muted) StudioColors.TextMuted else StudioColors.Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .clickable { vm.updateLayer(index) { it.muted = !it.muted } }
                    .padding(4.dp)
            )
            Icon(
                Icons.Rounded.Delete, "Remove layer",
                tint = StudioColors.TextMuted,
                modifier = Modifier.size(24.dp).clip(CircleShape).clickable { vm.removeLayer(index) }.padding(6.dp)
            )
        }
        Spacer(Modifier.height(3.dp))
        val clipName = vm.scene.clips.getOrNull(layer.clipIndex)?.name ?: "Clip"
        StudioDropdown(clipName, vm.scene.clips.map { it.name }) { clipIdx ->
            vm.updateLayer(index) { it.clipIndex = clipIdx }
        }
        SliderField(
            "Weight", layer.weight, 0f..1f,
            valueLabel = "${(layer.weight * 100).toInt()}%",
            onValueChange = { vm.updateLayer(index) { l -> l.weight = it } }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (layer.additive) "Additive" else "Override",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioColors.TextMuted
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "SWITCH",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioColors.Accent,
                    modifier = Modifier.clip(RoundedCornerShape(5.dp)).clickable { vm.updateLayer(index) { it.additive = !it.additive } }.padding(4.dp)
                )
            }
            Text(
                "MUTE",
                style = MaterialTheme.typography.labelSmall,
                color = if (layer.muted) StudioColors.Danger else StudioColors.TextMuted,
                modifier = Modifier.clip(RoundedCornerShape(5.dp)).clickable { vm.updateLayer(index) { it.muted = !it.muted } }.padding(4.dp)
            )
        }
    }
}
