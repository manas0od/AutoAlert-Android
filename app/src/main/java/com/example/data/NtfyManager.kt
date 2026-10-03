package com.example.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.AutoAlertApplication
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.util.Collections
import java.util.LinkedHashSet
import java.util.concurrent.TimeUnit

/**
 * Event types published by the ESP8266 on the ONE shared ntfy topic. The type is carried ONLY in an
 * ntfy tag of the form "eventType=<NAME>" - never inferred from the title, message text or topic name.
 * To support a future event, add it here, add it to the `when` expressions in
 * NtfyManager.handleIncomingMessage / isEventAllowedForBackup, and give the firmware the same name.
 */
enum class AutoAlertEventType {
    BUTTON,   // passenger / service button press (Coming/Busy actions)
    BATTERY,  // low-battery warning
    WIFI,     // Wi-Fi / network status (primary <-> safety switch)
    SAFETY,   // confirmed MPU tilt / rollover
    UNKNOWN   // no valid eventType tag
}

/** Which quick-action buttons a system notification gets. */
enum class NotificationActionMode {
    COMING_BUSY,   // BUTTON (service request): "Coming (5 min)" + "Busy"
    SAFETY_COMING, // SAFETY (confirmed tilt): ONE action, "I'm Coming" (+ 5-minute snooze)
    NONE           // BATTERY / WIFI / UNKNOWN: informational, no actions
}

data class NtfyMessage(
    val id: String,
    val time: Long,
    val event: String,
    val topic: String,
    val title: String,
    val message: String,
    val tags: List<String> = emptyList()
)

fun formatBatteryAge(timestampMs: Long?): String {
    if (timestampMs == null || timestampMs <= 0) return "from last alert"
    val diffSec = (System.currentTimeMillis() - timestampMs) / 1000
    val ageText = when {
        diffSec < 60 -> "just now"
        diffSec < 120 -> "1 min ago"
        diffSec < 3600 -> "${diffSec / 60} min ago"
        diffSec < 7200 -> "1 hour ago"
        diffSec < 86400 -> "${diffSec / 3600} hours ago"
        diffSec < 172800 -> "1 day ago"
        else -> "${diffSec / 86400} days ago"
    }
    return "as of $ageText, from last alert"
}

class NtfyManager(
    private val context: Context,
    private val repository: NotificationRepository
) {

    private val preferences by lazy { (context.applicationContext as? AutoAlertApplication)?.preferences ?: AutoAlertPreferences(context) }

    companion object {
        fun extractBatteryPercentFromTags(tags: List<String>): Int? {
            val regex = Regex("""^batt(\d+)$""", RegexOption.IGNORE_CASE)
            for (tag in tags) {
                val match = regex.find(tag.trim())
                if (match != null) {
                    val percent = match.groupValues[1].toIntOrNull()
                    if (percent != null && percent in 0..100) {
                        return percent
                    }
                }
            }
            return null
        }
        const val EVENT_TYPE_TAG_KEY = "eventType"

        /**
         * Reads the event type from ntfy tags such as ["batt75", "eventType=SAFETY"]. The key is matched
         * case-insensitively, the value is upper-cased. Returns UNKNOWN when no valid tag is present.
         */
        fun parseEventType(tags: List<String>): AutoAlertEventType {
            for (raw in tags) {
                val tag = raw.trim()
                val eq = tag.indexOf('=')
                if (eq <= 0) continue
                if (!tag.substring(0, eq).trim().equals(EVENT_TYPE_TAG_KEY, ignoreCase = true)) continue
                val value = tag.substring(eq + 1).trim().uppercase()
                val type = AutoAlertEventType.values().firstOrNull { it != AutoAlertEventType.UNKNOWN && it.name == value }
                if (type != null) return type
            }
            return AutoAlertEventType.UNKNOWN
        }

        /**
         * BACKUP-role local filter. BUTTON follows "Button Messages", SAFETY follows "Safety Messages".
         * BATTERY and WIFI are not controlled by either toggle and are shown as they always were.
         * A message with no valid eventType is never shown on a backup phone.
         */
        fun isEventAllowedForBackup(
            type: AutoAlertEventType,
            buttonEnabled: Boolean,
            safetyEnabled: Boolean
        ): Boolean = when (type) {
            AutoAlertEventType.BUTTON -> buttonEnabled
            AutoAlertEventType.SAFETY -> safetyEnabled
            AutoAlertEventType.BATTERY -> true
            AutoAlertEventType.WIFI -> true
            AutoAlertEventType.UNKNOWN -> false
        }

        // Exactly 15 seconds single source of truth for no-response reminder delay
        const val NO_RESPONSE_REMINDER_DELAY_MS = 15_000L

        // SAFETY: after "I'm Coming" the alert is snoozed this long, then shown again if still unresolved
        const val SAFETY_SNOOZE_MS = 5 * 60 * 1000L

        // Active SAFETY snooze timers: safety event DB id -> its ONE timer. Static (not per NtfyManager instance)
        // because the notification-action receiver creates a fresh NtfyManager for every press; this map is what
        // guarantees at most one timer per safety event no matter which instance schedules it.
        private val safetySnoozeJobs = java.util.concurrent.ConcurrentHashMap<Long, Job>()

        /** Number of safety events that currently have a snooze timer running (for tests / diagnostics). */
        fun pendingSafetySnoozeCount(): Int = safetySnoozeJobs.size

        /** True while this safety event has a snooze timer running. */
        fun hasPendingSafetySnooze(originalId: Long): Boolean = safetySnoozeJobs[originalId]?.isActive == true

        /** Cancels the snooze timer of one safety event (event resolved/cleared). */
        fun cancelSafetySnooze(originalId: Long) {
            safetySnoozeJobs.remove(originalId)?.cancel()
        }

        // Thread-safe LRU set of processed ntfy message IDs to deduplicate across listeners and services
        private val processedMessageIds = Collections.synchronizedSet(object : LinkedHashSet<String>() {
            override fun add(element: String): Boolean {
                val added = super.add(element)
                while (size > 200) {
                    val it = iterator()
                    if (it.hasNext()) {
                        it.next()
                        it.remove()
                    }
                }
                return added
            }
        })

        // Track original request DB IDs that have already scheduled or spawned a reminder (at most ONE reminder per request)
        private val remindedOriginalIds = Collections.synchronizedSet(HashSet<Long>())

        // Active scheduled reminder coroutine jobs: originalNotificationId -> Job
        private val pendingReminderJobs = java.util.concurrent.ConcurrentHashMap<Long, Job>()

        // System notification IDs for active reminders: originalNotificationId -> reminderSystemNotificationId
        private val activeReminderNotifIds = java.util.concurrent.ConcurrentHashMap<Long, Int>()

        // System notification IDs for active original notifications: originalNotificationId -> originalSystemNotificationId
        private val activeOriginalNotifIds = java.util.concurrent.ConcurrentHashMap<Long, Int>()

        // Bidirectional mapping between original DB ID and reminder DB ID
        private val originalToReminderDbId = java.util.concurrent.ConcurrentHashMap<Long, Long>()
        private val reminderToOriginalDbId = java.util.concurrent.ConcurrentHashMap<Long, Long>()

        fun getOriginalNotificationId(id: Long): Long? = reminderToOriginalDbId[id]
        fun getReminderDbId(originalId: Long): Long? = originalToReminderDbId[originalId]

        fun markMessageProcessed(msgId: String): Boolean {
            if (msgId.isBlank()) return true
            return processedMessageIds.add(msgId)
        }

        fun isMessageAlreadyProcessed(msgId: String): Boolean {
            if (msgId.isBlank()) return false
            return processedMessageIds.contains(msgId)
        }

        fun resolveAndDismissReminder(originalOrReminderId: Long, context: Context?) {
            // Determine true original ID
            val originalId = reminderToOriginalDbId[originalOrReminderId] ?: originalOrReminderId
            val reminderDbId = originalToReminderDbId[originalId]

            // 1. Cancel any pending reminder timer job for this request
            pendingReminderJobs.remove(originalId)?.cancel()
            //    (and the SAFETY snooze timer, if this request is a resolved safety event)
            safetySnoozeJobs.remove(originalId)?.cancel()

            // 2. Dismiss any active reminder notification from the system tray
            val reminderSystemId = activeReminderNotifIds.remove(originalId)
            if (reminderSystemId != null && context != null) {
                dismissNotification(context, reminderSystemId)
            }

            // 3. Dismiss original notification if needed
            val originalSystemId = activeOriginalNotifIds.remove(originalId)
            if (originalSystemId != null && context != null) {
                dismissNotification(context, originalSystemId)
            }

            // 4. Clean up ID mappings
            if (reminderDbId != null) {
                reminderToOriginalDbId.remove(reminderDbId)
            }
            originalToReminderDbId.remove(originalId)
        }

        fun dismissAllNotifications(context: Context) {
            try {
                pendingReminderJobs.values.forEach { it.cancel() }
                pendingReminderJobs.clear()
                activeReminderNotifIds.clear()
                activeOriginalNotifIds.clear()
                NotificationManagerCompat.from(context).cancelAll()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun dismissNotification(context: Context, notificationId: Int) {
            try {
                NotificationManagerCompat.from(context).cancel(notificationId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.SECONDS) // Infinite read timeout for long SSE stream
        .connectTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .pingInterval(15, TimeUnit.SECONDS) // Ping every 15s to quickly detect dropped TCP connections / network handover
        .build()

    private val postClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val _messageFlow = MutableSharedFlow<NtfyMessage>()
    val messageFlow = _messageFlow.asSharedFlow()

    private var subscribeJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    // The ONE shared ntfy topic. There is no separate backup or tilt topic.
    private var currentTopic: String = ""

    private var activeCall: okhttp3.Call? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        registerNetworkMonitoring()
    }

    private fun registerNetworkMonitoring() {
        try {
            val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager != null && networkCallback == null) {
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        Log.d("NtfyManager", "Network available. Releasing any local process network binding.")
                        DeviceDiscoveryManager.releaseNetworkBinding(context)
                        // Trigger immediate reconnect if listening
                        if (currentTopic.isNotBlank() && subscribeJob?.isActive == true) {
                            reconnectStream()
                        }
                    }

                    override fun onLost(network: Network) {
                        Log.d("NtfyManager", "Network lost. Clearing socket pool & releasing binding.")
                        DeviceDiscoveryManager.releaseNetworkBinding(context)
                        try {
                            client.connectionPool.evictAll()
                        } catch (e: Exception) {
                            // Ignored
                        }
                        // Interrupt active call so it reconnects over the next available network interface (e.g. mobile data)
                        activeCall?.cancel()
                    }

                    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                        val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        if (hasInternet) {
                            DeviceDiscoveryManager.releaseNetworkBinding(context)
                        }
                    }
                }
                networkCallback = callback
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    connectivityManager.registerDefaultNetworkCallback(callback)
                } else {
                    val request = NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()
                    connectivityManager.registerNetworkCallback(request, callback)
                }
            }
        } catch (e: Exception) {
            Log.e("NtfyManager", "Error registering network callback: ${e.message}")
        }
    }

    private fun reconnectStream() {
        if (currentTopic.isBlank()) return
        serviceScope.launch {
            try {
                activeCall?.cancel()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    /** Subscribes to the ONE shared ntfy topic (same for PRIMARY and BACKUP roles). */
    fun startListening(topic: String) {
        if (topic.isBlank()) return
        currentTopic = topic.trim()
        stopListening(clearTopic = false)

        // Ensure process is unbound from any local Wi-Fi pairing network
        DeviceDiscoveryManager.releaseNetworkBinding(context)

        subscribeJob = serviceScope.launch {
            listenToNtfyTopicStream(currentTopic)
        }
    }

    fun stopListening(clearTopic: Boolean = true) {
        if (clearTopic) {
            currentTopic = ""
        }
        try {
            activeCall?.cancel()
        } catch (e: Exception) {
            // Ignore
        }
        subscribeJob?.cancel()
        subscribeJob = null
    }

    fun cleanup() {
        stopListening(clearTopic = true)
        try {
            val connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            networkCallback?.let {
                connectivityManager?.unregisterNetworkCallback(it)
                networkCallback = null
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    private suspend fun listenToNtfyTopicStream(topic: String) {
        val url = "https://ntfy.sh/$topic/json"
        var backoffMs = 1500L

        while (true) {
            // Ensure no stale network binding is applied to the process
            DeviceDiscoveryManager.releaseNetworkBinding(context)

            try {
                val request = Request.Builder()
                    .url(url)
                    .build()

                val call = client.newCall(request)
                activeCall = call

                call.execute().use { response ->
                    val body = response.body

                    if (response.isSuccessful && body != null) {
                        backoffMs = 1500L // Reset backoff on successful stream connect
                        val reader = BufferedReader(body.charStream())
                        var line: String?

                        while (reader.readLine().also { line = it } != null) {
                            val trimmed = line?.trim() ?: continue
                            if (trimmed.isEmpty() || !trimmed.startsWith("{")) continue

                            try {
                                val json = JSONObject(trimmed)
                                val event = json.optString("event", "")
                                if (event == "message") {
                                    val id = json.optString("id", System.currentTimeMillis().toString())
                                    val time = json.optLong("time", System.currentTimeMillis() / 1000) * 1000
                                    val title = json.optString("title", "Auto-Rickshaw Call")
                                    val messageStr = json.optString("message", "Service requested!")
                                    val msgTopic = json.optString("topic").ifBlank { currentTopic }

                                    val tagsList = mutableListOf<String>()
                                    val tagsArray = json.optJSONArray("tags")
                                    if (tagsArray != null) {
                                        for (i in 0 until tagsArray.length()) {
                                            val t = tagsArray.optString(i, "")
                                            if (t.isNotBlank()) tagsList.add(t)
                                        }
                                    } else {
                                        val tagsStr = json.optString("tags", "")
                                        if (tagsStr.isNotBlank()) {
                                            tagsList.addAll(tagsStr.split(",").map { it.trim() })
                                        }
                                    }

                                    val msg = NtfyMessage(
                                        id = id,
                                        time = time,
                                        event = event,
                                        topic = msgTopic,
                                        title = title,
                                        message = messageStr,
                                        tags = tagsList
                                    )

                                    if (handleIncomingMessage(msg)) {
                                        _messageFlow.emit(msg)
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // If cancelled deliberately (e.g. on network switch), log info
                if (e is java.io.InterruptedIOException || e is java.net.SocketException) {
                    Log.d("NtfyManager", "Stream reconnecting/interrupted: ${e.message}")
                } else {
                    e.printStackTrace()
                }
            } finally {
                activeCall = null
            }

            // Retry stream connection after backoff
            delay(backoffMs)
            backoffMs = (backoffMs * 1.5).toLong().coerceAtMost(20000L)
        }
    }

    /**
     * BACKUP-role filter state. Fails OPEN: if preferences cannot be read, the message is treated as
     * PRIMARY (shown) - a safety alert must never be dropped because of a storage error.
     */
    private suspend fun isBackupFilteredOut(eventType: AutoAlertEventType): Boolean {
        return try {
            if (!preferences.isBackupViewer.first()) {
                false // PRIMARY role: receives and shows every supported event type
            } else {
                !isEventAllowedForBackup(
                    type = eventType,
                    buttonEnabled = preferences.backupButtonEnabled.first(),
                    safetyEnabled = preferences.backupSafetyEnabled.first()
                )
            }
        } catch (e: Exception) {
            Log.e("NtfyManager", "Could not read role/filter preferences; showing message: ${e.message}")
            false
        }
    }

    suspend fun handleIncomingMessage(msg: NtfyMessage): Boolean {
        // Extract battery tag (e.g. "batt64") and cache locally on every incoming message with the tag
        val parsedBattery = extractBatteryPercentFromTags(msg.tags)
        if (parsedBattery != null) {
            val now = if (msg.time > 0) msg.time else System.currentTimeMillis()
            try {
                preferences.updateLastKnownBattery(parsedBattery, now)
            } catch (e: Exception) {
                // Ignore
            }
        }

        if (msg.id.isNotBlank() && isMessageAlreadyProcessed(msg.id)) {
            return false
        }
        markMessageProcessed(msg.id)

        // The event type comes ONLY from the machine-readable "eventType=..." tag set by the ESP8266.
        // Title, message text and topic name are never used to classify an event.
        val eventType = parseEventType(msg.tags)

        // BACKUP role: same shared topic, filtered locally by the persisted toggles.
        if (isBackupFilteredOut(eventType)) {
            Log.d("NtfyManager", "BACKUP filter: ignoring $eventType event")
            return false
        }

        val status = when (eventType) {
            AutoAlertEventType.BUTTON -> "Pending"
            AutoAlertEventType.SAFETY -> "Safety Alert"
            AutoAlertEventType.BATTERY -> "Low Battery"
            AutoAlertEventType.WIFI -> "Wi-Fi Status"
            AutoAlertEventType.UNKNOWN -> "Info"
        }
        val locationLabel = "Auto Stand Module"

        val entityTitle = when (eventType) {
            AutoAlertEventType.SAFETY -> msg.title.ifBlank { "🚨 SAFETY ALERT: Vehicle Tilt / Rollover" }
            AutoAlertEventType.BATTERY -> "Low Battery Warning"
            AutoAlertEventType.BUTTON -> msg.title.ifBlank { "Service Requested" }
            AutoAlertEventType.WIFI -> msg.title.ifBlank { "AutoAlert Status" }
            AutoAlertEventType.UNKNOWN -> msg.title.ifBlank { "AutoAlert Message" }
        }

        val requestPrefix = when (eventType) {
            AutoAlertEventType.SAFETY -> "TILT-"
            AutoAlertEventType.WIFI -> "NET-"
            AutoAlertEventType.UNKNOWN -> "MSG-"
            else -> "REQ-"
        }

        val entity = NotificationEntity(
            title = entityTitle,
            message = msg.message,
            timestamp = if (msg.time > 0) msg.time else System.currentTimeMillis(),
            status = status,
            locationLabel = locationLabel,
            requestId = requestPrefix + (1000..9999).random()
        )

        val insertedId = repository.insertNotification(entity)

        // Derive system notification ID directly from ntfy's unique message ID
        val uniqueSystemId = if (msg.id.isNotBlank()) {
            (msg.id.hashCode() and 0x7FFFFFFF).let { if (it == 0) 1001 else it }
        } else {
            insertedId.toInt()
        }

        activeOriginalNotifIds[insertedId] = uniqueSystemId

        showSystemNotification(
            id = uniqueSystemId,
            title = entity.title,
            message = entity.message,
            isLowBattery = eventType == AutoAlertEventType.BATTERY,
            dbNotificationId = insertedId,
            originalNotificationId = insertedId,
            // BUTTON = Coming + Busy. SAFETY = ONE "I'm Coming" action (5-minute snooze).
            // BATTERY / WIFI / UNKNOWN = informational, no actions.
            actionMode = when (eventType) {
                AutoAlertEventType.BUTTON -> NotificationActionMode.COMING_BUSY
                AutoAlertEventType.SAFETY -> NotificationActionMode.SAFETY_COMING
                else -> NotificationActionMode.NONE
            }
        )

        // Schedule no-response reminder ONLY for genuine BUTTON service requests
        if (eventType == AutoAlertEventType.BUTTON && status == "Pending") {
            scheduleNoResponseReminder(insertedId, msg.id, entity.message, locationLabel)
        }

        return true
    }

    /**
     * SAFETY "I'm Coming" snooze. Hides the alert for [delayMs] (default 5 minutes), then shows the SAFETY
     * notification again - with its single "I'm Coming" action - if the event has not been cleared/resolved.
     *
     * Exactly ONE timer exists per safety event: the registry is keyed by the event's DB id and a new snooze
     * atomically replaces (and cancels) the previous one, so pressing "I'm Coming" again RESETS the 5 minutes.
     * "Cleared/resolved" means the event row was deleted or its status is no longer "Safety Alert".
     */
    fun snoozeSafetyAlert(originalId: Long, systemNotifId: Int, delayMs: Long = SAFETY_SNOOZE_MS) {
        val job = serviceScope.launch(start = CoroutineStart.LAZY) {
            delay(delayMs)

            val self = currentCoroutineContext()[Job]
            val entity = repository.getNotificationById(originalId)
            val stillUnresolved = entity != null && entity.status.equals("Safety Alert", ignoreCase = true)

            // Only act if this is still THE registered timer (a newer press replaces it) and the event is open.
            if (entity != null && stillUnresolved && safetySnoozeJobs[originalId] === self) {
                showSystemNotification(
                    id = systemNotifId,
                    title = entity.title,
                    message = entity.message,
                    isLowBattery = false,
                    dbNotificationId = originalId,
                    originalNotificationId = originalId,
                    actionMode = NotificationActionMode.SAFETY_COMING
                )
            }
            if (self != null) safetySnoozeJobs.remove(originalId, self)
        }
        // Replace-and-cancel in one step, then start: no window where two timers can both be registered.
        safetySnoozeJobs.put(originalId, job)?.cancel()
        job.start()
    }

    private fun scheduleNoResponseReminder(
        notificationId: Long,
        originalMsgId: String,
        originalMsg: String,
        locationLabel: String
    ) {
        // Enforce: At most ONE reminder per original service request
        if (!remindedOriginalIds.add(notificationId)) {
            return
        }

        // Cancel any prior scheduled job for this request
        pendingReminderJobs.remove(notificationId)?.cancel()

        val job = serviceScope.launch {
            // First tier: Exactly 15 seconds delay for primary driver reminder
            delay(NO_RESPONSE_REMINDER_DELAY_MS)

            // Check original request's current status in the database before firing reminder
            val originalEntity = repository.getNotificationById(notificationId)
            if (originalEntity == null || !originalEntity.status.equals("Pending", ignoreCase = true) || originalEntity.replyStatus != null) {
                pendingReminderJobs.remove(notificationId)
                return@launch
            }

            // Create and persist the reminder record
            val reminderEntity = NotificationEntity(
                title = "🚨 Reminder: Call Unacknowledged",
                message = "Unacknowledged service call: $originalMsg. Passenger still awaiting ride!",
                timestamp = System.currentTimeMillis(),
                status = "Pending",
                locationLabel = locationLabel,
                requestId = "REM-" + (1000..9999).random()
            )

            val reminderId = repository.insertNotification(reminderEntity)
            originalToReminderDbId[notificationId] = reminderId
            reminderToOriginalDbId[reminderId] = notificationId

            val reminderSystemId = ((originalMsgId + "_rem").hashCode() and 0x7FFFFFFF).let {
                if (it == 0) ((reminderId.toInt() + 9999) and 0x7FFFFFFF) else it
            }
            activeReminderNotifIds[notificationId] = reminderSystemId

            // Show reminder system notification linking back to originalNotificationId
            showSystemNotification(
                id = reminderSystemId,
                title = "🚨 Reminder: Call Unacknowledged",
                message = "Service call at Auto Stand is waiting for driver response!",
                isLowBattery = false,
                dbNotificationId = reminderId,
                originalNotificationId = notificationId
            )

            // Reminder shown. (The old second tier published an escalation to a separate "-backup" topic;
            // there is only ONE shared topic now, and backup phones already receive BUTTON events directly.)
            pendingReminderJobs.remove(notificationId)
        }

        pendingReminderJobs[notificationId] = job
    }

    private fun showSystemNotification(
        id: Int,
        title: String,
        message: String,
        isLowBattery: Boolean,
        dbNotificationId: Long = id.toLong(),
        originalNotificationId: Long = dbNotificationId,
        actionMode: NotificationActionMode = NotificationActionMode.COMING_BUSY
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("NOTIFICATION_ID", dbNotificationId)
            putExtra("ORIGINAL_NOTIFICATION_ID", originalNotificationId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (isLowBattery) {
            AutoAlertApplication.CHANNEL_LOW_BATTERY
        } else {
            AutoAlertApplication.CHANNEL_SERVICE_CALLS
        }

        val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val largeIconBitmap = try {
            BitmapFactory.decodeResource(context.resources, R.drawable.img_app_icon)
        } catch (e: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_auto)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(if (isLowBattery) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(defaultSound)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .setOnlyAlertOnce(false)
            .setColor(0xFFFFB300.toInt())
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (largeIconBitmap != null) {
            builder.setLargeIcon(largeIconBitmap)
        }

        if (!isLowBattery && actionMode == NotificationActionMode.COMING_BUSY) {
            // Add quick action buttons for Driver direct reply from notification shade / lockscreen
            val acceptIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_REPLY
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, dbNotificationId)
                putExtra(NotificationActionReceiver.EXTRA_ORIGINAL_NOTIFICATION_ID, originalNotificationId)
                putExtra(NotificationActionReceiver.EXTRA_SYSTEM_NOTIF_ID, id)
                putExtra(NotificationActionReceiver.EXTRA_REPLY_STATUS, "coming")
            }
            val acceptPendingIntent = PendingIntent.getBroadcast(
                context,
                id * 10 + 1,
                acceptIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val busyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_REPLY
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, dbNotificationId)
                putExtra(NotificationActionReceiver.EXTRA_ORIGINAL_NOTIFICATION_ID, originalNotificationId)
                putExtra(NotificationActionReceiver.EXTRA_SYSTEM_NOTIF_ID, id)
                putExtra(NotificationActionReceiver.EXTRA_REPLY_STATUS, "busy")
            }
            val busyPendingIntent = PendingIntent.getBroadcast(
                context,
                id * 10 + 2,
                busyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(0, "Coming (5 min)", acceptPendingIntent)
            builder.addAction(0, "Busy", busyPendingIntent)
        } else if (!isLowBattery && actionMode == NotificationActionMode.SAFETY_COMING) {
            // SAFETY: a single "I'm Coming" button. It has its own receiver action so it never marks the
            // event Accepted/Cancelled (the event stays open until cleared) and it starts the 5-minute snooze.
            val safetyComingIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_SAFETY_COMING
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, dbNotificationId)
                putExtra(NotificationActionReceiver.EXTRA_ORIGINAL_NOTIFICATION_ID, originalNotificationId)
                putExtra(NotificationActionReceiver.EXTRA_SYSTEM_NOTIF_ID, id)
            }
            val safetyComingPendingIntent = PendingIntent.getBroadcast(
                context,
                id * 10 + 3,
                safetyComingIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "I'm Coming", safetyComingPendingIntent)
        }

        try {
            val notificationManagerCompat = NotificationManagerCompat.from(context)
            if (notificationManagerCompat.areNotificationsEnabled()) {
                notificationManagerCompat.notify(id, builder.build())
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun publishTestNotification(topic: String, title: String, messageStr: String): Boolean = withContext(Dispatchers.IO) {
        if (topic.isBlank()) return@withContext false

        // Ensure process network binding is released so request routes through default active network (Cellular/Wi-Fi)
        DeviceDiscoveryManager.releaseNetworkBinding(context)

        val url = "https://ntfy.sh/$topic"
        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Title", title)
                .addHeader("Tags", "auto_rickshaw,call,${EVENT_TYPE_TAG_KEY}=BUTTON")
                .post(messageStr.toRequestBody("text/plain".toMediaType()))
                .build()

            postClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun sendReply(topic: String, replyStatus: String): Boolean = withContext(Dispatchers.IO) {
        if (topic.isBlank()) return@withContext false

        // Ensure process network binding is released so request routes through default active network (Cellular/Wi-Fi)
        DeviceDiscoveryManager.releaseNetworkBinding(context)

        val replyTopic = "${topic}-reply"
        val url = "https://ntfy.sh/$replyTopic"
        try {
            val messageBody = "status:$replyStatus"
            val request = Request.Builder()
                .url(url)
                .post(messageBody.toRequestBody("text/plain".toMediaType()))
                .build()

            postClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
