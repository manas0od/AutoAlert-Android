package com.example.ui.screens

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DiscoveredUsbDevice
import com.example.data.Esp8266ApiClient
import com.example.data.UsbSerialManager
import com.example.data.WifiConfigResult
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTextField
import com.example.ui.theme.AutoAmberContainer
import com.example.ui.theme.AutoAmberPrimary
import com.example.ui.theme.AutoBackground
import com.example.ui.theme.AutoGlassBorder
import com.example.ui.theme.AutoOnSurface
import com.example.ui.theme.AutoOnSurfaceVariant
import com.example.ui.theme.AutoStatusCancelled
import com.example.ui.theme.AutoStatusCompleted
import com.example.ui.theme.AutoSurfaceHighest
import com.example.util.LocalStrings
import kotlinx.coroutines.launch

enum class WifiConfigMode {
    USB_SERIAL,
    LOCAL_AP
}

data class CombinedWifiConfigResult(
    val primarySuccess: Boolean,
    val primaryMessage: String,
    val backupSuccess: Boolean?, // null if backup was skipped / not set
    val backupMessage: String,
    val ip: String
) {
    val overallSuccess: Boolean
        get() = primarySuccess && (backupSuccess == null || backupSuccess == true)
}

@Composable
fun WifiSetupScreen(
    currentDeviceIp: String = "192.168.4.1",
    isSimulationMode: Boolean = false,
    onBack: () -> Unit = {},
    onApplyDeviceIp: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()

    val usbSerialManager = remember { UsbSerialManager(context) }
    val apiClient = remember { Esp8266ApiClient() }

    var selectedMode by remember { mutableStateOf(WifiConfigMode.USB_SERIAL) }

    // Form inputs - Primary Stand Wi-Fi
    var wifiSsid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    // Form inputs - Driver's Phone Hotspot (Backup Wi-Fi)
    var backupWifiSsid by remember { mutableStateOf("") }
    var backupWifiPassword by remember { mutableStateOf("") }
    var isBackupPasswordVisible by remember { mutableStateOf(false) }

    // Device Password (Shared for paired device validation)
    var devicePassword by remember { mutableStateOf("") }
    var isDevicePasswordVisible by remember { mutableStateOf(false) }

    var apAddress by remember { mutableStateOf(if (currentDeviceIp.isNotBlank()) currentDeviceIp else "192.168.4.1") }

    // USB state
    var detectedDevices by remember { mutableStateOf<List<DiscoveredUsbDevice>>(emptyList()) }
    var selectedUsbDevice by remember { mutableStateOf<DiscoveredUsbDevice?>(null) }
    var isScanningUsb by remember { mutableStateOf(false) }
    var simulateUsbDevice by remember { mutableStateOf(isSimulationMode) }

    // Combined Execution & Result state
    var isConfiguring by remember { mutableStateOf(false) }
    var combinedConfigResult by remember { mutableStateOf<CombinedWifiConfigResult?>(null) }

    val serialLogs = remember { mutableStateListOf<String>() }
    var showLogsConsole by remember { mutableStateOf(true) }

    fun refreshUsbDevices() {
        isScanningUsb = true
        val devices = usbSerialManager.findConnectedSerialDevices()
        detectedDevices = devices
        selectedUsbDevice = devices.firstOrNull { it.driver != null } ?: devices.firstOrNull()
        isScanningUsb = false
    }

    LaunchedEffect(Unit) {
        refreshUsbDevices()
    }

    fun requestUsbPermission(device: DiscoveredUsbDevice) {
        val activity = context as? Activity
        if (activity != null) {
            usbSerialManager.requestUsbPermission(activity, device.device) { granted ->
                refreshUsbDevices()
                if (granted) {
                    serialLogs.add("[USB] Permission granted at detection for ${device.displayName}")
                } else {
                    serialLogs.add("[USB] Permission denied at detection for ${device.displayName}")
                }
            }
        }
    }

    fun executeWifiConfig() {
        if (wifiSsid.isBlank()) {
            combinedConfigResult = CombinedWifiConfigResult(
                primarySuccess = false,
                primaryMessage = "Please enter Wi-Fi network name (SSID)",
                backupSuccess = null,
                backupMessage = "not set",
                ip = ""
            )
            return
        }

        isConfiguring = true
        combinedConfigResult = null

        val hasBackup = backupWifiSsid.trim().isNotBlank()

        scope.launch {
            if (selectedMode == WifiConfigMode.USB_SERIAL) {
                // USB Serial Mode - Primary Wi-Fi
                serialLogs.add("--- Starting USB Wi-Fi configuration ---")
                val targetDriver = selectedUsbDevice?.driver
                val isSim = simulateUsbDevice || isSimulationMode || targetDriver == null

                val primaryRes = usbSerialManager.configureWifiOverUsb(
                    driver = targetDriver,
                    ssid = wifiSsid.trim(),
                    password = wifiPassword,
                    slot = "primary",
                    isSimulated = isSim,
                    onDebugLog = { logLine -> serialLogs.add(logLine) }
                )

                if (primaryRes.success) {
                    serialLogs.add("[SUCCESS] ESP8266 assigned IP: ${primaryRes.ip}")
                } else {
                    serialLogs.add("[ERROR] Primary Wi-Fi: ${primaryRes.message}")
                }

                // Backup Wi-Fi over USB (if configured)
                val backupRes = if (hasBackup) {
                    serialLogs.add("--- Starting USB Backup Wi-Fi configuration ---")
                    val bRes = usbSerialManager.configureWifiOverUsb(
                        driver = targetDriver,
                        ssid = backupWifiSsid.trim(),
                        password = backupWifiPassword,
                        slot = "safety",
                        isSimulated = isSim,
                        onDebugLog = { logLine -> serialLogs.add(logLine) }
                    )
                    if (bRes.success) {
                        serialLogs.add("[SUCCESS] Backup Wi-Fi configured successfully (${bRes.message})")
                    } else {
                        serialLogs.add("[ERROR] Backup Wi-Fi: ${bRes.message}")
                    }
                    bRes
                } else null

                val finalIp = if (primaryRes.ip.isNotBlank()) primaryRes.ip else backupRes?.ip.orEmpty()
                combinedConfigResult = CombinedWifiConfigResult(
                    primarySuccess = primaryRes.success,
                    primaryMessage = if (primaryRes.success) "saved and connected" else primaryRes.message,
                    backupSuccess = backupRes?.success,
                    backupMessage = if (backupRes != null) {
                        if (backupRes.success) "saved and connected" else backupRes.message
                    } else "not set",
                    ip = finalIp
                )
            } else {
                // Local SoftAP Mode - Primary Wi-Fi
                serialLogs.add("--- Starting Local AP Primary Wi-Fi configuration ---")
                serialLogs.add("[AP] Target endpoint: http://$apAddress/wifi-config (slot: primary)")

                val primaryRes = apiClient.configureWifiAp(
                    ip = apAddress,
                    ssid = wifiSsid.trim(),
                    password = wifiPassword,
                    slot = "primary",
                    currentPassword = devicePassword,
                    isSimulated = isSimulationMode
                )

                if (primaryRes.success) {
                    serialLogs.add("[AP-SUCCESS] Primary Wi-Fi configured, IP: ${primaryRes.ip}")
                } else {
                    serialLogs.add("[AP-ERROR] Primary Wi-Fi: ${primaryRes.message}")
                }

                // Backup Wi-Fi over Local SoftAP (if configured)
                val backupRes = if (hasBackup) {
                    serialLogs.add("--- Starting Local AP Backup Wi-Fi configuration ---")
                    serialLogs.add("[AP] Target endpoint: http://$apAddress/wifi-config (slot: safety)")
                    val bRes = apiClient.configureWifiAp(
                        ip = apAddress,
                        ssid = backupWifiSsid.trim(),
                        password = backupWifiPassword,
                        slot = "safety",
                        currentPassword = devicePassword,
                        isSimulated = isSimulationMode
                    )
                    if (bRes.success) {
                        serialLogs.add("[AP-SUCCESS] Backup Wi-Fi configured successfully (${bRes.message})")
                    } else {
                        serialLogs.add("[AP-ERROR] Backup Wi-Fi: ${bRes.message}")
                    }
                    bRes
                } else null

                val finalIp = if (primaryRes.ip.isNotBlank()) primaryRes.ip else backupRes?.ip.orEmpty()
                combinedConfigResult = CombinedWifiConfigResult(
                    primarySuccess = primaryRes.success,
                    primaryMessage = if (primaryRes.success) "saved and connected" else primaryRes.message,
                    backupSuccess = backupRes?.success,
                    backupMessage = if (backupRes != null) {
                        if (backupRes.success) "saved and connected" else backupRes.message
                    } else "not set",
                    ip = finalIp
                )
            }

            isConfiguring = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AutoBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("wifi_setup_screen")
    ) {
        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("wifi_setup_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = AutoAmberPrimary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = strings.wifiSetupTitle,
                    color = AutoOnSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = strings.wifiSetupSubtitle,
                    color = AutoOnSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        // Mode Selector: USB Serial vs Local AP
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = "Configuration Method",
                    color = AutoOnSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // USB Option (Primary)
                    val isUsbSelected = selectedMode == WifiConfigMode.USB_SERIAL
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isUsbSelected) AutoAmberContainer.copy(alpha = 0.35f)
                                else AutoSurfaceHighest
                            )
                            .border(
                                width = if (isUsbSelected) 1.5.dp else 1.dp,
                                color = if (isUsbSelected) AutoAmberPrimary else AutoGlassBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedMode = WifiConfigMode.USB_SERIAL }
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                            .testTag("mode_usb_serial_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Usb,
                                    contentDescription = null,
                                    tint = if (isUsbSelected) AutoAmberPrimary else AutoOnSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = strings.connectViaUsb,
                                    color = if (isUsbSelected) AutoAmberPrimary else AutoOnSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Primary / Reliable",
                                color = AutoStatusCompleted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Local AP Option (Fallback)
                    val isApSelected = selectedMode == WifiConfigMode.LOCAL_AP
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isApSelected) AutoAmberContainer.copy(alpha = 0.35f)
                                else AutoSurfaceHighest
                            )
                            .border(
                                width = if (isApSelected) 1.5.dp else 1.dp,
                                color = if (isApSelected) AutoAmberPrimary else AutoGlassBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedMode = WifiConfigMode.LOCAL_AP }
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                            .testTag("mode_local_ap_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = if (isApSelected) AutoAmberPrimary else AutoOnSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = strings.connectViaAp,
                                    color = if (isApSelected) AutoAmberPrimary else AutoOnSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Fallback / AP",
                                color = AutoOnSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Mode-Specific Details Card
        if (selectedMode == WifiConfigMode.USB_SERIAL) {
            // USB Connection Status Card
            GlassCard(modifier = Modifier.fillMaxWidth().testTag("usb_device_status_card")) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Cable,
                                contentDescription = null,
                                tint = AutoAmberPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "USB-OTG Serial Connection",
                                color = AutoOnSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = { refreshUsbDevices() },
                            modifier = Modifier.size(32.dp).testTag("refresh_usb_button")
                        ) {
                            if (isScanningUsb) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = AutoAmberPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh USB",
                                    tint = AutoAmberPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val activeDev = selectedUsbDevice
                    if (activeDev != null) {
                        // Detected Device Details
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(AutoSurfaceHighest)
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = activeDev.chipType,
                                        color = AutoAmberPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AutoAmberContainer.copy(alpha = 0.3f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "115200 baud",
                                            color = AutoAmberPrimary,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Device: ${activeDev.displayName}",
                                    color = AutoOnSurfaceVariant,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "VID: 0x${Integer.toHexString(activeDev.vendorId).uppercase()} | PID: 0x${Integer.toHexString(activeDev.productId).uppercase()}",
                                    color = AutoOnSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                val hasLivePermission = usbSerialManager.hasPermission(activeDev.device)
                                if (hasLivePermission) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = AutoStatusCompleted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "USB Permission Granted — Ready to Configure",
                                            color = AutoStatusCompleted,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = strings.permissionRequired,
                                            color = AutoStatusCancelled,
                                            fontSize = 12.sp
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(AutoAmberPrimary)
                                                .clickable { requestUsbPermission(activeDev) }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                                .testTag("grant_usb_permission_button")
                                        ) {
                                            Text(
                                                text = strings.grantUsbPermission,
                                                color = Color.Black,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // No Device Detected
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(AutoSurfaceHighest)
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = AutoAmberPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = strings.noUsbDeviceDetected,
                                        color = AutoOnSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(AutoAmberContainer.copy(alpha = 0.2f))
                                            .border(1.dp, AutoAmberPrimary.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                            .clickable { refreshUsbDevices() }
                                            .padding(vertical = 8.dp)
                                            .testTag("scan_usb_button"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Scan USB",
                                            color = AutoAmberPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (simulateUsbDevice) AutoAmberPrimary else AutoSurfaceHighest)
                                            .border(1.dp, AutoGlassBorder, RoundedCornerShape(6.dp))
                                            .clickable { simulateUsbDevice = !simulateUsbDevice }
                                            .padding(vertical = 8.dp)
                                            .testTag("simulate_usb_button"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (simulateUsbDevice) "Simulated ESP8266 (Active)" else "Simulate USB Device",
                                            color = if (simulateUsbDevice) Color.Black else AutoOnSurface,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Local AP Fallback Card
            GlassCard(modifier = Modifier.fillMaxWidth().testTag("local_ap_config_card")) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = AutoAmberPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Local SoftAP Fallback",
                            color = AutoOnSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Connect phone to the ESP8266's temporary Wi-Fi network (e.g., 'AutoAlert-Setup'), then send credentials via HTTP endpoint /wifi-config.",
                        color = AutoOnSurfaceVariant,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    GlassTextField(
                        value = apAddress,
                        onValueChange = { apAddress = it },
                        label = "ESP8266 Local AP IP Address",
                        leadingIcon = Icons.Default.Wifi,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        testTagStr = "ap_address_input"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Device Password Card (Placed above both Wi-Fi cards; only required if device is already paired)
        GlassCard(modifier = Modifier.fillMaxWidth().testTag("device_password_card")) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = AutoAmberPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.devicePasswordLabel,
                        color = AutoOnSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = strings.devicePasswordWifiNote,
                    color = AutoOnSurfaceVariant,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(10.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    GlassTextField(
                        value = devicePassword,
                        onValueChange = { devicePassword = it },
                        label = strings.devicePasswordLabel,
                        leadingIcon = Icons.Default.Lock,
                        visualTransformation = if (isDevicePasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        testTagStr = "device_password_input"
                    )

                    IconButton(
                        onClick = { isDevicePasswordVisible = !isDevicePasswordVisible },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 4.dp)
                            .testTag("toggle_device_password_visibility_button")
                    ) {
                        Icon(
                            imageVector = if (isDevicePasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isDevicePasswordVisible) "Hide password" else "Show password",
                            tint = AutoOnSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Primary Wi-Fi Credentials Input Card
        GlassCard(modifier = Modifier.fillMaxWidth().testTag("wifi_credentials_card")) {
            Column {
                Text(
                    text = strings.primaryWifiTitle,
                    color = AutoOnSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Primary SSID
                GlassTextField(
                    value = wifiSsid,
                    onValueChange = { wifiSsid = it },
                    label = strings.wifiSsidLabel,
                    leadingIcon = Icons.Default.Wifi,
                    modifier = Modifier.fillMaxWidth(),
                    testTagStr = "wifi_ssid_input"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Primary Password with visibility toggle
                Box(modifier = Modifier.fillMaxWidth()) {
                    GlassTextField(
                        value = wifiPassword,
                        onValueChange = { wifiPassword = it },
                        label = strings.wifiPasswordLabel,
                        leadingIcon = Icons.Default.Lock,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        testTagStr = "wifi_password_input"
                    )

                    IconButton(
                        onClick = { isPasswordVisible = !isPasswordVisible },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 4.dp)
                            .testTag("toggle_password_visibility_button")
                    ) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                            tint = AutoOnSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Second GlassCard: Driver's Phone Hotspot (Backup Wi-Fi) - rendered under BOTH USB_SERIAL and LOCAL_AP
        Spacer(modifier = Modifier.height(14.dp))
        GlassCard(modifier = Modifier.fillMaxWidth().testTag("backup_wifi_card")) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = AutoAmberPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.backupWifiTitle,
                        color = AutoOnSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = strings.backupWifiSubtitle,
                    color = AutoOnSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Backup SSID
                GlassTextField(
                    value = backupWifiSsid,
                    onValueChange = { backupWifiSsid = it },
                    label = strings.wifiSsidLabel,
                    leadingIcon = Icons.Default.Wifi,
                    modifier = Modifier.fillMaxWidth(),
                    testTagStr = "backup_wifi_ssid_input"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Backup Password with visibility toggle
                Box(modifier = Modifier.fillMaxWidth()) {
                    GlassTextField(
                        value = backupWifiPassword,
                        onValueChange = { backupWifiPassword = it },
                        label = strings.wifiPasswordLabel,
                        leadingIcon = Icons.Default.Lock,
                        visualTransformation = if (isBackupPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        testTagStr = "backup_wifi_password_input"
                    )

                    IconButton(
                        onClick = { isBackupPasswordVisible = !isBackupPasswordVisible },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 4.dp)
                            .testTag("toggle_backup_password_visibility_button")
                    ) {
                        Icon(
                            imageVector = if (isBackupPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isBackupPasswordVisible) "Hide password" else "Show password",
                            tint = AutoOnSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Unified Wi-Fi Configuration Submit Action
        if (isConfiguring) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AutoAmberContainer.copy(alpha = 0.3f))
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = AutoAmberPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = strings.configuringWifi,
                        color = AutoAmberPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            GlassButton(
                text = strings.sendWifiConfig,
                icon = if (selectedMode == WifiConfigMode.USB_SERIAL) Icons.Default.Usb else Icons.Default.Wifi,
                enabled = !isConfiguring,
                onClick = { executeWifiConfig() },
                modifier = Modifier.fillMaxWidth(),
                testTagStr = "send_wifi_config_button"
            )
        }

        // Combined Wi-Fi Configuration Result Card
        val cRes = combinedConfigResult
        AnimatedVisibility(
            visible = cRes != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (cRes != null) {
                Spacer(modifier = Modifier.height(14.dp))
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (cRes.overallSuccess) AutoStatusCompleted
                            else if (cRes.primarySuccess) AutoAmberPrimary
                            else AutoStatusCancelled,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .testTag("wifi_config_result_card")
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (cRes.overallSuccess) Icons.Default.CheckCircle
                                else if (cRes.primarySuccess) Icons.Default.Info
                                else Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = if (cRes.overallSuccess) AutoStatusCompleted
                                else if (cRes.primarySuccess) AutoAmberPrimary
                                else AutoStatusCancelled,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (cRes.overallSuccess) strings.wifiConfigSuccess
                                else if (cRes.primarySuccess) "Primary Connected (Backup Failed)"
                                else "Configuration Failed",
                                color = if (cRes.overallSuccess) AutoStatusCompleted
                                else if (cRes.primarySuccess) AutoAmberPrimary
                                else AutoStatusCancelled,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (cRes.ip.isNotBlank()) {
                            Text(
                                text = "Connected - IP: ${cRes.ip}",
                                color = AutoAmberPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        Text(
                            text = "Primary: ${cRes.primaryMessage}",
                            color = if (cRes.primarySuccess) AutoOnSurface else AutoStatusCancelled,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )

                        Text(
                            text = "Backup: ${if (cRes.backupSuccess == null) "not set" else cRes.backupMessage}",
                            color = when (cRes.backupSuccess) {
                                true -> AutoOnSurface
                                false -> AutoStatusCancelled
                                null -> AutoOnSurfaceVariant
                            },
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        if (cRes.ip.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AutoAmberContainer.copy(alpha = 0.2f))
                                    .border(1.dp, AutoAmberPrimary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "${strings.newDeviceIp}: ${cRes.ip}",
                                        color = AutoAmberPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(AutoAmberPrimary)
                                            .clickable {
                                                onApplyDeviceIp(cRes.ip)
                                                onBack()
                                            }
                                            .padding(vertical = 10.dp)
                                            .testTag("apply_ip_button"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = strings.useIpInApp,
                                            color = Color.Black,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Live ESP8266 Serial Log Terminal
        GlassCard(modifier = Modifier.fillMaxWidth().testTag("serial_terminal_card")) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLogsConsole = !showLogsConsole }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = AutoAmberPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.serialLogTitle,
                            color = AutoOnSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (serialLogs.isNotEmpty()) {
                            IconButton(
                                onClick = { serialLogs.clear() },
                                modifier = Modifier.size(28.dp).testTag("clear_logs_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear logs",
                                    tint = AutoOnSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Icon(
                            imageVector = if (showLogsConsole) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = AutoOnSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (showLogsConsole) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0D1117))
                            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (serialLogs.isEmpty()) {
                            Text(
                                text = "Serial output from ESP8266 will stream here at 115200 baud...\n- Connect phone to board via USB-OTG\n- Non-protocol debug messages will be displayed\n- Protocol line WIFI_RESULT: will be parsed",
                                color = Color(0xFF8B949E),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp
                            )
                        } else {
                            Column {
                                serialLogs.forEach { logLine ->
                                    val textColor = when {
                                        logLine.startsWith("[TX]") -> AutoAmberPrimary
                                        logLine.startsWith("[RX-PROTOCOL]") -> AutoStatusCompleted
                                        logLine.startsWith("[SUCCESS]") -> AutoStatusCompleted
                                        logLine.startsWith("[ERROR]") || logLine.startsWith("[AP-ERROR]") -> AutoStatusCancelled
                                        logLine.startsWith("[USB]") -> Color(0xFF58A6FF)
                                        else -> Color(0xFFC9D1D9)
                                    }
                                    Text(
                                        text = logLine,
                                        color = textColor,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
