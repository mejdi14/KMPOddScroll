package io.github.mejdi14.oddscroll.internal

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A unit quaternion, held mutably and turned in place.
 *
 * A sphere that is dragged from any direction has no sensible pair of angles to
 * store: yaw and pitch would gimbal-lock at the poles and, worse, would make a
 * diagonal drag arrive as two separate turns. A quaternion takes the drag as
 * what it is — one rotation about one axis — and composes cleanly with the next
 * one whatever direction it comes from.
 */
internal class Quat(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var w: Float = 1f,
) {
    fun normalize() {
        val length = sqrt(x * x + y * y + z * z + w * w)
        if (length <= 1e-9f) {
            x = 0f; y = 0f; z = 0f; w = 1f
            return
        }
        x /= length; y /= length; z /= length; w /= length
    }

    /** Turns [angle] radians about a unit axis, before whatever turn is held. */
    fun turn(ax: Float, ay: Float, az: Float, angle: Float) {
        val s = sin(angle / 2f)
        val bx = ax * s
        val by = ay * s
        val bz = az * s
        val bw = cos(angle / 2f)
        val nx = bw * x + bx * w + by * z - bz * y
        val ny = bw * y - bx * z + by * w + bz * x
        val nz = bw * z + bx * y - by * x + bz * w
        val nw = bw * w - bx * x - by * y - bz * z
        x = nx; y = ny; z = nz; w = nw
        normalize()
    }

    /** Rotates a vector, writing x, y, z into [out]. */
    fun rotate(vx: Float, vy: Float, vz: Float, out: FloatArray) {
        val tx = 2f * (y * vz - z * vy)
        val ty = 2f * (z * vx - x * vz)
        val tz = 2f * (x * vy - y * vx)
        out[0] = vx + w * tx + (y * tz - z * ty)
        out[1] = vy + w * ty + (z * tx - x * tz)
        out[2] = vz + w * tz + (x * ty - y * tx)
    }

    /** Only the z of the rotated vector, for the many places that is all that is wanted. */
    fun rotatedZ(vx: Float, vy: Float, vz: Float): Float {
        val tx = 2f * (y * vz - z * vy)
        val ty = 2f * (z * vx - x * vz)
        val tz = 2f * (x * vy - y * vx)
        return vz + w * tz + (x * ty - y * tx)
    }
}

/**
 * Points spread as evenly over a sphere as a simple rule can manage, by walking
 * the golden angle while stepping down the axis. There is no exact answer for
 * an arbitrary count — the five platonic solids are the only perfect cases — and
 * this one has no seam, no pole cluster and no preferred direction, which is
 * what a sphere that can be dragged any way needs.
 */
internal fun fibonacciSphere(count: Int): FloatArray {
    val golden = PI_F * (3f - sqrt(5f))
    val points = FloatArray(count * 3)
    for (i in 0 until count) {
        val y = 1f - (2f * (i + 0.5f)) / count
        val ring = sqrt((1f - y * y).coerceAtLeast(0f))
        points[i * 3] = cos(golden * i) * ring
        points[i * 3 + 1] = y
        points[i * 3 + 2] = sin(golden * i) * ring
    }
    return points
}
