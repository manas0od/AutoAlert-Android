package com.example.data.imu

import kotlin.math.sqrt

/**
 * 6-Axis Madgwick AHRS (Attitude and Heading Reference System) filter.
 *
 * Fuses Accelerometer + Gyroscope data into a drift-compensated unit quaternion
 * without requiring a magnetometer.
 *
 * Vehicle Frame Convention:
 *  +X = vehicle RIGHT
 *  +Y = vehicle UP (gravity points in +Y direction when vehicle is resting level)
 *  +Z = vehicle FRONT
 *  -Z = vehicle REAR
 *
 * Key Properties:
 *  - No Euler angle gimbal lock or singularity at ±90° or 180° (supports full 360° rollover/inverted).
 *  - Fast gyro dynamic response with steady accelerometer gravity drift compensation.
 *  - Accelerometer rejection threshold during high vibration / sudden acceleration shocks.
 */
class MadgwickAhrs(
    var beta: Float = 0.08f
) {
    var orientation: Quaternion = Quaternion.IDENTITY
        private set

    /**
     * Resets the filter quaternion to identity or a given initial orientation.
     */
    fun reset(initialOrientation: Quaternion = Quaternion.IDENTITY) {
        orientation = initialOrientation.normalize()
    }

    /**
     * Update filter with 6-axis IMU readings:
     * @param gx Gyroscope X in radians/second
     * @param gy Gyroscope Y in radians/second
     * @param gz Gyroscope Z in radians/second
     * @param ax Accelerometer X in g (or m/s^2)
     * @param ay Accelerometer Y in g (or m/s^2)
     * @param az Accelerometer Z in g (or m/s^2)
     * @param dt Elapsed time in seconds since previous sample
     */
    fun update(
        gx: Float, gy: Float, gz: Float,
        ax: Float, ay: Float, az: Float,
        dt: Float
    ): Quaternion {
        if (dt <= 0.0f || dt > 1.0f) {
            return orientation // Skip abnormal or negative delta time
        }

        var q0 = orientation.w
        var q1 = orientation.x
        var q2 = orientation.y
        var q3 = orientation.z

        // Rate of change of quaternion from gyroscope: qDot = 0.5 * q * [0, gx, gy, gz]
        var qDot1 = 0.5f * (-q1 * gx - q2 * gy - q3 * gz)
        var qDot2 = 0.5f * ( q0 * gx + q2 * gz - q3 * gy)
        var qDot3 = 0.5f * ( q0 * gy - q1 * gz + q3 * gx)
        var qDot4 = 0.5f * ( q0 * gz + q1 * gy - q2 * gx)

        // Accelerometer magnitude check
        val aNormSq = ax * ax + ay * ay + az * az
        // Accelerometer correction is only applied if gravity vector is reasonable (0.2g to 2.2g)
        if (aNormSq > 0.04f && aNormSq < 4.84f) {
            val aNorm = sqrt(aNormSq)
            val invA = 1.0f / aNorm
            val nax = ax * invA
            val nay = ay * invA
            val naz = az * invA

            // Estimated gravity vector in body coordinates for +Y UP world reference:
            // v = q^-1 * [0, 1, 0] * q
            // vx = 2 * (q1*q2 - q0*q3)
            // vy = q0^2 - q1^2 + q2^2 - q3^2
            // vz = 2 * (q2*q3 + q0*q1)
            val ex = 2.0f * (q1 * q2 - q0 * q3) - nax
            val ey = (q0 * q0 - q1 * q1 + q2 * q2 - q3 * q3) - nay
            val ez = 2.0f * (q2 * q3 + q0 * q1) - naz

            // Gradient descent step: s = J^T * e
            var s0 = -2.0f * q3 * ex + 2.0f * q0 * ey + 2.0f * q1 * ez
            var s1 =  2.0f * q2 * ex - 2.0f * q1 * ey + 2.0f * q0 * ez
            var s2 =  2.0f * q1 * ex + 2.0f * q2 * ey + 2.0f * q3 * ez
            var s3 = -2.0f * q0 * ex - 2.0f * q3 * ey + 2.0f * q2 * ez

            val sNormSq = s0 * s0 + s1 * s1 + s2 * s2 + s3 * s3
            if (sNormSq > 1e-8f) {
                val invS = 1.0f / sqrt(sNormSq)
                s0 *= invS
                s1 *= invS
                s2 *= invS
                s3 *= invS

                // Apply feedback step: qDot = qDot_omega - beta * s_hat
                qDot1 -= beta * s0
                qDot2 -= beta * s1
                qDot3 -= beta * s2
                qDot4 -= beta * s3
            }
        }

        // Integrate rate of change to yield quaternion
        q0 += qDot1 * dt
        q1 += qDot2 * dt
        q2 += qDot3 * dt
        q3 += qDot4 * dt

        // Normalize quaternion
        orientation = Quaternion(q0, q1, q2, q3).normalize()
        return orientation
    }
}
