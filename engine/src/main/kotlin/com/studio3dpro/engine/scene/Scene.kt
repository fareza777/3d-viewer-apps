package com.studio3dpro.engine.scene

import com.studio3dpro.engine.anim.AnimationClip
import com.studio3dpro.engine.anim.AnimationLayer
import com.studio3dpro.engine.anim.PosePreset
import com.studio3dpro.engine.math.Aabb
import com.studio3dpro.engine.math.Mat4
import com.studio3dpro.engine.math.Quat
import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.mesh.Mesh
import com.studio3dpro.engine.rig.BoneConstraint
import kotlin.math.max

class TextureData(
    val mimeType: String,
    val bytes: ByteArray,
    val name: String = "texture"
) {
    fun copy(): TextureData = TextureData(mimeType, bytes.copyOf(), name)
}

class Material(
    var name: String = "Material",
    var baseColor: Vec3 = Vec3(0.78f, 0.74f, 0.68f),
    var metallic: Float = 0f,
    var roughness: Float = 0.55f,
    var emissive: Vec3 = Vec3.ZERO,
    var emissiveStrength: Float = 1f,
    var opacity: Float = 1f,
    var doubleSided: Boolean = true,
    var baseColorTexture: TextureData? = null
) {
    fun copy(): Material = Material(
        name, baseColor, metallic, roughness, emissive, emissiveStrength,
        opacity, doubleSided, baseColorTexture?.copy()
    )
}

data class Transform(
    var position: Vec3 = Vec3.ZERO,
    var rotation: Quat = Quat.IDENTITY,
    var scale: Vec3 = Vec3.ONE
) {
    fun matrix(): Mat4 = Mat4.trs(position, rotation, scale)
    fun copy(): Transform = Transform(position, rotation, scale)
}

class SceneObject(
    var id: Long,
    var name: String,
    var mesh: Mesh?,
    var material: Material = Material(),
    var transform: Transform = Transform(),
    var visible: Boolean = true
) {
    fun worldMatrix(): Mat4 = transform.matrix()

    fun copy(newId: Long = id): SceneObject = SceneObject(
        newId, name, mesh?.clone(), material.copy(), transform.copy(), visible
    )

    fun bounds(): Aabb? = mesh?.bounds()?.transformed(worldMatrix())
}

/** Local bone pose deltas relative to the rest pose. */
data class BonePose(
    var translation: Vec3 = Vec3.ZERO,
    var rotation: Quat = Quat.IDENTITY,
    var scale: Vec3 = Vec3.ONE
) {
    fun copy(): BonePose = BonePose(translation, rotation, scale)
    fun reset() {
        translation = Vec3.ZERO
        rotation = Quat.IDENTITY
        scale = Vec3.ONE
    }
}

class Pose(boneCount: Int) {
    val bones: MutableList<BonePose> = MutableList(boneCount) { BonePose() }

    fun reset() {
        for (b in bones) b.reset()
    }

    fun copy(): Pose {
        val p = Pose(bones.size)
        for (i in bones.indices) p.bones[i] = bones[i].copy()
        return p
    }

    fun blendTo(other: Pose, t: Float) {
        for (i in bones.indices) {
            if (i >= other.bones.size) break
            bones[i].translation = bones[i].translation.lerp(other.bones[i].translation, t)
            bones[i].rotation = bones[i].rotation.slerp(other.bones[i].rotation, t)
            bones[i].scale = bones[i].scale.lerp(other.bones[i].scale, t)
        }
    }
}

class Bone(
    var name: String,
    var parent: Int,
    var headLocal: Vec3,
    var restRotation: Quat,
    var length: Float
) {
    fun copy(): Bone = Bone(name, parent, headLocal, restRotation, length)
}

/**
 * Bone hierarchy with rest local transforms. Pose rotations are deltas
 * applied after the rest rotation, which keeps retargeting and animation
 * authoring intuitive.
 */
class Skeleton(val bones: MutableList<Bone> = mutableListOf()) {

    val size: Int get() = bones.size

    fun copy(): Skeleton = Skeleton(bones.map { it.copy() }.toMutableList())

    fun indexOfName(name: String): Int = bones.indexOfFirst { it.name.equals(name, ignoreCase = true) }

    fun isValidHierarchy(): Boolean {
        for (i in bones.indices) {
            if (bones[i].parent >= i || bones[i].parent < -1) return false
        }
        return true
    }

    fun children(index: Int): List<Int> = bones.indices.filter { bones[it].parent == index }

    fun restLocalMatrix(index: Int): Mat4 {
        val b = bones[index]
        return Mat4.translation(b.headLocal) * Mat4.rotation(b.restRotation)
    }

    fun poseLocalMatrix(index: Int, pose: Pose): Mat4 {
        val b = bones[index]
        val bp = pose.bones[index]
        return Mat4.translation(b.headLocal + bp.translation) *
            Mat4.rotation(b.restRotation * bp.rotation) *
            Mat4.scaling(bp.scale)
    }

    fun restWorldMatrices(): Array<Mat4> {
        val out = Array(bones.size) { Mat4.identity() }
        for (i in bones.indices) {
            out[i] = (if (bones[i].parent >= 0) out[bones[i].parent] else Mat4.identity()) *
                restLocalMatrix(i)
        }
        return out
    }

    fun poseWorldMatrices(pose: Pose): Array<Mat4> {
        val out = Array(bones.size) { Mat4.identity() }
        for (i in bones.indices) {
            val parentMat = if (bones[i].parent >= 0) out[bones[i].parent] else Mat4.identity()
            out[i] = parentMat * poseLocalMatrix(i, pose)
        }
        return out
    }

    /**
     * Skinning matrices in object space. Vertices are bound in scene space;
     * the object transform cancels out, so the renderer applies objectMatrix last.
     */
    fun skinMatrices(pose: Pose, objectMatrix: Mat4 = Mat4.identity()): Array<Mat4> {
        val rest = restWorldMatrices()
        val pos = poseWorldMatrices(pose)
        val invObj = objectMatrix.inverse()
        return Array(bones.size) {
            invObj * pos[it] * rest[it].inverse() * objectMatrix
        }
    }

    fun restHeadWorld(matrices: Array<Mat4>, index: Int): Vec3 = matrices[index].translation()

    fun restTailWorld(index: Int, matrices: Array<Mat4> = restWorldMatrices()): Vec3 {
        val ch = children(index)
        if (ch.isNotEmpty()) return matrices[ch[0]].translation()
        val b = bones[index]
        return matrices[index].transformPoint(Vec3(0f, b.length, 0f))
    }

    fun poseTailWorld(index: Int, pose: Pose, matrices: Array<Mat4> = poseWorldMatrices(pose)): Vec3 {
        val ch = children(index)
        if (ch.isNotEmpty()) return matrices[ch[0]].translation()
        val b = bones[index]
        return matrices[index].transformPoint(Vec3(0f, b.length, 0f))
    }

    fun worldRotation(matrices: Array<Mat4>, index: Int): Quat =
        matrices[index].decompose().second

    fun bindPose(): Pose = Pose(bones.size)

    /** Adds a bone from world-space head/tail while preserving the rest hierarchy. */
    fun addBoneFromWorld(
        name: String,
        parent: Int,
        headWorld: Vec3,
        tailWorld: Vec3,
        restWorld: Array<Mat4> = restWorldMatrices()
    ): Int {
        val parentMat = if (parent >= 0) restWorld[parent] else Mat4.identity()
        val invParent = parentMat.inverse()
        val headLocal = invParent.transformPoint(headWorld)
        var dir = tailWorld - headWorld
        if (dir.lengthSq() < 1e-8f) dir = Vec3.UP
        val localDir = invParent.transformDirection(dir).normalized()
        val restRot = Quat.fromToRotation(Vec3.UP, localDir)
        bones.add(Bone(name, parent, headLocal, restRot, dir.length()))
        return bones.size - 1
    }

    fun computeRestWorld(): Array<Mat4> = restWorldMatrices()
}

class Scene {
    var name: String = "Untitled Scene"
    val objects: MutableList<SceneObject> = mutableListOf()
    var skeleton: Skeleton? = null
    val clips: MutableList<AnimationClip> = mutableListOf()
    val layers: MutableList<AnimationLayer> = mutableListOf()
    val poseLibrary: MutableList<PosePreset> = mutableListOf()
    val constraints: MutableList<BoneConstraint> = mutableListOf()
    var activeClip: Int = 0
    var revision: Long = 0L
    var nextObjectId: Long = 1L
    var autosaveEnabled: Boolean = true

    fun touch() { revision++ }

    fun addObject(
        name: String,
        mesh: Mesh?,
        material: Material = Material(),
        transform: Transform = Transform()
    ): SceneObject {
        val obj = SceneObject(nextObjectId++, name, mesh, material, transform)
        objects.add(obj)
        touch()
        return obj
    }

    fun removeObject(obj: SceneObject) {
        objects.remove(obj)
        touch()
    }

    fun bounds(): Aabb {
        var b = Aabb.EMPTY
        for (o in objects) {
            if (!o.visible) continue
            val ob = o.bounds() ?: continue
            b = b.expand(ob)
        }
        if (b.size.lengthSq() <= 0f) return Aabb(Vec3(-1f, 0f, -1f), Vec3(1f, 2f, 1f))
        return b
    }

    fun totalTriangles(): Int = objects.sumOf { it.mesh?.triangles()?.size?.div(3) ?: 0 }
    fun totalVertices(): Int = objects.sumOf { it.mesh?.vertexCount ?: 0 }

    fun copy(): Scene {
        val s = Scene()
        s.name = name
        s.nextObjectId = nextObjectId
        s.revision = revision + 1
        for (o in objects) s.objects.add(o.copy())
        s.skeleton = skeleton?.copy()
        for (c in clips) s.clips.add(c.copy())
        for (l in layers) s.layers.add(l.copy())
        for (p in poseLibrary) s.poseLibrary.add(p.copy())
        for (c in constraints) s.constraints.add(c.copy())
        s.activeClip = activeClip
        s.autosaveEnabled = autosaveEnabled
        return s
    }

    fun findObject(id: Long): SceneObject? = objects.firstOrNull { it.id == id }

    fun frameBounds(objectsOnly: Boolean = false): Aabb {
        if (objectsOnly || skeleton == null) return bounds()
        var b = bounds()
        val sk = skeleton!!
        val rest = sk.restWorldMatrices()
        for (i in sk.bones.indices) {
            b = b.expand(rest[i].translation())
            b = b.expand(sk.restTailWorld(i, rest))
        }
        return b
    }

    fun maxBoneLength(): Float = skeleton?.bones?.maxOfOrNull { it.length } ?: 1f

    fun stats(): String =
        "${objects.size} objects · ${totalTriangles()} tris · ${skeleton?.size ?: 0} bones"
}
