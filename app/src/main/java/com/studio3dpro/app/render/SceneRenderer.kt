package com.studio3dpro.app.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import com.studio3dpro.app.EditorMode
import com.studio3dpro.app.ElementMode
import com.studio3dpro.app.GizmoAxis
import com.studio3dpro.app.ToolMode
import com.studio3dpro.engine.anim.PoseSampler
import com.studio3dpro.engine.math.Aabb
import com.studio3dpro.engine.math.Mat4
import com.studio3dpro.engine.math.Quat
import com.studio3dpro.engine.math.Vec2
import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.math.degToRad
import com.studio3dpro.engine.mesh.Primitives
import com.studio3dpro.engine.scene.Material
import com.studio3dpro.engine.scene.Pose
import com.studio3dpro.engine.scene.Scene
import com.studio3dpro.engine.scene.SceneObject
import com.studio3dpro.engine.scene.Skeleton
import com.studio3dpro.engine.scene.TextureData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.tan

// GLES30's Kotlin surface omits this ES 1.x polygon-offset enum.
private const val POLYGON_OFFSET_LINE = 0x2AB2

class SceneRenderer(private val runtime: StudioRuntime) : GLSurfaceView.Renderer {

    private var width = 1
    private var height = 1
    private var meshProgram = 0
    private var lineProgram = 0
    private var solidProgram = 0
    private var gridProgram = 0
    private var backgroundProgram = 0
    private var shadowProgram = 0
    private var depthProgram = 0

    // Key-light shadow map. Transparent materials are skipped, so the map
    // only ever holds opaque silhouettes.
    private val shadowMapSize = 512
    private var shadowFbo = 0
    private var shadowTex = 0
    private var shadowReady = false
    private var lightVP = Mat4.identity()

    private var lastGeometryRevision = -1L
    private val gpuMeshes = HashMap<Long, GpuMesh>()
    private val textures = HashMap<Int, Int>()

    private var quadVbo = 0
    private var gridVbo = 0
    private var boneBuffer = 0
    private var shadowVbo = 0
    private var shadowIndexCount = 0
    private var lineVbo = 0
    private var dynamicVbo = 0
    private var arrowMesh: StaticMesh? = null
    private var headMesh: StaticMesh? = null
    private var cubeMesh: StaticMesh? = null
    private var sphereMesh: StaticMesh? = null

    private var fpsAccumulator = 0.0
    private var fpsFrames = 0
    private var lastFrameNanos = System.nanoTime()
    private var initialized = false

    private data class GpuMesh(
        var vao: Int = 0,
        var vbo: Int = 0,
        var ebo: Int = 0,
        var indexCount: Int = 0,
        val triangles: Int = 0,
        val data: FloatArray,
        val indices: IntArray,
        var wireVao: Int = 0,
        var wireVbo: Int = 0,
        var wireEbo: Int = 0,
        var wireCount: Int = 0
    )

    private class StaticMesh(val vao: Int, val vbo: Int, val count: Int)

    private class Program(val id: Int, val uniforms: Map<String, Int>)

    /** True when the light "warm paper" theme is active; drives the canvas shader palettes. */
    @Volatile
    private var lightCanvas = false

    fun setLightCanvas(light: Boolean) {
        lightCanvas = light
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.02f, 0.023f, 0.027f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        val build = Programs()
        meshProgram = build.mesh()
        lineProgram = build.line()
        solidProgram = build.solid()
        gridProgram = build.grid()
        backgroundProgram = build.background()
        shadowProgram = build.shadow()
        depthProgram = build.depth()
        // A fresh EGL context invalidates the old names; do not delete them.
        shadowFbo = 0
        shadowTex = 0
        shadowReady = false
        createShadowTarget()
        createStaticGeometry()
        for (m in gpuMeshes.values) releaseGpuMesh(m)
        gpuMeshes.clear()
        for (id in textures.values) GLES30.glDeleteTextures(1, intArrayOf(id), 0)
        textures.clear()
        Programs.clearCache()
        lastGeometryRevision = -1L
        initialized = true
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = max(1, w)
        height = max(1, h)
        runtime.viewportWidth = width
        runtime.viewportHeight = height
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (!initialized) return
        val scene = runtime.scene
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        drawBackground()
        if (scene == null) {
            updateFps()
            return
        }
        val pose: Pose = synchronized(runtime.lock) {
            runtime.poseOverride ?: runCatching { PoseSampler.sample(scene, runtime.time) }
                .getOrDefault(Pose(0))
        }
        val aspect = width.toFloat() / height.toFloat()
        val view = runtime.camera.viewMatrix()
        val viewProj = runtime.camera.projectionMatrix(aspect) * view
        val eye = runtime.camera.eye()

        rebuildGeometryIfNeeded(scene)
        if (runtime.dynamicShadows) renderShadowMap(scene, pose)
        if (runtime.showGrid) drawGrid(viewProj, eye)
        drawContactShadow(scene, viewProj)
        drawMeshes(scene, pose, viewProj, view, eye)
        drawSelectionOverlay(scene, pose, viewProj)
        if (runtime.showSkeleton && scene.skeleton != null) {
            drawSkeleton(scene, pose, viewProj)
        }
        drawIkTarget(scene, pose, viewProj)
        drawGizmo(scene, pose, viewProj)
        if (runtime.captureRequested) captureFrame()
        updateFps()
    }

    private fun captureFrame() {
        val callback = runtime.captureCallback
        runtime.captureRequested = false
        runtime.captureCallback = null
        if (callback == null) return
        val pixels = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels)
        pixels.position(0)
        val raw = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        raw.copyPixelsFromBuffer(pixels)
        val matrix = android.graphics.Matrix().apply { postScale(1f, -1f, width / 2f, height / 2f) }
        val flipped = Bitmap.createBitmap(raw, 0, 0, width, height, matrix, true)
        raw.recycle()
        callback(flipped)
    }

    // ---------------------------------------------------------------- geometry

    private fun rebuildGeometryIfNeeded(scene: Scene) {
        synchronized(runtime.lock) {
            val revision = runtime.geometryRevision
            if (revision == lastGeometryRevision) return
            lastGeometryRevision = revision
            val validIds = HashSet<Long>()
            for (obj in scene.objects) {
                validIds.add(obj.id)
                val mesh = obj.mesh ?: continue
                if (mesh.faces.isEmpty()) continue
                val gpu = gpuMeshes.getOrPut(obj.id) { buildGpuMesh(obj) }
                if (gpu.triangles == 0) gpuMeshes.remove(obj.id)
            }
            val iterator = gpuMeshes.entries.iterator()
            while (iterator.hasNext()) {
                val e = iterator.next()
                if (e.key !in validIds) {
                    releaseGpuMesh(e.value)
                    iterator.remove()
                }
            }
        }
    }

    private fun buildGpuMesh(obj: SceneObject): GpuMesh {
        val mesh = obj.mesh!!
        val corners = mesh.totalCorners()
        val data = FloatArray(corners * 16)
        val indexList = ArrayList<Int>(mesh.faces.sumOf { max(0, it.size - 2) } * 3)
        val normals = mesh.cornerNormals(60f)
        var corner = 0
        var ci = 0
        for (f in mesh.faces.indices) {
            val face = mesh.faces[f]
            val start = corner
            for (c in face.indices) {
                val v = face[c]
                val p = mesh.vertices[v]
                data[ci] = p.x; data[ci + 1] = p.y; data[ci + 2] = p.z
                data[ci + 3] = normals[corner * 3]
                data[ci + 4] = normals[corner * 3 + 1]
                data[ci + 5] = normals[corner * 3 + 2]
                data[ci + 6] = mesh.faceUvs[f][c * 2]
                data[ci + 7] = mesh.faceUvs[f][c * 2 + 1]
                val skin = mesh.skin
                if (skin != null) {
                    for (k in 0..3) {
                        data[ci + 8 + k] = skin.boneIndices[v * 4 + k].toFloat()
                        data[ci + 12 + k] = skin.weights[v * 4 + k]
                    }
                }
                ci += 16
                corner++
            }
            for (i in 1 until face.size - 1) {
                indexList.add(start); indexList.add(start + i); indexList.add(start + i + 1)
            }
        }
        val indices = indexList.toIntArray()
        val vao = IntArray(1); val vbo = IntArray(1); val ebo = IntArray(1)
        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glGenBuffers(1, vbo, 0)
        GLES30.glGenBuffers(1, ebo, 0)
        GLES30.glBindVertexArray(vao[0])
        val buffer = data.toFloatBuffer()
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, buffer, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo[0])
        GLES30.glBufferData(
            GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4,
            indices.toIntBuffer(), GLES30.GL_STATIC_DRAW
        )
        val stride = 16 * 4
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, stride, 24)
        GLES30.glEnableVertexAttribArray(3)
        GLES30.glVertexAttribPointer(3, 4, GLES30.GL_FLOAT, false, stride, 32)
        GLES30.glEnableVertexAttribArray(4)
        GLES30.glVertexAttribPointer(4, 4, GLES30.GL_FLOAT, false, stride, 48)
        GLES30.glBindVertexArray(0)
        val gpu = GpuMesh(vao[0], vbo[0], ebo[0], indices.size, indices.size / 3, data, indices)
        buildWireGeometry(gpu, mesh)
        return gpu
    }

    /**
     * Edge geometry shares the skinned vertex layout, so wireframe and edit
     * overlays are deformed by the same GPU skinning path as the solid mesh.
     * Doing this on the CPU for every frame cost ~50 FPS on a Redmi Note 11.
     */
    private fun buildWireGeometry(gpu: GpuMesh, mesh: com.studio3dpro.engine.mesh.Mesh) {
        if (mesh.vertexCount == 0) return
        val edges = mesh.edges().values.take(120_000)
        if (edges.isEmpty()) return
        val data = FloatArray(mesh.vertexCount * 16)
        for (v in 0 until mesh.vertexCount) {
            val p = mesh.vertices[v]
            val o = v * 16
            data[o] = p.x; data[o + 1] = p.y; data[o + 2] = p.z
            // normal and uv stay zero; the shader exits before lighting them.
            val skin = mesh.skin
            if (skin != null) {
                for (k in 0..3) {
                    data[o + 8 + k] = skin.boneIndices[v * 4 + k].toFloat()
                    data[o + 12 + k] = skin.weights[v * 4 + k]
                }
            }
        }
        val indices = IntArray(edges.size * 2)
        for (i in edges.indices) {
            indices[i * 2] = edges[i].a
            indices[i * 2 + 1] = edges[i].b
        }
        val vao = IntArray(1); val vbo = IntArray(1); val ebo = IntArray(1)
        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glGenBuffers(1, vbo, 0)
        GLES30.glGenBuffers(1, ebo, 0)
        GLES30.glBindVertexArray(vao[0])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, data.toFloatBuffer(), GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo[0])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, indices.toIntBuffer(), GLES30.GL_STATIC_DRAW)
        val stride = 16 * 4
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, stride, 24)
        GLES30.glEnableVertexAttribArray(3)
        GLES30.glVertexAttribPointer(3, 4, GLES30.GL_FLOAT, false, stride, 32)
        GLES30.glEnableVertexAttribArray(4)
        GLES30.glVertexAttribPointer(4, 4, GLES30.GL_FLOAT, false, stride, 48)
        GLES30.glBindVertexArray(0)
        gpu.wireVao = vao[0]; gpu.wireVbo = vbo[0]; gpu.wireEbo = ebo[0]; gpu.wireCount = indices.size
    }

    private fun releaseGpuMesh(gpu: GpuMesh) {
        if (gpu.vao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(gpu.vao), 0)
        if (gpu.wireVao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(gpu.wireVao), 0)
        val buffers = intArrayOf(gpu.vbo, gpu.ebo, gpu.wireVbo, gpu.wireEbo)
        GLES30.glDeleteBuffers(4, buffers, 0)
    }

    private fun createBoneBuffer() {
        if (boneBuffer != 0) GLES30.glDeleteBuffers(1, intArrayOf(boneBuffer), 0)
        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        boneBuffer = ids[0]
        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, boneBuffer)
        GLES30.glBufferData(GLES30.GL_UNIFORM_BUFFER, 48 * 16 * 4, null, GLES30.GL_DYNAMIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, 0)
        val block = GLES30.glGetUniformBlockIndex(meshProgram, "BoneBlock")
        if (block != -1) {
            GLES30.glUniformBlockBinding(meshProgram, block, 0)
            GLES30.glBindBufferBase(GLES30.GL_UNIFORM_BUFFER, 0, boneBuffer)
        }
    }

    private fun createStaticGeometry() {
        val quad = floatArrayOf(-1f, 0f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, -1f, 0f, 1f, 1f, 0f, 1f, -1f, 0f, 1f)
        quadVbo = uploadFloatVbo(quad)
        gridVbo = quadVbo
        val segments = 64
        val shadowVerts = ArrayList<Float>()
        for (i in 0 until segments) {
            val a0 = 2f * Math.PI.toFloat() * i / segments
            val a1 = 2f * Math.PI.toFloat() * (i + 1) / segments
            shadowVerts.add(0f); shadowVerts.add(0f)
            shadowVerts.add(kotlin.math.cos(a0)); shadowVerts.add(kotlin.math.sin(a0))
            shadowVerts.add(kotlin.math.cos(a1)); shadowVerts.add(kotlin.math.sin(a1))
        }
        shadowIndexCount = shadowVerts.size / 2
        shadowVbo = uploadFloatVbo(shadowVerts.toFloatArray())
        arrowMesh = buildStaticMesh(combinedArrow())
        headMesh = null
        cubeMesh = buildStaticMesh(Primitives.cube(0.11f))
        sphereMesh = buildStaticMesh(Primitives.uvSphere(0.055f, 16, 10))
    }

    private fun combinedArrow(): com.studio3dpro.engine.mesh.Mesh {
        val shaft = Primitives.cylinder(0.012f, 0.012f, 0.42f, 12)
        shaft.transform(Mat4.translation(Vec3(0f, 0.29f, 0f)))
        val head = Primitives.cone(0.05f, 0.18f, 16)
        head.transform(Mat4.translation(Vec3(0f, 0.56f, 0f)))
        shaft.append(head)
        return shaft
    }

    private fun buildStaticMesh(mesh: com.studio3dpro.engine.mesh.Mesh): StaticMesh {
        val data = FloatArray(mesh.vertices.size * 6)
        val normals = mesh.vertexNormals()
        for (i in mesh.vertices.indices) {
            val p = mesh.vertices[i]
            data[i * 6] = p.x; data[i * 6 + 1] = p.y; data[i * 6 + 2] = p.z
            data[i * 6 + 3] = normals[i * 3]
            data[i * 6 + 4] = normals[i * 3 + 1]
            data[i * 6 + 5] = normals[i * 3 + 2]
        }
        val indices = mesh.triangles()
        val vao = IntArray(1); val vbo = IntArray(1); val ebo = IntArray(1)
        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glGenBuffers(1, vbo, 0)
        GLES30.glGenBuffers(1, ebo, 0)
        GLES30.glBindVertexArray(vao[0])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, data.toFloatBuffer(), GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo[0])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, indices.toIntBuffer(), GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 24, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 24, 12)
        GLES30.glBindVertexArray(0)
        return StaticMesh(vao[0], vbo[0], indices.size)
    }

    private fun uploadFloatVbo(data: FloatArray): Int {
        val vbo = IntArray(1)
        GLES30.glGenBuffers(1, vbo, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, data.toFloatBuffer(), GLES30.GL_STATIC_DRAW)
        return vbo[0]
    }

    // ------------------------------------------------------------------ drawing

    private fun drawBackground() {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glUseProgram(backgroundProgram)
        GLES30.glUniform2f(Programs.u(backgroundProgram, "uRes"), width.toFloat(), height.toFloat())
        GLES30.glUniform1f(Programs.u(backgroundProgram, "uLight"), if (lightCanvas) 1f else 0f)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    private fun drawGrid(viewProj: Mat4, eye: Vec3) {
        GLES30.glUseProgram(gridProgram)
        GLES30.glUniformMatrix4fv(Programs.u(gridProgram, "uViewProj"), 1, false, viewProj.toFloatArray(), 0)
        GLES30.glUniform3f(Programs.u(gridProgram, "uCam"), eye.x, eye.y, eye.z)
        GLES30.glUniform1f(Programs.u(gridProgram, "uLight"), if (lightCanvas) 1f else 0f)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, gridVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun drawContactShadow(scene: Scene, viewProj: Mat4) {
        val b = scene.objects.filter { it.mesh?.skin != null }.mapNotNull { it.bounds() }
            .fold(Aabb.EMPTY) { acc, it -> acc.expand(it) }
        if (b.size.lengthSq() <= 0f) return
        GLES30.glUseProgram(shadowProgram)
        val radius = max(b.size.x, b.size.z) * 0.62f + 0.08f
        val model = Mat4.translation(Vec3(b.center.x, 0.004f, b.center.z)) * Mat4.scaling(Vec3(radius, 1f, radius))
        GLES30.glUniformMatrix4fv(Programs.u(shadowProgram, "uViewProj"), 1, false, viewProj.toFloatArray(), 0)
        GLES30.glUniformMatrix4fv(Programs.u(shadowProgram, "uModel"), 1, false, model.toFloatArray(), 0)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, shadowVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, shadowIndexCount)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun drawMeshes(scene: Scene, pose: Pose, viewProj: Mat4, view: Mat4, eye: Vec3) {
        GLES30.glUseProgram(meshProgram)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(true)
        GLES30.glUniformMatrix4fv(Programs.u(meshProgram, "uViewProj"), 1, false, viewProj.toFloatArray(), 0)
        GLES30.glUniform3f(Programs.u(meshProgram, "uCamera"), eye.x, eye.y, eye.z)
        GLES30.glUniform1i(Programs.u(meshProgram, "uHasShadow"), if (shadowReady && runtime.dynamicShadows) 1 else 0)
        GLES30.glUniformMatrix4fv(Programs.u(meshProgram, "uLightVP"), 1, false, lightVP.toFloatArray(), 0)
        GLES30.glUniform2f(Programs.u(meshProgram, "uShadowTexel"), 1f / shadowMapSize, 1f / shadowMapSize)
        GLES30.glUniform1i(Programs.u(meshProgram, "uShadowMap"), 1)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadowTex)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        var tris = 0
        val sorted = scene.objects.filter { it.visible && it.mesh != null }
            .sortedByDescending { it.material.opacity >= 0.999f }
        for (obj in sorted) {
            val gpu = gpuMeshes[obj.id] ?: continue
            val mesh = obj.mesh ?: continue
            val model = obj.transform.matrix()
            val selected = runtime.selection.objectId == obj.id
            val skinned = mesh.skin != null && scene.skeleton != null
            GLES30.glUniformMatrix4fv(Programs.u(meshProgram, "uModel"), 1, false, model.toFloatArray(), 0)
            GLES30.glUniform1i(Programs.u(meshProgram, "uSkinned"), if (skinned) 1 else 0)
            if (skinned) {
                val skeleton = scene.skeleton!!
                val mats = skeleton.skinMatrices(pose, model)
                val packed = FloatArray(48 * 16)
                for (i in 0 until 48) {
                    val m = mats.getOrNull(i) ?: Mat4.identity()
                    m.m.copyInto(packed, i * 16)
                }
                GLES30.glUniformMatrix4fv(Programs.u(meshProgram, "uBones[0]"), 48, false, packed, 0)
            }
            applyMaterial(meshProgram, obj.material)
            GLES30.glUniform1f(Programs.u(meshProgram, "uSelected"), if (selected) 1f else 0f)
            val weightBone = runtime.weightPaintBone
            GLES30.glUniform1i(Programs.u(meshProgram, "uWeightBone"), if (weightBone >= 0 && selected) weightBone else -1)
            if (obj.material.opacity < 0.999f) {
                GLES30.glEnable(GLES30.GL_BLEND)
                GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
                GLES30.glDepthMask(false)
            } else {
                GLES30.glDisable(GLES30.GL_BLEND)
                GLES30.glDepthMask(true)
            }
            GLES30.glBindVertexArray(gpu.vao)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, gpu.indexCount, GLES30.GL_UNSIGNED_INT, 0)
            GLES30.glBindVertexArray(0)
            tris += gpu.triangles
        }
        runtime.triangleCount = tris
        GLES30.glDisable(GLES30.GL_BLEND)
        if (runtime.wireframe) {
            drawGpuWire(scene, pose, viewProj, null, 0.22f)
        }
    }

    private fun applyMaterial(program: Int, mat: Material) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glUniform3f(Programs.u(program, "uBaseColor"), mat.baseColor.x, mat.baseColor.y, mat.baseColor.z)
        GLES30.glUniform1f(Programs.u(program, "uMetallic"), mat.metallic)
        GLES30.glUniform1f(Programs.u(program, "uRoughness"), mat.roughness)
        GLES30.glUniform3f(Programs.u(program, "uEmissive"),
            mat.emissive.x * mat.emissiveStrength,
            mat.emissive.y * mat.emissiveStrength,
            mat.emissive.z * mat.emissiveStrength)
        GLES30.glUniform1f(Programs.u(program, "uOpacity"), mat.opacity)
        val tex = mat.baseColorTexture
        if (tex != null) {
            val id = textureId(tex)
            if (id != 0) {
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
                GLES30.glUniform1i(Programs.u(program, "uTexture"), 0)
                GLES30.glUniform1i(Programs.u(program, "uHasTexture"), 1)
            } else {
                GLES30.glUniform1i(Programs.u(program, "uHasTexture"), 0)
            }
        } else {
            GLES30.glUniform1i(Programs.u(program, "uHasTexture"), 0)
        }
    }

    private fun textureId(texture: TextureData): Int {
        val key = System.identityHashCode(texture)
        textures[key]?.let { return it }
        val decoded = runCatching { BitmapFactory.decodeByteArray(texture.bytes, 0, texture.bytes.size) }.getOrNull()
            ?: return 0
        val bitmap = if (decoded.config == Bitmap.Config.ARGB_8888) decoded
            else decoded.copy(Bitmap.Config.ARGB_8888, false).also { decoded.recycle() }
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
        val buffer = ByteBuffer.allocateDirect(bitmap.width * bitmap.height * 4).order(ByteOrder.nativeOrder())
        bitmap.copyPixelsToBuffer(buffer)
        buffer.position(0)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_SRGB8_ALPHA8,
            bitmap.width, bitmap.height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer
        )
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        bitmap.recycle()
        textures[key] = ids[0]
        return ids[0]
    }

    // --------------------------------------------------------------- overlays

    private fun drawSelectionOverlay(scene: Scene, pose: Pose, viewProj: Mat4) {
        val sel = runtime.selection
        val obj = scene.objects.firstOrNull { it.id == sel.objectId } ?: return
        val mesh = obj.mesh ?: return
        val model = obj.transform.matrix()
        if (runtime.editorMode == EditorMode.EDIT && runtime.elementMode != ElementMode.OBJECT) {
            // Overlays follow the deformed surface via the mesh shader's
            // skinned edge path, so an animated character has no rest-pose ghost.
            val skinMats = skinMatricesFor(mesh, scene.skeleton, pose, model)
            drawGpuWire(scene, pose, viewProj, obj.id, 0.55f)
            if (sel.faces.isNotEmpty()) {
                val tris = ArrayList<Float>()
                for (f in sel.faces) {
                    val face = mesh.faces.getOrNull(f) ?: continue
                    val idx = face.map { overlayVertex(mesh, it, model, skinMats) }
                    for (i in 1 until idx.size - 1) {
                        for (p in listOf(idx[0], idx[i], idx[i + 1])) {
                            tris.add(p.x); tris.add(p.y); tris.add(p.z)
                        }
                    }
                }
                drawTriangles(tris.toFloatArray(), floatArrayOf(1f, 0.66f, 0.28f, 0.34f), viewProj)
            }
            if (sel.vertices.isNotEmpty()) {
                val pts = ArrayList<Float>()
                for (v in sel.vertices) {
                    if (v !in 0 until mesh.vertexCount) continue
                    val w = overlayVertex(mesh, v, model, skinMats)
                    pts.add(w.x); pts.add(w.y); pts.add(w.z)
                }
                drawLines(pts.toFloatArray(), floatArrayOf(1f, 0.72f, 0.32f, 1f), viewProj, depthTest = false, linePoints = true)
            }
            return
        }
        // Object selection is communicated by the amber rim in the mesh shader
        // and the transform gizmo. A bounding cage only adds visual noise on a
        // phone screen, so no box is drawn here.
    }

    private fun drawGpuWire(scene: Scene, pose: Pose, viewProj: Mat4, onlyObjectId: Long?, alpha: Float) {
        GLES30.glUseProgram(meshProgram)
        GLES30.glUniformMatrix4fv(Programs.u(meshProgram, "uViewProj"), 1, false, viewProj.toFloatArray(), 0)
        GLES30.glUniform1f(Programs.u(meshProgram, "uWire"), alpha)
        GLES30.glUniform1i(Programs.u(meshProgram, "uWeightBone"), -1)
        GLES30.glUniform1f(Programs.u(meshProgram, "uSelected"), 0f)
        GLES30.glUniform1i(Programs.u(meshProgram, "uHasTexture"), 0)
        GLES30.glUniform1f(Programs.u(meshProgram, "uOpacity"), 1f)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        GLES30.glEnable(POLYGON_OFFSET_LINE)
        GLES30.glPolygonOffset(-1f, -1f)
        for (obj in scene.objects) {
            if (!obj.visible || obj.mesh == null) continue
            if (onlyObjectId != null && obj.id != onlyObjectId) continue
            val gpu = gpuMeshes[obj.id] ?: continue
            if (gpu.wireVao == 0 || gpu.wireCount == 0) continue
            val mesh = obj.mesh ?: continue
            val model = obj.transform.matrix()
            val skinned = mesh.skin != null && scene.skeleton != null
            GLES30.glUniformMatrix4fv(Programs.u(meshProgram, "uModel"), 1, false, model.toFloatArray(), 0)
            GLES30.glUniform1i(Programs.u(meshProgram, "uSkinned"), if (skinned) 1 else 0)
            if (skinned) {
                val mats = scene.skeleton!!.skinMatrices(pose, model)
                val packed = FloatArray(48 * 16)
                for (i in 0 until 48) {
                    val m = mats.getOrNull(i) ?: Mat4.identity()
                    m.m.copyInto(packed, i * 16)
                }
                GLES30.glUniformMatrix4fv(Programs.u(meshProgram, "uBones[0]"), 48, false, packed, 0)
            }
            GLES30.glBindVertexArray(gpu.wireVao)
            GLES30.glDrawElements(GLES30.GL_LINES, gpu.wireCount, GLES30.GL_UNSIGNED_INT, 0)
            GLES30.glBindVertexArray(0)
        }
        GLES30.glDisable(POLYGON_OFFSET_LINE)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUniform1f(Programs.u(meshProgram, "uWire"), 0f)
    }

    private fun skinMatricesFor(mesh: com.studio3dpro.engine.mesh.Mesh, skeleton: Skeleton?, pose: Pose, model: Mat4): Array<Mat4>? {
        if (mesh.skin == null || skeleton == null) return null
        return skeleton.skinMatrices(pose, model)
    }

    /** Matches the vertex shader's linear blend skinning for overlay lines. */
    private fun overlayVertex(
        mesh: com.studio3dpro.engine.mesh.Mesh,
        index: Int,
        model: Mat4,
        mats: Array<Mat4>?
    ): Vec3 {
        val rest = mesh.vertices[index]
        val skin = mesh.skin
        if (skin == null || mats == null) return model.transformPoint(rest)
        var x = 0f
        var y = 0f
        var z = 0f
        for (k in 0..3) {
            val w = skin.weights[index * 4 + k]
            if (w <= 1e-6f) continue
            val bone = skin.boneIndices[index * 4 + k]
            if (bone !in mats.indices) continue
            val p = mats[bone].transformPoint(rest)
            x += p.x * w; y += p.y * w; z += p.z * w
        }
        return Vec3(x, y, z)
    }

    // ------------------------------------------------------------- shadow pass

    private fun createShadowTarget() {
        val tex = IntArray(1)
        GLES30.glGenTextures(1, tex, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_DEPTH_COMPONENT24,
            shadowMapSize, shadowMapSize, 0,
            GLES30.GL_DEPTH_COMPONENT, GLES30.GL_UNSIGNED_INT, null
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)

        val fbo = IntArray(1)
        GLES30.glGenFramebuffers(1, fbo, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT,
            GLES30.GL_TEXTURE_2D, tex[0], 0
        )
        GLES30.glDrawBuffers(1, intArrayOf(GLES30.GL_NONE), 0)
        GLES30.glReadBuffer(GLES30.GL_NONE)
        shadowReady = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        if (shadowReady) {
            shadowTex = tex[0]
            shadowFbo = fbo[0]
        } else {
            GLES30.glDeleteFramebuffers(1, fbo, 0)
            GLES30.glDeleteTextures(1, tex, 0)
        }
    }

    /**
     * Renders opaque silhouettes from the key light into a depth map. The main
     * shader samples it with a 3x3 PCF kernel so the character grounds itself
     * on the display base instead of floating above a blurry blob.
     */
    private fun renderShadowMap(scene: Scene, pose: Pose) {
        if (!shadowReady) return
        if (scene.objects.none { it.visible && it.mesh != null }) return
        val bounds = scene.frameBounds()
        val radius = (bounds.radius * 1.18f).coerceIn(0.6f, 8f)
        val center = bounds.center
        val dir = Vec3(-0.55f, 0.55f, 0.62f).normalized()
        val eye = center + dir * (radius * 3f)
        lightVP = Mat4.ortho(-radius, radius, -radius, radius, 0.1f, radius * 6f) *
            Mat4.lookAt(eye, center)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shadowFbo)
        GLES30.glViewport(0, 0, shadowMapSize, shadowMapSize)
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glUseProgram(depthProgram)
        GLES30.glUniformMatrix4fv(Programs.u(depthProgram, "uLightVP"), 1, false, lightVP.toFloatArray(), 0)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glPolygonOffset(2.0f, 4.0f)
        for (obj in scene.objects) {
            if (!obj.visible || obj.mesh == null) continue
            if (obj.material.opacity < 0.999f) continue
            val gpu = gpuMeshes[obj.id] ?: continue
            if (gpu.indexCount == 0) continue
            val mesh = obj.mesh ?: continue
            val model = obj.transform.matrix()
            val skinned = mesh.skin != null && scene.skeleton != null
            GLES30.glUniformMatrix4fv(Programs.u(depthProgram, "uModel"), 1, false, model.toFloatArray(), 0)
            GLES30.glUniform1i(Programs.u(depthProgram, "uSkinned"), if (skinned) 1 else 0)
            if (skinned) {
                val mats = scene.skeleton!!.skinMatrices(pose, model)
                val packed = FloatArray(48 * 16)
                for (i in 0 until 48) {
                    val m = mats.getOrNull(i) ?: Mat4.identity()
                    m.m.copyInto(packed, i * 16)
                }
                GLES30.glUniformMatrix4fv(Programs.u(depthProgram, "uBones[0]"), 48, false, packed, 0)
            }
            GLES30.glBindVertexArray(gpu.vao)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, gpu.indexCount, GLES30.GL_UNSIGNED_INT, 0)
            GLES30.glBindVertexArray(0)
        }
        GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, width, height)
    }

    private fun drawSkeleton(scene: Scene, pose: Pose, viewProj: Mat4) {
        val skeleton = scene.skeleton ?: return
        val mats = skeleton.poseWorldMatrices(pose)
        val lines = ArrayList<Float>()
        val joints = ArrayList<Float>()
        val selectedBone = runtime.selection.bone
        val selLines = ArrayList<Float>()
        for (i in skeleton.bones.indices) {
            val head = mats[i].translation()
            val tail = skeleton.poseTailWorld(i, pose, mats)
            if (i == selectedBone) {
                selLines.add(head.x); selLines.add(head.y); selLines.add(head.z)
                selLines.add(tail.x); selLines.add(tail.y); selLines.add(tail.z)
            } else {
                lines.add(head.x); lines.add(head.y); lines.add(head.z)
                lines.add(tail.x); lines.add(tail.y); lines.add(tail.z)
            }
            joints.add(head.x); joints.add(head.y); joints.add(head.z)
        }
        drawLines(lines.toFloatArray(), floatArrayOf(0.39f, 0.90f, 0.77f, 0.55f), viewProj, depthTest = false, linePoints = false)
        if (selLines.isNotEmpty()) {
            drawLines(selLines.toFloatArray(), floatArrayOf(1f, 0.72f, 0.32f, 1f), viewProj, depthTest = false, linePoints = false)
        }
        drawPoints(joints.toFloatArray(), floatArrayOf(0.62f, 1f, 0.88f, 0.95f), viewProj, 7f)
        if (selectedBone in skeleton.bones.indices) {
            val tail = skeleton.poseTailWorld(selectedBone, pose, mats)
            drawPoints(floatArrayOf(tail.x, tail.y, tail.z), floatArrayOf(1f, 0.72f, 0.32f, 1f), viewProj, 13f)
        }
    }

    private fun drawIkTarget(scene: Scene, pose: Pose, viewProj: Mat4) {
        val target = runtime.ikTarget ?: return
        if (runtime.editorMode != EditorMode.RIG) return
        val selected = runtime.selection.bone
        val skeleton = scene.skeleton ?: return
        if (selected in skeleton.bones.indices) {
            val mats = skeleton.poseWorldMatrices(pose)
            val head = mats[selected].translation()
            drawLines(
                floatArrayOf(head.x, head.y, head.z, target.x, target.y, target.z),
                floatArrayOf(0.45f, 0.95f, 0.82f, 0.45f), viewProj, depthTest = false, linePoints = false
            )
            drawPoints(floatArrayOf(target.x, target.y, target.z), floatArrayOf(0.55f, 1f, 0.86f, 1f), viewProj, 16f)
        }
    }

    private fun drawGizmo(scene: Scene, pose: Pose, viewProj: Mat4) {
        if (!runtime.gizmo.visible) return
        val pivot = runtime.gizmo.pivot
        val scale = gizmoScale()
        val mode = runtime.gizmo.mode
        if (mode == ToolMode.IK) {
            drawPoints(floatArrayOf(pivot.x, pivot.y, pivot.z), floatArrayOf(0.55f, 1f, 0.86f, 1f), viewProj, 14f)
            return
        }
        val axes = listOf(
            Triple(Vec3.RIGHT, GizmoAxis.X, floatArrayOf(0.95f, 0.35f, 0.32f, 1f)),
            Triple(Vec3.UP, GizmoAxis.Y, floatArrayOf(0.45f, 0.88f, 0.42f, 1f)),
            Triple(Vec3(0f, 0f, 1f), GizmoAxis.Z, floatArrayOf(0.32f, 0.60f, 1f, 1f))
        )
        if (mode == ToolMode.ROTATE) {
            for ((axis, axisId, color) in axes) {
                val ring = circleLine(pivot, axis, scale * 0.52f, 72)
                val c = if (runtime.gizmo.selectedAxis == axisId) floatArrayOf(1f, 0.85f, 0.45f, 1f) else color
                drawLines(ring, c, viewProj, depthTest = false, linePoints = false)
            }
            drawPoints(floatArrayOf(pivot.x, pivot.y, pivot.z), floatArrayOf(1f, 1f, 1f, 0.9f), viewProj, 9f)
            return
        }
        val program = solidProgram
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(Programs.u(program, "uViewProj"), 1, false, viewProj.toFloatArray(), 0)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        for ((axis, axisId, color) in axes) {
            val q = if (axis.x > 0.5f) Quat.axisAngle(Vec3(0f, 0f, 1f), degToRad(-90f))
            else if (axis.z > 0.5f) Quat.axisAngle(Vec3.RIGHT, degToRad(90f))
            else Quat.IDENTITY
            val model = Mat4.translation(pivot) * Mat4.rotation(q) * Mat4.scaling(Vec3.ONE * scale)
            val mesh = if (mode == ToolMode.SCALE) cubeMesh else arrowMesh
            val c = if (runtime.gizmo.selectedAxis == axisId) floatArrayOf(1f, 0.92f, 0.65f, 1f) else color
            if (mode == ToolMode.SCALE) {
                val scaled = model * Mat4.translation(Vec3(0f, 0.62f, 0f))
                drawStatic(scaled, c)
            } else {
                drawStatic(model, c)
            }
        }
        drawPoints(floatArrayOf(pivot.x, pivot.y, pivot.z), floatArrayOf(1f, 0.9f, 0.7f, 0.9f), viewProj, 9f)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    private fun drawStatic(model: Mat4, color: FloatArray) {
        val mesh = if (runtime.gizmo.mode == ToolMode.SCALE) cubeMesh else arrowMesh
        mesh ?: return
        GLES30.glUniformMatrix4fv(Programs.u(solidProgram, "uModel"), 1, false, model.toFloatArray(), 0)
        GLES30.glUniform4f(Programs.u(solidProgram, "uColor"), color[0], color[1], color[2], color[3])
        GLES30.glBindVertexArray(mesh.vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, mesh.count, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
    }

    private fun circleLine(center: Vec3, normal: Vec3, radius: Float, segments: Int): FloatArray {
        val up = if (abs(normal.y) > 0.9f) Vec3.RIGHT else Vec3.UP
        val a = (up cross normal).normalized()
        val b = normal cross a
        val pts = FloatArray((segments + 1) * 2 * 3)
        var o = 0
        for (i in 0..segments) {
            val ang = 2f * Math.PI.toFloat() * i / segments
            val p = center + a * (kotlin.math.cos(ang) * radius) + b * (kotlin.math.sin(ang) * radius)
            if (i > 0) {
                val prevAng = 2f * Math.PI.toFloat() * (i - 1) / segments
                val prev = center + a * (kotlin.math.cos(prevAng) * radius) + b * (kotlin.math.sin(prevAng) * radius)
                pts[o++] = prev.x; pts[o++] = prev.y; pts[o++] = prev.z
            }
            pts[o++] = p.x; pts[o++] = p.y; pts[o++] = p.z
        }
        return pts.copyOf(o)
    }

    private fun gizmoScale(): Float = runtime.camera.distance * 0.095f + 0.05f

    // ---------------------------------------------------------------- primitives

    private fun drawLines(
        positions: FloatArray,
        color: FloatArray,
        viewProj: Mat4,
        depthTest: Boolean,
        linePoints: Boolean
    ) {
        if (positions.isEmpty()) return
        GLES30.glUseProgram(lineProgram)
        GLES30.glUniformMatrix4fv(Programs.u(lineProgram, "uViewProj"), 1, false, viewProj.toFloatArray(), 0)
        GLES30.glUniform4f(Programs.u(lineProgram, "uColor"), color[0], color[1], color[2], color[3])
        GLES30.glUniform1f(Programs.u(lineProgram, "uPointSize"), if (linePoints) 10f else 1f)
        GLES30.glUniform1i(Programs.u(lineProgram, "uIsPoint"), if (linePoints) 1 else 0)
        if (!depthTest) GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        if (depthTest) {
            GLES30.glEnable(POLYGON_OFFSET_LINE)
            GLES30.glPolygonOffset(-1f, -1f)
        }
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        uploadDynamic(positions)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glDrawArrays(if (linePoints) GLES30.GL_POINTS else GLES30.GL_LINES, 0, positions.size / 3)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisable(GLES30.GL_BLEND)
        if (depthTest) GLES30.glDisable(POLYGON_OFFSET_LINE)
        if (!depthTest) GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    private fun drawPoints(positions: FloatArray, color: FloatArray, viewProj: Mat4, size: Float) {
        if (positions.isEmpty()) return
        GLES30.glUseProgram(lineProgram)
        GLES30.glUniformMatrix4fv(Programs.u(lineProgram, "uViewProj"), 1, false, viewProj.toFloatArray(), 0)
        GLES30.glUniform4f(Programs.u(lineProgram, "uColor"), color[0], color[1], color[2], color[3])
        GLES30.glUniform1f(Programs.u(lineProgram, "uPointSize"), size)
        GLES30.glUniform1i(Programs.u(lineProgram, "uIsPoint"), 1)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        uploadDynamic(positions)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, positions.size / 3)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    private fun drawTriangles(positions: FloatArray, color: FloatArray, viewProj: Mat4) {
        if (positions.isEmpty()) return
        GLES30.glUseProgram(solidProgram)
        GLES30.glUniformMatrix4fv(Programs.u(solidProgram, "uViewProj"), 1, false, viewProj.toFloatArray(), 0)
        GLES30.glUniformMatrix4fv(Programs.u(solidProgram, "uModel"), 1, false, Mat4.identity().toFloatArray(), 0)
        GLES30.glUniform4f(Programs.u(solidProgram, "uColor"), color[0], color[1], color[2], color[3])
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        uploadDynamic(positions)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        // Solid shader expects a normal attribute; use flat normal.
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glVertexAttrib3f(1, 0f, 1f, 0f)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, positions.size / 3)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    private fun uploadDynamic(positions: FloatArray) {
        if (dynamicVbo == 0) {
            val ids = IntArray(1)
            GLES30.glGenBuffers(1, ids, 0)
            dynamicVbo = ids[0]
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, dynamicVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, positions.size * 4, positions.toFloatBuffer(), GLES30.GL_DYNAMIC_DRAW)
    }

    private fun updateFps() {
        val now = System.nanoTime()
        val dt = (now - lastFrameNanos) / 1_000_000_000.0
        lastFrameNanos = now
        fpsAccumulator += dt
        fpsFrames++
        if (fpsAccumulator >= 0.5) {
            runtime.fps = (fpsFrames / fpsAccumulator).roundToInt()
            runtime.frameCallback?.invoke(runtime.fps)
            fpsAccumulator = 0.0
            fpsFrames = 0
        }
    }

    fun release() {
        for (m in gpuMeshes.values) releaseGpuMesh(m)
        gpuMeshes.clear()
        for (id in textures.values) GLES30.glDeleteTextures(1, intArrayOf(id), 0)
        textures.clear()
        listOf(quadVbo, shadowVbo, lineVbo, dynamicVbo, boneBuffer).forEach {
            if (it != 0) GLES30.glDeleteBuffers(1, intArrayOf(it), 0)
        }
        if (shadowFbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(shadowFbo), 0)
        if (shadowTex != 0) GLES30.glDeleteTextures(1, intArrayOf(shadowTex), 0)
        shadowFbo = 0
        shadowTex = 0
        shadowReady = false
        listOf(arrowMesh, cubeMesh, sphereMesh).forEach { mesh ->
            mesh?.let { GLES30.glDeleteVertexArrays(1, intArrayOf(it.vao), 0) }
        }
        initialized = false
    }

    private fun FloatArray.toFloatBuffer(): FloatBuffer =
        ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(this).also { it.position(0) }

    private fun IntArray.toIntBuffer() =
        ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().put(this).also { it.position(0) }

    // ------------------------------------------------------------------ shaders

    private class Programs {
        fun mesh(): Int = compile(MESH_VERT, MESH_FRAG, "MESH")
        fun line(): Int = compile(LINE_VERT, LINE_FRAG, "LINE")
        fun solid(): Int = compile(SOLID_VERT, SOLID_FRAG, "SOLID")
        fun grid(): Int = compile(GRID_VERT, GRID_FRAG, "GRID")
        fun background(): Int = compile(BG_VERT, BG_FRAG, "BG")
        fun shadow(): Int = compile(SHADOW_VERT, SHADOW_FRAG, "SHADOW")
        fun depth(): Int = compile(DEPTH_VERT, DEPTH_FRAG, "DEPTH")

        companion object {
            private val cache = HashMap<Int, HashMap<String, Int>>()

            fun clearCache() {
                cache.clear()
            }

            fun compile(vertex: String, fragment: String, label: String): Int {
                val vs = shader(GLES30.GL_VERTEX_SHADER, vertex, "$label-vert")
                val fs = shader(GLES30.GL_FRAGMENT_SHADER, fragment, "$label-frag")
                val program = GLES30.glCreateProgram()
                GLES30.glAttachShader(program, vs)
                GLES30.glAttachShader(program, fs)
                GLES30.glLinkProgram(program)
                val status = IntArray(1)
                GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
                if (status[0] == 0) {
                    throw RuntimeException("Failed to link $label: ${GLES30.glGetProgramInfoLog(program)}")
                }
                GLES30.glDeleteShader(vs)
                GLES30.glDeleteShader(fs)
                cache[program] = HashMap()
                return program
            }

            fun u(program: Int, name: String): Int {
                val map = cache.getOrPut(program) { HashMap() }
                return map.getOrPut(name) { GLES30.glGetUniformLocation(program, name) }
            }

            private fun shader(type: Int, source: String, label: String): Int {
                val shader = GLES30.glCreateShader(type)
                GLES30.glShaderSource(shader, source.trimStart())
                GLES30.glCompileShader(shader)
                val status = IntArray(1)
                GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
                if (status[0] == 0) {
                    throw RuntimeException("Shader $label failed: ${GLES30.glGetShaderInfoLog(shader)}")
                }
                return shader
            }
        }
    }

    companion object {
        private const val MESH_VERT = """
#version 300 es
precision highp float;
layout(location=0) in vec3 aPos;
layout(location=1) in vec3 aNormal;
layout(location=2) in vec2 aUv;
layout(location=3) in vec4 aJoints;
layout(location=4) in vec4 aWeights;
uniform mat4 uModel;
uniform mat4 uViewProj;
uniform mat4 uBones[48];
uniform bool uSkinned;
uniform highp int uWeightBone;
out vec3 vWorld;
out vec3 vNormal;
out vec2 vUv;
out float vWeight;
void main() {
    vec4 p = vec4(aPos, 1.0);
    vec3 n = aNormal;
    if (uSkinned) {
        mat4 skin =
            uBones[int(aJoints.x + 0.5)] * aWeights.x +
            uBones[int(aJoints.y + 0.5)] * aWeights.y +
            uBones[int(aJoints.z + 0.5)] * aWeights.z +
            uBones[int(aJoints.w + 0.5)] * aWeights.w;
        p = skin * p;
        n = mat3(skin) * n;
    }
    vec4 world = uModel * p;
    vWorld = world.xyz;
    vNormal = mat3(uModel) * n;
    vUv = aUv;
    float w = 0.0;
    if (uWeightBone >= 0) {
        if (int(aJoints.x + 0.5) == uWeightBone) w += aWeights.x;
        if (int(aJoints.y + 0.5) == uWeightBone) w += aWeights.y;
        if (int(aJoints.z + 0.5) == uWeightBone) w += aWeights.z;
        if (int(aJoints.w + 0.5) == uWeightBone) w += aWeights.w;
    }
    vWeight = w;
    gl_Position = uViewProj * world;
}
"""

        private const val MESH_FRAG = """
#version 300 es
precision highp float;
in vec3 vWorld;
in vec3 vNormal;
in vec2 vUv;
in float vWeight;
uniform vec3 uCamera;
uniform vec3 uBaseColor;
uniform vec3 uEmissive;
uniform float uMetallic;
uniform float uRoughness;
uniform float uOpacity;
uniform float uSelected;
uniform highp int uWeightBone;
uniform sampler2D uTexture;
uniform bool uHasTexture;
uniform float uWire;
uniform highp sampler2D uShadowMap;
uniform mat4 uLightVP;
uniform vec2 uShadowTexel;
uniform int uHasShadow;
out vec4 fragColor;

const float PI = 3.14159265359;

float D_GGX(float NoH, float a) {
    float a2 = a * a;
    float d = NoH * NoH * (a2 - 1.0) + 1.0;
    return a2 / (PI * d * d + 1e-6);
}
float V_Smith(float NoV, float NoL, float a) {
    float k = a * 0.5;
    return 0.25 / ((NoV * (1.0 - k) + k) * (NoL * (1.0 - k) + k) + 1e-5);
}
vec3 fresnel(float c, vec3 f0) { return f0 + (1.0 - f0) * pow(1.0 - c, 5.0); }
vec3 env(vec3 r, float rough) {
    vec3 sky = mix(vec3(0.13, 0.15, 0.19), vec3(0.46, 0.50, 0.58), clamp(r.y * 1.2, 0.0, 1.0));
    vec3 ground = vec3(0.025, 0.028, 0.032);
    vec3 c = mix(ground, sky, smoothstep(-0.35, 0.35, r.y));
    return c * mix(1.0, 0.55, rough);
}
vec3 aces(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}
vec3 evalLight(vec3 N, vec3 V, vec3 L, vec3 radiance, vec3 albedo, vec3 F0, float rough, float metal) {
    L = normalize(L);
    float NoL = max(dot(N, L), 0.0);
    if (NoL <= 0.0) return vec3(0.0);
    float NoV = max(dot(N, V), 1e-4);
    vec3 H = normalize(V + L);
    float NoH = max(dot(N, H), 0.0);
    float VoH = max(dot(V, H), 0.0);
    float a = rough * rough;
    vec3 F = fresnel(VoH, F0);
    vec3 spec = D_GGX(NoH, a) * V_Smith(NoV, NoL, a) * F;
    vec3 kd = (1.0 - F) * (1.0 - metal);
    return (kd * albedo / PI + spec) * radiance * NoL;
}
float shadowFactor(vec3 world, vec3 n, float NoL) {
    vec4 lp = uLightVP * vec4(world + n * 0.010, 1.0);
    vec3 p = lp.xyz / max(lp.w, 1e-5);
    p = p * 0.5 + 0.5;
    if (p.x < 0.0 || p.x > 1.0 || p.y < 0.0 || p.y > 1.0 || p.z > 1.0) return 1.0;
    // Slope-scaled bias: steep surfaces need a wider margin to avoid acne.
    float bias = mix(0.0012, 0.0003, clamp(NoL, 0.0, 1.0));
    // 2x2 PCF: four taps are enough to soften a 512px map and keep the
    // shader cheap on mobile GPUs.
    vec2 o = uShadowTexel * 0.75;
    float sum = 0.0;
    sum += p.z - bias > texture(uShadowMap, p.xy + vec2(-o.x, -o.y)).r ? 0.0 : 1.0;
    sum += p.z - bias > texture(uShadowMap, p.xy + vec2( o.x, -o.y)).r ? 0.0 : 1.0;
    sum += p.z - bias > texture(uShadowMap, p.xy + vec2(-o.x,  o.y)).r ? 0.0 : 1.0;
    sum += p.z - bias > texture(uShadowMap, p.xy + vec2( o.x,  o.y)).r ? 0.0 : 1.0;
    return sum * 0.25;
}
void main() {
    if (uWire > 0.001) {
        fragColor = vec4(0.78, 0.75, 0.68, uWire);
        return;
    }
    vec3 N = normalize(vNormal);
    if (!gl_FrontFacing) N = -N;
    vec3 V = normalize(uCamera - vWorld);
    vec3 albedo = pow(uBaseColor, vec3(2.2));
    if (uHasTexture) albedo *= texture(uTexture, vUv).rgb;
    float metal = clamp(uMetallic, 0.0, 1.0);
    float rough = clamp(uRoughness, 0.045, 1.0);
    vec3 diffuseColor = albedo * (1.0 - metal);
    vec3 F0 = mix(vec3(0.04), albedo, metal);
    vec3 color = vec3(0.0);
    vec3 keyDir = normalize(vec3(-0.55, 0.55, 0.62));
    float keyNoL = max(dot(N, keyDir), 0.0);
    float shadow = uHasShadow == 1 ? shadowFactor(vWorld, N, keyNoL) : 1.0;
    color += evalLight(N, V, keyDir, vec3(1.35, 1.22, 1.02) * (2.2 * shadow), albedo, F0, rough, metal);
    color += evalLight(N, V, vec3(0.72, 0.22, 0.45), vec3(0.38, 0.55, 0.85) * 0.85, albedo, F0, rough, metal);
    color += evalLight(N, V, vec3(0.10, 0.35, -0.95), vec3(1.0, 0.48, 0.18) * 1.35, albedo, F0, rough, metal);
    float hemi = clamp(N.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 ambient = mix(vec3(0.035, 0.038, 0.045), vec3(0.20, 0.22, 0.27), hemi);
    color += diffuseColor * ambient * 0.85;
    vec3 R = reflect(-V, N);
    float fres = pow(1.0 - max(dot(N, V), 0.0), 4.0);
    color += env(R, rough) * (F0 * 0.8 + fres * 0.28) * (0.35 + metal * 0.65);
    color += pow(uEmissive, vec3(2.2));
    color = aces(color);
    color = pow(color, vec3(1.0 / 2.2));
    float rim = pow(1.0 - max(dot(N, V), 0.0), 2.6);
    color = mix(color, vec3(1.0, 0.72, 0.36), clamp(rim * uSelected * 0.9, 0.0, 1.0));
    if (uWeightBone >= 0) {
        float w = clamp(vWeight, 0.0, 1.0);
        vec3 heat = mix(mix(vec3(0.04, 0.18, 0.9), vec3(0.1, 0.9, 0.55), smoothstep(0.0, 0.55, w)),
                        mix(vec3(1.0, 0.8, 0.2), vec3(1.0, 0.2, 0.12), smoothstep(0.55, 1.0, w)),
                        smoothstep(0.45, 0.9, w));
        color = mix(color * 0.4, heat, smoothstep(0.0, 0.12, w) * 0.9);
    }
    fragColor = vec4(color, uOpacity);
}
"""

        private const val LINE_VERT = """
#version 300 es
precision highp float;
layout(location=0) in vec3 aPos;
uniform mat4 uViewProj;
uniform float uPointSize;
void main() {
    gl_Position = uViewProj * vec4(aPos, 1.0);
    gl_PointSize = uPointSize;
}
"""

        private const val LINE_FRAG = """
#version 300 es
precision highp float;
uniform vec4 uColor;
uniform int uIsPoint;
out vec4 fragColor;
void main() {
    if (uIsPoint == 1) {
        vec2 c = gl_PointCoord * 2.0 - 1.0;
        if (dot(c, c) > 1.0) discard;
    }
    fragColor = uColor;
}
"""

        private const val SOLID_VERT = """
#version 300 es
precision highp float;
layout(location=0) in vec3 aPos;
layout(location=1) in vec3 aNormal;
uniform mat4 uModel;
uniform mat4 uViewProj;
out vec3 vNormal;
void main() {
    vNormal = normalize(mat3(uModel) * aNormal);
    gl_Position = uViewProj * uModel * vec4(aPos, 1.0);
}
"""

        private const val SOLID_FRAG = """
#version 300 es
precision highp float;
in vec3 vNormal;
uniform vec4 uColor;
out vec4 fragColor;
void main() {
    float shade = 0.72 + 0.28 * abs(normalize(vNormal).y);
    fragColor = vec4(uColor.rgb * shade, uColor.a);
}
"""

        private const val GRID_VERT = """
#version 300 es
precision highp float;
layout(location=0) in vec3 aPos;
uniform mat4 uViewProj;
out vec3 vWorld;
void main() {
    vWorld = vec3(aPos.x * 22.0, 0.0, aPos.z * 22.0);
    gl_Position = uViewProj * vec4(vWorld, 1.0);
}
"""

        private const val GRID_FRAG = """
#version 300 es
precision highp float;
in vec3 vWorld;
uniform vec3 uCam;
uniform float uLight;
out vec4 fragColor;
float grid(vec2 p, float scale) {
    vec2 coord = p / scale;
    vec2 g = abs(fract(coord - 0.5) - 0.5) / fwidth(coord);
    return 1.0 - min(min(g.x, g.y), 1.0);
}
void main() {
    vec2 p = vWorld.xz;
    float minor = grid(p, 0.25) * 0.30;
    float major = grid(p, 1.0) * 0.55;
    float fade = 1.0 - smoothstep(6.0, 20.0, length(p));
    float alpha = max(minor, major) * fade * 0.55;
    vec3 darkCol = mix(vec3(0.36, 0.40, 0.45), vec3(0.62, 0.66, 0.72), major);
    vec3 lightCol = mix(vec3(0.60, 0.58, 0.54), vec3(0.38, 0.37, 0.34), major);
    vec3 col = mix(darkCol, lightCol, uLight);
    float axisX = 1.0 - min(abs(p.y) / max(fwidth(p.y), 1e-4), 1.0);
    float axisZ = 1.0 - min(abs(p.x) / max(fwidth(p.x), 1e-4), 1.0);
    col = mix(col, mix(vec3(0.95, 0.35, 0.32), vec3(0.72, 0.22, 0.20), uLight), axisX * 0.55 * fade);
    col = mix(col, mix(vec3(0.32, 0.55, 0.95), vec3(0.16, 0.32, 0.72), uLight), axisZ * 0.55 * fade);
    alpha = max(alpha, max(axisX, axisZ) * fade * 0.45);
    fragColor = vec4(col, alpha);
}
"""

        private const val BG_VERT = """
#version 300 es
precision highp float;
uniform vec2 uRes;
out vec2 vUv;
void main() {
    vec2 p = vec2(
        gl_VertexID == 1 ? 3.0 : -1.0,
        gl_VertexID == 2 ? 3.0 : -1.0
    );
    vUv = p * 0.5 + 0.5;
    gl_Position = vec4(p, 0.0, 1.0);
}
"""

        private const val BG_FRAG = """
#version 300 es
precision highp float;
in vec2 vUv;
uniform vec2 uRes;
uniform float uLight;
out vec4 fragColor;
void main() {
    vec2 uv = vUv;
    vec3 bottom = vec3(0.020, 0.023, 0.028);
    vec3 top = vec3(0.055, 0.065, 0.082);
    vec3 col = mix(bottom, top, smoothstep(0.0, 1.0, uv.y));
    float glow = exp(-length((uv - vec2(0.68, 0.80)) * vec2(1.5, 2.0)) * 3.2);
    col += vec3(0.30, 0.17, 0.06) * glow * 0.30;
    float cool = exp(-length((uv - vec2(0.18, 0.25)) * vec2(2.0, 2.4)) * 3.5);
    col += vec3(0.04, 0.10, 0.14) * cool * 0.8;
    float vignette = smoothstep(0.98, 0.25, length(uv - 0.5));
    col *= mix(0.72, 1.0, vignette);
    // Warm-paper canvas for the light theme: same composition, inverted value.
    vec3 lBottom = vec3(0.988, 0.980, 0.960);
    vec3 lTop = vec3(0.918, 0.906, 0.878);
    vec3 lightCol = mix(lBottom, lTop, smoothstep(0.0, 1.0, uv.y));
    lightCol -= vec3(0.06, 0.10, 0.14) * glow * 0.30;
    lightCol -= vec3(0.03, 0.05, 0.07) * cool * 0.55;
    lightCol *= mix(0.90, 1.0, vignette);
    col = mix(col, lightCol, uLight);
    fragColor = vec4(col, 1.0);
}
"""

        private const val DEPTH_VERT = """
#version 300 es
precision highp float;
layout(location=0) in vec3 aPos;
layout(location=1) in vec3 aNormal;
layout(location=3) in vec4 aJoints;
layout(location=4) in vec4 aWeights;
uniform mat4 uModel;
uniform mat4 uLightVP;
uniform mat4 uBones[48];
uniform bool uSkinned;
void main() {
    vec4 p = vec4(aPos, 1.0);
    vec3 n = aNormal;
    if (uSkinned) {
        mat4 skin =
            uBones[int(aJoints.x + 0.5)] * aWeights.x +
            uBones[int(aJoints.y + 0.5)] * aWeights.y +
            uBones[int(aJoints.z + 0.5)] * aWeights.z +
            uBones[int(aJoints.w + 0.5)] * aWeights.w;
        p = skin * p;
        n = mat3(skin) * n;
    }
    vec3 world = (uModel * p).xyz;
    // Normal offset pushes the caster a few millimetres along its own normal,
    // which removes self-shadow acne on props that sit flush with the body.
    world += normalize(mat3(uModel) * n) * 0.012;
    gl_Position = uLightVP * vec4(world, 1.0);
}
"""

        private const val DEPTH_FRAG = """
#version 300 es
precision highp float;
void main() {}
"""

        private const val SHADOW_VERT = """
#version 300 es
precision highp float;
layout(location=0) in vec2 aPos;
uniform mat4 uViewProj;
uniform mat4 uModel;
out vec2 vPos;
void main() {
    vPos = aPos;
    gl_Position = uViewProj * uModel * vec4(aPos.x, 0.0, aPos.y, 1.0);
}
"""

        private const val SHADOW_FRAG = """
#version 300 es
precision highp float;
in vec2 vPos;
out vec4 fragColor;
void main() {
    float d = length(vPos);
    float a = pow(1.0 - smoothstep(0.0, 1.0, d), 2.2) * 0.32;
    fragColor = vec4(0.0, 0.0, 0.0, a);
}
"""
    }
}
