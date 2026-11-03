package com.studio3dpro.engine.io

import com.studio3dpro.engine.anim.AnimKey
import com.studio3dpro.engine.anim.AnimTrack
import com.studio3dpro.engine.anim.AnimationClip
import com.studio3dpro.engine.anim.AnimationLayer
import com.studio3dpro.engine.anim.ChannelType
import com.studio3dpro.engine.anim.Interp
import com.studio3dpro.engine.anim.PosePreset
import com.studio3dpro.engine.math.Quat
import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.math.Vec4
import com.studio3dpro.engine.mesh.Mesh
import com.studio3dpro.engine.mesh.SkinWeights
import com.studio3dpro.engine.rig.BoneConstraint
import com.studio3dpro.engine.rig.ConstraintType
import com.studio3dpro.engine.scene.Bone
import com.studio3dpro.engine.scene.BonePose
import com.studio3dpro.engine.scene.Material
import com.studio3dpro.engine.scene.Scene
import com.studio3dpro.engine.scene.SceneObject
import com.studio3dpro.engine.scene.Skeleton
import com.studio3dpro.engine.scene.TextureData
import com.studio3dpro.engine.scene.Transform
import java.util.Base64

/** Project (.studio3d) serialization: geometry, rig, animation and settings. */
object ProjectIO {
    const val FORMAT_VERSION = 1
    const val EXTENSION = "studio3d"

    fun save(scene: Scene): String {
        val root = JsonValue.Obj()
        root.fields["format"] = JsonValue.of("studio3d")
        root.fields["version"] = JsonValue.of(FORMAT_VERSION)
        root.fields["name"] = JsonValue.of(scene.name)
        root.fields["activeClip"] = JsonValue.of(scene.activeClip)
        root.fields["autosave"] = JsonValue.of(scene.autosaveEnabled)
        root.fields["objects"] = JsonValue.Arr(scene.objects.map { objJson(it) }.toMutableList())
        scene.skeleton?.let { root.fields["skeleton"] = skeletonJson(it) }
        root.fields["clips"] = JsonValue.Arr(scene.clips.map { clipJson(it) }.toMutableList())
        root.fields["layers"] = JsonValue.Arr(scene.layers.map { layer ->
            JsonValue.objOf(
                "name" to JsonValue.of(layer.name),
                "clipIndex" to JsonValue.of(layer.clipIndex),
                "weight" to JsonValue.of(layer.weight),
                "additive" to JsonValue.of(layer.additive),
                "muted" to JsonValue.of(layer.muted)
            )
        }.toMutableList())
        root.fields["poses"] = JsonValue.Arr(scene.poseLibrary.map { preset ->
            JsonValue.objOf(
                "name" to JsonValue.of(preset.name),
                "bones" to JsonValue.Arr(preset.bones.map { bp ->
                    JsonValue.objOf(
                        "t" to JsonValue.floats(bp.translation.toFloatArray()),
                        "r" to JsonValue.floats(bp.rotation.toArray()),
                        "s" to JsonValue.floats(bp.scale.toFloatArray())
                    )
                }.toMutableList())
            )
        }.toMutableList())
        root.fields["constraints"] = JsonValue.Arr(scene.constraints.map { c ->
            JsonValue.objOf(
                "type" to JsonValue.of(c.type.name),
                "bone" to JsonValue.of(c.bone),
                "source" to JsonValue.of(c.sourceBone),
                "min" to JsonValue.floats(c.minEuler.toFloatArray()),
                "max" to JsonValue.floats(c.maxEuler.toFloatArray()),
                "weight" to JsonValue.of(c.weight),
                "enabled" to JsonValue.of(c.enabled),
                "name" to JsonValue.of(c.name)
            )
        }.toMutableList())
        return Json.stringify(root, false)
    }

    fun load(text: String): Scene {
        val root = Json.parse(text).asObj ?: throw IllegalArgumentException("Invalid project file")
        if (root.str("format") != "studio3d") throw IllegalArgumentException("Not a 3D Studio Pro project")
        val scene = Scene()
        scene.name = root.str("name") ?: "Project"
        scene.activeClip = root.int("activeClip") ?: 0
        scene.autosaveEnabled = root.bool("autosave") ?: true
        root.arr("objects")?.items?.forEach { json ->
            val obj = objectFromJson(json) ?: return@forEach
            scene.objects.add(obj)
        }
        scene.skeleton = root.obj("skeleton")?.let { skeletonFromJson(it) }
        root.arr("clips")?.items?.forEach { scene.clips.add(clipFromJson(it)) }
        root.arr("layers")?.items?.forEach { json ->
            scene.layers.add(AnimationLayer(
                json.str("name") ?: "Layer",
                json.int("clipIndex") ?: 0,
                json.float("weight") ?: 1f,
                json.bool("additive") ?: false,
                json.bool("muted") ?: false
            ))
        }
        root.arr("poses")?.items?.forEach { json ->
            val bones = json.arr("bones")?.items?.map { b ->
                val t = b.arr("t")?.floatArray() ?: floatArrayOf(0f, 0f, 0f)
                val r = b.arr("r")?.floatArray() ?: floatArrayOf(0f, 0f, 0f, 1f)
                val s = b.arr("s")?.floatArray() ?: floatArrayOf(1f, 1f, 1f)
                BonePose(
                    Vec3(t.getOrElse(0) { 0f }, t.getOrElse(1) { 0f }, t.getOrElse(2) { 0f }),
                    Quat(r.getOrElse(0) { 0f }, r.getOrElse(1) { 0f }, r.getOrElse(2) { 0f }, r.getOrElse(3) { 1f }),
                    Vec3(s.getOrElse(0) { 1f }, s.getOrElse(1) { 1f }, s.getOrElse(2) { 1f })
                )
            }?.toMutableList() ?: mutableListOf()
            scene.poseLibrary.add(PosePreset(json.str("name") ?: "Pose", bones))
        }
        root.arr("constraints")?.items?.forEach { json ->
            val type = runCatching { ConstraintType.valueOf(json.str("type") ?: "") }.getOrNull() ?: return@forEach
            val c = BoneConstraint(
                type,
                json.int("bone") ?: 0,
                json.int("source") ?: -1,
                readVec3(json.arr("min"), Vec3(-180f, -180f, -180f)),
                readVec3(json.arr("max"), Vec3(180f, 180f, 180f)),
                json.float("weight") ?: 1f,
                json.bool("enabled") ?: true,
                json.str("name") ?: type.name
            )
            scene.constraints.add(c)
        }
        if (scene.skeleton != null) {
            val count = scene.skeleton!!.size
            scene.clips.forEach { clip -> clip.tracks.removeAll { it.bone !in 0 until count } }
            scene.poseLibrary.forEach { preset ->
                while (preset.bones.size < count) preset.bones.add(BonePose())
                while (preset.bones.size > count) preset.bones.removeAt(preset.bones.size - 1)
            }
        }
        scene.nextObjectId = (scene.objects.maxOfOrNull { it.id } ?: 0L) + 1
        scene.activeClip = scene.activeClip.coerceIn(0, maxOf(0, scene.clips.size - 1))
        scene.touch()
        return scene
    }

    private fun objJson(obj: SceneObject): JsonValue = JsonValue.objOf(
        "id" to JsonValue.of(obj.id),
        "name" to JsonValue.of(obj.name),
        "visible" to JsonValue.of(obj.visible),
        "transform" to JsonValue.objOf(
            "p" to JsonValue.floats(obj.transform.position.toFloatArray()),
            "r" to JsonValue.floats(obj.transform.rotation.toArray()),
            "s" to JsonValue.floats(obj.transform.scale.toFloatArray())
        ),
        "material" to materialJson(obj.material),
        "mesh" to obj.mesh?.let { meshJson(it) }
    )

    private fun materialJson(m: Material): JsonValue = JsonValue.objOf(
        "name" to JsonValue.of(m.name),
        "color" to JsonValue.floats(m.baseColor.toFloatArray()),
        "metallic" to JsonValue.of(m.metallic),
        "roughness" to JsonValue.of(m.roughness),
        "emissive" to JsonValue.floats(m.emissive.toFloatArray()),
        "emissiveStrength" to JsonValue.of(m.emissiveStrength),
        "opacity" to JsonValue.of(m.opacity),
        "doubleSided" to JsonValue.of(m.doubleSided),
        "texture" to m.baseColorTexture?.let { tex ->
            JsonValue.objOf(
                "mime" to JsonValue.of(tex.mimeType),
                "name" to JsonValue.of(tex.name),
                "data" to JsonValue.of(Base64.getEncoder().encodeToString(tex.bytes))
            )
        }
    )

    private fun meshJson(mesh: Mesh): JsonValue = JsonValue.objOf(
        "vertices" to JsonValue.Arr(mesh.vertices.map {
            JsonValue.floats(floatArrayOf(it.x, it.y, it.z))
        }.toMutableList()),
        "faces" to JsonValue.Arr(mesh.faces.map { JsonValue.ints(it) }.toMutableList()),
        "uvs" to JsonValue.Arr(mesh.faceUvs.map { JsonValue.floats(it) }.toMutableList()),
        "skin" to mesh.skin?.let { skin ->
            JsonValue.objOf(
                "bones" to JsonValue.ints(skin.boneIndices),
                "weights" to JsonValue.floats(skin.weights)
            )
        }
    )

    private fun skeletonJson(sk: Skeleton): JsonValue = JsonValue.objOf(
        "bones" to JsonValue.Arr(sk.bones.map { bone ->
            JsonValue.objOf(
                "name" to JsonValue.of(bone.name),
                "parent" to JsonValue.of(bone.parent),
                "head" to JsonValue.floats(bone.headLocal.toFloatArray()),
                "rest" to JsonValue.floats(bone.restRotation.toArray()),
                "length" to JsonValue.of(bone.length)
            )
        }.toMutableList())
    )

    private fun clipJson(clip: AnimationClip): JsonValue = JsonValue.objOf(
        "name" to JsonValue.of(clip.name),
        "duration" to JsonValue.of(clip.duration),
        "loop" to JsonValue.of(clip.loop),
        "tracks" to JsonValue.Arr(clip.tracks.map { track ->
            JsonValue.objOf(
                "bone" to JsonValue.of(track.bone),
                "type" to JsonValue.of(track.type.name),
                "keys" to JsonValue.Arr(track.keys.map { key ->
                    JsonValue.objOf(
                        "t" to JsonValue.of(key.time),
                        "v" to JsonValue.floats(floatArrayOf(key.value.x, key.value.y, key.value.z, key.value.w)),
                        "i" to JsonValue.of(key.interp.name)
                    )
                }.toMutableList())
            )
        }.toMutableList())
    )

    private fun objectFromJson(json: JsonValue): SceneObject? {
        val mesh = json.obj("mesh")?.let { meshFromJson(it) } ?: return null
        val t = json.obj("transform")
        val p = t?.arr("p")?.floatArray() ?: floatArrayOf(0f, 0f, 0f)
        val r = t?.arr("r")?.floatArray() ?: floatArrayOf(0f, 0f, 0f, 1f)
        val s = t?.arr("s")?.floatArray() ?: floatArrayOf(1f, 1f, 1f)
        return SceneObject(
            json.int("id")?.toLong() ?: 1L,
            json.str("name") ?: "Object",
            mesh,
            json.obj("material")?.let { materialFromJson(it) } ?: Material(),
            Transform(
                Vec3(p.getOrElse(0) { 0f }, p.getOrElse(1) { 0f }, p.getOrElse(2) { 0f }),
                Quat(r.getOrElse(0) { 0f }, r.getOrElse(1) { 0f }, r.getOrElse(2) { 0f }, r.getOrElse(3) { 1f }),
                Vec3(s.getOrElse(0) { 1f }, s.getOrElse(1) { 1f }, s.getOrElse(2) { 1f })
            ),
            json.bool("visible") ?: true
        )
    }

    private fun materialFromJson(json: JsonValue): Material {
        val color = readVec3(json.arr("color"), Vec3(0.8f, 0.8f, 0.8f))
        val texture = json.obj("texture")?.let { t ->
            val data = t.str("data")?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
            if (data != null) TextureData(t.str("mime") ?: "image/png", data, t.str("name") ?: "texture") else null
        }
        return Material(
            json.str("name") ?: "Material",
            color,
            json.float("metallic") ?: 0f,
            json.float("roughness") ?: 0.55f,
            readVec3(json.arr("emissive"), Vec3.ZERO),
            json.float("emissiveStrength") ?: 1f,
            json.float("opacity") ?: 1f,
            json.bool("doubleSided") ?: true,
            texture
        )
    }

    private fun meshFromJson(json: JsonValue): Mesh? {
        val mesh = Mesh()
        val verts = json.arr("vertices")?.items ?: return null
        for (v in verts) {
            val f = v.floatArray() ?: continue
            if (f.size >= 3) mesh.addVertex(Vec3(f[0], f[1], f[2]))
        }
        val faces = json.arr("faces")?.items ?: return null
        val uvs = json.arr("uvs")?.items
        for (i in faces.indices) {
            val indices = faces[i].intArray() ?: continue
            if (indices.size < 3 || indices.any { it !in mesh.vertices.indices }) continue
            mesh.addFace(indices, uvs?.getOrNull(i)?.floatArray() ?: FloatArray(indices.size * 2))
        }
        json.obj("skin")?.let { skinJson ->
            val bones = skinJson.arr("bones")?.intArray() ?: return@let
            val weights = skinJson.arr("weights")?.floatArray() ?: return@let
            if (bones.size == weights.size && bones.size % 4 == 0) mesh.skin = SkinWeights(bones, weights)
        }
        mesh.markDirty()
        return mesh
    }

    private fun skeletonFromJson(json: JsonValue): Skeleton {
        val sk = Skeleton()
        json.arr("bones")?.items?.forEach { b ->
            val h = b.arr("head")?.floatArray() ?: floatArrayOf(0f, 0f, 0f)
            val r = b.arr("rest")?.floatArray() ?: floatArrayOf(0f, 0f, 0f, 1f)
            sk.bones.add(Bone(
                b.str("name") ?: "Bone",
                b.int("parent") ?: -1,
                Vec3(h.getOrElse(0) { 0f }, h.getOrElse(1) { 0f }, h.getOrElse(2) { 0f }),
                Quat(r.getOrElse(0) { 0f }, r.getOrElse(1) { 0f }, r.getOrElse(2) { 0f }, r.getOrElse(3) { 1f }),
                b.float("length") ?: 0.1f
            ))
        }
        return sk
    }

    private fun clipFromJson(json: JsonValue): AnimationClip {
        val clip = AnimationClip(
            json.str("name") ?: "Animation",
            json.float("duration") ?: 1f,
            json.bool("loop") ?: true
        )
        json.arr("tracks")?.items?.forEach { t ->
            val type = runCatching { ChannelType.valueOf(t.str("type") ?: "") }.getOrNull() ?: return@forEach
            val track = AnimTrack(t.int("bone") ?: 0, type)
            t.arr("keys")?.items?.forEach { k ->
                val v = k.arr("v")?.floatArray() ?: return@forEach
                if (v.size < 4) return@forEach
                val interp = runCatching { Interp.valueOf(k.str("i") ?: "BEZIER") }.getOrDefault(Interp.BEZIER)
                track.keys.add(AnimKey(k.float("t") ?: 0f, Vec4(v[0], v[1], v[2], v[3]), interp))
            }
            track.sort()
            if (track.keys.isNotEmpty()) clip.tracks.add(track)
        }
        return clip
    }

    private fun readVec3(arr: JsonValue?, fallback: Vec3): Vec3 {
        val f = arr?.floatArray() ?: return fallback
        return if (f.size >= 3) Vec3(f[0], f[1], f[2]) else fallback
    }
}

private fun Vec3.toFloatArray() = floatArrayOf(x, y, z)
private fun Quat.toArray() = floatArrayOf(x, y, z, w)
