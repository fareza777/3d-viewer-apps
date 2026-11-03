package com.studio3dpro.engine.io

import com.studio3dpro.engine.anim.AnimationClip
import com.studio3dpro.engine.anim.ChannelType
import com.studio3dpro.engine.anim.Interp
import com.studio3dpro.engine.math.Quat
import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.math.Vec4
import com.studio3dpro.engine.math.degToRad
import com.studio3dpro.engine.math.radToDeg
import com.studio3dpro.engine.scene.Bone
import com.studio3dpro.engine.scene.Pose
import com.studio3dpro.engine.scene.Scene
import com.studio3dpro.engine.scene.Skeleton
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt

/** BVH motion capture import and export. */
object BvhIO {

    private class Joint(
        val name: String,
        val parent: Int,
        var offset: Vec3 = Vec3.ZERO,
        val channels: MutableList<String> = mutableListOf()
    ) {
        var endOffset: Vec3? = null
    }

    private class Motion(val frameTime: Float, val frames: List<FloatArray>)

    fun import(text: String, name: String = "BVH Motion"): ModelImportResult {
        val warnings = ArrayList<String>()
        val joints = ArrayList<Joint>()
        val stack = ArrayList<Int>()
        var pendingEnd: Int = -1
        var inEndSite = false
        var motion: Motion? = null
        val lines = text.lineSequence().toList()
        var i = 0
        while (i < lines.size) {
            val raw = lines[i].trim()
            val parts = raw.split(Regex("\\s+"))
            when {
                raw.startsWith("ROOT ", true) || raw.startsWith("JOINT ", true) -> {
                    val root = raw.startsWith("ROOT", true)
                    val j = Joint(parts.getOrElse(1) { "Joint${joints.size}" }, stack.lastOrNull() ?: -1)
                    if (root) stack.clear()
                    joints.add(j)
                    stack.add(joints.size - 1)
                }
                raw.equals("{", true) -> { /* block open */ }
                raw.equals("}", true) -> {
                    if (inEndSite) inEndSite = false
                    else if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                }
                raw.startsWith("OFFSET", true) -> {
                    val v = parts.drop(1).mapNotNull { it.toFloatOrNull() }
                    if (v.size >= 3) {
                        val offset = Vec3(v[0], v[1], v[2])
                        if (inEndSite) joints.getOrNull(pendingEnd)?.endOffset = offset
                        else joints.lastOrNull()?.offset = offset
                    }
                }
                raw.startsWith("CHANNELS", true) -> {
                    joints.lastOrNull()?.channels?.addAll(parts.drop(2))
                }
                raw.startsWith("End Site", true) -> {
                    pendingEnd = joints.size - 1
                    inEndSite = true
                }
                raw.startsWith("MOTION", true) -> {
                    var frames = 0
                    var frameTime = 1f / 30f
                    i++
                    while (i < lines.size) {
                        val l = lines[i].trim()
                        when {
                            l.startsWith("Frames:", true) -> frames = l.substringAfter(':').trim().toIntOrNull() ?: 0
                            l.startsWith("Frame Time:", true) -> frameTime =
                                l.substringAfter(':').trim().toFloatOrNull() ?: frameTime
                            l.startsWith("Frame", true) || l.isEmpty() -> { /* keep scanning */ }
                            else -> break
                        }
                        i++
                    }
                    val frameData = ArrayList<FloatArray>()
                    while (i < lines.size && frameData.size < max(frames, 0)) {
                        val l = lines[i].trim()
                        if (l.isNotEmpty()) {
                            val nums = l.split(Regex("\\s+")).mapNotNull { it.toFloatOrNull() }
                            if (nums.isNotEmpty()) frameData.add(nums.toFloatArray())
                        }
                        i++
                    }
                    motion = Motion(frameTime, frameData)
                    break
                }
            }
            i++
        }
        if (joints.isEmpty()) throw IllegalArgumentException("BVH has no joints")
        val maxOffset = joints.maxOf { it.offset.length() + (it.endOffset?.length() ?: 0f) }
        val scale = when {
            maxOffset > 50f -> 0.01f
            maxOffset > 5f -> 0.1f
            else -> 1f
        }
        if (scale != 1f) warnings.add("BVH units auto-scaled by ${scale}.")
        val skeleton = Skeleton()
        for ((index, j) in joints.withIndex()) {
            val childOffset = joints.withIndex()
                .filter { it.value.parent == index }
                .minOfOrNull { it.value.offset.length() * scale }
            val len = (j.endOffset?.length()?.times(scale) ?: childOffset ?: 0.1f).coerceAtLeast(0.01f)
            skeleton.bones.add(Bone(j.name.replace(' ', '_'), j.parent, j.offset * scale, Quat.IDENTITY, len))
        }
        val scene = Scene()
        scene.name = name
        scene.skeleton = skeleton
        val m = motion ?: throw IllegalArgumentException("BVH has no MOTION section")
        val clip = AnimationClip("BVH Motion", (m.frames.size * m.frameTime).coerceAtLeast(0.1f), true)
        val frameCount = m.frames.size
        for (f in 0 until frameCount) {
            var cursor = 0
            val time = f * m.frameTime
            for (j in joints.indices) {
                var q = Quat.IDENTITY
                var translation = Vec3.ZERO
                for (channel in joints[j].channels) {
                    val value = m.frames[f].getOrNull(cursor) ?: 0f
                    cursor++
                    when (channel.lowercase()) {
                        "xrotation" -> q = q * Quat.axisAngle(Vec3.RIGHT, degToRad(value))
                        "yrotation" -> q = q * Quat.axisAngle(Vec3.UP, degToRad(value))
                        "zrotation" -> q = q * Quat.axisAngle(Vec3(0f, 0f, 1f), degToRad(value))
                        "xposition" -> translation = Vec3(value * scale, translation.y, translation.z)
                        "yposition" -> translation = Vec3(translation.x, value * scale, translation.z)
                        "zposition" -> translation = Vec3(translation.x, translation.y, value * scale)
                    }
                }
                clip.ensureTrack(j, ChannelType.ROTATION).setKey(
                    time, Vec4(q.x, q.y, q.z, q.w), Interp.LINEAR
                )
                if (joints[j].channels.any { it.endsWith("position", true) }) {
                    clip.ensureTrack(j, ChannelType.TRANSLATION)
                        .setKey(time, Vec4(translation.x, translation.y, translation.z, 0f), Interp.LINEAR)
                }
            }
        }
        scene.clips.add(clip)
        scene.activeClip = 0
        scene.touch()
        return ModelImportResult(scene, warnings)
    }

    fun export(scene: Scene, clipIndex: Int = scene.activeClip, sampleRate: Float = 30f): String {
        val skeleton = scene.skeleton ?: throw IllegalArgumentException("Scene has no skeleton")
        val clip = scene.clips.getOrNull(clipIndex) ?: throw IllegalArgumentException("No animation clip selected")
        val roots = skeleton.bones.indices.filter { skeleton.bones[it].parent < 0 }
        if (roots.isEmpty()) throw IllegalArgumentException("Skeleton has no root bone")
        val sb = StringBuilder()
        sb.appendLine("HIERARCHY")
        fun writeJoint(index: Int, depth: Int, isRoot: Boolean) {
            val indent = "  ".repeat(depth)
            val bone = skeleton.bones[index]
            val name = bone.name.replace(' ', '_')
            sb.appendLine("$indent${if (isRoot) "ROOT" else "JOINT"} $name")
            sb.appendLine("$indent{")
            sb.appendLine("$indent  OFFSET ${fmt(bone.headLocal.x)} ${fmt(bone.headLocal.y)} ${fmt(bone.headLocal.z)}")
            sb.appendLine("$indent  CHANNELS 6 Zrotation Xrotation Yrotation Xposition Yposition Zposition")
            val children = skeleton.children(index)
            if (children.isEmpty()) {
                val tail = bone.restRotation.rotate(Vec3(0f, bone.length, 0f))
                sb.appendLine("$indent  End Site")
                sb.appendLine("$indent  {")
                sb.appendLine("$indent    OFFSET ${fmt(tail.x)} ${fmt(tail.y)} ${fmt(tail.z)}")
                sb.appendLine("$indent  }")
            } else {
                for (c in children) writeJoint(c, depth + 1, false)
            }
            sb.appendLine("$indent}")
        }
        writeJoint(roots[0], 1, true)
        sb.appendLine("MOTION")
        val rate = sampleRate.coerceIn(5f, 120f)
        val frameCount = maxOf(2, (clip.duration * rate).roundToInt() + 1)
        sb.appendLine("Frames: $frameCount")
        sb.appendLine("Frame Time: ${String.format(Locale.US, "%.7f", 1f / rate)}")
        for (f in 0 until frameCount) {
            val pose = Pose(skeleton.size)
            clip.evaluate(clip.duration * f / (frameCount - 1).toFloat(), pose)
            val values = ArrayList<Float>(skeleton.size * 6)
            for (i in skeleton.bones.indices) {
                val bone = skeleton.bones[i]
                val q = (bone.restRotation * pose.bones[i].rotation).normalized()
                val eulerZxy = quatToZxy(q)
                values.add(radToDeg(eulerZxy.x))
                values.add(radToDeg(eulerZxy.y))
                values.add(radToDeg(eulerZxy.z))
                val t = pose.bones[i].translation
                values.add(t.x); values.add(t.y); values.add(t.z)
            }
            sb.appendLine(values.joinToString(" ") { String.format(Locale.US, "%.6f", it) })
        }
        return sb.toString()
    }

    /** Returns (z, x, y) radians for R = Rz * Rx * Ry. */
    fun quatToZxy(q: Quat): Vec3 {
        val m = q.toMat4()
        val m01 = m[4]; val m11 = m[5]
        val m20 = m[2]; val m21 = m[6]; val m22 = m[10]
        val x = asin(m21.coerceIn(-1f, 1f))
        val z = atan2(-m01, m11)
        val y = atan2(-m20, m22)
        return Vec3(z, x, y)
    }

    fun zxyToQuat(zRad: Float, xRad: Float, yRad: Float): Quat =
        Quat.axisAngle(Vec3(0f, 0f, 1f), zRad) *
            Quat.axisAngle(Vec3.RIGHT, xRad) *
            Quat.axisAngle(Vec3.UP, yRad)

    private fun fmt(v: Float): String = String.format(Locale.US, "%.6f", v)
}
