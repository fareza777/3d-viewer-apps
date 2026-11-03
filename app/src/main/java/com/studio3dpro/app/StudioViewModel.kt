package com.studio3dpro.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.studio3dpro.app.render.CameraState
import com.studio3dpro.app.render.StudioRuntime
import com.studio3dpro.engine.DemoScene
import com.studio3dpro.engine.anim.AnimationClip
import com.studio3dpro.engine.anim.AnimTrack
import com.studio3dpro.engine.anim.AnimationLayer
import com.studio3dpro.engine.anim.ChannelType
import com.studio3dpro.engine.anim.CycleGenerator
import com.studio3dpro.engine.anim.Interp
import com.studio3dpro.engine.anim.PosePreset
import com.studio3dpro.engine.anim.PoseSampler
import com.studio3dpro.engine.anim.Retarget
import com.studio3dpro.engine.io.BvhIO
import com.studio3dpro.engine.io.Glb
import com.studio3dpro.engine.io.GltfExporter
import com.studio3dpro.engine.io.GltfImporter
import com.studio3dpro.engine.io.ModelImportResult
import com.studio3dpro.engine.io.ObjIO
import com.studio3dpro.engine.io.ProjectIO
import com.studio3dpro.engine.io.StlIO
import com.studio3dpro.engine.math.Aabb
import com.studio3dpro.engine.math.Mat4
import com.studio3dpro.engine.math.Quat
import com.studio3dpro.engine.math.Ray
import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.math.Vec4
import com.studio3dpro.engine.math.clamp
import com.studio3dpro.engine.math.degToRad
import com.studio3dpro.engine.math.radToDeg
import com.studio3dpro.engine.mesh.BooleanOp
import com.studio3dpro.engine.mesh.Mesh
import com.studio3dpro.engine.mesh.MeshBoolean
import com.studio3dpro.engine.mesh.MeshOps
import com.studio3dpro.engine.mesh.Primitives
import com.studio3dpro.engine.mesh.Sculpt
import com.studio3dpro.engine.mesh.SculptMode
import com.studio3dpro.engine.mesh.SkinWeights
import com.studio3dpro.engine.mesh.UvProjection
import com.studio3dpro.engine.rig.AutoRig
import com.studio3dpro.engine.rig.BoneConstraint
import com.studio3dpro.engine.rig.ConstraintType
import com.studio3dpro.engine.rig.Ik
import com.studio3dpro.engine.rig.Skinning
import com.studio3dpro.engine.scene.BonePose
import com.studio3dpro.engine.scene.Material
import com.studio3dpro.engine.scene.Pose
import com.studio3dpro.engine.scene.Scene
import com.studio3dpro.engine.scene.SceneObject
import com.studio3dpro.engine.scene.TextureData
import com.studio3dpro.engine.scene.Transform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class InspectorTab { OBJECT, MATERIAL, MESH, RIG, ANIM }
enum class PrimitiveKind { CUBE, SPHERE, CYLINDER, CONE, TORUS, PLANE }
enum class CameraView(val label: String) { PERSPECTIVE("Persp"), FRONT("Front"), SIDE("Side"), TOP("Top"), BACK("Back") }
enum class ImportAccept { OPEN, ADD, RETARGET }

data class ImportProposal(
    val fileName: String,
    val result: ModelImportResult,
    val rawBytes: ByteArray? = null,
    val format: String = "glTF"
)

data class ExternalRig(val name: String, val skeleton: com.studio3dpro.engine.scene.Skeleton, val clips: List<AnimationClip>)

data class UiMessage(val id: Long, val text: String)

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    val runtime = StudioRuntime()
    private val prefs by lazy { application.getSharedPreferences("studio3d", 0) }
    private val autosaveFile: File by lazy { File(application.filesDir, "autosave.${ProjectIO.EXTENSION}") }

    var scene: Scene by mutableStateOf(Scene())
        private set
    var sceneRevision by mutableIntStateOf(0)
        private set

    var mode by mutableStateOf(EditorMode.OBJECT)
        private set
    var tool by mutableStateOf(ToolMode.MOVE)
        private set
    var elementMode by mutableStateOf(ElementMode.OBJECT)
        private set
    var selection by mutableStateOf(SelectionState())
        private set
    var poseRevision by mutableIntStateOf(0)
        private set
    var time by mutableFloatStateOf(0f)
        private set
    var playing by mutableStateOf(true)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var showTimeline by mutableStateOf(false)
        private set
    var showGraphEditor by mutableStateOf(false)
        private set

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var lastAction by mutableStateOf("Ready")
        private set
    var lastAutosave by mutableStateOf<String?>(null)
        private set

    var inspectorTab by mutableStateOf(InspectorTab.OBJECT)
        private set
    var weightPaintActive by mutableStateOf(false)
        private set
    var weightRadius by mutableFloatStateOf(0.12f)
    var weightStrength by mutableFloatStateOf(0.5f)
    var sculptBrush by mutableStateOf(SculptBrush.DRAW)
    var sculptRadius by mutableFloatStateOf(0.14f)
    var sculptStrength by mutableFloatStateOf(0.35f)
    var symmetryX by mutableStateOf(true)
    var ikChainLength by mutableIntStateOf(2)
    var snapEnabled by mutableStateOf(false)
    var showSkeleton by mutableStateOf(true)
    var showGrid by mutableStateOf(true)
    var wireframe by mutableStateOf(false)
        private set

    var showOnboarding by mutableStateOf(!StudioPreferences.onboardingComplete)
        private set
    var showSettings by mutableStateOf(false)
        private set

    var message by mutableStateOf<UiMessage?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var busyLabel by mutableStateOf("")
        private set
    var importProposal by mutableStateOf<ImportProposal?>(null)
        private set
    var externalRigs by mutableStateOf<List<ExternalRig>>(emptyList())
        private set
    var lastImportedRigName by mutableStateOf<String?>(null)
        private set

    /**
     * Incremented after a successful export so the screen can offer an
     * interstitial at a natural break instead of in the middle of an edit.
     */
    var exportCompleted by mutableStateOf(0)
        private set

    private val undoStack = ArrayDeque<Scene>()
    private val redoStack = ArrayDeque<Scene>()
    private var lastUndoLabel = ""
    private var lastUndoAt = 0L
    private var messageId = 0L
    private var autosaveJob: Job? = null
    private var transformGestureActive = false
    private var gestureUndoPushed = false
    private var dragObjectId: Long? = null
    private var dragInitialPosition = Vec3.ZERO
    private var dragInitialRotation = Quat.IDENTITY
    private var dragInitialScale = Vec3.ONE
    private var dragInitialVertices = HashMap<Int, Vec3>()
    private var dragAxis: GizmoAxis = GizmoAxis.X
    private var dragStartParam = 0f
    private var dragStartAngle = 0f
    private var dragStartPoint = Vec3.ZERO
    private var dragPivot = Vec3.ZERO
    private var dragMode: ToolMode = ToolMode.MOVE
    private var ikStartPose: Pose? = null
    private var ikTargetBone = -1
    private var sculptObjectId: Long? = null
    private var sculpting = false
    private var weightObjectId: Long? = null
    private var painting = false

    init {
        runtime.dynamicShadows = StudioPreferences.dynamicShadows
        // Scene construction, skin binding and autosave parsing are deliberately
        // off the main thread; the first frame is a branded loading state.
        viewModelScope.launch(Dispatchers.IO) {
            val s = loadInitial()
            withContext(Dispatchers.Main) {
                scene = s
                setInitialScene(s)
                isLoading = false
            }
        }
    }

    // ------------------------------------------------------------------ loading

    private fun loadInitial(): Scene {
        return runCatching {
            if (autosaveFile.exists() && autosaveFile.length() > 100) {
                ProjectIO.load(autosaveFile.readText())
            } else DemoScene.create()
        }.getOrElse { DemoScene.create() }
    }

    private fun setInitialScene(s: Scene) {
        scene = s
        runtime.replaceScene(s)
        playing = true
        time = 0f
        runtime.time = 0f
        val clip = s.clips.getOrNull(s.activeClip)
        if (clip == null && s.clips.isNotEmpty()) {
            s.activeClip = 0
        }
        runtime.showSkeleton = false
        showSkeleton = false
        runtime.camera.frame(s.frameBounds())
        if (s.skeleton != null) {
            val hips = s.skeleton!!.indexOfName("Hips")
            if (hips >= 0) {
                selection = SelectionState(objectId = s.objects.firstOrNull { it.mesh?.skin != null }?.id, bone = hips, elements = ElementMode.BONE)
                runtime.selection = selection
            }
        } else if (s.objects.isNotEmpty()) {
            selection = SelectionState(objectId = s.objects.first().id)
            runtime.selection = selection
        }
        runtime.editorMode = mode
        runtime.tool = tool
        runtime.selectionRevision++
    }

    // ------------------------------------------------------------------- history

    private fun recordUndo(label: String, coalesce: Boolean = false) {
        val now = System.currentTimeMillis()
        if (coalesce && label == lastUndoLabel && now - lastUndoAt < 700) {
            lastUndoAt = now
            return
        }
        undoStack.addLast(scene.copy())
        while (undoStack.size > 24) undoStack.removeFirst()
        redoStack.clear()
        canUndo = true
        canRedo = false
        lastUndoLabel = label
        lastUndoAt = now
    }

    private fun publish(next: Scene, geometry: Boolean = true) {
        synchronized(runtime.lock) {
            scene = next
            runtime.scene = next
            if (geometry) runtime.geometryRevision++
            runtime.selectionRevision++
        }
        sceneRevision++
        lastAction = next.name
        scheduleAutosave()
    }

    private fun editScene(label: String, geometry: Boolean = true, coalesce: Boolean = false, block: (Scene) -> Unit) {
        recordUndo(label, coalesce)
        val next = scene.copy()
        synchronized(runtime.lock) {
            block(next)
            next.touch()
            scene = next
            runtime.scene = next
            if (geometry) runtime.geometryRevision++
        }
        sceneRevision++
        lastAction = label
        scheduleAutosave()
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(scene.copy())
        scene = previous
        runtime.replaceScene(previous)
        if (previous.clips.isNotEmpty()) {
            previous.activeClip = previous.activeClip.coerceIn(0, previous.clips.size - 1)
        }
        time = time.coerceIn(0f, currentClip()?.duration ?: 0f)
        runtime.time = time
        runtime.poseOverride = null
        validateSelection()
        canUndo = undoStack.isNotEmpty()
        canRedo = true
        sceneRevision++
        lastAction = "Undo"
        refreshGizmo()
        scheduleAutosave()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(scene.copy())
        scene = next
        runtime.replaceScene(next)
        if (next.clips.isNotEmpty()) next.activeClip = next.activeClip.coerceIn(0, next.clips.size - 1)
        runtime.poseOverride = null
        validateSelection()
        canUndo = true
        canRedo = redoStack.isNotEmpty()
        sceneRevision++
        lastAction = "Redo"
        refreshGizmo()
        scheduleAutosave()
    }

    private fun validateSelection() {
        if (scene.objects.none { it.id == selection.objectId }) {
            selection = selection.copy(objectId = null, vertices = emptySet(), faces = emptySet())
        }
        if (scene.skeleton == null || selection.bone !in 0 until (scene.skeleton?.size ?: 0)) {
            selection = selection.copy(bone = -1)
        }
        val mesh = selectedObject?.mesh
        if (mesh != null) {
            selection = selection.copy(
                vertices = selection.vertices.filter { it in 0 until mesh.vertexCount }.toSet(),
                faces = selection.faces.filter { it in 0 until mesh.faceCount }.toSet()
            )
        }
        runtime.selection = selection
        runtime.selectionRevision++
    }

    // ------------------------------------------------------------------ UI state

    fun showMessage(text: String) {
        message = UiMessage(++messageId, text)
    }

    fun consumeMessage() {
        message = null
    }

    fun changeMode(newMode: EditorMode) {
        if (mode != newMode) StudioHaptics.tick()
        mode = newMode
        runtime.editorMode = newMode
        runtime.poseOverride = null
        runtime.ikTarget = null
        if (newMode == EditorMode.SCULPT) {
            tool = ToolMode.SELECT
            runtime.tool = tool
            weightPaintActive = false
            runtime.weightPaintBone = -1
            runtime.showSkeleton = false
            playing = false
            runtime.playing = false
            showTimeline = false
        } else if (newMode == EditorMode.RIG) {
            tool = ToolMode.ROTATE
            runtime.tool = tool
            inspectorTab = InspectorTab.RIG
            runtime.showSkeleton = true
            showSkeleton = true
            showTimeline = false
        } else if (newMode == EditorMode.ANIMATE) {
            tool = ToolMode.ROTATE
            runtime.tool = tool
            inspectorTab = InspectorTab.ANIM
            runtime.showSkeleton = true
            showSkeleton = true
            showTimeline = true
            runningOrbitDefaults()
        } else if (newMode == EditorMode.EDIT) {
            tool = ToolMode.MOVE
            runtime.tool = tool
            elementMode = ElementMode.VERTEX
            selection = selection.copy(elements = ElementMode.VERTEX)
            runtime.elementMode = elementMode
            runtime.selection = selection
            runtime.showSkeleton = false
            showTimeline = false
            playing = false
            runtime.playing = false
        } else {
            tool = ToolMode.MOVE
            runtime.tool = tool
            elementMode = ElementMode.OBJECT
            selection = selection.copy(elements = ElementMode.OBJECT, vertices = emptySet(), faces = emptySet())
            runtime.elementMode = elementMode
            runtime.selection = selection
            runtime.showSkeleton = false
        }
        refreshGizmo()
    }

    private fun runningOrbitDefaults() {
        if (scene.skeleton == null) return
        if (selection.bone < 0) {
            val hips = scene.skeleton!!.indexOfName("Hips")
            if (hips >= 0) selectBone(hips)
        }
    }

    fun changeTool(newTool: ToolMode) {
        if (tool != newTool) StudioHaptics.tick()
        tool = newTool
        runtime.tool = newTool
        if (newTool == ToolMode.IK) {
            playing = false
            runtime.playing = false
        }
        refreshGizmo()
    }

    fun changeElementMode(newMode: ElementMode) {
        elementMode = newMode
        runtime.elementMode = newMode
        selection = selection.copy(
            elements = newMode,
            vertices = if (newMode == ElementMode.VERTEX || newMode == ElementMode.EDGE) selection.vertices else emptySet(),
            faces = if (newMode == ElementMode.FACE) selection.faces else emptySet()
        )
        runtime.selection = selection
        runtime.selectionRevision++
        refreshGizmo()
    }

    fun openInspectorTab(tab: InspectorTab) {
        inspectorTab = tab
    }

    fun openSettings() { showSettings = true }
    fun closeSettings() { showSettings = false }

    fun completeOnboarding(startEmpty: Boolean) {
        StudioPreferences.updateOnboardingComplete(true)
        showOnboarding = false
        if (startEmpty) newScene(empty = true) else frameAll()
    }

    fun replayOnboarding() {
        StudioPreferences.updateOnboardingComplete(false)
        showSettings = false
        showOnboarding = true
    }

    fun resetDemoScene() {
        showSettings = false
        newScene(empty = false)
    }

    fun clearAutosave() {
        autosaveJob?.cancel()
        runCatching { autosaveFile.delete() }
        lastAutosave = null
        showMessage("Autosave cleared")
    }

    fun toggleTimeline() { showTimeline = !showTimeline }
    fun toggleGraphEditor() {
        showGraphEditor = !showGraphEditor
        if (showGraphEditor) showTimeline = true
    }

    fun openGraphEditor() {
        showGraphEditor = true
        showTimeline = true
    }

    fun changeSkeletonVisibility(value: Boolean) {
        showSkeleton = value
        runtime.showSkeleton = value
    }

    fun changeGridVisibility(value: Boolean) {
        showGrid = value
        runtime.showGrid = value
    }

    fun changeWireframe(value: Boolean) {
        wireframe = value
        runtime.wireframe = value
    }

    fun changeDynamicShadows(value: Boolean) {
        StudioPreferences.updateDynamicShadows(value)
        runtime.dynamicShadows = value
    }

    fun frameSelection() {
        val bounds = selectedObject?.bounds() ?: scene.skeleton?.let { scene.frameBounds() } ?: scene.frameBounds()
        runtime.camera.frame(bounds)
    }

    fun frameScene() {
        runtime.camera.frame(scene.frameBounds())
    }

    fun setCameraView(view: CameraView) {
        when (view) {
            CameraView.PERSPECTIVE -> { runtime.camera.yawDeg = 26f; runtime.camera.pitchDeg = 13f }
            CameraView.FRONT -> { runtime.camera.yawDeg = 0f; runtime.camera.pitchDeg = 0f }
            CameraView.SIDE -> { runtime.camera.yawDeg = 90f; runtime.camera.pitchDeg = 0f }
            CameraView.TOP -> { runtime.camera.yawDeg = 0f; runtime.camera.pitchDeg = 86f }
            CameraView.BACK -> { runtime.camera.yawDeg = 180f; runtime.camera.pitchDeg = 0f }
        }
    }

    fun setSnap(value: Boolean) { snapEnabled = value }

    fun setPlaybackSpeed(value: Float) { speed = value.coerceIn(0.1f, 3f) }

    fun notify(message: String) = showMessage(message)

    // -------------------------------------------------------------------- picking

    fun currentPose(): Pose {
        val s = scene
        val skeleton = s.skeleton ?: return Pose(0)
        synchronized(runtime.lock) {
            runtime.poseOverride?.let { return it }
            return PoseSampler.sample(s, time)
        }
    }

    fun togglePlay() {
        playing = !playing
        runtime.playing = playing
        if (playing) {
            runtime.poseOverride = null
        }
        refreshGizmo()
    }

    fun setPlayback(value: Boolean) {
        playing = value
        runtime.playing = value
        if (value) runtime.poseOverride = null
        refreshGizmo()
    }

    fun seekTo(newTime: Float) {
        val clip = currentClip()
        val duration = clip?.duration ?: 0f
        time = newTime.coerceIn(0f, duration)
        runtime.time = time
        runtime.poseOverride = null
        poseRevision++
        refreshGizmo()
    }

    fun advance(dt: Float) {
        if (!playing) return
        val clip = currentClip() ?: return
        if (clip.duration <= 0f) return
        var t = time + dt * speed
        if (t > clip.duration) {
            if (clip.loop) t %= clip.duration
            else {
                t = clip.duration
                playing = false
                runtime.playing = false
                refreshGizmo()
            }
        }
        time = t
        runtime.time = t
    }

    /** Called by the Compose frame clock; keeps GL playback smooth even while the inspector is idle. */
    fun tick(dt: Float) {
        if (playing) advance(dt.coerceIn(0f, 0.1f))
    }

    fun currentClip(): AnimationClip? = scene.clips.getOrNull(scene.activeClip)

    val selectedObject: SceneObject?
        get() = scene.objects.firstOrNull { it.id == selection.objectId }

    val selectedBoneName: String
        get() = scene.skeleton?.bones?.getOrNull(selection.bone)?.name ?: "—"

    val skeleton: com.studio3dpro.engine.scene.Skeleton?
        get() = scene.skeleton

    fun selectObject(id: Long?) {
        selection = SelectionState(
            objectId = id,
            elements = if (mode == EditorMode.EDIT) elementMode else ElementMode.OBJECT
        )
        runtime.selection = selection
        runtime.selectionRevision++
        refreshGizmo()
    }

    fun selectBone(index: Int) {
        if (index !in 0 until (scene.skeleton?.size ?: 0)) return
        selection = selection.copy(bone = index, elements = ElementMode.BONE)
        runtime.selection = selection
        runtime.selectionRevision++
        if (weightPaintActive) runtime.weightPaintBone = index
        if (mode != EditorMode.RIG && mode != EditorMode.ANIMATE) {
            mode = EditorMode.RIG
            runtime.editorMode = mode
        }
        refreshGizmo()
    }

    fun selectVertex(index: Int, additive: Boolean) {
        val mesh = selectedObject?.mesh ?: return
        if (index !in 0 until mesh.vertexCount) return
        val set = if (additive) LinkedHashSet(selection.vertices) else LinkedHashSet()
        if (additive && index in set) set.remove(index) else set.add(index)
        selection = selection.copy(vertices = set, elements = ElementMode.VERTEX)
        runtime.selection = selection
        runtime.selectionRevision++
        refreshGizmo()
    }

    fun selectFace(index: Int, additive: Boolean) {
        val mesh = selectedObject?.mesh ?: return
        if (index !in 0 until mesh.faceCount) return
        val set = if (additive) LinkedHashSet(selection.faces) else LinkedHashSet()
        if (additive && index in set) set.remove(index) else set.add(index)
        selection = selection.copy(faces = set, elements = ElementMode.FACE)
        runtime.selection = selection
        runtime.selectionRevision++
        refreshGizmo()
    }

    fun selectAllElements() {
        val mesh = selectedObject?.mesh ?: return
        when (elementMode) {
            ElementMode.VERTEX, ElementMode.EDGE -> selection = selection.copy(vertices = (0 until mesh.vertexCount).toSet())
            ElementMode.FACE -> selection = selection.copy(faces = (0 until mesh.faceCount).toSet())
            else -> return
        }
        runtime.selection = selection
        runtime.selectionRevision++
        refreshGizmo()
    }

    fun clearElementSelection() {
        selection = selection.copy(vertices = emptySet(), faces = emptySet())
        runtime.selection = selection
        runtime.selectionRevision++
        refreshGizmo()
    }

    private fun dp(value: Float): Float = value * getApplication<Application>().resources.displayMetrics.density

    fun onViewportTap(x: Float, y: Float) {
        val w = runtime.viewportWidth
        val h = runtime.viewportHeight
        if (w < 2 || h < 2) return
        if (mode == EditorMode.RIG || mode == EditorMode.ANIMATE) {
            val bone = pickBone(x, y)
            if (bone >= 0) {
                selectBone(bone)
                return
            }
        }
        if (mode == EditorMode.EDIT) {
            val current = selectedObject
            val hit = current?.let { raycastObject(it, x, y) }
            if (hit != null && hit.first == current.id) {
                when (elementMode) {
                    ElementMode.FACE -> selectFace(hit.second.face, false)
                    ElementMode.VERTEX, ElementMode.EDGE -> {
                        val objectHit = scene.objects.first { it.id == hit.first }
                        if (elementMode == ElementMode.VERTEX) {
                            val v = nearestVertex(objectHit, x, y)
                            if (v >= 0) selectVertex(v, false)
                        } else {
                            val edge = nearestEdge(objectHit, x, y)
                            if (edge != null) {
                                selection = selection.copy(vertices = edge, faces = emptySet(), elements = ElementMode.EDGE)
                                runtime.selection = selection
                                runtime.selectionRevision++
                                refreshGizmo()
                            }
                        }
                    }
                    else -> {}
                }
                return
            }
            val best = pickObject(x, y)
            if (best != null) {
                selectObject(best.id)
                return
            }
            clearElementSelection()
            return
        }
        val best = pickObject(x, y)
        if (best != null) {
            selectObject(best.id)
            if (mode == EditorMode.OBJECT) inspectorTab = InspectorTab.OBJECT
        } else {
            selectObject(null)
        }
    }

    private fun pickObject(x: Float, y: Float): SceneObject? {
        var best: SceneObject? = null
        var bestDist = Float.MAX_VALUE
        for (obj in scene.objects) {
            if (!obj.visible || obj.mesh == null) continue
            val hit = raycastObject(obj, x, y) ?: continue
            if (hit.second.distance < bestDist) {
                bestDist = hit.second.distance
                best = obj
            }
        }
        return best
    }

    /** Returns object id and the mesh-space hit. */
    private fun raycastObject(obj: SceneObject, x: Float, y: Float): Pair<Long, com.studio3dpro.engine.mesh.RayHit>? {
        val mesh = obj.mesh ?: return null
        val ray = runtime.camera.rayFromScreen(x, y, runtime.viewportWidth, runtime.viewportHeight)
        val inv = obj.transform.matrix().inverse()
        val localRay = Ray(inv.transformPoint(ray.origin), inv.transformDirection(ray.direction).normalized())
        val hit = mesh.raycastLocal(localRay) ?: return null
        return obj.id to hit
    }

    private fun nearestVertex(obj: SceneObject, x: Float, y: Float): Int {
        val mesh = obj.mesh ?: return -1
        val model = obj.transform.matrix()
        var best = -1
        var bestD = dp(30f)
        for (i in 0 until mesh.vertexCount) {
            val p = model.transformPoint(mesh.vertices[i])
            val d = runtime.camera.screenDistanceTo(p, x, y, runtime.viewportWidth, runtime.viewportHeight)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    private fun nearestEdge(obj: SceneObject, x: Float, y: Float): Set<Int>? {
        val mesh = obj.mesh ?: return null
        val model = obj.transform.matrix()
        var best: Set<Int>? = null
        var bestD = dp(26f)
        for (e in mesh.edges().values) {
            val a = model.transformPoint(mesh.vertices[e.a])
            val b = model.transformPoint(mesh.vertices[e.b])
            val sa = runtime.camera.projectPoint(a, runtime.viewportWidth, runtime.viewportHeight) ?: continue
            val sb = runtime.camera.projectPoint(b, runtime.viewportWidth, runtime.viewportHeight) ?: continue
            val d = distanceToSegment(x, y, sa.x, sa.y, sb.x, sb.y)
            if (d < bestD) { bestD = d; best = setOf(e.a, e.b) }
        }
        return best
    }

    private fun pickBone(x: Float, y: Float): Int {
        val skeleton = scene.skeleton ?: return -1
        val pose = currentPose()
        val mats = skeleton.poseWorldMatrices(pose)
        var best = -1
        var bestD = dp(34f)
        val w = runtime.viewportWidth
        val h = runtime.viewportHeight
        for (i in skeleton.bones.indices) {
            val head = mats[i].translation()
            val tail = skeleton.poseTailWorld(i, pose, mats)
            val sh = runtime.camera.projectPoint(head, w, h) ?: continue
            val st = runtime.camera.projectPoint(tail, w, h) ?: continue
            val d = distanceToSegment(x, y, sh.x, sh.y, st.x, st.y)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 < 1e-6f) return kotlin.math.sqrt((px - ax) * (px - ax) + (py - ay) * (py - ay))
        val t = (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
        val qx = ax + dx * t
        val qy = ay + dy * t
        return kotlin.math.sqrt((px - qx) * (px - qx) + (py - qy) * (py - qy))
    }

    // --------------------------------------------------------------------- gizmo

    fun gizmoScale(): Float = runtime.camera.distance * 0.095f + 0.05f

    fun refreshGizmo() {
        val obj = selectedObject
        val skeleton = scene.skeleton
        var visible = selection.hasSelection && mode != EditorMode.SCULPT && tool != ToolMode.SELECT
        var pivot = Vec3.ZERO
        var gmode = tool
        if ((mode == EditorMode.RIG || mode == EditorMode.ANIMATE) && skeleton != null) {
            if (selection.bone !in skeleton.bones.indices) {
                visible = false
            } else {
                val pose = currentPose()
                val mats = skeleton.poseWorldMatrices(pose)
                pivot = if (tool == ToolMode.IK) skeleton.poseTailWorld(selection.bone, pose, mats)
                else mats[selection.bone].translation()
                if (tool == ToolMode.MOVE || tool == ToolMode.SCALE) gmode = ToolMode.ROTATE
                if (playing) visible = false
                runtime.ikTarget = if (tool == ToolMode.IK) pivot else null
            }
        } else if (mode == EditorMode.EDIT) {
            val mesh = obj?.mesh
            if (mesh == null) visible = false
            else if (selection.vertices.isNotEmpty()) {
                var sum = Vec3.ZERO
                for (v in selection.vertices) if (v in mesh.vertices.indices) sum += mesh.vertices[v]
                val center = sum / selection.vertices.size.toFloat()
                pivot = obj.transform.matrix().transformPoint(center)
            } else if (selection.faces.isNotEmpty()) {
                var sum = Vec3.ZERO
                var n = 0
                for (f in selection.faces) if (f in mesh.faces.indices) {
                    sum += mesh.faceCentroid(f); n++
                }
                if (n > 0) pivot = obj.transform.matrix().transformPoint(sum / n.toFloat()) else visible = false
            } else visible = false
            runtime.ikTarget = null
        } else {
            if (obj == null) visible = false
            else {
                val b = obj.bounds()
                pivot = if (b != null) obj.transform.matrix().transformPoint(obj.mesh?.bounds()?.center ?: Vec3.ZERO) else obj.transform.position
            }
            runtime.ikTarget = null
        }
        runtime.gizmo = GizmoState(visible, pivot, gmode, runtime.gizmo.selectedAxis)
    }

    fun grabGizmo(x: Float, y: Float): Boolean {
        val g = runtime.gizmo
        if (!g.visible) return false
        val w = runtime.viewportWidth
        val h = runtime.viewportHeight
        val pivotScreen = runtime.camera.projectPoint(g.pivot, w, h) ?: return false
        if (g.mode == ToolMode.IK) {
            val d = distanceToSegment(x, y, pivotScreen.x, pivotScreen.y, pivotScreen.x, pivotScreen.y)
            if (d <= dp(36f)) {
                beginGizmoDrag(GizmoAxis.CENTER, x, y)
                return true
            }
            return false
        }
        if (g.mode == ToolMode.ROTATE) {
            val radius = gizmoScale() * 0.52f
            for ((axis, axisId) in listOf(Vec3.RIGHT to GizmoAxis.X, Vec3.UP to GizmoAxis.Y, Vec3(0f, 0f, 1f) to GizmoAxis.Z)) {
                val pts = sampleCircle(g.pivot, axis, radius, 40)
                var prev: Vec3? = null
                var best = dp(20f)
                var hit = false
                for (p in pts) {
                    val s = runtime.camera.projectPoint(p, w, h) ?: continue
                    if (prev != null) {
                        if (distanceToSegment(x, y, prev.x, prev.y, s.x, s.y) < best) { hit = true }
                    }
                    prev = s
                }
                if (hit) {
                    beginGizmoDrag(axisId, x, y)
                    return true
                }
            }
            return false
        }
        val len = gizmoScale() * 0.72f
        for ((axis, axisId) in listOf(Vec3.RIGHT to GizmoAxis.X, Vec3.UP to GizmoAxis.Y, Vec3(0f, 0f, 1f) to GizmoAxis.Z)) {
            val tip = g.pivot + axis * len
            val st = runtime.camera.projectPoint(tip, w, h) ?: continue
            val d = distanceToSegment(x, y, pivotScreen.x, pivotScreen.y, st.x, st.y)
            if (d <= dp(26f)) {
                beginGizmoDrag(axisId, x, y)
                return true
            }
        }
        return false
    }

    private fun beginGizmoDrag(axis: GizmoAxis, x: Float, y: Float) {
        if (!gestureUndoPushed) {
            recordUndo(if (mode == EditorMode.EDIT) "Transform Elements" else if (mode == EditorMode.RIG || mode == EditorMode.ANIMATE) "Pose Bone" else "Transform Object")
            gestureUndoPushed = true
        }
        dragAxis = axis
        dragMode = runtime.gizmo.mode
        dragPivot = runtime.gizmo.pivot
        dragObjectId = selection.objectId
        dragInitialVertices.clear()
        selectedObject?.let { obj ->
            dragInitialPosition = obj.transform.position
            dragInitialRotation = obj.transform.rotation
            dragInitialScale = obj.transform.scale
            if (mode == EditorMode.EDIT) {
                val mesh = obj.mesh
                if (mesh != null) {
                    val affected = LinkedHashSet(selection.vertices)
                    for (f in selection.faces) mesh.faces.getOrNull(f)?.forEach { affected.add(it) }
                    for (v in affected) if (v in mesh.vertices.indices) dragInitialVertices[v] = mesh.vertices[v]
                }
            }
        }
        if (mode == EditorMode.RIG && tool == ToolMode.IK) {
            ikStartPose = poseForEditing().copy()
            ikTargetBone = selection.bone
            runtime.poseOverride = ikStartPose
        }
        val ray = runtime.camera.rayFromScreen(x, y, runtime.viewportWidth, runtime.viewportHeight)
        if (dragMode == ToolMode.ROTATE) {
            val (_, angle) = rayAngleOnPlane(ray, dragPivot, axisVector(axis))
            dragStartAngle = angle
        } else {
            val param = axisParam(ray, dragPivot, axisVector(axis))
            dragStartParam = param ?: 0f
        }
        runtime.gizmo = runtime.gizmo.copy(selectedAxis = axis)
    }

    fun dragGizmo(x: Float, y: Float) {
        if (!gestureUndoPushed) return
        val w = runtime.viewportWidth
        val h = runtime.viewportHeight
        val ray = runtime.camera.rayFromScreen(x, y, w, h)
        if (mode == EditorMode.RIG || mode == EditorMode.ANIMATE) {
            if (tool == ToolMode.IK) {
                val base = ikStartPose ?: return
                val cameraForward = (runtime.camera.target - runtime.camera.eye()).normalized()
                val planeHit = intersectPlane(ray, dragPivot, cameraForward) ?: return
                val pose = base.copy()
                val error = Ik.solveCcd(
                    scene.skeleton!!, pose, ikTargetBone, planeHit,
                    Ik.clampChainLength(scene.skeleton!!, ikTargetBone, ikChainLength), iterations = 20
                )
                runtime.poseOverride = pose
                runtime.ikTarget = planeHit
                runtime.gizmo = runtime.gizmo.copy(pivot = planeHit)
                poseRevision++
                return
            }
            if (dragMode != ToolMode.ROTATE) return
            val axis = axisVector(dragAxis)
            val (point, angle) = rayAngleOnPlane(ray, dragPivot, axis)
            var delta = angle - dragStartAngle
            if (delta > Math.PI) delta -= (2f * Math.PI).toFloat()
            if (delta < -Math.PI) delta += (2f * Math.PI).toFloat()
            if (snapEnabled) delta = (delta / degToRad(15f)).roundToInt() * degToRad(15f)
            rotateSelectedBoneWorld(axis, delta)
            dragStartAngle = angle
            return
        }
        val obj = dragObjectId?.let { scene.findObject(it) } ?: return
        if (dragMode == ToolMode.ROTATE) {
            val axis = axisVector(dragAxis)
            val (_, angle) = rayAngleOnPlane(ray, dragPivot, axis)
            var delta = angle - dragStartAngle
            if (delta > Math.PI) delta -= (2f * Math.PI).toFloat()
            if (delta < -Math.PI) delta += (2f * Math.PI).toFloat()
            if (snapEnabled) delta = (delta / degToRad(15f)).roundToInt() * degToRad(15f)
            if (mode == EditorMode.EDIT) {
                rotateSelectionWorld(axis, delta)
            } else {
                obj.transform.rotation = (Quat.axisAngle(axis, delta) * dragInitialRotation).normalized()
            }
            dragStartAngle = angle
        } else if (dragMode == ToolMode.SCALE) {
            val param = axisParam(ray, dragPivot, axisVector(dragAxis)) ?: return
            var factor = 1f + (param - dragStartParam) / (gizmoScale() * 1.2f)
            factor = factor.coerceIn(0.05f, 12f)
            if (snapEnabled) factor = (factor * 10f).roundToInt() / 10f
            if (mode == EditorMode.EDIT) {
                scaleSelectionWorld(axisVector(dragAxis), factor)
            } else {
                val axis = axisVector(dragAxis)
                obj.transform.scale = Vec3(
                    if (abs(axis.x) > 0.5f) (dragInitialScale.x * factor).coerceAtLeast(0.02f) else dragInitialScale.x,
                    if (abs(axis.y) > 0.5f) (dragInitialScale.y * factor).coerceAtLeast(0.02f) else dragInitialScale.y,
                    if (abs(axis.z) > 0.5f) (dragInitialScale.z * factor).coerceAtLeast(0.02f) else dragInitialScale.z
                )
            }
        } else if (dragMode == ToolMode.MOVE) {
            val param = axisParam(ray, dragPivot, axisVector(dragAxis)) ?: return
            var delta = param - dragStartParam
            if (snapEnabled) delta = (delta * 10f).roundToInt() / 10f
            val offset = axisVector(dragAxis) * delta
            if (mode == EditorMode.EDIT) {
                translateSelection(offset)
            } else {
                obj.transform.position = dragInitialPosition + offset
            }
        }
        runtime.scene = scene
        sceneRevision++
        runtime.gizmo = runtime.gizmo.copy(pivot = if (mode == EditorMode.EDIT) runtime.gizmo.pivot else obj.transform.matrix().transformPoint(obj.mesh?.bounds()?.center ?: Vec3.ZERO))
    }

    fun endGizmoDrag() {
        if (!gestureUndoPushed) return
        gestureUndoPushed = false
        if (mode == EditorMode.RIG && tool == ToolMode.IK) {
            runtime.ikTarget = null
            poseRevision++
        }
        dragInitialVertices.clear()
        ikStartPose = null
        val next = scene.copy()
        scene = next
        runtime.replaceScene(next)
        sceneRevision++
        lastAction = "Transform"
        refreshGizmo()
        scheduleAutosave()
    }

    private fun axisVector(axis: GizmoAxis): Vec3 = when (axis) {
        GizmoAxis.X -> Vec3.RIGHT
        GizmoAxis.Y -> Vec3.UP
        GizmoAxis.Z -> Vec3(0f, 0f, 1f)
        GizmoAxis.CENTER -> Vec3.UP
    }

    private fun axisParam(ray: Ray, pivot: Vec3, axis: Vec3): Float? {
        val w0 = pivot - ray.origin
        val a = 1f
        val b = axis dot ray.direction
        val c = 1f
        val d = axis dot w0
        val e = ray.direction dot w0
        val denom = a * c - b * b
        if (abs(denom) < 1e-5f) return null
        return (b * e - c * d) / denom
    }

    private fun intersectPlane(ray: Ray, pivot: Vec3, normal: Vec3): Vec3? {
        val denom = ray.direction dot normal
        if (abs(denom) < 1e-5f) return null
        val t = ((pivot - ray.origin) dot normal) / denom
        if (t <= 0f) return null
        return ray.at(t)
    }

    private fun rayAngleOnPlane(ray: Ray, pivot: Vec3, normal: Vec3): Pair<Vec3, Float> {
        val hit = intersectPlane(ray, pivot, normal) ?: return pivot to dragStartAngle
        val v = hit - pivot
        if (v.lengthSq() < 1e-8f) return hit to dragStartAngle
        val up = if (abs(normal.y) > 0.9f) Vec3(0f, 0f, 1f) else Vec3.UP
        val a = (up cross normal).normalized()
        val b = normal cross a
        val angle = kotlin.math.atan2(v dot b, v dot a)
        return hit to angle
    }

    private fun sampleCircle(center: Vec3, normal: Vec3, radius: Float, segments: Int): List<Vec3> {
        val up = if (abs(normal.y) > 0.9f) Vec3.RIGHT else Vec3.UP
        val a = (up cross normal).normalized()
        val b = normal cross a
        val out = ArrayList<Vec3>(segments + 1)
        for (i in 0..segments) {
            val ang = 2f * Math.PI.toFloat() * i / segments
            out.add(center + a * (kotlin.math.cos(ang) * radius) + b * (kotlin.math.sin(ang) * radius))
        }
        return out
    }

    // ---------------------------------------------------------------- edit tools

    private fun translateSelection(worldOffset: Vec3) {
        val obj = selectedObject ?: return
        val mesh = obj.mesh ?: return
        if (dragInitialVertices.isEmpty()) return
        val inv = obj.transform.matrix().inverse()
        val localDelta = inv.transformDirection(worldOffset)
        for ((v, original) in dragInitialVertices) {
            if (v in mesh.vertices.indices) mesh.vertices[v] = original + localDelta
        }
        mesh.markDirty()
        runtime.markGeometryChanged()
    }

    private fun rotateSelectionWorld(axis: Vec3, delta: Float) {
        val obj = selectedObject ?: return
        val mesh = obj.mesh ?: return
        if (dragInitialVertices.isEmpty()) return
        val inv = obj.transform.matrix().inverse()
        val localPivot = inv.transformPoint(dragPivot)
        val localAxis = inv.transformDirection(axis).normalized()
        val initial = dragInitialVertices
        for ((v, p) in initial) {
            val rel = p - localPivot
            val q = Quat.axisAngle(localAxis, delta)
            mesh.vertices[v] = localPivot + q.rotate(rel)
        }
        mesh.markDirty()
        runtime.markGeometryChanged()
    }

    private fun scaleSelectionWorld(axis: Vec3, factor: Float) {
        val obj = selectedObject ?: return
        val mesh = obj.mesh ?: return
        val inv = obj.transform.matrix().inverse()
        val localPivot = inv.transformPoint(dragPivot)
        val localAxis = inv.transformDirection(axis).normalized()
        for ((v, p) in dragInitialVertices) {
            val rel = p - localPivot
            val along = rel dot localAxis
            val newRel = rel + localAxis * (along * (factor - 1f))
            mesh.vertices[v] = localPivot + newRel
        }
        mesh.markDirty()
        runtime.markGeometryChanged()
    }

    fun beginViewportManipulation(x: Float, y: Float): Boolean {
        if (grabGizmo(x, y)) return true
        if (mode == EditorMode.SCULPT) {
            return beginSculpt(x, y)
        }
        if (weightPaintActive && (mode == EditorMode.RIG || mode == EditorMode.EDIT)) {
            return beginWeightPaint(x, y)
        }
        return false
    }

    fun isManipulating(): Boolean = gestureUndoPushed || sculpting || painting

    fun dragViewportManipulation(x: Float, y: Float) {
        if (gestureUndoPushed) dragGizmo(x, y)
        else if (sculpting) sculptStroke(x, y)
        else if (painting) weightStroke(x, y)
    }

    fun endViewportManipulation() {
        if (gestureUndoPushed) {
            endGizmoDrag()
        } else if (sculpting) {
            endSculpt()
        } else if (painting) {
            endWeightPaint()
        }
    }

    // --------------------------------------------------------------------- sculpt

    private fun beginSculpt(x: Float, y: Float): Boolean {
        val obj = selectedObject ?: return false
        val hit = raycastObject(obj, x, y) ?: return false
        recordUndo("Sculpt")
        sculptObjectId = obj.id
        sculpting = true
        val inv = obj.transform.matrix().inverse()
        val center = inv.transformPoint(hit.second.point)
        val normal = inv.transformDirection(hit.second.normal).normalized()
        Sculpt.stroke(
            obj.mesh!!, center, localRadius(obj, sculptRadius), sculptStrength,
            sculptBrush.toEngine(), normal, symmetryX
        )
        runtime.markGeometryChanged()
        return true
    }

    private fun sculptStroke(x: Float, y: Float) {
        val obj = sculptObjectId?.let { scene.findObject(it) } ?: return
        val hit = raycastObject(obj, x, y) ?: return
        val inv = obj.transform.matrix().inverse()
        val center = inv.transformPoint(hit.second.point)
        val normal = inv.transformDirection(hit.second.normal).normalized()
        Sculpt.stroke(
            obj.mesh!!, center, localRadius(obj, sculptRadius), sculptStrength,
            sculptBrush.toEngine(), normal, symmetryX
        )
        runtime.markGeometryChanged()
        sceneRevision++
    }

    private fun endSculpt() {
        if (!sculpting) return
        sculpting = false
        sculptObjectId = null
        val next = scene.copy()
        scene = next
        runtime.replaceScene(next)
        sceneRevision++
        scheduleAutosave()
    }

    private fun SculptBrush.toEngine(): SculptMode = when (this) {
        SculptBrush.DRAW -> SculptMode.DRAW
        SculptBrush.INFLATE -> SculptMode.INFLATE
        SculptBrush.SMOOTH -> SculptMode.SMOOTH
        SculptBrush.FLATTEN -> SculptMode.FLATTEN
        SculptBrush.PINCH -> SculptMode.PINCH
    }

    private fun localRadius(obj: SceneObject, worldRadius: Float): Float {
        val s = obj.transform.scale
        val avg = (abs(s.x) + abs(s.y) + abs(s.z)) / 3f
        return (worldRadius / avg.coerceAtLeast(1e-3f)).coerceAtLeast(1e-4f)
    }

    fun undoableSculptSetup() {
        recordUndo("Sculpt")
    }

    // ------------------------------------------------------------------ weight paint

    fun toggleWeightPaint(): Boolean {
        if (scene.skeleton == null) {
            showMessage("Create or import a rig first.")
            return false
        }
        if (selection.bone < 0) {
            showMessage("Select a bone to paint weights.")
            return false
        }
        weightPaintActive = !weightPaintActive
        if (weightPaintActive) {
            mode = EditorMode.RIG
            runtime.editorMode = mode
            tool = ToolMode.ROTATE
            runtime.tool = tool
            runtime.showSkeleton = true
            playing = false
            runtime.playing = false
            runtime.weightPaintBone = selection.bone
            refreshGizmo()
        } else {
            runtime.weightPaintBone = -1
        }
        return weightPaintActive
    }

    private fun beginWeightPaint(x: Float, y: Float): Boolean {
        val obj = selectedObject?.takeIf { it.mesh?.skin != null } ?: return false
        val hit = raycastObject(obj, x, y) ?: return false
        recordUndo("Paint Weights")
        weightObjectId = obj.id
        painting = true
        applyWeightAt(obj, hit.second.point)
        return true
    }

    private fun weightStroke(x: Float, y: Float) {
        val obj = weightObjectId?.let { scene.findObject(it) } ?: return
        val hit = raycastObject(obj, x, y) ?: return
        applyWeightAt(obj, hit.second.point)
    }

    private fun applyWeightAt(obj: SceneObject, worldPoint: Vec3) {
        val mesh = obj.mesh ?: return
        val local = obj.transform.matrix().inverse().transformPoint(worldPoint)
        val bone = selection.bone
        if (bone < 0) return
        Skinning.paint(
            mesh, local, localRadius(obj, weightRadius), bone, weightStrength,
            Skinning.PaintMode.ADD
        )
        runtime.markGeometryChanged()
        sceneRevision++
    }

    private fun endWeightPaint() {
        if (!painting) return
        painting = false
        weightObjectId = null
        val next = scene.copy()
        scene = next
        runtime.replaceScene(next)
        sceneRevision++
        scheduleAutosave()
    }

    // ------------------------------------------------------------------ primitives

    fun addPrimitive(kind: PrimitiveKind) {
        StudioHaptics.tick()
        val size = 1f
        val mesh = when (kind) {
            PrimitiveKind.CUBE -> Primitives.cube(size)
            PrimitiveKind.SPHERE -> Primitives.uvSphere(0.5f, 32, 18)
            PrimitiveKind.CYLINDER -> Primitives.cylinder(0.45f, 0.45f, 1.1f, 32)
            PrimitiveKind.CONE -> Primitives.cone(0.5f, 1.1f, 32)
            PrimitiveKind.TORUS -> Primitives.torus(0.5f, 0.17f, 32, 16)
            PrimitiveKind.PLANE -> Primitives.plane(2f, 1)
        }
        val center = runtime.camera.target
        val y = if (kind == PrimitiveKind.PLANE) 0.001f else mesh.bounds().size.y * 0.5f
        mesh.transform(Mat4.translation(Vec3(center.x, y, center.z)))
        UvProjection.project(mesh, UvProjection.Mode.BOX)
        val material = Material(
            name = "Studio Clay",
            baseColor = Vec3(0.78f, 0.72f, 0.62f),
            roughness = 0.6f
        )
        val baseName = when (kind) {
            PrimitiveKind.CUBE -> "Cube"
            PrimitiveKind.SPHERE -> "Sphere"
            PrimitiveKind.CYLINDER -> "Cylinder"
            PrimitiveKind.CONE -> "Cone"
            PrimitiveKind.TORUS -> "Torus"
            PrimitiveKind.PLANE -> "Plane"
        }
        editScene("Add $baseName", geometry = true) { s ->
            var name = baseName
            var n = 2
            while (s.objects.any { it.name == name }) { name = "$baseName $n"; n++ }
            val obj = s.addObject(name, mesh, material)
            selection = SelectionState(objectId = obj.id)
            runtime.selection = selection
        }
        mode = EditorMode.OBJECT
        runtime.editorMode = mode
        inspectorTab = InspectorTab.OBJECT
        refreshGizmo()
        showMessage("$baseName added")
    }

    fun duplicateSelectedObject() {
        val obj = selectedObject ?: return
        val mesh = obj.mesh ?: return
        val copy = obj.copy(scene.nextObjectId)
        copy.name = "${obj.name} Copy"
        copy.transform.position += Vec3(0.25f, 0f, 0f)
        copy.mesh = mesh.clone()
        editScene("Duplicate", geometry = true) { s ->
            s.nextObjectId++
            s.objects.add(copy)
            selection = SelectionState(objectId = copy.id)
            runtime.selection = selection
        }
        refreshGizmo()
    }

    fun deleteSelectedObject() {
        val obj = selectedObject ?: return
        editScene("Delete Object", geometry = true) { s ->
            s.objects.removeAll { it.id == obj.id }
            selection = SelectionState()
            runtime.selection = selection
        }
        refreshGizmo()
    }

    fun renameObject(id: Long, name: String) {
        val clean = name.trim().take(48)
        if (clean.isEmpty()) return
        editScene("Rename", geometry = false, coalesce = true) { s ->
            s.findObject(id)?.name = clean
        }
    }

    fun toggleObjectVisibility(id: Long) {
        editScene("Visibility", geometry = true, coalesce = true) { s ->
            s.findObject(id)?.let { it.visible = !it.visible }
        }
        runtime.markGeometryChanged()
    }

    fun updateObjectPosition(id: Long, position: Vec3) {
        val obj = scene.findObject(id) ?: return
        obj.transform.position = position
        runtime.scene = scene
        refreshGizmo()
        sceneRevision++
    }

    fun updateObjectRotation(id: Long, rotation: Quat) {
        val obj = scene.findObject(id) ?: return
        obj.transform.rotation = rotation
        runtime.scene = scene
        refreshGizmo()
        sceneRevision++
    }

    fun updateObjectScale(id: Long, scale: Vec3) {
        val obj = scene.findObject(id) ?: return
        obj.transform.scale = Vec3(
            scale.x.coerceIn(0.01f, 100f),
            scale.y.coerceIn(0.01f, 100f),
            scale.z.coerceIn(0.01f, 100f)
        )
        runtime.scene = scene
        refreshGizmo()
        sceneRevision++
    }

    fun beginNumericEdit(label: String) {
        if (!transformGestureActive) {
            recordUndo(label)
            transformGestureActive = true
        }
    }

    fun finishNumericEdit() {
        if (!transformGestureActive) return
        transformGestureActive = false
        val next = scene.copy()
        scene = next
        runtime.replaceScene(next)
        sceneRevision++
        scheduleAutosave()
    }

    fun updateMaterial(update: (Material) -> Unit) {
        val obj = selectedObject ?: return
        recordUndo("Material", coalesce = true)
        update(obj.material)
        runtime.scene = scene
        sceneRevision++
        scheduleAutosave()
    }

    fun setBaseColor(color: Vec3) {
        updateMaterial { it.baseColor = color }
    }

    fun assignTexture(bytes: ByteArray, mime: String = "image/png") {
        val obj = selectedObject ?: return
        recordUndo("Texture")
        obj.material.baseColorTexture = TextureData(mime, bytes, "Imported")
        runtime.markGeometryChanged()
        sceneRevision++
        scheduleAutosave()
        showMessage("Texture assigned")
    }

    fun clearTexture() {
        updateMaterial { it.baseColorTexture = null }
        runtime.markGeometryChanged()
    }

    fun importTexture(uri: Uri) {
        executeBusy("Loading texture…") {
            val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Cannot read texture")
            val mime = getApplication<Application>().contentResolver.getType(uri) ?: "image/png"
            withContext(Dispatchers.Main) { assignTexture(bytes, mime) }
        }
    }

    fun captureScreenshot(uri: Uri) {
        executeBusy("Capturing viewport…") {
            val bitmap = withTimeout(6_000) {
                suspendCancellableCoroutine<Bitmap> { cont ->
                    runtime.captureCallback = { shot ->
                        runtime.captureCallback = null
                        cont.resume(shot)
                    }
                    runtime.captureRequested = true
                }
            }
            val resolver = getApplication<Application>().contentResolver
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: throw IllegalStateException("Cannot open destination")
            bitmap.recycle()
            withContext(Dispatchers.Main) { showMessage("Viewport PNG saved") }
        }
    }

    fun renameMaterial(name: String) {
        updateMaterial { it.name = name.take(40) }
    }

    /** Applies a PBR preset by name; used by the material inspector. */
    fun applyMaterialPreset(label: String) {
        val preset = when (label) {
            "Brushed Steel" -> Triple(Vec3(0.63f, 0.65f, 0.69f), 1f, 0.32f)
            "Obsidian" -> Triple(Vec3(0.07f, 0.08f, 0.10f), 0.25f, 0.2f)
            "Warm Skin" -> Triple(Vec3(0.76f, 0.53f, 0.43f), 0f, 0.55f)
            "Polished Gold" -> Triple(Vec3(0.95f, 0.71f, 0.29f), 1f, 0.16f)
            "Porcelain" -> Triple(Vec3(0.92f, 0.9f, 0.86f), 0f, 0.14f)
            else -> Triple(Vec3(0.78f, 0.72f, 0.62f), 0f, 0.62f)
        }
        updateMaterial { m ->
            m.name = label
            m.baseColor = preset.first
            m.metallic = preset.second
            m.roughness = preset.third
            m.emissive = Vec3.ZERO
        }
        showMessage("$label material applied")
    }

    // ---------------------------------------------------------------- mesh editing

    private fun requireMesh(): SceneObject? {
        val obj = selectedObject
        if (obj?.mesh == null) showMessage("Select a mesh object first")
        return obj?.takeIf { it.mesh != null }
    }

    fun extrude(distance: Float = 0.15f) {
        val obj = requireMesh() ?: return
        val selected = selection.faces
        if (selected.isEmpty()) { showMessage("Select faces to extrude"); return }
        val nextFaces = HashSet<Int>()
        var caps: Set<Int> = emptySet()
        editScene("Extrude", geometry = true) { s ->
            val target = s.findObject(obj.id) ?: return@editScene
            caps = MeshOps.extrudeFaces(target.mesh!!, selected, distance)
            nextFaces.addAll(caps)
            selection = selection.copy(faces = nextFaces)
            runtime.selection = selection
        }
        runtime.selectionRevision++
        refreshGizmo()
        showMessage("Extruded ${selected.size} face(s)")
    }

    fun inset(amount: Float = 0.2f) {
        val obj = requireMesh() ?: return
        if (selection.faces.isEmpty()) { showMessage("Select faces to inset"); return }
        editScene("Inset", geometry = true) { s ->
            val target = s.findObject(obj.id) ?: return@editScene
            MeshOps.insetFaces(target.mesh!!, selection.faces, amount)
        }
        refreshGizmo()
        showMessage("Inset applied")
    }

    fun bevel(width: Float = 0.05f) {
        val obj = requireMesh() ?: return
        val verts = when (elementMode) {
            ElementMode.VERTEX, ElementMode.EDGE -> selection.vertices
            else -> emptySet()
        }
        if (verts.isEmpty()) { showMessage("Select vertices or an edge to bevel"); return }
        editScene("Bevel", geometry = true) { s ->
            val target = s.findObject(obj.id) ?: return@editScene
            MeshOps.bevelVertices(target.mesh!!, verts, width)
            selection = selection.copy(vertices = emptySet(), faces = emptySet())
            runtime.selection = selection
        }
        refreshGizmo()
        showMessage("Bevel applied")
    }

    fun subdivideMesh() {
        val obj = requireMesh() ?: return
        editScene("Subdivide", geometry = true) { s ->
            s.findObject(obj.id)?.mesh?.let { MeshOps.subdivide(it, smooth = true) }
            selection = selection.copy(vertices = emptySet(), faces = emptySet())
            runtime.selection = selection
        }
        refreshGizmo()
        showMessage("Catmull-Clark subdivision applied")
    }

    fun deleteSelectedFaces() {
        val obj = requireMesh() ?: return
        if (selection.faces.isEmpty()) { showMessage("Select faces to delete"); return }
        editScene("Delete Faces", geometry = true) { s ->
            s.findObject(obj.id)?.mesh?.let { MeshOps.deleteFaces(it, selection.faces) }
            selection = selection.copy(faces = emptySet())
            runtime.selection = selection
        }
        refreshGizmo()
    }

    fun smoothMesh() {
        val obj = requireMesh() ?: return
        editScene("Smooth", geometry = true) { s ->
            s.findObject(obj.id)?.mesh?.let { MeshOps.laplacianSmooth(it, null, 2, 0.35f) }
        }
    }

    fun mirrorMesh() {
        val obj = requireMesh() ?: return
        editScene("Mirror", geometry = true) { s ->
            s.findObject(obj.id)?.mesh?.let { MeshOps.mirror(it, 0, weldSeam = true) }
        }
        showMessage("Mirrored across X")
    }

    fun weldMesh() {
        val obj = requireMesh() ?: return
        var removed = 0
        editScene("Merge Vertices", geometry = true) { s ->
            removed = s.findObject(obj.id)?.mesh?.weld(1e-4f) ?: 0
        }
        showMessage("Merged $removed vertices")
    }

    fun flipNormals() {
        val obj = requireMesh() ?: return
        editScene("Flip Normals", geometry = true) { s ->
            s.findObject(obj.id)?.mesh?.let { MeshOps.flipNormals(it) }
        }
    }

    fun unwrap(mode: UvProjection.Mode) {
        val obj = requireMesh() ?: return
        editScene("UV Unwrap", geometry = true) { s ->
            s.findObject(obj.id)?.mesh?.let { UvProjection.project(it, mode) }
        }
        showMessage("UV ${mode.name.lowercase().replaceFirstChar { it.uppercase() }} projection")
    }

    fun booleanWith(otherId: Long, op: BooleanOp) {
        val a = selectedObject ?: return
        val b = scene.findObject(otherId) ?: return
        if (a.id == b.id) return
        val meshA = a.mesh ?: return
        val meshB = b.mesh ?: return
        executeBusy("Boolean ${op.name.lowercase()}…") {
            val preparedA = meshA.clone().also { it.transform(a.transform.matrix()) }
            val preparedB = meshB.clone().also { it.transform(b.transform.matrix()) }
            val result = MeshBoolean.apply(preparedA, preparedB, op)
            if (result.faces.isEmpty()) throw IllegalStateException("Boolean produced an empty mesh")
            withContext(Dispatchers.Main) {
                editScene("Boolean ${op.name.lowercase()}", geometry = true) { s ->
                    val target = s.findObject(a.id) ?: return@editScene
                    target.mesh = result
                    target.mesh!!.markDirty()
                    target.transform = Transform()
                    s.objects.removeAll { it.id == b.id }
                    selection = SelectionState(objectId = a.id)
                    runtime.selection = selection
                }
                refreshGizmo()
                showMessage("Boolean complete · ${result.faceCount} faces")
            }
        }
    }

    // ------------------------------------------------------------------------- rig

    fun autoRigSelected() {
        val obj = selectedObject?.takeIf { it.mesh != null } ?: run {
            showMessage("Select a mesh to rig")
            return
        }
        if (scene.skeleton != null) {
            showMessage("The scene already has a skeleton. Start a new scene to auto-rig another character.")
            return
        }
        val bounds = obj.bounds() ?: obj.mesh!!.bounds()
        val skeleton = AutoRig.createHumanoid(bounds)
        var weightsInfo = ""
        editScene("Auto-Rig Humanoid", geometry = true) { s ->
            val target = s.findObject(obj.id) ?: return@editScene
            val localBounds = target.mesh!!.bounds()
            Skinning.bind(target.mesh!!, skeleton)
            s.skeleton = skeleton
            val hips = skeleton.indexOfName("Hips")
            selection = SelectionState(objectId = obj.id, bone = hips, elements = ElementMode.BONE)
            runtime.selection = selection
            weightsInfo = "${target.mesh!!.vertexCount} vertices weighted"
        }
        mode = EditorMode.RIG
        runtime.editorMode = mode
        runtime.showSkeleton = true
        showSkeleton = true
        inspectorTab = InspectorTab.RIG
        runtime.poseOverride = null
        playing = false
        refreshGizmo()
        showMessage("Humanoid rig created · ${skeleton.size} bones · $weightsInfo")
    }

    fun addBone() {
        val skeleton = scene.skeleton ?: run {
            showMessage("Create a rig first")
            return
        }
        val parent = selection.bone
        val rest = skeleton.restWorldMatrices()
        val parentHead = if (parent >= 0) rest[parent].translation() else Vec3(0f, 0.9f, 0f)
        val parentTail = if (parent >= 0) skeleton.restTailWorld(parent, rest) else parentHead + Vec3(0f, 0.2f, 0f)
        val head = parentTail
        val tail = head + Vec3(0f, 0.16f, 0f)
        var newIndex = -1
        editScene("Add Bone", geometry = true) { s ->
            val sk = s.skeleton ?: return@editScene
            newIndex = sk.addBoneFromWorld("Bone ${sk.size + 1}", parent, head, tail)
            selection = selection.copy(bone = newIndex)
            runtime.selection = selection
        }
        if (newIndex >= 0) {
            selectBone(newIndex)
            showMessage("Bone added")
        }
    }

    fun boneRestEuler(index: Int): Vec3 =
        scene.skeleton?.bones?.getOrNull(index)?.restRotation?.toEulerDegrees() ?: Vec3.ZERO

    fun boneHeadLocal(index: Int): Vec3 =
        scene.skeleton?.bones?.getOrNull(index)?.headLocal ?: Vec3.ZERO

    fun setBoneRestHead(index: Int, head: Vec3) {
        if (scene.skeleton?.bones?.getOrNull(index) == null) return
        editScene("Edit Bone Head", geometry = false, coalesce = true) { s ->
            s.skeleton?.bones?.getOrNull(index)?.headLocal = head
        }
        poseRevision++
    }

    fun setBoneLength(index: Int, length: Float) {
        if (scene.skeleton?.bones?.getOrNull(index) == null) return
        editScene("Edit Bone Length", geometry = false, coalesce = true) { s ->
            s.skeleton?.bones?.getOrNull(index)?.length = length.coerceIn(0.01f, 5f)
        }
        poseRevision++
    }

    fun setBoneRestEuler(index: Int, euler: Vec3) {
        if (scene.skeleton?.bones?.getOrNull(index) == null) return
        editScene("Edit Bone Roll", geometry = false, coalesce = true) { s ->
            s.skeleton?.bones?.getOrNull(index)?.restRotation = Quat.fromEulerDegrees(euler)
        }
        poseRevision++
    }

    fun resetObjectTransform(id: Long) {
        editScene("Reset Transform", geometry = false) { s ->
            s.findObject(id)?.transform = Transform()
        }
        refreshGizmo()
    }

    fun renameBone(index: Int, name: String) {
        val clean = name.trim().replace(" ", "_").take(40)
        if (clean.isEmpty()) return
        editScene("Rename Bone", geometry = true, coalesce = true) { s ->
            s.skeleton?.bones?.getOrNull(index)?.name = clean
        }
    }

    fun deleteBone(index: Int) {
        val skeleton = scene.skeleton ?: return
        if (index !in skeleton.bones.indices) return
        if (skeleton.children(index).isNotEmpty()) {
            showMessage("Delete child bones first")
            return
        }
        editScene("Delete Bone", geometry = true) { s ->
            val sk = s.skeleton ?: return@editScene
            if (index < 0 || index >= sk.size) return@editScene
            sk.bones.removeAt(index)
            for (b in sk.bones) {
                if (b.parent == index) b.parent = -1
                else if (b.parent > index) b.parent--
            }
            for (o in s.objects) {
                val skin = o.mesh?.skin ?: continue
                val bones = skin.boneIndices.copyOf()
                val weights = skin.weights.copyOf()
                for (i in 0 until bones.size / 4) {
                    for (k in 0..3) {
                        if (bones[i * 4 + k] == index) { bones[i * 4 + k] = 0; weights[i * 4 + k] = 0f }
                        else if (bones[i * 4 + k] > index) bones[i * 4 + k]--
                    }
                    var sum = 0f
                    for (k in 0..3) sum += weights[i * 4 + k]
                    if (sum < 1e-5f) weights[i * 4] = 1f else for (k in 0..3) weights[i * 4 + k] /= sum
                }
                o.mesh?.skin = SkinWeights(bones, weights)
            }
            for (clip in s.clips) {
                val remapped = clip.tracks
                    .filter { it.bone != index }
                    .map { track ->
                        if (track.bone > index) AnimTrack(track.bone - 1, track.type, track.keys.toMutableList())
                        else track
                    }
                clip.tracks.clear()
                clip.tracks.addAll(remapped)
            }
            s.constraints.removeAll { it.bone == index || it.sourceBone == index }
            for (c in s.constraints) {
                if (c.bone > index) c.bone--
                if (c.sourceBone > index) c.sourceBone--
            }
            for (preset in s.poseLibrary) if (index < preset.bones.size) preset.bones.removeAt(index)
            selection = selection.copy(bone = -1)
            runtime.selection = selection
        }
        refreshGizmo()
        showMessage("Bone deleted")
    }

    fun bindWeights() {
        val obj = requireMesh() ?: return
        val skeleton = scene.skeleton ?: run { showMessage("No skeleton in scene"); return }
        val mesh = obj.mesh ?: return
        var count = 0
        executeBusy("Computing skin weights…") {
            val weights = Skinning.computeWeights(mesh, skeleton)
            withContext(Dispatchers.Main) {
                recordUndo("Bind Weights")
                mesh.skin = weights
                runtime.markGeometryChanged()
                sceneRevision++
                count = mesh.vertexCount
                scheduleAutosave()
                showMessage("Bound $count vertices to ${skeleton.size} bones")
            }
        }
    }

    fun addLimitConstraint(min: Vec3, max: Vec3) {
        val bone = selection.bone
        if (bone < 0) return
        editScene("Limit Rotation", geometry = false) { s ->
            s.constraints.add(
                BoneConstraint(
                    ConstraintType.LIMIT_ROTATION, bone,
                    minEuler = Vec3(minOf(min.x, max.x), minOf(min.y, max.y), minOf(min.z, max.z)),
                    maxEuler = Vec3(maxOf(min.x, max.x), maxOf(min.y, max.y), maxOf(min.z, max.z)),
                    name = "Limit ${scene.skeleton?.bones?.getOrNull(bone)?.name ?: "Bone"}"
                )
            )
        }
        runtime.poseOverride = null
        poseRevision++
        showMessage("Limit rotation added")
    }

    fun addCopyConstraint(sourceBone: Int) {
        val bone = selection.bone
        if (bone < 0 || sourceBone < 0) return
        editScene("Copy Rotation", geometry = false) { s ->
            s.constraints.add(
                BoneConstraint(
                    ConstraintType.COPY_ROTATION, bone, sourceBone,
                    name = "Copy ${scene.skeleton?.bones?.getOrNull(sourceBone)?.name ?: "Bone"}"
                )
            )
        }
        poseRevision++
        showMessage("Copy rotation added")
    }

    fun removeConstraint(index: Int) {
        editScene("Remove Constraint", geometry = false) { s ->
            if (index in s.constraints.indices) s.constraints.removeAt(index)
        }
        runtime.poseOverride = null
        poseRevision++
    }

    fun poseForEditing(): Pose {
        val skeleton = scene.skeleton ?: return Pose(0)
        runtime.poseOverride?.let { return it }
        val pose = PoseSampler.sample(scene, time)
        return pose
    }

    fun setBoneEuler(x: Float, y: Float, z: Float) {
        val skeleton = scene.skeleton ?: return
        val bone = selection.bone
        if (bone !in skeleton.bones.indices) return
        val pose = poseForEditing()
        pose.bones[bone].rotation = Quat.fromEulerDegrees(Vec3(x, y, z))
        runtime.poseOverride = pose
        poseRevision++
    }

    fun resetPose() {
        runtime.poseOverride = null
        poseRevision++
        runtime.ikTarget = null
        refreshGizmo()
        showMessage("Pose reset")
    }

    private fun rotateSelectedBoneWorld(axis: Vec3, delta: Float) {
        val skeleton = scene.skeleton ?: return
        val bone = selection.bone
        if (bone !in skeleton.bones.indices) return
        val pose = poseForEditing()
        val mats = skeleton.poseWorldMatrices(pose)
        val parentRot = if (skeleton.bones[bone].parent >= 0)
            skeleton.worldRotation(mats, skeleton.bones[bone].parent) else Quat.IDENTITY
        val base = parentRot * skeleton.bones[bone].restRotation
        val deltaQ = Quat.axisAngle(axis, delta)
        pose.bones[bone].rotation = (base.conjugate() * deltaQ * base * pose.bones[bone].rotation).normalized()
        runtime.poseOverride = pose
        poseRevision++
        runtime.gizmo = runtime.gizmo.copy(pivot = mats[bone].translation())
    }

    fun bonePoseEuler(): Vec3 {
        val skeleton = scene.skeleton ?: return Vec3.ZERO
        val bone = selection.bone
        if (bone !in skeleton.bones.indices) return Vec3.ZERO
        return poseForEditing().bones[bone].rotation.toEulerDegrees()
    }

    // ------------------------------------------------------------------- animation

    fun selectClip(index: Int) {
        if (index !in scene.clips.indices) return
        scene.activeClip = index
        runtime.scene = scene
        time = 0f
        runtime.time = 0f
        runtime.poseOverride = null
        playing = true
        runtime.playing = true
        refreshGizmo()
        sceneRevision++
    }

    fun createClip(name: String = "New Clip", duration: Float = 2f) {
        editScene("New Clip", geometry = false) { s ->
            val clip = AnimationClip(name, duration, true)
            s.clips.add(clip)
            s.activeClip = s.clips.size - 1
        }
        time = 0f
        runtime.time = 0f
        showMessage("Clip created")
    }

    fun duplicateClip() {
        val clip = currentClip() ?: return
        editScene("Duplicate Clip", geometry = false) { s ->
            val copy = clip.copy().also { it.name = "${clip.name} Copy" }
            s.clips.add(copy)
            s.activeClip = s.clips.size - 1
        }
    }

    fun deleteClip() {
        if (scene.clips.size <= 1) { showMessage("A project needs at least one clip"); return }
        editScene("Delete Clip", geometry = false) { s ->
            s.clips.removeAt(s.activeClip.coerceIn(0, s.clips.size - 1))
            s.layers.removeAll { it.clipIndex !in s.clips.indices }
            s.layers.forEach { if (it.clipIndex >= s.clips.size) it.clipIndex = 0 }
            s.activeClip = s.activeClip.coerceIn(0, s.clips.size - 1)
        }
        time = 0f
        runtime.time = 0f
    }

    fun renameClip(name: String) {
        if (currentClip() == null) return
        editScene("Rename Clip", geometry = false, coalesce = true) { s ->
            s.clips.getOrNull(s.activeClip)?.name = name.take(40)
        }
    }

    fun setClipLoop(loop: Boolean) {
        if (currentClip() == null) return
        editScene("Clip Loop", geometry = false, coalesce = true) { s ->
            s.clips.getOrNull(s.activeClip)?.loop = loop
        }
    }

    fun setClipDuration(duration: Float) {
        if (currentClip() == null) return
        editScene("Clip Duration", geometry = false, coalesce = true) { s ->
            s.clips.getOrNull(s.activeClip)?.duration = duration.coerceIn(0.1f, 120f)
        }
        time = time.coerceAtMost(currentClip()?.duration ?: time)
        runtime.time = time
    }

    fun insertKeyframe(allBones: Boolean = false) {
        StudioHaptics.tick()
        val skeleton = scene.skeleton ?: run { showMessage("No rig to key"); return }
        val clip = currentClip() ?: run { showMessage("No animation clip"); return }
        val pose = poseForEditing()
        val bones = if (allBones) skeleton.bones.indices.toList()
        else if (selection.bone >= 0) listOf(selection.bone) else skeleton.bones.indices.toList()
        val t = time
        editScene("Insert Key", geometry = false) { s ->
            val target = s.clips.getOrNull(s.activeClip) ?: return@editScene
            target.duration = maxOf(target.duration, max(t, 0.1f))
            for (i in bones) {
                if (i !in pose.bones.indices) continue
                target.addBoneKey(
                    i, t,
                    rotation = pose.bones[i].rotation,
                    translation = pose.bones[i].translation,
                    scale = pose.bones[i].scale,
                    interp = Interp.BEZIER
                )
            }
            if (s.skeleton != null) {
                // Keep bone indices valid if the rig differs from the pose source.
                for (track in target.tracks.toList()) if (track.bone !in s.skeleton!!.bones.indices) target.tracks.remove(track)
            }
        }
        runtime.poseOverride = null
        poseRevision++
        showMessage("Key inserted at ${"%.2f".format(t)}s")
    }

    fun deleteKeyAtCurrent() {
        val clip = currentClip() ?: return
        val bones = if (selection.bone >= 0) setOf(selection.bone) else clip.tracks.map { it.bone }.toSet()
        var removed = 0
        editScene("Delete Key", geometry = false) { s ->
            val target = s.clips.getOrNull(s.activeClip) ?: return@editScene
            for (track in target.tracks) {
                if (track.bone in bones) {
                    val before = track.keys.size
                    val idx = track.keys.indexOfFirst { abs(it.time - time) < 0.03f }
                    if (idx >= 0) {
                        track.keys.removeAt(idx)
                        removed += before - track.keys.size
                    }
                }
            }
            target.tracks.removeAll { it.keys.isEmpty() }
        }
        showMessage(if (removed > 0) "Key deleted" else "No key at playhead")
    }

    fun moveKeyTime(bone: Int, fromTime: Float, toTime: Float) {
        if (currentClip() == null) return
        val clamped = toTime.coerceIn(0f, currentClip()?.duration ?: toTime)
        editScene("Move Key", geometry = false, coalesce = true) { s ->
            val clip = s.clips.getOrNull(s.activeClip) ?: return@editScene
            for (track in clip.tracks) {
                if (track.bone != bone) continue
                val key = track.keys.minByOrNull { abs(it.time - fromTime) } ?: continue
                if (abs(key.time - fromTime) > 0.03f) continue
                key.time = clamped
                track.keys.sortBy { it.time }
            }
        }
    }

    fun updateRotationKeyEuler(bone: Int, atTime: Float, euler: Vec3) {
        if (currentClip() == null) return
        editScene("Edit Curve Key", geometry = false, coalesce = true) { s ->
            val clip = s.clips.getOrNull(s.activeClip) ?: return@editScene
            val track = clip.tracks.firstOrNull { it.bone == bone && it.type == ChannelType.ROTATION } ?: return@editScene
            val key = track.keys.minByOrNull { abs(it.time - atTime) } ?: return@editScene
            if (abs(key.time - atTime) > 0.03f) return@editScene
            val q = Quat.fromEulerDegrees(euler)
            key.value = Vec4(q.x, q.y, q.z, q.w)
        }
        runtime.poseOverride = null
        poseRevision++
    }

    fun generateCycle(run: Boolean) {
        val skeleton = scene.skeleton ?: run { showMessage("Create or import a rig first"); return }
        val clip = CycleGenerator.generate(skeleton, run)
        editScene("Generate ${clip.name}", geometry = false) { s ->
            var name = clip.name
            var i = 2
            while (s.clips.any { it.name == name }) { name = "${clip.name} $i"; i++ }
            clip.name = name
            s.clips.add(clip)
            s.activeClip = s.clips.size - 1
        }
        time = 0f
        runtime.time = 0f
        playing = true
        showMessage("${clip.name} added · ${clip.keyCount()} keys")
    }

    fun addAnimationLayer(clipIndex: Int) {
        editScene("Add Layer", geometry = false) { s ->
            s.layers.add(
                AnimationLayer("Layer ${s.layers.size + 1}", clipIndex.coerceIn(0, max(0, s.clips.size - 1)), 1f, additive = false)
            )
        }
    }

    fun updateLayer(index: Int, block: (AnimationLayer) -> Unit) {
        editScene("Layer", geometry = false, coalesce = true) { s ->
            s.layers.getOrNull(index)?.let(block)
        }
        runtime.poseOverride = null
        poseRevision++
    }

    fun removeLayer(index: Int) {
        editScene("Remove Layer", geometry = false) { s ->
            if (index in s.layers.indices) s.layers.removeAt(index)
        }
        runtime.poseOverride = null
        poseRevision++
    }

    fun capturePose(name: String) {
        val pose = poseForEditing()
        if (pose.bones.isEmpty()) { showMessage("No rig pose to capture"); return }
        editScene("Capture Pose", geometry = false) { s ->
            var finalName = name.ifBlank { "Pose ${s.poseLibrary.size + 1}" }
            var i = 2
            while (s.poseLibrary.any { it.name == finalName }) { finalName = "$name $i"; i++ }
            s.poseLibrary.add(PosePreset.capture(finalName, pose))
        }
        showMessage("Pose saved to library")
    }

    fun applyPosePreset(index: Int, weight: Float = 1f) {
        val preset = scene.poseLibrary.getOrNull(index) ?: return
        val pose = poseForEditing()
        preset.apply(pose, weight)
        runtime.poseOverride = pose
        poseRevision++
        showMessage("Pose applied")
    }

    fun deletePosePreset(index: Int) {
        editScene("Delete Pose", geometry = false) { s ->
            if (index in s.poseLibrary.indices) s.poseLibrary.removeAt(index)
        }
    }

    fun retargetExternal(rigIndex: Int, clipIndex: Int) {
        val rig = externalRigs.getOrNull(rigIndex) ?: run { showMessage("Import a rig to retarget from"); return }
        val target = scene.skeleton ?: run { showMessage("Current scene has no rig"); return }
        val sourceClip = rig.clips.getOrNull(clipIndex) ?: return
        executeBusy("Retargeting animation…") {
            val clip = Retarget.retarget(rig.skeleton, sourceClip, target)
            if (clip.tracks.isEmpty()) throw IllegalStateException("No matching humanoid bones found")
            withContext(Dispatchers.Main) {
                editScene("Retarget Animation", geometry = false) { s ->
                    var name = "${sourceClip.name} (Retarget)"
                    var i = 2
                    while (s.clips.any { it.name == name }) { name = "${sourceClip.name} Retarget $i"; i++ }
                    clip.name = name
                    s.clips.add(clip)
                    s.activeClip = s.clips.size - 1
                }
                time = 0f
                runtime.time = 0f
                playing = true
                showMessage("Retargeted ${clip.tracks.size} bones")
            }
        }
    }

    // ------------------------------------------------------------------ file IO

    private fun queryName(uri: Uri): String {
        return runCatching {
            getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "imported"
    }

    fun importFile(uri: Uri) {
        executeBusy("Importing model…") {
            val resolver = getApplication<Application>().contentResolver
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Cannot open file")
            val name = queryName(uri)
            val lower = name.lowercase()
            val result: ModelImportResult
            val format: String
            when {
                lower.endsWith(".studio3d") -> {
                    val s = ProjectIO.load(bytes.toString(Charsets.UTF_8))
                    withContext(Dispatchers.Main) {
                        recordUndo("Open Project")
                        scene = s
                        runtime.replaceScene(s)
                        sceneRevision++
                        selection = SelectionState(objectId = s.objects.firstOrNull()?.id)
                        runtime.selection = selection
                        time = 0f
                        runtime.time = 0f
                        runtime.camera.frame(s.frameBounds())
                        inspectorTab = InspectorTab.OBJECT
                        showMessage("Project opened")
                        scheduleAutosave()
                    }
                    return@executeBusy
                }
                lower.endsWith(".glb") || lower.endsWith(".gltf") || Glb.isGlb(bytes) -> {
                    result = GltfImporter().import(bytes, name.substringBeforeLast('.'))
                    format = "glTF"
                }
                lower.endsWith(".bvh") -> {
                    result = BvhIO.import(bytes.toString(Charsets.UTF_8), name.substringBeforeLast('.'))
                    format = "BVH"
                }
                lower.endsWith(".obj") -> {
                    result = ObjIO.import(bytes.toString(Charsets.UTF_8), name.substringBeforeLast('.'))
                    format = "OBJ"
                }
                lower.endsWith(".stl") -> {
                    result = StlIO.import(bytes, name.substringBeforeLast('.'))
                    format = "STL"
                }
                lower.endsWith(".fbx") -> throw IllegalArgumentException(
                    "FBX import is not supported. Export FBX for Unity, or import GLB/glTF, OBJ, STL or BVH."
                )
                else -> {
                    val text = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull() ?: ""
                    when {
                        text.trimStart().startsWith("HIERARCHY") -> {
                            result = BvhIO.import(text, name.substringBeforeLast('.'))
                            format = "BVH"
                        }
                        text.trimStart().startsWith("{") -> {
                            result = GltfImporter().import(bytes, name.substringBeforeLast('.'))
                            format = "glTF"
                        }
                        text.contains("\nv ") || text.startsWith("v ") -> {
                            result = ObjIO.import(text, name.substringBeforeLast('.'))
                            format = "OBJ"
                        }
                        else -> {
                            result = StlIO.import(bytes, name.substringBeforeLast('.'))
                            format = "STL"
                        }
                    }
                }
            }
            withContext(Dispatchers.Main) {
                importProposal = ImportProposal(name, result, bytes, format)
            }
        }
    }

    fun cancelImport() {
        importProposal = null
    }

    fun acceptImport(mode: ImportAccept) {
        val proposal = importProposal ?: return
        val incoming = proposal.result.scene
        when (mode) {
            ImportAccept.OPEN -> {
                recordUndo("Import ${proposal.format}")
                scene = incoming
                runtime.replaceScene(incoming)
                sceneRevision++
                selection = SelectionState(
                    objectId = incoming.objects.firstOrNull()?.id,
                    bone = incoming.skeleton?.indexOfName("Hips") ?: -1,
                    elements = if (incoming.skeleton != null) ElementMode.BONE else ElementMode.OBJECT
                )
                runtime.selection = selection
                time = 0f
                runtime.time = 0f
                runtime.poseOverride = null
                runtime.camera.frame(incoming.frameBounds())
                playing = incoming.clips.isNotEmpty()
                runtime.playing = playing
                inspectorTab = if (incoming.skeleton != null) InspectorTab.RIG else InspectorTab.OBJECT
                if (incoming.skeleton != null) {
                    this.mode = EditorMode.RIG
                    runtime.editorMode = this.mode
                    runtime.showSkeleton = true
                    showSkeleton = true
                } else {
                    this.mode = EditorMode.OBJECT
                    runtime.editorMode = this.mode
                }
                showMessage("Imported ${proposal.fileName}")
                scheduleAutosave()
            }
            ImportAccept.ADD -> {
                var added = 0
                var skippedRig = false
                editScene("Import Objects", geometry = true) { s ->
                    for (obj in incoming.objects) {
                        if (obj.mesh == null) continue
                        if (obj.mesh!!.skin != null) {
                            skippedRig = true
                            continue
                        }
                        val copy = obj.copy(s.nextObjectId++)
                        s.objects.add(copy)
                        added++
                    }
                    if (incoming.clips.isNotEmpty() && s.skeleton == null) {
                        val incomingSkeleton = incoming.skeleton
                        if (incomingSkeleton != null) {
                            s.skeleton = incomingSkeleton.copy()
                            for (c in incoming.clips) s.clips.add(c.copy())
                            s.activeClip = max(0, s.clips.size - 1)
                        }
                    }
                }
                runtime.camera.frame(scene.frameBounds())
                inspectorTab = InspectorTab.OBJECT
                val note = if (skippedRig) " (skinned meshes need Open or Retarget)" else ""
                showMessage("Added $added object(s)$note")
            }
            ImportAccept.RETARGET -> {
                val sourceSkeleton = incoming.skeleton
                if (sourceSkeleton == null || incoming.clips.isEmpty()) {
                    showMessage("No animation found to retarget")
                    return
                }
                if (scene.skeleton == null) {
                    showMessage("Open the rigged model first, then retarget")
                    return
                }
                externalRigs = externalRigs + ExternalRig(proposal.fileName, sourceSkeleton, incoming.clips.map { it.copy() })
                lastImportedRigName = proposal.fileName
                val rigIndex = externalRigs.size - 1
                retargetExternal(rigIndex, 0)
            }
        }
        importProposal = null
    }

    fun exportToUri(uri: Uri, format: SceneFormat, allClips: Boolean = true, includeTextures: Boolean = true) {
        val current = scene
        executeBusy("Exporting ${format.label}…") {
            val bytes = when (format) {
                SceneFormat.GLB -> GltfExporter().exportGlb(
                    current,
                    GltfExporter.Options(
                        exportAllClips = allClips,
                        clipIndices = if (allClips) null else listOf(current.activeClip),
                        includeTextures = includeTextures
                    )
                )
                SceneFormat.OBJ -> ObjIO.export(current).toByteArray(Charsets.UTF_8)
                SceneFormat.STL -> StlIO.export(current, binary = true)
                SceneFormat.BVH -> BvhIO.export(current, current.activeClip).toByteArray(Charsets.UTF_8)
                SceneFormat.PROJECT -> ProjectIO.save(current).toByteArray(Charsets.UTF_8)
                SceneFormat.PNG -> throw IllegalArgumentException("Use the viewport camera button to export a still.")
            }
            val resolver = getApplication<Application>().contentResolver
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("Cannot open destination")
            withContext(Dispatchers.Main) {
                exportCompleted++
                showMessage("${format.label} saved · ${(bytes.size / 1024f).roundToInt()} KB")
            }
        }
    }

    fun suggestedFileName(format: SceneFormat): String {
        val base = scene.name
            .replace(Regex("[^A-Za-z0-9 _-]"), "")
            .trim()
            .replace(Regex("[\\s_]+"), "_")
            .ifBlank { "Untitled" }
        return "${base}.${format.extension}"
    }

    fun renameScene(name: String) {
        val clean = name.trim().take(48)
        if (clean.isEmpty()) return
        editScene("Rename Project", geometry = false, coalesce = true) { it.name = clean }
    }

    fun saveNow() {
        executeBusy("Saving project…") {
            val snapshot = synchronized(runtime.lock) { scene.copy() }
            autosaveFile.writeText(ProjectIO.save(snapshot))
            withContext(Dispatchers.Main) {
                lastAutosave = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date())
                showMessage("Project saved to device storage")
            }
        }
    }

    fun scheduleAutosave() {
        if (!StudioPreferences.autosaveEnabled) return
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(1800)
            runCatching {
                val snapshot = synchronized(runtime.lock) { scene.copy() }
                val text = ProjectIO.save(snapshot)
                autosaveFile.writeText(text)
                withContext(Dispatchers.Main) {
                    lastAutosave = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date())
                }
            }
        }
    }

    fun newScene(empty: Boolean = false) {
        recordUndo("New Scene")
        val s = if (empty) Scene().also { it.name = "Untitled Scene" } else DemoScene.create()
        scene = s
        runtime.replaceScene(s)
        sceneRevision++
        selection = SelectionState(objectId = s.objects.firstOrNull()?.id)
        runtime.selection = selection
        runtime.poseOverride = null
        time = 0f
        runtime.time = 0f
        playing = s.clips.isNotEmpty()
        runtime.playing = playing
        mode = EditorMode.OBJECT
        runtime.editorMode = mode
        runtime.camera.frame(s.frameBounds())
        sceneRevision++
        refreshGizmo()
        scheduleAutosave()
        showMessage(if (empty) "New empty scene" else "Demo scene loaded")
    }

    fun loadDemoScene() {
        newScene(empty = false)
    }

    fun frameAll() {
        runtime.camera.frame(scene.frameBounds())
    }

    fun renameProject(name: String) {
        val clean = name.trim().take(60)
        if (clean.isEmpty()) return
        recordUndo("Rename Project", coalesce = true)
        scene.name = clean
        runtime.scene = scene
        sceneRevision++
        scheduleAutosave()
    }

    fun attachExternalRigForRetarget() {
        showMessage("Choose Import and select a rigged GLB/BVH, then Retarget")
    }

    fun executeBusy(label: String, block: suspend () -> Unit) {
        busy = true
        busyLabel = label
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    showMessage(t.message ?: "Operation failed")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    busy = false
                    busyLabel = ""
                }
            }
        }
    }

    override fun onCleared() {
        runCatching {
            val snapshot = synchronized(runtime.lock) { scene.copy() }
            autosaveFile.writeText(ProjectIO.save(snapshot))
        }
        super.onCleared()
    }

    fun setAspectFromViewport(w: Int, h: Int) {
        runtime.viewportWidth = w
        runtime.viewportHeight = h
    }
}
