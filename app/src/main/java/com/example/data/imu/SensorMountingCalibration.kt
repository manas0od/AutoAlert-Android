package com.example.data.imu

enum class MountingPreset(val displayName: String, val description: String) {
    STANDARD_FLAT(
        "Standard Flat (Z=Up, Y=Front, X=Right)",
        "MPU6500 PCB mounted horizontally on vehicle floor/seat, chip facing up."
    ),
    DIRECT_MATCH(
        "Direct Vehicle Frame (X=Right, Y=Up, Z=Front)",
        "MPU coordinate frame matches vehicle coordinate frame directly."
    ),
    DASHBOARD_VERTICAL(
        "Dashboard Bulkhead (Y=Up, Z=Front, -X=Right)",
        "MPU mounted vertically on the front dashboard or firewall."
    ),
    UNDER_SEAT_INVERTED(
        "Under-Seat Inverted (-Z=Up, Y=Front, -X=Right)",
        "MPU mounted upside down underneath the driver seat."
    ),
    SIDE_WALL(
        "Side Frame (X=Up, Y=Front, Z=Right)",
        "MPU mounted vertically on the vehicle side chassis rail."
    );

    companion object {
        fun fromName(name: String?): MountingPreset {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: STANDARD_FLAT
        }
    }
}

/**
 * Handles explicit coordinate transformation between physical MPU6500 sensor axes
 * and the target 3D Vehicle Coordinate Frame:
 *   +X = vehicle RIGHT
 *   +Y = vehicle UP
 *   +Z = vehicle FRONT
 *   -Z = vehicle REAR
 */
class SensorMountingCalibration(
    var preset: MountingPreset = MountingPreset.STANDARD_FLAT,
    var levelCalibrationOffset: Quaternion = Quaternion.IDENTITY,
    var yawOffsetDegrees: Float = 0.0f
) {
    /**
     * Transforms raw sensor 3D vector (accel or gyro) from physical MPU frame to Vehicle frame.
     */
    fun transformVector(sx: Float, sy: Float, sz: Float): FloatArray {
        return when (preset) {
            MountingPreset.STANDARD_FLAT -> {
                // PCB flat: Z is Up, Y is Front, X is Right
                floatArrayOf(sx, sz, sy)
            }
            MountingPreset.DIRECT_MATCH -> {
                // X = Right, Y = Up, Z = Front
                floatArrayOf(sx, sy, sz)
            }
            MountingPreset.DASHBOARD_VERTICAL -> {
                // Y = Up, Z = Front, -X = Right
                floatArrayOf(-sx, sy, sz)
            }
            MountingPreset.UNDER_SEAT_INVERTED -> {
                // -Z = Up, Y = Front, -X = Right
                floatArrayOf(-sx, -sz, sy)
            }
            MountingPreset.SIDE_WALL -> {
                // X = Up, Y = Front, Z = Right
                floatArrayOf(sz, sx, sy)
            }
        }
    }

    /**
     * Applies level ground zero-calibration and yaw offset to the fused orientation quaternion.
     */
    fun applyCalibration(fusedQuaternion: Quaternion): Quaternion {
        // Multiply by inverse of level reference: q_corrected = q_offset * q_fused
        val levelCorrected = (levelCalibrationOffset * fusedQuaternion).normalize()

        return if (kotlin.math.abs(yawOffsetDegrees) > 0.01f) {
            // Apply yaw correction around vehicle UP (+Y)
            val yawCorrection = Quaternion.fromAxisAngle(
                0.0f, 1.0f, 0.0f,
                Math.toRadians(-yawOffsetDegrees.toDouble()).toFloat()
            )
            (yawCorrection * levelCorrected).normalize()
        } else {
            levelCorrected
        }
    }

    /**
     * Calibrate current orientation as level ground reference.
     */
    fun calibrateLevelGround(currentFused: Quaternion) {
        // Find offset q_calib such that q_calib * currentFused = IDENTITY
        levelCalibrationOffset = currentFused.inverse()
        yawOffsetDegrees = 0.0f
    }

    /**
     * Zero current yaw heading.
     */
    fun zeroYawHeading(currentOrientation: Quaternion) {
        val euler = currentOrientation.toEulerAngles()
        yawOffsetDegrees += euler.yaw
    }

    fun resetCalibration() {
        levelCalibrationOffset = Quaternion.IDENTITY
        yawOffsetDegrees = 0.0f
    }
}
