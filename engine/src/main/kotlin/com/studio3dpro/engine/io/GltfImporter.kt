package com.studio3dpro.engine.io

import com.studio3dpro.engine.anim.AnimKey
import com.studio3dpro.engine.anim.AnimTrack
import com.studio3dpro.engine.anim.AnimationClip
import com.studio3dpro.engine.anim.ChannelType
import com.studio3dpro.engine.anim.Interp
import com.studio3dpro.engine.math.Mat4
import com.studio3dpro.engine.math.Quat
import com.studio3dpro.engine.math.Vec2
import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.math.Vec4
import com.studio3dpro.engine.mesh.Mesh
import com.studio3dpro.engine.mesh.SkinWeights
import com.studio3dpro.engine.scene.Bone
import com.studio3dpro.engine.scene.Material
import com.studio3dpro.engine.scene.Scene
import com.studio3dpro.engine.scene.SceneObject
import com.studio3dpro.engine.scene.Skeleton
import com.studio3dpro.engine.scene.TextureData
import com.studio3dpro.engine.scene.Transform
import kotlin.math.max
import kotlin.math.pow

data class ModelImportResult(
    val scene: Scene,
    val warnings: List<String> = emptyList()
)

class GltfImporter {

    private class NodeInfo(
        val index: Int,
        val name: String,
        var parent: Int = -1,
        val children: MutableList<Int> = mutableListOf(),
        val meshIndex: Int,
        val skinIndex: Int,
        val local: Transform,
        var world: Mat4 = Mat4.identity()
    )

    data class Context(
        val json: JsonValue.Obj,
        val bin: ByteArray?,
        val name: String,
        val external: ((String) -> ByteArray?)?,
        val warnings: MutableList<String> = mutableListOf()
    )

    fun import(bytes: ByteArray, name: String = "Model", external: ((String) -> ByteArray?)? = null): ModelImportResult {
        val container = Glb.read(bytes)
        return import(container, name, external)
    }

    fun import(container: Glb.Container, name: String = "Model", external: ((String) -> ByteArray?)? = null): ModelImportResult {
        val ctx = Context(container.json, container.bin, name, external)
        val scene = buildScene(ctx)
        return ModelImportResult(scene, ctx.warnings.toList())
    }

    private fun buildScene(ctx: Context): Scene {
        val json = ctx.json
        val scene = Scene()
        scene.name = ctx.name
        val buffers = readBuffers(ctx)
        val reader = GltfAccessorReader(json, buffers)
        val nodes = parseNodes(ctx)
        val nodeByIndex = nodes.associateBy { it.index }
        val sceneNodes = json.arr("scenes")?.get(0)?.arr("nodes")?.items?.mapNotNull { it.asInt } ?: emptyList()
        val roots = if (sceneNodes.isNotEmpty()) sceneNodes else nodes.filter { it.parent < 0 }.map { it.index }
        for (root in roots) nodeByIndex[root]?.let { computeWorld(it, Mat4.identity(), nodeByIndex, ctx) }

        // Build skeleton from the first skin.
        val skins = json.arr("skins")?.items ?: emptyList()
        val skinJoints: List<List<Int>> = skins.map { skin ->
            skin.arr("joints")?.items?.mapNotNull { it.asInt } ?: emptyList()
        }
        var skeleton: Skeleton? = null
        var jointToBone = HashMap<Int, Int>()
        var skinIndexUsed = -1
        val firstSkinnedNode = nodes.firstOrNull { it.skinIndex >= 0 && it.meshIndex >= 0 }
        if (firstSkinnedNode != null && firstSkinnedNode.skinIndex < skins.size) {
            skinIndexUsed = firstSkinnedNode.skinIndex
            val joints = skinJoints[skinIndexUsed]
            if (joints.isNotEmpty()) {
                val jointSet = joints.toHashSet()
                val order = ArrayList<Int>()
                fun visit(nodeIndex: Int) {
                    if (nodeIndex !in jointSet || nodeIndex in order) return
                    val parent = nodeByIndex[nodeIndex]?.parent ?: -1
                    if (parent in jointSet) visit(parent)
                    order.add(nodeIndex)
                }
                joints.forEach { visit(it) }
                for (j in joints) if (j !in order) order.add(j)
                if (joints.size > 48) {
                    throw IllegalArgumentException(
                        "This rig has ${joints.size} bones; the viewport supports up to 48 per skin. " +
                            "Merge finger or twist bones before importing."
                    )
                }
                val sk = Skeleton()
                val height = modelHeight(nodes, nodeByIndex)
                for (nodeIndex in order) {
                    val node = nodeByIndex[nodeIndex] ?: continue
                    val parentJointNode = generateSequence(node.parent) { p -> nodeByIndex[p]?.parent }
                        .firstOrNull { it in jointSet }
                    val parentBone = parentJointNode?.let { jointToBone[it] } ?: -1
                    val parentWorld = if (parentJointNode != null) nodeByIndex[parentJointNode]!!.world else Mat4.identity()
                    val localMatrix = parentWorld.inverse() * node.world
                    val (t, r, s) = localMatrix.decompose()
                    val childJoint = node.children.firstOrNull { it in jointSet }
                    val length = if (childJoint != null) {
                        node.world.translation().distanceTo(nodeByIndex[childJoint]!!.world.translation())
                            .coerceAtLeast(height * 0.01f)
                    } else height * 0.06f
                    val bone = Bone(node.name.ifBlank { "Bone_$nodeIndex" }, parentBone, t, r, length)
                    jointToBone[nodeIndex] = sk.bones.size
                    sk.bones.add(bone)
                }
                skeleton = sk
                scene.skeleton = sk
            }
        }
        if (skins.size > 1) ctx.warnings.add("Multiple skins found; only the first is used.")

        // Materials.
        val materials = json.arr("materials")?.items?.mapIndexed { i, m -> parseMaterial(ctx, m, i) }
            ?: emptyList()

        // Meshes as scene objects.
        val meshAccessors = json.arr("meshes")?.items ?: emptyList()
        for (node in nodes) {
            if (node.meshIndex < 0) continue
            val meshDef = meshAccessors.getOrNull(node.meshIndex) ?: continue
            val primitives = meshDef.arr("primitives")?.items ?: continue
            val isSkinned = node.skinIndex >= 0 && skeleton != null && node.skinIndex == skinIndexUsed
            val nodeMatrix = if (isSkinned) Mat4.identity() else node.world
            primitives.forEachIndexed { primitiveIndex, primitive ->
                val mesh = importPrimitive(ctx, reader, primitive, if (isSkinned) jointToBone else emptyMap(), skinJoints.getOrElse(node.skinIndex) { emptyList() })
                    ?: return@forEachIndexed
                if (!isSkinned) mesh.transform(nodeMatrix)
                val matIndex = primitive.int("material") ?: -1
                val material = materials.getOrNull(matIndex)?.copy() ?: Material()
                val partName = node.name.ifBlank { meshDef.str("name") ?: "Mesh" } +
                    if (primitives.size > 1) "_${primitiveIndex + 1}" else ""
                val obj = SceneObject(
                    scene.nextObjectId++,
                    partName,
                    mesh,
                    material,
                    if (isSkinned) Transform() else Transform(), // transforms are baked
                    true
                )
                scene.objects.add(obj)
            }
        }

        // Animations.
        val animations = json.arr("animations")?.items ?: emptyList()
        for ((animIndex, anim) in animations.withIndex()) {
            val clip = importAnimation(ctx, reader, anim, jointToBone, nodes, skeleton)
            if (clip != null && clip.tracks.isNotEmpty()) {
                clip.name = anim.str("name") ?: "Animation ${animIndex + 1}"
                scene.clips.add(clip)
            }
        }
        if (scene.clips.isNotEmpty()) scene.activeClip = 0
        scene.touch()
        return scene
    }

    private fun readBuffers(ctx: Context): List<ByteArray> {
        val defs = ctx.json.arr("buffers")?.items ?: return emptyList()
        return defs.mapIndexed { index, def ->
            val uri = def.str("uri")
            when {
                uri == null -> ctx.bin ?: ByteArray(0).also {
                    ctx.warnings.add("Buffer $index has no URI and no GLB BIN chunk.")
                }
                uri.startsWith("data:") -> decodeDataUri(uri) ?: ByteArray(0)
                else -> ctx.external?.invoke(uri) ?: ByteArray(0).also {
                    ctx.warnings.add("Missing external buffer '$uri'.")
                }
            }
        }
    }

    private fun parseNodes(ctx: Context): List<NodeInfo> {
        val arr = ctx.json.arr("nodes")?.items ?: emptyList()
        val out = arr.mapIndexed { i, n ->
            val matrix = n.arr("matrix")?.floatArray()
            val local = if (matrix != null && matrix.size == 16) {
                val (t, r, s) = Mat4.fromFloatArray(matrix).decompose()
                Transform(t, r, s)
            } else {
                val t = n.arr("translation")?.floatArray()?.let { Vec3(it[0], it[1], it[2]) } ?: Vec3.ZERO
                val r = n.arr("rotation")?.floatArray()?.let {
                    val q = Vec4(it[0], it[1], it[2], it[3]).normalized()
                    Quat(q.x, q.y, q.z, q.w)
                } ?: Quat.IDENTITY
                val s = n.arr("scale")?.floatArray()?.let { Vec3(it[0], it[1], it[2]) } ?: Vec3.ONE
                Transform(t, r, s)
            }
            NodeInfo(
                index = i,
                name = n.str("name") ?: "Node $i",
                meshIndex = n.int("mesh") ?: -1,
                skinIndex = n.int("skin") ?: -1,
                local = local
            )
        }
        for (i in out.indices) {
            val children = arr[i].arr("children")?.items?.mapNotNull { it.asInt } ?: continue
            for (c in children) if (c in out.indices) {
                out[c].parent = i
                out[i].children.add(c)
            }
        }
        return out
    }

    private fun computeWorld(node: NodeInfo, parent: Mat4, byIndex: Map<Int, NodeInfo>, ctx: Context) {
        node.world = parent * node.local.matrix()
        for (c in node.children) byIndex[c]?.let { computeWorld(it, node.world, byIndex, ctx) }
    }

    private fun modelHeight(nodes: List<NodeInfo>, byIndex: Map<Int, NodeInfo>): Float {
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (n in nodes) {
            val p = n.world.translation()
            minY = minOf(minY, p.y)
            maxY = maxOf(maxY, p.y)
        }
        if (minY > maxY) return 1f
        return max(maxY - minY, 0.05f)
    }

    private fun importPrimitive(
        ctx: Context,
        reader: GltfAccessorReader,
        primitive: JsonValue,
        jointToBone: Map<Int, Int>,
        skinJoints: List<Int>
    ): Mesh? {
        val attrs = primitive.obj("attributes") ?: run {
            ctx.warnings.add("Primitive without attributes skipped.")
            return null
        }
        val posIdx = attrs.int("POSITION") ?: run {
            ctx.warnings.add("Primitive without POSITION skipped.")
            return null
        }
        val pos = reader.read(posIdx)
        val mesh = Mesh()
        for (i in 0 until pos.count) mesh.addVertex(pos.vec3(i))
        val uvIndex = attrs.int("TEXCOORD_0")
        val uv = uvIndex?.let { reader.read(it) }
        val indices = primitive.int("indices")?.let { reader.read(it) }
        val mode = primitive.int("mode") ?: 4
        val idxCount = indices?.count ?: pos.count
        fun idx(i: Int): Int = if (indices != null) indices.ints[i] else i
        val triangles = ArrayList<IntArray>()
        when (mode) {
            4 -> {
                var i = 0
                while (i + 2 < idxCount) {
                    triangles.add(intArrayOf(idx(i), idx(i + 1), idx(i + 2)))
                    i += 3
                }
            }
            5 -> {
                for (i in 0 until idxCount - 2) {
                    val a = idx(i); val b = idx(i + 1); val c = idx(i + 2)
                    triangles.add(if (i % 2 == 0) intArrayOf(a, b, c) else intArrayOf(b, a, c))
                }
            }
            6 -> {
                for (i in 1 until idxCount - 1) triangles.add(intArrayOf(idx(0), idx(i), idx(i + 1)))
            }
            else -> {
                ctx.warnings.add("Unsupported primitive mode $mode.")
                return null
            }
        }
        for (t in triangles) {
            if (t.any { it !in 0 until pos.count }) continue
            val uvs = FloatArray(6)
            for (c in 0..2) {
                val u = uv?.vec2(t[c]) ?: (0f to 0f)
                uvs[c * 2] = u.first
                uvs[c * 2 + 1] = u.second
            }
            mesh.addFace(t, uvs)
        }
        mesh.removeDegenerateFaces()
        val jointsIdx = attrs.int("JOINTS_0")
        val weightsIdx = attrs.int("WEIGHTS_0")
        if (jointsIdx != null && weightsIdx != null && jointToBone.isNotEmpty()) {
            val j = reader.read(jointsIdx)
            val w = reader.read(weightsIdx)
            val bones = IntArray(pos.count * 4)
            val weights = FloatArray(pos.count * 4)
            for (v in 0 until pos.count) {
                var sum = 0f
                for (k in 0..3) {
                    val jointNumber = j.ints.getOrElse(v * j.components + k) { 0 }
                    val skinJointNode = skinJoints.getOrNull(jointNumber) ?: -1
                    val bone = jointToBone[skinJointNode] ?: 0
                    bones[v * 4 + k] = bone
                    weights[v * 4 + k] = w.floats.getOrElse(v * w.components + k) { 0f }
                    sum += weights[v * 4 + k]
                }
                if (sum < 1e-6f) {
                    bones[v * 4] = 0; weights[v * 4] = 1f
                    for (k in 1..3) weights[v * 4 + k] = 0f
                } else {
                    for (k in 0..3) weights[v * 4 + k] /= sum
                }
            }
            mesh.skin = SkinWeights(bones, weights)
        }
        mesh.markDirty()
        return mesh
    }

    private fun parseMaterial(ctx: Context, material: JsonValue, index: Int): Material {
        val pbr = material.obj("pbrMetallicRoughness")
        val base = pbr?.arr("baseColorFactor")?.floatArray()
        val color = if (base != null && base.size >= 3) {
            Vec3(srgb(base[0]), srgb(base[1]), srgb(base[2]))
        } else Vec3(0.8f, 0.8f, 0.8f)
        val alpha = base?.getOrNull(3) ?: 1f
        val metallic = pbr?.optFloat("metallicFactor", 1f) ?: 1f
        val roughness = pbr?.optFloat("roughnessFactor", 1f) ?: 1f
        val emissive = material.arr("emissiveFactor")?.floatArray()?.let {
            if (it.size >= 3) Vec3(srgb(it[0]), srgb(it[1]), srgb(it[2])) else Vec3.ZERO
        } ?: Vec3.ZERO
        val strength = material.obj("extensions")?.obj("KHR_materials_emissive_strength")
            ?.optFloat("emissiveStrength", 1f) ?: 1f
        var texture: TextureData? = null
        val texInfo = pbr?.obj("baseColorTexture")
        val textureIndex = texInfo?.int("index") ?: -1
        if (textureIndex >= 0) {
            val tex = ctx.json.arr("textures")?.get(textureIndex)
            val imageIndex = tex?.int("source") ?: -1
            if (imageIndex >= 0) {
                val image = ctx.json.arr("images")?.get(imageIndex)
                val mime = image?.str("mimeType") ?: "image/png"
                val bytes = image?.let { img ->
                    val view = img.int("bufferView")
                    if (view != null) {
                        readBufferView(ctx, view)
                    } else {
                        val uri = img.str("uri")
                        when {
                            uri == null -> null
                            uri.startsWith("data:") -> decodeDataUri(uri)
                            else -> ctx.external?.invoke(uri)
                        }
                    }
                }
                if (bytes != null) {
                    texture = TextureData(mime, bytes, image?.str("name") ?: "baseColor")
                } else ctx.warnings.add("Could not load texture for material $index.")
            }
        }
        val doubleSided = material.bool("doubleSided") ?: false
        val name = material.str("name") ?: "Material ${index + 1}"
        return Material(
            name = name,
            baseColor = color,
            metallic = metallic,
            roughness = roughness,
            emissive = emissive,
            emissiveStrength = strength,
            opacity = alpha,
            doubleSided = doubleSided,
            baseColorTexture = texture
        )
    }

    private fun readBufferView(ctx: Context, index: Int): ByteArray? {
        val view = ctx.json.arr("bufferViews")?.get(index) ?: return null
        val bufferIndex = view.int("buffer") ?: return null
        val bufferDef = ctx.json.arr("buffers")?.get(bufferIndex)
        val buffer = if (bufferDef?.str("uri") == null) ctx.bin else {
            val uri = bufferDef?.str("uri") ?: return null
            if (uri.startsWith("data:")) decodeDataUri(uri) else ctx.external?.invoke(uri)
        } ?: return null
        val offset = view.int("byteOffset") ?: 0
        val length = view.int("byteLength") ?: 0
        if (offset < 0 || offset + length > buffer.size) return null
        return buffer.copyOfRange(offset, offset + length)
    }

    private fun importAnimation(
        ctx: Context,
        reader: GltfAccessorReader,
        anim: JsonValue,
        jointToBone: Map<Int, Int>,
        nodes: List<NodeInfo>,
        skeleton: Skeleton?
    ): AnimationClip? {
        if (skeleton == null) return null
        val channels = anim.arr("channels")?.items ?: return null
        val samplers = anim.arr("samplers")?.items ?: return null
        val nodeByIndex = nodes.associateBy { it.index }
        val pending = ArrayList<Triple<Int, ChannelType, AnimTrack>>()
        var minTime = Float.MAX_VALUE
        var maxTime = -Float.MAX_VALUE
        val parsed = ArrayList<Triple<Int, String, Pair<AnimTrack, Int>>>()
        data class Parsed(val bone: Int, val path: String, val input: FloatArray, val output: GltfAccessorData, val interp: String)
        val data = ArrayList<Parsed>()
        for (channel in channels) {
            val target = channel.obj("target") ?: continue
            val nodeIndex = target.int("node") ?: continue
            val path = target.str("path") ?: continue
            if (path !in listOf("translation", "rotation", "scale")) continue
            val bone = jointToBone[nodeIndex] ?: continue
            val samplerIndex = channel.int("sampler") ?: continue
            val sampler = samplers.getOrNull(samplerIndex) ?: continue
            val input = sampler.int("input")?.let { reader.read(it) } ?: continue
            val output = sampler.int("output")?.let { reader.read(it) } ?: continue
            val interp = sampler.str("interpolation") ?: "LINEAR"
            if (input.count == 0 || output.count == 0) continue
            if (input.count > 0) {
                minTime = minOf(minTime, input.floats[0])
                maxTime = maxOf(maxTime, input.floats[input.count - 1])
            }
            data.add(Parsed(bone, path, input.floats.copyOf(input.count), output, interp))
        }
        if (data.isEmpty()) return null
        if (minTime > maxTime) return null
        val clip = AnimationClip("Animation", maxTime - minTime, true)
        for (entry in data) {
            val node = nodeByIndex[entry.bone.let { boneIndex -> jointToBone.entries.firstOrNull { it.value == boneIndex }?.key } ?: -1]
            val restT = node?.local?.position ?: Vec3.ZERO
            val restR = node?.local?.rotation ?: Quat.IDENTITY
            val restS = node?.local?.scale ?: Vec3.ONE
            val type = when (entry.path) {
                "translation" -> ChannelType.TRANSLATION
                "rotation" -> ChannelType.ROTATION
                else -> ChannelType.SCALE
            }
            val track = AnimTrack(entry.bone, type)
            val cubic = entry.interp == "CUBICSPLINE"
            val step = entry.interp == "STEP"
            val outComp = entry.output.components
            val stride = if (cubic) outComp * 3 else outComp
            val frames = minOf(entry.input.size, entry.output.count / (if (cubic) 3 else 1))
            for (i in 0 until frames) {
                val offset = if (cubic) i * outComp * 3 + outComp else i * outComp
                if (offset + outComp > entry.output.floats.size) break
                val time = (entry.input[i] - minTime).coerceAtLeast(0f)
                val value = when (type) {
                    ChannelType.TRANSLATION -> {
                        val v = Vec3(
                            entry.output.floats[offset] - restT.x,
                            entry.output.floats[offset + 1] - restT.y,
                            entry.output.floats[offset + 2] - restT.z
                        )
                        Vec4(v.x, v.y, v.z, 0f)
                    }
                    ChannelType.ROTATION -> {
                        val animQ = Quat(
                            entry.output.floats[offset],
                            entry.output.floats[offset + 1],
                            entry.output.floats[offset + 2],
                            entry.output.floats[offset + 3]
                        ).normalized()
                        val delta = restR.conjugate() * animQ
                        Vec4(delta.x, delta.y, delta.z, delta.w)
                    }
                    ChannelType.SCALE -> {
                        val s = Vec3(
                            entry.output.floats[offset] / max(restS.x, 1e-6f),
                            entry.output.floats[offset + 1] / max(restS.y, 1e-6f),
                            entry.output.floats[offset + 2] / max(restS.z, 1e-6f)
                        )
                        Vec4(s.x, s.y, s.z, 0f)
                    }
                }
                val k = AnimKey(time, value, if (step) Interp.STEP else if (cubic) Interp.BEZIER else Interp.LINEAR)
                track.keys.add(k)
            }
            track.sort()
            if (track.keys.isNotEmpty()) clip.tracks.add(track)
        }
        clip.duration = maxTime - minTime
        return clip
    }

    private fun srgb(c: Float): Float = if (c <= 0.0031308f) c * 12.92f else
        1.055f * c.toDouble().pow(1.0 / 2.4).toFloat() - 0.055f
}
