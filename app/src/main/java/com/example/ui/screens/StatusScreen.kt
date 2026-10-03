package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BatteryResult
import com.example.data.Esp8266ApiClient
import com.example.data.PingResult
import com.example.data.formatBatteryAge
import com.example.ui.components.CircularBatteryGauge
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.SimulationBadge
import com.example.ui.theme.AutoAmberContainer
import com.example.ui.theme.AutoAmberPrimary
import com.example.ui.theme.AutoOnSurface
import com.example.ui.theme.AutoOnSurfaceVariant
import com.example.ui.theme.AutoStatusCancelled
import com.example.ui.theme.AutoStatusCompleted
import com.example.ui.theme.AutoSurfaceHighest
import kotlinx.coroutines.launch

@Composable
fun StatusScreen(
    deviceIp: String,
    ntfyTopic: String,
    apiClient: Esp8266ApiClient,
    batteryResult: BatteryResult? = null,
    isCheckingBattery: Boolean = false,
    lastKnownBatteryPercent: Int? = null,
    lastKnownBatteryTimestamp: Long? = null,
    onCheckBattery: () -> Unit = {},
    isSimulationMode: Boolean = false,
    onOpenWifiSetup: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var isPinging by remember { mutableStateOf(false) }
    var pingResult by remember { mutableStateOf<PingResult?>(null) }
    var isAdvancedExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("status_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Device Status",
                color = AutoAmberPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            if (isSimulationMode) {
                SimulationBadge()
            }
        }

        // Ping ESP8266 Device Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Router,
                            contentDescription = null,
                            tint = AutoAmberPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Your AutoAlert Device",
                                color = AutoOnSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "IP: $deviceIp",
                                color = AutoOnSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (pingResult?.success == true) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AutoStatusCompleted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                GlassButton(
                    text = if (isPinging) "Checking Connection..." else "Check Connection",
                    icon = Icons.Default.Refresh,
                    onClick = {
                        isPinging = true
                        scope.launch {
                            pingResult = apiClient.pingDevice(deviceIp, isSimulated = isSimulationMode)
                            isPinging = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTagStr = "ping_button"
                )

                if (pingResult != null) {
                    val isConnected = pingResult?.success == true
                    val statusColor = if (isConnected) AutoStatusCompleted else AutoStatusCancelled
                    val statusIcon = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Cancel
                    val statusLabel = if (isConnected) "Connected" else "Not Reachable"

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(statusColor.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                GlassButton(
                    text = "Configure Wi-Fi (USB / AP)",
                    icon = Icons.Default.Usb,
                    onClick = onOpenWifiSetup,
                    modifier = Modifier.fillMaxWidth(),
                    testTagStr = "status_wifi_setup_button"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Battery Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = AutoAmberPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Battery",
                                color = AutoOnSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Current charge status",
                                color = AutoOnSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                GlassButton(
                    text = if (isCheckingBattery) "Checking Battery..." else "Check Battery",
                    icon = Icons.Default.Refresh,
                    onClick = onCheckBattery,
                    modifier = Modifier.fillMaxWidth(),
                    testTagStr = "battery_check_button"
                )

                val hasLiveReading = batteryResult != null && batteryResult.success && batteryResult.batteryPercent != null
                val livePercent = if (hasLiveReading) batteryResult?.batteryPercent else null
                val effectivePercent = livePercent ?: lastKnownBatteryPercent
                val isUsingCached = !hasLiveReading && lastKnownBatteryPercent != null

                if (effectivePercent != null) {
                    val percent = effectivePercent
                    val isLow = percent < 20
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularBatteryGauge(
                            batteryPercent = percent,
                            size = 56.dp,
                            strokeWidth = 5.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = if (hasLiveReading) {
                                    if (isLow) "Low Battery ($percent%)" else "Good Charge ($percent%)"
                                } else {
                                    "$percent% - ${formatBatteryAge(lastKnownBatteryTimestamp)}"
                                },
                                color = if (isLow) AutoStatusCancelled else if (hasLiveReading) AutoStatusCompleted else AutoAmberPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when {
                                    isUsingCached -> if (isLow) "Low battery warning from last alert" else "Cached status from last alert"
                                    isLow -> "Please plug in to recharge soon"
                                    else -> "Device is ready for your shifts"
                                },
                                color = AutoOnSurfaceVariant,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                if (!hasLiveReading && batteryResult != null && batteryResult.message.isNotBlank()) {
                    Text(
                        text = batteryResult.message,
                        color = AutoOnSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Notification Service Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SignalCellular4Bar,
                            contentDescription = null,
                            tint = AutoAmberPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Notification Service",
                                color = AutoOnSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (ntfyTopic.isNotBlank()) "Active & ready for calls" else "Not configured",
                                color = if (ntfyTopic.isNotBlank()) AutoStatusCompleted else AutoOnSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Collapsible Advanced section
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isAdvancedExpanded = !isAdvancedExpanded }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isAdvancedExpanded) "Hide Advanced Details" else "Show Advanced Details",
                        color = AutoAmberPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isAdvancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = AutoAmberPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                AnimatedVisibility(visible = isAdvancedExpanded) {
                    Column(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AutoSurfaceHighest.copy(alpha = 0.5f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Topic: ${if (ntfyTopic.isNotBlank()) ntfyTopic else "Not paired"}",
                            color = AutoOnSurfaceVariant,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Device IP: $deviceIp",
                            color = AutoOnSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

