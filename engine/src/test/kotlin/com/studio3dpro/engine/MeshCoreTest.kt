package com.studio3dpro.engine

import com.studio3dpro.engine.math.Mat4
import com.studio3dpro.engine.math.Quat
import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.mesh.BooleanOp
import com.studio3dpro.engine.mesh.Mesh
import com.studio3dpro.engine.mesh.MeshBoolean
import com.studio3dpro.engine.mesh.MeshOps
import com.studio3dpro.engine.mesh.Primitives
import com.studio3dpro.engine.mesh.Sculpt
import com.studio3dpro.engine.mesh.SculptMode
import com.studio3dpro.engine.mesh.UvProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MeshCoreTest {

    @Test
    fun matrixInverseRoundTrips() {
        val m = Mat4.translation(Vec3(1f, 2f, 3f)) *
            Mat4.rotation(Quat.axisAngle(Vec3(0.3f, 1f, 0.2f), 0.7f)) *
            Mat4.scaling(Vec3(2f, 3f, 4f))
        val inv = m.inverse()
        val p = Vec3(0.4f, -1.2f, 3.5f)
        val q = inv.transformPoint(m.transformPoint(p))
        assertTrue((q - p).length() < 1e-4f)
        assertEquals(1f, (m * inv)[0], 1e-4f)
    }

    @Test
    fun quaternionRotatesAndConvertsBack() {
        val q = Quat.fromEulerDegrees(Vec3(20f, 45f, -10f))
        val m = Mat4.fromQuat(q)
        val q2 = Quat.fromMat4(m)
        assertTrue(q.angleTo(q2) < 1e-3f)
        val rotated = q.rotate(Vec3.RIGHT)
        assertEquals(1f, rotated.length(), 1e-4f)
    }

    @Test
    fun cubeTopologyAndVolume() {
        val cube = Primitives.cube(2f)
        assertEquals(8, cube.vertexCount)
        assertEquals(6, cube.faceCount)
        assertEquals(8f, MeshBoolean.volume(cube), 1e-3f)
        assertTrue(cube.edges().values.all { it.faces.size == 2 })
        val cornerNormals = cube.cornerNormals()
        assertEquals(24 * 3, cornerNormals.size)
    }

    @Test
    fun extrudeTopFaceAddsVolume() {
        val cube = Primitives.cube(1f)
        val top = cube.faces.indices.first { cube.faceNormal(it).y > 0.9f }
        val caps = MeshOps.extrudeFaces(cube, setOf(top), 0.5f)
        assertEquals(1, caps.size)
        assertEquals(1.5f, MeshBoolean.volume(cube), 1e-3f)
        assertEquals(1.5f, cube.bounds().size.y, 1e-3f)
        assertEquals(1.0f, cube.bounds().max.y, 1e-3f)
        assertTrue(cube.faceCount > 6)
    }

    @Test
    fun insetCreatesRim() {
        val cube = Primitives.cube(1f)
        val before = cube.faceCount
        MeshOps.insetFaces(cube, setOf(0), 0.25f)
        assertEquals(before + 4, cube.faceCount)
        assertTrue(cube.vertexCount > 8)
        assertTrue(MeshBoolean.volume(cube) in 0.9f..1.1f)
    }

    @Test
    fun catmullClarkProducesQuadsAndSmooths() {
        val cube = Primitives.cube(1f)
        MeshOps.subdivide(cube, smooth = true)
        assertEquals(24, cube.faceCount)
        assertTrue(cube.vertexCount > 8)
        assertTrue(cube.faces.all { it.size == 4 })
        assertTrue(MeshBoolean.volume(cube) in 0.25f..1f)
    }

    @Test
    fun booleanDifferenceVolume() {
        val a = Primitives.cube(2f)
        val b = Primitives.cube(2f)
        b.translate(Vec3(1f, 0f, 0f))
        val diff = MeshBoolean.apply(a, b, BooleanOp.DIFFERENCE)
        assertEquals(4f, MeshBoolean.volume(diff), 0.2f)
        val union = MeshBoolean.apply(Primitives.cube(2f), b, BooleanOp.UNION)
        assertEquals(12f, MeshBoolean.volume(union), 0.3f)
        val inter = MeshBoolean.apply(Primitives.cube(2f), b, BooleanOp.INTERSECTION)
        assertEquals(4f, MeshBoolean.volume(inter), 0.2f)
    }

    @Test
    fun bevelVertexAddsCap() {
        val cube = Primitives.cube(1f)
        MeshOps.bevelVertices(cube, setOf(7), 0.15f)
        assertEquals(7, cube.faceCount)
        assertTrue(cube.vertexCount > 8)
        assertTrue(MeshBoolean.volume(cube) in 0.8f..1f)
    }

    @Test
    fun uvProjectionWithinRange() {
        val sphere = Primitives.uvSphere(1f, 24, 12)
        UvProjection.project(sphere, UvProjection.Mode.BOX)
        for (face in sphere.faces.indices) {
            for (i in 0 until sphere.faces[face].size) {
                val uv = sphere.uvAt(face, i)
                assertTrue(uv.x in -0.001f..1.001f)
                assertTrue(uv.y in -0.001f..1.001f)
            }
        }
    }

    @Test
    fun sculptDrawMovesVertices() {
        val sphere = Primitives.uvSphere(1f, 24, 12)
        val before = sphere.bounds().max.x
        val touched = Sculpt.stroke(
            sphere,
            Vec3(1f, 0f, 0f),
            radius = 0.5f,
            strength = 0.2f,
            mode = SculptMode.DRAW,
            normal = Vec3.RIGHT
        )
        assertTrue(touched > 0)
        assertTrue(sphere.bounds().max.x > before)
    }

    @Test
    fun cylinderIsClosedAndOutward() {
        val cyl = Primitives.cylinder(radiusBottom = 0.5f, radiusTop = 0.5f, height = 2f, segments = 24)
        assertEquals(2 * 24 + 2, cyl.vertexCount)
        assertTrue(MeshBoolean.volume(cyl) > 1.3f)
        assertTrue(cyl.edges().values.all { it.faces.size == 2 })
    }

    @Test
    fun raycastHitsCube() {
        val cube = Primitives.cube(1f)
        val hit = cube.raycastLocal(com.studio3dpro.engine.math.Ray(Vec3(0f, 0f, 5f), Vec3(0f, 0f, -1f)))
        assertNotNull(hit)
        assertEquals(4.5f, hit!!.distance, 1e-3f)
    }

    @Test
    fun mirrorWeldCreatesSymmetricMesh() {
        val half = Primitives.cube(1f)
        half.transform(Mat4.translation(Vec3(0.5f, 0f, 0f)))
        MeshOps.mirror(half, 0)
        assertEquals(12, half.faceCount)
        assertTrue(abs(half.bounds().size.x - 2f) < 1e-3f)
    }
}
