package com.studio3dpro.engine.rig

import com.studio3dpro.engine.math.Quat
import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.scene.Pose
import com.studio3dpro.engine.scene.Skeleton
import kotlin.math.min

object Ik {

    data class Result(val error: Float, val iterations: Int, val reachable: Boolean)

    /**
     * CCD inverse kinematics on a chain of `chainLength` bones ending at
     * `endBone`. The end effector is the tip of the final bone.
     */
    fun solveCcd(
        skeleton: Skeleton,
        pose: Pose,
        endBone: Int,
        target: Vec3,
        chainLength: Int = 2,
        iterations: Int = 16,
        tolerance: Float = 0.002f
    ): Result {
        if (endBone !in skeleton.bones.indices) return Result(0f, 0, false)
        val chain = ArrayList<Int>()
        var current = endBone
        repeat(chainLength.coerceAtLeast(1)) {
            if (current >= 0) {
                chain.add(current)
                current = skeleton.bones[current].parent
            }
        }
        chain.reverse()
        if (chain.isEmpty()) return Result(0f, 0, false)
        var lastError = Float.MAX_VALUE
        for (iteration in 0 until iterations) {
            var matrices = skeleton.poseWorldMatrices(pose)
            var endPos = skeleton.poseTailWorld(endBone, pose, matrices)
            val err = endPos.distanceTo(target)
            lastError = err
            if (err <= tolerance) return Result(err, iteration, true)
            // Iterate from the joint closest to the end effector back to the chain root.
            for (joint in chain.asReversed()) {
                matrices = skeleton.poseWorldMatrices(pose)
                endPos = skeleton.poseTailWorld(endBone, pose, matrices)
                val head = matrices[joint].translation()
                val toEnd = endPos - head
                val toTarget = target - head
                if (toEnd.lengthSq() < 1e-10f || toTarget.lengthSq() < 1e-10f) continue
                val delta = Quat.fromToRotation(toEnd, toTarget)
                val damped = Quat.IDENTITY.slerp(delta, 0.9f)
                val bone = skeleton.bones[joint]
                val parentRot = if (bone.parent >= 0) skeleton.worldRotation(matrices, bone.parent) else Quat.IDENTITY
                val jointWorldRot = skeleton.worldRotation(matrices, joint)
                val newWorldRot = damped * jointWorldRot
                val baseRot = parentRot * bone.restRotation
                pose.bones[joint].rotation = baseRot.conjugate() * newWorldRot
            }
            // If the target is beyond reach, straighten the chain and stop.
            val rootHead = skeleton.poseWorldMatrices(pose)[chain[0]].translation()
            val totalLength = chain.sumOf { (skeleton.bones[it].length).toDouble() }.toFloat()
            if (rootHead.distanceTo(target) > totalLength) break
        }
        val matrices = skeleton.poseWorldMatrices(pose)
        lastError = skeleton.poseTailWorld(endBone, pose, matrices).distanceTo(target)
        return Result(lastError, iterations, lastError <= tolerance)
    }

    /** Two-bone analytic solve used for quick limb posing (hips/knee). */
    fun solveTwoBone(
        skeleton: Skeleton,
        pose: Pose,
        upper: Int,
        lower: Int,
        target: Vec3,
        pole: Vec3 = Vec3.FORWARD
    ): Float {
        val matrices = skeleton.poseWorldMatrices(pose)
        val root = matrices[upper].translation()
        val l1 = skeleton.bones[upper].length
        val l2 = skeleton.bones[lower].length
        val dist = root.distanceTo(target).coerceIn(1e-4f, l1 + l2 - 1e-4f)
        // Law of cosines.
        val cosA = ((l1 * l1 + dist * dist - l2 * l2) / (2f * l1 * dist)).coerceIn(-1f, 1f)
        val a = kotlin.math.acos(cosA)
        val dir = (target - root).normalized()
        var poleDir = (pole - dir * (pole dot dir))
        if (poleDir.lengthSq() < 1e-8f) poleDir = Vec3.FORWARD - dir * (Vec3.FORWARD dot dir)
        poleDir = poleDir.normalized()
        val jointPos = root + (dir * kotlin.math.cos(a) + poleDir * kotlin.math.sin(a)) * l1
        // Rotate upper bone to aim at jointPos.
        val upperMat = matrices[upper]
        val parentUpperRot = if (skeleton.bones[upper].parent >= 0)
            skeleton.worldRotation(matrices, skeleton.bones[upper].parent) else Quat.IDENTITY
        val upperRest = parentUpperRot * skeleton.bones[upper].restRotation
        val upperDir = jointPos - root
        val upperDelta = Quat.fromToRotation(Vec3.UP, upperRest.conjugate().rotate(upperDir))
        pose.bones[upper].rotation = upperDelta
        // Recompute, then aim the lower bone.
        val m2 = skeleton.poseWorldMatrices(pose)
        val lowerMat = m2[lower]
        val lowerHead = lowerMat.translation()
        val parentLowerRot = if (skeleton.bones[lower].parent >= 0)
            skeleton.worldRotation(m2, skeleton.bones[lower].parent) else Quat.IDENTITY
        val lowerRest = parentLowerRot * skeleton.bones[lower].restRotation
        val lowerDelta = Quat.fromToRotation(Vec3.UP, lowerRest.conjugate().rotate(target - lowerHead))
        pose.bones[lower].rotation = lowerDelta
        val endPos = skeleton.poseTailWorld(lower, pose)
        return endPos.distanceTo(target)
    }

    fun chainIndices(skeleton: Skeleton, endBone: Int, length: Int): List<Int> {
        val out = ArrayList<Int>()
        var b = endBone
        repeat(length) {
            if (b >= 0) { out.add(b); b = skeleton.bones[b].parent }
        }
        return out.reversed()
    }

    fun clampChainLength(skeleton: Skeleton, endBone: Int, requested: Int): Int {
        var b = endBone
        var count = 0
        while (b >= 0 && count < requested) {
            count++
            b = skeleton.bones[b].parent
        }
        return min(count, 6)
    }
}
