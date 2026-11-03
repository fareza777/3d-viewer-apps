package com.studio3dpro.engine.io

import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.mesh.Mesh
import com.studio3dpro.engine.scene.Material
import com.studio3dpro.engine.scene.Scene
import com.studio3dpro.engine.scene.SceneObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/** STL import/export, binary and ASCII. */
object StlIO {

    fun import(bytes: ByteArray, name: String = "STL Model"): ModelImportResult {
        val warnings = ArrayList<String>()
        val mesh = if (isBinary(bytes)) importBinary(bytes) else importAscii(bytes.toString(Charsets.US_ASCII), warnings)
        if (mesh.faceCount == 0) throw IllegalArgumentException("STL contains no triangles")
        mesh.weld(1e-5f)
        mesh.removeDegenerateFaces()
        val scene = Scene()
        scene.name = name
        scene.addObject(name, mesh, Material(name = "STL Material", roughness = 0.7f))
        scene.touch()
        return ModelImportResult(scene, warnings)
    }

    private fun isBinary(bytes: ByteArray): Boolean {
        if (bytes.size < 84) return false
        val count = ByteBuffer.wrap(bytes, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int
        if (count <= 0) return false
        val expected = 84L + count.toLong() * 50L
        if (expected == bytes.size.toLong()) return true
        val head = bytes.copyOfRange(0, minOf(256, bytes.size)).toString(Charsets.US_ASCII)
        return !head.trimStart().startsWith("solid")
    }

    private fun importBinary(bytes: ByteArray): Mesh {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(80)
        val count = buffer.int
        val mesh = Mesh()
        repeat(count) {
            if (buffer.remaining() < 48) return@repeat
            buffer.float // normal
            buffer.float
            buffer.float
            val a = Vec3(buffer.float, buffer.float, buffer.float)
            val b = Vec3(buffer.float, buffer.float, buffer.float)
            val c = Vec3(buffer.float, buffer.float, buffer.float)
            buffer.short // attribute byte count
            val start = mesh.vertices.size
            mesh.addVertex(a); mesh.addVertex(b); mesh.addVertex(c)
            mesh.addFace(intArrayOf(start, start + 1, start + 2), floatArrayOf(0f, 0f, 1f, 0f, 0.5f, 1f))
        }
        mesh.markDirty()
        return mesh
    }

    private fun importAscii(text: String, warnings: MutableList<String>): Mesh {
        val mesh = Mesh()
        val vertices = ArrayList<Vec3>()
        for (line in text.lineSequence()) {
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 4 && parts[0].equals("vertex", ignoreCase = true)) {
                val v = parts.slice(1..3).mapNotNull { it.toFloatOrNull() }
                if (v.size == 3) vertices.add(Vec3(v[0], v[1], v[2]))
            }
        }
        if (vertices.size % 3 != 0) warnings.add("STL vertex count is not divisible by three.")
        var i = 0
        while (i + 2 < vertices.size) {
            val start = mesh.vertices.size
            mesh.addVertex(vertices[i]); mesh.addVertex(vertices[i + 1]); mesh.addVertex(vertices[i + 2])
            mesh.addFace(intArrayOf(start, start + 1, start + 2), floatArrayOf(0f, 0f, 1f, 0f, 0.5f, 1f))
            i += 3
        }
        mesh.markDirty()
        return mesh
    }

    fun export(scene: Scene, selectedObjectId: Long? = null, binary: Boolean = false): ByteArray {
        val triangles = ArrayList<Triple<Vec3, Vec3, Vec3>>()
        for (obj in scene.objects) {
            if (!obj.visible || obj.mesh == null) continue
            if (selectedObjectId != null && obj.id != selectedObjectId) continue
            val mesh = obj.mesh!!
            val world = obj.worldMatrix()
            val tris = mesh.triangles()
            var i = 0
            while (i < tris.size) {
                triangles.add(Triple(
                    world.transformPoint(mesh.vertices[tris[i]]),
                    world.transformPoint(mesh.vertices[tris[i + 1]]),
                    world.transformPoint(mesh.vertices[tris[i + 2]])
                ))
                i += 3
            }
        }
        return if (binary) exportBinary(triangles) else exportAscii(triangles)
    }

    private fun exportBinary(triangles: List<Triple<Vec3, Vec3, Vec3>>): ByteArray {
        val buffer = ByteBuffer.allocate(84 + triangles.size * 50).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(0)
        val header = "3D Studio Pro STL export".toByteArray(Charsets.US_ASCII)
        buffer.put(header)
        repeat(80 - header.size) { buffer.put(0) }
        buffer.putInt(triangles.size)
        for ((a, b, c) in triangles) {
            val n = ((b - a) cross (c - a)).normalized()
            buffer.putFloat(n.x); buffer.putFloat(n.y); buffer.putFloat(n.z)
            for (p in listOf(a, b, c)) {
                buffer.putFloat(p.x); buffer.putFloat(p.y); buffer.putFloat(p.z)
            }
            buffer.putShort(0)
        }
        return buffer.array()
    }

    private fun exportAscii(triangles: List<Triple<Vec3, Vec3, Vec3>>): ByteArray {
        val sb = StringBuilder()
        sb.appendLine("solid studio3d")
        for ((a, b, c) in triangles) {
            val n = ((b - a) cross (c - a)).normalized()
            sb.appendLine("  facet normal ${f(n.x)} ${f(n.y)} ${f(n.z)}")
            sb.appendLine("    outer loop")
            for (p in listOf(a, b, c)) sb.appendLine("      vertex ${f(p.x)} ${f(p.y)} ${f(p.z)}")
            sb.appendLine("    endloop")
            sb.appendLine("  endfacet")
        }
        sb.appendLine("endsolid studio3d")
        return sb.toString().toByteArray(Charsets.US_ASCII)
    }

    private fun f(v: Float): String = String.format(Locale.US, "%.6f", v)
}
