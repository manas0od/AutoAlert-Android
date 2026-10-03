package com.example

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AutoAlertDatabase
import com.example.data.NotificationRepository
import com.example.data.NtfyManager
import com.example.data.NtfyMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Notification actions per event type + the SAFETY "I'm Coming" 5-minute snooze.
 * Timer tests use short delays (the production constant is NtfyManager.SAFETY_SNOOZE_MS = 5 minutes).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafetyReminderTest {

    private lateinit var context: Context
    private lateinit var database: AutoAlertDatabase
    private lateinit var repository: NotificationRepository
    private lateinit var ntfyManager: NtfyManager
    private lateinit var nm: NotificationManager
    private val createdIds = mutableListOf<Long>()
    private val topic = "autoalert-kerala-123456"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AutoAlertDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = NotificationRepository(database.notificationDao())
        ntfyManager = NtfyManager(context, repository)
        ntfyManager.startListening(topic)
        nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @After
    fun teardown() {
        createdIds.forEach { NtfyManager.cancelSafetySnooze(it) }
        NtfyManager.dismissAllNotifications(context) // also cancels BUTTON no-response reminder timers
        ntfyManager.cleanup()
        database.close()
    }

    private fun msg(id: String, vararg tags: String) = NtfyMessage(
        id = id, time = System.currentTimeMillis(), event = "message", topic = topic,
        title = "T-$id", message = "M-$id", tags = tags.toList()
    )

    private fun posted(): List<Notification> = shadowOf(nm).allNotifications
    private fun actionTitles(n: Notification): List<String> = (n.actions ?: emptyArray()).map { it.title.toString() }

    /** Inserts a SAFETY event through the real handler and returns its DB id, with the tray emptied afterwards. */
    private fun newSafetyEvent(msgId: String): Long = runBlocking {
        assertTrue(ntfyManager.handleIncomingMessage(msg(msgId, "eventType=SAFETY")))
        val id = repository.allNotifications.first().first { it.title == "T-$msgId" }.id
        createdIds += id
        nm.cancelAll()
        id
    }

    // ---------- which buttons each event type gets ----------

    @Test
    fun button_hasComingAndBusy() = runBlocking {
        ntfyManager.handleIncomingMessage(msg("b1", "eventType=BUTTON"))
        assertEquals(listOf("Coming (5 min)", "Busy"), actionTitles(posted().single()))
    }

    @Test
    fun safety_hasOnlyImComing_noComingNoBusy() = runBlocking {
        ntfyManager.handleIncomingMessage(msg("s1", "eventType=SAFETY"))
        val titles = actionTitles(posted().single())
        assertEquals(listOf("I'm Coming"), titles)
        assertFalse(titles.any { it.startsWith("Coming") || it == "Busy" })
    }

    @Test
    fun battery_wifi_unknown_haveNoActions() = runBlocking {
        ntfyManager.handleIncomingMessage(msg("bt", "eventType=BATTERY"))
        ntfyManager.handleIncomingMessage(msg("wf", "eventType=WIFI"))
        ntfyManager.handleIncomingMessage(msg("uk", "auto_rickshaw"))
        val all = posted()
        assertEquals(3, all.size)
        all.forEach { assertTrue(actionTitles(it).isEmpty()) }
    }

    // ---------- snooze timer ----------

    @Test
    fun snooze_hidesAlert_thenShowsItAgainOnce_withOnlyImComing() {
        val id = newSafetyEvent("snz1")
        ntfyManager.snoozeSafetyAlert(id, systemNotifId = 4242, delayMs = 600)
        assertTrue(NtfyManager.hasPendingSafetySnooze(id))

        Thread.sleep(300)
        assertTrue("still snoozed - nothing shown yet", posted().isEmpty())

        Thread.sleep(900)
        val shown = posted().single()
        assertEquals(listOf("I'm Coming"), actionTitles(shown))
        assertFalse("timer unregisters itself after firing", NtfyManager.hasPendingSafetySnooze(id))
    }

    @Test
    fun pressingAgain_resetsTheSnooze_andOnlyOneTimerExists() {
        val id = newSafetyEvent("snz2")
        ntfyManager.snoozeSafetyAlert(id, 4242, delayMs = 1000)   // would fire at t=1000
        Thread.sleep(600)
        ntfyManager.snoozeSafetyAlert(id, 4242, delayMs = 1000)   // reset: now fires at t=1600
        assertEquals("one timer for the event", 1, NtfyManager.pendingSafetySnoozeCount())

        Thread.sleep(700)                                          // t=1300: the FIRST timer's time has passed
        assertTrue("first timer must have been cancelled by the reset", posted().isEmpty())

        Thread.sleep(1000)                                         // t=2300: the second timer fired
        assertEquals(1, posted().size)
    }

    @Test
    fun repeatedPresses_neverCreateDuplicateTimers() {
        val id = newSafetyEvent("snz3")
        repeat(6) { ntfyManager.snoozeSafetyAlert(id, 4242, delayMs = 5000) }
        assertEquals(1, NtfyManager.pendingSafetySnoozeCount())
        // another NtfyManager instance (the receiver makes a new one per press) is deduplicated too
        NtfyManager(context, repository).snoozeSafetyAlert(id, 4242, delayMs = 5000)
        assertEquals(1, NtfyManager.pendingSafetySnoozeCount())
    }

    @Test
    fun differentSafetyEvents_haveIndependentTimers() {
        val a = newSafetyEvent("snz4a")
        val b = newSafetyEvent("snz4b")
        ntfyManager.snoozeSafetyAlert(a, 4242, delayMs = 5000)
        ntfyManager.snoozeSafetyAlert(b, 4343, delayMs = 5000)
        assertEquals(2, NtfyManager.pendingSafetySnoozeCount())
    }

    @Test
    fun clearedEvent_isNotShownAgain_statusChanged() = runBlocking {
        val id = newSafetyEvent("snz5")
        ntfyManager.snoozeSafetyAlert(id, 4242, delayMs = 400)
        repository.updateStatus(id, "Completed")                  // resolved before the snooze ends
        Thread.sleep(1000)
        assertTrue(posted().isEmpty())
        assertFalse(NtfyManager.hasPendingSafetySnooze(id))
    }

    @Test
    fun clearedEvent_isNotShownAgain_rowDeleted() = runBlocking {
        val id = newSafetyEvent("snz6")
        ntfyManager.snoozeSafetyAlert(id, 4242, delayMs = 400)
        repository.deleteNotification(id)
        Thread.sleep(1000)
        assertTrue(posted().isEmpty())
    }

    @Test
    fun resolvingTheEvent_cancelsItsTimerImmediately() {
        val id = newSafetyEvent("snz7")
        ntfyManager.snoozeSafetyAlert(id, 4242, delayMs = 5000)
        assertTrue(NtfyManager.hasPendingSafetySnooze(id))
        NtfyManager.resolveAndDismissReminder(id, context)         // what the status-change path calls
        assertFalse(NtfyManager.hasPendingSafetySnooze(id))
    }

    @Test
    fun productionSnoozeIsFiveMinutes() {
        assertEquals(5 * 60 * 1000L, NtfyManager.SAFETY_SNOOZE_MS)
    }
}
