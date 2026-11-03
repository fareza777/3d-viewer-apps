package com.studio3dpro.engine.mesh

import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.math.clamp
import kotlin.math.max

enum class SculptMode { DRAW, INFLATE, SMOOTH, FLATTEN, PINCH, GRAB }

/** Vertex-level sculpt brushes with falloff, symmetry and optional X mirroring. */
object Sculpt {

    private fun falloff(distance: Float, radius: Float): Float {
        val t = clamp(1f - distance / radius, 0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun stroke(
        mesh: Mesh,
        center: Vec3,
        radius: Float,
        strength: Float,
        mode: SculptMode,
        normal: Vec3,
        symmetryX: Boolean = false,
        grabDelta: Vec3 = Vec3.ZERO
    ): Int {
        if (radius <= 0f) return 0
        var touched = applyStroke(mesh, center, radius, strength, mode, normal, grabDelta)
        if (symmetryX) {
            val mirrored = Vec3(-center.x, center.y, center.z)
            val mirroredNormal = Vec3(-normal.x, normal.y, normal.z)
            touched += applyStroke(mesh, mirrored, radius, strength, mode, mirroredNormal,
                Vec3(-grabDelta.x, grabDelta.y, grabDelta.z))
        }
        mesh.markDirty()
        return touched
    }

    private fun applyStroke(
        mesh: Mesh,
        center: Vec3,
        radius: Float,
        strength: Float,
        mode: SculptMode,
        normal: Vec3,
        grabDelta: Vec3
    ): Int {
        val affected = mesh.verticesInRadius(center, radius)
        if (affected.isEmpty()) return 0
        val normals = if (mode == SculptMode.INFLATE || mode == SculptMode.FLATTEN) mesh.vertexNormals() else null
        val neighbors = if (mode == SculptMode.SMOOTH) mesh.vertexNeighbors() else null
        val before = affected.map { mesh.vertices[it] }
        val n = normal.normalized()
        val planeD = n dot center
        for ((index, v) in affected.withIndex()) {
            val p = before[index]
            val d = p.distanceTo(center)
            val w = falloff(d, radius) * strength
            if (w <= 1e-5f) continue
            val delta = when (mode) {
                SculptMode.DRAW -> n * w
                SculptMode.INFLATE -> {
                    val vn = Vec3(normals!![v * 3], normals[v * 3 + 1], normals[v * 3 + 2])
                    vn * w
                }
                SculptMode.SMOOTH -> {
                    val nb = neighbors!![v]
                    if (nb.isEmpty()) Vec3.ZERO else {
                        var avg = Vec3.ZERO
                        for (x in nb) avg += p.let { mesh.vertices[x] }
                        avg /= nb.size.toFloat()
                        (avg - p) * w
                    }
                }
                SculptMode.FLATTEN -> {
                    val dist = (n dot p) - planeD
                    -n * (dist * w)
                }
                SculptMode.PINCH -> (center - p) * (w * 0.5f)
                SculptMode.GRAB -> grabDelta * falloff(d, radius)
            }
            mesh.vertices[v] = p + delta
        }
        return affected.size
    }

    /** Smooths the area, preserving the silhouette better than repeated draw strokes. */
    fun smoothArea(mesh: Mesh, center: Vec3, radius: Float, iterations: Int = 2, strength: Float = 0.6f): Int {
        val affected = mesh.verticesInRadius(center, radius).toSet()
        if (affected.isEmpty()) return 0
        MeshOps.laplacianSmooth(mesh, affected, iterations, strength)
        return affected.size
    }

    fun brushRadiusForMesh(mesh: Mesh): Float {
        val b = mesh.bounds()
        return max(b.radius * 0.08f, 0.02f)
    }
}
