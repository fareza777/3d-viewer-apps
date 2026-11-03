package com.studio3dpro.app

import com.studio3dpro.engine.math.Vec3

enum class EditorMode(val label: String, val short: String) {
    OBJECT("Object", "OBJ"),
    EDIT("Model", "MODEL"),
    RIG("Rigging", "RIG"),
    ANIMATE("Animation", "ANIM"),
    SCULPT("Sculpt", "SCULPT")
}

enum class ElementMode { OBJECT, VERTEX, EDGE, FACE, BONE }

enum class ToolMode { SELECT, MOVE, ROTATE, SCALE, IK }

enum class SculptBrush(val label: String) {
    DRAW("Draw"),
    INFLATE("Inflate"),
    SMOOTH("Smooth"),
    FLATTEN("Flatten"),
    PINCH("Pinch")
}

enum class GizmoAxis { X, Y, Z, CENTER }

data class SelectionState(
    val objectId: Long? = null,
    val vertices: Set<Int> = emptySet(),
    val faces: Set<Int> = emptySet(),
    val bone: Int = -1,
    val elements: ElementMode = ElementMode.OBJECT
) {
    val hasSelection: Boolean
        get() = objectId != null || bone >= 0
}

data class GizmoState(
    val visible: Boolean = false,
    val pivot: Vec3 = Vec3.ZERO,
    val mode: ToolMode = ToolMode.MOVE,
    val selectedAxis: GizmoAxis? = null
)

data class ViewportHit(
    val objectId: Long,
    val face: Int,
    val point: Vec3,
    val normal: Vec3,
    val distance: Float
)

enum class SceneFormat(val extension: String, val label: String, val mime: String) {
    GLB("glb", "glTF Binary", "model/gltf-binary"),
    OBJ("obj", "Wavefront OBJ", "text/plain"),
    STL("stl", "STL Binary", "model/stl"),
    BVH("bvh", "BVH Motion", "text/plain"),
    PROJECT("studio3d", "Studio Project", "application/json"),
    PNG("png", "Viewport PNG", "image/png")
}
