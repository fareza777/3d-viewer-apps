package com.studio3dpro.engine.mesh

import com.studio3dpro.engine.math.Mat4
import com.studio3dpro.engine.math.Vec2
import com.studio3dpro.engine.math.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object Primitives {

    private fun quad(mesh: Mesh, a: Int, b: Int, c: Int, d: Int) {
        mesh.addFace(
            intArrayOf(a, b, c, d),
            floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
        )
    }

    fun cube(size: Float = 1f): Mesh {
        val mesh = Mesh()
        val h = size * 0.5f
        // Ordered corners: index bits are X=1, Y=2, Z=4 but interleaved
        // so the face loops below stay readable and consistently wound.
        mesh.addVertex(Vec3(-h, -h, -h)) // 0
        mesh.addVertex(Vec3(h, -h, -h))  // 1
        mesh.addVertex(Vec3(-h, h, -h))  // 2
        mesh.addVertex(Vec3(h, h, -h))   // 3
        mesh.addVertex(Vec3(-h, -h, h))  // 4
        mesh.addVertex(Vec3(h, -h, h))   // 5
        mesh.addVertex(Vec3(-h, h, h))   // 6
        mesh.addVertex(Vec3(h, h, h))    // 7
        mesh.addFace(intArrayOf(4, 5, 7, 6), floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f))
        mesh.addFace(intArrayOf(1, 0, 2, 3), floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f))
        mesh.addFace(intArrayOf(5, 1, 3, 7), floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f))
        mesh.addFace(intArrayOf(0, 4, 6, 2), floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f))
        mesh.addFace(intArrayOf(6, 7, 3, 2), floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f))
        mesh.addFace(intArrayOf(0, 1, 5, 4), floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f))
        mesh.markDirty()
        return mesh
    }

    fun plane(size: Float = 10f, subdivisions: Int = 1): Mesh {
        val mesh = Mesh()
        val n = subdivisions.coerceAtLeast(1)
        val step = size / n
        for (z in 0..n) for (x in 0..n) {
            mesh.addVertex(Vec3(-size / 2 + x * step, 0f, -size / 2 + z * step))
        }
        for (z in 0 until n) for (x in 0 until n) {
            val i0 = z * (n + 1) + x
            val i1 = i0 + 1
            val i2 = i0 + (n + 1) + 1
            val i3 = i0 + (n + 1)
            val u0 = x.toFloat() / n
            val v0 = z.toFloat() / n
            val u1 = (x + 1).toFloat() / n
            val v1 = (z + 1).toFloat() / n
            mesh.addFace(
                intArrayOf(i0, i1, i2, i3),
                floatArrayOf(u0, v0, u1, v0, u1, v1, u0, v1)
            )
        }
        mesh.markDirty()
        return mesh
    }

    fun uvSphere(radius: Float = 0.5f, segments: Int = 32, rings: Int = 16): Mesh {
        require(segments >= 3 && rings >= 2)
        val mesh = Mesh()
        val top = mesh.addVertex(Vec3(0f, radius, 0f))
        val ringStart = IntArray(rings - 1)
        for (r in 1 until rings) {
            ringStart[r - 1] = mesh.vertices.size
            val phi = PI.toFloat() * r / rings
            val y = cos(phi) * radius
            val rad = sin(phi) * radius
            for (s in 0 until segments) {
                val theta = 2f * PI.toFloat() * s / segments
                mesh.addVertex(Vec3(rad * cos(theta), y, rad * sin(theta)))
            }
        }
        val bottom = mesh.addVertex(Vec3(0f, -radius, 0f))
        for (s in 0 until segments) {
            val s2 = (s + 1) % segments
            val u0 = s.toFloat() / segments
            val u1 = (s + 1).toFloat() / segments
            mesh.addFace(
                intArrayOf(top, ringStart[0] + s, ringStart[0] + s2),
                floatArrayOf((u0 + u1) * 0.5f, 1f, u1, 1f - 1f / rings, u0, 1f - 1f / rings)
            )
        }
        for (r in 0 until rings - 2) {
            val v0 = 1f - r.toFloat() / rings
            val v1 = 1f - (r + 1).toFloat() / rings
            for (s in 0 until segments) {
                val s2 = (s + 1) % segments
                val a = ringStart[r] + s
                val b = ringStart[r] + s2
                val c = ringStart[r + 1] + s2
                val d = ringStart[r + 1] + s
                val u0 = s.toFloat() / segments
                val u1 = (s + 1).toFloat() / segments
                mesh.addFace(
                    intArrayOf(a, b, c, d),
                    floatArrayOf(u0, v0, u1, v0, u1, v1, u0, v1)
                )
            }
        }
        val last = ringStart[rings - 2]
        for (s in 0 until segments) {
            val s2 = (s + 1) % segments
            val u0 = s.toFloat() / segments
            mesh.addFace(
                intArrayOf(last + s, bottom, last + s2),
                floatArrayOf(u0, 1f / rings, 0.5f, 0f, (s + 1f) / segments, 1f / rings)
            )
        }
        mesh.markDirty()
        MeshOps.ensureOutward(mesh)
        return mesh
    }

    fun cylinder(
        radiusBottom: Float = 0.5f,
        radiusTop: Float = 0.5f,
        height: Float = 1f,
        segments: Int = 32,
        capBottom: Boolean = true,
        capTop: Boolean = true,
        startAngle: Float = 0f
    ): Mesh {
        require(segments >= 3)
        val mesh = Mesh()
        val half = height * 0.5f
        val bStart = mesh.vertices.size
        for (s in 0 until segments) {
            val a = startAngle + 2f * PI.toFloat() * s / segments
            mesh.addVertex(Vec3(radiusBottom * cos(a), -half, radiusBottom * sin(a)))
        }
        val tStart = mesh.vertices.size
        for (s in 0 until segments) {
            val a = startAngle + 2f * PI.toFloat() * s / segments
            mesh.addVertex(Vec3(radiusTop * cos(a), half, radiusTop * sin(a)))
        }
        for (s in 0 until segments) {
            val s2 = (s + 1) % segments
            val u0 = s.toFloat() / segments
            val u1 = (s + 1).toFloat() / segments
            mesh.addFace(
                intArrayOf(bStart + s, tStart + s, tStart + s2, bStart + s2),
                floatArrayOf(u0, 0f, u0, 1f, u1, 1f, u1, 0f)
            )
        }
        if (capTop) {
            val center = mesh.addVertex(Vec3(0f, half, 0f))
            val rim = IntArray(segments) { tStart + it }
            for (s in 0 until segments) {
                val s2 = (s + 1) % segments
                mesh.addFace(
                    intArrayOf(center, rim[s2], rim[s]),
                    floatArrayOf(0.5f, 0.5f, 0.5f + 0.5f * cos(2f * PI.toFloat() * (s + 1) / segments), 0.5f + 0.5f * sin(2f * PI.toFloat() * (s + 1) / segments), 0.5f + 0.5f * cos(2f * PI.toFloat() * s / segments), 0.5f + 0.5f * sin(2f * PI.toFloat() * s / segments))
                )
            }
        }
        if (capBottom) {
            val center = mesh.addVertex(Vec3(0f, -half, 0f))
            val rim = IntArray(segments) { bStart + it }
            for (s in 0 until segments) {
                val s2 = (s + 1) % segments
                mesh.addFace(
                    intArrayOf(center, rim[s], rim[s2]),
                    floatArrayOf(0.5f, 0.5f, 0.5f + 0.5f * cos(2f * PI.toFloat() * s / segments), 0.5f + 0.5f * sin(2f * PI.toFloat() * s / segments), 0.5f + 0.5f * cos(2f * PI.toFloat() * (s + 1) / segments), 0.5f + 0.5f * sin(2f * PI.toFloat() * (s + 1) / segments))
                )
            }
        }
        mesh.markDirty()
        MeshOps.ensureOutward(mesh)
        return mesh
    }

    fun cone(radius: Float = 0.5f, height: Float = 1f, segments: Int = 32): Mesh {
        require(segments >= 3)
        val mesh = Mesh()
        val half = height * 0.5f
        val bStart = mesh.vertices.size
        for (s in 0 until segments) {
            val a = 2f * PI.toFloat() * s / segments
            mesh.addVertex(Vec3(radius * cos(a), -half, radius * sin(a)))
        }
        val apex = mesh.addVertex(Vec3(0f, half, 0f))
        for (s in 0 until segments) {
            val s2 = (s + 1) % segments
            val u0 = s.toFloat() / segments
            val u1 = (s + 1).toFloat() / segments
            mesh.addFace(
                intArrayOf(bStart + s, apex, bStart + s2),
                floatArrayOf(u0, 0f, (u0 + u1) * 0.5f, 1f, u1, 0f)
            )
        }
        val center = mesh.addVertex(Vec3(0f, -half, 0f))
        for (s in 0 until segments) {
            val s2 = (s + 1) % segments
            mesh.addFace(intArrayOf(center, bStart + s, bStart + s2), floatArrayOf(0.5f, 0.5f, 0.5f, 0f, 1f, 0f))
        }
        mesh.markDirty()
        MeshOps.ensureOutward(mesh)
        return mesh
    }

    fun torus(majorRadius: Float = 0.5f, minorRadius: Float = 0.18f, majorSegments: Int = 32, minorSegments: Int = 16): Mesh {
        require(majorSegments >= 3 && minorSegments >= 3)
        val mesh = Mesh()
        for (u in 0 until majorSegments) {
            val a = 2f * PI.toFloat() * u / majorSegments
            for (v in 0 until minorSegments) {
                val b = 2f * PI.toFloat() * v / minorSegments
                val r = majorRadius + minorRadius * cos(b)
                mesh.addVertex(Vec3(r * cos(a), minorRadius * sin(b), r * sin(a)))
            }
        }
        for (u in 0 until majorSegments) {
            for (v in 0 until minorSegments) {
                val u2 = (u + 1) % majorSegments
                val v2 = (v + 1) % minorSegments
                val a = u * minorSegments + v
                val b = u * minorSegments + v2
                val c = u2 * minorSegments + v2
                val d = u2 * minorSegments + v
                mesh.addFace(
                    intArrayOf(a, b, c, d),
                    floatArrayOf(u.toFloat() / majorSegments, v.toFloat() / minorSegments,
                        u.toFloat() / majorSegments, (v + 1f) / minorSegments,
                        (u + 1f) / majorSegments, (v + 1f) / minorSegments,
                        (u + 1f) / majorSegments, v.toFloat() / minorSegments)
                )
            }
        }
        mesh.markDirty()
        MeshOps.ensureOutward(mesh)
        return mesh
    }

    /** Rounded box built by scaling a subdivided cube outward. */
    fun roundedCube(size: Float = 1f, radius: Float = 0.08f, segments: Int = 4): Mesh {
        val source = uvSphere(radius = 1f, segments = 32, rings = 16)
        val half = size * 0.5f - radius
        val mesh = Mesh()
        for (v in source.vertices) {
            val s = Vec3(
                if (v.x > 0) half else -half,
                if (v.y > 0) half else -half,
                if (v.z > 0) half else -half
            )
            mesh.addVertex(v * radius + s)
        }
        for (f in source.faces.indices) {
            mesh.faces.add(source.faces[f].copyOf())
            mesh.faceUvs.add(source.faceUvs[f].copyOf())
        }
        mesh.markDirty()
        MeshOps.ensureOutward(mesh)
        return mesh
    }

    fun transform(mesh: Mesh, matrix: Mat4): Mesh {
        val copy = mesh.clone()
        copy.transform(matrix)
        return copy
    }

    fun ellipseUv(centerU: Float, centerV: Float, radiusU: Float, radiusV: Float, angle: Float): Vec2 =
        Vec2(centerU + cos(angle) * radiusU, centerV + sin(angle) * radiusV)
}
