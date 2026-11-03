package com.studio3dpro.engine.io

import com.studio3dpro.engine.math.Mat4
import com.studio3dpro.engine.math.Vec2
import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.mesh.Mesh
import com.studio3dpro.engine.scene.Material
import com.studio3dpro.engine.scene.Scene
import com.studio3dpro.engine.scene.SceneObject
import java.util.Locale

/** Wavefront OBJ import/export (geometry, UVs, normals, groups). */
object ObjIO {

    private data class Face(
        val indices: IntArray,
        val uvs: FloatArray
    )

    private class Segment(var name: String) {
        val faces = ArrayList<Face>()
    }

    fun import(text: String, name: String = "OBJ Model"): ModelImportResult {
        val positions = ArrayList<Vec3>()
        val texCoords = ArrayList<Vec2>()
        val warnings = ArrayList<String>()
        val segments = ArrayList<Segment>()
        var current = Segment(name)
        segments.add(current)
        var currentMaterial = ""
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val parts = line.split(Regex("\\s+"))
            when (parts[0]) {
                "v" -> {
                    if (parts.size >= 4) positions.add(Vec3(parts[1].toFloat(), parts[2].toFloat(), parts[3].toFloat()))
                }
                "vt" -> {
                    if (parts.size >= 3) texCoords.add(Vec2(parts[1].toFloat(), parts[2].toFloat()))
                }
                "o", "g" -> {
                    val newName = parts.drop(1).joinToString(" ").ifBlank { "Group ${segments.size + 1}" }
                    if (current.faces.isEmpty()) current.name = newName
                    else {
                        current = Segment(newName)
                        segments.add(current)
                    }
                }
                "usemtl" -> {
                    val mat = parts.drop(1).joinToString(" ")
                    if (mat != currentMaterial && current.faces.isNotEmpty()) {
                        current = Segment(current.name + (if (mat.isNotBlank()) " [$mat]" else ""))
                        segments.add(current)
                    }
                    currentMaterial = mat
                }
                "f" -> {
                    if (parts.size < 4) continue
                    val idx = ArrayList<Int>()
                    val uvs = ArrayList<Float>()
                    for (token in parts.drop(1)) {
                        val slash = token.split('/')
                        var vi = slash[0].toIntOrNull() ?: continue
                        if (vi < 0) vi = positions.size + vi
                        else vi -= 1
                        if (vi !in positions.indices) continue
                        idx.add(vi)
                        var u = 0f; var v = 0f
                        if (slash.size > 1 && slash[1].isNotEmpty()) {
                            var ti = slash[1].toIntOrNull() ?: 0
                            if (ti < 0) ti = texCoords.size + ti else ti -= 1
                            if (ti in texCoords.indices) { u = texCoords[ti].x; v = texCoords[ti].y }
                        }
                        uvs.add(u); uvs.add(v)
                    }
                    if (idx.size >= 3) {
                        val remap = HashMap<Int, Int>()
                        // Vertex indices are remapped per segment later; store global for now.
                        current.faces.add(Face(idx.toIntArray(), uvs.toFloatArray()))
                    }
                }
            }
        }
        val scene = Scene()
        scene.name = name
        for (segment in segments) {
            if (segment.faces.isEmpty()) continue
            val mesh = Mesh()
            val map = HashMap<Int, Int>()
            for (face in segment.faces) {
                val out = IntArray(face.indices.size)
                for (i in face.indices.indices) {
                    val global = face.indices[i]
                    out[i] = map.getOrPut(global) {
                        mesh.addVertex(positions[global]); mesh.vertices.size - 1
                    }
                }
                mesh.addFace(out, face.uvs)
            }
            mesh.markDirty()
            val materialName = Regex("\\[(.+)]$").find(segment.name)?.groupValues?.get(1) ?: ""
            scene.addObject(
                segment.name,
                mesh,
                Material(name = materialName.ifBlank { "OBJ Material" })
            )
        }
        if (scene.objects.isEmpty()) throw IllegalArgumentException("OBJ contains no faces")
        if (texCoords.isEmpty()) warnings.add("OBJ has no texture coordinates; a box projection was applied.")
        if (scene.objects.any { it.mesh!!.faceUvs.all { uv -> uv.all { it == 0f } } }) {
            for (o in scene.objects) {
                if (o.mesh!!.faceUvs.all { uv -> uv.all { it == 0f } }) {
                    com.studio3dpro.engine.mesh.UvProjection.project(o.mesh!!, com.studio3dpro.engine.mesh.UvProjection.Mode.BOX)
                }
            }
        }
        scene.touch()
        return ModelImportResult(scene, warnings)
    }

    fun export(scene: Scene, selectedObjectId: Long? = null): String {
        val sb = StringBuilder()
        sb.appendLine("# 3D Studio Pro OBJ export")
        sb.appendLine("# ${scene.objects.size} object(s)")
        var vertexOffset = 1
        var uvOffset = 1
        var normalOffset = 1
        val objects = scene.objects.filter { it.visible && it.mesh != null && (selectedObjectId == null || it.id == selectedObjectId) }
        for (obj in objects) {
            val mesh = obj.mesh ?: continue
            val world = obj.worldMatrix()
            val safeName = obj.name.ifBlank { "Object" }.replace(Regex("\\s+"), "_")
            sb.appendLine("o $safeName")
            val normals = mesh.cornerNormals(60f)
            for (v in mesh.vertices) {
                val p = world.transformPoint(v)
                sb.appendLine("v ${fmt(p.x)} ${fmt(p.y)} ${fmt(p.z)}")
            }
            var corner = 0
            for (f in mesh.faces.indices) {
                for (c in mesh.faces[f].indices) {
                    sb.appendLine("vt ${fmt(mesh.faceUvs[f][c * 2])} ${fmt(mesh.faceUvs[f][c * 2 + 1])}")
                }
            }
            for (i in mesh.faces.indices) {
                for (c in mesh.faces[i].indices) {
                    sb.appendLine("vn ${fmt(normals[corner * 3])} ${fmt(normals[corner * 3 + 1])} ${fmt(normals[corner * 3 + 2])}")
                    corner++
                }
            }
            corner = 0
            sb.appendLine("usemtl ${obj.material.name.replace(Regex("\\s+"), "_")}")
            for (f in mesh.faces.indices) {
                sb.append("f")
                for (c in mesh.faces[f].indices) {
                    val vi = vertexOffset + mesh.faces[f][c]
                    val ti = uvOffset + corner
                    val ni = normalOffset + corner
                    sb.append(" $vi/$ti/$ni")
                    corner++
                }
                sb.appendLine()
            }
            vertexOffset += mesh.vertices.size
            uvOffset += mesh.totalCorners()
            normalOffset += mesh.totalCorners()
        }
        return sb.toString()
    }

    private fun fmt(v: Float): String = String.format(Locale.US, "%.6f", v)
}
