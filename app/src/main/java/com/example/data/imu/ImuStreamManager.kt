package com.example.data.imu

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.BufferedReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

enum class ImuConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    STREAMING,
    ERROR
}

data class ImuTelemetryData(
    val ax: Float = 0.0f,
    val ay: Float = 1.0f,
    val az: Float = 0.0f,
    val gx: Float = 0.0f,
    val gy: Float = 0.0f,
    val gz: Float = 0.0f,
    val rawQuaternion: Quaternion? = null,
    val fusedOrientation: Quaternion = Quaternion.IDENTITY,
    val smoothedOrientation: Quaternion = Quaternion.IDENTITY,
    val rollDeg: Float = 0.0f,
    val pitchDeg: Float = 0.0f,
    val yawDeg: Float = 0.0f,
    val totalTiltDeg: Float = 0.0f,
    val isInverted: Boolean = false,
    val sampleRateHz: Int = 0,
    val packetCount: Long = 0L,
    val latencyMs: Long = 0L,
    val transport: String = "SSE Stream",
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Manages continuous real-time IMU telemetry networking with ESP8266,
 * sensor fusion via 6-axis Madgwick filter, mounting transformation,
 * zero-calibration, and jitter smoothing.
 */
class ImuStreamManager(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "ImuStreamManager"
        private const val UDP_PORT = 4210
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    val calibration = SensorMountingCalibration()
    private val madgwick = MadgwickAhrs(beta = 0.08f)

    private val _connectionStatus = MutableStateFlow(ImuConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ImuConnectionStatus> = _connectionStatus.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready to connect")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _telemetry = MutableStateFlow(ImuTelemetryData())
    val telemetry: StateFlow<ImuTelemetryData> = _telemetry.asStateFlow()

    private var streamJob: Job? = null
    private var lastSampleTimeNs: Long = 0L
    private val packetCounter = AtomicLong(0L)
    private var packetsInLastSecond = 0
    private var lastRateCalcTimeMs = System.currentTimeMillis()
    private var currentHz = 0

    private var targetOrientation = Quaternion.IDENTITY
    private var renderedSmoothed = Quaternion.IDENTITY

    /**
     * Connects to the ESP8266 at [deviceIp] and starts receiving continuous IMU telemetry.
     */
    fun startStreaming(deviceIp: String) {
        stopStreaming()

        val cleanIp = cleanIpOrHostname(deviceIp)
        _connectionStatus.value = ImuConnectionStatus.CONNECTING
        _statusMessage.value = "Connecting to ESP8266 at $cleanIp..."

        streamJob = scope.launch(Dispatchers.IO) {
            var connected = false

            // Strategy 1: Attempt Server-Sent Events (SSE) Stream at http://<ip>/imu/stream
            try {
                Log.d(TAG, "Attempting SSE stream: http://$cleanIp/imu/stream")
                connected = startSseStream(cleanIp)
            } catch (e: Exception) {
                Log.w(TAG, "SSE stream failed: ${e.message}")
            }

            // Strategy 2: If SSE not supported or disconnected, fallback to HTTP Fast Polling at http://<ip>/imu
            if (!connected && isActive) {
                try {
                    Log.d(TAG, "Falling back to HTTP polling: http://$cleanIp/imu")
                    startHttpPolling(cleanIp)
                } catch (e: Exception) {
                    Log.w(TAG, "HTTP polling failed: ${e.message}")
                }
            }
        }
    }

    fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        _connectionStatus.value = ImuConnectionStatus.DISCONNECTED
        _statusMessage.value = "Streaming stopped"
    }

    /**
     * Connect to SSE stream at http://<ip>/imu/stream
     */
    private suspend fun startSseStream(ip: String): Boolean {
        val url = "http://$ip/imu/stream"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .build()

        try {
            val response: Response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return false
            }

            _connectionStatus.value = ImuConnectionStatus.STREAMING
            _statusMessage.value = "Live SSE Stream Active ($ip)"

            val reader = BufferedReader(response.body?.charStream())
            var line: String? = null

            while (scope.isActive && reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: continue
                if (currentLine.startsWith("data:")) {
                    val jsonStr = currentLine.removePrefix("data:").trim()
                    if (jsonStr.isNotEmpty()) {
                        processImuJson(jsonStr, transport = "HTTP SSE Stream")
                    }
                }
            }
            response.close()
            return true
        } catch (e: Exception) {
            Log.d(TAG, "SSE read loop exited: ${e.message}")
            return false
        }
    }

    /**
     * Fallback HTTP Polling at http://<ip>/imu
     */
    private suspend fun startHttpPolling(ip: String) {
        val url = "http://$ip/imu"
        val pollClient = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()

        var consecutiveFailures = 0

        while (scope.isActive) {
            val startTime = System.currentTimeMillis()
            try {
                val request = Request.Builder().url(url).get().build()
                pollClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.isNotEmpty()) {
                            consecutiveFailures = 0
                            _connectionStatus.value = ImuConnectionStatus.STREAMING
                            _statusMessage.value = "Live HTTP Polling ($ip)"
                            processImuJson(body, transport = "HTTP Polling", requestLatency = System.currentTimeMillis() - startTime)
                        }
                    } else {
                        consecutiveFailures++
                    }
                }
            } catch (e: Exception) {
                consecutiveFailures++
            }

            if (consecutiveFailures > 5) {
                _connectionStatus.value = ImuConnectionStatus.ERROR
                _statusMessage.value = "Unable to reach ESP8266 at $ip. Check local Wi-Fi."
                delay(2000L)
            } else {
                delay(33L) // ~30 Hz polling rate
            }
        }
    }

    /**
     * Parses incoming JSON packet, executes sensor fusion and coordinate transform.
     */
    fun processImuJson(jsonStr: String, transport: String = "Live IMU", requestLatency: Long = 0L) {
        try {
            val json = JSONObject(jsonStr)

            // Acceleration in g
            val rawAx = json.optDouble("ax", json.optDouble("acc_x", 0.0)).toFloat()
            val rawAy = json.optDouble("ay", json.optDouble("acc_y", 1.0)).toFloat()
            val rawAz = json.optDouble("az", json.optDouble("acc_z", 0.0)).toFloat()

            // Gyroscope in degrees/second
            val rawGx = json.optDouble("gx", json.optDouble("gyro_x", 0.0)).toFloat()
            val rawGy = json.optDouble("gy", json.optDouble("gyro_y", 0.0)).toFloat()
            val rawGz = json.optDouble("gz", json.optDouble("gyro_z", 0.0)).toFloat()

            // Optional pre-fused quaternion if ESP8266 has DMP onboard
            val hasQuat = json.has("qw") || json.has("w")
            val rawQuat = if (hasQuat) {
                Quaternion(
                    w = json.optDouble("qw", json.optDouble("w", 1.0)).toFloat(),
                    x = json.optDouble("qx", json.optDouble("x", 0.0)).toFloat(),
                    y = json.optDouble("qy", json.optDouble("y", 0.0)).toFloat(),
                    z = json.optDouble("qz", json.optDouble("z", 0.0)).toFloat()
                ).normalize()
            } else null

            // Compute delta time in seconds
            val nowNs = System.nanoTime()
            val dt = if (lastSampleTimeNs > 0) {
                ((nowNs - lastSampleTimeNs) / 1_000_000_000.0f).coerceIn(0.001f, 0.2f)
            } else {
                0.033f
            }
            lastSampleTimeNs = nowNs

            // 1. Transform raw sensor measurements by mounting preset into Vehicle Frame:
            //    +X = Right, +Y = Up, +Z = Front
            val vehicleAccel = calibration.transformVector(rawAx, rawAy, rawAz)
            val vehicleGyro = calibration.transformVector(rawGx, rawGy, rawGz)

            // 2. Perform 6-axis Madgwick sensor fusion:
            // Convert gyro from deg/s to rad/s for integration
            val gxRad = Math.toRadians(vehicleGyro[0].toDouble()).toFloat()
            val gyRad = Math.toRadians(vehicleGyro[1].toDouble()).toFloat()
            val gzRad = Math.toRadians(vehicleGyro[2].toDouble()).toFloat()

            val fused = if (rawQuat != null) {
                rawQuat
            } else {
                madgwick.update(
                    gx = gxRad, gy = gyRad, gz = gzRad,
                    ax = vehicleAccel[0], ay = vehicleAccel[1], az = vehicleAccel[2],
                    dt = dt
                )
            }

            // 3. Apply level ground zero-offset calibration & yaw offset:
            val calibrated = calibration.applyCalibration(fused)
            targetOrientation = calibrated

            // 4. Smooth orientation for jitter-free rendering:
            // Adaptive smoothing factor: faster response during rapid movement, smoother when stationary
            val gyroMag = kotlin.math.sqrt(vehicleGyro[0]*vehicleGyro[0] + vehicleGyro[1]*vehicleGyro[1] + vehicleGyro[2]*vehicleGyro[2])
            val slerpAlpha = (0.25f + (gyroMag / 200f)).coerceIn(0.20f, 0.85f)
            renderedSmoothed = renderedSmoothed.slerp(targetOrientation, slerpAlpha)

            // 5. Update Telemetry metrics
            val euler = renderedSmoothed.toEulerAngles()
            val tiltDeg = renderedSmoothed.getTiltAngleDegrees()
            val inverted = renderedSmoothed.isInverted()

            // Update Hz calculation
            val count = packetCounter.incrementAndGet()
            packetsInLastSecond++
            val nowMs = System.currentTimeMillis()
            if (nowMs - lastRateCalcTimeMs >= 1000) {
                currentHz = packetsInLastSecond
                packetsInLastSecond = 0
                lastRateCalcTimeMs = nowMs
            }

            _telemetry.value = ImuTelemetryData(
                ax = vehicleAccel[0],
                ay = vehicleAccel[1],
                az = vehicleAccel[2],
                gx = vehicleGyro[0],
                gy = vehicleGyro[1],
                gz = vehicleGyro[2],
                rawQuaternion = rawQuat,
                fusedOrientation = calibrated,
                smoothedOrientation = renderedSmoothed,
                rollDeg = euler.roll,
                pitchDeg = euler.pitch,
                yawDeg = euler.yaw,
                totalTiltDeg = tiltDeg,
                isInverted = inverted,
                sampleRateHz = currentHz,
                packetCount = count,
                latencyMs = requestLatency,
                transport = transport,
                timestampMs = nowMs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error processing IMU JSON: ${e.message}")
        }
    }

    /**
     * Zero-level ground calibration.
     */
    fun calibrateLevel() {
        calibration.calibrateLevelGround(madgwick.orientation)
        _statusMessage.value = "Zero Level Ground calibrated"
    }

    /**
     * Zero heading / yaw.
     */
    fun zeroYaw() {
        calibration.zeroYawHeading(renderedSmoothed)
        _statusMessage.value = "Yaw heading zeroed"
    }

    /**
     * Change mounting preset.
     */
    fun setMountingPreset(preset: MountingPreset) {
        calibration.preset = preset
        calibration.resetCalibration()
        madgwick.reset()
        _statusMessage.value = "Mounting set to: ${preset.displayName}"
    }

    private fun cleanIpOrHostname(input: String): String {
        var clean = input.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .removeSuffix("/")
            .trim()
        if (clean.isEmpty() || clean.equals("autoalert.local", ignoreCase = true) || clean.equals("autoalert", ignoreCase = true)) {
            clean = "192.168.4.1"
        }
        return clean
    }
}
