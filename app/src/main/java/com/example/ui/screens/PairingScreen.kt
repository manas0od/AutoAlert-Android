package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneForwarded
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import com.example.ui.theme.AutoGlassBorder
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.DEBUG_SKIP_PAIRING
import com.example.data.DeviceDiscoveryManager
import com.example.data.DiscoveredDevice
import com.example.data.Esp8266ApiClient
import com.example.ui.components.GlassBackgroundCanvas
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTextField
import com.example.ui.theme.AutoAmberContainer
import com.example.ui.theme.AutoAmberPrimary
import com.example.ui.theme.AutoBackground
import com.example.ui.theme.AutoOnSurface
import com.example.ui.theme.AutoOnSurfaceVariant
import com.example.ui.theme.AutoStatusCompleted
import com.example.ui.theme.AutoSurfaceHighest
import com.example.util.LocalStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class DiscoveryState {
    SEARCHING,
    FOUND,
    TIMED_OUT,
    MANUAL
}

enum class SetupRole {
    OWN_DEVICE,
    BACKUP_VIEWER
}

@Composable
fun PairingScreen(
    initialIp: String = "192.168.4.1",
    initialPhone: String = "",
    initialBackupPhone: String = "",
    isAlreadyPaired: Boolean = false,
    isSimulationMode: Boolean = false,
    onPairingSuccess: (topic: String, phone: String, password: String, ip: String, isSimulated: Boolean, backupPhone: String) -> Unit,
    onLinkViewerSuccess: (topic: String, viewerPhone: String, ip: String, isSimulated: Boolean) -> Unit = { _, _, _, _ -> },
    onSkipPairing: () -> Unit = {},
    onOpenWifiSetup: () -> Unit = {}
) {
    val context = LocalContext.current
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val apiClient = remember { Esp8266ApiClient() }
    val discoveryManager = remember { DeviceDiscoveryManager(context) }

    var selectedRole by remember { mutableStateOf(SetupRole.OWN_DEVICE) }

    // Owner Flow state
    var deviceIp by remember { mutableStateOf(if (initialIp.isBlank() || initialIp.contains("autoalert.local")) "192.168.4.1" else initialIp) }
    var phoneLabel by remember { mutableStateOf(initialPhone.ifBlank { "+91 9876543210" }) }
    var backupPhoneLabel by remember { mutableStateOf(initialBackupPhone) }
    var password by remember { mutableStateOf("auto123") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf("Auto-discovering device via NsdManager (mDNS)...") }

    // Backup Viewer Flow state
    var viewerDeviceIp by remember { mutableStateOf(if (initialIp.isBlank() || initialIp.contains("autoalert.local")) "192.168.4.1" else initialIp) }
    var viewerPhone by remember { mutableStateOf("") }
    var viewerMasterPassword by remember { mutableStateOf("") }
    var isViewerPasswordVisible by remember { mutableStateOf(false) }
    var isViewerSubmitting by remember { mutableStateOf(false) }
    var viewerErrorMessage by remember { mutableStateOf<String?>(null) }
    var viewerStatusText by remember { mutableStateOf("") }

    var discoveryState by remember { mutableStateOf(DiscoveryState.SEARCHING) }
    var discoveredDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var showManualIpSection by remember { mutableStateOf(false) }

    fun startMdnsDiscovery() {
        discoveryState = DiscoveryState.SEARCHING
        errorMessage = null
        statusText = "Searching for AutoAlert button on local WiFi (mDNS)..."

        scope.launch {
            if (isSimulationMode) {
                delay(1200L)
                val simDev = DiscoveredDevice(
                    ip = "192.168.4.1",
                    hostname = "autoalert",
                    description = "AutoAlert ESP8266 Button (Simulated)",
                    isReachable = true
                )
                discoveredDevice = simDev
                discoveryState = DiscoveryState.FOUND
                deviceIp = simDev.ip
                statusText = "Device resolved: ${simDev.ip} (1-Tap Connect Ready)"
                return@launch
            }

            val device = discoveryManager.discoverDevice(timeoutMs = 5000L)
            if (device != null) {
                discoveredDevice = device
                discoveryState = DiscoveryState.FOUND
                deviceIp = device.ip // Always store and use numeric IP
                statusText = "Device found via mDNS at ${device.ip}"
            } else {
                discoveryState = DiscoveryState.TIMED_OUT
                showManualIpSection = true
                statusText = "Auto-discovery timed out. Ready for manual IP or retry."
            }
        }
    }

    LaunchedEffect(Unit) {
        startMdnsDiscovery()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    GlassBackgroundCanvas(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("pairing_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header Icon
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .scale(if (selectedRole == SetupRole.OWN_DEVICE && discoveryState == DiscoveryState.SEARCHING) pulseScale else 1f)
                    .background(AutoAmberContainer.copy(alpha = 0.15f), shape = CircleShape)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        selectedRole == SetupRole.BACKUP_VIEWER -> Icons.Default.Security
                        discoveryState == DiscoveryState.FOUND -> Icons.Default.CheckCircle
                        discoveryState == DiscoveryState.SEARCHING -> Icons.Default.WifiTethering
                        else -> Icons.Default.Router
                    },
                    contentDescription = null,
                    tint = if (selectedRole == SetupRole.OWN_DEVICE && discoveryState == DiscoveryState.FOUND) AutoStatusCompleted else AutoAmberPrimary,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (selectedRole == SetupRole.BACKUP_VIEWER) {
                    "Backup Safety Viewer"
                } else if (isAlreadyPaired) {
                    "Device Re-configuration"
                } else {
                    "1-Tap Device Pairing"
                },
                color = AutoOnSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (selectedRole == SetupRole.BACKUP_VIEWER) {
                    "Link as family safety recipient for tilt & emergency alerts"
                } else {
                    "Auto-discovers your AutoAlert button on local WiFi via mDNS"
                },
                color = AutoOnSurfaceVariant,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
            )

            // Setup Role Choice: Own Device vs Backup Viewer
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .testTag("setup_role_selection_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = strings.setupRoleTitle,
                        color = AutoOnSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.setupRoleSubtitle,
                        color = AutoOnSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AutoSurfaceHighest)
                            .border(1.dp, AutoGlassBorder, RoundedCornerShape(10.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedRole == SetupRole.OWN_DEVICE) AutoAmberPrimary else Color.Transparent)
                                .clickable { selectedRole = SetupRole.OWN_DEVICE }
                                .padding(vertical = 10.dp, horizontal = 6.dp)
                                .testTag("role_own_device_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = strings.setupRoleOwner,
                                color = if (selectedRole == SetupRole.OWN_DEVICE) Color.Black else AutoOnSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = if (selectedRole == SetupRole.OWN_DEVICE) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedRole == SetupRole.BACKUP_VIEWER) AutoAmberPrimary else Color.Transparent)
                                .clickable { selectedRole = SetupRole.BACKUP_VIEWER }
                                .padding(vertical = 10.dp, horizontal = 6.dp)
                                .testTag("role_backup_viewer_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = strings.setupRoleViewer,
                                color = if (selectedRole == SetupRole.BACKUP_VIEWER) Color.Black else AutoOnSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = if (selectedRole == SetupRole.BACKUP_VIEWER) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            if (selectedRole == SetupRole.BACKUP_VIEWER) {
                // BACKUP SAFETY VIEWER FLOW
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_viewer_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = AutoAmberPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.backupViewerCardTitle,
                                color = AutoAmberPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = strings.backupViewerCardSubtitle,
                            color = AutoOnSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // 1. Device IP
                        GlassTextField(
                            value = viewerDeviceIp,
                            onValueChange = { viewerDeviceIp = it },
                            label = "Device IP",
                            placeholder = "192.168.4.1",
                            leadingIcon = Icons.Default.Router,
                            testTagStr = "viewer_device_ip_input"
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 2. Viewer's Own Phone Number
                        GlassTextField(
                            value = viewerPhone,
                            onValueChange = { viewerPhone = it },
                            label = strings.viewerPhoneLabel,
                            placeholder = "+91 9876543210",
                            leadingIcon = Icons.Default.Phone,
                            testTagStr = "viewer_phone_input"
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 3. Master Password (with clear instruction to ask device owner)
                        GlassTextField(
                            value = viewerMasterPassword,
                            onValueChange = { viewerMasterPassword = it },
                            label = strings.deviceOwnerPasswordLabel,
                            placeholder = "Ask owner to enter master password",
                            leadingIcon = Icons.Default.Lock,
                            visualTransformation = if (isViewerPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isViewerPasswordVisible = !isViewerPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isViewerPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (isViewerPasswordVisible) "Hide password" else "Show password",
                                        tint = AutoOnSurfaceVariant
                                    )
                                }
                            },
                            testTagStr = "viewer_device_password_input"
                        )

                        Text(
                            text = strings.deviceOwnerPasswordHelper,
                            color = AutoAmberPrimary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                        )

                        // Error message
                        AnimatedVisibility(visible = viewerErrorMessage != null) {
                            Text(
                                text = viewerErrorMessage ?: "",
                                color = AutoAmberContainer,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }

                        if (isViewerSubmitting) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = AutoAmberPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = viewerStatusText.ifBlank { strings.linkingBackupViewer },
                                    color = AutoOnSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        GlassButton(
                            text = if (isViewerSubmitting) strings.linkingBackupViewer else strings.linkAsBackupViewer,
                            icon = Icons.AutoMirrored.Filled.ArrowForward,
                            enabled = !isViewerSubmitting && viewerPhone.isNotBlank() && viewerMasterPassword.isNotBlank(),
                            onClick = {
                                isViewerSubmitting = true
                                viewerErrorMessage = null
                                viewerStatusText = strings.linkingBackupViewer
                                val targetIp = viewerDeviceIp.trim().ifBlank { "192.168.4.1" }
                                val targetPassword = viewerMasterPassword
                                val targetPhone = viewerPhone.trim()

                                scope.launch {
                                    val result = apiClient.linkViewer(
                                        ip = targetIp,
                                        password = targetPassword,
                                        viewerPhone = targetPhone,
                                        isSimulated = isSimulationMode
                                    )
                                    // CRITICAL: Immediately discard the typed password from memory!
                                    viewerMasterPassword = ""
                                    isViewerSubmitting = false

                                    if (result.success && result.topic.isNotBlank()) {
                                        DeviceDiscoveryManager.releaseNetworkBinding(context)
                                        // BACKUP role: subscribe to the SAME shared topic as the primary phone
                                        onLinkViewerSuccess(result.topic, targetPhone, targetIp, isSimulationMode)
                                    } else {
                                        viewerErrorMessage = result.message
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            testTagStr = "link_backup_viewer_button"
                        )
                    }
                }
            } else {
                // EXISTING OWNER FLOW
                // Wi-Fi Configuration Banner (USB / AP)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AutoAmberContainer.copy(alpha = 0.22f))
                        .border(1.dp, AutoAmberPrimary.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .clickable { onOpenWifiSetup() }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("open_wifi_setup_pairing_banner")
                ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = null,
                            tint = AutoAmberPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Need to configure button Wi-Fi?",
                                color = AutoAmberPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Connect via USB-OTG (Primary) or Local AP",
                                color = AutoOnSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open Wi-Fi Setup",
                        tint = AutoAmberPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Auto-Discovery Card
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auto_discovery_card")
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                tint = AutoAmberPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "mDNS Auto-Discovery",
                                color = AutoAmberPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (discoveryState == DiscoveryState.SEARCHING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = AutoAmberPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AutoSurfaceHighest)
                                    .clickable { startMdnsDiscovery() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("retry_discovery_button")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Retry",
                                        tint = AutoOnSurfaceVariant,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Re-scan",
                                        color = AutoOnSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when (discoveryState) {
                        DiscoveryState.SEARCHING -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AutoSurfaceHighest.copy(alpha = 0.5f))
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = AutoAmberPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                     Column {
                                        Text(
                                            text = "Scanning for AutoAlert button...",
                                            color = AutoOnSurface,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Resolving via Android NsdManager (_http._tcp mDNS)",
                                            color = AutoOnSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                        DiscoveryState.FOUND -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x1A4CAF50))
                                    .border(BorderStroke(1.dp, Color(0x4D4CAF50)), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = AutoStatusCompleted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "AutoAlert ESP8266 Discovered!",
                                            color = AutoStatusCompleted,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Resolved IP: ${discoveredDevice?.ip ?: deviceIp} (${discoveredDevice?.description ?: "mDNS"})",
                                            color = AutoOnSurface,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                        DiscoveryState.TIMED_OUT, DiscoveryState.MANUAL -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AutoAmberContainer.copy(alpha = 0.1f))
                                    .border(BorderStroke(1.dp, AutoAmberContainer.copy(alpha = 0.3f)), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "mDNS Auto-Discovery Timed Out",
                                        color = AutoAmberContainer,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Device not found automatically. Connect to the button's WiFi (or stand WiFi) and enter the device's numeric IP below.",
                                        color = AutoOnSurfaceVariant,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Driver Phone
                    GlassTextField(
                        value = phoneLabel,
                        onValueChange = { phoneLabel = it },
                        label = strings.primaryDriverNumber,
                        placeholder = "+91 9876543210",
                        leadingIcon = Icons.Default.Phone,
                        testTagStr = "driver_phone_input"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Backup Driver Phone (Optional for Escalation)
                    GlassTextField(
                        value = backupPhoneLabel,
                        onValueChange = { backupPhoneLabel = it },
                        label = "${strings.backupDriverNumber} (Optional)",
                        placeholder = "+91 9876500000",
                        leadingIcon = Icons.Default.PhoneForwarded,
                        testTagStr = "backup_driver_phone_input"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Device Password
                    GlassTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = strings.devicePasswordLabel,
                        placeholder = "auto123 (Default)",
                        leadingIcon = Icons.Default.Lock,
                        testTagStr = "device_password_input"
                    )

                    // Expandable Manual IP Section (Fallback)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showManualIpSection = !showManualIpSection }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (showManualIpSection) "Hide Manual IP Settings" else "Manual Numeric IP Fallback",
                            color = AutoOnSurfaceVariant,
                            fontSize = 12.sp
                        )
                        Icon(
                            imageVector = if (showManualIpSection) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = AutoOnSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    AnimatedVisibility(visible = showManualIpSection) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            GlassTextField(
                                value = deviceIp,
                                onValueChange = { deviceIp = it },
                                label = "ESP8266 Numeric IP Address",
                                placeholder = "e.g. 192.168.4.1 or 192.168.1.100",
                                leadingIcon = Icons.Default.Router,
                                testTagStr = "device_ip_input"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    AnimatedVisibility(visible = errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = AutoAmberContainer,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = AutoAmberPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = statusText,
                            color = AutoOnSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }

                    // Primary 1-Tap Connect Button
                    GlassButton(
                        text = if (isSubmitting) "Pairing Device..." else "Connect Device",
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        enabled = !isSubmitting && phoneLabel.isNotBlank(),
                        onClick = {
                            isSubmitting = true
                            errorMessage = null
                            val targetIp = if (deviceIp.isBlank() || deviceIp.contains("autoalert.local")) "192.168.4.1" else deviceIp.trim()
                            statusText = "Connecting to $targetIp..."

                            scope.launch {
                                // If device not discovered yet, try quick resolve before pair
                                val resolvedIp = if (discoveryState == DiscoveryState.SEARCHING && !isSimulationMode) {
                                    val quickDev = discoveryManager.discoverDevice(timeoutMs = 1500L)
                                    quickDev?.ip ?: targetIp
                                } else {
                                    targetIp
                                }

                                if (isAlreadyPaired) {
                                    val verifyRes = apiClient.verifyPassword(
                                        ip = resolvedIp,
                                        password = password,
                                        isSimulated = isSimulationMode
                                    )
                                    if (!verifyRes.success) {
                                        isSubmitting = false
                                        errorMessage = verifyRes.message
                                        statusText = "Password verification failed with ESP8266."
                                        return@launch
                                    }
                                    statusText = "Password verified. Registering device..."
                                }

                                val result = apiClient.pairDevice(
                                    ip = resolvedIp,
                                    phone = phoneLabel,
                                    password = password,
                                    isSimulated = isSimulationMode,
                                    backupPhone = backupPhoneLabel.trim()
                                )

                                isSubmitting = false
                                if (result.success && result.topic.isNotBlank()) {
                                    statusText = "Connected successfully!"
                                    DeviceDiscoveryManager.releaseNetworkBinding(context)
                                    onPairingSuccess(
                                        result.topic,
                                        phoneLabel,
                                        password,
                                        resolvedIp,
                                        isSimulationMode,
                                        backupPhoneLabel.trim()
                                    )
                                } else {
                                    DeviceDiscoveryManager.releaseNetworkBinding(context)
                                    errorMessage = result.message
                                    showManualIpSection = true
                                    statusText = "Connection failed. Please check local WiFi or enter IP manually."
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTagStr = "connect_device_button"
                    )
                }
            }

            // DEV ONLY: Bypass button when DEBUG_SKIP_PAIRING is enabled
            if (DEBUG_SKIP_PAIRING) {
                Spacer(modifier = Modifier.height(28.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x22FF5252))
                            .border(BorderStroke(1.dp, Color(0x66FF5252)), RoundedCornerShape(6.dp))
                            .clickable(onClick = onSkipPairing)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("dev_skip_pairing_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "[DEV] Skip Pairing",
                                color = Color(0xFFFF5252),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "DEV ONLY: Bypasses hardware pairing with placeholder data",
                        color = Color(0x88FF5252),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            }
        }
    }
}
