package com.studio3dpro.engine

import com.studio3dpro.engine.anim.PoseSampler
import com.studio3dpro.engine.math.Aabb
import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.scene.Pose
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class SkinningDebugTest {
    @Test
    fun demoBodyDeformsWithinHumanScale() {
        val scene = DemoScene.create()
        val body = scene.objects.first { it.name == "ATLAS Body" }
        val mesh = body.mesh!!
        val skeleton = scene.skeleton!!
        println("raw bounds=${mesh.bounds()}")
        for (time in listOf(0f, 0.25f, 0.5f, 0.75f)) {
            val pose = PoseSampler.sample(scene, time)
            val mats = skeleton.skinMatrices(pose, body.transform.matrix())
            var lo = Vec3(1e9f, 1e9f, 1e9f)
            var hi = Vec3(-1e9f, -1e9f, -1e9f)
            var bad = 0
            for (i in 0 until mesh.vertexCount) {
                var p = Vec3.ZERO
                for (k in 0..3) {
                    val bi = mesh.skin!!.boneIndices[i * 4 + k]
                    val w = mesh.skin!!.weights[i * 4 + k]
                    val transformed = if (bi in mats.indices) mats[bi].transformPoint(mesh.vertices[i]) else mesh.vertices[i]
                    p += transformed * w
                }
                if (p.x.isNaN() || p.y.isNaN() || p.z.isNaN()) bad++
                lo = Vec3(min(lo.x, p.x), min(lo.y, p.y), min(lo.z, p.z))
                hi = Vec3(max(hi.x, p.x), max(hi.y, p.y), max(hi.z, p.z))
            }
            println("time=$time bounds=${Aabb(lo, hi)} nan=$bad")
            assertTrue("NaN deformation", bad == 0)
            assertTrue("Body exploded: $lo .. $hi", (hi - lo).length() < 6f)
        }
    }

    @Test
    fun diagnoseSpikyVertices() {
        val scene = DemoScene.create()
        val body = scene.objects.first { it.name == "ATLAS Body" }
        val mesh = body.mesh!!
        val skeleton = scene.skeleton!!
        val skin = mesh.skin!!
        val pose = PoseSampler.sample(scene, 0f)
        val mats = skeleton.skinMatrices(pose, body.transform.matrix())
        data class Row(val i: Int, val rest: Vec3, val p: Vec3, val weights: String, val maxW: Float)
        val rows = ArrayList<Row>()
        for (i in 0 until mesh.vertexCount) {
            var p = Vec3.ZERO
            var maxW = 0f
            val sb = StringBuilder()
            for (k in 0..3) {
                val bi = skin.boneIndices[i * 4 + k]
                val w = skin.weights[i * 4 + k]
                maxW = max(maxW, w)
                if (w > 0.01f) sb.append("${skeleton.bones[bi].name}:${String.format("%.2f", w)} ")
                val transformed = if (bi in mats.indices) mats[bi].transformPoint(mesh.vertices[i]) else mesh.vertices[i]
                p += transformed * w
            }
            rows.add(Row(i, mesh.vertices[i], p, sb.toString(), maxW))
        }
        val spikes = rows.sortedByDescending { it.p.distanceTo(it.rest) }.take(15)
        for (r in spikes) println("v${r.i} rest=${r.rest} -> ${r.p} maxW=${r.maxW} ${r.weights}")
        val ambiguous = rows.filter { it.maxW < 0.6f }.sortedByDescending { it.p.distanceTo(it.rest) }.take(15)
        for (r in ambiguous) println("AMB v${r.i} rest=${r.rest} -> ${r.p} maxW=${r.maxW} ${r.weights}")

        var worstRatio = 0f
        var worstTri = ""
        var stretched = 0
        var triangles = 0
        for (face in mesh.faces) {
            if (face.size < 3) continue
            for (t in 1 until face.size - 1) {
                val ia = face[0]; val ib = face[t]; val ic = face[t + 1]
                val ids = intArrayOf(ia, ib, ic)
                for (e in 0..2) {
                    val a = ids[e]; val b = ids[(e + 1) % 3]
                    val restLen = rows[a].rest.distanceTo(rows[b].rest)
                    val posedLen = rows[a].p.distanceTo(rows[b].p)
                    if (restLen < 1e-4f) continue
                    val ratio = posedLen / restLen
                    triangles++
                    if (ratio > 1.6f) stretched++
                    if (ratio > worstRatio) {
                        worstRatio = ratio
                        worstTri = "v$a(${rows[a].weights}) -> v$b(${rows[b].weights}) rest=$restLen posed=$posedLen ratio=$ratio"
                    }
                }
            }
        }
        println("STRETCHED edges=$stretched of $triangles worst=$worstRatio")
        println("WORST $worstTri")
    }
}
