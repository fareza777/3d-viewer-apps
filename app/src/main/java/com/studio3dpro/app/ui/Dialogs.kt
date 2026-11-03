package com.studio3dpro.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.studio3dpro.app.BuildConfig
import com.studio3dpro.app.ImportAccept
import com.studio3dpro.app.ImportProposal
import com.studio3dpro.app.PrimitiveKind
import com.studio3dpro.app.SceneFormat
import com.studio3dpro.app.StudioViewModel
import com.studio3dpro.app.UiMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddObjectSheet(vm: StudioViewModel, onDismiss: () -> Unit, onImport: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = StudioColors.SurfaceRaised,
        dragHandle = { SheetHandle() }
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ADD GEOMETRY", style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted)
                    Spacer(Modifier.height(3.dp))
                    Text("Start with a primitive", style = MaterialTheme.typography.headlineSmall, color = StudioColors.TextPrimary)
                }
                StudioIconButton(Icons.Rounded.Close, "Close", onClick = onDismiss)
            }
            Spacer(Modifier.height(14.dp))
            val kinds = PrimitiveKind.entries
            for (row in kinds.chunked(2)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { kind ->
                        PrimitiveCard(kind, Modifier.weight(1f)) {
                            vm.addPrimitive(kind)
                            onDismiss()
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(4.dp))
            Hairline()
            Spacer(Modifier.height(12.dp))
            StudioGhostButton(
                "Import GLB / glTF / OBJ / STL / BVH",
                Modifier.fillMaxWidth(),
                icon = Icons.Rounded.FolderOpen,
                onClick = {
                    onDismiss()
                    onImport()
                }
            )
            Spacer(Modifier.height(6.dp))
            DisabledHint("Imported characters keep their skeleton, skin and animation clips.")
        }
    }
}

@Composable
private fun SheetHandle() {
    Box(Modifier.fillMaxWidth().padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(StudioColors.Border))
    }
}

@Composable
private fun PrimitiveCard(kind: PrimitiveKind, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(104.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(StudioColors.Surface)
            .border(1.dp, StudioColors.BorderSoft, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(11.dp)
    ) {
        PrimitiveGlyph(kind, Modifier.size(34.dp))
        Spacer(Modifier.weight(1f))
        Text(
            when (kind) {
                PrimitiveKind.CUBE -> "Cube"
                PrimitiveKind.SPHERE -> "UV Sphere"
                PrimitiveKind.CYLINDER -> "Cylinder"
                PrimitiveKind.CONE -> "Cone"
                PrimitiveKind.TORUS -> "Torus"
                PrimitiveKind.PLANE -> "Plane"
            },
            style = MaterialTheme.typography.titleSmall,
            color = StudioColors.TextPrimary
        )
        Text(
            when (kind) {
                PrimitiveKind.CUBE -> "8 verts · quad topology"
                PrimitiveKind.SPHERE -> "576 verts · UV mapped"
                PrimitiveKind.CYLINDER -> "128 verts · capped"
                PrimitiveKind.CONE -> "96 verts · capped"
                PrimitiveKind.TORUS -> "512 verts · quad grid"
                PrimitiveKind.PLANE -> "4 verts · 1 face"
            },
            style = MaterialTheme.typography.labelSmall,
            color = StudioColors.TextMuted
        )
    }
}

@Composable
private fun PrimitiveGlyph(kind: PrimitiveKind, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.4.dp.toPx()
        val c = StudioColors.Accent
        val dim = StudioColors.Accent.copy(alpha = 0.35f)
        when (kind) {
            PrimitiveKind.CUBE -> {
                val a = Offset(w * 0.5f, h * 0.08f)
                val b = Offset(w * 0.9f, h * 0.3f)
                val cc = Offset(w * 0.5f, h * 0.52f)
                val d = Offset(w * 0.1f, h * 0.3f)
                val top = Offset(w * 0.5f, h * 0.36f)
                drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(cc.x, cc.y); lineTo(d.x, d.y); close() }, c, style = Stroke(stroke))
                drawLine(dim, d, Offset(w * 0.1f, h * 0.62f), stroke)
                drawLine(dim, cc, Offset(w * 0.5f, h * 0.84f), stroke)
                drawLine(dim, b, Offset(w * 0.9f, h * 0.62f), stroke)
                drawLine(dim, Offset(w * 0.1f, h * 0.62f), Offset(w * 0.5f, h * 0.84f), stroke)
                drawLine(dim, Offset(w * 0.5f, h * 0.84f), Offset(w * 0.9f, h * 0.62f), stroke)
                drawLine(dim, top, cc, stroke)
            }
            PrimitiveKind.SPHERE -> {
                drawCircle(c, w * 0.36f, style = Stroke(stroke))
                drawOval(c.copy(alpha = 0.5f), topLeft = Offset(w * 0.14f, h * 0.15f), size = androidx.compose.ui.geometry.Size(w * 0.72f, h * 0.7f), style = Stroke(stroke))
                drawOval(c.copy(alpha = 0.5f), topLeft = Offset(w * 0.36f, h * 0.14f), size = androidx.compose.ui.geometry.Size(w * 0.28f, h * 0.72f), style = Stroke(stroke))
            }
            PrimitiveKind.CYLINDER -> {
                drawOval(c, topLeft = Offset(w * 0.24f, h * 0.08f), size = androidx.compose.ui.geometry.Size(w * 0.52f, h * 0.2f), style = Stroke(stroke))
                drawLine(c, Offset(w * 0.24f, h * 0.18f), Offset(w * 0.24f, h * 0.78f), stroke)
                drawLine(c, Offset(w * 0.76f, h * 0.18f), Offset(w * 0.76f, h * 0.78f), stroke)
                drawOval(c.copy(alpha = 0.5f), topLeft = Offset(w * 0.24f, h * 0.68f), size = androidx.compose.ui.geometry.Size(w * 0.52f, h * 0.2f), style = Stroke(stroke))
            }
            PrimitiveKind.CONE -> {
                drawPath(Path().apply { moveTo(w * 0.5f, h * 0.08f); lineTo(w * 0.8f, h * 0.76f); lineTo(w * 0.2f, h * 0.76f); close() }, c, style = Stroke(stroke))
                drawOval(c.copy(alpha = 0.5f), topLeft = Offset(w * 0.2f, h * 0.66f), size = androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.2f), style = Stroke(stroke))
            }
            PrimitiveKind.TORUS -> {
                drawOval(c, topLeft = Offset(w * 0.08f, h * 0.25f), size = androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.5f), style = Stroke(stroke))
                drawOval(c.copy(alpha = 0.5f), topLeft = Offset(w * 0.3f, h * 0.39f), size = androidx.compose.ui.geometry.Size(w * 0.4f, h * 0.22f), style = Stroke(stroke))
            }
            PrimitiveKind.PLANE -> {
                drawPath(
                    Path().apply {
                        moveTo(w * 0.12f, h * 0.58f); lineTo(w * 0.52f, h * 0.3f)
                        lineTo(w * 0.88f, h * 0.54f); lineTo(w * 0.48f, h * 0.82f); close()
                    },
                    c.copy(alpha = 0.65f),
                    style = Stroke(stroke)
                )
                drawLine(dim, Offset(w * 0.52f, h * 0.3f), Offset(w * 0.48f, h * 0.82f), 1f)
                drawLine(dim, Offset(w * 0.12f, h * 0.58f), Offset(w * 0.88f, h * 0.54f), 1f)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    vm: StudioViewModel,
    onDismiss: () -> Unit,
    onExport: (SceneFormat, Boolean, Boolean) -> Unit
) {
    var allClips by remember { mutableStateOf(true) }
    var textures by remember { mutableStateOf(true) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = StudioColors.SurfaceRaised,
        dragHandle = { SheetHandle() }
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("EXPORT", style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted)
                    Spacer(Modifier.height(3.dp))
                    Text("Deliver your scene", style = MaterialTheme.typography.headlineSmall, color = StudioColors.TextPrimary)
                }
                StudioIconButton(Icons.Rounded.Close, "Close", onClick = onDismiss)
            }
            Spacer(Modifier.height(12.dp))
            StudioPanel(padding = 11.dp) {
                SectionHeader("glTF options", caption = "Applies to .glb")
                ToggleRow("All animation clips", allClips, { allClips = it }, subtitle = "${vm.scene.clips.size} clips · NLA-friendly")
                ToggleRow("Embed textures", textures, { textures = it }, subtitle = "Base color textures packed in the binary")
            }
            Spacer(Modifier.height(10.dp))
            val options = listOf(
                ExportOption(SceneFormat.GLB, "Game-engine ready", "Skinned mesh, skeleton, PBR material and animation. Recommended for Unity and Unreal."),
                ExportOption(SceneFormat.OBJ, "Interchange mesh", "Geometry and UVs for DCC tools. No rig or animation."),
                ExportOption(SceneFormat.STL, "3D printing", "Binary triangle mesh. No UVs, rig or animation."),
                ExportOption(SceneFormat.BVH, "Motion capture", "Active clip as BVH hierarchy and motion channels."),
                ExportOption(SceneFormat.PROJECT, "Studio project", "Lossless scene with rig, clips, layers, constraints and materials."),
                ExportOption(SceneFormat.PNG, "Viewport still", "Capture the current camera at viewport resolution.")
            )
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    ExportRow(option) {
                        val format = option.format
                        onDismiss()
                        onExport(format, allClips, textures)
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(5.dp))
                DisabledHint("FBX import is not supported by the bundled open-source stack. GLB preserves rig, skin and animation in Unity; BVH carries motion for retargeting.")
            }
        }
    }
}

private data class ExportOption(val format: SceneFormat, val title: String, val body: String)

@Composable
private fun ExportRow(option: ExportOption, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StudioColors.Surface)
            .border(1.dp, StudioColors.BorderSoft, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (option.format == SceneFormat.GLB) StudioColors.Accent.copy(alpha = 0.14f) else StudioColors.SurfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            Text(
                option.format.extension.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (option.format == SceneFormat.GLB) StudioColors.Accent else StudioColors.TextSecondary,
                fontSize = 9.5.sp
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(option.title, style = MaterialTheme.typography.titleSmall, color = StudioColors.TextPrimary)
            Text(option.body, style = MaterialTheme.typography.bodySmall, color = StudioColors.TextMuted, maxLines = 2)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.FileDownload, null, tint = StudioColors.TextMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun ImportProposalDialog(vm: StudioViewModel, proposal: ImportProposal, onDismiss: () -> Unit) {
    val incoming = proposal.result.scene
    val canRetarget = vm.scene.skeleton != null && incoming.skeleton != null && incoming.clips.isNotEmpty()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 22.dp)
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(StudioColors.SurfaceRaised)
                .border(1.dp, StudioColors.Border, RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(StudioColors.Accent.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.FolderOpen, null, tint = StudioColors.Accent, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("IMPORT · ${proposal.format.uppercase()}", style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        proposal.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        color = StudioColors.TextPrimary,
                        maxLines = 1
                    )
                }
                StudioIconButton(Icons.Rounded.Close, "Close", onClick = onDismiss)
            }
            Spacer(Modifier.height(14.dp))
            StudioPanel(padding = 12.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    ImportStat("${incoming.objects.size}", "objects")
                    ImportStat("${incoming.totalVertices()}", "verts")
                    ImportStat("${incoming.skeleton?.size ?: 0}", "bones")
                    ImportStat("${incoming.clips.size}", "clips")
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                ImportChoice(
                    title = "Open as new scene",
                    body = "Replace the current scene with this file. Rig, skinning, materials and clips are preserved.",
                    accent = StudioColors.Accent
                ) {
                    vm.acceptImport(ImportAccept.OPEN)
                    onDismiss()
                }
                Spacer(Modifier.height(7.dp))
                ImportChoice(
                    title = "Add objects to current scene",
                    body = if (incoming.objects.any { it.mesh?.skin != null })
                        "Static meshes are appended. Skinned meshes are skipped to keep the existing rig consistent."
                    else "Append all meshes with their transforms and materials.",
                    accent = StudioColors.Mint
                ) {
                    vm.acceptImport(ImportAccept.ADD)
                    onDismiss()
                }
                Spacer(Modifier.height(7.dp))
                ImportChoice(
                    title = "Retarget animation onto current rig",
                    body = if (canRetarget) "Map this file's motion onto the open humanoid using bone-name matching."
                    else "Open a rigged character first, and import a rig with at least one clip. FBX sources are not supported.",
                    accent = StudioColors.Amber,
                    enabled = canRetarget
                ) {
                    vm.acceptImport(ImportAccept.RETARGET)
                    onDismiss()
                }
                if (proposal.result.warnings.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Warning, null, tint = StudioColors.Amber, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            proposal.result.warnings.take(4).joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioColors.Amber.copy(alpha = 0.85f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            StudioGhostButton("Cancel import", Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}

@Composable
private fun ImportStat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, color = StudioColors.TextPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted)
    }
}

@Composable
private fun ImportChoice(
    title: String,
    body: String,
    accent: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(if (enabled) StudioColors.Surface else StudioColors.Surface.copy(alpha = 0.45f))
            .border(1.dp, if (enabled) accent.copy(alpha = 0.25f) else StudioColors.BorderSoft, RoundedCornerShape(11.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (enabled) accent else StudioColors.TextMuted.copy(alpha = 0.4f)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) StudioColors.TextPrimary else StudioColors.TextMuted)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = StudioColors.TextMuted)
        }
    }
}

@Composable
fun NewProjectDialog(onDismiss: () -> Unit, onNew: (Boolean) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(StudioColors.SurfaceRaised)
                .border(1.dp, StudioColors.Border, RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Text("NEW PROJECT", style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted)
            Spacer(Modifier.height(4.dp))
            Text("Start fresh", style = MaterialTheme.typography.headlineSmall, color = StudioColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "The current scene is kept in undo history and the autosave snapshot is replaced only after your next edit.",
                style = MaterialTheme.typography.bodySmall,
                color = StudioColors.TextMuted
            )
            Spacer(Modifier.height(14.dp))
            ImportChoice(
                "Rigged demo scene",
                "The 24-bone studio character with walk and run motion — ideal for learning the workflow.",
                StudioColors.Mint
            ) { onNew(false) }
            Spacer(Modifier.height(7.dp))
            ImportChoice(
                "Empty scene",
                "A clean grid with no objects, rig or clips.",
                StudioColors.Accent
            ) { onNew(true) }
            Spacer(Modifier.height(14.dp))
            StudioGhostButton("Cancel", Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 22.dp)
                .fillMaxWidth()
                .heightIn(max = 650.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(StudioColors.SurfaceRaised)
                .border(1.dp, StudioColors.Border, RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StudioMark(38.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("3D STUDIO PRO", style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted)
                    Spacer(Modifier.height(2.dp))
                    Text("Field guide", style = MaterialTheme.typography.headlineSmall, color = StudioColors.TextPrimary)
                }
                StudioIconButton(Icons.Rounded.Close, "Close", onClick = onDismiss)
            }
            Spacer(Modifier.height(14.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                HelpSection("Viewport", "One finger orbits, two fingers pan and pinch-zoom. Double-tap frames the selection. Use the axis dial to frame the whole scene. The HUD toggles grid, skeleton, wireframe and camera presets.")
                HelpSection("Modelling", "Model mode exposes vertex, edge and face selection. Build with extrude, inset, bevel, Catmull-Clark subdivision, Laplacian smoothing, mirror, weld, flip and CSG boolean. UV projection supports box, cylindrical, spherical and planar mapping.")
                HelpSection("Sculpting", "Five stroke-based brushes: draw, inflate, smooth, flatten and pinch. X symmetry and radius/strength are live. Sculpting writes directly into the editable mesh.")
                HelpSection("Rigging", "Auto-rig fits a 24-bone Unity-style humanoid to the selected mesh and computes smooth weights. Edit bone rest transforms, paint weights with a heat-map overlay, drag IK targets or add limit/copy-rotation constraints.")
                HelpSection("Animation", "Pose bones in Animation mode, insert keys at the playhead, and edit timing in the track timeline. The graph editor plots Euler rotation curves and supports vertical key dragging. Layers blend or add clips; cycles generate real walk/run keys.")
                HelpSection("Export", "GLB carries mesh, skin, skeleton, materials, textures and clips for Unity/Unreal. OBJ/STL are geometry-only. BVH carries the active motion. Studio projects are lossless. PNG captures the viewport.")
                HelpSection("Compatibility", "FBX import is not available in the open-source stack used here; use GLB. BVH retargeting accepts Mixamo, Unity and Unreal bone naming. Up to 48 bones per mesh are evaluated on the GPU.")
                Spacer(Modifier.height(12.dp))
                Hairline()
                Spacer(Modifier.height(10.dp))
                Text(
                    "3D Studio Pro 1.0 · Offline-first. Built with a custom GLES 3 PBR renderer, glTF/OBJ/STL/BVH codecs and a Kotlin geometry engine. Space Grotesk and Inter fonts are licensed under the SIL Open Font License.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioColors.TextMuted
                )
            }
            Spacer(Modifier.height(12.dp))
            StudioPrimaryButton("Continue creating", Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 22.dp)
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(StudioColors.SurfaceRaised)
                .border(1.dp, StudioColors.Border, RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StudioMark(44.dp)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("3D STUDIO PRO", style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted)
                    Spacer(Modifier.height(2.dp))
                    Text("About this build", style = MaterialTheme.typography.headlineSmall, color = StudioColors.TextPrimary)
                    Text(
                        "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioColors.TextMuted
                    )
                }
                StudioIconButton(Icons.Rounded.Close, "Close", onClick = onDismiss)
            }
            Spacer(Modifier.height(14.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                StudioPanel(padding = 12.dp) {
                    Text("WHAT'S NEW", style = MaterialTheme.typography.labelSmall, color = StudioColors.Accent)
                    Spacer(Modifier.height(6.dp))
                    AboutLine("Branded splash and native Android 12 launch animation")
                    AboutLine("Three-step first-run tour with demo or empty-scene start")
                    AboutLine("Light, dark and system themes with larger touch typography")
                    AboutLine("New adaptive launcher icon and a complete settings screen")
                }
                Spacer(Modifier.height(10.dp))
                StudioPanel(padding = 12.dp) {
                    Text("ENGINE", style = MaterialTheme.typography.labelSmall, color = StudioColors.Accent)
                    Spacer(Modifier.height(6.dp))
                    AboutFact("Renderer", "Custom OpenGL ES 3.0 PBR, 48-bone GPU skinning")
                    AboutFact("Geometry", "Editable quads, extrude, bevel, CSG, sculpt, UV")
                    AboutFact("Rigging", "24-bone humanoid auto-rig, weights, IK, constraints")
                    AboutFact("Animation", "Keyframes, curves, layers, cycles, retargeting")
                    AboutFact("Files", "GLB/glTF, OBJ, STL, BVH, .studio3d projects")
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Runs on-device: no account, no cloud sync and no analytics. Scenes, autosaves and textures stay in app-private storage until you export them. The only network traffic is ad serving; Settings → Ads & rewards removes it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioColors.TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Built with Kotlin, Jetpack Compose and AndroidX. Fonts under the SIL Open Font License; glTF exchange follows the Khronos 2.0 specification. © 2026 3D Studio Pro.",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioColors.TextMuted
                )
            }
            Spacer(Modifier.height(12.dp))
            StudioPrimaryButton("Back to studio", Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}

@Composable
private fun AboutLine(text: String) {
    Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 5.dp).size(4.dp).clip(CircleShape).background(StudioColors.Mint))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = StudioColors.TextSecondary)
    }
}

@Composable
private fun AboutFact(label: String, value: String) {
    Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = StudioColors.TextMuted,
            modifier = Modifier.width(68.dp)
        )
        Text(value, style = MaterialTheme.typography.bodySmall, color = StudioColors.TextSecondary)
    }
}

@Composable
private fun HelpSection(title: String, body: String) {
    Column(Modifier.padding(bottom = 11.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = StudioColors.Accent)
        Spacer(Modifier.height(3.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = StudioColors.TextSecondary)
    }
}

@Composable
fun BusyOverlay(label: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xB307080B))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(StudioColors.SurfaceRaised)
                .border(1.dp, StudioColors.Border, RoundedCornerShape(18.dp))
                .padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = StudioColors.Accent, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(13.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = StudioColors.TextSecondary, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun MessageToast(message: UiMessage?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(StudioColors.SurfaceRaised)
                .border(1.dp, StudioColors.Border, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(StudioColors.Mint))
            Spacer(Modifier.width(8.dp))
            Text(
                message?.text.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = StudioColors.TextPrimary,
                maxLines = 2
            )
        }
    }
}
