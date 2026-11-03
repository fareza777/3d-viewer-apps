package com.studio3dpro.engine

import com.studio3dpro.engine.anim.AnimationClip
import com.studio3dpro.engine.anim.CycleGenerator
import com.studio3dpro.engine.anim.PosePreset
import com.studio3dpro.engine.math.Aabb
import com.studio3dpro.engine.math.Mat4
import com.studio3dpro.engine.math.Quat
import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.math.Vec4
import com.studio3dpro.engine.math.degToRad
import com.studio3dpro.engine.mesh.ImplicitSurface
import com.studio3dpro.engine.mesh.Mesh
import com.studio3dpro.engine.mesh.MeshOps
import com.studio3dpro.engine.mesh.Primitives
import com.studio3dpro.engine.mesh.SdfCapsule
import com.studio3dpro.engine.mesh.SdfEllipsoid
import com.studio3dpro.engine.mesh.SdfShape
import com.studio3dpro.engine.mesh.SdfSphere
import com.studio3dpro.engine.mesh.SkinWeights
import com.studio3dpro.engine.mesh.UvProjection
import com.studio3dpro.engine.rig.AutoRig
import com.studio3dpro.engine.rig.Skinning
import com.studio3dpro.engine.scene.BonePose
import com.studio3dpro.engine.scene.Material
import com.studio3dpro.engine.scene.Pose
import com.studio3dpro.engine.scene.Scene
import com.studio3dpro.engine.scene.Skeleton
import com.studio3dpro.engine.scene.Transform
import com.studio3dpro.engine.anim.Interp
import com.studio3dpro.engine.anim.ChannelType
import kotlin.math.pow

/**
 * Built-in rigged character used as a live demo. Everything is generated at
 * runtime: a continuous skinned body mesh, a 24-bone humanoid rig, warm
 * studio materials, a display base and editable walk/run/idle clips.
 */
object DemoScene {

    fun create(): Scene {
        val scene = Scene()
        scene.name = "ATLAS · Demo"
        val skeleton = AutoRig.createHumanoid(Aabb(Vec3(-0.55f, 0f, -0.35f), Vec3(0.55f, 1.8f, 0.35f)))
        scene.skeleton = skeleton
        val rest = skeleton.restWorldMatrices()
        fun head(name: String): Vec3 = skeleton.restHeadWorld(rest, skeleton.indexOfName(name))
        fun tail(name: String): Vec3 = skeleton.restTailWorld(skeleton.indexOfName(name), rest)

        val headCenter = (head("Head") + tail("Head")) * 0.5f + Vec3(0f, 0.005f, 0.015f)

        // One continuous skin built from a signed distance field. Modelling the
        // body as blended capsules and ellipsoids avoids the hard intersection
        // lines (and z-fighting fringes) that stacked primitives produce.
        val shapes = ArrayList<SdfShape>()
        shapes.add(SdfEllipsoid(head("Hips"), Vec3(0.20f, 0.15f, 0.175f)))
        shapes.add(SdfCapsule(head("Hips"), head("Chest"), 0.152f, 0.182f))
        shapes.add(SdfEllipsoid(head("Chest"), Vec3(0.245f, 0.175f, 0.205f)))
        shapes.add(SdfCapsule(head("Chest"), head("Head"), 0.078f, 0.060f))
        shapes.add(SdfEllipsoid(headCenter, Vec3(0.162f, 0.178f, 0.155f)))
        for (side in listOf("L", "R")) {
            val hand = head("Hand.$side")
            val fingers = tail("Fingers.$side")
            // Shoulder to fingertips, with a readable palm and thumb.
            shapes.add(SdfCapsule(head("Shoulder.$side"), head("UpperArm.$side"), 0.066f, 0.078f))
            shapes.add(SdfCapsule(head("UpperArm.$side"), head("Forearm.$side"), 0.078f, 0.054f))
            shapes.add(SdfSphere(head("Forearm.$side"), 0.060f))
            shapes.add(SdfCapsule(head("Forearm.$side"), hand, 0.048f, 0.038f))
            shapes.add(SdfEllipsoid(hand + (fingers - hand) * 0.5f, Vec3(0.058f, 0.080f, 0.042f)))
            shapes.add(SdfSphere(fingers, 0.043f))
            val inward = if (hand.x >= 0f) -1f else 1f
            shapes.add(
                SdfCapsule(
                    hand + Vec3(inward * 0.025f, -0.02f, 0.012f),
                    hand + Vec3(inward * 0.062f, -0.052f, 0.04f),
                    0.030f, 0.024f
                )
            )
            // Hip to ankle. Real thigh and calf volume keeps the knees from
            // reading as a straight pipe when the rig bends.
            val thigh = head("Thigh.$side")
            val knee = head("Shin.$side")
            val ankle = head("Foot.$side")
            shapes.add(SdfSphere(thigh, 0.101f))
            shapes.add(SdfCapsule(thigh, knee, 0.101f, 0.077f))
            shapes.add(SdfEllipsoid(thigh + (knee - thigh) * 0.38f, Vec3(0.090f, 0.130f, 0.092f)))
            shapes.add(SdfSphere(knee, 0.079f))
            // The calf mass sits high and eases into a slim ankle. An ellipsoid
            // centred at the middle of the shin used to balloon the calf and
            // pinch the ankle, which read as a broken joint instead of a leg.
            shapes.add(SdfCapsule(knee, ankle, 0.068f, 0.042f))
            shapes.add(SdfEllipsoid(knee + (ankle - knee).normalized() * 0.080f, Vec3(0.066f, 0.080f, 0.071f)))
            shapes.add(SdfSphere(ankle, 0.043f))
        }
        val body = ImplicitSurface.mesh(
            Aabb(Vec3(-0.62f, -0.02f, -0.4f), Vec3(0.62f, 1.88f, 0.42f)),
            cellSize = 0.0185f,
            shapes = shapes,
            blend = 0.045f
        )
        UvProjection.project(body, UvProjection.Mode.BOX)
        Skinning.bind(body, skeleton)
        val bodyMat = Material(
            name = "Studio Clay",
            baseColor = Vec3(0.74f, 0.62f, 0.51f),
            metallic = 0f,
            roughness = 0.66f
        )
        scene.addObject("ATLAS Body", body, bodyMat)

        val graphite = Material(
            "Obsidian Alloy", Vec3(0.085f, 0.092f, 0.105f), metallic = 0.72f, roughness = 0.30f
        )
        val amber = Material(
            "Amber Alloy", Vec3(0.86f, 0.54f, 0.22f), metallic = 0.85f, roughness = 0.26f
        )
        val panelMat = Material(
            "Panel Black", Vec3(0.035f, 0.040f, 0.048f), metallic = 0.35f, roughness = 0.42f
        )
        // The plinth is deliberately a mid-tone stone instead of near-black:
        // the key-light shadow map needs a surface that can actually show a
        // silhouette, otherwise the character looks pasted onto the base.
        val plinthMat = Material(
            "Studio Plinth", Vec3(0.430f, 0.455f, 0.500f), metallic = 0.08f, roughness = 0.58f
        )
        val visorMat = Material(
            "Visor Glass", Vec3(0.020f, 0.075f, 0.085f),
            metallic = 0.90f, roughness = 0.08f,
            emissive = Vec3(0.06f, 0.55f, 0.46f), emissiveStrength = 0.55f
        )
        val coreMat = Material(
            "Core Emissive", Vec3(0.05f, 0.90f, 0.72f),
            metallic = 0.10f, roughness = 0.15f,
            emissive = Vec3(0.05f, 0.90f, 0.72f), emissiveStrength = 1.6f
        )
        val bootMat = Material(
            "Boot Alloy", Vec3(0.075f, 0.080f, 0.090f), metallic = 0.28f, roughness = 0.62f
        )
        val rubber = Material("Rubber", Vec3(0.045f, 0.048f, 0.052f), metallic = 0f, roughness = 0.94f)

        // Boots are hard-surface props, so they stay separate rigid pieces.
        // They are lofted from rounded cross-sections instead of scaled boxes:
        // the shaft hugs the ankle, swells over the instep and eases into a
        // rounded toe, so no flat flange or black gap is left at the joint.
        for (side in listOf("L", "R")) {
            val foot = head("Foot.$side")
            val sign = if (foot.x >= 0f) 1f else -1f
            val splay = Quat.axisAngle(Vec3.UP, degToRad(sign * 5f))
            val bootPose = Mat4.translation(foot) * Mat4.rotation(splay)

            val boot = loftedRingMesh(
                listOf(
                    floatArrayOf(-0.115f, 0.040f, 0.052f, 0.139f, 3.00f),
                    floatArrayOf(-0.098f, 0.036f, 0.055f, 0.132f, 3.00f),
                    floatArrayOf(-0.070f, 0.030f, 0.056f, 0.115f, 2.90f),
                    floatArrayOf(-0.035f, 0.020f, 0.055f, 0.093f, 2.70f),
                    floatArrayOf(0.000f, 0.010f, 0.053f, 0.074f, 2.50f),
                    floatArrayOf(0.035f, 0.005f, 0.052f, 0.064f, 2.35f),
                    floatArrayOf(0.070f, 0.003f, 0.057f, 0.060f, 2.20f),
                    floatArrayOf(0.096f, 0.002f, 0.059f, 0.062f, 2.10f),
                    floatArrayOf(0.118f, 0.001f, 0.061f, 0.063f, 2.05f)
                ),
                capBottomY = -0.118f, capBottomZ = 0.040f,
                capTopY = 0.122f, capTopZ = 0.0f
            )
            boot.transform(bootPose)
            boot.markDirty()
            bindRigid(boot, skeleton.indexOfName("Foot.$side"))
            scene.addObject("Boot $side", boot, bootMat)

            val sole = loftedRingMesh(
                listOf(
                    floatArrayOf(-0.112f, 0.040f, 0.060f, 0.149f, 3.00f),
                    floatArrayOf(-0.128f, 0.040f, 0.062f, 0.152f, 3.00f),
                    floatArrayOf(-0.133f, 0.040f, 0.056f, 0.146f, 3.00f)
                ),
                capBottomY = -0.135f, capBottomZ = 0.040f,
                capTopY = -0.108f, capTopZ = 0.040f
            )
            sole.transform(bootPose)
            sole.markDirty()
            bindRigid(sole, skeleton.indexOfName("Foot.$side"))
            scene.addObject("Boot Sole $side", sole, rubber)

            // A slim collar finishes the boot opening so the leg meets a
            // padded rim instead of a hard cut edge.
            val cuff = Primitives.torus(0.0605f, 0.006f, 44, 10)
            cuff.transform(
                Mat4.translation(foot + Vec3(0f, 0.117f, 0.001f)) *
                    Mat4.rotation(splay) *
                    Mat4.scaling(Vec3(1f, 1f, 1.02f))
            )
            cuff.markDirty()
            bindRigid(cuff, skeleton.indexOfName("Foot.$side"))
            scene.addObject("Boot Collar $side", cuff, graphite)
        }

        // Chest armour: a curved shield that follows the ribcage instead of a box.
        val chestPlate = Primitives.uvSphere(1f, 48, 26)
        chestPlate.transform(
            Mat4.translation(Vec3(0f, 1.252f, 0.188f)) *
                Mat4.scaling(Vec3(0.168f, 0.118f, 0.052f))
        )
        chestPlate.markDirty()
        bindRigid(chestPlate, skeleton.indexOfName("Chest"))
        scene.addObject("Chest Plate", chestPlate, graphite)

        val chestInset = Primitives.uvSphere(1f, 40, 22)
        chestInset.transform(
            Mat4.translation(Vec3(0f, 1.252f, 0.228f)) *
                Mat4.scaling(Vec3(0.132f, 0.086f, 0.020f))
        )
        chestInset.markDirty()
        bindRigid(chestInset, skeleton.indexOfName("Chest"))
        scene.addObject("Chest Inset", chestInset, panelMat)

        val coreRing = Primitives.torus(0.038f, 0.007f, 40, 10)
        coreRing.transform(
            Mat4.translation(Vec3(0f, 1.252f, 0.248f)) *
                Mat4.rotation(Quat.axisAngle(Vec3(1f, 0f, 0f), degToRad(90f)))
        )
        coreRing.markDirty()
        bindRigid(coreRing, skeleton.indexOfName("Chest"))
        scene.addObject("Core Ring", coreRing, amber)

        val core = Primitives.cylinder(0.024f, 0.024f, 0.014f, 28)
        core.transform(
            Mat4.translation(Vec3(0f, 1.252f, 0.250f)) *
                Mat4.rotation(Quat.axisAngle(Vec3(1f, 0f, 0f), degToRad(90f)))
        )
        core.markDirty()
        bindRigid(core, skeleton.indexOfName("Chest"))
        scene.addObject("Core", core, coreMat)

        // Collar and belt tie the armour into one suit instead of loose parts.
        val collar = Primitives.torus(0.094f, 0.013f, 48, 10)
        collar.transform(Mat4.translation(Vec3(0f, 1.365f, 0f)) * Mat4.scaling(Vec3(1.12f, 1f, 0.82f)))
        collar.markDirty()
        bindRigid(collar, skeleton.indexOfName("Chest"))
        scene.addObject("Collar", collar, graphite)

        val belt = Primitives.torus(0.205f, 0.016f, 48, 10)
        belt.transform(Mat4.translation(Vec3(0f, 0.955f, 0f)) * Mat4.scaling(Vec3(1f, 1f, 0.82f)))
        belt.markDirty()
        bindRigid(belt, skeleton.indexOfName("Hips"))
        scene.addObject("Belt", belt, graphite)

        val buckle = Primitives.roundedCube(1f, 0.30f)
        buckle.transform(Mat4.translation(Vec3(0f, 0.952f, 0.178f)) * Mat4.scaling(Vec3(0.060f, 0.045f, 0.020f)))
        buckle.markDirty()
        bindRigid(buckle, skeleton.indexOfName("Hips"))
        scene.addObject("Belt Buckle", buckle, amber)

        // Visor: shallow dark frame with a glowing lens instead of a pasted oval.
        val visorFrame = Primitives.roundedCube(1f, 0.36f)
        visorFrame.transform(
            Mat4.translation(Vec3(0f, 1.600f, 0.150f)) *
                Mat4.scaling(Vec3(0.315f, 0.115f, 0.065f))
        )
        visorFrame.markDirty()
        bindRigid(visorFrame, skeleton.indexOfName("Head"))
        scene.addObject("Visor Frame", visorFrame, graphite)

        val visorLens = Primitives.roundedCube(1f, 0.42f)
        visorLens.transform(
            Mat4.translation(Vec3(0f, 1.600f, 0.181f)) *
                Mat4.scaling(Vec3(0.255f, 0.062f, 0.022f))
        )
        visorLens.markDirty()
        bindRigid(visorLens, skeleton.indexOfName("Head"))
        scene.addObject("Visor Lens", visorLens, visorMat)

        // Ear discs break up the silhouette from the side.
        for (side in listOf("L", "R")) {
            val sign = if (side == "L") -1f else 1f
            val ear = Primitives.cylinder(0.042f, 0.042f, 0.020f, 24)
            ear.transform(
                Mat4.translation(Vec3(sign * 0.158f, 1.600f, 0f)) *
                    Mat4.rotation(Quat.axisAngle(Vec3(0f, 0f, 1f), degToRad(90f)))
            )
            ear.markDirty()
            bindRigid(ear, skeleton.indexOfName("Head"))
            scene.addObject("Ear $side", ear, graphite)

            val earDot = Primitives.cylinder(0.018f, 0.018f, 0.012f, 20)
            earDot.transform(
                Mat4.translation(Vec3(sign * 0.173f, 1.600f, 0f)) *
                    Mat4.rotation(Quat.axisAngle(Vec3(0f, 0f, 1f), degToRad(90f)))
            )
            earDot.markDirty()
            bindRigid(earDot, skeleton.indexOfName("Head"))
            scene.addObject("Ear Dot $side", earDot, amber)
        }

        // Shoulder shells hug the deltoid: a smaller, tilted cap that sinks
        // into the arm instead of floating above it like a separate ball.
        for (side in listOf("L", "R")) {
            val sign = if (head("UpperArm.$side").x >= 0f) 1f else -1f
            val pad = Primitives.uvSphere(1f, 36, 20)
            pad.transform(
                Mat4.translation(head("UpperArm.$side") + Vec3(sign * 0.016f, 0.020f, 0f)) *
                    Mat4.rotation(Quat.axisAngle(Vec3(0f, 0f, 1f), degToRad(-sign * 15f))) *
                    Mat4.scaling(Vec3(0.101f, 0.061f, 0.117f))
            )
            pad.markDirty()
            bindRigid(pad, skeleton.indexOfName("UpperArm.$side"))
            scene.addObject("Shoulder Pad $side", pad, graphite)
        }

        // Amber wrist cuffs visually separate hands from forearms.
        for (side in listOf("L", "R")) {
            val fore = head("Forearm.$side")
            val hand = head("Hand.$side")
            val armDir = (hand - fore).normalized()
            val cuff = Primitives.torus(0.046f, 0.009f, 36, 8)
            cuff.transform(
                Mat4.translation(hand - armDir * 0.004f) *
                    Mat4.rotation(Quat.fromToRotation(Vec3.UP, armDir)) *
                    Mat4.scaling(Vec3(1f, 1f, 0.85f))
            )
            cuff.markDirty()
            bindRigid(cuff, skeleton.indexOfName("Hand.$side"))
            scene.addObject("Wrist Cuff $side", cuff, amber)
        }

        // Display base.
        val base = Primitives.cylinder(0.56f, 0.53f, 0.045f, 64)
        base.transform(Mat4.translation(Vec3(0f, -0.0225f, 0f)))
        base.markDirty()
        UvProjection.project(base, UvProjection.Mode.CYLINDRICAL)
        scene.addObject("Display Base", base, plinthMat, Transform())

        val baseTrim = Primitives.torus(0.548f, 0.0045f, 96, 8)
        baseTrim.transform(Mat4.translation(Vec3(0f, 0.002f, 0f)))
        baseTrim.markDirty()
        scene.addObject("Base Trim", baseTrim, amber)

        val walk = CycleGenerator.generate(skeleton, run = false).also { it.name = "Walk Cycle" }
        val run = CycleGenerator.generate(skeleton, run = true).also { it.name = "Run Cycle" }
        val idle = createIdle(skeleton)
        scene.clips.add(walk)
        scene.clips.add(run)
        scene.clips.add(idle)
        // Open on the breathing idle so the first frame reads as a finished
        // character; walk and run remain one tap away in Animate.
        scene.activeClip = 2
        scene.poseLibrary.add(PosePreset("T-Pose", skeleton.bindPose().bones.map { it.copy() }.toMutableList()))
        val wavePose = Pose(skeleton.size)
        val leftArm = skeleton.indexOfName("UpperArm.L")
        val leftForearm = skeleton.indexOfName("Forearm.L")
        wavePose.bones[leftArm].rotation = Quat.axisAngle(Vec3(0f, 0f, 1f), degToRad(-95f))
        wavePose.bones[leftForearm].rotation = Quat.axisAngle(Vec3(0f, 0f, 1f), degToRad(-25f))
        scene.poseLibrary.add(PosePreset.capture("Wave", wavePose))
        scene.touch()
        return scene
    }

    /**
     * Lofts a closed surface through rounded (superellipse) cross-sections.
     * Each ring is [y, centerZ, halfX, halfZ, exponent]; higher exponents give
     * a squarer footprint, which is what makes a shoe read as a shoe.
     */
    private fun loftedRingMesh(
        rings: List<FloatArray>,
        capBottomY: Float,
        capBottomZ: Float,
        capTopY: Float,
        capTopZ: Float,
        segments: Int = 40
    ): Mesh {
        val mesh = Mesh()
        fun ringLevel(level: FloatArray): IntArray {
            val y = level[0]
            val cz = level[1]
            val hx = level[2]
            val hz = level[3]
            val exp = level[4]
            val indices = IntArray(segments)
            for (s in 0 until segments) {
                val t = 2f * kotlin.math.PI.toFloat() * s / segments
                val c = kotlin.math.cos(t)
                val sn = kotlin.math.sin(t)
                val x = hx * (if (c < 0f) -1f else 1f) * kotlin.math.abs(c).pow(2f / exp)
                val z = cz + hz * (if (sn < 0f) -1f else 1f) * kotlin.math.abs(sn).pow(2f / exp)
                indices[s] = mesh.addVertex(Vec3(x, y, z))
            }
            return indices
        }
        val loops = rings.map { ringLevel(it) }
        for (r in 0 until loops.size - 1) {
            val lower = loops[r]
            val upper = loops[r + 1]
            for (s in 0 until segments) {
                val s2 = (s + 1) % segments
                val u0 = s.toFloat() / segments
                val u1 = (s + 1).toFloat() / segments
                mesh.addFace(
                    intArrayOf(lower[s], upper[s], upper[s2], lower[s2]),
                    floatArrayOf(u0, 0f, u0, 1f, u1, 1f, u1, 0f)
                )
            }
        }
        val bottom = mesh.addVertex(Vec3(0f, capBottomY, capBottomZ))
        val top = mesh.addVertex(Vec3(0f, capTopY, capTopZ))
        val first = loops.first()
        val last = loops.last()
        for (s in 0 until segments) {
            val s2 = (s + 1) % segments
            mesh.addFace(intArrayOf(bottom, first[s], first[s2]), floatArrayOf(0.5f, 0.5f, 0f, 0f, 1f, 0f))
            mesh.addFace(intArrayOf(top, last[s2], last[s]), floatArrayOf(0.5f, 0.5f, 1f, 1f, 0f, 1f))
        }
        mesh.markDirty()
        MeshOps.ensureOutward(mesh)
        return mesh
    }

    fun bindRigid(mesh: Mesh, bone: Int) {
        val n = mesh.vertexCount
        val bones = IntArray(n * 4) { if (it % 4 == 0) bone else 0 }
        val weights = FloatArray(n * 4) { if (it % 4 == 0) 1f else 0f }
        mesh.skin = SkinWeights(bones, weights)
    }

    private fun createIdle(skeleton: Skeleton): AnimationClip {
        val clip = AnimationClip("Idle", 2.8f, true)
        val hips = skeleton.indexOfName("Hips")
        val chest = skeleton.indexOfName("Chest")
        val head = skeleton.indexOfName("Head")
        val leftArm = skeleton.indexOfName("UpperArm.L")
        val rightArm = skeleton.indexOfName("UpperArm.R")
        for (i in 0..8) {
            val t = 2.8f * i / 8f
            val phase = i / 8f * Math.PI.toFloat() * 2f
            val breath = kotlin.math.sin(phase)
            clip.addBoneKey(hips, t, translation = Vec3(0f, 0.008f * breath, 0f))
            clip.addBoneKey(chest, t, rotation = Quat.fromEulerDegrees(Vec3(1.6f * breath, 0f, 0f)), interp = Interp.BEZIER)
            clip.addBoneKey(head, t, rotation = Quat.fromEulerDegrees(Vec3(1f * breath, 4f * kotlin.math.sin(phase * 0.5f), 0f)), interp = Interp.BEZIER)
            clip.addBoneKey(leftArm, t, rotation = Quat.axisAngle(Vec3.RIGHT, degToRad(1.5f * breath)))
            clip.addBoneKey(rightArm, t, rotation = Quat.axisAngle(Vec3.RIGHT, degToRad(-1.5f * breath)))
        }
        return clip
    }
}
