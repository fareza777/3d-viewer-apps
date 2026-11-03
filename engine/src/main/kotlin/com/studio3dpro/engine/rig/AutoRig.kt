package com.studio3dpro.engine.rig

import com.studio3dpro.engine.math.Aabb
import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.scene.Skeleton

/**
 * Creates a production-style 15+ bone biped skeleton fitted to a mesh's
 * bounding box. Joint positions follow humanoid proportions and use the
 * standard ".L/.R" naming so animation retargeting can map them.
 */
object AutoRig {

    val humanoidNames = listOf(
        "Root", "Hips", "Spine", "Chest", "Neck", "Head",
        "Shoulder.L", "UpperArm.L", "Forearm.L", "Hand.L",
        "Shoulder.R", "UpperArm.R", "Forearm.R", "Hand.R",
        "Thigh.L", "Shin.L", "Foot.L", "Toe.L",
        "Thigh.R", "Shin.R", "Foot.R", "Toe.R"
    )

    fun createHumanoid(bounds: Aabb): Skeleton {
        val h = (bounds.size.y.coerceAtLeast(0.2f))
        val cx = bounds.center.x
        val cz = bounds.center.z
        val ground = bounds.min.y
        val sk = Skeleton()

        fun p(x: Float, y: Float, z: Float = 0f) = Vec3(cx + x * h, ground + y * h, cz + z * h)
        fun add(name: String, parent: Int, head: Vec3, tail: Vec3): Int {
            return sk.addBoneFromWorld(name, parent, head, tail, sk.restWorldMatrices())
        }

        val root = add("Root", -1, p(0f, 0f), p(0f, 0.12f))
        val hips = add("Hips", root, p(0f, 0.52f), p(0f, 0.56f))
        val spine = add("Spine", hips, p(0f, 0.56f), p(0f, 0.64f))
        val chest = add("Chest", spine, p(0f, 0.64f), p(0f, 0.73f))
        val neck = add("Neck", chest, p(0f, 0.73f), p(0f, 0.79f))
        add("Head", neck, p(0f, 0.79f), p(0f, 0.96f))

        for (side in listOf(-1f, 1f)) {
            val suffix = if (side < 0) ".L" else ".R"
            val shoulder = add(
                "Shoulder$suffix", chest,
                p(side * 0.025f, 0.72f), p(side * 0.095f, 0.715f)
            )
            val upper = add(
                "UpperArm$suffix", shoulder,
                p(side * 0.095f, 0.715f), p(side * 0.20f, 0.575f)
            )
            val fore = add(
                "Forearm$suffix", upper,
                p(side * 0.20f, 0.575f), p(side * 0.255f, 0.415f)
            )
            val hand = add(
                "Hand$suffix", fore,
                p(side * 0.255f, 0.415f), p(side * 0.265f, 0.345f)
            )
            add("Fingers$suffix", hand, p(side * 0.265f, 0.345f), p(side * 0.265f, 0.30f))

            val thigh = add(
                "Thigh$suffix", hips,
                p(side * 0.075f, 0.515f), p(side * 0.085f, 0.30f)
            )
            val shin = add(
                "Shin$suffix", thigh,
                p(side * 0.085f, 0.30f), p(side * 0.092f, 0.075f)
            )
            val foot = add(
                "Foot$suffix", shin,
                p(side * 0.092f, 0.075f), p(side * 0.092f, 0.02f, 0.075f)
            )
            add("Toe$suffix", foot, p(side * 0.092f, 0.02f, 0.075f), p(side * 0.092f, 0.015f, 0.13f))
        }
        return sk
    }

    fun isHumanoid(skeleton: Skeleton): Boolean {
        val names = skeleton.bones.map { it.name.lowercase() }
        return names.contains("hips") && names.any { it.startsWith("thigh") } &&
            names.any { it.startsWith("upperarm") }
    }
}
