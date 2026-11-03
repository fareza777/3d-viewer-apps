package com.studio3dpro.engine.anim

import com.studio3dpro.engine.math.Quat
import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.math.Vec4
import com.studio3dpro.engine.math.clamp
import com.studio3dpro.engine.math.lerp
import com.studio3dpro.engine.rig.ConstraintSolver
import com.studio3dpro.engine.scene.BonePose
import com.studio3dpro.engine.scene.Pose
import com.studio3dpro.engine.scene.Scene
import com.studio3dpro.engine.scene.Skeleton
import kotlin.math.abs

enum class ChannelType { TRANSLATION, ROTATION, SCALE }
enum class Interp { STEP, LINEAR, BEZIER }

class AnimKey(
    var time: Float,
    var value: Vec4,
    var interp: Interp = Interp.BEZIER
) {
    fun copy(): AnimKey = AnimKey(time, value, interp)
}

class AnimTrack(
    val bone: Int,
    val type: ChannelType,
    val keys: MutableList<AnimKey> = mutableListOf()
) {
    fun copy(): AnimTrack = AnimTrack(bone, type, keys.map { it.copy() }.toMutableList())

    fun sort() { keys.sortBy { it.time } }

    fun removeKeyAt(time: Float, tolerance: Float = 0.002f): Boolean {
        val idx = keys.indexOfFirst { abs(it.time - time) <= tolerance }
        if (idx >= 0) { keys.removeAt(idx); return true }
        return false
    }

    fun keyAt(time: Float, tolerance: Float = 0.002f): AnimKey? =
        keys.firstOrNull { abs(it.time - time) <= tolerance }

    /** Inserts or replaces a key, keeping quaternion keys in one hemisphere. */
    fun setKey(time: Float, value: Vec4, interp: Interp = Interp.BEZIER, tolerance: Float = 0.002f): AnimKey {
        var v = value
        val existing = keys.indexOfFirst { abs(it.time - time) <= tolerance }
        var reference = existing - 1
        if (reference < 0 && keys.size > 1) reference = 0
        if (type == ChannelType.ROTATION && reference in keys.indices) {
            val prev = keys[reference].value
            if ((v.x * prev.x + v.y * prev.y + v.z * prev.z + v.w * prev.w) < 0f) {
                v = Vec4(-v.x, -v.y, -v.z, -v.w)
            }
        }
        if (existing >= 0) {
            keys[existing].value = v
            keys[existing].interp = interp
            keys[existing].time = time
            sort()
            return keys.first { abs(it.time - time) <= tolerance + 1e-6f }
        }
        val k = AnimKey(time, v, interp)
        keys.add(k)
        sort()
        return k
    }

    fun sample(time: Float): Vec4 {
        if (keys.isEmpty()) return defaultValue(type)
        if (keys.size == 1) return keys[0].value
        if (time <= keys.first().time) return keys.first().value
        if (time >= keys.last().time) return keys.last().value
        var index = 0
        while (index < keys.size - 1 && keys[index + 1].time < time) index++
        val k0 = keys[index]
        val k1 = keys[minOf(index + 1, keys.size - 1)]
        val dt = k1.time - k0.time
        if (dt <= 1e-6f) return k1.value
        val t = clamp((time - k0.time) / dt, 0f, 1f)
        return when (k0.interp) {
            Interp.STEP -> k0.value
            Interp.LINEAR -> interpolateValue(k0.value, k1.value, t, type)
            Interp.BEZIER -> {
                if (type == ChannelType.ROTATION) {
                    val q0 = k0.value.toQuat()
                    val q1 = k1.value.toQuat()
                    q0.slerp(q1, t).let { Vec4(it.x, it.y, it.z, it.w) }
                } else {
                    val p0 = keys.getOrNull(index - 1)?.value ?: k0.value
                    val p1 = keys.getOrNull(index + 2)?.value ?: k1.value
                    val t0 = keys.getOrNull(index - 1)?.time ?: (k0.time - dt)
                    val t1 = keys.getOrNull(index + 2)?.time ?: (k1.time + dt)
                    val m0 = (k1.value - p0).let { Vec4(it.x, it.y, it.z, it.w) * (1f / maxOf(dt, 1e-6f)) }
                    val m1 = (p1 - k0.value).let { Vec4(it.x, it.y, it.z, it.w) * (1f / maxOf(t1 - k0.time, 1e-6f)) }
                    hermite(k0.value, m0, k1.value, m1, dt, t)
                }
            }
        }
    }

    private fun hermite(p0: Vec4, m0: Vec4, p1: Vec4, m1: Vec4, dt: Float, t: Float): Vec4 {
        val t2 = t * t
        val t3 = t2 * t
        val h00 = 2f * t3 - 3f * t2 + 1f
        val h10 = t3 - 2f * t2 + t
        val h01 = -2f * t3 + 3f * t2
        val h11 = t3 - t2
        return Vec4(
            h00 * p0.x + h10 * dt * m0.x + h01 * p1.x + h11 * dt * m1.x,
            h00 * p0.y + h10 * dt * m0.y + h01 * p1.y + h11 * dt * m1.y,
            h00 * p0.z + h10 * dt * m0.z + h01 * p1.z + h11 * dt * m1.z,
            h00 * p0.w + h10 * dt * m0.w + h01 * p1.w + h11 * dt * m1.w
        )
    }

    private fun interpolateValue(a: Vec4, b: Vec4, t: Float, type: ChannelType): Vec4 {
        if (type == ChannelType.ROTATION) {
            val q = a.toQuat().slerp(b.toQuat(), t)
            return Vec4(q.x, q.y, q.z, q.w)
        }
        return a.lerp(b, t)
    }

    companion object {
        fun defaultValue(type: ChannelType): Vec4 = when (type) {
            ChannelType.TRANSLATION -> Vec4(0f, 0f, 0f, 0f)
            ChannelType.ROTATION -> Vec4(0f, 0f, 0f, 1f)
            ChannelType.SCALE -> Vec4(1f, 1f, 1f, 0f)
        }
    }
}

private fun Vec4.toQuat(): Quat = Quat(x, y, z, w).normalized()

class AnimationClip(
    var name: String,
    var duration: Float = 1f,
    var loop: Boolean = true,
    val tracks: MutableList<AnimTrack> = mutableListOf()
) {
    fun copy(): AnimationClip = AnimationClip(name, duration, loop, tracks.map { it.copy() }.toMutableList())

    fun track(bone: Int, type: ChannelType): AnimTrack? = tracks.firstOrNull { it.bone == bone && it.type == type }

    fun ensureTrack(bone: Int, type: ChannelType): AnimTrack {
        track(bone, type)?.let { return it }
        val t = AnimTrack(bone, type)
        tracks.add(t)
        return t
    }

    fun removeTrack(bone: Int, type: ChannelType) {
        tracks.removeAll { it.bone == bone && it.type == type }
    }

    fun allKeyTimes(): List<Float> = tracks.flatMap { t -> t.keys.map { it.time } }.distinct().sorted()

    fun mapTime(time: Float): Float {
        if (duration <= 0f) return 0f
        return if (loop) {
            var t = time % duration
            if (t < 0f) t += duration
            t
        } else time.coerceIn(0f, duration)
    }

    /**
     * Applies this clip to [pose]. Weight < 1 blends toward the rest pose;
     * additive mode layers the delta on top of the existing pose.
     */
    fun evaluate(time: Float, pose: Pose, weight: Float = 1f, additive: Boolean = false) {
        val t = mapTime(time)
        val w = clamp(weight, 0f, 1f)
        for (tr in tracks) {
            val bp = pose.bones.getOrNull(tr.bone) ?: continue
            val v = tr.sample(t)
            when (tr.type) {
                ChannelType.TRANSLATION -> {
                    val delta = Vec3(v.x, v.y, v.z)
                    bp.translation = if (additive) bp.translation + delta * w else delta * w
                }
                ChannelType.ROTATION -> {
                    var q = Quat(v.x, v.y, v.z, v.w).normalized()
                    if (q dot Quat.IDENTITY < 0f) q = Quat(-q.x, -q.y, -q.z, -q.w)
                    bp.rotation = if (additive) {
                        bp.rotation * Quat.IDENTITY.slerp(q, w)
                    } else {
                        Quat.IDENTITY.slerp(q, w)
                    }
                }
                ChannelType.SCALE -> {
                    val s = Vec3(v.x, v.y, v.z)
                    val blended = Vec3.ONE.lerp(s, w)
                    bp.scale = if (additive) {
                        Vec3(bp.scale.x * blended.x, bp.scale.y * blended.y, bp.scale.z * blended.z)
                    } else blended
                }
            }
        }
    }

    fun keyCount(): Int = tracks.sumOf { it.keys.size }

    fun boneTracks(): List<Int> = tracks.map { it.bone }.distinct().sorted()

    fun addBoneKey(bone: Int, time: Float, rotation: Quat? = null, translation: Vec3? = null, scale: Vec3? = null, interp: Interp = Interp.BEZIER) {
        rotation?.let { ensureTrack(bone, ChannelType.ROTATION).setKey(time, Vec4(it.x, it.y, it.z, it.w), interp) }
        translation?.let { ensureTrack(bone, ChannelType.TRANSLATION).setKey(time, Vec4(it.x, it.y, it.z, 0f), interp) }
        scale?.let { ensureTrack(bone, ChannelType.SCALE).setKey(time, Vec4(it.x, it.y, it.z, 0f), interp) }
    }

    fun totalDurationKeys(): Float = tracks.maxOfOrNull { t -> t.keys.maxOfOrNull { it.time } ?: 0f } ?: duration
}

class AnimationLayer(
    var name: String,
    var clipIndex: Int,
    var weight: Float = 1f,
    var additive: Boolean = false,
    var muted: Boolean = false
) {
    fun copy(): AnimationLayer = AnimationLayer(name, clipIndex, weight, additive, muted)
}

class PosePreset(
    var name: String,
    val bones: MutableList<BonePose> = mutableListOf()
) {
    fun copy(): PosePreset = PosePreset(name, bones.map { it.copy() }.toMutableList())

    fun apply(pose: Pose, weight: Float = 1f) {
        val w = clamp(weight, 0f, 1f)
        for (i in bones.indices) {
            val target = bones[i]
            val dst = pose.bones.getOrNull(i) ?: continue
            dst.translation = dst.translation.lerp(target.translation, w)
            dst.rotation = dst.rotation.slerp(target.rotation, w)
            dst.scale = dst.scale.lerp(target.scale, w)
        }
    }

    companion object {
        fun capture(name: String, pose: Pose): PosePreset =
            PosePreset(name, pose.bones.map { it.copy() }.toMutableList())
    }
}

object PoseSampler {
    /**
     * Full pose evaluation: base clip, then animation layers, then constraints.
     */
    fun sample(scene: Scene, time: Float): Pose {
        val skeleton = scene.skeleton ?: return Pose(0)
        val pose = Pose(skeleton.size)
        val active = scene.clips.getOrNull(scene.activeClip)
        active?.evaluate(time, pose, 1f)
        for (layer in scene.layers) {
            if (layer.muted || layer.weight <= 0f) continue
            val clip = scene.clips.getOrNull(layer.clipIndex) ?: continue
            if (layer.additive) {
                clip.evaluate(time, pose, layer.weight, additive = true)
            } else {
                val temp = Pose(skeleton.size)
                clip.evaluate(time, temp, 1f)
                pose.blendTo(temp, clamp(layer.weight, 0f, 1f))
            }
        }
        if (scene.constraints.isNotEmpty()) ConstraintSolver.apply(skeleton, pose, scene.constraints)
        return pose
    }

    fun sampleClip(skeleton: Skeleton, clip: AnimationClip, time: Float): Pose {
        val pose = Pose(skeleton.size)
        clip.evaluate(time, pose, 1f)
        return pose
    }

    fun applyPreset(pose: Pose, preset: PosePreset, weight: Float = 1f) = preset.apply(pose, weight)
}
