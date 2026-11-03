package com.studio3dpro.engine.mesh

import com.studio3dpro.engine.math.EPS
import com.studio3dpro.engine.math.Vec2
import com.studio3dpro.engine.math.Vec3
import kotlin.math.abs

enum class BooleanOp { UNION, DIFFERENCE, INTERSECTION }

/**
 * Constructive solid geometry via BSP trees (csg.js approach).
 * Inputs should be closed manifolds; works on arbitrary polygon soups.
 */
object MeshBoolean {

    fun apply(a: Mesh, b: Mesh, op: BooleanOp): Mesh {
        if (a.faces.isEmpty()) return if (op == BooleanOp.UNION) b.clone() else Mesh()
        if (b.faces.isEmpty()) {
            return when (op) {
                BooleanOp.UNION -> a.clone()
                BooleanOp.DIFFERENCE -> a.clone()
                BooleanOp.INTERSECTION -> Mesh()
            }
        }
        val na = CsgNode(polygonsOf(a))
        val nb = CsgNode(polygonsOf(b))
        val result: List<CsgPolygon> = when (op) {
            BooleanOp.UNION -> {
                na.clipTo(nb)
                nb.clipTo(na)
                nb.invert()
                nb.clipTo(na)
                nb.invert()
                na.build(nb.allPolygons())
                na.allPolygons()
            }
            BooleanOp.DIFFERENCE -> {
                na.invert()
                na.clipTo(nb)
                nb.clipTo(na)
                nb.invert()
                nb.clipTo(na)
                nb.invert()
                na.build(nb.allPolygons())
                na.invert()
                na.allPolygons()
            }
            BooleanOp.INTERSECTION -> {
                na.invert()
                nb.clipTo(na)
                nb.invert()
                na.clipTo(nb)
                nb.clipTo(na)
                na.build(nb.allPolygons())
                na.invert()
                na.allPolygons()
            }
        }
        return meshOf(result)
    }

    private fun polygonsOf(mesh: Mesh): List<CsgPolygon> {
        val out = ArrayList<CsgPolygon>(mesh.faces.size)
        for (f in mesh.faces.indices) {
            val idx = mesh.faces[f]
            if (idx.size < 3) continue
            val verts = idx.mapIndexed { i, v ->
                CsgVertex(mesh.vertices[v], Vec2(mesh.faceUvs[f][i * 2], mesh.faceUvs[f][i * 2 + 1]))
            }
            if (verts.size >= 3) out.add(CsgPolygon(verts.toMutableList()))
        }
        return out
    }

    private fun meshOf(polygons: List<CsgPolygon>): Mesh {
        val mesh = Mesh()
        for (p in polygons) {
            if (p.vertices.size < 3) continue
            val start = mesh.vertices.size
            p.vertices.forEach { mesh.addVertex(it.pos) }
            val uv = FloatArray(p.vertices.size * 2)
            p.vertices.forEachIndexed { i, v ->
                uv[i * 2] = v.uv.x
                uv[i * 2 + 1] = v.uv.y
            }
            mesh.addFace(IntArray(p.vertices.size) { start + it }, uv)
        }
        mesh.removeDegenerateFaces(1e-10f)
        MeshOps.ensureOutward(mesh)
        return mesh
    }

    private data class CsgVertex(val pos: Vec3, val uv: Vec2) {
        fun interpolate(other: CsgVertex, t: Float): CsgVertex = CsgVertex(
            pos.lerp(other.pos, t),
            uv.let { Vec2(it.x + (other.uv.x - it.x) * t, it.y + (other.uv.y - it.y) * t) }
        )

        fun flipped() = CsgVertex(pos, Vec2(uv.x, 1f - uv.y))
    }

    private class CsgPlane(val normal: Vec3, val w: Float) {
        fun flip(): CsgPlane = CsgPlane(-normal, -w)
        fun splitPolygon(polygon: CsgPolygon, coplanarFront: MutableList<CsgPolygon>,
                         coplanarBack: MutableList<CsgPolygon>, front: MutableList<CsgPolygon>,
                         back: MutableList<CsgPolygon>) {
            val coplanar = 0; val frontType = 1; val backType = 2; val spanning = 3
            var polygonType = coplanar
            val types = IntArray(polygon.vertices.size)
            for (i in polygon.vertices.indices) {
                val t = (normal dot polygon.vertices[i].pos) - w
                val type = if (t < -EPS) backType else if (t > EPS) frontType else coplanar
                polygonType = polygonType or type
                types[i] = type
            }
            when (polygonType) {
                coplanar -> {
                    if ((normal dot polygon.plane.normal) > 0f) coplanarFront.add(polygon)
                    else coplanarBack.add(polygon)
                }
                frontType -> front.add(polygon)
                backType -> back.add(polygon)
                else -> {
                    val f = ArrayList<CsgVertex>()
                    val b = ArrayList<CsgVertex>()
                    for (i in polygon.vertices.indices) {
                        val j = (i + 1) % polygon.vertices.size
                        val ti = types[i]; val tj = types[j]
                        val vi = polygon.vertices[i]; val vj = polygon.vertices[j]
                        if (ti != backType) f.add(vi)
                        if (ti != frontType) b.add(vi)
                        if ((ti or tj) == spanning) {
                            val t = (w - (normal dot vi.pos)) / (normal dot (vj.pos - vi.pos))
                            val v = vi.interpolate(vj, t)
                            f.add(v); b.add(v)
                        }
                    }
                    if (f.size >= 3) front.add(CsgPolygon(f))
                    if (b.size >= 3) back.add(CsgPolygon(b))
                }
            }
        }

        companion object {
            fun fromPoints(a: Vec3, b: Vec3, c: Vec3): CsgPlane? {
                val n = (b - a) cross (c - a)
                if (n.lengthSq() < 1e-12f) return null
                val norm = n.normalized()
                return CsgPlane(norm, norm dot a)
            }
        }
    }

    private class CsgPolygon(val vertices: MutableList<CsgVertex>) {
        val plane: CsgPlane
        init {
            require(vertices.size >= 3)
            var p: CsgPlane? = null
            var i = 2
            while (i < vertices.size && p == null) {
                p = CsgPlane.fromPoints(vertices[0].pos, vertices[1].pos, vertices[i].pos)
                i++
            }
            plane = p ?: CsgPlane(Vec3.UP, 0f)
        }

        fun flipped(): CsgPolygon =
            CsgPolygon(vertices.asReversed().map { it.flipped() }.toMutableList())
    }

    private class CsgNode(polygons: List<CsgPolygon>? = null) {
        private var plane: CsgPlane? = null
        private var front: CsgNode? = null
        private var back: CsgNode? = null
        private val polygons = ArrayList<CsgPolygon>()

        init {
            if (polygons != null) build(polygons)
        }

        fun invert() {
            val stack = ArrayDeque<CsgNode>()
            stack.add(this)
            while (stack.isNotEmpty()) {
                val node = stack.removeLast()
                for (i in node.polygons.indices) node.polygons[i] = node.polygons[i].flipped()
                node.plane = node.plane?.flip()
                val f = node.front; node.front = node.back; node.back = f
                node.front?.let { stack.add(it) }
                node.back?.let { stack.add(it) }
            }
        }

        fun clipPolygons(input: List<CsgPolygon>): List<CsgPolygon> {
            val p = plane ?: return input.toList()
            val frontPoly = ArrayList<CsgPolygon>()
            val backPoly = ArrayList<CsgPolygon>()
            for (poly in input) p.splitPolygon(poly, frontPoly, backPoly, frontPoly, backPoly)
            var result = front?.clipPolygons(frontPoly) ?: frontPoly
            if (back != null) {
                result = result + back!!.clipPolygons(backPoly)
            }
            return result
        }

        fun clipTo(other: CsgNode) {
            val stack = ArrayDeque<CsgNode>()
            stack.add(this)
            while (stack.isNotEmpty()) {
                val node = stack.removeLast()
                val clipped = other.clipPolygons(node.polygons)
                node.polygons.clear(); node.polygons.addAll(clipped)
                node.front?.let { stack.add(it) }
                node.back?.let { stack.add(it) }
            }
        }

        fun allPolygons(): List<CsgPolygon> {
            val out = ArrayList<CsgPolygon>()
            val stack = ArrayDeque<CsgNode>()
            stack.add(this)
            while (stack.isNotEmpty()) {
                val node = stack.removeLast()
                out.addAll(node.polygons)
                node.front?.let { stack.add(it) }
                node.back?.let { stack.add(it) }
            }
            return out
        }

        fun build(input: List<CsgPolygon>) {
            if (input.isEmpty()) return
            val stack = ArrayDeque<Pair<CsgNode, List<CsgPolygon>>>()
            stack.add(this to input)
            while (stack.isNotEmpty()) {
                val (node, polys) = stack.removeLast()
                if (polys.isEmpty()) continue
                if (node.plane == null) node.plane = polys[0].plane
                val frontList = ArrayList<CsgPolygon>()
                val backList = ArrayList<CsgPolygon>()
                for (poly in polys) {
                    node.plane!!.splitPolygon(poly, node.polygons, node.polygons, frontList, backList)
                }
                if (frontList.isNotEmpty()) {
                    if (node.front == null) node.front = CsgNode()
                    stack.add(node.front!! to frontList)
                }
                if (backList.isNotEmpty()) {
                    if (node.back == null) node.back = CsgNode()
                    stack.add(node.back!! to backList)
                }
            }
        }
    }

    /** Signed volume of a closed mesh; useful for validating boolean results. */
    fun volume(mesh: Mesh): Float {
        val tri = mesh.triangles()
        var v = 0f
        var i = 0
        while (i < tri.size) {
            val a = mesh.vertices[tri[i]]
            val b = mesh.vertices[tri[i + 1]]
            val c = mesh.vertices[tri[i + 2]]
            v += (a dot (b cross c)) / 6f
            i += 3
        }
        return v
    }
}
