package com.studio3dpro.engine.mesh

import com.studio3dpro.engine.math.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max

object UvProjection {

    enum class Mode { BOX, CYLINDRICAL, SPHERICAL, PLANAR }

    fun project(mesh: Mesh, mode: Mode, tileScale: Float = 1f) {
        if (mesh.faces.isEmpty()) return
        val bounds = mesh.bounds()
        val size = Vec3(
            max(bounds.size.x, 1e-5f),
            max(bounds.size.y, 1e-5f),
            max(bounds.size.z, 1e-5f)
        )
        for (f in mesh.faces.indices) {
            val idx = mesh.faces[f]
            for (corner in idx.indices) {
                val p = mesh.vertices[idx[corner]]
                val uv = when (mode) {
                    Mode.BOX -> {
                        val n = mesh.faceNormal(f).abs()
                        when {
                            n.x >= n.y && n.x >= n.z -> Pair((p.z - bounds.min.z) / size.z, (p.y - bounds.min.y) / size.y)
                            n.y >= n.x && n.y >= n.z -> Pair((p.x - bounds.min.x) / size.x, (p.z - bounds.min.z) / size.z)
                            else -> Pair((p.x - bounds.min.x) / size.x, (p.y - bounds.min.y) / size.y)
                        }
                    }
                    Mode.CYLINDRICAL -> {
                        val u = atan2(p.z - bounds.center.z, p.x - bounds.center.x) / (2f * PI.toFloat()) + 0.5f
                        Pair(u, (p.y - bounds.min.y) / size.y)
                    }
                    Mode.SPHERICAL -> {
                        val d = p - bounds.center
                        val u = atan2(d.z, d.x) / (2f * PI.toFloat()) + 0.5f
                        val v = 1f - (d.y / (bounds.radius + 1e-6f)) * 0.5f - 0.5f
                        Pair(u, v)
                    }
                    Mode.PLANAR -> {
                        val n = mesh.faceNormal(f).abs()
                        when {
                            n.y >= n.x && n.y >= n.z -> Pair((p.x - bounds.min.x) / size.x, (p.z - bounds.min.z) / size.z)
                            n.z >= n.x -> Pair((p.x - bounds.min.x) / size.x, (p.y - bounds.min.y) / size.y)
                            else -> Pair((p.z - bounds.min.z) / size.z, (p.y - bounds.min.y) / size.y)
                        }
                    }
                }
                var u = uv.first * tileScale
                var v = uv.second * tileScale
                if (mode == Mode.CYLINDRICAL) {
                    // Keep seam-crossing faces continuous.
                    val arr = mesh.faceUvs[f]
                    arr[corner * 2] = u
                    arr[corner * 2 + 1] = v
                    if (corner > 0) {
                        val prevU = arr[(corner - 1) * 2]
                        if (abs(u - prevU) > 0.5f) {
                            u += if (u < prevU) 1f else -1f
                            arr[corner * 2] = u
                        }
                    }
                } else {
                    mesh.faceUvs[f][corner * 2] = u
                    mesh.faceUvs[f][corner * 2 + 1] = v
                }
            }
        }
        mesh.markDirty()
    }
}
