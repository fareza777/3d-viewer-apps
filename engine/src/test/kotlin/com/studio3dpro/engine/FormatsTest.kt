package com.studio3dpro.engine

import com.studio3dpro.engine.anim.AnimationLayer
import com.studio3dpro.engine.anim.PoseSampler
import com.studio3dpro.engine.io.BvhIO
import com.studio3dpro.engine.io.ObjIO
import com.studio3dpro.engine.io.ProjectIO
import com.studio3dpro.engine.io.StlIO
import com.studio3dpro.engine.mesh.MeshBoolean
import com.studio3dpro.engine.mesh.Primitives
import com.studio3dpro.engine.scene.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatsTest {

    @Test
    fun objRoundTrip() {
        val scene = Scene()
        scene.addObject("Cube", Primitives.cube(1.5f))
        val text = ObjIO.export(scene)
        assertTrue(text.contains("v "))
        val imported = ObjIO.import(text).scene
        assertEquals(1, imported.objects.size)
        val mesh = imported.objects[0].mesh!!
        assertEquals(6, mesh.faceCount)
        assertEquals(1.5f, mesh.bounds().size.x, 1e-3f)
        assertEquals(8, mesh.vertexCount)
        assertEquals(8, mesh.vertexCount)
    }

    @Test
    fun stlBinaryAndAsciiRoundTrip() {
        val scene = Scene()
        scene.addObject("Cube", Primitives.cube(1f))
        val binary = StlIO.export(scene, binary = true)
        val imported = StlIO.import(binary).scene
        assertEquals(12, imported.objects[0].mesh!!.faceCount)
        assertTrue(MeshBoolean.volume(imported.objects[0].mesh!!) in 0.9f..1.1f)
        val ascii = StlIO.export(scene, binary = false)
        val imported2 = StlIO.import(ascii).scene
        assertEquals(12, imported2.objects[0].mesh!!.faceCount)
    }

    @Test
    fun bvhRoundTripPreservesMotion() {
        val scene = DemoScene.create()
        val bvh = BvhIO.export(scene, clipIndex = 0, sampleRate = 24f)
        assertTrue(bvh.startsWith("HIERARCHY"))
        assertTrue(bvh.contains("MOTION"))
        val imported = BvhIO.import(bvh).scene
        assertNotNull(imported.skeleton)
        assertEquals(scene.skeleton!!.size, imported.skeleton!!.size)
        assertEquals(1, imported.clips.size)
        assertTrue(imported.clips[0].keyCount() > 50)
        val pose = PoseSampler.sample(imported, 0.4f)
        assertTrue(pose.bones.any { kotlin.math.abs(it.rotation.w) < 0.999f })
    }

    @Test
    fun projectRoundTripKeepsRigAndClips() {
        val scene = DemoScene.create()
        scene.layers.add(AnimationLayer("Overlay", 1, 0.5f, additive = true))
        val text = ProjectIO.save(scene)
        assertTrue(text.length > 10000)
        val loaded = ProjectIO.load(text)
        assertEquals(scene.objects.size, loaded.objects.size)
        assertEquals(scene.skeleton!!.size, loaded.skeleton!!.size)
        assertEquals(scene.clips.size, loaded.clips.size)
        assertEquals(scene.clips[0].keyCount(), loaded.clips[0].keyCount())
        assertTrue(loaded.objects.any { it.mesh?.skin != null })
        assertEquals(scene.layers.size, loaded.layers.size)
        assertEquals(scene.poseLibrary.size, loaded.poseLibrary.size)
        assertTrue(loaded.totalTriangles() > 1000)
        val poseA = PoseSampler.sample(scene, 0.5f)
        val poseB = PoseSampler.sample(loaded, 0.5f)
        for (i in poseA.bones.indices) {
            assertTrue(poseA.bones[i].rotation.angleTo(poseB.bones[i].rotation) < 1e-3f)
        }
    }

    @Test
    fun demoCharacterIsProductionReady() {
        val scene = DemoScene.create()
        assertTrue(scene.objects.size >= 7)
        assertTrue(scene.totalTriangles() > 5000)
        assertTrue(scene.skeleton!!.size >= 20)
        assertTrue(scene.clips.size == 3)
        assertTrue(scene.clips.all { it.keyCount() > 10 })
        val body = scene.objects.first { it.name.contains("Body") }
        val skin = body.mesh!!.skin!!
        for (v in 0 until skin.vertexCount) {
            var sum = 0f
            for (k in 0..3) sum += skin.weights[v * 4 + k]
            assertTrue(kotlin.math.abs(sum - 1f) < 1e-3f)
        }
        assertTrue(scene.poseLibrary.size >= 2)
    }
}
