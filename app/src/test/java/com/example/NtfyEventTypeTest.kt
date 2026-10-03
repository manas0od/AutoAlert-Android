package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AutoAlertDatabase
import com.example.data.AutoAlertEventType
import com.example.data.NotificationRepository
import com.example.data.NtfyManager
import com.example.data.NtfyMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ONE shared ntfy topic. The event type is read ONLY from the "eventType=..." tag.
 * Titles / message text are deliberately misleading in several tests to prove they are never used.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NtfyEventTypeTest {

    private lateinit var context: Context
    private lateinit var database: AutoAlertDatabase
    private lateinit var repository: NotificationRepository
    private lateinit var ntfyManager: NtfyManager
    private val sharedTopic = "autoalert-kerala-123456"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AutoAlertDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = NotificationRepository(database.notificationDao())
        ntfyManager = NtfyManager(context, repository)
        ntfyManager.startListening(sharedTopic)
    }

    @After
    fun teardown() {
        ntfyManager.cleanup()
        database.close()
    }

    private fun msg(id: String, title: String, body: String, vararg tags: String) = NtfyMessage(
        id = id,
        time = System.currentTimeMillis(),
        event = "message",
        topic = sharedTopic,
        title = title,
        message = body,
        tags = tags.toList()
    )

    // ---------- pure parsing ----------

    @Test
    fun parseEventType_readsEachSupportedType() {
        assertEquals(AutoAlertEventType.BUTTON, NtfyManager.parseEventType(listOf("batt80", "eventType=BUTTON")))
        assertEquals(AutoAlertEventType.BATTERY, NtfyManager.parseEventType(listOf("eventType=BATTERY", "batt15")))
        assertEquals(AutoAlertEventType.WIFI, NtfyManager.parseEventType(listOf("batt80", "eventType=WIFI", "net-safety")))
        assertEquals(AutoAlertEventType.SAFETY, NtfyManager.parseEventType(listOf("batt80", "eventType=SAFETY")))
    }

    @Test
    fun parseEventType_isTolerantOfCaseAndSpaces_butNeverGuesses() {
        assertEquals(AutoAlertEventType.SAFETY, NtfyManager.parseEventType(listOf("  EVENTTYPE=safety  ")))
        assertEquals(AutoAlertEventType.UNKNOWN, NtfyManager.parseEventType(emptyList()))
        assertEquals(AutoAlertEventType.UNKNOWN, NtfyManager.parseEventType(listOf("auto_rickshaw", "call")))
        assertEquals(AutoAlertEventType.UNKNOWN, NtfyManager.parseEventType(listOf("eventType=")))
        assertEquals(AutoAlertEventType.UNKNOWN, NtfyManager.parseEventType(listOf("eventType=NORMAL")))
        assertEquals(AutoAlertEventType.UNKNOWN, NtfyManager.parseEventType(listOf("eventType=UNKNOWN")))
        assertEquals(AutoAlertEventType.UNKNOWN, NtfyManager.parseEventType(listOf("SAFETY")))
    }

    // ---------- BACKUP filter (pure) ----------

    @Test
    fun backupFilter_buttonFollowsButtonToggle_safetyFollowsSafetyToggle() {
        val b = AutoAlertEventType.BUTTON
        val s = AutoAlertEventType.SAFETY
        assertTrue(NtfyManager.isEventAllowedForBackup(b, buttonEnabled = true, safetyEnabled = false))
        assertFalse(NtfyManager.isEventAllowedForBackup(b, buttonEnabled = false, safetyEnabled = true))
        assertTrue(NtfyManager.isEventAllowedForBackup(s, buttonEnabled = false, safetyEnabled = true))
        assertFalse(NtfyManager.isEventAllowedForBackup(s, buttonEnabled = true, safetyEnabled = false))
    }

    @Test
    fun backupFilter_batteryAndWifiAreNotControlledByEitherToggle() {
        for (type in listOf(AutoAlertEventType.BATTERY, AutoAlertEventType.WIFI)) {
            assertTrue(NtfyManager.isEventAllowedForBackup(type, buttonEnabled = false, safetyEnabled = false))
            assertTrue(NtfyManager.isEventAllowedForBackup(type, buttonEnabled = true, safetyEnabled = true))
        }
    }

    @Test
    fun backupFilter_untypedMessagesAreNeverShown() {
        assertFalse(NtfyManager.isEventAllowedForBackup(AutoAlertEventType.UNKNOWN, buttonEnabled = true, safetyEnabled = true))
    }

    // ---------- PRIMARY mode: every type is shown with its own presentation ----------

    @Test
    fun button_isPending_withRequestPrefix_regardlessOfMisleadingText() = runBlocking {
        // Title/body mention safety on purpose: only the tag may decide.
        assertTrue(ntfyManager.handleIncomingMessage(
            msg("b1", "SAFETY ALERT: Vehicle Tilt", "tilt rollover battery wifi", "batt75", "eventType=BUTTON")
        ))
        val saved = repository.allNotifications.first().single()
        assertEquals("Pending", saved.status)
        assertTrue(saved.requestId.startsWith("REQ-"))
        assertEquals("SAFETY ALERT: Vehicle Tilt", saved.title) // title is shown as sent, just never used to classify
    }

    @Test
    fun safety_isSafetyAlert_regardlessOfPlainServiceText() = runBlocking {
        assertTrue(ntfyManager.handleIncomingMessage(
            msg("s1", "Service Required", "Passenger requesting service at auto stand.", "batt75", "eventType=SAFETY")
        ))
        val saved = repository.allNotifications.first().single()
        assertEquals("Safety Alert", saved.status)
        assertTrue(saved.requestId.startsWith("TILT-"))
    }

    @Test
    fun battery_isLowBattery_notAServiceRequest() = runBlocking {
        assertTrue(ntfyManager.handleIncomingMessage(
            msg("bt1", "Service Required", "Passenger requesting service", "batt12", "eventType=BATTERY")
        ))
        val saved = repository.allNotifications.first().single()
        assertEquals("Low Battery", saved.status)
        assertEquals("Low Battery Warning", saved.title)
    }

    @Test
    fun wifi_isItsOwnStatus_notPendingAndNotARideRequest() = runBlocking {
        assertTrue(ntfyManager.handleIncomingMessage(
            msg("w1", "AutoAlert Status", "Switched to backup WiFi (driver's phone) - out of auto stand WiFi range.",
                "batt75", "eventType=WIFI", "net-safety")
        ))
        val saved = repository.allNotifications.first().single()
        assertEquals("Wi-Fi Status", saved.status)
        assertTrue(saved.requestId.startsWith("NET-"))
        assertEquals("AutoAlert Status", saved.title)
    }

    @Test
    fun untypedMessage_isShownToPrimaryAsPlainInfo_neverAsButtonRequest() = runBlocking {
        assertTrue(ntfyManager.handleIncomingMessage(
            msg("u1", "Passenger Ride Call", "Auto needed at Main Stand", "auto_rickshaw", "call")
        ))
        val saved = repository.allNotifications.first().single()
        assertEquals("Info", saved.status)
        assertTrue(saved.requestId.startsWith("MSG-"))
    }

    @Test
    fun duplicateMessageId_isHandledOnlyOnce() = runBlocking {
        val m = msg("dup1", "Service Required", "x", "eventType=BUTTON")
        assertTrue(ntfyManager.handleIncomingMessage(m))
        assertFalse(ntfyManager.handleIncomingMessage(m))
        assertNotNull(repository.allNotifications.first().firstOrNull())
        assertEquals(1, repository.allNotifications.first().size)
    }
}
