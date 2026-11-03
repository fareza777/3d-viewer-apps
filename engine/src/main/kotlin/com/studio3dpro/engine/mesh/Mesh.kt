package com.studio3dpro.engine.mesh

import com.studio3dpro.engine.math.Aabb
import com.studio3dpro.engine.math.Mat4
import com.studio3dpro.engine.math.Ray
import com.studio3dpro.engine.math.Vec2
import com.studio3dpro.engine.math.Vec3
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** GPU skin weights, 4 influences per vertex. */
class SkinWeights(
    val boneIndices: IntArray,
    val weights: FloatArray
) {
    init {
        require(boneIndices.size == weights.size) { "Skin arrays must match" }
        require(boneIndices.size % 4 == 0) { "Skin data must be 4 influences per vertex" }
    }

    val vertexCount: Int get() = boneIndices.size / 4

    fun copy(): SkinWeights = SkinWeights(boneIndices.copyOf(), weights.copyOf())

    fun normalizedVertex(v: Int): FloatArray {
        val out = FloatArray(8)
        var sum = 0f
        for (i in 0..3) {
            val w = weights[v * 4 + i]
            out[4 + i] = w
            sum += w
        }
        if (sum > 1e-8f) for (i in 0..3) out[4 + i] /= sum
        for (i in 0..3) out[i] = boneIndices[v * 4 + i].toFloat()
        return out
    }
}

data class RayHit(
    val triangle: Int,
    val face: Int,
    val distance: Float,
    val point: Vec3,
    val normal: Vec3
)

data class EdgeInfo(
    val a: Int,
    val b: Int,
    val faces: MutableList<Int> = mutableListOf()
) {
    val key: Long get() = edgeKey(a, b)
}

fun edgeKey(a: Int, b: Int): Long {
    val lo = min(a, b).toLong() and 0xffffffffL
    val hi = max(a, b).toLong() and 0xffffffffL
    return (lo shl 32) or hi
}

fun edgeEndpoints(key: Long): Pair<Int, Int> =
    ((key ushr 32).toInt()) to (key and 0xffffffffL).toInt()

/**
 * Polygon mesh with per-face-corner UVs. UV seams are represented by
 * per-corner data so editing never needs to split vertices.
 */
class Mesh {
    val vertices = ArrayList<Vec3>()
    val faces = ArrayList<IntArray>()
    val faceUvs = ArrayList<FloatArray>()
    var skin: SkinWeights? = null

    var version: Long = 0L
        private set

    private var cachedTriangles: IntArray? = null
    private var cachedTriangleFaces: IntArray? = null
    private var cachedVertexNormals: FloatArray? = null
    private var cachedCornerNormals: FloatArray? = null
    private var cachedEdges: Map<Long, EdgeInfo>? = null

    fun markDirty() {
        version++
        cachedTriangles = null
        cachedTriangleFaces = null
        cachedVertexNormals = null
        cachedCornerNormals = null
        cachedEdges = null
    }

    /**
     * Per-corner normals split by a smoothing angle. This keeps sphere/cylinder
     * sides smooth while cube and cap corners stay hard, with shared topology.
     */
    fun cornerNormals(smoothingAngleDeg: Float = 60f): FloatArray {
        cachedCornerNormals?.let { return it }
        val faceNormals = Array(faces.size) { faceNormal(it) }
        val total = totalCorners()
        val out = FloatArray(total * 3)
        val cosLimit = kotlin.math.cos(smoothingAngleDeg * Math.PI.toFloat() / 180f)
        val vertexFaces = vertexFaces()
        var offset = 0
        for (f in faces.indices) {
            val fn = faceNormals[f]
            val idx = faces[f]
            for (corner in idx.indices) {
                val v = idx[corner]
                var nx = 0f; var ny = 0f; var nz = 0f
                for (nf in vertexFaces[v]) {
                    val other = faceNormals[nf]
                    if ((other dot fn) >= cosLimit) {
                        val area = faceArea(nf)
                        nx += other.x * area; ny += other.y * area; nz += other.z * area
                    }
                }
                val len = sqrt(nx * nx + ny * ny + nz * nz)
                if (len > 1e-8f) {
                    out[offset * 3] = nx / len; out[offset * 3 + 1] = ny / len; out[offset * 3 + 2] = nz / len
                } else {
                    out[offset * 3] = fn.x; out[offset * 3 + 1] = fn.y; out[offset * 3 + 2] = fn.z
                }
                offset++
            }
        }
        cachedCornerNormals = out
        return out
    }

    val vertexCount: Int get() = vertices.size
    val faceCount: Int get() = faces.size

    fun clear() {
        vertices.clear(); faces.clear(); faceUvs.clear(); skin = null
        markDirty()
    }

    fun addVertex(p: Vec3): Int {
        vertices.add(p)
        return vertices.size - 1
    }

    fun addFace(indices: IntArray, uvs: FloatArray? = null) {
        require(indices.size >= 3) { "Face needs at least 3 vertices" }
        for (i in indices) require(i in vertices.indices) { "Face index out of range: $i" }
        faces.add(indices)
        faceUvs.add(uvs?.copyOf() ?: FloatArray(indices.size * 2))
    }

    fun setFaceUv(face: Int, corner: Int, uv: Vec2) {
        val arr = faceUvs[face]
        arr[corner * 2] = uv.x
        arr[corner * 2 + 1] = uv.y
    }

    fun uvAt(face: Int, corner: Int): Vec2 {
        val arr = faceUvs[face]
        return Vec2(arr[corner * 2], arr[corner * 2 + 1])
    }

    fun faceNormal(face: Int): Vec3 {
        val idx = faces[face]
        var nx = 0f; var ny = 0f; var nz = 0f
        for (i in idx.indices) {
            val cur = vertices[idx[i]]
            val nxt = vertices[idx[(i + 1) % idx.size]]
            nx += (cur.y - nxt.y) * (cur.z + nxt.z)
            ny += (cur.z - nxt.z) * (cur.x + nxt.x)
            nz += (cur.x - nxt.x) * (cur.y + nxt.y)
        }
        val n = Vec3(nx, ny, nz)
        return if (n.lengthSq() < 1e-14f) Vec3.UP else n.normalized()
    }

    fun faceCentroid(face: Int): Vec3 {
        val idx = faces[face]
        var p = Vec3.ZERO
        for (i in idx) p += vertices[i]
        return p / idx.size.toFloat()
    }

    fun faceArea(face: Int): Float {
        val idx = faces[face]
        if (idx.size < 3) return 0f
        val a = vertices[idx[0]]
        var area = 0f
        for (i in 1 until idx.size - 1) {
            area += ((vertices[idx[i]] - a) cross (vertices[idx[i + 1]] - a)).length() * 0.5f
        }
        return area
    }

    fun bounds(): Aabb {
        if (vertices.isEmpty()) return Aabb.EMPTY
        var mn = vertices[0]
        var mx = vertices[0]
        for (i in 1 until vertices.size) {
            mn = mn.componentMin(vertices[i])
            mx = mx.componentMax(vertices[i])
        }
        return Aabb(mn, mx)
    }

    fun triangles(): IntArray {
        cachedTriangles?.let { return it }
        val out = IntArray(faces.sumOf { max(0, it.size - 2) } * 3)
        var o = 0
        for (f in faces.indices) {
            val idx = faces[f]
            for (i in 1 until idx.size - 1) {
                out[o++] = idx[0]
                out[o++] = idx[i]
                out[o++] = idx[i + 1]
            }
        }
        cachedTriangles = out
        return out
    }

    fun triangleFaces(): IntArray {
        cachedTriangleFaces?.let { return it }
        val out = IntArray(triangles().size / 3)
        var o = 0
        for (f in faces.indices) {
            repeat(max(0, faces[f].size - 2)) { out[o++] = f }
        }
        cachedTriangleFaces = out
        return out
    }

    fun vertexNormals(): FloatArray {
        cachedVertexNormals?.let { return it }
        val n = FloatArray(vertices.size * 3)
        val tri = triangles()
        var t = 0
        while (t < tri.size) {
            val ia = tri[t]; val ib = tri[t + 1]; val ic = tri[t + 2]
            val a = vertices[ia]; val b = vertices[ib]; val c = vertices[ic]
            val fn = (b - a) cross (c - a)
            n[ia * 3] += fn.x; n[ia * 3 + 1] += fn.y; n[ia * 3 + 2] += fn.z
            n[ib * 3] += fn.x; n[ib * 3 + 1] += fn.y; n[ib * 3 + 2] += fn.z
            n[ic * 3] += fn.x; n[ic * 3 + 1] += fn.y; n[ic * 3 + 2] += fn.z
            t += 3
        }
        for (i in vertices.indices) {
            val x = n[i * 3]; val y = n[i * 3 + 1]; val z = n[i * 3 + 2]
            val len = sqrt(x * x + y * y + z * z)
            if (len > 1e-8f) {
                n[i * 3] = x / len; n[i * 3 + 1] = y / len; n[i * 3 + 2] = z / len
            } else {
                n[i * 3] = 0f; n[i * 3 + 1] = 1f; n[i * 3 + 2] = 0f
            }
        }
        cachedVertexNormals = n
        return n
    }

    fun edges(): Map<Long, EdgeInfo> {
        cachedEdges?.let { return it }
        val map = LinkedHashMap<Long, EdgeInfo>()
        for (f in faces.indices) {
            val idx = faces[f]
            for (i in idx.indices) {
                val a = idx[i]
                val b = idx[(i + 1) % idx.size]
                if (a == b) continue
                val key = edgeKey(a, b)
                val e = map.getOrPut(key) { EdgeInfo(min(a, b), max(a, b)) }
                if (f !in e.faces) e.faces.add(f)
            }
        }
        cachedEdges = map
        return map
    }

    fun vertexFaces(): List<MutableList<Int>> {
        val out = List(vertices.size) { mutableListOf<Int>() }
        for (f in faces.indices) for (v in faces[f]) if (v in out.indices) out[v].add(f)
        return out
    }

    fun vertexNeighbors(): List<MutableSet<Int>> {
        val out = List(vertices.size) { mutableSetOf<Int>() }
        for (f in faces) {
            for (i in f.indices) {
                val a = f[i]
                val b = f[(i + 1) % f.size]
                if (a in out.indices && b in out.indices) {
                    out[a].add(b); out[b].add(a)
                }
            }
        }
        return out
    }

    fun unsignedVolume(): Float {
        val tri = triangles()
        var vol = 0f
        var i = 0
        while (i < tri.size) {
            val a = vertices[tri[i]]
            val b = vertices[tri[i + 1]]
            val c = vertices[tri[i + 2]]
            vol += (a dot (b cross c)) / 6f
            i += 3
        }
        return abs(vol)
    }

    fun transform(mat: Mat4) {
        for (i in vertices.indices) vertices[i] = mat.transformPoint(vertices[i])
        markDirty()
    }

    fun translate(offset: Vec3) {
        for (i in vertices.indices) vertices[i] += offset
        markDirty()
    }

    fun clone(): Mesh {
        val m = Mesh()
        m.vertices.addAll(vertices)
        m.faces.addAll(faces.map { it.copyOf() })
        m.faceUvs.addAll(faceUvs.map { it.copyOf() })
        m.skin = skin?.copy()
        m.markDirty()
        return m
    }

    fun append(other: Mesh, transform: Mat4 = Mat4.identity(), includeSkin: Boolean = true) {
        val offset = vertices.size
        for (v in other.vertices) vertices.add(transform.transformPoint(v))
        val mirror = (transform[0] * transform[5] * transform[10] +
            transform[4] * transform[9] * transform[2] +
            transform[8] * transform[1] * transform[6] -
            transform[8] * transform[5] * transform[2] -
            transform[4] * transform[1] * transform[10] -
            transform[0] * transform[9] * transform[6]) < 0f
        for (f in other.faces.indices) {
            val idx = other.faces[f].map { it + offset }.toIntArray()
            if (mirror) idx.reverse()
            faces.add(idx)
            faceUvs.add(other.faceUvs[f].copyOf())
        }
        if (includeSkin && other.skin != null) {
            val os = other.skin ?: return
            val total = vertices.size
            val newBones = IntArray(total * 4)
            val newWeights = FloatArray(total * 4)
            val existing = skin
            if (existing != null) {
                existing.boneIndices.copyInto(newBones)
                existing.weights.copyInto(newWeights)
            } else {
                // Previously unskinned geometry is bound rigidly to the root.
                for (v in 0 until offset) newWeights[v * 4] = 1f
            }
            for (v in 0 until other.vertices.size) {
                val r = os.normalizedVertex(v)
                val dst = offset + v
                for (k in 0..3) {
                    newBones[dst * 4 + k] = r[k].toInt()
                    newWeights[dst * 4 + k] = r[4 + k]
                }
            }
            skin = SkinWeights(newBones, newWeights)
        }
        markDirty()
    }

    fun weld(epsilon: Float = 1e-4f): Int {
        if (vertices.isEmpty()) return 0
        data class Key(val x: Int, val y: Int, val z: Int)
        val cell = max(epsilon, 1e-6f)
        val map = HashMap<Key, Int>(vertices.size)
        val remap = IntArray(vertices.size)
        val newVerts = ArrayList<Vec3>()
        val buckets = HashMap<Key, MutableList<Int>>()
        for (i in vertices.indices) {
            val p = vertices[i]
            val bx = floor(p.x / cell).toInt()
            val by = floor(p.y / cell).toInt()
            val bz = floor(p.z / cell).toInt()
            var found = -1
            outer@ for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
                val list = buckets[Key(bx + dx, by + dy, bz + dz)] ?: continue
                for (candidate in list) {
                    if (newVerts[candidate].distanceTo(p) <= epsilon) {
                        found = candidate
                        break@outer
                    }
                }
            }
            if (found < 0) {
                found = newVerts.size
                newVerts.add(p)
                buckets.getOrPut(Key(bx, by, bz)) { mutableListOf() }.add(found)
            }
            remap[i] = found
        }
        val newFaces = ArrayList<IntArray>()
        val newUvs = ArrayList<FloatArray>()
        for (f in faces.indices) {
            val raw = faces[f]
            val idx = ArrayList<Int>()
            val uv = ArrayList<Float>()
            for (c in raw.indices) {
                val r = remap[raw[c]]
                if (idx.isEmpty() || idx.last() != r) {
                    idx.add(r)
                    uv.add(faceUvs[f][c * 2]); uv.add(faceUvs[f][c * 2 + 1])
                }
            }
            if (idx.size > 1 && idx.first() == idx.last()) {
                idx.removeAt(idx.size - 1); uv.removeAt(uv.size - 1); uv.removeAt(uv.size - 1)
            }
            if (idx.size >= 3) {
                newFaces.add(idx.toIntArray())
                newUvs.add(uv.toFloatArray())
            }
        }
        val removed = vertices.size - newVerts.size
        vertices.clear(); vertices.addAll(newVerts)
        faces.clear(); faces.addAll(newFaces)
        faceUvs.clear(); faceUvs.addAll(newUvs)
        if (skin != null) {
            val old = skin!!
            val bones = IntArray(newVerts.size * 4)
            val weights = FloatArray(newVerts.size * 4)
            val assigned = BooleanArray(newVerts.size)
            for (i in 0 until min(remap.size, old.vertexCount)) {
                val dst = remap[i]
                if (assigned[dst]) continue
                assigned[dst] = true
                for (k in 0..3) {
                    bones[dst * 4 + k] = old.boneIndices[i * 4 + k]
                    weights[dst * 4 + k] = old.weights[i * 4 + k]
                }
            }
            skin = SkinWeights(bones, weights)
        }
        markDirty()
        return removed
    }

    fun removeDegenerateFaces(minArea: Float = 1e-10f): Int {
        var removed = 0
        var i = 0
        while (i < faces.size) {
            if (faces[i].distinct().size < 3 || faceArea(i) < minArea) {
                faces.removeAt(i); faceUvs.removeAt(i); removed++
            } else i++
        }
        if (removed > 0) markDirty()
        return removed
    }

    fun raycastLocal(ray: Ray): RayHit? {
        val tri = triangles()
        val tf = triangleFaces()
        var bestT = Float.MAX_VALUE
        var bestTri = -1
        var bestFace = -1
        var ti = 0
        while (ti < tri.size) {
            val t = ray.intersectsTriangle(
                vertices[tri[ti]], vertices[tri[ti + 1]], vertices[tri[ti + 2]]
            )
            if (t != null && t < bestT) {
                bestT = t; bestTri = ti / 3; bestFace = tf[ti / 3]
            }
            ti += 3
        }
        if (bestTri < 0) return null
        return RayHit(bestTri, bestFace, bestT, ray.at(bestT), faceNormal(bestFace))
    }

    fun verticesInRadius(center: Vec3, radius: Float): List<Int> {
        val r2 = radius * radius
        val out = ArrayList<Int>()
        val r = radius
        for (i in vertices.indices) {
            val p = vertices[i]
            if (abs(p.x - center.x) > r || abs(p.y - center.y) > r || abs(p.z - center.z) > r) continue
            if ((p - center).lengthSq() <= r2) out.add(i)
        }
        return out
    }

    fun totalCorners(): Int = faces.sumOf { it.size }

    override fun toString(): String = "Mesh(vertices=${vertices.size}, faces=${faces.size})"
}
