package com.studio3dpro.engine.rig

import com.studio3dpro.engine.math.Mat4
import com.studio3dpro.engine.math.Quat
import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.math.clamp
import com.studio3dpro.engine.scene.Pose
import com.studio3dpro.engine.scene.Skeleton

enum class ConstraintType { LIMIT_ROTATION, COPY_ROTATION }

class BoneConstraint(
    var type: ConstraintType,
    var bone: Int,
    var sourceBone: Int = -1,
    var minEuler: Vec3 = Vec3(-180f, -180f, -180f),
    var maxEuler: Vec3 = Vec3(180f, 180f, 180f),
    var weight: Float = 1f,
    var enabled: Boolean = true,
    var name: String = type.name
) {
    fun copy(): BoneConstraint = BoneConstraint(
        type, bone, sourceBone, minEuler, maxEuler, weight, enabled, name
    )
}

object ConstraintSolver {

    fun apply(skeleton: Skeleton, pose: Pose, constraints: List<BoneConstraint>) {
        for (c in constraints) {
            if (!c.enabled || c.bone !in skeleton.bones.indices) continue
            when (c.type) {
                ConstraintType.LIMIT_ROTATION -> {
                    val e = pose.bones[c.bone].rotation.toEulerDegrees()
                    val limited = Vec3(
                        clamp(e.x, c.minEuler.x, c.maxEuler.x),
                        clamp(e.y, c.minEuler.y, c.maxEuler.y),
                        clamp(e.z, c.minEuler.z, c.maxEuler.z)
                    )
                    if (limited != e) {
                        pose.bones[c.bone].rotation =
                            Quat.IDENTITY.slerp(Quat.fromEulerDegrees(limited), c.weight)
                    }
                }
                ConstraintType.COPY_ROTATION -> {
                    if (c.sourceBone !in skeleton.bones.indices) continue
                    val matrices = skeleton.poseWorldMatrices(pose)
                    val rest = skeleton.restWorldMatrices()
                    val sourcePose = skeleton.worldRotation(matrices, c.sourceBone)
                    val sourceRest = skeleton.worldRotation(rest, c.sourceBone)
                    val targetRest = skeleton.worldRotation(rest, c.bone)
                    val delta = sourcePose * sourceRest.conjugate()
                    val desiredWorld = delta * targetRest
                    val bone = skeleton.bones[c.bone]
                    val parentWorld = if (bone.parent >= 0) skeleton.worldRotation(matrices, bone.parent) else Quat.IDENTITY
                    val base = parentWorld * bone.restRotation
                    val targetLocal = base.conjugate() * desiredWorld
                    pose.bones[c.bone].rotation = pose.bones[c.bone].rotation.slerp(targetLocal, c.weight)
                }
            }
        }
    }
}

enum class IkMode { FK, IK }

class IkTarget(
    var bone: Int,
    var chainLength: Int = 2,
    var enabled: Boolean = true
) {
    fun copy(): IkTarget = IkTarget(bone, chainLength, enabled)
}
