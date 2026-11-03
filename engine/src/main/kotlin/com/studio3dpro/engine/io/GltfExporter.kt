package com.studio3dpro.engine.io

import com.studio3dpro.engine.anim.ChannelType
import com.studio3dpro.engine.anim.PoseSampler
import com.studio3dpro.engine.math.Mat4
import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.math.Vec4
import com.studio3dpro.engine.mesh.Mesh
import com.studio3dpro.engine.scene.Scene
import com.studio3dpro.engine.scene.SceneObject
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

class GltfExporter {

    data class Options(
        val clipIndices: List<Int>? = null,
        val sampleRate: Float = 30f,
        val includeTextures: Boolean = true,
        val includeSkins: Boolean = true,
        val exportAllClips: Boolean = true
    )

    private lateinit var scene: Scene
    private lateinit var options: Options
    private val bin = GltfBinaryBuilder()
    private val bufferViews = JsonValue.Arr()
    private val accessors = JsonValue.Arr()
    private val meshes = JsonValue.Arr()
    private val nodes = JsonValue.Arr()
    private val materials = JsonValue.Arr()
    private val animations = JsonValue.Arr()
    private var samplerAdded = false
    private val textures = JsonValue.Arr()
    private val images = JsonValue.Arr()
    private val samplers = JsonValue.Arr()
    private var armatureNode = -1
    private val boneNodeIndices = ArrayList<Int>()
    private var skinIndex = -1
    private val extensionsUsed = LinkedHashSet<String>()

    fun exportGlb(scene: Scene, options: Options = Options()): ByteArray {
        if (options.includeSkins && scene.skeleton != null && scene.skeleton!!.size > 48) {
            throw IllegalArgumentException(
                "The scene rig has ${scene.skeleton!!.size} bones; GLB skinning supports up to 48. " +
                    "Disable skin export or reduce the rig."
            )
        }
        this.scene = scene
        this.options = options
        bufferViews.items.clear(); accessors.items.clear(); meshes.items.clear()
        nodes.items.clear(); materials.items.clear(); animations.items.clear()
        textures.items.clear(); images.items.clear(); samplers.items.clear()
        materialIndexByObject.clear()
        bin.align()
        extensionsUsed.clear()
        boneNodeIndices.clear()
        skinIndex = -1
        armatureNode = -1
        skinJson = null
        val rootNodes = ArrayList<Int>()

        buildSkeletonNodes(rootNodes)
        if (scene.skeleton != null && boneNodeIndices.isNotEmpty() && options.includeSkins) {
            buildSkin()
        }
        val objectNodes = ArrayList<Int>()
        for (obj in scene.objects) {
            if (obj.mesh == null || !obj.visible) continue
            val idx = buildObjectNode(obj)
            if (idx >= 0) objectNodes.add(idx)
        }
        buildAnimations()
        rootNodes.addAll(objectNodes)

        val nodeArray = JsonValue.Arr()
        nodeArray.items.addAll(nodes.items)
        val buffer = bin.bytes()
        val rootJson = JsonValue.objOf(
            "asset" to JsonValue.objOf(
                "version" to JsonValue.of("2.0"),
                "generator" to JsonValue.of("3D Studio Pro 1.0"),
                "copyright" to JsonValue.of("Made with 3D Studio Pro")
            ),
            "scene" to JsonValue.of(0),
            "scenes" to JsonValue.arrOf(JsonValue.objOf("name" to JsonValue.of(scene.name), "nodes" to JsonValue.arrOf(rootNodes.map { JsonValue.of(it) }))),
            "nodes" to nodeArray
        )
        if (materials.items.isNotEmpty()) rootJson.fields["materials"] = materials
        if (meshes.items.isNotEmpty()) rootJson.fields["meshes"] = meshes
        if (bufferViews.items.isNotEmpty()) rootJson.fields["bufferViews"] = bufferViews
        if (accessors.items.isNotEmpty()) rootJson.fields["accessors"] = accessors
        if (buffer.isNotEmpty()) rootJson.fields["buffers"] = JsonValue.arrOf(
            JsonValue.objOf("byteLength" to JsonValue.of(buffer.size))
        )
        if (textures.items.isNotEmpty()) {
            rootJson.fields["textures"] = textures
            rootJson.fields["images"] = images
            rootJson.fields["samplers"] = samplers
        }
        if (skinIndex >= 0) {
            // Skin JSON is appended after creation.
            rootJson.fields["skins"] = JsonValue.arrOf(skinJson!!)
        }
        if (animations.items.isNotEmpty()) rootJson.fields["animations"] = animations
        if (extensionsUsed.isNotEmpty()) {
            rootJson.fields["extensionsUsed"] = JsonValue.arrOf(extensionsUsed.sorted().map { JsonValue.of(it) })
        }
        val jsonText = Json.stringify(rootJson, false)
        return Glb.write(jsonText, buffer)
    }

    private var skinJson: JsonValue.Obj? = null

    private fun buildSkeletonNodes(rootNodes: MutableList<Int>) {
        val skeleton = scene.skeleton ?: return
        val boneNodes = IntArray(skeleton.size)
        val rest = skeleton.restWorldMatrices()
        for (i in skeleton.bones.indices) {
            boneNodes[i] = nodes.size
            nodes.items.add(JsonValue.objOf("name" to JsonValue.of(skeleton.bones[i].name)))
        }
        for (i in skeleton.bones.indices) {
            val bone = skeleton.bones[i]
            val node = nodes.items[boneNodes[i]].asObj!!
            node.fields["translation"] = JsonValue.floats(bone.headLocal.toFloatArray())
            node.fields["rotation"] = JsonValue.floats(
                floatArrayOf(bone.restRotation.x, bone.restRotation.y, bone.restRotation.z, bone.restRotation.w)
            )
            val kids = skeleton.bones.indices.filter { skeleton.bones[it].parent == i }.map { JsonValue.of(boneNodes[it]) }
            if (kids.isNotEmpty()) node.fields["children"] = JsonValue.arrOf(kids)
        }
        val rootBones = skeleton.bones.indices.filter { skeleton.bones[it].parent < 0 }.map { JsonValue.of(boneNodes[it]) }
        armatureNode = nodes.size
        nodes.items.add(JsonValue.objOf(
            "name" to JsonValue.of("Armature"),
            "children" to JsonValue.arrOf(rootBones)
        ))
        rootNodes.add(armatureNode)
        boneNodeIndices.addAll(boneNodes.toList())
    }

    private fun buildSkin() {
        val skeleton = scene.skeleton ?: return
        if (boneNodeIndices.isEmpty()) return
        val rest = skeleton.restWorldMatrices()
        val ibm = FloatArray(skeleton.size * 16)
        for (i in skeleton.bones.indices) {
            val inv = rest[i].inverse()
            for (c in 0..15) ibm[i * 16 + c] = inv.m[c]
            // glTF requires an exact affine last row. Float inversion leaves
            // 1.0000001-style drift that strict validators and runtimes reject.
            ibm[i * 16 + 3] = 0f
            ibm[i * 16 + 7] = 0f
            ibm[i * 16 + 11] = 0f
            ibm[i * 16 + 15] = 1f
        }
        val ibmAccessor = addFloatAccessor(ibm, 16, "MAT4", target = null, bounds = false)
        skinIndex = 0
        skinJson = JsonValue.objOf(
            "name" to JsonValue.of("Skin"),
            "joints" to JsonValue.arrOf(boneNodeIndices.map { JsonValue.of(it) }),
            "inverseBindMatrices" to JsonValue.of(ibmAccessor),
            "skeleton" to JsonValue.of(armatureNode)
        )
    }

    private fun buildObjectNode(obj: SceneObject): Int {
        val mesh = obj.mesh ?: return -1
        if (mesh.faces.isEmpty()) return -1
        val skinned = mesh.skin != null && scene.skeleton != null && options.includeSkins
        val primitive = buildPrimitive(obj, skinned)
        val meshIndex = meshes.size
        meshes.items.add(JsonValue.objOf(
            "name" to JsonValue.of(obj.name),
            "primitives" to JsonValue.arrOf(primitive)
        ))
        val nodeIndex = nodes.size
        val node = JsonValue.objOf(
            "name" to JsonValue.of(obj.name.ifBlank { "Object" }),
            "mesh" to JsonValue.of(meshIndex)
        )
        if (skinned) {
            node.fields["skin"] = JsonValue.of(skinIndex)
        } else {
            node.fields["translation"] = JsonValue.floats(obj.transform.position.toFloatArray())
            node.fields["rotation"] = JsonValue.floats(
                floatArrayOf(
                    obj.transform.rotation.x, obj.transform.rotation.y,
                    obj.transform.rotation.z, obj.transform.rotation.w
                )
            )
            node.fields["scale"] = JsonValue.floats(obj.transform.scale.toFloatArray())
        }
        nodes.items.add(node)
        return nodeIndex
    }

    private fun buildPrimitive(obj: SceneObject, skinned: Boolean): JsonValue.Obj {
        val mesh = obj.mesh ?: Mesh()
        val corners = mesh.totalCorners()
        val pos = FloatArray(corners * 3)
        val nor = FloatArray(corners * 3)
        val uv = FloatArray(corners * 2)
        val jnt = IntArray(corners * 4)
        val wgt = FloatArray(corners * 4)
        val cornerNormals = mesh.cornerNormals(60f)
        val indices = ArrayList<Int>()
        var cornerIndex = 0
        for (f in mesh.faces.indices) {
            val face = mesh.faces[f]
            val start = cornerIndex
            for (c in face.indices) {
                val v = face[c]
                val p = mesh.vertices[v]
                pos[cornerIndex * 3] = p.x; pos[cornerIndex * 3 + 1] = p.y; pos[cornerIndex * 3 + 2] = p.z
                nor[cornerIndex * 3] = cornerNormals[cornerIndex * 3]
                nor[cornerIndex * 3 + 1] = cornerNormals[cornerIndex * 3 + 1]
                nor[cornerIndex * 3 + 2] = cornerNormals[cornerIndex * 3 + 2]
                uv[cornerIndex * 2] = mesh.faceUvs[f][c * 2]
                uv[cornerIndex * 2 + 1] = mesh.faceUvs[f][c * 2 + 1]
                if (skinned) {
                    val skin = mesh.skin
                    if (skin != null) {
                        for (k in 0..3) {
                            val weight = skin.weights[v * 4 + k]
                            // glTF requires zero-weight influences to reference
                            // joint 0, not a leftover bone index.
                            jnt[cornerIndex * 4 + k] =
                                if (weight <= 0f) 0 else skin.boneIndices[v * 4 + k].coerceIn(0, 63)
                            wgt[cornerIndex * 4 + k] = weight
                        }
                    }
                }
                cornerIndex++
            }
            for (i in 1 until face.size - 1) {
                indices.add(start); indices.add(start + i); indices.add(start + i + 1)
            }
        }
        val posAcc = addFloatAccessor(pos, 3, "VEC3", 34962, bounds = true)
        val norAcc = addFloatAccessor(nor, 3, "VEC3", 34962, bounds = false)
        val uvAcc = addFloatAccessor(uv, 2, "VEC2", 34962, bounds = false)
        val attributes = JsonValue.objOf(
            "POSITION" to JsonValue.of(posAcc),
            "NORMAL" to JsonValue.of(norAcc),
            "TEXCOORD_0" to JsonValue.of(uvAcc)
        )
        if (skinned) {
            attributes.fields["JOINTS_0"] = JsonValue.of(addUShortAccessor(jnt, 4, 34962))
            attributes.fields["WEIGHTS_0"] = JsonValue.of(addFloatAccessor(wgt, 4, "VEC4", 34962, bounds = false))
        }
        val idxArray = indices.toIntArray()
        val useUShort = corners <= 65535
        val idxAcc = if (useUShort) addUShortIndexAccessor(idxArray, 34963) else addUIntAccessor(idxArray, 34963)
        val materialIndex = addMaterial(obj)
        return JsonValue.objOf(
            "attributes" to attributes,
            "indices" to JsonValue.of(idxAcc),
            "material" to JsonValue.of(materialIndex),
            "mode" to JsonValue.of(4)
        )
    }

    private val materialIndexByObject = HashMap<Long, Int>()

    private fun addMaterial(obj: SceneObject): Int {
        materialIndexByObject[obj.id]?.let { return it }
        val mat = obj.material
        val pbr = JsonValue.objOf(
            "baseColorFactor" to JsonValue.floats(floatArrayOf(linear(mat.baseColor.x), linear(mat.baseColor.y), linear(mat.baseColor.z), mat.opacity)),
            "metallicFactor" to JsonValue.of(mat.metallic),
            "roughnessFactor" to JsonValue.of(mat.roughness)
        )
        if (mat.baseColorTexture != null && options.includeTextures) {
            val tex = mat.baseColorTexture!!
            val (offset, length) = bin.append(tex.bytes)
            val view = bufferViews.size
            bufferViews.items.add(JsonValue.objOf(
                "buffer" to JsonValue.of(0),
                "byteOffset" to JsonValue.of(offset),
                "byteLength" to JsonValue.of(length)
            ))
            val imageIndex = images.size
            images.items.add(JsonValue.objOf(
                "name" to JsonValue.of(tex.name),
                "mimeType" to JsonValue.of(tex.mimeType),
                "bufferView" to JsonValue.of(view)
            ))
            if (!samplerAdded) {
                samplers.items.add(JsonValue.objOf(
                    "magFilter" to JsonValue.of(9729),
                    "minFilter" to JsonValue.of(9987),
                    "wrapS" to JsonValue.of(10497),
                    "wrapT" to JsonValue.of(10497)
                ))
                samplerAdded = true
            }
            val textureIndex = textures.size
            textures.items.add(JsonValue.objOf("source" to JsonValue.of(imageIndex), "sampler" to JsonValue.of(0)))
            pbr.fields["baseColorTexture"] = JsonValue.objOf("index" to JsonValue.of(textureIndex))
        }
        val m = JsonValue.objOf(
            "name" to JsonValue.of(mat.name),
            "pbrMetallicRoughness" to pbr,
            "doubleSided" to JsonValue.of(mat.doubleSided)
        )
        if (mat.opacity < 0.999f) m.fields["alphaMode"] = JsonValue.of("BLEND")
        if (mat.emissive.lengthSq() > 1e-6f) {
            m.fields["emissiveFactor"] = JsonValue.floats(
                floatArrayOf(linear(mat.emissive.x), linear(mat.emissive.y), linear(mat.emissive.z))
            )
            if (mat.emissiveStrength != 1f) {
                extensionsUsed.add("KHR_materials_emissive_strength")
                m.fields["extensions"] = JsonValue.objOf(
                    "KHR_materials_emissive_strength" to
                        JsonValue.objOf("emissiveStrength" to JsonValue.of(mat.emissiveStrength))
                )
            }
        }
        val idx = materials.size
        materials.items.add(m)
        materialIndexByObject[obj.id] = idx
        return idx
    }

    private fun addFloatAccessor(values: FloatArray, components: Int, type: String, target: Int?, bounds: Boolean): Int {
        val (offset, length) = bin.append(floatsToBytes(values))
        val view = bufferViews.size
        val viewJson = JsonValue.objOf(
            "buffer" to JsonValue.of(0),
            "byteOffset" to JsonValue.of(offset),
            "byteLength" to JsonValue.of(length)
        )
        if (target != null) viewJson.fields["target"] = JsonValue.of(target)
        bufferViews.items.add(viewJson)
        val acc = JsonValue.objOf(
            "bufferView" to JsonValue.of(view),
            "componentType" to JsonValue.of(5126),
            "count" to JsonValue.of(values.size / components),
            "type" to JsonValue.of(type)
        )
        if (bounds) {
            val (mn, mx) = minMax(values, components)
            acc.fields["min"] = JsonValue.floats(mn)
            acc.fields["max"] = JsonValue.floats(mx)
        }
        val idx = accessors.size
        accessors.items.add(acc)
        return idx
    }

    private fun addUShortAccessor(values: IntArray, components: Int, target: Int? = null): Int {
        val (offset, length) = bin.append(ushortsToBytes(values))
        val view = bufferViews.size
        val viewJson = JsonValue.objOf(
            "buffer" to JsonValue.of(0),
            "byteOffset" to JsonValue.of(offset),
            "byteLength" to JsonValue.of(length)
        )
        if (target != null) viewJson.fields["target"] = JsonValue.of(target)
        bufferViews.items.add(viewJson)
        val idx = accessors.size
        accessors.items.add(JsonValue.objOf(
            "bufferView" to JsonValue.of(view),
            "componentType" to JsonValue.of(5123),
            "count" to JsonValue.of(values.size / components),
            "type" to JsonValue.of(if (components == 4) "VEC4" else "SCALAR")
        ))
        return idx
    }

    private fun addUShortIndexAccessor(values: IntArray, target: Int? = null): Int {
        val (offset, length) = bin.append(ushortsToBytes(values), alignment = 2)
        val view = bufferViews.size
        val viewJson = JsonValue.objOf(
            "buffer" to JsonValue.of(0),
            "byteOffset" to JsonValue.of(offset),
            "byteLength" to JsonValue.of(length)
        )
        if (target != null) viewJson.fields["target"] = JsonValue.of(target)
        bufferViews.items.add(viewJson)
        val idx = accessors.size
        accessors.items.add(JsonValue.objOf(
            "bufferView" to JsonValue.of(view),
            "componentType" to JsonValue.of(5123),
            "count" to JsonValue.of(values.size),
            "type" to JsonValue.of("SCALAR")
        ))
        return idx
    }

    private fun addUIntAccessor(values: IntArray, target: Int? = null): Int {
        val (offset, length) = bin.append(uintToBytes(values))
        val view = bufferViews.size
        val viewJson = JsonValue.objOf(
            "buffer" to JsonValue.of(0),
            "byteOffset" to JsonValue.of(offset),
            "byteLength" to JsonValue.of(length)
        )
        if (target != null) viewJson.fields["target"] = JsonValue.of(target)
        bufferViews.items.add(viewJson)
        val idx = accessors.size
        accessors.items.add(JsonValue.objOf(
            "bufferView" to JsonValue.of(view),
            "componentType" to JsonValue.of(5125),
            "count" to JsonValue.of(values.size),
            "type" to JsonValue.of("SCALAR")
        ))
        return idx
    }

    private fun addAnimationAccessor(values: FloatArray, components: Int, type: String): Int =
        addFloatAccessor(values, components, type, null, bounds = false)

    private fun buildAnimations() {
        val skeleton = scene.skeleton ?: return
        if (boneNodeIndices.isEmpty()) return
        val indices = if (options.exportAllClips) {
            scene.clips.indices.toList()
        } else options.clipIndices ?: listOf(scene.activeClip)
        for (clipIndex in indices) {
            val clip = scene.clips.getOrNull(clipIndex) ?: continue
            if (clip.duration <= 0f) continue
            val rate = options.sampleRate.coerceIn(5f, 120f)
            val frames = max(2, (clip.duration * rate).roundToInt() + 1)
            val times = FloatArray(frames) { i -> clip.duration * i / (frames - 1).toFloat() }
            val timeAcc = addAnimationAccessor(times, 1, "SCALAR")
            // min/max are required for animation input accessors.
            accessors.items[timeAcc].asObj!!.fields["min"] = JsonValue.floats(floatArrayOf(times.first()))
            accessors.items[timeAcc].asObj!!.fields["max"] = JsonValue.floats(floatArrayOf(times.last()))
            val samplerList = JsonValue.Arr()
            val channelList = JsonValue.Arr()
            val animatedBones = clip.tracks.map { it.bone }.distinct().filter { it in skeleton.bones.indices }
            val sampledPoses = Array(frames) { i ->
                val pose = com.studio3dpro.engine.scene.Pose(skeleton.size)
                clip.evaluate(times[i], pose, 1f)
                pose
            }
            for (boneIndex in animatedBones) {
                val trans = FloatArray(frames * 3)
                val rots = FloatArray(frames * 4)
                val scales = FloatArray(frames * 3)
                var prev = Vec4(0f, 0f, 0f, 1f)
                for (f in 0 until frames) {
                    val pose = sampledPoses[f]
                    val bp = pose.bones[boneIndex]
                    val bone = skeleton.bones[boneIndex]
                    val p = bone.headLocal + bp.translation
                    trans[f * 3] = p.x; trans[f * 3 + 1] = p.y; trans[f * 3 + 2] = p.z
                    var q = bone.restRotation * bp.rotation
                    var v = Vec4(q.x, q.y, q.z, q.w)
                    if ((v dot prev) < 0f) v = Vec4(-v.x, -v.y, -v.z, -v.w)
                    prev = v
                    rots[f * 4] = v.x; rots[f * 4 + 1] = v.y; rots[f * 4 + 2] = v.z; rots[f * 4 + 3] = v.w
                    scales[f * 3] = bp.scale.x; scales[f * 3 + 1] = bp.scale.y; scales[f * 3 + 2] = bp.scale.z
                }
                val tAcc = addAnimationAccessor(trans, 3, "VEC3")
                val rAcc = addAnimationAccessor(rots, 4, "VEC4")
                val sAcc = addAnimationAccessor(scales, 3, "VEC3")
                val nodeIndex = boneNodeIndices[boneIndex]
                val paths = listOf(
                    "translation" to tAcc,
                    "rotation" to rAcc,
                    "scale" to sAcc
                )
                for ((path, acc) in paths) {
                    val samplerIndex = samplerList.size
                    samplerList.items.add(JsonValue.objOf(
                        "input" to JsonValue.of(timeAcc),
                        "output" to JsonValue.of(acc),
                        "interpolation" to JsonValue.of("LINEAR")
                    ))
                    channelList.items.add(JsonValue.objOf(
                        "sampler" to JsonValue.of(samplerIndex),
                        "target" to JsonValue.objOf(
                            "node" to JsonValue.of(nodeIndex),
                            "path" to JsonValue.of(path)
                        )
                    ))
                }
            }
            animations.items.add(JsonValue.objOf(
                "name" to JsonValue.of(clip.name),
                "channels" to channelList,
                "samplers" to samplerList
            ))
        }
    }

    private fun linear(c: Float): Float = if (c <= 0.04045f) c / 12.92f else
        ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
}
