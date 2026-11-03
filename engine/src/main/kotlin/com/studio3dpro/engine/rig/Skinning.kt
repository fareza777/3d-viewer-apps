package com.studio3dpro.engine.rig

import com.studio3dpro.engine.math.Mat4
import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.math.clamp
import com.studio3dpro.engine.mesh.Mesh
import com.studio3dpro.engine.mesh.SkinWeights
import com.studio3dpro.engine.scene.Pose
import com.studio3dpro.engine.scene.Skeleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object Skinning {

    private fun segmentDistanceSq(p: Vec3, a: Vec3, b: Vec3): Float {
        val ab = b - a
        val len2 = ab.lengthSq()
        if (len2 < 1e-10f) return (p - a).lengthSq()
        val t = (((p - a) dot ab) / len2).coerceIn(0f, 1f)
        return (p - (a + ab * t)).lengthSq()
    }

    /**
     * Automatic weights from distance to bone segments with inverse-power
     * falloff, Laplacian smoothing and 4-influence compression.
     */
    fun computeWeights(
        mesh: Mesh,
        skeleton: Skeleton,
        smoothingIterations: Int = 2,
        power: Float = 3.2f
    ): SkinWeights {
        val n = mesh.vertexCount
        val boneCount = skeleton.size
        require(boneCount in 1..64) { "Skinning supports 1..64 bones" }
        val rest = skeleton.restWorldMatrices()
        val heads = Array(boneCount) { rest[it].translation() }
        val tails = Array(boneCount) { skeleton.restTailWorld(it, rest) }
        // A humanoid "Root" is a control at the origin, not a deform bone.
        // Letting it capture feet and props drags them around the scene.
        val allowed = (0 until boneCount).filter { b ->
            !(b == 0 && skeleton.bones[b].parent < 0 && skeleton.bones[b].name.equals("Root", true))
        }.ifEmpty { (0 until boneCount).toList() }
        val bounds = mesh.bounds()
        val scale = max(bounds.radius, 1e-3f)
        val dense = Array(n) { FloatArray(boneCount) }
        for (v in 0 until n) {
            val p = mesh.vertices[v]
            var total = 0f
            val dists = FloatArray(boneCount)
            for (b in allowed) {
                val d = kotlin.math.sqrt(segmentDistanceSq(p, heads[b], tails[b]))
                dists[b] = d
            }
            val nearest = allowed.minOf { dists[it] }
            // Local support only. A whole-mesh radius here lets a toe bone
            // pull on the opposite ankle and produces LBS spikes.
            val cutoff = nearest + max(0.06f, scale * 0.22f)
            for (b in allowed) {
                val d = dists[b]
                if (d > cutoff) continue
                val w = 1f / ((d / scale + 0.03f).pow(power) + 1e-5f)
                // Keep head/hand bones from grabbing the torso when the mesh
                // has a low-poly silhouette.
                dense[v][b] = w
                total += w
            }
            if (total <= 1e-8f) dense[v][allowed[0]] = 1f else for (b in allowed) dense[v][b] /= total
        }
        if (smoothingIterations > 0 && n * boneCount <= 8_000_000) {
            val neighbors = mesh.vertexNeighbors()
            repeat(smoothingIterations) {
                val next = Array(n) { FloatArray(boneCount) }
                for (v in 0 until n) {
                    val nb = neighbors[v]
                    if (nb.isEmpty()) { next[v] = dense[v]; continue }
                    for (b in 0 until boneCount) {
                        var sum = dense[v][b]
                        for (x in nb) sum += dense[x][b]
                        next[v][b] = sum / (nb.size + 1)
                    }
                    var sum = 0f
                    for (b in 0 until boneCount) sum += next[v][b]
                    if (sum > 1e-8f) for (b in 0 until boneCount) next[v][b] /= sum
                }
                for (v in 0 until n) dense[v] = next[v]
            }
        }
        // Drop negligible long-range influences before top-4 compression.
        for (v in 0 until n) {
            var total = 0f
            for (b in 0 until boneCount) total += dense[v][b]
            if (total > 1e-8f) {
                for (b in 0 until boneCount) {
                    if (dense[v][b] < total * 0.03f) dense[v][b] = 0f
                }
            }
        }
        val outBones = IntArray(n * 4)
        val weights = FloatArray(n * 4)
        val order = IntArray(boneCount)
        val slots = min(4, boneCount)
        for (v in 0 until n) {
            for (b in 0 until boneCount) order[b] = b
            // Partial selection of the 4 largest influences.
            for (i in 0 until slots) {
                var best = i
                for (j in i + 1 until boneCount) if (dense[v][order[j]] > dense[v][order[best]]) best = j
                val tmp = order[i]; order[i] = order[best]; order[best] = tmp
            }
            var sum = 0f
            for (i in 0 until slots) sum += dense[v][order[i]]
            for (i in 0..3) {
                if (i >= slots) {
                    outBones[v * 4 + i] = 0
                    weights[v * 4 + i] = 0f
                    continue
                }
                val b = order[i]
                outBones[v * 4 + i] = b
                weights[v * 4 + i] = if (sum > 1e-8f) dense[v][b] / sum else if (i == 0) 1f else 0f
            }
        }
        return SkinWeights(outBones, weights)
    }

    fun bind(mesh: Mesh, skeleton: Skeleton): SkinWeights {
        val w = computeWeights(mesh, skeleton)
        mesh.skin = w
        return w
    }

    fun normalize(mesh: Mesh) {
        val skin = mesh.skin ?: return
        for (v in 0 until skin.vertexCount) {
            var sum = 0f
            for (k in 0..3) sum += skin.weights[v * 4 + k]
            if (sum > 1e-8f) for (k in 0..3) skin.weights[v * 4 + k] /= sum
            else {
                skin.weights[v * 4] = 1f
                for (k in 1..3) skin.weights[v * 4 + k] = 0f
            }
        }
    }

    /**
     * Weight-paint brush. Adds (or removes, with negative strength) a bone's
     * influence around a world-space point and renormalizes.
     */
    fun paint(
        mesh: Mesh,
        center: Vec3,
        radius: Float,
        boneIndex: Int,
        strength: Float,
        mode: PaintMode = PaintMode.ADD
    ): Int {
        val skin = mesh.skin ?: return 0
        if (boneIndex !in 0..63) return 0
        val affected = mesh.verticesInRadius(center, radius)
        for (v in affected) {
            val p = mesh.vertices[v]
            val t = clamp(1f - p.distanceTo(center) / radius, 0f, 1f)
            val falloff = t * t * (3f - 2f * t)
            var sum = 0f
            var targetSlot = -1
            var smallestSlot = 0
            for (k in 0..3) {
                if (skin.boneIndices[v * 4 + k] == boneIndex) targetSlot = k
                if (skin.weights[v * 4 + k] < skin.weights[v * 4 + smallestSlot]) smallestSlot = k
                sum += skin.weights[v * 4 + k]
            }
            if (targetSlot < 0) {
                targetSlot = smallestSlot
                skin.boneIndices[v * 4 + targetSlot] = boneIndex
                skin.weights[v * 4 + targetSlot] = 0f
            }
            val current = skin.weights[v * 4 + targetSlot]
            val delta = when (mode) {
                PaintMode.ADD -> falloff * strength
                PaintMode.SUBTRACT -> -falloff * strength
                PaintMode.REPLACE -> falloff * strength - current * falloff
            }
            val newTarget = clamp(current + delta, 0f, 1f)
            skin.weights[v * 4 + targetSlot] = newTarget
            var rest = 0f
            for (k in 0..3) if (k != targetSlot) rest += skin.weights[v * 4 + k]
            val restScale = if (rest > 1e-8f) (1f - newTarget).coerceAtLeast(0f) / rest else 0f
            for (k in 0..3) if (k != targetSlot) skin.weights[v * 4 + k] *= restScale
            if (newTarget < 1e-4f && rest <= 1e-8f) skin.weights[v * 4 + targetSlot] = 1f
            sum = 0f
            for (k in 0..3) sum += skin.weights[v * 4 + k]
            if (sum > 1e-8f) for (k in 0..3) skin.weights[v * 4 + k] /= sum
        }
        return affected.size
    }

    fun weightsForVertex(skin: SkinWeights, vertex: Int, boneName: (Int) -> String): Map<String, Float> {
        val map = LinkedHashMap<String, Float>()
        for (k in 0..3) {
            val w = skin.weights[vertex * 4 + k]
            if (w > 1e-4f) map[boneName(skin.boneIndices[vertex * 4 + k])] = w
        }
        return map
    }

    fun skinnedPosition(
        mesh: Mesh,
        skeleton: Skeleton,
        pose: Pose,
        objectMatrix: Mat4,
        vertex: Int
    ): Vec3? {
        val skin = mesh.skin ?: return null
        val mats = skeleton.skinMatrices(pose, objectMatrix)
        var p = Vec3.ZERO
        for (k in 0..3) {
            val w = skin.weights[vertex * 4 + k]
            if (w <= 1e-6f) continue
            val mat = mats.getOrNull(skin.boneIndices[vertex * 4 + k]) ?: continue
            p += mat.transformPoint(mesh.vertices[vertex]) * w
        }
        return objectMatrix.transformPoint(p)
    }

    enum class PaintMode { ADD, SUBTRACT, REPLACE }
}
