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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studio3dpro.app.EditorMode
import com.studio3dpro.app.InspectorTab
import com.studio3dpro.app.SculptBrush
import com.studio3dpro.app.StudioViewModel
import com.studio3dpro.engine.math.Quat
import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.math.radToDeg
import com.studio3dpro.engine.scene.Material
import com.studio3dpro.engine.scene.SceneObject
import com.studio3dpro.engine.scene.TextureData

@Composable
fun InspectorPanel(
    vm: StudioViewModel,
    narrow: Boolean,
    onClose: () -> Unit,
    onAddObject: () -> Unit,
    onImport: () -> Unit,
    onPickTexture: () -> Unit,
    onOpenGraphEditor: () -> Unit = {}
) {
    // Subscribe to every scene mutation so numeric inspector fields follow
    // gizmo drags and undo/redo immediately.
    val revision = vm.sceneRevision
    val selection = vm.selection
    Column(Modifier.fillMaxSize().background(StudioColors.Surface)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("INSPECTOR", style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted)
                Spacer(Modifier.height(2.dp))
                Text(
                    when (vm.mode) {
                        com.studio3dpro.app.EditorMode.OBJECT -> vm.selectedObject?.name ?: "Scene"
                        com.studio3dpro.app.EditorMode.EDIT -> vm.selectedObject?.name?.let { "$it · Mesh" } ?: "Mesh"
                        com.studio3dpro.app.EditorMode.SCULPT -> vm.selectedObject?.name?.let { "$it · Sculpt" } ?: "Sculpt"
                        com.studio3dpro.app.EditorMode.RIG -> vm.selectedBoneName
                        com.studio3dpro.app.EditorMode.ANIMATE -> vm.currentClip()?.name ?: "Animation"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = StudioColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (narrow) {
                StudioIconButton(Icons.Rounded.Close, "Close inspector", onClick = onClose)
            }
        }
        Hairline()
        val tabs = InspectorTab.entries
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            tabs.forEach { tab ->
                val active = vm.inspectorTab == tab
                Box(
                    Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (active) StudioColors.Accent.copy(alpha = 0.13f) else Color.Transparent)
                        .clickable { vm.openInspectorTab(tab) }
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Text(
                        tab.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) StudioColors.Accent else StudioColors.TextMuted
                    )
                }
            }
        }
        Hairline()
        Box(Modifier.weight(1f)) {
            // Reading revision above keeps these panels in sync after undo,
            // gizmo drags and asynchronous boolean operations.
            val ignored = revision
            when (vm.inspectorTab) {
                InspectorTab.OBJECT -> ObjectInspector(vm, onAddObject, onImport)
                InspectorTab.MATERIAL -> MaterialInspector(vm, onPickTexture)
                InspectorTab.MESH -> MeshInspector(vm)
                InspectorTab.RIG -> RigInspector(vm, onImport)
                InspectorTab.ANIM -> AnimationInspector(vm, onImport, onOpenGraphEditor)
            }
            if (ignored < 0) Unit
        }
    }
}

@Composable
private fun SculptControls(vm: StudioViewModel) {
    StudioPanel(padding = 12.dp) {
        SectionHeader(
            "Sculpt brush",
            caption = "${vm.sculptBrush.label} · ${String.format(java.util.Locale.US, "%.2f m", vm.sculptRadius)}",
            trailing = {
                TinyBadge(
                    if (vm.symmetryX) "X-SYM" else "SYM OFF",
                    if (vm.symmetryX) StudioColors.Mint else StudioColors.TextMuted
                )
            }
        )
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SculptBrush.entries.forEach { brush ->
                val active = vm.sculptBrush == brush
                Box(
                    Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) StudioColors.Accent.copy(alpha = 0.14f) else StudioColors.Base)
                        .border(
                            1.dp,
                            if (active) StudioColors.Accent.copy(alpha = 0.55f) else StudioColors.BorderSoft,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { vm.sculptBrush = brush },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        brush.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) StudioColors.Accent else StudioColors.TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        SliderField(
            "Radius",
            vm.sculptRadius,
            0.02f..0.4f,
            valueLabel = String.format(java.util.Locale.US, "%.2f m", vm.sculptRadius)
        ) { vm.sculptRadius = it }
        SliderField(
            "Strength",
            vm.sculptStrength,
            0.05f..1f,
            valueLabel = "${(vm.sculptStrength * 100).toInt()}%"
        ) { vm.sculptStrength = it }
        ToggleRow(
            "X symmetry",
            vm.symmetryX,
            { vm.symmetryX = it },
            subtitle = "Mirror every stroke across the X axis"
        )
        Text(
            "One finger sculpts; two fingers orbit and zoom. Undo restores the last stroke.",
            style = MaterialTheme.typography.bodySmall,
            color = StudioColors.TextMuted
        )
    }
}

@Composable
private fun ObjectInspector(vm: StudioViewModel, onAddObject: () -> Unit, onImport: () -> Unit) {
    val obj = vm.selectedObject
    val state = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(state).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (vm.mode == EditorMode.SCULPT) SculptControls(vm)

        StudioPanel(padding = 12.dp) {
            SectionHeader("Project", caption = "Autosaved locally", trailing = {
                TinyBadge(vm.mode.short, StudioColors.Accent)
            })
            Spacer(Modifier.height(9.dp))
            StudioTextField(vm.scene.name, { vm.renameScene(it) }, placeholder = "Project name")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LabelStat("${vm.scene.objects.size}", "objects")
                LabelStat("${formatShort(vm.scene.totalVertices())}", "verts")
                LabelStat("${vm.scene.skeleton?.size ?: 0}", "bones")
            }
        }

        StudioPanel(padding = 12.dp) {
            SectionHeader("Objects", caption = "${vm.scene.objects.size} in scene", trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    StudioIconButton(Icons.Rounded.FolderOpen, "Import", size = 30.dp, onClick = onImport)
                    StudioIconButton(Icons.Rounded.Add, "Add object", size = 30.dp, onClick = onAddObject)
                }
            })
            Spacer(Modifier.height(8.dp))
            if (vm.scene.objects.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.Add,
                    title = "Empty scene",
                    body = "Add a primitive to start modelling or import a GLB character.",
                    action = { StudioGhostButton("Add object", icon = Icons.Rounded.Add, onClick = onAddObject) }
                )
            } else {
                Column(
                    Modifier.heightIn(max = 216.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    vm.scene.objects.forEach { sceneObject ->
                        ObjectRow(vm, sceneObject)
                    }
                }
            }
        }

        if (obj != null) {
            StudioPanel(padding = 12.dp) {
                SectionHeader("Transform", caption = "Local space", trailing = {
                    Text(
                        "RESET",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioColors.Accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { vm.resetObjectTransform(obj.id) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                })
                Spacer(Modifier.height(10.dp))
                AxisCaption("POSITION")
                VectorField(
                    obj.transform.position,
                    { vm.updateObjectPosition(obj.id, it) },
                    onEditStart = { vm.beginNumericEdit("Move Object") },
                    onEditEnd = { vm.finishNumericEdit() }
                )
                Spacer(Modifier.height(8.dp))
                AxisCaption("ROTATION")
                val euler = obj.transform.rotation.toEulerDegrees()
                VectorField(
                    euler,
                    { vm.updateObjectRotation(obj.id, Quat.fromEulerDegrees(it)) },
                    onEditStart = { vm.beginNumericEdit("Rotate Object") },
                    onEditEnd = { vm.finishNumericEdit() },
                    suffix = "°"
                )
                Spacer(Modifier.height(8.dp))
                AxisCaption("SCALE")
                VectorField(
                    obj.transform.scale,
                    { vm.updateObjectScale(obj.id, it) },
                    onEditStart = { vm.beginNumericEdit("Scale Object") },
                    onEditEnd = { vm.finishNumericEdit() }
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    StudioGhostButton("Duplicate", Modifier.weight(1f), icon = Icons.Rounded.ContentCopy) { vm.duplicateSelectedObject() }
                    StudioGhostButton("Frame", Modifier.weight(1f), accent = StudioColors.Mint) { vm.frameSelection() }
                    StudioGhostButton("Delete", Modifier.weight(1f), icon = Icons.Rounded.Delete, accent = StudioColors.Danger) { vm.deleteSelectedObject() }
                }
            }

            if (obj.mesh != null) {
                StudioPanel(padding = 12.dp) {
                    SectionHeader("Geometry", caption = obj.mesh!!.bounds().let {
                        "Size ${"%.2f".format(it.size.x)} × ${"%.2f".format(it.size.y)} × ${"%.2f".format(it.size.z)}"
                    })
                    Spacer(Modifier.height(10.dp))
                    LabeledValueRow("Vertices", formatShort(obj.mesh!!.vertexCount))
                    LabeledValueRow("Faces", formatShort(obj.mesh!!.faceCount))
                    LabeledValueRow("Triangles", formatShort(obj.mesh!!.triangles().size / 3))
                    LabeledValueRow("UV channels", if (obj.mesh!!.faceUvs.isNotEmpty()) "1" else "0")
                    if (obj.mesh!!.skin != null) LabeledValueRow("Skin", "4 influences / vertex", valueColor = StudioColors.Mint)
                    Spacer(Modifier.height(8.dp))
                    StudioGhostButton("Edit mesh", Modifier.fillMaxWidth(), accent = StudioColors.Accent) {
                        vm.selectObject(obj.id)
                        vm.changeMode(com.studio3dpro.app.EditorMode.EDIT)
                        vm.openInspectorTab(InspectorTab.MESH)
                    }
                }
            }
        } else {
            EmptyState(
                icon = Icons.Rounded.Palette,
                title = "Nothing selected",
                body = "Tap an object in the viewport or pick one from the list above.",
                action = { StudioGhostButton("Import model", icon = Icons.Rounded.FolderOpen, onClick = onImport) }
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ObjectRow(vm: StudioViewModel, obj: SceneObject) {
    val selected = vm.selection.objectId == obj.id
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) StudioColors.Accent.copy(alpha = 0.11f) else Color.Transparent)
            .border(1.dp, if (selected) StudioColors.Accent.copy(alpha = 0.32f) else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable { vm.selectObject(obj.id) }
            .padding(start = 7.dp, end = 3.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    Color(
                        obj.material.baseColor.x.coerceIn(0f, 1f),
                        obj.material.baseColor.y.coerceIn(0f, 1f),
                        obj.material.baseColor.z.coerceIn(0f, 1f)
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                obj.name,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) StudioColors.TextPrimary else StudioColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (obj.mesh != null) "${formatShort(obj.mesh!!.vertexCount)} verts" else "empty",
                style = MaterialTheme.typography.labelSmall,
                color = StudioColors.TextMuted
            )
        }
        Icon(
            if (obj.visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
            "Toggle visibility",
            tint = if (obj.visible) StudioColors.TextMuted else StudioColors.TextMuted.copy(alpha = 0.45f),
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable { vm.toggleObjectVisibility(obj.id) }
                .padding(7.dp)
        )
    }
}

@Composable
private fun MaterialInspector(vm: StudioViewModel, onPickTexture: () -> Unit) {
    val obj = vm.selectedObject
    if (obj == null) {
        EmptyState(Icons.Rounded.Palette, "No material", "Select a mesh object to edit its PBR material.")
        return
    }
    val material = obj.material
    var showColor by remember { mutableStateOf(false) }
    var showEmissive by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StudioPanel(padding = 12.dp) {
            SectionHeader("Material", caption = obj.name, trailing = { TinyBadge("PBR", StudioColors.Amber) })
            Spacer(Modifier.height(9.dp))
            StudioTextField(material.name, { vm.renameMaterial(it) }, placeholder = "Material name")
            Spacer(Modifier.height(11.dp))
            Text("BASE COLOR", style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted)
            Spacer(Modifier.height(5.dp))
            ColorField(material.baseColor) { showColor = true }
            Spacer(Modifier.height(7.dp))
            PaletteRow(material.baseColor) { vm.setBaseColor(it) }
            Spacer(Modifier.height(12.dp))
            SectionHeader("Surface")
            Spacer(Modifier.height(4.dp))
            SliderField(
                "Metallic", material.metallic, 0f..1f,
                valueLabel = "${(material.metallic * 100).toInt()}%",
                onValueChange = { vm.updateMaterial { m -> m.metallic = it } }
            )
            SliderField(
                "Roughness", material.roughness, 0.04f..1f,
                valueLabel = "${(material.roughness * 100).toInt()}%",
                onValueChange = { vm.updateMaterial { m -> m.roughness = it } }
            )
            SliderField(
                "Opacity", material.opacity, 0.05f..1f,
                valueLabel = "${(material.opacity * 100).toInt()}%",
                onValueChange = { vm.updateMaterial { m -> m.opacity = it } }
            )
            ToggleRow("Double sided", material.doubleSided, { vm.updateMaterial { m -> m.doubleSided = it } })
        }

        StudioPanel(padding = 12.dp) {
            SectionHeader("Emission")
            Spacer(Modifier.height(5.dp))
            ColorField(material.emissive) { showEmissive = true }
            SliderField(
                "Strength", material.emissiveStrength, 0f..8f,
                valueLabel = "%.1fx".format(material.emissiveStrength),
                onValueChange = { vm.updateMaterial { m -> m.emissiveStrength = it } }
            )
            if (material.emissive.lengthSq() > 0.0001f) {
                StudioGhostButton("Disable emission", Modifier.fillMaxWidth(), accent = StudioColors.TextMuted) {
                    vm.updateMaterial { it.emissive = Vec3.ZERO }
                }
            }
        }

        StudioPanel(padding = 12.dp) {
            SectionHeader("Base Color Texture", caption = material.baseColorTexture?.name ?: "No texture")
            Spacer(Modifier.height(9.dp))
            val tex = material.baseColorTexture
            if (tex != null) {
                TexturePreview(tex)
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                StudioGhostButton(if (tex == null) "Load texture" else "Replace", Modifier.weight(1f), icon = Icons.Rounded.FolderOpen) { onPickTexture() }
                if (tex != null) {
                    StudioGhostButton("Remove", Modifier.weight(1f), accent = StudioColors.Danger) { vm.clearTexture() }
                }
            }
            Spacer(Modifier.height(5.dp))
            DisabledHint("PNG or JPEG · decoded locally · packed into GLB on export")
        }

        StudioPanel(padding = 12.dp) {
            SectionHeader("Presets")
            Spacer(Modifier.height(8.dp))
            MaterialPreset("Studio Clay", Vec3(0.78f, 0.72f, 0.62f), 0f, 0.62f) { vm.applyMaterialPreset(it) }
            MaterialPreset("Brushed Steel", Vec3(0.63f, 0.65f, 0.69f), 1f, 0.32f) { vm.applyMaterialPreset(it) }
            MaterialPreset("Obsidian", Vec3(0.07f, 0.08f, 0.10f), 0.25f, 0.2f) { vm.applyMaterialPreset(it) }
            MaterialPreset("Warm Skin", Vec3(0.76f, 0.53f, 0.43f), 0f, 0.55f) { vm.applyMaterialPreset(it) }
            MaterialPreset("Polished Gold", Vec3(0.95f, 0.71f, 0.29f), 1f, 0.16f) { vm.applyMaterialPreset(it) }
            MaterialPreset("Porcelain", Vec3(0.92f, 0.9f, 0.86f), 0f, 0.14f) { vm.applyMaterialPreset(it) }
        }
        Spacer(Modifier.height(18.dp))
    }
    if (showColor) {
        ColorPickerDialog(
            title = "Base color",
            initial = material.baseColor,
            onDismiss = { showColor = false },
            onPick = {
                vm.setBaseColor(it)
                showColor = false
            }
        )
    }
    if (showEmissive) {
        ColorPickerDialog(
            title = "Emission color",
            initial = material.emissive,
            onDismiss = { showEmissive = false },
            onPick = {
                vm.updateMaterial { m -> m.emissive = it }
                showEmissive = false
            }
        )
    }
}

@Composable
private fun TexturePreview(texture: TextureData) {
    val bitmap = remember(texture) {
        runCatching { android.graphics.BitmapFactory.decodeByteArray(texture.bytes, 0, texture.bytes.size) }.getOrNull()
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(StudioColors.Base)
            .border(1.dp, StudioColors.Border, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Base color texture",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Text("Cannot decode texture", style = MaterialTheme.typography.bodySmall, color = StudioColors.Danger)
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xCC07080B))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(
                "${texture.name} · ${texture.bytes.size / 1024} KB",
                style = MaterialTheme.typography.labelSmall,
                color = StudioColors.TextSecondary
            )
        }
    }
}

@Composable
private fun MaterialPreset(label: String, color: Vec3, metallic: Float, roughness: Float, onClick: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick(label) }
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ColorSwatch(color)
        Spacer(Modifier.width(9.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = StudioColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(
            "M ${(metallic * 100).toInt()} · R ${(roughness * 100).toInt()}",
            style = MaterialTheme.typography.labelSmall,
            color = StudioColors.TextMuted
        )
    }
}

@Composable
private fun PaletteRow(selected: Vec3, onPick: (Vec3) -> Unit) {
    val colors = listOf(
        Vec3(0.78f, 0.72f, 0.62f), Vec3(0.88f, 0.87f, 0.84f), Vec3(0.36f, 0.38f, 0.42f),
        Vec3(0.07f, 0.08f, 0.10f), Vec3(0.72f, 0.44f, 0.32f), Vec3(0.63f, 0.54f, 0.78f),
        Vec3(0.35f, 0.55f, 0.42f), Vec3(0.79f, 0.62f, 0.29f), Vec3(0.35f, 0.48f, 0.68f)
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        colors.forEach { c ->
            val isSelected = hexOf(c) == hexOf(selected)
            Box(Modifier.weight(1f)) {
                ColorSwatch(c, Modifier.fillMaxWidth().height(20.dp), isSelected)
            }
        }
    }
}

@Composable
private fun AxisCaption(label: String) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun LabelStat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, color = StudioColors.TextPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted)
    }
}

internal fun formatShort(value: Int): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000f)
    value >= 1_000 -> "%.1fk".format(value / 1_000f)
    else -> value.toString()
}
