package com.example.data.imu

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance, immutable 3D Unit Quaternion for orientation math and sensor fusion.
 *
 * Coordinate Convention for Vehicle:
 *  +X = vehicle RIGHT
 *  +Y = vehicle UP
 *  +Z = vehicle FRONT
 *  -Z = vehicle REAR
 */
data class Quaternion(
    val w: Float = 1.0f,
    val x: Float = 0.0f,
    val y: Float = 0.0f,
    val z: Float = 0.0f
) {
    companion object {
        val IDENTITY = Quaternion(1.0f, 0.0f, 0.0f, 0.0f)

        fun fromAxisAngle(axisX: Float, axisY: Float, axisZ: Float, angleRadians: Float): Quaternion {
            val halfAngle = angleRadians * 0.5f
            val s = sin(halfAngle)
            val len = sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ)
            if (len < 1e-6f) return IDENTITY
            val invLen = s / len
            return Quaternion(
                w = cos(halfAngle),
                x = axisX * invLen,
                y = axisY * invLen,
                z = axisZ * invLen
            ).normalize()
        }

        /**
         * Construct quaternion from Euler angles in degrees.
         * Roll about Z (front), Pitch about X (right), Yaw about Y (up).
         */
        fun fromEuler(rollDeg: Float, pitchDeg: Float, yawDeg: Float): Quaternion {
            val rollRad = Math.toRadians(rollDeg.toDouble()).toFloat() * 0.5f
            val pitchRad = Math.toRadians(pitchDeg.toDouble()).toFloat() * 0.5f
            val yawRad = Math.toRadians(yawDeg.toDouble()).toFloat() * 0.5f

            val cr = cos(rollRad)
            val sr = sin(rollRad)
            val cp = cos(pitchRad)
            val sp = sin(pitchRad)
            val cy = cos(yawRad)
            val sy = sin(yawRad)

            return Quaternion(
                w = cr * cp * cy + sr * sp * sy,
                x = cr * sp * cy + sr * cp * sy,
                y = cr * cp * sy - sr * sp * cy,
                z = sr * cp * cy - cr * sp * sy
            ).normalize()
        }
    }

    fun norm(): Float = sqrt(w * w + x * x + y * y + z * z)

    fun normalize(): Quaternion {
        val n = norm()
        return if (n > 1e-7f) {
            val inv = 1.0f / n
            Quaternion(w * inv, x * inv, y * inv, z * inv)
        } else {
            IDENTITY
        }
    }

    fun conjugate(): Quaternion = Quaternion(w, -x, -y, -z)

    fun inverse(): Quaternion {
        val n2 = w * w + x * x + y * y + z * z
        return if (n2 > 1e-7f) {
            val inv = 1.0f / n2
            Quaternion(w * inv, -x * inv, -y * inv, -z * inv)
        } else {
            IDENTITY
        }
    }

    /**
     * Hamilton product: q1 * q2
     */
    operator fun times(q: Quaternion): Quaternion {
        return Quaternion(
            w = w * q.w - x * q.x - y * q.y - z * q.z,
            x = w * q.x + x * q.w + y * q.z - z * q.y,
            y = w * q.y - x * q.z + y * q.w + z * q.x,
            z = w * q.z + x * q.y - y * q.x + z * q.w
        )
    }

    /**
     * Rotates a 3D vector v = (vx, vy, vz) by this quaternion: v' = q * v * q^-1.
     * Uses optimized Rodrigues formula without full matrix multiplication.
     */
    fun rotateVector(vx: Float, vy: Float, vz: Float): FloatArray {
        val qx2 = 2.0f * x
        val qy2 = 2.0f * y
        val qz2 = 2.0f * z

        val wx2 = qx2 * w
        val wy2 = qy2 * w
        val wz2 = qz2 * w

        val xx2 = qx2 * x
        val xy2 = qy2 * x
        val xz2 = qz2 * x

        val yy2 = qy2 * y
        val yz2 = qz2 * y
        val zz2 = qz2 * z

        val rx = vx * (1.0f - yy2 - zz2) + vy * (xy2 - wz2) + vz * (xz2 + wy2)
        val ry = vx * (xy2 + wz2) + vy * (1.0f - xx2 - zz2) + vz * (yz2 - wx2)
        val rz = vx * (xz2 - wy2) + vy * (yz2 + wx2) + vz * (1.0f - xx2 - yy2)

        return floatArrayOf(rx, ry, rz)
    }

    /**
     * Normalized linear interpolation (Nlerp) or spherical linear interpolation (Slerp)
     * between this quaternion and target quaternion [target] by parameter [t] in [0, 1].
     */
    fun slerp(target: Quaternion, t: Float): Quaternion {
        var cosOmega = w * target.w + x * target.x + y * target.y + z * target.z
        var targetW = target.w
        var targetX = target.x
        var targetY = target.y
        var targetZ = target.z

        // Take the shortest path on 4D sphere
        if (cosOmega < 0.0f) {
            cosOmega = -cosOmega
            targetW = -targetW
            targetX = -targetX
            targetY = -targetY
            targetZ = -targetZ
        }

        return if (cosOmega > 0.9995f) {
            // Nlerp fallback for near-parallel quaternions (avoids divide-by-zero)
            val invT = 1.0f - t
            Quaternion(
                w = invT * w + t * targetW,
                x = invT * x + t * targetX,
                y = invT * y + t * targetY,
                z = invT * z + t * targetZ
            ).normalize()
        } else {
            val omega = acos(cosOmega.coerceIn(-1.0f, 1.0f))
            val sinOmega = sin(omega)
            val s0 = sin((1.0f - t) * omega) / sinOmega
            val s1 = sin(t * omega) / sinOmega
            Quaternion(
                w = s0 * w + s1 * targetW,
                x = s0 * x + s1 * targetX,
                y = s0 * y + s1 * targetY,
                z = s0 * z + s1 * targetZ
            ).normalize()
        }
    }

    /**
     * 3x3 Rotation matrix in column-major order:
     * [0]=Rxx, [1]=Ryx, [2]=Rzx (Vehicle Right vector)
     * [3]=Rxy, [4]=Ryy, [5]=Rzy (Vehicle Up vector)
     * [6]=Rxz, [7]=Ryz, [8]=Rzz (Vehicle Front vector)
     */
    fun toRotationMatrix(): FloatArray {
        val qx2 = 2.0f * x
        val qy2 = 2.0f * y
        val qz2 = 2.0f * z

        val wx2 = qx2 * w
        val wy2 = qy2 * w
        val wz2 = qz2 * w

        val xx2 = qx2 * x
        val xy2 = qy2 * x
        val xz2 = qz2 * x

        val yy2 = qy2 * y
        val yz2 = qz2 * y
        val zz2 = qz2 * z

        return floatArrayOf(
            1.0f - yy2 - zz2, xy2 + wz2, xz2 - wy2,  // Column 0: Right
            xy2 - wz2, 1.0f - xx2 - zz2, yz2 + wx2,  // Column 1: Up
            xz2 + wy2, yz2 - wx2, 1.0f - xx2 - yy2   // Column 2: Front
        )
    }

    /**
     * Returns true if vehicle is upside-down / inverted (roll or pitch > 90°).
     * Specifically checks if the vehicle UP vector is pointing downwards in world coordinates.
     */
    fun isInverted(): Boolean {
        // Up vector Y component is Ryy = 1 - 2(x^2 + z^2)
        val ryy = 1.0f - 2.0f * (x * x + z * z)
        return ryy < 0.0f
    }

    /**
     * Computes the vehicle total tilt angle in degrees away from true vertical (0° = level, 90° = on side, 180° = inverted).
     */
    fun getTiltAngleDegrees(): Float {
        // Ryy is the cosine of the angle between vehicle UP and world UP
        val ryy = (1.0f - 2.0f * (x * x + z * z)).coerceIn(-1.0f, 1.0f)
        return Math.toDegrees(acos(ryy.toDouble())).toFloat()
    }

    /**
     * Extracts Roll, Pitch, and Yaw in degrees:
     *  Roll  = tilt to Right/Left (+ = tilt right, - = tilt left)
     *  Pitch = nose Up/Down (+ = nose up, - = nose down)
     *  Yaw   = heading relative to initial calibration (+ = clockwise turn)
     */
    fun toEulerAngles(): EulerAngles {
        val mat = toRotationMatrix()
        val rxx = mat[0]
        val ryx = mat[1]
        val rzx = mat[2]
        val rxy = mat[3]
        val ryy = mat[4]
        val rzy = mat[5]
        val rxz = mat[6]
        val ryz = mat[7]
        val rzz = mat[8]

        // Pitch around X axis
        val pitchRad = -asin(ryz.coerceIn(-1.0f, 1.0f))
        val rollRad = atan2(ryx.toDouble(), ryy.toDouble()).toFloat()
        val yawRad = atan2(rxz.toDouble(), rzz.toDouble()).toFloat()

        return EulerAngles(
            roll = Math.toDegrees(rollRad.toDouble()).toFloat(),
            pitch = Math.toDegrees(pitchRad.toDouble()).toFloat(),
            yaw = Math.toDegrees(yawRad.toDouble()).toFloat()
        )
    }
}

data class EulerAngles(
    val roll: Float,
    val pitch: Float,
    val yaw: Float
)
