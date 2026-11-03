package com.studio3dpro.engine.mesh

import com.studio3dpro.engine.math.Mat4
import com.studio3dpro.engine.math.Vec2
import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.math.clamp
import com.studio3dpro.engine.math.lerp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object MeshOps {

    fun ensureOutward(mesh: Mesh) {
        if (mesh.faces.isEmpty()) return
        if (mesh.unsignedVolume() <= 0f) return
        // Compute signed volume explicitly to flip inward-facing meshes.
        val tri = mesh.triangles()
        var signed = 0f
        var i = 0
        while (i < tri.size) {
            val a = mesh.vertices[tri[i]]
            val b = mesh.vertices[tri[i + 1]]
            val c = mesh.vertices[tri[i + 2]]
            signed += (a dot (b cross c)) / 6f
            i += 3
        }
        if (signed < 0f) flipNormals(mesh)
    }

    fun flipNormals(mesh: Mesh) {
        for (f in mesh.faces.indices) {
            mesh.faces[f].reverse()
            val uv = mesh.faceUvs[f]
            val n = mesh.faces[f].size
            val copy = uv.copyOf()
            for (c in 0 until n) {
                uv[c * 2] = copy[(n - 1 - c) * 2]
                uv[c * 2 + 1] = copy[(n - 1 - c) * 2 + 1]
            }
        }
        mesh.markDirty()
    }

    private fun growSkin(mesh: Mesh, origins: List<Int>) {
        val skin = mesh.skin ?: return
        val newCount = mesh.vertices.size
        if (skin.vertexCount == newCount) return
        val bones = IntArray(newCount * 4)
        val weights = FloatArray(newCount * 4)
        val oldCount = min(skin.vertexCount, newCount)
        skin.boneIndices.copyInto(bones, 0, 0, oldCount * 4)
        skin.weights.copyInto(weights, 0, 0, oldCount * 4)
        for (v in skin.vertexCount until newCount) {
            val origin = origins.getOrElse(v - skin.vertexCount) { -1 }
            if (origin in 0 until skin.vertexCount) {
                for (k in 0..3) {
                    bones[v * 4 + k] = skin.boneIndices[origin * 4 + k]
                    weights[v * 4 + k] = skin.weights[origin * 4 + k]
                }
            }
        }
        mesh.skin = SkinWeights(bones, weights)
    }

    /**
     * Extrudes a region of faces along its average normal and stitches
     * boundary edges with side quads. Returns the new cap face indices.
     */
    fun extrudeFaces(mesh: Mesh, selected: Set<Int>, distance: Float): Set<Int> {
        if (selected.isEmpty()) return emptySet()
        val count = HashMap<Long, Int>()
        val oriented = HashMap<Long, Pair<Int, Int>>()
        for (f in selected) {
            val idx = mesh.faces[f]
            for (i in idx.indices) {
                val a = idx[i]; val b = idx[(i + 1) % idx.size]
                val k = edgeKey(a, b)
                count[k] = (count[k] ?: 0) + 1
                oriented[k] = a to b
            }
        }
        var normal = Vec3.ZERO
        for (f in selected) normal += mesh.faceNormal(f)
        normal = if (normal.lengthSq() < 1e-10f) Vec3.UP else normal.normalized()

        val oldCount = mesh.vertices.size
        val origins = ArrayList<Int>()
        val dup = IntArray(oldCount) { -1 }
        for (f in selected) {
            for (v in mesh.faces[f]) {
                if (dup[v] < 0) {
                    dup[v] = mesh.addVertex(mesh.vertices[v] + normal * distance)
                    origins.add(v)
                }
            }
        }

        val newFaces = ArrayList<IntArray>()
        val newUvs = ArrayList<FloatArray>()
        val capMap = HashMap<Int, Int>()
        for (f in mesh.faces.indices) {
            if (f !in selected) {
                newFaces.add(mesh.faces[f].copyOf())
                newUvs.add(mesh.faceUvs[f].copyOf())
            }
        }
        for (f in selected) {
            val cap = mesh.faces[f].map { dup[it] }.toIntArray()
            if (cap.any { it < 0 }) continue
            capMap[f] = newFaces.size
            newFaces.add(cap)
            newUvs.add(mesh.faceUvs[f].copyOf())
        }
        for ((key, c) in count) {
            if (c != 1) continue
            val (a, b) = oriented[key] ?: continue
            val na = dup[a]; val nb = dup[b]
            if (na < 0 || nb < 0) continue
            newFaces.add(intArrayOf(a, b, nb, na))
            newUvs.add(floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f))
        }
        mesh.faces.clear(); mesh.faces.addAll(newFaces)
        mesh.faceUvs.clear(); mesh.faceUvs.addAll(newUvs)
        growSkin(mesh, origins)
        mesh.markDirty()
        return capMap.values.toSet()
    }

    /** Insets each selected face, creating a smaller inner face and a rim. */
    fun insetFaces(mesh: Mesh, selected: Set<Int>, amount: Float, depth: Float = 0f) {
        if (selected.isEmpty()) return
        val oldCount = mesh.vertices.size
        val origins = ArrayList<Int>()
        val rimFaces = ArrayList<Pair<IntArray, FloatArray>>()
        val insetAmount = clamp(amount, 0.01f, 0.6f)
        for (f in selected.toList().sorted()) {
            if (f !in mesh.faces.indices) continue
            val idx = mesh.faces[f]
            if (idx.size < 3) continue
            val center = mesh.faceCentroid(f)
            val n = mesh.faceNormal(f)
            val uvCenter = Vec2(
                (0 until idx.size).sumOf { mesh.faceUvs[f][it * 2].toDouble() }.toFloat() / idx.size,
                (0 until idx.size).sumOf { mesh.faceUvs[f][it * 2 + 1].toDouble() }.toFloat() / idx.size
            )
            val inner = IntArray(idx.size)
            for (i in idx.indices) {
                val p = mesh.vertices[idx[i]]
                val target = p + (center - p) * insetAmount + n * depth
                inner[i] = mesh.addVertex(target)
                origins.add(idx[i])
            }
            for (i in idx.indices) {
                val a = idx[i]; val b = idx[(i + 1) % idx.size]
                rimFaces.add(
                    intArrayOf(a, b, inner[(i + 1) % idx.size], inner[i]) to
                        floatArrayOf(
                            mesh.faceUvs[f][i * 2], mesh.faceUvs[f][i * 2 + 1],
                            mesh.faceUvs[f][((i + 1) % idx.size) * 2], mesh.faceUvs[f][((i + 1) % idx.size) * 2 + 1],
                            (mesh.faceUvs[f][((i + 1) % idx.size) * 2] + uvCenter.x) * 0.5f,
                            (mesh.faceUvs[f][((i + 1) % idx.size) * 2 + 1] + uvCenter.y) * 0.5f,
                            (mesh.faceUvs[f][i * 2] + uvCenter.x) * 0.5f,
                            (mesh.faceUvs[f][i * 2 + 1] + uvCenter.y) * 0.5f
                        )
                )
            }
            mesh.faces[f] = inner
            val uv = FloatArray(idx.size * 2)
            for (i in idx.indices) {
                val oldU = mesh.faceUvs[f][i * 2]
                val oldV = mesh.faceUvs[f][i * 2 + 1]
                uv[i * 2] = lerp(uvCenter.x, oldU, 1f - insetAmount)
                uv[i * 2 + 1] = lerp(uvCenter.y, oldV, 1f - insetAmount)
            }
            mesh.faceUvs[f] = uv
        }
        for ((idx, uv) in rimFaces) {
            mesh.faces.add(idx)
            mesh.faceUvs.add(uv)
        }
        growSkin(mesh, origins)
        mesh.markDirty()
    }

    /**
     * Chamfers selected vertices: every incident face corner is split by two
     * points along the adjacent edges and a new cap polygon closes the cut.
     */
    fun bevelVertices(mesh: Mesh, selected: Set<Int>, width: Float) {
        if (selected.isEmpty() || width <= 0f) return
        val incident = mesh.vertexFaces()
        val normals = mesh.vertexNormals()
        val oldCount = mesh.vertices.size
        val origins = ArrayList<Int>()
        val pointMap = HashMap<Long, Int>()

        fun pointFor(v: Int, u: Int): Int {
            val key = (v.toLong() shl 32) or (u.toLong() and 0xffffffffL)
            pointMap[key]?.let { return it }
            val pv = mesh.vertices[v]
            val pu = mesh.vertices[u]
            val len = (pu - pv).length()
            if (len < 1e-7f) return v
            val t = (width / len).coerceIn(0.002f, 0.45f)
            val nv = mesh.addVertex(pv + (pu - pv) * t)
            origins.add(v)
            pointMap[key] = nv
            return nv
        }

        val newFaces = ArrayList<IntArray>()
        val newUvs = ArrayList<FloatArray>()
        for (f in mesh.faces.indices) {
            val idx = mesh.faces[f]
            val n = idx.size
            if (idx.none { it in selected }) {
                newFaces.add(idx.copyOf()); newUvs.add(mesh.faceUvs[f].copyOf()); continue
            }
            val outIdx = ArrayList<Int>()
            val outUv = ArrayList<Float>()
            for (i in 0 until n) {
                val v = idx[i]
                val prev = idx[(i - 1 + n) % n]
                val next = idx[(i + 1) % n]
                if (v !in selected) {
                    outIdx.add(v)
                    outUv.add(mesh.faceUvs[f][i * 2]); outUv.add(mesh.faceUvs[f][i * 2 + 1])
                } else {
                    val pv = mesh.vertices[v]
                    val uvV = Vec2(mesh.faceUvs[f][i * 2], mesh.faceUvs[f][i * 2 + 1])
                    val a = pointFor(v, prev)
                    val b = pointFor(v, next)
                    if (a != v) {
                        val t = (mesh.vertices[a] - pv).length() / max(1e-7f, (mesh.vertices[prev] - pv).length())
                        val uvPrev = Vec2(mesh.faceUvs[f][((i - 1 + n) % n) * 2], mesh.faceUvs[f][((i - 1 + n) % n) * 2 + 1])
                        val uv = Vec2(lerp(uvV.x, uvPrev.x, t), lerp(uvV.y, uvPrev.y, t))
                        outIdx.add(a); outUv.add(uv.x); outUv.add(uv.y)
                    }
                    if (b != v) {
                        val t = (mesh.vertices[b] - pv).length() / max(1e-7f, (mesh.vertices[next] - pv).length())
                        val uvNext = Vec2(mesh.faceUvs[f][((i + 1) % n) * 2], mesh.faceUvs[f][((i + 1) % n) * 2 + 1])
                        val uv = Vec2(lerp(uvV.x, uvNext.x, t), lerp(uvV.y, uvNext.y, t))
                        outIdx.add(b); outUv.add(uv.x); outUv.add(uv.y)
                    }
                }
            }
            if (outIdx.distinct().size >= 3) {
                newFaces.add(outIdx.toIntArray()); newUvs.add(outUv.toFloatArray())
            }
        }

        for (v in selected) {
            if (v !in mesh.vertices.indices) continue
            val n = Vec3(
                normals.getOrElse(v * 3) { 0f },
                normals.getOrElse(v * 3 + 1) { 1f },
                normals.getOrElse(v * 3 + 2) { 0f }
            ).normalized()
            val pts = ArrayList<Int>()
            val keys = pointMap.keys.filter { (it ushr 32).toInt() == v }
            for (k in keys) {
                pts.add(pointMap[k]!!)
            }
            if (pts.size < 3) continue
            val center = pts.fold(Vec3.ZERO) { acc, p -> acc + mesh.vertices[p] } / pts.size.toFloat()
            var tangent = (mesh.vertices[pts[0]] - center)
            tangent = (tangent - n * (tangent dot n))
            if (tangent.lengthSq() < 1e-9f) tangent = Vec3.RIGHT
            tangent = tangent.normalized()
            val bitangent = n cross tangent
            pts.sortBy {
                val d = mesh.vertices[it] - center
                atan2((d dot bitangent).toDouble(), (d dot tangent).toDouble()).toFloat()
            }
            val cap = pts.toIntArray()
            var capNormal = Vec3.ZERO
            for (i in cap.indices) {
                capNormal += (mesh.vertices[cap[i]] - center) cross (mesh.vertices[cap[(i + 1) % cap.size]] - center)
            }
            if ((capNormal dot n) < 0f) cap.reverse()
            val uvs = FloatArray(cap.size * 2)
            for (i in cap.indices) {
                val d = mesh.vertices[cap[i]] - center
                uvs[i * 2] = (d dot tangent) + 0.5f
                uvs[i * 2 + 1] = (d dot bitangent) + 0.5f
            }
            newFaces.add(cap); newUvs.add(uvs)
        }

        mesh.faces.clear(); mesh.faces.addAll(newFaces)
        mesh.faceUvs.clear(); mesh.faceUvs.addAll(newUvs)
        growSkin(mesh, origins)
        mesh.removeDegenerateFaces()
        mesh.markDirty()
    }

    /** One level of Catmull-Clark subdivision; produces quads only. */
    fun subdivide(mesh: Mesh, smooth: Boolean = true) {
        if (mesh.faces.isEmpty()) return
        val oldCount = mesh.vertices.size
        val faceCenters = ArrayList<Int>()
        val origins = ArrayList<Int>()
        val faceCenterUv = ArrayList<Vec2>()
        for (f in mesh.faces.indices) {
            val idx = mesh.faces[f]
            val c = mesh.faceCentroid(f)
            faceCenters.add(mesh.addVertex(c))
            origins.add(idx[0])
            var u = 0f; var v = 0f
            for (i in idx.indices) { u += mesh.faceUvs[f][i * 2]; v += mesh.faceUvs[f][i * 2 + 1] }
            faceCenterUv.add(Vec2(u / idx.size, v / idx.size))
        }
        val edges = mesh.edges()
        val edgePoints = HashMap<Long, Int>()
        for ((key, e) in edges) {
            val pa = mesh.vertices[e.a]; val pb = mesh.vertices[e.b]
            var p = (pa + pb) * 0.5f
            if (smooth && e.faces.size == 2) {
                p = (pa + pb +
                    mesh.vertices[faceCenters[e.faces[0]]] +
                    mesh.vertices[faceCenters[e.faces[1]]]) / 4f
            }
            edgePoints[key] = mesh.addVertex(p)
            origins.add(e.a)
        }
        if (smooth) {
            val vertexFaces = mesh.vertexFaces()
            val neighbors = mesh.vertexNeighbors()
            val oldPositions = mesh.vertices.take(oldCount)
            for (v in 0 until oldCount) {
                val nFaces = vertexFaces[v].size
                if (nFaces == 0) continue
                val boundaryNeighbors = ArrayList<Int>()
                for (nb in neighbors[v]) {
                    val key = edgeKey(v, nb)
                    if (edges[key]?.faces?.size == 1) boundaryNeighbors.add(nb)
                }
                if (boundaryNeighbors.size >= 2) {
                    val sum = oldPositions[boundaryNeighbors[0]] + oldPositions[boundaryNeighbors[1]]
                    mesh.vertices[v] = (oldPositions[v] * 6f + sum) / 8f
                } else {
                    val f = vertexFaces[v].fold(Vec3.ZERO) { acc, fi -> acc + mesh.vertices[faceCenters[fi]] } / nFaces.toFloat()
                    val r = neighbors[v].fold(Vec3.ZERO) { acc, nb ->
                        acc + (oldPositions[v] + oldPositions[nb]) * 0.5f
                    } / max(1, neighbors[v].size).toFloat()
                    val n = nFaces.toFloat()
                    mesh.vertices[v] = (f + r * 2f + oldPositions[v] * (n - 3f)) / n
                }
            }
        }
        val newFaces = ArrayList<IntArray>()
        val newUvs = ArrayList<FloatArray>()
        for (f in mesh.faces.indices) {
            val idx = mesh.faces[f]
            val n = idx.size
            for (i in 0 until n) {
                val v = idx[i]
                val next = idx[(i + 1) % n]
                val prev = idx[(i - 1 + n) % n]
                val epNext = edgePoints[edgeKey(v, next)] ?: continue
                val epPrev = edgePoints[edgeKey(prev, v)] ?: continue
                val uvV = Vec2(mesh.faceUvs[f][i * 2], mesh.faceUvs[f][i * 2 + 1])
                val uvNext = Vec2(mesh.faceUvs[f][((i + 1) % n) * 2], mesh.faceUvs[f][((i + 1) % n) * 2 + 1])
                val uvPrev = Vec2(mesh.faceUvs[f][((i - 1 + n) % n) * 2], mesh.faceUvs[f][((i - 1 + n) % n) * 2 + 1])
                newFaces.add(intArrayOf(v, epNext, faceCenters[f], epPrev))
                newUvs.add(floatArrayOf(
                    uvV.x, uvV.y,
                    (uvV.x + uvNext.x) * 0.5f, (uvV.y + uvNext.y) * 0.5f,
                    faceCenterUv[f].x, faceCenterUv[f].y,
                    (uvV.x + uvPrev.x) * 0.5f, (uvV.y + uvPrev.y) * 0.5f
                ))
            }
        }
        mesh.faces.clear(); mesh.faces.addAll(newFaces)
        mesh.faceUvs.clear(); mesh.faceUvs.addAll(newUvs)
        growSkin(mesh, origins)
        mesh.markDirty()
    }

    fun deleteFaces(mesh: Mesh, selected: Set<Int>) {
        if (selected.isEmpty()) return
        val keep = ArrayList<IntArray>()
        val keepUv = ArrayList<FloatArray>()
        for (f in mesh.faces.indices) {
            if (f in selected) continue
            keep.add(mesh.faces[f])
            keepUv.add(mesh.faceUvs[f])
        }
        mesh.faces.clear(); mesh.faces.addAll(keep)
        mesh.faceUvs.clear(); mesh.faceUvs.addAll(keepUv)
        mesh.markDirty()
    }

    fun laplacianSmooth(mesh: Mesh, vertices: Set<Int>? = null, iterations: Int = 1, strength: Float = 0.5f) {
        if (mesh.vertices.isEmpty()) return
        val nb = mesh.vertexNeighbors()
        repeat(iterations) {
            val old = mesh.vertices.toList()
            for (v in mesh.vertices.indices) {
                if (vertices != null && v !in vertices) continue
                if (nb[v].isEmpty()) continue
                var avg = Vec3.ZERO
                for (n in nb[v]) avg += old[n]
                avg /= nb[v].size.toFloat()
                mesh.vertices[v] = old[v].lerp(avg, strength)
            }
        }
        mesh.markDirty()
    }

    fun mirror(mesh: Mesh, axis: Int = 0, weldSeam: Boolean = true) {
        val clone = mesh.clone()
        val scale = when (axis) {
            0 -> Mat4.scaling(Vec3(-1f, 1f, 1f))
            1 -> Mat4.scaling(Vec3(1f, -1f, 1f))
            else -> Mat4.scaling(Vec3(1f, 1f, -1f))
        }
        clone.transform(scale)
        mesh.append(clone)
        if (weldSeam) mesh.weld(1e-4f)
        mesh.markDirty()
    }

    fun transformVertices(mesh: Mesh, vertices: Set<Int>, matrix: Mat4) {
        for (v in vertices) if (v in mesh.vertices.indices) mesh.vertices[v] = matrix.transformPoint(mesh.vertices[v])
        mesh.markDirty()
    }

    fun translateVertices(mesh: Mesh, vertices: Set<Int>, offset: Vec3) {
        for (v in vertices) if (v in mesh.vertices.indices) mesh.vertices[v] += offset
        mesh.markDirty()
    }

    fun scaleVertices(mesh: Mesh, vertices: Set<Int>, pivot: Vec3, factor: Vec3) {
        for (v in vertices) if (v in mesh.vertices.indices) {
            val rel = mesh.vertices[v] - pivot
            mesh.vertices[v] = pivot + Vec3(rel.x * factor.x, rel.y * factor.y, rel.z * factor.z)
        }
        mesh.markDirty()
    }

    fun rotateVertices(mesh: Mesh, vertices: Set<Int>, pivot: Vec3, rotation: com.studio3dpro.engine.math.Quat) {
        val mat = Mat4.translation(pivot) * Mat4.rotation(rotation) * Mat4.translation(-pivot)
        for (v in vertices) if (v in mesh.vertices.indices) mesh.vertices[v] = mat.transformPoint(mesh.vertices[v])
        mesh.markDirty()
    }

    fun vertexCenter(mesh: Mesh, vertices: Set<Int>): Vec3 {
        if (vertices.isEmpty()) return Vec3.ZERO
        var sum = Vec3.ZERO
        for (v in vertices) if (v in mesh.vertices.indices) sum += mesh.vertices[v]
        return sum / vertices.size.toFloat()
    }

    /** Subdivides a mesh into triangles, useful for importers that need flat faces. */
    fun triangulate(mesh: Mesh): Mesh {
        val tri = mesh.clone()
        val outFaces = ArrayList<IntArray>()
        val outUvs = ArrayList<FloatArray>()
        val triIndices = mesh.triangles()
        val triFaces = mesh.triangleFaces()
        for (t in triFaces.indices) {
            val f = triFaces[t]
            val srcIdx = mesh.faces[f]
            val i0 = srcIdx.indexOf(triIndices[t * 3])
            val i1 = srcIdx.indexOf(triIndices[t * 3 + 1])
            val i2 = srcIdx.indexOf(triIndices[t * 3 + 2])
            if (i0 < 0 || i1 < 0 || i2 < 0) continue
            outFaces.add(intArrayOf(triIndices[t * 3], triIndices[t * 3 + 1], triIndices[t * 3 + 2]))
            outUvs.add(
                floatArrayOf(
                    mesh.faceUvs[f][i0 * 2], mesh.faceUvs[f][i0 * 2 + 1],
                    mesh.faceUvs[f][i1 * 2], mesh.faceUvs[f][i1 * 2 + 1],
                    mesh.faceUvs[f][i2 * 2], mesh.faceUvs[f][i2 * 2 + 1]
                )
            )
        }
        tri.faces.clear(); tri.faces.addAll(outFaces)
        tri.faceUvs.clear(); tri.faceUvs.addAll(outUvs)
        tri.markDirty()
        return tri
    }

    fun circlePoints(count: Int, radius: Float, y: Float, startAngle: Float = 0f): List<Vec3> =
        (0 until count).map {
            val a = startAngle + 2f * Math.PI.toFloat() * it / count
            Vec3(cos(a) * radius, y, sin(a) * radius)
        }
}
