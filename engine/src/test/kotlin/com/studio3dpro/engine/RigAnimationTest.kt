package com.studio3dpro.engine

import com.studio3dpro.engine.anim.AnimationClip
import com.studio3dpro.engine.anim.AnimTrack
import com.studio3dpro.engine.anim.ChannelType
import com.studio3dpro.engine.anim.CycleGenerator
import com.studio3dpro.engine.anim.Interp
import com.studio3dpro.engine.anim.PoseSampler
import com.studio3dpro.engine.anim.Retarget
import com.studio3dpro.engine.math.Quat
import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.math.Vec4
import com.studio3dpro.engine.mesh.Mesh
import com.studio3dpro.engine.mesh.Primitives
import com.studio3dpro.engine.rig.AutoRig
import com.studio3dpro.engine.rig.BoneConstraint
import com.studio3dpro.engine.rig.ConstraintSolver
import com.studio3dpro.engine.rig.ConstraintType
import com.studio3dpro.engine.rig.Ik
import com.studio3dpro.engine.rig.Skinning
import com.studio3dpro.engine.scene.Pose
import com.studio3dpro.engine.scene.Scene
import com.studio3dpro.engine.scene.Skeleton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RigAnimationTest {

    private fun bodyMesh(): Mesh {
        val mesh = Mesh()
        mesh.append(Primitives.uvSphere(0.16f, 16, 10), com.studio3dpro.engine.math.Mat4.translation(Vec3(0f, 1.45f, 0f)))
        mesh.append(Primitives.cylinder(0.16f, 0.2f, 0.55f, 16), com.studio3dpro.engine.math.Mat4.translation(Vec3(0f, 1.05f, 0f)))
        mesh.append(Primitives.cylinder(0.08f, 0.07f, 0.5f, 12), com.studio3dpro.engine.math.Mat4.translation(Vec3(0.25f, 1.2f, 0f)))
        mesh.append(Primitives.cylinder(0.08f, 0.06f, 0.45f, 12), com.studio3dpro.engine.math.Mat4.translation(Vec3(0.25f, 0.73f, 0f)))
        mesh.append(Primitives.cylinder(0.1f, 0.09f, 0.55f, 12), com.studio3dpro.engine.math.Mat4.translation(Vec3(0.1f, 0.55f, 0f)))
        mesh.append(Primitives.cylinder(0.08f, 0.06f, 0.45f, 12), com.studio3dpro.engine.math.Mat4.translation(Vec3(0.1f, 0.2f, 0f)))
        return mesh
    }

    @Test
    fun autoRigProducesValidHumanoid() {
        val mesh = bodyMesh()
        val skeleton = AutoRig.createHumanoid(mesh.bounds())
        assertTrue(skeleton.size >= 18)
        assertTrue(skeleton.isValidHierarchy())
        assertTrue(AutoRig.isHumanoid(skeleton))
        assertNotNull(skeleton.indexOfName("Hips").takeIf { it >= 0 })
        assertNotNull(skeleton.indexOfName("Hand.L").takeIf { it >= 0 })
        val weights = Skinning.computeWeights(mesh, skeleton)
        mesh.skin = weights
        assertEquals(mesh.vertexCount * 4, weights.weights.size)
        for (v in 0 until weights.vertexCount) {
            var sum = 0f
            for (k in 0..3) sum += weights.weights[v * 4 + k]
            assertTrue(abs(sum - 1f) < 1e-3f)
        }
    }

    @Test
    fun poseChangesSkinnedVertices() {
        val mesh = bodyMesh()
        val skeleton = AutoRig.createHumanoid(mesh.bounds())
        Skinning.bind(mesh, skeleton)
        val pose = Pose(skeleton.size)
        val before = Skinning.skinnedPosition(mesh, skeleton, pose, com.studio3dpro.engine.math.Mat4.identity(), 0)
        val hips = skeleton.indexOfName("Hips")
        pose.bones[hips].translation = Vec3(0.3f, 0.1f, 0f)
        val after = Skinning.skinnedPosition(mesh, skeleton, pose, com.studio3dpro.engine.math.Mat4.identity(), 0)
        assertTrue(before!!.distanceTo(after!!) > 0.01f)
    }

    @Test
    fun ikReachesTarget() {
        val mesh = bodyMesh()
        val skeleton = AutoRig.createHumanoid(mesh.bounds())
        val pose = Pose(skeleton.size)
        val hand = skeleton.indexOfName("Hand.L")
        val rest = skeleton.poseWorldMatrices(pose)
        val start = skeleton.poseTailWorld(hand, pose, rest)
        val target = start + Vec3(-0.15f, 0.12f, 0.08f)
        val result = Ik.solveCcd(skeleton, pose, hand, target, chainLength = 3, iterations = 30)
        assertTrue("IK error ${result.error}", result.error < 0.05f)
    }

    @Test
    fun limitRotationConstraintClamps() {
        val skeleton = Skeleton()
        skeleton.bones.add(com.studio3dpro.engine.scene.Bone("Root", -1, Vec3.ZERO, Quat.IDENTITY, 0.2f))
        val pose = Pose(1)
        pose.bones[0].rotation = Quat.axisAngle(Vec3.RIGHT, 1.2f)
        ConstraintSolver.apply(
            skeleton, pose,
            listOf(BoneConstraint(ConstraintType.LIMIT_ROTATION, 0,
                minEuler = Vec3(-10f, -10f, -10f), maxEuler = Vec3(10f, 10f, 10f)))
        )
        val deg = pose.bones[0].rotation.toEulerDegrees()
        assertTrue(abs(deg.x) <= 10.5f)
    }

    @Test
    fun keyframeInterpolation() {
        val track = AnimTrack(0, ChannelType.TRANSLATION)
        track.setKey(0f, Vec4(0f, 0f, 0f, 0f), Interp.LINEAR)
        track.setKey(1f, Vec4(2f, 0f, 0f, 0f), Interp.LINEAR)
        assertEquals(1f, track.sample(0.5f).x, 1e-4f)
        val rot = AnimTrack(0, ChannelType.ROTATION)
        rot.setKey(0f, Vec4(0f, 0f, 0f, 1f))
        rot.setKey(1f, Quat.axisAngle(Vec3.UP, 1f).let { Vec4(it.x, it.y, it.z, it.w) })
        assertTrue(rot.sample(0.5f).normalized().let { abs(it.w) } < 0.99f)
    }

    @Test
    fun cycleGeneratorCreatesUsableClip() {
        val mesh = bodyMesh()
        val skeleton = AutoRig.createHumanoid(mesh.bounds())
        val clip = CycleGenerator.generate(skeleton, run = true)
        assertTrue(clip.tracks.size >= 8)
        assertTrue(clip.keyCount() >= 50)
        assertTrue(clip.duration > 0.3f)
        val pose = Pose(skeleton.size)
        clip.evaluate(clip.duration * 0.25f, pose)
        assertTrue(pose.bones.any { it.rotation.let { q -> abs(q.w) < 0.999f } })
    }

    @Test
    fun walkCycleFlexesKneesBackward() {
        val skeleton = AutoRig.createHumanoid(bodyMesh().bounds())
        val walk = CycleGenerator.generate(skeleton, false)
        val shin = skeleton.indexOfName("Shin.L")
        val rest = skeleton.restWorldMatrices()
        val kneeZ = rest[shin].translation().z
        val pose = Pose(skeleton.size)
        // Mid-swing is the maximum knee bend: the ankle must sit behind the
        // knee. A positive local rotation would hyperextend it forward.
        walk.evaluate(walk.duration * 0.5f, pose)
        val world = skeleton.poseWorldMatrices(pose)
        val ankleZ = skeleton.restTailWorld(shin, world).z
        assertTrue("Knee hyperextends forward (ankleZ=$ankleZ, kneeZ=$kneeZ)", ankleZ < kneeZ - 0.10f)
    }

    @Test
    fun layersAndPoseSamplerWork() {
        val mesh = bodyMesh()
        val skeleton = AutoRig.createHumanoid(mesh.bounds())
        val scene = Scene()
        scene.skeleton = skeleton
        scene.objects.add(com.studio3dpro.engine.scene.SceneObject(1, "Body", mesh))
        val walk = CycleGenerator.generate(skeleton, false)
        scene.clips.add(walk)
        scene.clips.add(AnimationClip("Empty", 1f, true))
        scene.activeClip = 0
        val posed = PoseSampler.sample(scene, 0.3f)
        assertTrue(posed.bones.any { it.translation.lengthSq() > 0.0001f })
    }

    @Test
    fun retargetProducesClipForAnotherRig() {
        val source = AutoRig.createHumanoid(bodyMesh().bounds())
        val walk = CycleGenerator.generate(source, false)
        val targetBones = source.bones.map {
            val b = it.copy()
            b.headLocal *= 1.2f
            b.length *= 1.2f
            b
        }
        val target = Skeleton(targetBones.toMutableList())
        val retargeted = Retarget.retarget(source, walk, target)
        assertTrue(retargeted.tracks.isNotEmpty())
        assertTrue(retargeted.keyCount() > 20)
        val pose = Pose(target.size)
        retargeted.evaluate(0.4f, pose)
        assertTrue(pose.bones.any { abs(it.rotation.w) < 0.999f })
    }
}
