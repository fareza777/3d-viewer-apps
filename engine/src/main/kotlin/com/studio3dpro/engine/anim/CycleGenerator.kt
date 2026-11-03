package com.studio3dpro.engine.anim

import com.studio3dpro.engine.math.Quat
import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.math.degToRad
import com.studio3dpro.engine.scene.Skeleton
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Procedural walk/run cycles authored as real keyframes on a humanoid rig.
 * The output is a normal editable AnimationClip, not a runtime-only effect.
 */
object CycleGenerator {

    fun generate(skeleton: Skeleton, run: Boolean = false): AnimationClip {
        val duration = if (run) 0.62f else 1.05f
        val swing = if (run) 48f else 26f
        val knee = if (run) 78f else 42f
        val armSwing = if (run) 55f else 24f
        val bob = if (run) 0.085f else 0.035f
        val clip = AnimationClip(if (run) "Run Cycle" else "Walk Cycle", duration, true)
        val steps = 12
        val height = skeletonHeight(skeleton)
        fun bone(vararg names: String): Int {
            for (n in names) {
                val i = skeleton.indexOfName(n)
                if (i >= 0) return i
            }
            return -1
        }
        val leftThigh = bone("Thigh.L", "LeftUpLeg", "LeftThigh", "UpLeg.L")
        val rightThigh = bone("Thigh.R", "RightUpLeg", "RightThigh", "UpLeg.R")
        val leftShin = bone("Shin.L", "LeftLeg", "LeftShin", "Leg.L")
        val rightShin = bone("Shin.R", "RightLeg", "RightShin", "Leg.R")
        val leftFoot = bone("Foot.L", "LeftFoot")
        val rightFoot = bone("Foot.R", "RightFoot")
        val leftArm = bone("UpperArm.L", "LeftArm", "LeftUpperArm")
        val rightArm = bone("UpperArm.R", "RightArm", "RightUpperArm")
        val leftForearm = bone("Forearm.L", "LeftForeArm", "LeftLowerArm")
        val rightForearm = bone("Forearm.R", "RightForeArm", "RightLowerArm")
        val hips = bone("Hips", "Pelvis")
        val spine = bone("Spine", "Spine1")
        val chest = bone("Chest", "Spine2")

        val footSwing = if (run) 20f else 12f

        // Bone local +X swings a downward-pointing bone forward, so knee
        // flexion and hip swing need opposite signs; otherwise the knee
        // hyperextends and the walk reads as a broken joint.
        fun poseSide(
            legPhase: Float,
            thigh: Int,
            shin: Int,
            foot: Int,
            arm: Int,
            forearm: Int,
            t: Float
        ) {
            val legSwing = sin(legPhase)
            if (thigh >= 0) {
                clip.addBoneKey(
                    thigh, t,
                    rotation = Quat.axisAngle(Vec3.RIGHT, degToRad(-swing * legSwing)),
                    interp = Interp.BEZIER
                )
            }
            if (shin >= 0) {
                // Flexion peaks through the swing phase and the knee is almost
                // straight at heel strike, as in a real gait.
                val swingBend = sin(legPhase - PI.toFloat() / 3f).coerceIn(0f, 1f)
                val bend = knee * (0.10f + 0.90f * swingBend)
                clip.addBoneKey(shin, t, rotation = Quat.axisAngle(Vec3.RIGHT, degToRad(-bend)))
            }
            if (foot >= 0) {
                clip.addBoneKey(foot, t, rotation = Quat.axisAngle(Vec3.RIGHT, degToRad(footSwing * legSwing)))
            }
            if (arm >= 0) {
                clip.addBoneKey(arm, t, rotation = Quat.axisAngle(Vec3.RIGHT, degToRad(armSwing * legSwing)))
            }
            if (forearm >= 0) {
                clip.addBoneKey(
                    forearm, t,
                    rotation = Quat.axisAngle(Vec3.RIGHT, degToRad(8f + armSwing * 0.30f * (0.5f + 0.5f * legSwing)))
                )
            }
        }

        for (s in 0..steps) {
            val f = s.toFloat() / steps
            val t = f * duration
            val phase = f * 2f * PI.toFloat()
            val leftSwing = sin(phase)
            poseSide(phase, leftThigh, leftShin, leftFoot, leftArm, leftForearm, t)
            poseSide(phase + PI.toFloat(), rightThigh, rightShin, rightFoot, rightArm, rightForearm, t)
            if (hips >= 0) {
                // Pelvis sits at its rest height when a leg passes under the
                // body and drops into the spread support pose; the old
                // inverted wave bounced the character on every foot strike.
                val lift = bob * height * (0.5f * cos(2f * phase) - 0.5f)
                clip.addBoneKey(
                    hips, t,
                    rotation = Quat.fromEulerDegrees(Vec3(0f, 3f * leftSwing, 2f * leftSwing)),
                    translation = Vec3(0f, lift, 0f)
                )
            }
            if (spine >= 0) {
                clip.addBoneKey(spine, t, rotation = Quat.fromEulerDegrees(Vec3(4f, -2f * leftSwing, 1.5f * leftSwing)))
            }
            if (chest >= 0) {
                clip.addBoneKey(chest, t, rotation = Quat.fromEulerDegrees(Vec3(0f, -4f * leftSwing, 0f)))
            }
        }
        return clip
    }

    private fun skeletonHeight(skeleton: Skeleton): Float {
        if (skeleton.bones.isEmpty()) return 1f
        val mats = skeleton.restWorldMatrices()
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in skeleton.bones.indices) {
            val h = mats[i].translation().y
            val tail = skeleton.restTailWorld(i, mats).y
            minY = minOf(minY, h, tail)
            maxY = maxOf(maxY, h, tail)
        }
        return (maxY - minY).coerceAtLeast(0.1f)
    }
}
