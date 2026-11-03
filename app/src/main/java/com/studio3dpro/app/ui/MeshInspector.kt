package com.studio3dpro.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.studio3dpro.app.ElementMode
import com.studio3dpro.app.EditorMode
import com.studio3dpro.app.StudioViewModel
import com.studio3dpro.engine.mesh.BooleanOp
import com.studio3dpro.engine.mesh.UvProjection

@Composable
fun MeshInspector(vm: StudioViewModel) {
    val obj = vm.selectedObject
    if (obj?.mesh == null) {
        EmptyState(
            icon = Icons.Rounded.Layers,
            title = "No mesh selected",
            body = "Select a mesh object in the viewport, then switch to Model mode to edit topology."
        )
        return
    }
    val mesh = obj.mesh!!
    var extrudeDistance by remember { mutableFloatStateOf(0.15f) }
    var insetAmount by remember { mutableFloatStateOf(0.2f) }
    var bevelWidth by remember { mutableFloatStateOf(0.05f) }
    var booleanTarget by remember { mutableIntStateOf(-1) }
    var booleanOp by remember { mutableStateOf(BooleanOp.UNION) }
    var uvMode by remember { mutableStateOf(UvProjection.Mode.BOX) }
    val others = vm.scene.objects.filter { it.id != obj.id && it.mesh != null }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StudioPanel(padding = 12.dp) {
            SectionHeader("Topology", caption = obj.name, trailing = { TinyBadge("EDIT", StudioColors.Amber) })
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MeshStat("${formatShort(mesh.vertexCount)}", "vertices")
                MeshStat("${formatShort(mesh.edges().size)}", "edges")
                MeshStat("${formatShort(mesh.faceCount)}", "faces")
                MeshStat("${formatShort(mesh.triangles().size / 3)}", "tris")
            }
            Spacer(Modifier.height(11.dp))
            SegmentedOptions(
                listOf("Vertex", "Edge", "Face"),
                when (vm.elementMode) {
                    ElementMode.EDGE -> 1
                    ElementMode.FACE -> 2
                    else -> 0
                }
            ) { index ->
                vm.changeElementMode(
                    when (index) {
                        1 -> ElementMode.EDGE
                        2 -> ElementMode.FACE
                        else -> ElementMode.VERTEX
                    }
                )
            }
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                StudioGhostButton("Select all", Modifier.weight(1f), icon = Icons.Rounded.SelectAll) { vm.selectAllElements() }
                StudioGhostButton("Deselect", Modifier.weight(1f)) { vm.clearElementSelection() }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                when (vm.elementMode) {
                    ElementMode.VERTEX -> "${vm.selection.vertices.size} vertices selected · tap to pick, drag gizmo to move"
                    ElementMode.EDGE -> "${vm.selection.vertices.size / 2} edges selected · bevel uses edge vertices"
                    ElementMode.FACE -> "${vm.selection.faces.size} faces selected · extrude/inset apply to selection"
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = StudioColors.TextMuted
            )
        }

        StudioPanel(padding = 12.dp) {
            SectionHeader("Extrude", caption = "Pull selected faces along their normals")
            Spacer(Modifier.height(8.dp))
            SliderField("Distance", extrudeDistance, 0.01f..1f, valueLabel = "%.2f".format(extrudeDistance)) { extrudeDistance = it }
            StudioPrimaryButton("Extrude faces", Modifier.fillMaxWidth()) { vm.extrude(extrudeDistance) }
        }

        StudioPanel(padding = 12.dp) {
            SectionHeader("Inset", caption = "Create a border on each selected face")
            Spacer(Modifier.height(8.dp))
            SliderField("Amount", insetAmount, 0.02f..0.8f, valueLabel = "%.2f".format(insetAmount)) { insetAmount = it }
            StudioGhostButton("Inset faces", Modifier.fillMaxWidth(), accent = StudioColors.Accent) { vm.inset(insetAmount) }
        }

        StudioPanel(padding = 12.dp) {
            SectionHeader("Bevel", caption = "Chamfer selected vertices or edge")
            Spacer(Modifier.height(8.dp))
            SliderField("Width", bevelWidth, 0.005f..0.25f, valueLabel = "%.3f".format(bevelWidth)) { bevelWidth = it }
            StudioGhostButton("Bevel selection", Modifier.fillMaxWidth(), accent = StudioColors.Accent) { vm.bevel(bevelWidth) }
        }

        StudioPanel(padding = 12.dp) {
            SectionHeader("Remesh")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                StudioGhostButton("Subdivide", Modifier.weight(1f), icon = Icons.Rounded.DoneAll) { vm.subdivideMesh() }
                StudioGhostButton("Smooth", Modifier.weight(1f), icon = Icons.Rounded.Straighten) { vm.smoothMesh() }
            }
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                StudioGhostButton("Mirror X", Modifier.weight(1f)) { vm.mirrorMesh() }
                StudioGhostButton("Merge", Modifier.weight(1f)) { vm.weldMesh() }
            }
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                StudioGhostButton("Flip normals", Modifier.weight(1f), icon = Icons.Rounded.Flip) { vm.flipNormals() }
                StudioGhostButton("Delete faces", Modifier.weight(1f), icon = Icons.Rounded.Delete, accent = StudioColors.Danger) { vm.deleteSelectedFaces() }
            }
        }

        StudioPanel(padding = 12.dp) {
            SectionHeader("Boolean", caption = "Trim with another solid")
            Spacer(Modifier.height(8.dp))
            if (others.isEmpty()) {
                DisabledHint("Add a second mesh to use constructive solid geometry.")
            } else {
                StudioDropdown(
                    others.getOrNull(booleanTarget)?.name ?: "Choose cutter",
                    others.map { it.name }
                ) { index -> booleanTarget = index }
                Spacer(Modifier.height(7.dp))
                SegmentedOptions(
                    listOf("Union", "Difference", "Intersect"),
                    when (booleanOp) {
                        BooleanOp.UNION -> 0
                        BooleanOp.DIFFERENCE -> 1
                        BooleanOp.INTERSECTION -> 2
                    }
                ) { index ->
                    booleanOp = when (index) {
                        1 -> BooleanOp.DIFFERENCE
                        2 -> BooleanOp.INTERSECTION
                        else -> BooleanOp.UNION
                    }
                }
                Spacer(Modifier.height(7.dp))
                StudioPrimaryButton(
                    "Apply ${booleanOp.name.lowercase()}",
                    Modifier.fillMaxWidth(),
                    enabled = booleanTarget in others.indices
                ) {
                    others.getOrNull(booleanTarget)?.let { vm.booleanWith(it.id, booleanOp) }
                }
            }
        }

        StudioPanel(padding = 12.dp) {
            SectionHeader("UV Mapping", caption = "Procedural projection for the active mesh")
            Spacer(Modifier.height(8.dp))
            SegmentedOptions(
                listOf("Box", "Cylinder", "Sphere", "Planar"),
                uvMode.ordinal
            ) { index -> uvMode = UvProjection.Mode.entries[index] }
            Spacer(Modifier.height(7.dp))
            StudioGhostButton("Project UVs", Modifier.fillMaxWidth(), accent = StudioColors.Mint) { vm.unwrap(uvMode) }
            Spacer(Modifier.height(6.dp))
            DisabledHint("GLB export writes the active UV channel with the material.")
        }

        StudioPanel(padding = 12.dp) {
            SectionHeader("Sculpt")
            Spacer(Modifier.height(6.dp))
            Text(
                "Switch to Sculpt mode for draw, inflate, smooth, flatten and pinch brushes with X symmetry.",
                style = MaterialTheme.typography.bodySmall,
                color = StudioColors.TextMuted
            )
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                StudioGhostButton("Open sculpt", Modifier.weight(1f), icon = Icons.Rounded.Brush, accent = StudioColors.Amber) {
                    vm.selectObject(obj.id)
                    vm.changeMode(EditorMode.SCULPT)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun MeshStat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, color = StudioColors.TextPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted)
    }
}
