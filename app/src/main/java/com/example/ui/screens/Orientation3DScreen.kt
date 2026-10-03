package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.imu.ImuConnectionStatus
import com.example.data.imu.ImuStreamManager
import com.example.data.imu.MountingPreset
import com.example.data.imu.Quaternion
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.model3d.AutoRickshaw3DViewport
import com.example.ui.model3d.AutoRickshawModel
import com.example.ui.model3d.GlbModelLoader
import com.example.ui.model3d.Polygon3D
import com.example.ui.theme.AutoAmberContainer
import com.example.ui.theme.AutoAmberPrimary
import com.example.ui.theme.AutoBackground
import com.example.ui.theme.AutoOnSurface
import com.example.ui.theme.AutoOnSurfaceVariant
import com.example.ui.theme.AutoStatusCancelled
import com.example.ui.theme.AutoStatusCompleted
import com.example.ui.theme.AutoSurfaceHigh
import com.example.ui.theme.AutoSurfaceHighest
import java.util.Locale

@Composable
fun Orientation3DScreen(
    deviceIp: String,
    streamManager: ImuStreamManager,
    onUpdateDeviceIp: (String) -> Unit
) {
    val context = LocalContext.current
    val connectionStatus by streamManager.connectionStatus.collectAsState()
    val statusMessage by streamManager.statusMessage.collectAsState()
    val telemetry by streamManager.telemetry.collectAsState()

    var isLiveImuMode by remember { mutableStateOf(true) }
    var cameraAzimuth by remember { mutableFloatStateOf(35f) }
    var cameraElevation by remember { mutableFloatStateOf(22f) }
    var cameraZoom by remember { mutableFloatStateOf(3.2f) }

    // Manual mode test orientation
    var manualOrientation by remember { mutableStateOf(Quaternion.IDENTITY) }

    var showMountingDialog by remember { mutableStateOf(false) }
    var showIpDialog by remember { mutableStateOf(false) }
    var customIpInput by remember { mutableStateOf(deviceIp) }

    // Check if custom GLB model is available in assets
    val loadedGlbPolygons: List<Polygon3D>? = remember {
        GlbModelLoader.loadGlbFromAsset(context, "models/auto_rickshaw.glb")
    }
    val activePolygons = loadedGlbPolygons ?: remember { AutoRickshawModel.createModel() }

    // Start streaming when screen opens, stop when screen leaves
    LaunchedEffect(deviceIp) {
        if (deviceIp.isNotBlank()) {
            streamManager.startStreaming(deviceIp)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            streamManager.stopStreaming()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("orientation_3d_screen")
    ) {
        // -------------------------------------------------------------
        // HEADER: Status Pill & Reconnect
        // -------------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "3D Auto Orientation",
                    color = AutoAmberPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (loadedGlbPolygons != null) "Using Asset GLB Model" else "Kerala Auto 3D Model",
                    color = AutoOnSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            // Connection Status Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(
                        when (connectionStatus) {
                            ImuConnectionStatus.STREAMING -> AutoStatusCompleted.copy(alpha = 0.15f)
                            ImuConnectionStatus.CONNECTING -> AutoAmberContainer.copy(alpha = 0.25f)
                            else -> AutoSurfaceHigh
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = when (connectionStatus) {
                            ImuConnectionStatus.STREAMING -> AutoStatusCompleted
                            ImuConnectionStatus.CONNECTING -> AutoAmberPrimary
                            else -> AutoOnSurfaceVariant.copy(alpha = 0.4f)
                        },
                        shape = RoundedCornerShape(50.dp)
                    )
                    .clickable { showIpDialog = true }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                when (connectionStatus) {
                                    ImuConnectionStatus.STREAMING -> AutoStatusCompleted.copy(alpha = pulseAlpha)
                                    ImuConnectionStatus.CONNECTING -> AutoAmberPrimary
                                    else -> AutoStatusCancelled
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (connectionStatus) {
                            ImuConnectionStatus.STREAMING -> "LIVE IMU (${telemetry.sampleRateHz} Hz)"
                            ImuConnectionStatus.CONNECTING -> "CONNECTING..."
                            ImuConnectionStatus.DISCONNECTED -> "OFFLINE"
                            ImuConnectionStatus.ERROR -> "UNREACHABLE"
                        },
                        color = when (connectionStatus) {
                            ImuConnectionStatus.STREAMING -> AutoStatusCompleted
                            ImuConnectionStatus.CONNECTING -> AutoAmberPrimary
                            else -> AutoOnSurfaceVariant
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // -------------------------------------------------------------
        // MODE TOGGLE: Live IMU vs Manual 3D Orbit
        // -------------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AutoSurfaceHighest)
                .padding(4.dp)
        ) {
            // Live IMU Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isLiveImuMode) AutoAmberContainer else Color.Transparent)
                    .clickable { isLiveImuMode = true }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = if (isLiveImuMode) AutoAmberPrimary else AutoOnSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Live IMU Mode",
                        color = if (isLiveImuMode) AutoAmberPrimary else AutoOnSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Manual Orbit Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (!isLiveImuMode) AutoAmberContainer else Color.Transparent)
                    .clickable { isLiveImuMode = false }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = if (!isLiveImuMode) AutoAmberPrimary else AutoOnSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Manual 3D Orbit",
                        color = if (!isLiveImuMode) AutoAmberPrimary else AutoOnSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // -------------------------------------------------------------
        // 3D VIEWPORT CONTAINER
        // -------------------------------------------------------------
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.05f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AutoRickshaw3DViewport(
                    orientation = if (isLiveImuMode) telemetry.smoothedOrientation else manualOrientation,
                    cameraAzimuth = cameraAzimuth,
                    cameraElevation = cameraElevation,
                    cameraZoom = cameraZoom,
                    isManualMode = !isLiveImuMode,
                    onOrbitChange = { az, el ->
                        cameraAzimuth = az
                        cameraElevation = el
                    },
                    onZoomChange = { zoom ->
                        cameraZoom = zoom
                    },
                    modelPolygons = activePolygons
                )

                // Quick Camera View Switchers (Top-Right overlay)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    CameraViewPill("3/4 Iso") {
                        cameraAzimuth = 35f
                        cameraElevation = 22f
                        cameraZoom = 3.2f
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    CameraViewPill("Top") {
                        cameraAzimuth = 0f
                        cameraElevation = 78f
                        cameraZoom = 3.5f
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    CameraViewPill("Side") {
                        cameraAzimuth = 90f
                        cameraElevation = 10f
                        cameraZoom = 3.2f
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    CameraViewPill("Front") {
                        cameraAzimuth = 0f
                        cameraElevation = 10f
                        cameraZoom = 3.2f
                    }
                }

                // Touch hint overlay in bottom-left
                Text(
                    text = if (isLiveImuMode) "Pinch to zoom • Drag to orbit view" else "Drag to rotate view • Pinch to zoom",
                    color = AutoOnSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // -------------------------------------------------------------
        // CALIBRATION & RECENTER CONTROLS
        // -------------------------------------------------------------
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = "Sensor Calibration & Alignment",
                    color = AutoAmberPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Calibrate Level Ground Button
                    GlassButton(
                        text = "Calibrate Level",
                        icon = Icons.Default.CompassCalibration,
                        onClick = { streamManager.calibrateLevel() },
                        modifier = Modifier.weight(1f)
                    )

                    // Zero Yaw Heading Button
                    GlassButton(
                        text = "Zero Yaw",
                        icon = Icons.Default.RestartAlt,
                        onClick = { streamManager.zeroYaw() },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Mounting Preset Selector Button
                    GlassButton(
                        text = "Mount: ${streamManager.calibration.preset.name.take(10)}...",
                        icon = Icons.Default.SettingsSuggest,
                        onClick = { showMountingDialog = true },
                        modifier = Modifier.weight(1f)
                    )

                    // IP / Connection Setup Button
                    GlassButton(
                        text = "IP: $deviceIp",
                        icon = Icons.Default.Refresh,
                        onClick = { showIpDialog = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // -------------------------------------------------------------
        // REAL-TIME ORIENTATION TELEMETRY CARD
        // -------------------------------------------------------------
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vehicle Orientation & Attitude",
                        color = AutoAmberPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Status Badge (Normal, Steep Incline, Rollover)
                    val statusText = when {
                        telemetry.isInverted -> "🚨 INVERTED / ROLLOVER"
                        telemetry.totalTiltDeg > 35f -> "⚠️ DANGEROUS TILT"
                        telemetry.totalTiltDeg > 18f -> "INCLINE"
                        else -> "LEVEL / UPRIGHT"
                    }
                    val statusColor = when {
                        telemetry.isInverted -> AutoStatusCancelled
                        telemetry.totalTiltDeg > 35f -> AutoStatusCancelled
                        telemetry.totalTiltDeg > 18f -> AutoAmberPrimary
                        else -> AutoStatusCompleted
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(statusColor.copy(alpha = 0.2f))
                            .border(1.dp, statusColor, RoundedCornerShape(50.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Euler Angles Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AngleMetric(
                        label = "Roll (Tilt R/L)",
                        value = String.format(Locale.US, "%+.1f°", telemetry.rollDeg),
                        color = if (kotlin.math.abs(telemetry.rollDeg) > 30f) AutoStatusCancelled else AutoOnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    AngleMetric(
                        label = "Pitch (Nose Up/Dn)",
                        value = String.format(Locale.US, "%+.1f°", telemetry.pitchDeg),
                        color = if (kotlin.math.abs(telemetry.pitchDeg) > 30f) AutoStatusCancelled else AutoOnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    AngleMetric(
                        label = "Yaw (Heading)",
                        value = String.format(Locale.US, "%.1f°", (telemetry.yawDeg + 360f) % 360f),
                        color = AutoOnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    AngleMetric(
                        label = "Total Tilt",
                        value = String.format(Locale.US, "%.1f°", telemetry.totalTiltDeg),
                        color = if (telemetry.totalTiltDeg > 35f) AutoStatusCancelled else AutoAmberPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Raw Sensor Diagnostics (Accel & Gyro)
                Text(
                    text = "Raw MPU6500 Telemetry (${telemetry.transport})",
                    color = AutoOnSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = String.format(Locale.US, "Accel: X:%+.2f Y:%+.2f Z:%+.2f g", telemetry.ax, telemetry.ay, telemetry.az),
                        color = AutoOnSurfaceVariant,
                        fontSize = 10.sp
                    )
                    Text(
                        text = String.format(Locale.US, "Gyro: X:%+.0f Y:%+.0f Z:%+.0f °/s", telemetry.gx, telemetry.gy, telemetry.gz),
                        color = AutoOnSurfaceVariant,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Packets: ${telemetry.packetCount} • Rate: ${telemetry.sampleRateHz} Hz",
                        color = AutoOnSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                    Text(
                        text = "6-Axis Madgwick Fusion (Quaternion)",
                        color = AutoAmberPrimary.copy(alpha = 0.8f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------
    // MOUNTING PRESET DIALOG
    // -------------------------------------------------------------
    if (showMountingDialog) {
        AlertDialog(
            onDismissRequest = { showMountingDialog = false },
            title = {
                Text(
                    text = "MPU Mounting Orientation",
                    color = AutoAmberPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Select how the physical MPU6500 sensor PCB is mounted relative to the vehicle frame (+X=Right, +Y=Up, +Z=Front):",
                        color = AutoOnSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    MountingPreset.entries.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    streamManager.setMountingPreset(preset)
                                    showMountingDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = streamManager.calibration.preset == preset,
                                onClick = {
                                    streamManager.setMountingPreset(preset)
                                    showMountingDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = AutoAmberPrimary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = preset.displayName,
                                    color = AutoOnSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = preset.description,
                                    color = AutoOnSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMountingDialog = false }) {
                    Text("Close", color = AutoAmberPrimary)
                }
            },
            containerColor = Color(0xFF1E2228)
        )
    }

    // -------------------------------------------------------------
    // IP / CONNECTION DIALOG
    // -------------------------------------------------------------
    if (showIpDialog) {
        AlertDialog(
            onDismissRequest = { showIpDialog = false },
            title = {
                Text(
                    text = "ESP8266 Device Connection",
                    color = AutoAmberPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter the local IP address of your AutoAlert ESP8266 module on the local Wi-Fi network:",
                        color = AutoOnSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = customIpInput,
                        onValueChange = { customIpInput = it },
                        label = { Text("Device IP or Hostname") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AutoAmberPrimary,
                            unfocusedBorderColor = AutoOnSurfaceVariant.copy(alpha = 0.5f),
                            focusedTextColor = AutoOnSurface,
                            unfocusedTextColor = AutoOnSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Status: $statusMessage",
                        color = AutoAmberPrimary,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleaned = customIpInput.trim()
                        if (cleaned.isNotBlank()) {
                            onUpdateDeviceIp(cleaned)
                            streamManager.startStreaming(cleaned)
                        }
                        showIpDialog = false
                    }
                ) {
                    Text("Connect", color = AutoAmberPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showIpDialog = false }) {
                    Text("Cancel", color = AutoOnSurfaceVariant)
                }
            },
            containerColor = Color(0xFF1E2228)
        )
    }
}

@Composable
private fun AngleMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = AutoOnSurfaceVariant,
            fontSize = 9.sp,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CameraViewPill(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xCC212529))
            .border(1.dp, Color(0x44FFA000), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
