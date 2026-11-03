package com.studio3dpro.engine.mesh

import com.studio3dpro.engine.math.Aabb
import com.studio3dpro.engine.math.Quat
import com.studio3dpro.engine.math.Vec3
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Signed-distance primitives used to model organic shapes without seams.
 * The surface is meshed once by [ImplicitSurface], so limbs and torso blend
 * into a single watertight skin instead of interpenetrating shells.
 */
sealed class SdfShape {
    // Scalar evaluation: the mesher samples hundreds of thousands of points,
    // so this path must not allocate.
    abstract fun distance(x: Float, y: Float, z: Float): Float
    abstract fun bounds(margin: Float): Aabb
}

class SdfSphere(val center: Vec3, val radius: Float) : SdfShape() {
    override fun distance(x: Float, y: Float, z: Float): Float {
        val dx = x - center.x
        val dy = y - center.y
        val dz = z - center.z
        return sqrt(dx * dx + dy * dy + dz * dz) - radius
    }
    override fun bounds(margin: Float): Aabb {
        val r = radius + margin
        return Aabb(center - Vec3(r, r, r), center + Vec3(r, r, r))
    }
}

/** Ellipsoid with optional rotation; the SDF is the standard first-order approximation. */
class SdfEllipsoid(
    val center: Vec3,
    val radii: Vec3,
    val rotation: Quat? = null
) : SdfShape() {
    private val inv = rotation?.let { it.conjugate() }

    override fun distance(x: Float, y: Float, z: Float): Float {
        var lx = x - center.x
        var ly = y - center.y
        var lz = z - center.z
        val q = inv
        if (q != null) {
            val qx = q.x; val qy = q.y; val qz = q.z; val qw = q.w
            val tx = 2f * (qy * lz - qz * ly)
            val ty = 2f * (qz * lx - qx * lz)
            val tz = 2f * (qx * ly - qy * lx)
            val rx = lx + qw * tx + (qy * tz - qz * ty)
            val ry = ly + qw * ty + (qz * tx - qx * tz)
            val rz = lz + qw * tz + (qx * ty - qy * tx)
            lx = rx; ly = ry; lz = rz
        }
        val ax = lx / radii.x
        val ay = ly / radii.y
        val az = lz / radii.z
        val k0 = sqrt(ax * ax + ay * ay + az * az)
        if (k0 < 1e-6f) return -min(radii.x, min(radii.y, radii.z))
        val rx = radii.x * radii.x
        val ry = radii.y * radii.y
        val rz = radii.z * radii.z
        val k1 = sqrt(
            (lx * lx) / (rx * rx) +
                (ly * ly) / (ry * ry) +
                (lz * lz) / (rz * rz)
        )
        return k0 * (k0 - 1f) / max(k1, 1e-6f)
    }

    override fun bounds(margin: Float): Aabb {
        val r = max(radii.x, max(radii.y, radii.z)) + margin
        return Aabb(center - Vec3(r, r, r), center + Vec3(r, r, r))
    }
}

/** Tapered capsule between two joints. Radius varies linearly along the segment. */
class SdfCapsule(
    val a: Vec3,
    val b: Vec3,
    val radiusA: Float,
    val radiusB: Float
) : SdfShape() {
    private val ab = b - a
    private val len2 = max(ab.lengthSq(), 1e-12f)

    override fun distance(x: Float, y: Float, z: Float): Float {
        val apx = x - a.x
        val apy = y - a.y
        val apz = z - a.z
        val t = ((apx * ab.x + apy * ab.y + apz * ab.z) / len2).coerceIn(0f, 1f)
        val dx = apx - ab.x * t
        val dy = apy - ab.y * t
        val dz = apz - ab.z * t
        return sqrt(dx * dx + dy * dy + dz * dz) - (radiusA + (radiusB - radiusA) * t)
    }

    override fun bounds(margin: Float): Aabb {
        val r = max(radiusA, radiusB) + margin
        return Aabb(a.componentMin(b) - Vec3(r, r, r), a.componentMax(b) + Vec3(r, r, r))
    }
}

/**
 * Naive surface nets: samples a signed distance field on a regular grid and
 * emits one vertex per sign-changing cell, stitched into quads. Cheaper than
 * marching cubes and naturally relaxed, which suits character blockouts.
 */
object ImplicitSurface {

    fun mesh(bounds: Aabb, cellSize: Float, shapes: List<SdfShape>, blend: Float = 0.04f): Mesh {
        val pad = cellSize * 2f + blend
        val min = bounds.min - Vec3(pad, pad, pad)
        val max = bounds.max + Vec3(pad, pad, pad)
        val nx = ceil((max.x - min.x) / cellSize).toInt() + 1
        val ny = ceil((max.y - min.y) / cellSize).toInt() + 1
        val nz = ceil((max.z - min.z) / cellSize).toInt() + 1

        val field = FloatArray(nx * ny * nz) { 1f }
        fun idx(x: Int, y: Int, z: Int) = (z * ny + y) * nx + x
        fun gridPoint(x: Int, y: Int, z: Int) =
            Vec3(min.x + x * cellSize, min.y + y * cellSize, min.z + z * cellSize)

        // Rasterise each primitive only inside its expanded bounds, smooth-min
        // into the field. This is far cheaper than evaluating every shape at
        // every sample and keeps the blend local.
        for (shape in shapes) {
            val b = shape.bounds(blend)
            val x0 = max(0, ((b.min.x - min.x) / cellSize).toInt())
            val y0 = max(0, ((b.min.y - min.y) / cellSize).toInt())
            val z0 = max(0, ((b.min.z - min.z) / cellSize).toInt())
            val x1 = min(nx - 1, ceil((b.max.x - min.x) / cellSize).toInt())
            val y1 = min(ny - 1, ceil((b.max.y - min.y) / cellSize).toInt())
            val z1 = min(nz - 1, ceil((b.max.z - min.z) / cellSize).toInt())
            for (z in z0..z1) {
                for (y in y0..y1) {
                    for (x in x0..x1) {
                        val d = shape.distance(
                            min.x + x * cellSize,
                            min.y + y * cellSize,
                            min.z + z * cellSize
                        )
                        val i = idx(x, y, z)
                        field[i] = smoothMin(field[i], d, blend)
                    }
                }
            }
        }

        val mesh = Mesh()
        val cellsX = nx - 1
        val cellsY = ny - 1
        val cellsZ = nz - 1
        val cellVert = IntArray(cellsX * cellsY * cellsZ) { -1 }
        fun cellIndex(x: Int, y: Int, z: Int) = (z * cellsY + y) * cellsX + x

        val cornerX = intArrayOf(0, 1, 1, 0, 0, 1, 1, 0)
        val cornerY = intArrayOf(0, 0, 1, 1, 0, 0, 1, 1)
        val cornerZ = intArrayOf(0, 0, 0, 0, 1, 1, 1, 1)
        val edges = intArrayOf(
            0, 1, 1, 2, 2, 3, 3, 0,
            4, 5, 5, 6, 6, 7, 7, 4,
            0, 4, 1, 5, 2, 6, 3, 7
        )
        val values = FloatArray(8)
        val points = arrayOfNulls<Vec3>(8)

        for (z in 0 until cellsZ) {
            for (y in 0 until cellsY) {
                for (x in 0 until cellsX) {
                    var mask = 0
                    for (c in 0..7) {
                        val v = field[idx(x + cornerX[c], y + cornerY[c], z + cornerZ[c])]
                        values[c] = v
                        if (v < 0f) mask = mask or (1 shl c)
                    }
                    if (mask == 0 || mask == 255) continue
                    for (c in 0..7) {
                        points[c] = Vec3(
                            min.x + (x + cornerX[c]) * cellSize,
                            min.y + (y + cornerY[c]) * cellSize,
                            min.z + (z + cornerZ[c]) * cellSize
                        )
                    }
                    var sum = Vec3.ZERO
                    var count = 0
                    var e = 0
                    while (e < edges.size) {
                        val a = edges[e]
                        val b = edges[e + 1]
                        val va = values[a]
                        val vb = values[b]
                        if ((va < 0f) != (vb < 0f)) {
                            val denom = va - vb
                            val t = if (abs(denom) < 1e-9f) 0.5f else va / denom
                            sum += points[a]!!.lerp(points[b]!!, t.coerceIn(0f, 1f))
                            count++
                        }
                        e += 2
                    }
                    if (count == 0) continue
                    cellVert[cellIndex(x, y, z)] = mesh.addVertex(sum / count.toFloat())
                }
            }
        }

        fun vertexAt(x: Int, y: Int, z: Int): Int {
            if (x < 0 || y < 0 || z < 0 || x >= cellsX || y >= cellsY || z >= cellsZ) return -1
            return cellVert[cellIndex(x, y, z)]
        }

        // One quad per sign-changing grid edge, joining the four cells that
        // share it. Orientation follows the field gradient.
        for (z in 0 until nz) {
            for (y in 0 until ny) {
                for (x in 0 until nx) {
                    val v0 = field[idx(x, y, z)]
                    if (x < nx - 1 && y >= 1 && z >= 1) {
                        val v1 = field[idx(x + 1, y, z)]
                        if ((v0 < 0f) != (v1 < 0f)) {
                            val a = vertexAt(x, y - 1, z - 1)
                            val b = vertexAt(x, y, z - 1)
                            val c = vertexAt(x, y, z)
                            val d = vertexAt(x, y - 1, z)
                            if (a >= 0 && b >= 0 && c >= 0 && d >= 0) {
                                if (v0 < 0f) mesh.addFace(intArrayOf(a, b, c, d))
                                else mesh.addFace(intArrayOf(d, c, b, a))
                            }
                        }
                    }
                    if (y < ny - 1 && x >= 1 && z >= 1) {
                        val v1 = field[idx(x, y + 1, z)]
                        if ((v0 < 0f) != (v1 < 0f)) {
                            val a = vertexAt(x - 1, y, z - 1)
                            val b = vertexAt(x, y, z - 1)
                            val c = vertexAt(x, y, z)
                            val d = vertexAt(x - 1, y, z)
                            if (a >= 0 && b >= 0 && c >= 0 && d >= 0) {
                                // +y edges need the reverse of the +x order.
                                if (v0 < 0f) mesh.addFace(intArrayOf(d, c, b, a))
                                else mesh.addFace(intArrayOf(a, b, c, d))
                            }
                        }
                    }
                    if (z < nz - 1 && x >= 1 && y >= 1) {
                        val v1 = field[idx(x, y, z + 1)]
                        if ((v0 < 0f) != (v1 < 0f)) {
                            val a = vertexAt(x - 1, y - 1, z)
                            val b = vertexAt(x, y - 1, z)
                            val c = vertexAt(x, y, z)
                            val d = vertexAt(x - 1, y, z)
                            if (a >= 0 && b >= 0 && c >= 0 && d >= 0) {
                                if (v0 < 0f) mesh.addFace(intArrayOf(a, b, c, d))
                                else mesh.addFace(intArrayOf(d, c, b, a))
                            }
                        }
                    }
                }
            }
        }

        MeshOps.ensureOutward(mesh)
        MeshOps.laplacianSmooth(mesh, iterations = 2, strength = 0.42f)
        return mesh
    }

    private fun smoothMin(a: Float, b: Float, k: Float): Float {
        if (k <= 0f) return min(a, b)
        val h = (0.5f + 0.5f * (b - a) / k).coerceIn(0f, 1f)
        return b * (1f - h) + a * h - k * h * (1f - h)
    }
}
