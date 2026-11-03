package com.studio3dpro.app.render

import com.studio3dpro.app.EditorMode
import com.studio3dpro.app.ElementMode
import com.studio3dpro.app.GizmoState
import com.studio3dpro.app.SelectionState
import com.studio3dpro.app.ToolMode
import android.graphics.Bitmap
import com.studio3dpro.engine.scene.Pose
import com.studio3dpro.engine.scene.Scene
import com.studio3dpro.engine.math.Vec3

/**
 * Thread-safe bridge between Compose/ViewModel and the GL renderer.
 * Geometry and scene mutations are guarded by [lock]; renderer reads a
 * consistent scene snapshot before uploading.
 */
class StudioRuntime {
    val lock = Any()
    val camera = CameraState()

    @Volatile var scene: Scene? = null
    @Volatile var poseOverride: Pose? = null
    @Volatile var ikTarget: Vec3? = null
    @Volatile var time: Float = 0f
    @Volatile var playing: Boolean = true
    @Volatile var geometryRevision: Long = 0L
    @Volatile var selectionRevision: Long = 0L
    @Volatile var selection: SelectionState = SelectionState()
    @Volatile var gizmo: GizmoState = GizmoState()
    @Volatile var editorMode: EditorMode = EditorMode.OBJECT
    @Volatile var elementMode: ElementMode = ElementMode.OBJECT
    @Volatile var tool: ToolMode = ToolMode.MOVE
    @Volatile var showSkeleton: Boolean = true
    @Volatile var showGrid: Boolean = true
    @Volatile var weightPaintBone: Int = -1
    @Volatile var wireframe: Boolean = false

    /** Soft key-light shadows. Disabled on low-end devices from Settings. */
    @Volatile var dynamicShadows: Boolean = true
    @Volatile var viewportWidth: Int = 1
    @Volatile var viewportHeight: Int = 1
    @Volatile var fps: Int = 0
    @Volatile var triangleCount: Int = 0
    @Volatile var frameCallback: ((Int) -> Unit)? = null
    @Volatile var captureRequested: Boolean = false
    @Volatile var captureCallback: ((Bitmap) -> Unit)? = null

    fun markGeometryChanged() {
        geometryRevision++
    }

    fun markSelectionChanged() {
        selectionRevision++
    }

    fun replaceScene(scene: Scene) {
        synchronized(lock) {
            this.scene = scene
            geometryRevision++
            selectionRevision++
        }
    }
}
