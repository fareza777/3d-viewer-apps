package com.studio3dpro.engine.anim

import com.studio3dpro.engine.math.Quat
import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.math.Vec4
import com.studio3dpro.engine.scene.Pose
import com.studio3dpro.engine.scene.Skeleton

/**
 * Humanoid animation retargeting. Bone names are normalized and mapped to
 * canonical slots, then source world-space rotation deltas are transferred to
 * the target rest pose. Handles Mixamo, Unity Humanoid, UE and Blender naming.
 */
object Retarget {

    enum class Slot {
        ROOT, HIPS, SPINE, CHEST, NECK, HEAD,
        LEFT_SHOULDER, LEFT_UPPER_ARM, LEFT_FOREARM, LEFT_HAND,
        RIGHT_SHOULDER, RIGHT_UPPER_ARM, RIGHT_FOREARM, RIGHT_HAND,
        LEFT_THIGH, LEFT_SHIN, LEFT_FOOT,
        RIGHT_THIGH, RIGHT_SHIN, RIGHT_FOOT
    }

    fun normalizeName(name: String): String {
        var n = name.lowercase()
            .replace("mixamorig", "")
            .replace("mixamo", "")
            .replace("bip001", "")
            .replace("bip01", "")
            .replace(":", "")
            .replace("_", "")
            .replace(".", "")
            .replace("-", "")
            .replace(" ", "")
        n = n.removePrefix("root")
        return n
    }

    fun slotFor(name: String): Slot? {
        val n = normalizeName(name)
        // Handles Unity/UE "LeftArm", Mixamo "LeftArm" and Blender "Arm.L".
        val left = n.contains("left") || n.endsWith("l") || n.startsWith("left")
        val right = n.contains("right") || n.endsWith("r") || n.startsWith("right")
        return when {
            n == "hips" || n == "pelvis" || n == "hip" -> Slot.HIPS
            n == "spine" || n == "spine1" || n == "spine01" || n == "lowerback" -> Slot.SPINE
            n == "chest" || n == "spine2" || n == "spine02" || n == "upperchest" || n == "spine3" -> Slot.CHEST
            n.startsWith("neck") -> Slot.NECK
            n.startsWith("head") && !n.contains("top") -> Slot.HEAD
            (n.contains("shoulder") || n.contains("clavicle")) && left -> Slot.LEFT_SHOULDER
            (n.contains("shoulder") || n.contains("clavicle")) && right -> Slot.RIGHT_SHOULDER
            (n.contains("forearm") || n.contains("lowerarm")) && left -> Slot.LEFT_FOREARM
            (n.contains("forearm") || n.contains("lowerarm")) && right -> Slot.RIGHT_FOREARM
            (n.contains("upperarm") || (n.contains("arm") && !n.contains("forearm"))) && left -> Slot.LEFT_UPPER_ARM
            (n.contains("upperarm") || (n.contains("arm") && !n.contains("forearm"))) && right -> Slot.RIGHT_UPPER_ARM
            n.contains("hand") && left -> Slot.LEFT_HAND
            n.contains("hand") && right -> Slot.RIGHT_HAND
            (n.contains("upleg") || n.contains("thigh") || n.contains("upperleg")) && left -> Slot.LEFT_THIGH
            (n.contains("upleg") || n.contains("thigh") || n.contains("upperleg")) && right -> Slot.RIGHT_THIGH
            (n.contains("shin") || n.contains("calf") || (n.contains("leg") && !n.contains("upleg"))) && left -> Slot.LEFT_SHIN
            (n.contains("shin") || n.contains("calf") || (n.contains("leg") && !n.contains("upleg"))) && right -> Slot.RIGHT_SHIN
            n.contains("foot") && left -> Slot.LEFT_FOOT
            n.contains("foot") && right -> Slot.RIGHT_FOOT
            n == "root" -> Slot.ROOT
            else -> null
        }
    }

    fun heightOf(skeleton: Skeleton): Float {
        val mats = skeleton.restWorldMatrices()
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in skeleton.bones.indices) {
            val h = mats[i].translation().y
            val t = skeleton.restTailWorld(i, mats).y
            minY = minOf(minY, h, t)
            maxY = maxOf(maxY, h, t)
        }
        return (maxY - minY).coerceAtLeast(0.05f)
    }

    fun retarget(
        sourceSkeleton: Skeleton,
        sourceClip: AnimationClip,
        targetSkeleton: Skeleton,
        sampleRate: Float = 30f
    ): AnimationClip {
        val out = AnimationClip("${sourceClip.name} (Retarget)", sourceClip.duration, sourceClip.loop)
        val sourceBySlot = HashMap<Slot, Int>()
        for (i in sourceSkeleton.bones.indices) {
            val slot = slotFor(sourceSkeleton.bones[i].name) ?: continue
            sourceBySlot.putIfAbsent(slot, i)
        }
        val targetBySlot = HashMap<Slot, Int>()
        for (i in targetSkeleton.bones.indices) {
            val slot = slotFor(targetSkeleton.bones[i].name) ?: continue
            targetBySlot.putIfAbsent(slot, i)
        }
        if (sourceBySlot.isEmpty() || targetBySlot.isEmpty()) return out

        val srcRest = sourceSkeleton.restWorldMatrices()
        val srcRestRot = Array(srcRest.size) { sourceSkeleton.worldRotation(srcRest, it) }
        val tgtRest = targetSkeleton.restWorldMatrices()
        val tgtRestRot = Array(tgtRest.size) { targetSkeleton.worldRotation(tgtRest, it) }
        val srcHeight = heightOf(sourceSkeleton)
        val tgtHeight = heightOf(targetSkeleton)
        val scaleRatio = tgtHeight / srcHeight
        val fps = sampleRate.coerceIn(15f, 60f)
        val frames = maxOf(2, (sourceClip.duration * fps).toInt() + 1)
        val srcHips = sourceBySlot[Slot.HIPS]
        val tgtHips = targetBySlot[Slot.HIPS]
        val tgtWorldRots = Array(targetSkeleton.size) { tgtRestRot[it] }

        for (frame in 0 until frames) {
            val time = sourceClip.duration * frame / (frames - 1).toFloat()
            val srcPose = Pose(sourceSkeleton.size)
            sourceClip.evaluate(time, srcPose, 1f)
            val srcWorld = sourceSkeleton.poseWorldMatrices(srcPose)
            val tgtPose = Pose(targetSkeleton.size)
            var hipsTranslation = Vec3.ZERO
            for (i in targetSkeleton.bones.indices) {
                val bone = targetSkeleton.bones[i]
                val parentWorld = if (bone.parent >= 0) tgtWorldRots[bone.parent] else Quat.IDENTITY
                val base = parentWorld * bone.restRotation
                val slot = slotFor(bone.name)
                val sourceIndex = slot?.let { sourceBySlot[it] }
                if (sourceIndex != null) {
                    val srcAnimated = sourceSkeleton.worldRotation(srcWorld, sourceIndex)
                    val delta = srcAnimated * srcRestRot[sourceIndex].conjugate()
                    val desiredWorld = delta * tgtRestRot[i]
                    val local = base.conjugate() * desiredWorld
                    tgtPose.bones[i].rotation = local
                    tgtWorldRots[i] = desiredWorld
                    if (slot == Slot.HIPS && srcHips != null) {
                        val tr = srcPose.bones[srcHips].translation
                        hipsTranslation = tr * scaleRatio
                        tgtPose.bones[i].translation = hipsTranslation
                    }
                } else {
                    tgtPose.bones[i].rotation = Quat.IDENTITY
                    tgtWorldRots[i] = base
                }
            }
            for (i in targetSkeleton.bones.indices) {
                val slot = slotFor(targetSkeleton.bones[i].name) ?: continue
                if (sourceBySlot.containsKey(slot)) {
                    val q = tgtPose.bones[i].rotation
                    out.ensureTrack(i, ChannelType.ROTATION)
                        .setKey(time, Vec4(q.x, q.y, q.z, q.w), Interp.LINEAR)
                }
            }
            if (tgtHips != null && srcHips != null) {
                val tr = srcPose.bones[srcHips].translation * scaleRatio
                out.ensureTrack(tgtHips, ChannelType.TRANSLATION)
                    .setKey(time, Vec4(tr.x, tr.y, tr.z, 0f), Interp.LINEAR)
            } else if (tgtHips != null) {
                out.ensureTrack(tgtHips, ChannelType.TRANSLATION)
                    .setKey(time, Vec4(hipsTranslation.x, hipsTranslation.y, hipsTranslation.z, 0f), Interp.LINEAR)
            }
        }
        out.duration = sourceClip.duration
        return out
    }

    fun mappedSlots(skeleton: Skeleton): Map<Slot, Int> {
        val map = HashMap<Slot, Int>()
        for (i in skeleton.bones.indices) {
            slotFor(skeleton.bones[i].name)?.let { map.putIfAbsent(it, i) }
        }
        return map
    }

    fun slotLabel(slot: Slot): String = slot.name.lowercase().split("_")
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}
