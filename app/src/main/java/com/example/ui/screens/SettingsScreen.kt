package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneForwarded
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Esp8266ApiClient
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTextField
import com.example.ui.components.SimulationBadge
import com.example.ui.theme.AutoAmberContainer
import com.example.ui.theme.AutoAmberPrimary
import com.example.ui.theme.AutoBackground
import com.example.ui.theme.AutoGlassBorder
import com.example.ui.theme.AutoOnSurface
import com.example.ui.theme.AutoOnSurfaceVariant
import com.example.ui.theme.AutoStatusCancelled
import com.example.ui.theme.AutoStatusCompleted
import com.example.ui.theme.AutoSurfaceHigh
import com.example.ui.theme.AutoSurfaceHighest
import com.example.util.LocalStrings
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    driverPhone: String,
    backupDriverPhone: String = "",
    ntfyTopic: String,
    deviceIp: String,
    appLanguage: String = "en",
    alertSound: String = "Loud Chime (Default)",
    requestTimeout: String = "5 Minutes",
    isSimulationMode: Boolean = false,
    isBackupViewer: Boolean = false,
    backupButtonEnabled: Boolean = true,
    backupSafetyEnabled: Boolean = true,
    onSetBackupButtonEnabled: (Boolean) -> Unit = {},
    onSetBackupSafetyEnabled: (Boolean) -> Unit = {},
    onUpdatePhone: (String) -> Unit,
    onUpdateBackupDriverPhone: (String) -> Unit = {},
    onUpdateAppLanguage: (String) -> Unit = {},
    onUpdateDeviceIp: (String) -> Unit,
    onUpdateAlertSound: (String) -> Unit = {},
    onUpdateRequestTimeout: (String) -> Unit = {},
    onSendTestNotification: ((Boolean) -> Unit) -> Unit,
    onResetPairing: () -> Unit,
    onOpenWifiSetup: () -> Unit = {}
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val apiClient = remember { Esp8266ApiClient() }

    // BACKUP-role message toggles: the switches show the PERSISTED value. Changing one opens a dialog that
    // requires the ESP8266 /verify password; the value is saved only after verification succeeds.
    var pendingToggle by remember { mutableStateOf<String?>(null) } // "button" | "safety" | null
    var pendingToggleValue by remember { mutableStateOf(false) }
    var toggleVerifyPassword by remember { mutableStateOf("") }
    var toggleVerifyError by remember { mutableStateOf<String?>(null) }
    var isVerifyingToggle by remember { mutableStateOf(false) }

    // Dialog state for revoking backup viewers (Owner only)
    var showRevokeViewersDialog by remember { mutableStateOf(false) }
    var enteredRevokePassword by remember { mutableStateOf("") }
    var revokeVerificationError by remember { mutableStateOf<String?>(null) }
    var isRevokingViewers by remember { mutableStateOf(false) }

    // Dialog state for changing primary phone
    var showChangePhoneDialog by remember { mutableStateOf(false) }
    var enteredPrimaryPassword by remember { mutableStateOf("") }
    var newPrimaryPhoneNumber by remember { mutableStateOf(driverPhone) }
    var primaryVerificationError by remember { mutableStateOf<String?>(null) }
    var isVerifyingPrimary by remember { mutableStateOf(false) }

    // Dialog state for changing backup phone
    var showChangeBackupDialog by remember { mutableStateOf(false) }
    var enteredBackupPassword by remember { mutableStateOf("") }
    var newBackupPhoneNumber by remember { mutableStateOf(backupDriverPhone) }
    var backupVerificationError by remember { mutableStateOf<String?>(null) }
    var isVerifyingBackup by remember { mutableStateOf(false) }

    var statusFeedback by remember { mutableStateOf<String?>(null) }
    var editableIp by remember { mutableStateOf(deviceIp) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AutoBackground)
            .testTag("settings_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isBackupViewer) strings.backupViewerSettingsTitle else strings.settingsTitle,
                    color = AutoAmberPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isSimulationMode) {
                    SimulationBadge()
                }
            }
            Text(
                text = if (isBackupViewer) "Emergency safety & vehicle tilt alerts monitor" else strings.settingsSubtitle,
                color = AutoOnSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Status feedback banner
            AnimatedVisibility(visible = statusFeedback != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AutoAmberContainer.copy(alpha = 0.15f))
                        .border(1.dp, AutoAmberContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = AutoAmberPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusFeedback ?: "",
                            color = AutoOnSurface,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (isBackupViewer) {
                // REDUCED SETTINGS FOR BACKUP VIEWER
                // 1. Role Info Card
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("backup_viewer_info_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = AutoAmberPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.backupViewerRoleBadge,
                                color = AutoAmberPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Linked Phone: $driverPhone\nDevice IP: $deviceIp",
                            color = AutoOnSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "You are linked as a backup viewer. Master vehicle configuration, Wi-Fi credentials, and ride dispatch controls are restricted to the primary driver.",
                            color = AutoOnSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                // 2. Notification Controls Card (BACKUP role: local filters on the shared topic)
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("backup_viewer_notifications_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.backupButtonMessagesTitle,
                                    color = AutoOnSurface,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = strings.backupButtonMessagesSubtitle,
                                    color = AutoOnSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                            Switch(
                                checked = backupButtonEnabled,
                                onCheckedChange = { newValue ->
                                    pendingToggle = "button"
                                    pendingToggleValue = newValue
                                    toggleVerifyPassword = ""
                                    toggleVerifyError = null
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = AutoAmberPrimary
                                ),
                                modifier = Modifier.testTag("backup_viewer_button_toggle")
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.backupSafetyMessagesTitle,
                                    color = AutoOnSurface,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = strings.backupSafetyMessagesSubtitle,
                                    color = AutoOnSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                            Switch(
                                checked = backupSafetyEnabled,
                                onCheckedChange = { newValue ->
                                    pendingToggle = "safety"
                                    pendingToggleValue = newValue
                                    toggleVerifyPassword = ""
                                    toggleVerifyError = null
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = AutoAmberPrimary
                                ),
                                modifier = Modifier.testTag("backup_viewer_safety_toggle")
                            )
                        }

                        Text(
                            text = strings.backupToggleDialogHint,
                            color = AutoAmberContainer,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                // 3. Language Selection Card
                GlassCard(modifier = Modifier.fillMaxWidth().testTag("language_card")) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = AutoAmberPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.languageSection,
                                color = AutoOnSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = strings.languageSubtitle,
                            color = AutoOnSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(AutoSurfaceHighest)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (appLanguage == "en") AutoAmberPrimary else Color.Transparent)
                                    .clickable { onUpdateAppLanguage("en") }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = strings.english,
                                    color = if (appLanguage == "en") Color.Black else AutoOnSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = if (appLanguage == "en") FontWeight.Bold else FontWeight.Normal
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (appLanguage == "ml") AutoAmberPrimary else Color.Transparent)
                                    .clickable { onUpdateAppLanguage("ml") }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = strings.malayalam,
                                    color = if (appLanguage == "ml") Color.Black else AutoOnSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = if (appLanguage == "ml") FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4. Unlink Card
                GlassCard(modifier = Modifier.fillMaxWidth().testTag("unlink_card")) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = AutoStatusCancelled,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.unlinkDevice,
                                color = AutoStatusCancelled,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Unlink this phone from the AutoAlert device. You will no longer receive safety or tilt alerts.",
                            color = AutoOnSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        GlassButton(
                            text = strings.unlinkDevice,
                            icon = Icons.Default.Refresh,
                            onClick = onResetPairing,
                            modifier = Modifier.fillMaxWidth(),
                            testTagStr = "unlink_backup_viewer_button"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 5. About App Card
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = AutoAmberPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.aboutTitle,
                                color = AutoOnSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = strings.aboutDescription,
                            color = AutoOnSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            } else {
                // NORMAL OWNER SETTINGS FLOW
                // 1. Language Selection Toggle Card (English / Malayalam)
                GlassCard(modifier = Modifier.fillMaxWidth().testTag("language_card")) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = AutoAmberPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.languageSection,
                            color = AutoOnSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = strings.languageSubtitle,
                        color = AutoOnSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // English Option
                        val isEnSelected = appLanguage != "ml"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isEnSelected) AutoAmberContainer.copy(alpha = 0.25f) else AutoSurfaceHighest)
                                .border(
                                    width = if (isEnSelected) 1.5.dp else 1.dp,
                                    color = if (isEnSelected) AutoAmberPrimary else AutoGlassBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onUpdateAppLanguage("en") }
                                .padding(vertical = 12.dp, horizontal = 12.dp)
                                .testTag("lang_en_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isEnSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = AutoAmberPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = "English",
                                    color = if (isEnSelected) AutoAmberPrimary else AutoOnSurface,
                                    fontSize = 13.sp,
                                    fontWeight = if (isEnSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }

                        // Malayalam Option
                        val isMlSelected = appLanguage == "ml"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isMlSelected) AutoAmberContainer.copy(alpha = 0.25f) else AutoSurfaceHighest)
                                .border(
                                    width = if (isMlSelected) 1.5.dp else 1.dp,
                                    color = if (isMlSelected) AutoAmberPrimary else AutoGlassBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onUpdateAppLanguage("ml") }
                                .padding(vertical = 12.dp, horizontal = 12.dp)
                                .testTag("lang_ml_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isMlSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = AutoAmberPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = "മലയാളം",
                                    color = if (isMlSelected) AutoAmberPrimary else AutoOnSurface,
                                    fontSize = 13.sp,
                                    fontWeight = if (isMlSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Primary Driver Phone Card
            GlassCard(modifier = Modifier.fillMaxWidth().testTag("primary_driver_card")) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = AutoAmberPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.primaryDriverNumber,
                            color = AutoOnSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = strings.primaryDriverSubtitle,
                        color = AutoOnSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Text(
                        text = driverPhone.ifBlank { strings.notSet },
                        color = AutoAmberPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    GlassButton(
                        text = strings.changePrimaryNumber,
                        icon = Icons.Default.Security,
                        onClick = {
                            showChangePhoneDialog = true
                            enteredPrimaryPassword = ""
                            newPrimaryPhoneNumber = driverPhone
                            primaryVerificationError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTagStr = "change_phone_button"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Backup Driver Phone Card (Escalation)
            GlassCard(modifier = Modifier.fillMaxWidth().testTag("backup_driver_card")) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneForwarded,
                            contentDescription = null,
                            tint = AutoAmberPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.backupDriverNumber,
                            color = AutoOnSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = strings.backupDriverSubtitle,
                        color = AutoOnSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Text(
                        text = backupDriverPhone.ifBlank { strings.notSet },
                        color = if (backupDriverPhone.isNotBlank()) AutoAmberPrimary else AutoOnSurfaceVariant,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    GlassButton(
                        text = strings.changeBackupNumber,
                        icon = Icons.Default.Security,
                        onClick = {
                            showChangeBackupDialog = true
                            enteredBackupPassword = ""
                            newBackupPhoneNumber = backupDriverPhone
                            backupVerificationError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTagStr = "change_backup_phone_button"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. ESP8266 IP Config Card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Router,
                            contentDescription = null,
                            tint = AutoAmberPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.hardwareModuleStatus,
                            color = AutoOnSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    GlassTextField(
                        value = editableIp,
                        onValueChange = { editableIp = it },
                        label = strings.ipAddress,
                        placeholder = "192.168.4.1 or 192.168.1.50",
                        leadingIcon = Icons.Default.Router,
                        testTagStr = "settings_device_ip"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    GlassButton(
                        text = strings.saveChanges,
                        icon = Icons.Default.Refresh,
                        onClick = {
                            onUpdateDeviceIp(editableIp)
                            statusFeedback = "Updated device IP to $editableIp"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTagStr = "save_ip_button"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Alert Sound Card
            val soundOptions = listOf("Loud Chime (Default)", "Subtle Ping", "Continuous Siren", "Voice Announcement")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = AutoAmberPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.alertSoundTitle,
                            color = AutoOnSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Audio tone for incoming IoT service requests",
                        color = AutoOnSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    soundOptions.forEach { option ->
                        val isSelected = option == alertSound
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) AutoAmberContainer.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { onUpdateAlertSound(option) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = option,
                                color = if (isSelected) AutoAmberPrimary else AutoOnSurface,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = AutoAmberPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 6. Request Timeout Card
            val timeoutOptions = listOf("3 Minutes", "5 Minutes", "10 Minutes", "No Timeout")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = AutoAmberPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.requestTimeoutTitle,
                            color = AutoOnSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    timeoutOptions.forEach { option ->
                        val isSelected = option == requestTimeout
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) AutoAmberContainer.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { onUpdateRequestTimeout(option) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = option,
                                color = if (isSelected) AutoAmberPrimary else AutoOnSurface,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = AutoAmberPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 7. Push Notification Test Card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = AutoAmberPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.pushTestTitle,
                            color = AutoOnSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = strings.pushTestSubtitle,
                        color = AutoOnSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    GlassButton(
                        text = strings.sendTestPush,
                        icon = Icons.Default.NotificationsActive,
                        onClick = {
                            onSendTestNotification { success ->
                                statusFeedback = if (success) "Test notification dispatched!" else "Failed to send test push"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTagStr = "test_notification_button"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hardware Button Wi-Fi Setup Card (USB / AP)
            GlassCard(modifier = Modifier.fillMaxWidth().testTag("settings_wifi_setup_card")) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = null,
                            tint = AutoAmberPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.wifiSetupTitle,
                            color = AutoOnSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = strings.wifiSetupSubtitle,
                        color = AutoOnSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    GlassButton(
                        text = "${strings.connectViaUsb} / AP",
                        icon = Icons.Default.Usb,
                        onClick = onOpenWifiSetup,
                        modifier = Modifier.fillMaxWidth(),
                        testTagStr = "open_wifi_setup_settings_button"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Backup Viewers Access Card (Owner only)
            GlassCard(modifier = Modifier.fillMaxWidth().testTag("backup_viewers_section_card")) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = AutoAmberPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.backupViewersSectionTitle,
                            color = AutoOnSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = strings.backupViewersSectionSubtitle,
                        color = AutoOnSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    GlassButton(
                        text = strings.revokeAllViewersButton,
                        icon = Icons.Default.Close,
                        onClick = {
                            enteredRevokePassword = ""
                            revokeVerificationError = null
                            showRevokeViewersDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTagStr = "revoke_backup_viewers_button"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 8. Device Re-pair Card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = AutoStatusCancelled,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.rePairTitle,
                            color = AutoStatusCancelled,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = strings.rePairSubtitle,
                        color = AutoOnSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    GlassButton(
                        text = strings.rePairTitle,
                        icon = Icons.Default.Refresh,
                        onClick = onResetPairing,
                        modifier = Modifier.fillMaxWidth(),
                        testTagStr = "reset_pairing_button"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 9. About App Card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = AutoAmberPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.aboutTitle,
                            color = AutoOnSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = strings.aboutDescription,
                        color = AutoOnSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Dialog: BACKUP toggle change - requires the existing ESP8266 /verify password
        if (pendingToggle != null) {
            AlertDialog(
                onDismissRequest = { if (!isVerifyingToggle) pendingToggle = null },
                containerColor = AutoSurfaceHigh,
                titleContentColor = AutoAmberPrimary,
                textContentColor = AutoOnSurface,
                title = { Text(text = strings.backupToggleDialogTitle) },
                text = {
                    Column {
                        Text(
                            text = (if (pendingToggle == "button") strings.backupButtonMessagesTitle else strings.backupSafetyMessagesTitle) +
                                ": " + (if (pendingToggleValue) "ON" else "OFF"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AutoOnSurface,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Text(
                            text = strings.backupToggleDialogHint,
                            fontSize = 12.sp,
                            color = AutoOnSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        GlassTextField(
                            value = editableIp,
                            onValueChange = { editableIp = it },
                            label = "Device IP",
                            placeholder = "192.168.4.1",
                            leadingIcon = Icons.Default.Router,
                            testTagStr = "dialog_toggle_ip_input"
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        GlassTextField(
                            value = toggleVerifyPassword,
                            onValueChange = { toggleVerifyPassword = it },
                            label = strings.devicePasswordLabel,
                            placeholder = "Enter device password",
                            leadingIcon = Icons.Default.Lock,
                            testTagStr = "dialog_toggle_password_input"
                        )

                        if (toggleVerifyError != null) {
                            Text(
                                text = toggleVerifyError ?: "",
                                color = AutoStatusCancelled,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        if (isVerifyingToggle) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = AutoAmberPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Verifying with ESP8266...",
                                    fontSize = 11.sp,
                                    color = AutoOnSurfaceVariant
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !isVerifyingToggle && toggleVerifyPassword.isNotBlank() && editableIp.isNotBlank(),
                        onClick = {
                            val kind = pendingToggle
                            val newValue = pendingToggleValue
                            val typedPassword = toggleVerifyPassword
                            toggleVerifyPassword = "" // never keep the password around
                            isVerifyingToggle = true
                            toggleVerifyError = null

                            scope.launch {
                                val verifyRes = apiClient.verifyPassword(
                                    ip = editableIp,
                                    password = typedPassword,
                                    isSimulated = isSimulationMode
                                )
                                isVerifyingToggle = false
                                if (verifyRes.success) {
                                    if (kind == "button") {
                                        onSetBackupButtonEnabled(newValue)
                                    } else {
                                        onSetBackupSafetyEnabled(newValue)
                                    }
                                    pendingToggle = null
                                    statusFeedback = if (kind == "button") {
                                        strings.backupButtonMessagesTitle + ": " + (if (newValue) "ON" else "OFF")
                                    } else {
                                        strings.backupSafetyMessagesTitle + ": " + (if (newValue) "ON" else "OFF")
                                    }
                                } else {
                                    toggleVerifyError = verifyRes.message
                                }
                            }
                        },
                        modifier = Modifier.testTag("confirm_toggle_verify_button")
                    ) {
                        Text(if (isVerifyingToggle) "Verifying..." else strings.confirm, color = AutoAmberPrimary)
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isVerifyingToggle,
                        onClick = { pendingToggle = null }
                    ) {
                        Text(strings.cancel, color = AutoOnSurfaceVariant)
                    }
                }
            )
        }

        // Dialog: Update Primary Driver Number
        if (showChangePhoneDialog) {
            AlertDialog(
                onDismissRequest = { showChangePhoneDialog = false },
                containerColor = AutoSurfaceHigh,
                titleContentColor = AutoAmberPrimary,
                textContentColor = AutoOnSurface,
                title = { Text(text = strings.primaryNumberDialogTitle) },
                text = {
                    Column {
                        Text(
                            text = "Enter the ESP8266 device password to update the primary driver phone number.",
                            fontSize = 12.sp,
                            color = AutoOnSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        GlassTextField(
                            value = editableIp,
                            onValueChange = { editableIp = it },
                            label = "Device IP",
                            placeholder = "192.168.4.1",
                            leadingIcon = Icons.Default.Router,
                            testTagStr = "dialog_primary_ip_input"
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        GlassTextField(
                            value = newPrimaryPhoneNumber,
                            onValueChange = { newPrimaryPhoneNumber = it },
                            label = strings.primaryDriverNumber,
                            placeholder = "+91 9876543210",
                            leadingIcon = Icons.Default.Phone,
                            testTagStr = "dialog_new_phone_input"
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        GlassTextField(
                            value = enteredPrimaryPassword,
                            onValueChange = { enteredPrimaryPassword = it },
                            label = strings.devicePasswordLabel,
                            placeholder = "Enter device password",
                            leadingIcon = Icons.Default.Lock,
                            testTagStr = "dialog_password_input"
                        )

                        if (primaryVerificationError != null) {
                            Text(
                                text = primaryVerificationError ?: "",
                                color = AutoStatusCancelled,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        if (isVerifyingPrimary) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = AutoAmberPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Verifying with ESP8266...",
                                    fontSize = 11.sp,
                                    color = AutoOnSurfaceVariant
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !isVerifyingPrimary && enteredPrimaryPassword.isNotBlank() && newPrimaryPhoneNumber.isNotBlank() && editableIp.isNotBlank(),
                        onClick = {
                            isVerifyingPrimary = true
                            primaryVerificationError = null

                            scope.launch {
                                val verifyRes = apiClient.verifyPassword(
                                    ip = editableIp,
                                    password = enteredPrimaryPassword,
                                    isSimulated = isSimulationMode
                                )

                                if (!verifyRes.success) {
                                    isVerifyingPrimary = false
                                    primaryVerificationError = verifyRes.message
                                    return@launch
                                }

                                val pairRes = apiClient.pairDevice(
                                    ip = editableIp,
                                    phone = newPrimaryPhoneNumber,
                                    password = enteredPrimaryPassword,
                                    isSimulated = isSimulationMode
                                )

                                isVerifyingPrimary = false
                                if (pairRes.success) {
                                    onUpdatePhone(newPrimaryPhoneNumber)
                                    onUpdateDeviceIp(editableIp)
                                    showChangePhoneDialog = false
                                    statusFeedback = strings.primaryPhoneUpdatedSuccess
                                } else {
                                    primaryVerificationError = pairRes.message
                                }
                            }
                        },
                        modifier = Modifier.testTag("confirm_verify_button")
                    ) {
                        Text(if (isVerifyingPrimary) "Verifying..." else strings.saveChanges, color = AutoAmberPrimary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showChangePhoneDialog = false }) {
                        Text(strings.cancel, color = AutoOnSurfaceVariant)
                    }
                }
            )
        }

        // Dialog: Update Backup Driver Number
        if (showChangeBackupDialog) {
            AlertDialog(
                onDismissRequest = { showChangeBackupDialog = false },
                containerColor = AutoSurfaceHigh,
                titleContentColor = AutoAmberPrimary,
                textContentColor = AutoOnSurface,
                title = { Text(text = strings.backupNumberDialogTitle) },
                text = {
                    Column {
                        Text(
                            text = "Enter the ESP8266 device password to update the backup driver escalation number.",
                            fontSize = 12.sp,
                            color = AutoOnSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        GlassTextField(
                            value = editableIp,
                            onValueChange = { editableIp = it },
                            label = "Device IP",
                            placeholder = "192.168.4.1",
                            leadingIcon = Icons.Default.Router,
                            testTagStr = "dialog_backup_ip_input"
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        GlassTextField(
                            value = newBackupPhoneNumber,
                            onValueChange = { newBackupPhoneNumber = it },
                            label = strings.backupDriverNumber,
                            placeholder = "+91 9876543211",
                            leadingIcon = Icons.Default.PhoneForwarded,
                            testTagStr = "dialog_new_backup_phone_input"
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        GlassTextField(
                            value = enteredBackupPassword,
                            onValueChange = { enteredBackupPassword = it },
                            label = strings.devicePasswordLabel,
                            placeholder = "Enter device password",
                            leadingIcon = Icons.Default.Lock,
                            testTagStr = "dialog_backup_password_input"
                        )

                        if (backupVerificationError != null) {
                            Text(
                                text = backupVerificationError ?: "",
                                color = AutoStatusCancelled,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        if (isVerifyingBackup) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = AutoAmberPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Updating backup driver...",
                                    fontSize = 11.sp,
                                    color = AutoOnSurfaceVariant
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !isVerifyingBackup && enteredBackupPassword.isNotBlank() && newBackupPhoneNumber.isNotBlank() && editableIp.isNotBlank(),
                        onClick = {
                            isVerifyingBackup = true
                            backupVerificationError = null

                            scope.launch {
                                val updateRes = apiClient.updateBackupDriver(
                                    ip = editableIp,
                                    backupPhone = newBackupPhoneNumber,
                                    password = enteredBackupPassword,
                                    isSimulated = isSimulationMode
                                )

                                isVerifyingBackup = false
                                if (updateRes.success) {
                                    onUpdateBackupDriverPhone(newBackupPhoneNumber)
                                    onUpdateDeviceIp(editableIp)
                                    showChangeBackupDialog = false
                                    statusFeedback = strings.backupPhoneUpdatedSuccess
                                } else {
                                    backupVerificationError = updateRes.message
                                }
                            }
                        },
                        modifier = Modifier.testTag("confirm_backup_verify_button")
                    ) {
                        Text(if (isVerifyingBackup) "Saving..." else strings.saveChanges, color = AutoAmberPrimary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showChangeBackupDialog = false }) {
                        Text(strings.cancel, color = AutoOnSurfaceVariant)
                    }
                }
            )
        }

        // Dialog: Revoke All Backup Viewers (Owner only)
        if (showRevokeViewersDialog) {
            AlertDialog(
                onDismissRequest = { showRevokeViewersDialog = false },
                containerColor = AutoSurfaceHigh,
                titleContentColor = AutoAmberPrimary,
                textContentColor = AutoOnSurface,
                title = { Text(text = strings.revokeAllViewersDialogTitle) },
                text = {
                    Column {
                        Text(
                            text = strings.revokeAllViewersDialogText,
                            fontSize = 12.sp,
                            color = AutoOnSurfaceVariant,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        GlassTextField(
                            value = editableIp,
                            onValueChange = { editableIp = it },
                            label = "Device IP",
                            placeholder = "192.168.4.1",
                            leadingIcon = Icons.Default.Router,
                            testTagStr = "dialog_revoke_ip_input"
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        GlassTextField(
                            value = enteredRevokePassword,
                            onValueChange = { enteredRevokePassword = it },
                            label = strings.devicePasswordLabel,
                            placeholder = "Enter device password",
                            leadingIcon = Icons.Default.Lock,
                            visualTransformation = PasswordVisualTransformation(),
                            testTagStr = "revoke_password_input"
                        )

                        if (revokeVerificationError != null) {
                            Text(
                                text = revokeVerificationError ?: "",
                                color = AutoStatusCancelled,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        if (isRevokingViewers) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = AutoAmberPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = strings.revokingViewers,
                                    fontSize = 11.sp,
                                    color = AutoOnSurfaceVariant
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !isRevokingViewers && enteredRevokePassword.isNotBlank() && editableIp.isNotBlank(),
                        onClick = {
                            isRevokingViewers = true
                            revokeVerificationError = null

                            scope.launch {
                                val result = apiClient.revokeViewers(
                                    ip = editableIp,
                                    password = enteredRevokePassword,
                                    isSimulated = isSimulationMode
                                )
                                isRevokingViewers = false
                                if (result.success) {
                                    showRevokeViewersDialog = false
                                    statusFeedback = result.message.ifBlank { strings.revokeAllViewersSuccess }
                                } else {
                                    revokeVerificationError = result.message
                                }
                            }
                        },
                        modifier = Modifier.testTag("confirm_revoke_viewers_button")
                    ) {
                        Text(if (isRevokingViewers) strings.revokingViewers else strings.confirm, color = AutoAmberPrimary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRevokeViewersDialog = false }) {
                        Text(strings.cancel, color = AutoOnSurfaceVariant)
                    }
                }
            )
        }
    }
}
