package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AutoAlertApplication
import com.example.data.BatteryResult
import com.example.data.Esp8266ApiClient
import com.example.data.NtfyManager
import com.example.data.NotificationEntity
import com.example.data.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AutoAlertApplication
    private val prefs = app.preferences
    private val repository = NotificationRepository(app.database.notificationDao())
    val ntfyManager = NtfyManager(application, repository)
    val apiClient = Esp8266ApiClient()
    val imuStreamManager = com.example.data.imu.ImuStreamManager(viewModelScope)

    // Shared Single Source of Truth for live Battery State
    private val _batteryResult = MutableStateFlow<BatteryResult?>(null)
    val batteryResult: StateFlow<BatteryResult?> = _batteryResult.asStateFlow()

    private val _isCheckingBattery = MutableStateFlow(false)
    val isCheckingBattery: StateFlow<Boolean> = _isCheckingBattery.asStateFlow()

    val lastKnownBatteryPercent: StateFlow<Int?> = prefs.lastKnownBatteryPercent.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val lastKnownBatteryTimestamp: StateFlow<Long?> = prefs.lastKnownBatteryTimestamp.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val isPaired: StateFlow<Boolean> = prefs.isPaired.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val isBackupViewer: StateFlow<Boolean> = prefs.isBackupViewer.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    // BACKUP-role local filters (persisted). Only meaningful when isBackupViewer == true.
    val backupButtonEnabled: StateFlow<Boolean> = prefs.backupButtonEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val backupSafetyEnabled: StateFlow<Boolean> = prefs.backupSafetyEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val ntfyTopic: StateFlow<String> = prefs.ntfyTopic.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val driverPhone: StateFlow<String> = prefs.driverPhone.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val backupDriverPhone: StateFlow<String> = prefs.backupDriverPhone.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val appLanguage: StateFlow<String> = prefs.appLanguage.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "en"
    )

    val deviceIp: StateFlow<String> = prefs.deviceIp.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "autoalert.local"
    )

    val isAvailable: StateFlow<Boolean> = prefs.isAvailable.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val isSimulationMode: StateFlow<Boolean> = prefs.isSimulationMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val alertSound: StateFlow<String> = prefs.alertSound.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "Loud Chime (Default)"
    )

    val requestTimeout: StateFlow<String> = prefs.requestTimeout.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "5 Minutes"
    )

    val notifications: StateFlow<List<NotificationEntity>> = repository.allNotifications.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            // One-time cleanup of the retired backup/tilt topics from older installs (idempotent).
            prefs.migrateLegacyTopics()
            // Start listening to the ONE shared ntfy topic if paired (PRIMARY and BACKUP alike)
            val currentTopic = prefs.ntfyTopic.first()
            if (currentTopic.isNotBlank()) {
                ntfyManager.startListening(currentTopic)
            }
            // Auto check battery on startup
            checkBattery()
        }
    }

    fun checkBattery() {
        viewModelScope.launch {
            _isCheckingBattery.value = true
            val ip = prefs.deviceIp.first()
            val isSim = prefs.isSimulationMode.first()
            val result = apiClient.checkBattery(ip, isSimulated = isSim)
            _batteryResult.value = result
            _isCheckingBattery.value = false
        }
    }

    fun dismissAllNotifications() {
        NtfyManager.dismissAllNotifications(app)
    }

    fun savePairingData(
        topic: String,
        phone: String,
        password: String,
        ip: String,
        isSimulated: Boolean = false,
        backupPhone: String = ""
    ) {
        viewModelScope.launch {
            prefs.savePairingData(topic, phone, password, ip, isSimulated, backupPhone)
            ntfyManager.startListening(topic)
            checkBattery()
        }
    }

    fun saveBackupViewerData(
        topic: String,
        viewerPhone: String,
        ip: String,
        isSimulated: Boolean = false
    ) {
        viewModelScope.launch {
            prefs.saveBackupViewerData(topic, viewerPhone, ip, isSimulated)
            ntfyManager.startListening(topic)
        }
    }

    /** Persist the BACKUP "Button Messages" toggle. The UI calls this ONLY after the ESP /verify password succeeded. */
    fun setBackupButtonEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setBackupButtonEnabled(enabled)
        }
    }

    /** Persist the BACKUP "Safety Messages" toggle. The UI calls this ONLY after the ESP /verify password succeeded. */
    fun setBackupSafetyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setBackupSafetyEnabled(enabled)
        }
    }

    fun setAvailable(available: Boolean) {
        viewModelScope.launch {
            prefs.setAvailable(available)
        }
    }

    fun updateDriverPhone(phone: String) {
        viewModelScope.launch {
            prefs.updatePhoneLabel(phone)
        }
    }

    fun updateBackupDriverPhone(phone: String) {
        viewModelScope.launch {
            prefs.updateBackupDriverPhone(phone)
        }
    }

    fun updateAppLanguage(lang: String) {
        viewModelScope.launch {
            prefs.updateAppLanguage(lang)
        }
    }

    fun updateDeviceIp(ip: String) {
        viewModelScope.launch {
            prefs.updateDeviceIp(ip)
        }
    }

    fun updateAlertSound(sound: String) {
        viewModelScope.launch {
            prefs.updateAlertSound(sound)
        }
    }

    fun updateRequestTimeout(timeout: String) {
        viewModelScope.launch {
            prefs.updateRequestTimeout(timeout)
        }
    }

    fun updateNotificationStatus(id: Long, status: String) {
        viewModelScope.launch {
            repository.updateStatus(id, status)
            val originalId = NtfyManager.getOriginalNotificationId(id) ?: id
            val reminderDbId = NtfyManager.getReminderDbId(originalId)
            if (originalId != id) {
                repository.updateStatus(originalId, status)
            }
            if (reminderDbId != null && reminderDbId != id) {
                repository.updateStatus(reminderDbId, status)
            }
            NtfyManager.resolveAndDismissReminder(id, app)
        }
    }

    fun sendReply(notificationId: Long, replyStatus: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val topic = prefs.ntfyTopic.first()
            val success = ntfyManager.sendReply(topic, replyStatus)
            if (success) {
                val dbStatus = if (replyStatus.equals("coming", ignoreCase = true)) "Accepted" else "Cancelled"
                val originalId = NtfyManager.getOriginalNotificationId(notificationId) ?: notificationId
                val reminderDbId = NtfyManager.getReminderDbId(originalId)
                val replyNow = System.currentTimeMillis()

                repository.updateReplyStatus(notificationId, replyStatus, replyNow)
                repository.updateStatus(notificationId, dbStatus)

                if (originalId != notificationId) {
                    repository.updateReplyStatus(originalId, replyStatus, replyNow)
                    repository.updateStatus(originalId, dbStatus)
                }
                if (reminderDbId != null && reminderDbId != notificationId) {
                    repository.updateReplyStatus(reminderDbId, replyStatus, replyNow)
                    repository.updateStatus(reminderDbId, dbStatus)
                }

                NtfyManager.resolveAndDismissReminder(notificationId, app)
            }
            onResult(success)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun triggerTestNotification(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val topic = prefs.ntfyTopic.first().ifBlank { "autoalert-kerala-demo" }
            val success = ntfyManager.publishTestNotification(
                topic = topic,
                title = "Service Requested",
                messageStr = "Passenger pressed IoT auto call button at Stand #4"
            )
            // If offline or network delayed, ensure notification is also dispatched locally for immediate test
            if (!success) {
                ntfyManager.handleIncomingMessage(
                    com.example.data.NtfyMessage(
                        id = System.currentTimeMillis().toString(),
                        time = System.currentTimeMillis(),
                        event = "message",
                        topic = topic,
                        title = "Service Requested",
                        message = "Passenger pressed IoT auto call button at Stand #4",
                        tags = listOf("eventType=BUTTON")
                    )
                )
            }
            onResult(true)
        }
    }

    fun generateSampleWeekData(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val oneDayMillis = 24L * 60 * 60 * 1000
            val oneHourMillis = 60L * 60 * 1000
            val random = java.util.Random()

            val standLocations = listOf(
                "Auto Stand #4 (Main Gate)",
                "Palarivattom Junction Stand",
                "Kaloor Metro Stand #2",
                "North Railway Station Stand",
                "Kakkanad Stand #1"
            )

            // Generate sample data for the past 14 days (current week + previous week for trends)
            for (dayOffset in 0..13) {
                // Number of requests per day: busier on weekdays and peak evening hours
                val callsForDay = if (dayOffset == 0) 8 else (3 + random.nextInt(7))
                for (callIdx in 0 until callsForDay) {
                    // Pick realistic peak auto stand hours (mostly 9am, 1pm, 5pm, 6pm, 7pm)
                    val hourOfDay = when (random.nextInt(6)) {
                        0 -> 9
                        1 -> 13
                        2 -> 17 // Peak evening
                        3 -> 18 // Busiest hour
                        4 -> 19 // Peak evening
                        else -> 8 + random.nextInt(12)
                    }
                    val minute = random.nextInt(60)
                    val callTimestamp = now - (dayOffset * oneDayMillis) - ((20 - hourOfDay) * oneHourMillis) - (minute * 60 * 1000)

                    val responseDelaySeconds = 15 + random.nextInt(35) // 15 to 50 seconds
                    val replyTimestamp = callTimestamp + (responseDelaySeconds * 1000L)

                    val isAccepted = random.nextDouble() > 0.15
                    val replyStatus = if (isAccepted) "coming" else "busy"
                    val status = if (dayOffset == 0 && callIdx == 0) "Pending" else if (isAccepted) "Accepted" else "Cancelled"

                    val entity = NotificationEntity(
                        title = "Service Requested",
                        message = "Passenger pressed IoT auto call button at ${standLocations[random.nextInt(standLocations.size)]}",
                        timestamp = callTimestamp,
                        status = status,
                        locationLabel = standLocations[random.nextInt(standLocations.size)],
                        requestId = "REQ-${(1000 + random.nextInt(9000))}",
                        replyStatus = if (status == "Pending") null else replyStatus,
                        replyTimestamp = if (status == "Pending") null else replyTimestamp
                    )
                    repository.insertNotification(entity)
                }
            }
            onComplete()
        }
    }

    fun resetPairing() {
        viewModelScope.launch {
            prefs.clearPairing()
            ntfyManager.stopListening()
        }
    }
}
