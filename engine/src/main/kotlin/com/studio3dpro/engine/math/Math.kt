package com.studio3dpro.engine.math

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

const val EPS = 1e-5f

fun clamp(v: Float, lo: Float, hi: Float): Float = max(lo, min(hi, v))
fun clamp01(v: Float): Float = clamp(v, 0f, 1f)
fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
fun smoothstep(t0: Float, t1: Float, x: Float): Float {
    val t = clamp01((x - t0) / (t1 - t0 + 1e-9f))
    return t * t * (3f - 2f * t)
}
fun degToRad(d: Float): Float = d * (Math.PI.toFloat() / 180f)
fun radToDeg(r: Float): Float = r * (180f / Math.PI.toFloat())

data class Vec2(val x: Float, val y: Float) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Float) = Vec2(x * s, y * s)
    fun length() = sqrt(x * x + y * y)
    fun normalized(): Vec2 {
        val l = length()
        return if (l < EPS) ZERO else Vec2(x / l, y / l)
    }

    companion object {
        val ZERO = Vec2(0f, 0f)
    }
}

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun div(s: Float) = Vec3(x / s, y / s, z / s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)
    infix fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x
    )
    fun lengthSq(): Float = x * x + y * y + z * z
    fun length(): Float = sqrt(lengthSq())
    fun normalized(): Vec3 {
        val l = length()
        return if (l < 1e-8f) ZERO else Vec3(x / l, y / l, z / l)
    }
    fun componentMin(o: Vec3) = Vec3(min(x, o.x), min(y, o.y), min(z, o.z))
    fun componentMax(o: Vec3) = Vec3(max(x, o.x), max(y, o.y), max(z, o.z))
    fun abs() = Vec3(abs(x), abs(y), abs(z))
    fun lerp(o: Vec3, t: Float) = this + (o - this) * t
    fun distanceTo(o: Vec3): Float = (this - o).length()
    val isFinite: Boolean
        get() = x.isFinite() && y.isFinite() && z.isFinite()
    fun toFloatArray() = floatArrayOf(x, y, z)
    fun maxComponent() = max(x, max(y, z))

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val ONE = Vec3(1f, 1f, 1f)
        val UP = Vec3(0f, 1f, 0f)
        val RIGHT = Vec3(1f, 0f, 0f)
        val FORWARD = Vec3(0f, 0f, -1f)
    }
}

data class Vec4(val x: Float, val y: Float, val z: Float, val w: Float) {
    operator fun plus(o: Vec4) = Vec4(x + o.x, y + o.y, z + o.z, w + o.w)
    operator fun minus(o: Vec4) = Vec4(x - o.x, y - o.y, z - o.z, w - o.w)
    operator fun times(s: Float) = Vec4(x * s, y * s, z * s, w * s)
    infix fun dot(o: Vec4) = x * o.x + y * o.y + z * o.z + w * o.w
    fun xyz() = Vec3(x, y, z)
    fun normalized(): Vec4 {
        val l = sqrt(x * x + y * y + z * z + w * w)
        return if (l < 1e-8f) Vec4(0f, 0f, 0f, 1f) else Vec4(x / l, y / l, z / l, w / l)
    }
    fun lerp(o: Vec4, t: Float) = Vec4(
        lerp(x, o.x, t), lerp(y, o.y, t), lerp(z, o.z, t), lerp(w, o.w, t)
    )

    companion object {
        val ZERO = Vec4(0f, 0f, 0f, 0f)
    }
}

data class Quat(val x: Float, val y: Float, val z: Float, val w: Float) {
    operator fun times(o: Quat) = Quat(
        w * o.x + x * o.w + y * o.z - z * o.y,
        w * o.y - x * o.z + y * o.w + z * o.x,
        w * o.z + x * o.y - y * o.x + z * o.w,
        w * o.w - x * o.x - y * o.y - z * o.z
    )

    fun normalized(): Quat {
        val l = sqrt(x * x + y * y + z * z + w * w)
        return if (l < 1e-8f) IDENTITY else Quat(x / l, y / l, z / l, w / l)
    }

    fun conjugate() = Quat(-x, -y, -z, w)

    fun rotate(v: Vec3): Vec3 {
        val q = normalized()
        val u = Vec3(q.x, q.y, q.z)
        val t = u cross v * 2f
        return v + t * q.w + (u cross t)
    }

    fun toMat4(): Mat4 = Mat4.fromQuat(this)

    fun toEulerDegrees(): Vec3 {
        val q = normalized()
        val sinr = 2f * (q.w * q.x + q.y * q.z)
        val cosr = 1f - 2f * (q.x * q.x + q.y * q.y)
        val roll = kotlin.math.atan2(sinr, cosr)
        val sinp = clamp(2f * (q.w * q.y - q.z * q.x), -1f, 1f)
        val pitch = kotlin.math.asin(sinp)
        val siny = 2f * (q.w * q.z + q.x * q.y)
        val cosy = 1f - 2f * (q.y * q.y + q.z * q.z)
        val yaw = kotlin.math.atan2(siny, cosy)
        return Vec3(radToDeg(roll), radToDeg(pitch), radToDeg(yaw))
    }

    fun angleTo(o: Quat): Float {
        val d = abs((this dot o).coerceIn(-1f, 1f))
        return 2f * acos(d)
    }

    infix fun dot(o: Quat): Float = x * o.x + y * o.y + z * o.z + w * o.w

    fun slerp(o: Quat, t: Float): Quat {
        var cosHalf = this dot o
        var target = o
        if (cosHalf < 0f) {
            target = Quat(-o.x, -o.y, -o.z, -o.w)
            cosHalf = -cosHalf
        }
        if (cosHalf > 0.9995f) {
            return Quat(
                lerp(x, target.x, t), lerp(y, target.y, t),
                lerp(z, target.z, t), lerp(w, target.w, t)
            ).normalized()
        }
        val half = acos(cosHalf.coerceIn(-1f, 1f))
        val sinHalf = sin(half)
        val a = sin((1f - t) * half) / sinHalf
        val b = sin(t * half) / sinHalf
        return Quat(
            x * a + target.x * b,
            y * a + target.y * b,
            z * a + target.z * b,
            w * a + target.w * b
        ).normalized()
    }

    companion object {
        val IDENTITY = Quat(0f, 0f, 0f, 1f)

        fun axisAngle(axis: Vec3, radians: Float): Quat {
            val n = axis.normalized()
            val h = radians * 0.5f
            val s = sin(h)
            return Quat(n.x * s, n.y * s, n.z * s, cos(h))
        }

        fun fromEulerDegrees(e: Vec3): Quat =
            fromEulerRadians(degToRad(e.x), degToRad(e.y), degToRad(e.z))

        /** Intrinsic XYZ rotation: R = Rz * Ry * Rx (roll, pitch, yaw). */
        fun fromEulerRadians(rx: Float, ry: Float, rz: Float): Quat {
            val cx = cos(rx * 0.5f); val sx = sin(rx * 0.5f)
            val cy = cos(ry * 0.5f); val sy = sin(ry * 0.5f)
            val cz = cos(rz * 0.5f); val sz = sin(rz * 0.5f)
            return Quat(
                sx * cy * cz - cx * sy * sz,
                cx * sy * cz + sx * cy * sz,
                cx * cy * sz - sx * sy * cz,
                cx * cy * cz + sx * sy * sz
            )
        }

        fun fromToRotation(from: Vec3, to: Vec3): Quat {
            val a = from.normalized()
            val b = to.normalized()
            val d = a dot b
            if (d > 0.999999f) return IDENTITY
            if (d < -0.999999f) {
                var axis = a cross Vec3.RIGHT
                if (axis.lengthSq() < 1e-6f) axis = a cross Vec3.UP
                return axisAngle(axis.normalized(), Math.PI.toFloat())
            }
            val c = a cross b
            return Quat(c.x, c.y, c.z, 1f + d).normalized()
        }

        fun lookRotation(forward: Vec3, up: Vec3 = Vec3.UP): Quat {
            val f = forward.normalized()
            val r = (up cross f).normalized()
            if (r.lengthSq() < 1e-8f) return IDENTITY
            val u = f cross r
            val m = Mat4.identity()
            m[0] = r.x; m[1] = r.y; m[2] = r.z
            m[4] = u.x; m[5] = u.y; m[6] = u.z
            m[8] = -f.x; m[9] = -f.y; m[10] = -f.z
            return fromMat4(m)
        }

        fun fromMat4(m: Mat4): Quat {
            val m00 = m[0]; val m01 = m[4]; val m02 = m[8]
            val m10 = m[1]; val m11 = m[5]; val m12 = m[9]
            val m20 = m[2]; val m21 = m[6]; val m22 = m[10]
            val tr = m00 + m11 + m22
            return if (tr > 0f) {
                val s = sqrt(tr + 1f) * 2f
                Quat((m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s, 0.25f * s)
            } else if (m00 > m11 && m00 > m22) {
                val s = sqrt(1f + m00 - m11 - m22) * 2f
                Quat(0.25f * s, (m01 + m10) / s, (m02 + m20) / s, (m21 - m12) / s)
            } else if (m11 > m22) {
                val s = sqrt(1f + m11 - m00 - m22) * 2f
                Quat((m01 + m10) / s, 0.25f * s, (m12 + m21) / s, (m02 - m20) / s)
            } else {
                val s = sqrt(1f + m22 - m00 - m11) * 2f
                Quat((m02 + m20) / s, (m12 + m21) / s, 0.25f * s, (m10 - m01) / s)
            }.normalized()
        }
    }
}

/**
 * Column-major 4x4 matrix, compatible with OpenGL and glTF.
 * m[column * 4 + row].
 */
class Mat4(val m: FloatArray = FloatArray(16)) {
    operator fun get(i: Int): Float = m[i]
    operator fun set(i: Int, v: Float) { m[i] = v }

    fun copy(): Mat4 = Mat4(m.copyOf())

    operator fun times(o: Mat4): Mat4 {
        val r = Mat4()
        for (c in 0..3) {
            for (row in 0..3) {
                var s = 0f
                for (k in 0..3) s += m[k * 4 + row] * o.m[c * 4 + k]
                r.m[c * 4 + row] = s
            }
        }
        return r
    }

    fun transformPoint(p: Vec3): Vec3 {
        val x = m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12]
        val y = m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13]
        val z = m[2] * p.x + m[6] * p.y + m[10] * p.z + m[14]
        val w = m[3] * p.x + m[7] * p.y + m[11] * p.z + m[15]
        return if (abs(w) > 1e-8f && abs(w - 1f) > 1e-8f) Vec3(x / w, y / w, z / w) else Vec3(x, y, z)
    }

    fun transformDirection(v: Vec3): Vec3 = Vec3(
        m[0] * v.x + m[4] * v.y + m[8] * v.z,
        m[1] * v.x + m[5] * v.y + m[9] * v.z,
        m[2] * v.x + m[6] * v.y + m[10] * v.z
    )

    fun translation(): Vec3 = Vec3(m[12], m[13], m[14])

    fun toFloatArray(): FloatArray = m.copyOf()

    fun inverse(): Mat4 {
        val inv = FloatArray(16)
        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] +
            m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10]
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] -
            m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10]
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] +
            m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9]
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] -
            m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9]
        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] -
            m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10]
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] +
            m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10]
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] -
            m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9]
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] +
            m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9]
        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] +
            m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6]
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] -
            m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6]
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] +
            m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5]
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] -
            m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5]
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] -
            m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6]
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] +
            m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6]
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] -
            m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5]
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] +
            m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5]
        var det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12]
        if (abs(det) < 1e-12f) return identity()
        det = 1f / det
        for (i in 0..15) inv[i] *= det
        return Mat4(inv)
    }

    fun decompose(): Triple<Vec3, Quat, Vec3> {
        val t = Vec3(m[12], m[13], m[14])
        val sx = Vec3(m[0], m[1], m[2]).length()
        val sy = Vec3(m[4], m[5], m[6]).length()
        val sz = Vec3(m[8], m[9], m[10]).length()
        val det = m[0] * (m[5] * m[10] - m[6] * m[9]) -
            m[4] * (m[1] * m[10] - m[2] * m[9]) +
            m[8] * (m[1] * m[6] - m[2] * m[5])
        val sign = if (det < 0f) -1f else 1f
        val rot = Mat4.identity()
        rot[0] = m[0] / (sx * sign); rot[1] = m[1] / (sx * sign); rot[2] = m[2] / (sx * sign)
        rot[4] = m[4] / sy; rot[5] = m[5] / sy; rot[6] = m[6] / sy
        rot[8] = m[8] / sz; rot[9] = m[9] / sz; rot[10] = m[10] / sz
        return Triple(t, Quat.fromMat4(rot), Vec3(sx * sign, sy, sz))
    }

    fun normalMatrix(): Mat4 {
        val inv = inverse()
        val n = Mat4.identity()
        n[0] = inv[0]; n[1] = inv[4]; n[2] = inv[8]
        n[4] = inv[1]; n[5] = inv[5]; n[6] = inv[9]
        n[8] = inv[2]; n[9] = inv[6]; n[10] = inv[10]
        return n
    }

    companion object {
        fun identity(): Mat4 {
            val m = Mat4()
            m[0] = 1f; m[5] = 1f; m[10] = 1f; m[15] = 1f
            return m
        }

        fun translation(t: Vec3): Mat4 {
            val m = identity()
            m[12] = t.x; m[13] = t.y; m[14] = t.z
            return m
        }

        fun rotation(q: Quat): Mat4 = fromQuat(q)

        fun fromQuat(q: Quat): Mat4 {
            val n = q.normalized()
            val x = n.x; val y = n.y; val z = n.z; val w = n.w
            val xx = x * x; val yy = y * y; val zz = z * z
            val xy = x * y; val xz = x * z; val yz = y * z
            val wx = w * x; val wy = w * y; val wz = w * z
            val m = Mat4()
            m[0] = 1f - 2f * (yy + zz); m[1] = 2f * (xy + wz); m[2] = 2f * (xz - wy)
            m[4] = 2f * (xy - wz); m[5] = 1f - 2f * (xx + zz); m[6] = 2f * (yz + wx)
            m[8] = 2f * (xz + wy); m[9] = 2f * (yz - wx); m[10] = 1f - 2f * (xx + yy)
            m[15] = 1f
            return m
        }

        fun scaling(s: Vec3): Mat4 {
            val m = identity()
            m[0] = s.x; m[5] = s.y; m[10] = s.z
            return m
        }

        fun trs(t: Vec3, r: Quat, s: Vec3): Mat4 =
            translation(t) * fromQuat(r) * scaling(s)

        fun perspective(fovYDeg: Float, aspect: Float, near: Float, far: Float): Mat4 {
            val f = 1f / kotlin.math.tan(degToRad(fovYDeg) * 0.5f)
            val m = Mat4()
            m[0] = f / aspect
            m[5] = f
            m[10] = (far + near) / (near - far)
            m[11] = -1f
            m[14] = 2f * far * near / (near - far)
            return m
        }

        fun ortho(left: Float, right: Float, bottom: Float, top: Float, near: Float, far: Float): Mat4 {
            val m = identity()
            m[0] = 2f / (right - left)
            m[5] = 2f / (top - bottom)
            m[10] = -2f / (far - near)
            m[12] = -(right + left) / (right - left)
            m[13] = -(top + bottom) / (top - bottom)
            m[14] = -(far + near) / (far - near)
            return m
        }

        fun lookAt(eye: Vec3, target: Vec3, up: Vec3 = Vec3.UP): Mat4 {
            val f = (target - eye).normalized()
            var s = f cross up
            if (s.lengthSq() < 1e-8f) s = f cross Vec3(0f, 0f, 1f)
            s = s.normalized()
            val u = s cross f
            val m = identity()
            m[0] = s.x; m[4] = s.y; m[8] = s.z
            m[1] = u.x; m[5] = u.y; m[9] = u.z
            m[2] = -f.x; m[6] = -f.y; m[10] = -f.z
            m[12] = -(s dot eye); m[13] = -(u dot eye); m[14] = (f dot eye)
            return m
        }

        fun fromFloatArray(data: FloatArray): Mat4 = Mat4(data.copyOf(16))
    }
}

data class Ray(val origin: Vec3, val direction: Vec3) {
    fun at(t: Float) = origin + direction * t

    fun intersectsTriangle(a: Vec3, b: Vec3, c: Vec3): Float? {
        val e1 = b - a
        val e2 = c - a
        val p = direction cross e2
        val det = e1 dot p
        if (abs(det) < 1e-8f) return null
        val invDet = 1f / det
        val tVec = origin - a
        val u = (tVec dot p) * invDet
        if (u < -1e-5f || u > 1.00001f) return null
        val q = tVec cross e1
        val v = (direction dot q) * invDet
        if (v < -1e-5f || u + v > 1.00001f) return null
        val t = (e2 dot q) * invDet
        return if (t > 1e-5f) t else null
    }

    fun intersectsAabb(box: Aabb): Boolean {
        var tmin = 0f
        var tmax = 1e30f
        for (axis in 0..2) {
            val o = when (axis) { 0 -> origin.x; 1 -> origin.y; else -> origin.z }
            val d = when (axis) { 0 -> direction.x; 1 -> direction.y; else -> direction.z }
            val mn = when (axis) { 0 -> box.min.x; 1 -> box.min.y; else -> box.min.z }
            val mx = when (axis) { 0 -> box.max.x; 1 -> box.max.y; else -> box.max.z }
            if (abs(d) < 1e-8f) {
                if (o < mn || o > mx) return false
            } else {
                var t1 = (mn - o) / d
                var t2 = (mx - o) / d
                if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
                tmin = max(tmin, t1)
                tmax = min(tmax, t2)
                if (tmin > tmax) return false
            }
        }
        return true
    }
}

data class Aabb(val min: Vec3, val max: Vec3) {
    val center: Vec3 get() = (min + max) * 0.5f
    val size: Vec3 get() = max - min
    val radius: Float get() = size.length() * 0.5f

    fun expand(p: Vec3): Aabb = Aabb(min.componentMin(p), max.componentMax(p))
    fun expand(o: Aabb): Aabb = Aabb(min.componentMin(o.min), max.componentMax(o.max))
    fun contains(p: Vec3): Boolean =
        p.x >= min.x && p.x <= max.x && p.y >= min.y && p.y <= max.y && p.z >= min.z && p.z <= max.z

    fun transformed(m: Mat4): Aabb {
        var b = Aabb(Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE),
            Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE))
        for (i in 0..7) {
            val p = Vec3(
                if (i and 1 == 0) min.x else max.x,
                if (i and 2 == 0) min.y else max.y,
                if (i and 4 == 0) min.z else max.z
            )
            b = b.expand(m.transformPoint(p))
        }
        return b
    }

    companion object {
        val EMPTY = Aabb(
            Vec3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE),
            Vec3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        )

        fun fromPoints(points: Iterable<Vec3>): Aabb {
            var b = EMPTY
            for (p in points) b = b.expand(p)
            return b
        }
    }
}
