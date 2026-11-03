package com.studio3dpro.app.render

import com.studio3dpro.engine.math.Aabb
import com.studio3dpro.engine.math.Mat4
import com.studio3dpro.engine.math.Ray
import com.studio3dpro.engine.math.Vec2
import com.studio3dpro.engine.math.Vec3
import com.studio3dpro.engine.math.clamp
import com.studio3dpro.engine.math.degToRad
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Orbit camera shared between the GL thread and touch input. */
class CameraState {
    @Volatile var target: Vec3 = Vec3(0f, 0.95f, 0f)
    @Volatile var distance: Float = 4.4f
    @Volatile var yawDeg: Float = 24f
    @Volatile var pitchDeg: Float = 11f
    @Volatile var fovDeg: Float = 38f

    fun eye(): Vec3 {
        val yaw = degToRad(yawDeg)
        val pitch = degToRad(pitchDeg)
        val cp = cos(pitch)
        return target + Vec3(sin(yaw) * cp, sin(pitch), cos(yaw) * cp) * distance
    }

    fun viewMatrix(): Mat4 = Mat4.lookAt(eye(), target, Vec3.UP)

    fun projectionMatrix(aspect: Float): Mat4 =
        Mat4.perspective(fovDeg, aspect.coerceAtLeast(0.2f), 0.02f, 250f)

    fun viewProjection(aspect: Float): Mat4 = projectionMatrix(aspect) * viewMatrix()

    fun rayFromScreen(x: Float, y: Float, width: Int, height: Int): Ray {
        val aspect = width.toFloat() / height.coerceAtLeast(1).toFloat()
        val inv = viewProjection(aspect).inverse()
        val nx = (x / width) * 2f - 1f
        val ny = 1f - (y / height) * 2f
        val near = inv.transformPoint(Vec3(nx, ny, -1f))
        val far = inv.transformPoint(Vec3(nx, ny, 1f))
        return Ray(near, (far - near).normalized())
    }

    fun projectPoint(p: Vec3, width: Int, height: Int): Vec3? {
        val aspect = width.toFloat() / height.coerceAtLeast(1).toFloat()
        val m = viewProjection(aspect)
        val x = m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12]
        val y = m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13]
        val w = m[3] * p.x + m[7] * p.y + m[11] * p.z + m[15]
        if (w <= 1e-5f) return null
        val ndcX = x / w
        val ndcY = y / w
        val screenX = (ndcX * 0.5f + 0.5f) * width
        val screenY = (1f - (ndcY * 0.5f + 0.5f)) * height
        return Vec3(screenX, screenY, w)
    }

    fun screenDistanceTo(p: Vec3, x: Float, y: Float, width: Int, height: Int): Float {
        val s = projectPoint(p, width, height) ?: return Float.MAX_VALUE
        return Vec2(s.x - x, s.y - y).length()
    }

    fun orbit(dx: Float, dy: Float) {
        yawDeg -= dx * 0.28f
        pitchDeg = clamp(pitchDeg + dy * 0.22f, -82f, 82f)
        if (yawDeg > 360f) yawDeg -= 360f
        if (yawDeg < -360f) yawDeg += 360f
    }

    fun pan(dx: Float, dy: Float, width: Int, height: Int) {
        val forward = (target - eye()).normalized()
        val right = (forward cross Vec3.UP).normalized()
        val up = right cross forward
        val worldPerPixel = (2f * distance * tan(degToRad(fovDeg) * 0.5f)) / height.coerceAtLeast(1)
        target = target - right * (dx * worldPerPixel) + up * (dy * worldPerPixel)
    }

    fun zoom(factor: Float) {
        distance = clamp(distance / factor, 0.25f, 60f)
    }

    fun frame(bounds: Aabb, animate: Boolean = true) {
        val radius = bounds.radius.coerceAtLeast(0.2f)
        target = bounds.center
        val wanted = (radius / sin(degToRad(fovDeg * 0.5f)) * 1.18f).coerceIn(0.8f, 50f)
        distance = if (animate) wanted else wanted
        pitchDeg = 8f
    }

    fun snapshot(): CameraSnapshot = CameraSnapshot(target, distance, yawDeg, pitchDeg, fovDeg)

    data class CameraSnapshot(
        val target: Vec3,
        val distance: Float,
        val yawDeg: Float,
        val pitchDeg: Float,
        val fovDeg: Float
    )
}
