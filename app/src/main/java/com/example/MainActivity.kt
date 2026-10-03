package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Esp8266ApiClient
import com.example.data.NtfyForegroundService
import com.example.ui.MainViewModel
import com.example.ui.components.GlassBackgroundCanvas
import com.example.ui.components.GlassBottomNavigation
import com.example.ui.components.GlassTopBar
import com.example.ui.components.LocalHazeState
import com.example.ui.components.NavTab
import dev.chrisbanes.haze.HazeState
import androidx.compose.runtime.CompositionLocalProvider
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.Orientation3DScreen
import com.example.ui.screens.PairingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.StatusScreen
import com.example.ui.screens.WifiSetupScreen
import com.example.ui.theme.AutoAlertTheme
import com.example.ui.theme.AutoBackground
import com.example.util.LocalStrings
import com.example.util.getStrings
import kotlinx.coroutines.delay

// FOR DEVELOPMENT TESTING ONLY - MUST BE SET TO FALSE BEFORE FINAL BUILD/SUBMISSION.
const val DEBUG_SKIP_PAIRING = true

class MainActivity : ComponentActivity() {
    private val targetNotificationIdState = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            val viewModel: MainViewModel = viewModel()
            val appLanguage by viewModel.appLanguage.collectAsState()
            val strings = getStrings(appLanguage)
            val isMalayalam = appLanguage == "ml"

            CompositionLocalProvider(LocalStrings provides strings) {
                AutoAlertTheme(isMalayalam = isMalayalam) {
                    AutoAlertApp(
                        viewModel = viewModel,
                        targetNotificationId = targetNotificationIdState.value,
                        onClearTargetNotification = { targetNotificationIdState.value = null }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Dismiss system notifications and reset launcher badge count when app comes to foreground
        try {
            androidx.core.app.NotificationManagerCompat.from(this).cancelAll()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val notifId = intent?.getLongExtra("NOTIFICATION_ID", -1L) ?: -1L
        if (notifId != -1L) {
            targetNotificationIdState.value = notifId
            intent?.removeExtra("NOTIFICATION_ID")
        }
    }
}

@Composable
fun AutoAlertApp(
    viewModel: MainViewModel,
    targetNotificationId: Long? = null,
    onClearTargetNotification: () -> Unit = {}
) {
    val context = LocalContext.current
    val ntfyTopic by viewModel.ntfyTopic.collectAsState()
    val driverPhone by viewModel.driverPhone.collectAsState()
    val backupDriverPhone by viewModel.backupDriverPhone.collectAsState()
    val backupButtonEnabled by viewModel.backupButtonEnabled.collectAsState()
    val backupSafetyEnabled by viewModel.backupSafetyEnabled.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val deviceIp by viewModel.deviceIp.collectAsState()
    val isAvailable by viewModel.isAvailable.collectAsState()
    val isSimulationMode by viewModel.isSimulationMode.collectAsState()
    val alertSound by viewModel.alertSound.collectAsState()
    val requestTimeout by viewModel.requestTimeout.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val batteryResult by viewModel.batteryResult.collectAsState()
    val isCheckingBattery by viewModel.isCheckingBattery.collectAsState()
    val lastKnownBatteryPercent by viewModel.lastKnownBatteryPercent.collectAsState()
    val lastKnownBatteryTimestamp by viewModel.lastKnownBatteryTimestamp.collectAsState()
    val isBackupViewer by viewModel.isBackupViewer.collectAsState()

    val strings = LocalStrings.current
    var showSplash by remember { mutableStateOf(true) }
    var showWifiSetup by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(NavTab.HOME) }
    val apiClient = remember { Esp8266ApiClient() }

    // Request POST_NOTIFICATIONS permission at runtime on Android 13+ (API 33+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && ntfyTopic.isNotBlank()) {
            try {
                val serviceIntent = Intent(context, NtfyForegroundService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Start background listening foreground service when topic is available
    LaunchedEffect(ntfyTopic) {
        if (ntfyTopic.isNotBlank()) {
            try {
                val serviceIntent = Intent(context, NtfyForegroundService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(targetNotificationId, notifications) {
        val targetId = targetNotificationId
        if (targetId != null) {
            showSplash = false
            val idx = notifications.indexOfFirst { it.id == targetId }
            if (idx in 0..7) {
                selectedTab = NavTab.HOME
            } else if (idx >= 8) {
                selectedTab = NavTab.HISTORY
            } else if (notifications.isNotEmpty()) {
                selectedTab = NavTab.HOME
            }

            delay(5000L)
            onClearTargetNotification()
        }
    }

    if (showSplash) {
        SplashScreen(
            onGetStartedClick = { showSplash = false }
        )
    } else if (showWifiSetup) {
        WifiSetupScreen(
            currentDeviceIp = deviceIp,
            isSimulationMode = isSimulationMode,
            onBack = { showWifiSetup = false },
            onApplyDeviceIp = { newIp ->
                viewModel.updateDeviceIp(newIp)
            }
        )
    } else if (ntfyTopic.isBlank()) {
        PairingScreen(
            initialIp = deviceIp,
            initialPhone = driverPhone,
            initialBackupPhone = backupDriverPhone,
            isAlreadyPaired = false,
            isSimulationMode = isSimulationMode,
            onPairingSuccess = { topic, phone, pwd, ip, isSimulated, backupPhone ->
                viewModel.savePairingData(topic, phone, pwd, ip, isSimulated, backupPhone)
            },
            onLinkViewerSuccess = { topic, viewerPhone, ip, isSim ->
                viewModel.saveBackupViewerData(topic, viewerPhone, ip, isSim)
            },
            onSkipPairing = {
                viewModel.savePairingData(
                    topic = "autoalert-dev-bypass",
                    phone = "+91 9876543210",
                    password = "dev",
                    ip = "autoalert.local",
                    isSimulated = true
                )
            },
            onOpenWifiSetup = { showWifiSetup = true }
        )
    } else {
        Scaffold(
            topBar = {
                GlassTopBar(
                    title = when (selectedTab) {
                        NavTab.HOME -> if (isBackupViewer) "Safety Monitor" else strings.dashboardTitle
                        NavTab.ORIENTATION_3D -> strings.orientation3DTitle
                        NavTab.HISTORY -> strings.historyTitle
                        NavTab.INSIGHTS -> strings.insightsTitle
                        NavTab.STATUS -> strings.statusTitle
                        NavTab.SETTINGS -> strings.settingsTitle
                    },
                    subtitle = when (selectedTab) {
                        NavTab.HOME -> if (isBackupViewer) "Backup Safety Viewer: $driverPhone" else "${strings.primaryDriverNumber}: $driverPhone"
                        NavTab.ORIENTATION_3D -> strings.orientation3DSubtitle
                        NavTab.INSIGHTS -> strings.insightsSubtitle
                        else -> null
                    },
                    onNotificationClick = { selectedTab = NavTab.HISTORY },
                    hasUnread = notifications.any { it.status.equals("Pending", ignoreCase = true) }
                )
            },
            bottomBar = {
                GlassBottomNavigation(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            },
            containerColor = AutoBackground
        ) { innerPadding ->
            GlassBackgroundCanvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    NavTab.HOME -> {
                        HomeScreen(
                            driverPhone = driverPhone,
                            isAvailable = isAvailable,
                            batteryResult = batteryResult,
                            isCheckingBattery = isCheckingBattery,
                            lastKnownBatteryPercent = lastKnownBatteryPercent,
                            lastKnownBatteryTimestamp = lastKnownBatteryTimestamp,
                            isSimulationMode = isSimulationMode,
                            targetNotificationId = targetNotificationId,
                            onAvailableChange = { viewModel.setAvailable(it) },
                            onCheckBattery = { viewModel.checkBattery() },
                            ntfyTopic = ntfyTopic,
                            deviceIp = deviceIp,
                            notifications = notifications,
                            onTriggerTestCall = { viewModel.triggerTestNotification() },
                            onUpdateStatus = { id, status -> viewModel.updateNotificationStatus(id, status) },
                            onSendReply = { id, reply, onResult -> viewModel.sendReply(id, reply, onResult) },
                            onNavigateToHistory = { selectedTab = NavTab.HISTORY },
                            onNavigateTo3D = { selectedTab = NavTab.ORIENTATION_3D }
                        )
                    }
                    NavTab.ORIENTATION_3D -> {
                        Orientation3DScreen(
                            deviceIp = deviceIp,
                            streamManager = viewModel.imuStreamManager,
                            onUpdateDeviceIp = { viewModel.updateDeviceIp(it) }
                        )
                    }
                    NavTab.HISTORY -> {
                        HistoryScreen(
                            notifications = notifications,
                            targetNotificationId = targetNotificationId,
                            onUpdateStatus = { id, status -> viewModel.updateNotificationStatus(id, status) },
                            onSendReply = { id, reply, onResult -> viewModel.sendReply(id, reply, onResult) },
                            onClearAll = { viewModel.clearHistory() }
                        )
                    }
                    NavTab.INSIGHTS -> {
                        InsightsScreen(
                            notifications = notifications,
                            onGenerateSampleData = { viewModel.generateSampleWeekData() }
                        )
                    }
                    NavTab.STATUS -> {
                        StatusScreen(
                            deviceIp = deviceIp,
                            ntfyTopic = ntfyTopic,
                            apiClient = apiClient,
                            batteryResult = batteryResult,
                            isCheckingBattery = isCheckingBattery,
                            lastKnownBatteryPercent = lastKnownBatteryPercent,
                            lastKnownBatteryTimestamp = lastKnownBatteryTimestamp,
                            onCheckBattery = { viewModel.checkBattery() },
                            isSimulationMode = isSimulationMode,
                            onOpenWifiSetup = { showWifiSetup = true }
                        )
                    }
                    NavTab.SETTINGS -> {
                        SettingsScreen(
                            driverPhone = driverPhone,
                            backupDriverPhone = backupDriverPhone,
                            ntfyTopic = ntfyTopic,
                            deviceIp = deviceIp,
                            appLanguage = appLanguage,
                            alertSound = alertSound,
                            requestTimeout = requestTimeout,
                            isSimulationMode = isSimulationMode,
                            isBackupViewer = isBackupViewer,
                            backupButtonEnabled = backupButtonEnabled,
                            backupSafetyEnabled = backupSafetyEnabled,
                            onSetBackupButtonEnabled = { viewModel.setBackupButtonEnabled(it) },
                            onSetBackupSafetyEnabled = { viewModel.setBackupSafetyEnabled(it) },
                            onUpdatePhone = { viewModel.updateDriverPhone(it) },
                            onUpdateBackupDriverPhone = { viewModel.updateBackupDriverPhone(it) },
                            onUpdateAppLanguage = { viewModel.updateAppLanguage(it) },
                            onUpdateDeviceIp = { viewModel.updateDeviceIp(it) },
                            onUpdateAlertSound = { viewModel.updateAlertSound(it) },
                            onUpdateRequestTimeout = { viewModel.updateRequestTimeout(it) },
                            onSendTestNotification = { onResult -> viewModel.triggerTestNotification(onResult) },
                            onResetPairing = { viewModel.resetPairing() },
                            onOpenWifiSetup = { showWifiSetup = true }
                        )
                    }
                }
            }
        }
    }
}

