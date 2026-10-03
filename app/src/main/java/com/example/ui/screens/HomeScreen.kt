package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.data.BatteryResult
import com.example.data.Esp8266ApiClient
import com.example.data.NotificationEntity
import com.example.data.NtfyManager
import com.example.data.formatBatteryAge
import com.example.ui.components.CircularBatteryGauge
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassReplyButton
import com.example.ui.components.SimulationBadge
import com.example.ui.components.StatusBadge
import com.example.ui.components.glassmorphic
import com.example.ui.theme.AutoAmberContainer
import com.example.ui.theme.AutoAmberPrimary
import com.example.ui.theme.AutoBackground
import com.example.ui.theme.AutoGlassBorder
import com.example.ui.theme.AutoOnSurface
import com.example.ui.theme.AutoOnSurfaceVariant
import com.example.ui.theme.AutoStatusCancelled
import com.example.ui.theme.AutoStatusCompleted
import com.example.ui.theme.AutoStatusPending
import com.example.ui.theme.AutoSurfaceHigh
import com.example.ui.theme.AutoSurfaceHighest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    driverPhone: String,
    ntfyTopic: String,
    deviceIp: String,
    notifications: List<NotificationEntity>,
    isAvailable: Boolean,
    batteryResult: BatteryResult? = null,
    isCheckingBattery: Boolean = false,
    lastKnownBatteryPercent: Int? = null,
    lastKnownBatteryTimestamp: Long? = null,
    isSimulationMode: Boolean = false,
    targetNotificationId: Long? = null,
    onAvailableChange: (Boolean) -> Unit,
    onCheckBattery: () -> Unit = {},
    onUpdateStatus: (id: Long, status: String) -> Unit,
    onSendReply: ((id: Long, reply: String, onResult: (Boolean) -> Unit) -> Unit)? = null,
    onTriggerTestCall: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateTo3D: (() -> Unit)? = null
) {
    var isAdvancedExpanded by remember { mutableStateOf(false) }
    var selectedNotificationForAction by remember { mutableStateOf<NotificationEntity?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Automatically check live device battery and dismiss system notifications to reset icon badge count on load
    LaunchedEffect(Unit) {
        onCheckBattery()
        NtfyManager.dismissAllNotifications(context)
    }

    val startOfDay = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }

    val todayNotifications = remember(notifications) {
        notifications.filter { it.timestamp >= startOfDay }
    }
    val todayTotal = todayNotifications.size
    val todayCompleted = todayNotifications.count { 
        it.status.equals("completed", ignoreCase = true) || it.replyStatus?.equals("coming", ignoreCase = true) == true 
    }
    val todayPending = todayNotifications.count { 
        it.status.equals("pending", ignoreCase = true) && it.replyStatus == null 
    }
    val todayRate = if (todayTotal > 0) ((todayCompleted.toFloat() / todayTotal) * 100).toInt() else 100

    val urgentPendingRequest = remember(notifications, targetNotificationId) {
        if (targetNotificationId != null) {
            notifications.firstOrNull { it.id == targetNotificationId }
        } else {
            notifications.firstOrNull { it.status.equals("pending", ignoreCase = true) && it.replyStatus == null }
        }
    }

    // Auto-scroll when targeted by deep-link notification
    LaunchedEffect(targetNotificationId) {
        if (targetNotificationId != null) {
            listState.animateScrollToItem(0)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AutoBackground)
            .testTag("home_screen")
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 100.dp)
        ) {
            // Simulation Mode Compact Badge
            if (isSimulationMode) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        SimulationBadge()
                    }
                }
            }

            // Driver Greeting & Availability Toggle Status Card
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = AutoAmberContainer.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hello, Driver",
                                color = AutoAmberPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Registered Phone: ${driverPhone.ifBlank { "KL-07 Auto Driver" }}",
                                color = AutoOnSurfaceVariant,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Text(
                                text = if (isAvailable) "Status: Online for Service Calls" else "Status: Offline",
                                color = if (isAvailable) AutoStatusCompleted else AutoOnSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Switch(
                            checked = isAvailable,
                            onCheckedChange = onAvailableChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AutoAmberPrimary,
                                checkedTrackColor = AutoAmberContainer.copy(alpha = 0.5f),
                                uncheckedThumbColor = AutoOnSurfaceVariant,
                                uncheckedTrackColor = AutoSurfaceHigh
                            ),
                            modifier = Modifier.testTag("availability_toggle")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // IoT Device Status & Battery Meter Card
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (ntfyTopic.isNotBlank()) AutoStatusCompleted else AutoStatusCancelled)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "IoT Button Device Status",
                                    color = AutoOnSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.SignalCellular4Bar,
                                    contentDescription = null,
                                    tint = AutoAmberPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (ntfyTopic.isNotBlank()) "ntfy.sh Active" else "Offline",
                                    color = if (ntfyTopic.isNotBlank()) AutoAmberPrimary else AutoStatusCancelled,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Battery Level Display with Circular Progress Gauge from live device state or cached ntfy alert tag
                        val hasLiveReading = batteryResult != null && batteryResult.success && batteryResult.batteryPercent != null
                        val livePercent = if (hasLiveReading) batteryResult?.batteryPercent else null
                        val effectiveBattery = livePercent ?: lastKnownBatteryPercent
                        val isUsingCached = !hasLiveReading && lastKnownBatteryPercent != null

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                CircularBatteryGauge(
                                    batteryPercent = effectiveBattery ?: 0,
                                    size = 48.dp,
                                    strokeWidth = 4.5.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (effectiveBattery != null) "Battery: $effectiveBattery%" else "Battery Status",
                                        color = AutoOnSurface,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = when {
                                            isCheckingBattery -> "Checking live ESP8266..."
                                            hasLiveReading && livePercent != null && livePercent < 20 -> "Low battery (Recharge)"
                                            hasLiveReading -> "Normal charge (${batteryResult?.voltage?.let { "%.2fV".format(it) } ?: "Good"})"
                                            isUsingCached -> "$lastKnownBatteryPercent% - ${formatBatteryAge(lastKnownBatteryTimestamp)}"
                                            else -> "Live sync on stand network"
                                        },
                                        color = if ((effectiveBattery ?: 100) < 20) AutoStatusCancelled else AutoOnSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            TextButton(
                                onClick = onCheckBattery,
                                enabled = !isCheckingBattery,
                                modifier = Modifier.testTag("check_battery_button")
                            ) {
                                if (isCheckingBattery) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = AutoAmberPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = AutoAmberPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isCheckingBattery) "Checking..." else "Check Battery",
                                    color = AutoAmberPrimary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (batteryResult != null && batteryResult.message.isNotBlank()) {
                            Text(
                                text = batteryResult?.message ?: "",
                                color = AutoOnSurfaceVariant,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Collapsible Advanced section for ntfy topic & local device IP
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isAdvancedExpanded = !isAdvancedExpanded }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isAdvancedExpanded) "Hide Advanced Details" else "Show Advanced Details",
                                color = AutoAmberPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (isAdvancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = AutoAmberPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        AnimatedVisibility(visible = isAdvancedExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AutoSurfaceHighest.copy(alpha = 0.5f))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "ntfy Topic: ${if (ntfyTopic.isNotBlank()) ntfyTopic else "Not paired"}",
                                    color = AutoOnSurfaceVariant,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "Local Device IP: $deviceIp",
                                    color = AutoOnSurfaceVariant,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 3D Auto-Rickshaw Orientation Card
            item {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateTo3D?.invoke() }
                        .testTag("home_card_3d_orientation")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AutoAmberContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sensors,
                                    contentDescription = null,
                                    tint = AutoAmberPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "3D Auto Orientation",
                                    color = AutoOnSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Real-time MPU6500 6-Axis Telemetry & Rollover",
                                    color = AutoOnSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = AutoAmberPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Today's Operational Summary (Core Dashboard Focus)
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Today's Summary",
                                color = AutoOnSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = SimpleDateFormat("EEE, MMM dd", Locale.getDefault()).format(Date()),
                                color = AutoAmberPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Metric counters row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SummaryMetricTile(
                                label = "Total Calls",
                                value = todayTotal.toString(),
                                modifier = Modifier.weight(1f)
                            )
                            SummaryMetricTile(
                                label = "Completed",
                                value = todayCompleted.toString(),
                                valueColor = AutoStatusCompleted,
                                modifier = Modifier.weight(1f)
                            )
                            SummaryMetricTile(
                                label = "Pending",
                                value = todayPending.toString(),
                                valueColor = if (todayPending > 0) AutoAmberPrimary else AutoOnSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            SummaryMetricTile(
                                label = "Rate",
                                value = "$todayRate%",
                                valueColor = AutoAmberContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (todayTotal == 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AutoSurfaceHighest)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(AutoAmberPrimary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "No requests yet today — you'll see them here once your device is active",
                                    color = AutoOnSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Urgent / Active Request Banner (if pending request or deep-linked target exists)
            if (urgentPendingRequest != null) {
                item {
                    Text(
                        text = "Active Service Request",
                        color = AutoAmberPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    RequestCard(
                        notification = urgentPendingRequest,
                        onClick = { selectedNotificationForAction = urgentPendingRequest },
                        onSendReply = onSendReply,
                        isTargeted = urgentPendingRequest.id == targetNotificationId
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Quick Actions Banner & Navigation to History
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassCard(
                        modifier = Modifier.weight(1f),
                        onClick = onTriggerTestCall,
                        backgroundColor = AutoAmberContainer.copy(alpha = 0.18f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = AutoAmberPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Simulate Request",
                                    color = AutoAmberPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Trigger IoT test call",
                                    color = AutoOnSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // Activity Overview Section Header with direct link to History
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Activity Log",
                        color = AutoAmberPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    TextButton(
                        onClick = onNavigateToHistory
                    ) {
                        Text(
                            text = "View History (${notifications.size})",
                            color = AutoAmberPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = AutoAmberPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Activity Log items / Empty State
            if (notifications.isEmpty()) {
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp, horizontal = 16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(AutoSurfaceHighest)
                                    .border(1.dp, AutoGlassBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsNone,
                                    contentDescription = null,
                                    tint = AutoAmberPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "No requests yet — you'll see them here once your device is active",
                                color = AutoOnSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Text(
                                text = "Incoming passenger calls will trigger instant alerts and auto-log here.",
                                color = AutoOnSurfaceVariant,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            } else {
                // Show compact preview of the 2 most recent non-urgent items, directing to History for full browsing
                val previewList = notifications.filter { it.id != urgentPendingRequest?.id }.take(2)
                items(previewList) { item ->
                    RequestCard(
                        notification = item,
                        onClick = { selectedNotificationForAction = item },
                        onSendReply = onSendReply,
                        isTargeted = item.id == targetNotificationId
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onNavigateToHistory,
                        backgroundColor = AutoSurfaceHighest
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = AutoAmberPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Browse all ${notifications.size} logged requests",
                                    color = AutoOnSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = AutoAmberPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Action Sheet Dialog for updating status of a request
        if (selectedNotificationForAction != null) {
            val item = selectedNotificationForAction!!
            AlertDialog(
                onDismissRequest = { selectedNotificationForAction = null },
                containerColor = AutoSurfaceHigh,
                titleContentColor = AutoAmberPrimary,
                textContentColor = AutoOnSurface,
                title = { Text(text = item.title) },
                text = {
                    Column {
                        Text("Location: ${item.locationLabel}", fontSize = 14.sp)
                        Text("Message: ${item.message}", fontSize = 13.sp, color = AutoOnSurfaceVariant)
                        Text("Current Status: ${item.status}", fontSize = 12.sp, color = AutoAmberPrimary)
                    }
                },
                confirmButton = {
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    onUpdateStatus(item.id, "Completed")
                                    selectedNotificationForAction = null
                                }
                            ) {
                                Text("Mark Completed", color = AutoStatusCompleted)
                            }

                            TextButton(
                                onClick = {
                                    onUpdateStatus(item.id, "Cancelled")
                                    selectedNotificationForAction = null
                                }
                            ) {
                                Text("Cancel", color = AutoStatusCancelled)
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedNotificationForAction = null }) {
                        Text("Close", color = AutoOnSurfaceVariant)
                    }
                }
            )
        }
    }
}

@Composable
fun RequestCard(
    notification: NotificationEntity,
    onClick: () -> Unit,
    onSendReply: ((notificationId: Long, reply: String, onResult: (Boolean) -> Unit) -> Unit)? = null,
    isTargeted: Boolean = false
) {
    val isCancelled = notification.status.lowercase() == "cancelled"
    val isCompleted = notification.status.lowercase() == "completed"
    val isLowBattery = notification.status.lowercase().contains("battery") || notification.title.lowercase().contains("battery")
    val isServiceRequest = !isLowBattery

    val context = LocalContext.current
    var isSendingReply by remember { mutableStateOf<String?>(null) }
    val ageMs = remember(notification.timestamp) { System.currentTimeMillis() - notification.timestamp }
    val isExpired = ageMs >= 5 * 60 * 1000

    val highlightAnim = remember { Animatable(0f) }

    LaunchedEffect(isTargeted) {
        if (isTargeted) {
            repeat(3) {
                highlightAnim.animateTo(1f, animationSpec = tween(400))
                highlightAnim.animateTo(0.2f, animationSpec = tween(400))
            }
            highlightAnim.animateTo(0f, animationSpec = tween(400))
        } else {
            highlightAnim.snapTo(0f)
        }
    }

    val highlightVal = highlightAnim.value
    val cardBg = when {
        isLowBattery -> AutoStatusCancelled.copy(alpha = 0.12f)
        highlightVal > 0.01f -> AutoAmberContainer.copy(alpha = 0.35f * highlightVal + 0.15f)
        else -> AutoSurfaceHigh.copy(alpha = 0.4f)
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("request_card_${notification.id}"),
        onClick = onClick,
        backgroundColor = cardBg,
        borderColor = if (highlightVal > 0.01f) AutoAmberPrimary.copy(alpha = highlightVal) else AutoGlassBorder
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Icon Bubble
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(AutoSurfaceHighest)
                        .border(1.dp, AutoGlassBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCancelled) Icons.Default.LocationOff else Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = when {
                            isCancelled -> AutoStatusCancelled.copy(alpha = 0.7f)
                            isLowBattery -> AutoStatusCancelled
                            isCompleted -> AutoOnSurfaceVariant
                            else -> AutoAmberContainer
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Text Info
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (notification.requestId.isNotBlank()) "Request ${notification.requestId}" else notification.title,
                            color = AutoOnSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        StatusBadge(status = notification.status)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = notification.locationLabel,
                            color = AutoOnSurfaceVariant,
                            fontSize = 12.sp
                        )

                        Text(
                            text = formatRelativeTime(notification.timestamp),
                            color = AutoOnSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = AutoOnSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }

            if (isServiceRequest) {
                Spacer(modifier = Modifier.height(10.dp))

                when {
                    notification.replyStatus != null -> {
                        val isComing = notification.replyStatus.equals("coming", ignoreCase = true)
                        val tagColor = if (isComing) AutoStatusCompleted else AutoStatusCancelled
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(tagColor.copy(alpha = 0.12f))
                                .border(1.dp, tagColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = tagColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "You replied: ${if (isComing) "Coming" else "Busy"}",
                                color = tagColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    isExpired -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(AutoSurfaceHighest)
                                .border(1.dp, AutoGlassBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TimerOff,
                                contentDescription = null,
                                tint = AutoOnSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Expired",
                                color = AutoOnSurfaceVariant.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GlassReplyButton(
                                text = "Coming",
                                icon = Icons.Default.Check,
                                color = AutoStatusCompleted,
                                isLoading = isSendingReply == "coming",
                                enabled = isSendingReply == null && onSendReply != null,
                                onClick = {
                                    isSendingReply = "coming"
                                    onSendReply?.invoke(notification.id, "coming") { success ->
                                        isSendingReply = null
                                        if (!success) {
                                            Toast.makeText(context, "Failed to send reply — check connection", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                testTagStr = "reply_coming_button_${notification.id}"
                            )

                            GlassReplyButton(
                                text = "Busy",
                                icon = Icons.Default.Close,
                                color = AutoStatusCancelled,
                                isLoading = isSendingReply == "busy",
                                enabled = isSendingReply == null && onSendReply != null,
                                onClick = {
                                    isSendingReply = "busy"
                                    onSendReply?.invoke(notification.id, "busy") { success ->
                                        isSendingReply = null
                                        if (!success) {
                                            Toast.makeText(context, "Failed to send reply — check connection", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                testTagStr = "reply_busy_button_${notification.id}"
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatRelativeTime(timestampMs: Long): String {
    val diffSec = (System.currentTimeMillis() - timestampMs) / 1000
    return when {
        diffSec < 60 -> "Just now"
        diffSec < 3600 -> "${diffSec / 60} mins ago"
        diffSec < 86400 -> "${diffSec / 3600} hours ago"
        else -> SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(timestampMs))
    }
}

@Composable
fun SummaryMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = AutoAmberPrimary
) {
    Column(
        modifier = modifier
            .glassmorphic(
                shape = RoundedCornerShape(12.dp),
                tintColor = Color.White.copy(alpha = 0.12f),
                elevation = 8.dp
            )
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = valueColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = AutoOnSurfaceVariant,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}
