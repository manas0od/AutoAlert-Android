package com.example

import com.example.data.imu.EulerAngles
import com.example.data.imu.ImuStreamManager
import com.example.data.imu.MadgwickAhrs
import com.example.data.imu.MountingPreset
import com.example.data.imu.Quaternion
import com.example.data.imu.SensorMountingCalibration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImuOrientationFusionTest {

    @Test
    fun testQuaternionIdentityAndNormalize() {
        val q = Quaternion.IDENTITY
        assertEquals(1.0f, q.w, 1e-5f)
        assertEquals(0.0f, q.x, 1e-5f)
        assertEquals(0.0f, q.y, 1e-5f)
        assertEquals(0.0f, q.z, 1e-5f)
        assertEquals(1.0f, q.norm(), 1e-5f)
        assertEquals(0.0f, q.getTiltAngleDegrees(), 1e-3f)
        assertFalse(q.isInverted())
    }

    @Test
    fun testVehicleCoordinateFrameRotations() {
        // Vehicle frame convention:
        // +X = vehicle RIGHT
        // +Y = vehicle UP
        // +Z = vehicle FRONT
        // -Z = vehicle REAR

        // Rotate 90 degrees around Z axis (Roll to right)
        val qRollRight90 = Quaternion.fromAxisAngle(0f, 0f, 1f, Math.toRadians(90.0).toFloat())
        val rotatedUp = qRollRight90.rotateVector(0f, 1f, 0f) // Original UP vector (0, 1, 0)
        // With +90° roll around Z, UP (0,1,0) should rotate towards -X (-1, 0, 0)
        assertEquals(-1.0f, rotatedUp[0], 1e-4f)
        assertEquals(0.0f, rotatedUp[1], 1e-4f)
        assertEquals(0.0f, rotatedUp[2], 1e-4f)

        // Rotate 180 degrees (Inverted / Rollover)
        val qInverted = Quaternion.fromAxisAngle(0f, 0f, 1f, Math.toRadians(180.0).toFloat())
        val upInverted = qInverted.rotateVector(0f, 1f, 0f)
        assertEquals(0.0f, upInverted[0], 1e-4f)
        assertEquals(-1.0f, upInverted[1], 1e-4f) // UP points DOWN (-Y)
        assertEquals(0.0f, upInverted[2], 1e-4f)
        assertTrue(qInverted.isInverted())
        assertEquals(180.0f, qInverted.getTiltAngleDegrees(), 0.1f)
    }

    @Test
    fun testEulerAnglesConversion() {
        val euler = EulerAngles(roll = 25.0f, pitch = -15.0f, yaw = 40.0f)
        val q = Quaternion.fromEuler(euler.roll, euler.pitch, euler.yaw)

        val recovered = q.toEulerAngles()
        assertEquals(25.0f, recovered.roll, 1.0f)
        assertEquals(-15.0f, recovered.pitch, 1.0f)
        assertEquals(40.0f, recovered.yaw, 1.0f)
    }

    @Test
    fun testMadgwickUprightConvergence() {
        val filter = MadgwickAhrs(beta = 0.1f)
        filter.reset()

        // Feed upright gravity (+Y = 1.0g, gx=gy=gz=0) over 50 steps
        for (i in 0 until 50) {
            filter.update(
                gx = 0f, gy = 0f, gz = 0f,
                ax = 0f, ay = 1f, az = 0f,
                dt = 0.02f
            )
        }

        val q = filter.orientation
        assertEquals(0.0f, q.getTiltAngleDegrees(), 2.0f)
        assertFalse(q.isInverted())
    }

    @Test
    fun testMountingPresetsTransformation() {
        val calib = SensorMountingCalibration()

        // Standard Flat: PCB lying flat with chip facing up (Z=Up, Y=Front, X=Right)
        calib.preset = MountingPreset.STANDARD_FLAT
        val v1 = calib.transformVector(1.0f, 2.0f, 3.0f) // sx=1(Right), sy=2(Front), sz=3(Up)
        assertEquals(1.0f, v1[0], 1e-5f) // X_V = Right
        assertEquals(3.0f, v1[1], 1e-5f) // Y_V = Up
        assertEquals(2.0f, v1[2], 1e-5f) // Z_V = Front

        // Direct Match: X=Right, Y=Up, Z=Front
        calib.preset = MountingPreset.DIRECT_MATCH
        val v2 = calib.transformVector(1.0f, 2.0f, 3.0f)
        assertEquals(1.0f, v2[0], 1e-5f)
        assertEquals(2.0f, v2[1], 1e-5f)
        assertEquals(3.0f, v2[2], 1e-5f)
    }

    @Test
    fun testZeroLevelCalibration() {
        val calib = SensorMountingCalibration()

        // Suppose sensor is mounted with a 10-degree initial pitch offset
        val initialTilted = Quaternion.fromAxisAngle(1f, 0f, 0f, Math.toRadians(10.0).toFloat())
        assertEquals(10.0f, initialTilted.getTiltAngleDegrees(), 0.5f)

        // Calibrate level
        calib.calibrateLevelGround(initialTilted)

        // Apply calibration to the same orientation
        val calibrated = calib.applyCalibration(initialTilted)
        assertEquals(0.0f, calibrated.getTiltAngleDegrees(), 0.01f)
    }

    @Test
    fun testImuJsonPacketParsing() {
        val testScope = TestScope(UnconfinedTestDispatcher())
        val manager = ImuStreamManager(testScope)

        val jsonPacket = """
            {
                "ax": 0.05,
                "ay": 0.98,
                "az": -0.02,
                "gx": 1.2,
                "gy": -0.5,
                "gz": 0.1,
                "t": 14250
            }
        """.trimIndent()

        manager.processImuJson(jsonPacket, transport = "SSE Stream")
        val data = manager.telemetry.value

        assertEquals(1L, data.packetCount)
        assertFalse(data.isInverted)
        assertTrue(data.totalTiltDeg < 15.0f)
        assertNotNull(data.fusedOrientation)
        assertNotNull(data.smoothedOrientation)
    }

    @Test
    fun testInvertedRolloverJsonPacketParsing() {
        val testScope = TestScope(UnconfinedTestDispatcher())
        val manager = ImuStreamManager(testScope)

        // Vehicle upside down: gravity reaction points along -Y (inverted)
        // With pre-fused quaternion or inverted gravity
        val invertedJson = """
            {
                "qw": 0.0,
                "qx": 0.0,
                "qy": 0.0,
                "qz": 1.0,
                "ax": 0.0,
                "ay": -1.0,
                "az": 0.0
            }
        """.trimIndent()

        // Feed continuous inverted packets to allow smoothing filter to converge
        repeat(5) {
            manager.processImuJson(invertedJson, transport = "Live IMU")
        }
        val data = manager.telemetry.value

        assertTrue(data.fusedOrientation.isInverted())
        assertEquals(180.0f, data.fusedOrientation.getTiltAngleDegrees(), 1.0f)
        assertTrue(data.isInverted)
        assertTrue(data.totalTiltDeg > 90.0f)
    }
}
