package com.studio3dpro.engine

import com.studio3dpro.engine.anim.CycleGenerator
import com.studio3dpro.engine.anim.PoseSampler
import com.studio3dpro.engine.io.Glb
import com.studio3dpro.engine.io.GltfAccessorReader
import com.studio3dpro.engine.io.GltfExporter
import com.studio3dpro.engine.io.GltfImporter
import com.studio3dpro.engine.math.Mat4
import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.mesh.Primitives
import com.studio3dpro.engine.rig.AutoRig
import com.studio3dpro.engine.rig.Skinning
import com.studio3dpro.engine.scene.Material
import com.studio3dpro.engine.scene.Scene
import com.studio3dpro.engine.scene.Transform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GltfIoTest {

    @Test
    fun importsCesiumManWithSkeletonAndAnimation() {
        val bytes = File("src/test/resources/models/CesiumMan.glb").readBytes()
        val result = GltfImporter().import(bytes, "CesiumMan")
        val scene = result.scene
        assertTrue("objects=${scene.objects.size}", scene.objects.isNotEmpty())
        assertTrue("triangles=${scene.totalTriangles()}", scene.totalTriangles() > 100)
        assertTrue("skeleton=${scene.skeleton?.size}", (scene.skeleton?.size ?: 0) > 10)
        assertTrue("clips=${scene.clips.size}", scene.clips.isNotEmpty())
        assertTrue(scene.clips[0].keyCount() > 10)
        assertTrue(scene.objects.any { it.mesh?.skin != null })
        val pose = PoseSampler.sample(scene, 0.8f)
        assertTrue(pose.bones.any { it.rotation.w < 0.999f })
    }

    @Test
    fun importsFoxWithMaterialsAndClips() {
        val bytes = File("src/test/resources/models/Fox.glb").readBytes()
        val result = GltfImporter().import(bytes, "Fox")
        val scene = result.scene
        assertTrue(scene.objects.isNotEmpty())
        assertTrue((scene.skeleton?.size ?: 0) > 5)
        assertTrue(scene.clips.size >= 1)
        assertTrue(scene.objects.any { it.material.baseColorTexture != null })
    }

    @Test
    fun exportedGlbReimportsWithSkinAndAnimation() {
        val mesh = Primitives.uvSphere(0.5f, 24, 14)
        mesh.transform(Mat4.translation(Vec3(0f, 1f, 0f)))
        val scene = Scene()
        scene.name = "Rigged"
        val skeleton = AutoRig.createHumanoid(mesh.bounds())
        Skinning.bind(mesh, skeleton)
        scene.skeleton = skeleton
        scene.addObject("Character", mesh, Material(name = "Clay", baseColor = Vec3(0.82f, 0.55f, 0.3f)))
        val walk = CycleGenerator.generate(skeleton)
        scene.clips.add(walk)
        scene.activeClip = 0
        val bytes = GltfExporter().exportGlb(scene)
        assertTrue(Glb.isGlb(bytes))
        val reimport = GltfImporter().import(bytes, "Reimported")
        val rs = reimport.scene
        assertEquals(1, rs.objects.size)
        assertTrue(rs.skeleton != null)
        assertTrue(rs.clips.isNotEmpty())
        assertTrue(rs.objects[0].mesh?.skin != null)
        assertEquals(24f * 14f * 2f, rs.objects[0].mesh!!.faceCount.toFloat(), 300f)
        assertTrue(rs.clips[0].duration > 0.5f)
        assertTrue(reimport.warnings.isEmpty())
    }

    @Test
    fun exportedGlbIsKhronosSpecClean() {
        val scene = DemoScene.create()
        val bytes = GltfExporter().exportGlb(scene)
        val container = Glb.read(bytes)
        val json = container.json
        val extensions = json.arr("extensionsUsed")?.items?.mapNotNull { it.asString } ?: emptyList()
        assertTrue("extensionsUsed=$extensions", extensions.contains("KHR_materials_emissive_strength"))
        val bin = container.bin!!
        val reader = GltfAccessorReader(json, listOf(bin))
        val skin = json.arr("skins")!!.items.first().asObj!!
        val ibm = reader.read(skin.int("inverseBindMatrices")!!)
        assertEquals(skin.arr("joints")!!.items.size, ibm.count)
        for (i in 0 until ibm.count) {
            val m = ibm.mat4(i)
            assertEquals(0f, m[3], 0f)
            assertEquals(0f, m[7], 0f)
            assertEquals(0f, m[11], 0f)
            assertEquals(1f, m[15], 0f)
        }
        val jointAccessor = json.arr("meshes")!!.items
            .flatMap { it.asObj!!.arr("primitives")!!.items }
            .map { it.asObj!! }
            .firstNotNullOfOrNull { it.obj("attributes")?.int("JOINTS_0") }
        assertTrue("skinned JOINTS_0 accessor expected", jointAccessor != null)
        val jointView = json.arr("bufferViews")!!.items[json.arr("accessors")!!.items[jointAccessor!!].asObj!!.int("bufferView")!!]
        assertEquals(34962, jointView.asObj!!.int("target"))
        val animations = json.arr("animations")!!.items
        assertEquals(3, animations.size)
        assertTrue(animations.all { it.asObj!!.arr("channels")!!.items.isNotEmpty() })
    }

    @Test
    fun unskinnedExportPreservesTransform() {
        val scene = Scene()
        val cube = Primitives.cube(1f)
        scene.addObject("Cube", cube, Material(), Transform(Vec3(1f, 2f, 3f)))
        val bytes = GltfExporter().exportGlb(scene)
        val imported = GltfImporter().import(bytes).scene
        assertEquals(1, imported.objects.size)
        val c = imported.objects[0].mesh!!.bounds()
        assertEquals(1.0f, c.center.x, 0.01f)
        assertEquals(2.0f, c.center.y, 0.01f)
        assertEquals(3.0f, c.center.z, 0.01f)
    }
}
