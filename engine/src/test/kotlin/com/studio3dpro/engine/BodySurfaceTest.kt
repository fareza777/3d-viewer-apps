package com.studio3dpro.engine

import com.studio3dpro.engine.mesh.Mesh
import com.studio3dpro.engine.mesh.MeshBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Guards the procedural character skin: one closed, consistently wound shell. */
class BodySurfaceTest {
    @Test
    fun demoBodyIsOneWatertightShell() {
        val start = System.nanoTime()
        val scene = DemoScene.create()
        val createMs = (System.nanoTime() - start) / 1_000_000
        val body = scene.objects.first { it.name == "ATLAS Body" }.mesh!!
        println("body create=${createMs}ms v=${body.vertexCount} f=${body.faceCount}")

        for (v in body.vertices) {
            assertTrue("NaN vertex", !v.x.isNaN() && !v.y.isNaN() && !v.z.isNaN())
        }
        assertTrue("Too few vertices: ${body.vertexCount}", body.vertexCount > 3000)
        assertTrue("Too many vertices: ${body.vertexCount}", body.vertexCount < 40000)
        assertTrue("Signed volume must be positive", MeshBoolean.volume(body) > 0f)

        var boundary = 0
        var nonManifold = 0
        for (e in body.edges().values) {
            when {
                e.faces.size < 2 -> boundary++
                e.faces.size > 2 -> nonManifold++
            }
        }
        assertEquals("Open boundary edges", 0, boundary)
        assertEquals("Non-manifold edges", 0, nonManifold)

        // Every shared edge must be traversed in opposite directions by its
        // two faces, otherwise lighting/normals break.
        val directed = HashMap<Long, Int>()
        var flipped = 0
        for (face in body.faces) {
            for (i in face.indices) {
                val a = face[i]
                val b = face[(i + 1) % face.size]
                val key = (a.toLong() shl 32) or (b.toLong() and 0xffffffffL)
                if (directed.put(key, 1) != null) flipped++
            }
        }
        assertEquals("Faces with inconsistent winding", 0, flipped)

        val comps = components(body)
        assertEquals("Body should be one connected surface", 1, comps)

        val b = body.bounds()
        assertTrue("Implausible bounds $b", abs(b.size.x - 1.06f) < 0.25f)
        assertTrue("Implausible bounds $b", abs(b.size.y - 1.7f) < 0.25f)
    }

    private fun components(mesh: Mesh): Int {
        val nb = mesh.vertexNeighbors()
        val seen = BooleanArray(mesh.vertexCount)
        var count = 0
        for (start in 0 until mesh.vertexCount) {
            if (seen[start]) continue
            count++
            val stack = ArrayDeque<Int>()
            stack.add(start); seen[start] = true
            while (stack.isNotEmpty()) {
                val v = stack.removeLast()
                for (n in nb[v]) if (!seen[n]) { seen[n] = true; stack.add(n) }
            }
        }
        return count
    }
}
