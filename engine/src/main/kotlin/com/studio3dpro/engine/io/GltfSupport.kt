package com.studio3dpro.engine.io

import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.math.Vec4
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** GLB container utilities. */
object Glb {
    const val MAGIC = 0x46546C67
    const val CHUNK_JSON = 0x4E4F534A
    const val CHUNK_BIN = 0x004E4942

    class Container(val json: JsonValue.Obj, val bin: ByteArray?)

    fun isGlb(bytes: ByteArray): Boolean =
        bytes.size >= 12 &&
            (bytes[0].toInt() and 0xff) == 0x67 &&
            (bytes[1].toInt() and 0xff) == 0x6c &&
            (bytes[2].toInt() and 0xff) == 0x54 &&
            (bytes[3].toInt() and 0xff) == 0x46

    fun read(bytes: ByteArray): Container {
        if (!isGlb(bytes)) {
            val text = bytes.toString(Charsets.UTF_8)
            return Container(Json.parse(text).asObj
                ?: throw IllegalArgumentException("glTF JSON root must be an object"), null)
        }
        val magic = readU32(bytes, 0)
        require(magic == MAGIC) { "Not a GLB file" }
        val version = readU32(bytes, 4)
        require(version == 2) { "Unsupported GLB version $version" }
        val length = readU32(bytes, 8)
        var offset = 12
        var json: JsonValue.Obj? = null
        var bin: ByteArray? = null
        while (offset + 8 <= minOf(length, bytes.size)) {
            val chunkLength = readU32(bytes, offset)
            val chunkType = readU32(bytes, offset + 4)
            offset += 8
            if (offset + chunkLength > bytes.size) break
            when (chunkType) {
                CHUNK_JSON -> {
                    val text = String(bytes, offset, chunkLength, Charsets.UTF_8).trim()
                    json = Json.parse(text).asObj ?: throw IllegalArgumentException("Invalid glTF JSON")
                }
                CHUNK_BIN -> bin = bytes.copyOfRange(offset, offset + chunkLength)
            }
            offset += chunkLength
        }
        return Container(
            json ?: throw IllegalArgumentException("GLB is missing its JSON chunk"),
            bin
        )
    }

    fun write(json: String, bin: ByteArray?): ByteArray {
        val jsonBytes = json.toByteArray(Charsets.UTF_8)
        val jsonPadded = (jsonBytes.size + 3) / 4 * 4
        val binPadded = if (bin != null) (bin.size + 3) / 4 * 4 else 0
        val total = 12 + 8 + jsonPadded + if (bin != null) 8 + binPadded else 0
        val out = ByteArray(total)
        writeU32(out, 0, MAGIC)
        writeU32(out, 4, 2)
        writeU32(out, 8, total)
        writeU32(out, 12, jsonPadded)
        writeU32(out, 16, CHUNK_JSON)
        jsonBytes.copyInto(out, 20)
        for (i in jsonBytes.size until jsonPadded) out[20 + i] = ' '.code.toByte()
        if (bin != null) {
            val base = 20 + jsonPadded
            writeU32(out, base, binPadded)
            writeU32(out, base + 4, CHUNK_BIN)
            bin.copyInto(out, base + 8)
        }
        return out
    }

    fun readU32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    fun writeU32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xff).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xff).toByte()
        bytes[offset + 2] = ((value ushr 16) and 0xff).toByte()
        bytes[offset + 3] = ((value ushr 24) and 0xff).toByte()
    }

    fun readF32(bytes: ByteArray, offset: Int): Float = Float.fromBits(readU32(bytes, offset))
}

fun decodeBase64(input: String): ByteArray {
    val table = IntArray(128) { -1 }
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    alphabet.forEachIndexed { i, c -> table[c.code] = i }
    val cleaned = input.filter { !it.isWhitespace() && it != '=' }
    val out = ByteArray(cleaned.length * 3 / 4)
    var buffer = 0
    var bits = 0
    var o = 0
    for (c in cleaned) {
        val v = if (c.code < 128) table[c.code] else -1
        if (v < 0) continue
        buffer = (buffer shl 6) or v
        bits += 6
        if (bits >= 8) {
            bits -= 8
            out[o++] = ((buffer ushr bits) and 0xff).toByte()
        }
    }
    return if (o == out.size) out else out.copyOf(o)
}

fun decodeDataUri(uri: String): ByteArray? {
    val comma = uri.indexOf(',')
    if (!uri.startsWith("data:") || comma < 0) return null
    val meta = uri.substring(0, comma)
    val data = uri.substring(comma + 1)
    return if (meta.contains(";base64")) decodeBase64(data) else java.net.URLDecoder.decode(data, "UTF-8").toByteArray()
}

class GltfAccessorData(
    val count: Int,
    val components: Int,
    val floats: FloatArray,
    val ints: IntArray
) {
    fun vec2(index: Int): Pair<Float, Float> {
        val o = index * components
        return floats[o] to floats[o + 1]
    }

    fun vec3(index: Int): Vec3 {
        val o = index * components
        return Vec3(floats[o], floats[o + 1], floats[o + 2])
    }

    fun vec4(index: Int): Vec4 {
        val o = index * components
        return Vec4(floats[o], floats[o + 1], floats[o + 2], floats[o + 3])
    }

    fun mat4(index: Int): FloatArray {
        val out = FloatArray(16)
        val o = index * 16
        for (i in 0..15) out[i] = floats[o + i]
        return out
    }
}

/** Reads glTF 2.0 accessors from buffers/bufferViews. */
class GltfAccessorReader(
    private val json: JsonValue.Obj,
    private val buffers: List<ByteArray>
) {
    private val views = json.arr("bufferViews")?.items ?: emptyList()
    private val accessors = json.arr("accessors")?.items ?: emptyList()

    fun count(index: Int): Int = accessors.getOrNull(index)?.int("count") ?: 0

    fun read(index: Int): GltfAccessorData {
        val acc = accessors.getOrNull(index)
            ?: throw IllegalArgumentException("Missing accessor $index")
        val compType = acc.int("componentType")
            ?: throw IllegalArgumentException("Accessor $index has no componentType")
        val type = acc.str("type") ?: "SCALAR"
        val components = when (type) {
            "SCALAR" -> 1
            "VEC2" -> 2
            "VEC3" -> 3
            "VEC4" -> 4
            "MAT2" -> 4
            "MAT3" -> 9
            "MAT4" -> 16
            else -> throw IllegalArgumentException("Unknown accessor type $type")
        }
        val count = acc.int("count") ?: 0
        val normalized = acc.bool("normalized") ?: false
        val floats = FloatArray(count * components)
        val ints = IntArray(count * components)
        val viewIndex = acc.int("bufferView")
        if (viewIndex == null) {
            // Accessor without a bufferView is all zeros; sparse data is ignored.
            return GltfAccessorData(count, components, floats, ints)
        }
        val view = views.getOrNull(viewIndex)
            ?: throw IllegalArgumentException("Missing bufferView $viewIndex")
        val buffer = buffers.getOrNull(view.int("buffer") ?: -1)
            ?: throw IllegalArgumentException("Missing buffer for view $viewIndex")
        val viewOffset = view.int("byteOffset") ?: 0
        val accOffset = acc.int("byteOffset") ?: 0
        val compSize = when (compType) {
            5120, 5121 -> 1
            5122, 5123 -> 2
            5125, 5126 -> 4
            else -> throw IllegalArgumentException("Unknown componentType $compType")
        }
        val stride = view.int("byteStride") ?: (compSize * components)
        var offset = viewOffset + accOffset
        for (i in 0 until count) {
            for (c in 0 until components) {
                val o = offset + c * compSize
                val fi = i * components + c
                when (compType) {
                    5120 -> { val v = buffer[o].toInt(); ints[fi] = v; floats[fi] = v.toFloat() }
                    5121 -> { val v = buffer[o].toInt() and 0xff; ints[fi] = v; floats[fi] = if (normalized) v / 255f else v.toFloat() }
                    5122 -> {
                        val v = ((buffer[o].toInt() and 0xff) or (buffer[o + 1].toInt() shl 8)).toShort().toInt()
                        ints[fi] = v; floats[fi] = if (normalized) (v / 32767f).coerceIn(-1f, 1f) else v.toFloat()
                    }
                    5123 -> {
                        val v = (buffer[o].toInt() and 0xff) or ((buffer[o + 1].toInt() and 0xff) shl 8)
                        ints[fi] = v; floats[fi] = if (normalized) v / 65535f else v.toFloat()
                    }
                    5125 -> {
                        val v = Glb.readU32(buffer, o)
                        ints[fi] = v
                        floats[fi] = (v.toLong() and 0xffffffffL).toFloat()
                    }
                    5126 -> {
                        val v = Glb.readF32(buffer, o)
                        floats[fi] = v
                        ints[fi] = v.toInt()
                    }
                }
            }
            offset += stride
        }
        return GltfAccessorData(count, components, floats, ints)
    }
}

/** Accumulates binary data with 4-byte alignment and tracks bufferViews. */
class GltfBinaryBuilder {
    private val out = ByteArrayOutputStream()
    var byteLength: Int = 0
        private set

    fun align(alignment: Int = 4) {
        while (byteLength % alignment != 0) {
            out.write(0)
            byteLength++
        }
    }

    fun append(bytes: ByteArray, alignment: Int = 4): Pair<Int, Int> {
        align(alignment)
        val offset = byteLength
        out.write(bytes)
        byteLength += bytes.size
        return offset to bytes.size
    }

    fun bytes(): ByteArray = out.toByteArray()
}

fun floatsToBytes(values: FloatArray): ByteArray {
    val out = ByteArray(values.size * 4)
    for (i in values.indices) Glb.writeU32(out, i * 4, values[i].toRawBits())
    return out
}

fun ushortsToBytes(values: IntArray): ByteArray {
    val out = ByteArray(values.size * 2)
    for (i in values.indices) {
        out[i * 2] = (values[i] and 0xff).toByte()
        out[i * 2 + 1] = ((values[i] ushr 8) and 0xff).toByte()
    }
    return out
}

fun uintToBytes(values: IntArray): ByteArray {
    val out = ByteArray(values.size * 4)
    for (i in values.indices) Glb.writeU32(out, i * 4, values[i])
    return out
}

fun ubyteToBytes(values: IntArray): ByteArray = ByteArray(values.size) { (values[it] and 0xff).toByte() }

fun minMax(values: FloatArray, components: Int): Pair<FloatArray, FloatArray> {
    val min = FloatArray(components) { Float.MAX_VALUE }
    val max = FloatArray(components) { -Float.MAX_VALUE }
    var i = 0
    while (i < values.size) {
        for (c in 0 until components) {
            min[c] = kotlin.math.min(min[c], values[i + c])
            max[c] = max(max[c], values[i + c])
        }
        i += components
    }
    return min to max
}
