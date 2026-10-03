package com.example.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.AutoAlertApplication
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == ACTION_SAFETY_COMING) {
            handleSafetyComing(context, intent)
            return
        }
        if (action != ACTION_REPLY) return

        val notifId = intent.getLongExtra(EXTRA_NOTIFICATION_ID, -1L)
        val explicitOriginalId = intent.getLongExtra(EXTRA_ORIGINAL_NOTIFICATION_ID, -1L)
        val systemNotifId = intent.getIntExtra(EXTRA_SYSTEM_NOTIF_ID, if (notifId != -1L) notifId.toInt() else 1001)
        val rawReply = intent.getStringExtra(EXTRA_REPLY_STATUS) ?: "coming"
        val normalizedReply = if (rawReply.contains("busy", ignoreCase = true)) "busy" else "coming"

        // Determine true original request ID
        val targetOriginalId = when {
            explicitOriginalId != -1L -> explicitOriginalId
            notifId != -1L -> NtfyManager.getOriginalNotificationId(notifId) ?: notifId
            else -> -1L
        }
        val pairedReminderId = if (targetOriginalId != -1L) NtfyManager.getReminderDbId(targetOriginalId) else null

        val pendingResult = goAsync()
        val appContext = context.applicationContext as AutoAlertApplication
        val repository = NotificationRepository(appContext.database.notificationDao())
        val ntfyManager = NtfyManager(appContext, repository)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dbStatus = if (normalizedReply == "coming") "Accepted" else "Cancelled"

                // 1. Update database record for clicked notification
                if (notifId != -1L) {
                    repository.updateStatus(notifId, dbStatus)
                    repository.updateReplyStatus(notifId, normalizedReply)
                }

                // 2. Update database record for paired original/reminder request
                if (targetOriginalId != -1L && targetOriginalId != notifId) {
                    repository.updateStatus(targetOriginalId, dbStatus)
                    repository.updateReplyStatus(targetOriginalId, normalizedReply)
                }
                if (pairedReminderId != null && pairedReminderId != notifId) {
                    repository.updateStatus(pairedReminderId, dbStatus)
                    repository.updateReplyStatus(pairedReminderId, normalizedReply)
                }

                // 3. Cancel any pending reminder job and dismiss any already-shown reminder notification
                if (targetOriginalId != -1L) {
                    NtfyManager.resolveAndDismissReminder(targetOriginalId, context)
                } else if (notifId != -1L) {
                    NtfyManager.resolveAndDismissReminder(notifId, context)
                }

                // 4. Send ntfy reply POST: 'status:coming' or 'status:busy' for the ONE shared topic
                //    (published to "<topic>-reply", which the ESP8266 polls)
                val topic = appContext.preferences.ntfyTopic.first()
                if (topic.isNotBlank()) {
                    ntfyManager.sendReply(topic, normalizedReply)
                }

                // 5. Update the notification in the tray to show confirmation
                val replyText = if (normalizedReply == "coming") "Coming" else "Busy"
                val largeIconBitmap = try {
                    BitmapFactory.decodeResource(context.resources, R.drawable.img_app_icon)
                } catch (e: Exception) {
                    null
                }

                val confirmationBuilder = NotificationCompat.Builder(context, AutoAlertApplication.CHANNEL_SERVICE_CALLS)
                    .setSmallIcon(R.drawable.ic_notification_auto)
                    .setContentTitle("AutoAlert: Reply Sent")
                    .setContentText("You replied: $replyText")
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText("You replied: $replyText\nResponse sent to Auto Stand.")
                    )
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setColor(0xFFFFB300.toInt())
                    .setAutoCancel(true)
                    .setTimeoutAfter(7000L)

                if (largeIconBitmap != null) {
                    confirmationBuilder.setLargeIcon(largeIconBitmap)
                }

                val notificationManager = NotificationManagerCompat.from(context)
                if (notificationManager.areNotificationsEnabled()) {
                    notificationManager.notify(systemNotifId, confirmationBuilder.build())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * SAFETY "I'm Coming": sends the existing "coming" reply through the shared topic's "<topic>-reply",
     * records it, and snoozes the safety alert for 5 minutes. Unlike the BUTTON Coming/Busy flow this does NOT
     * change the event's status (it stays "Safety Alert" = unresolved), so the alert comes back after the
     * snooze unless the event was cleared in the meantime.
     */
    private fun handleSafetyComing(context: Context, intent: Intent) {
        val notifId = intent.getLongExtra(EXTRA_NOTIFICATION_ID, -1L)
        val explicitOriginalId = intent.getLongExtra(EXTRA_ORIGINAL_NOTIFICATION_ID, -1L)
        val originalId = if (explicitOriginalId != -1L) explicitOriginalId else notifId
        if (originalId == -1L) return
        val systemNotifId = intent.getIntExtra(EXTRA_SYSTEM_NOTIF_ID, originalId.toInt())

        val pendingResult = goAsync()
        val appContext = context.applicationContext as AutoAlertApplication
        val repository = NotificationRepository(appContext.database.notificationDao())
        val ntfyManager = NtfyManager(appContext, repository)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Record the reply only; status stays "Safety Alert".
                repository.updateReplyStatus(originalId, "coming")

                // 2. Start (or RESET) the single 5-minute snooze timer FIRST, so a slow/failed network call
                //    below can never lose the snooze.
                ntfyManager.snoozeSafetyAlert(originalId, systemNotifId)

                // 3. Existing reply mechanism: POST to "<shared topic>-reply" with the same "coming" reply.
                val topic = appContext.preferences.ntfyTopic.first()
                val sent = if (topic.isNotBlank()) ntfyManager.sendReply(topic, "coming") else false

                // 4. Replace the alert in the tray with a short confirmation (the alert is now snoozed).
                val body = if (sent) {
                    "You replied: I'm Coming\nResponse sent to Auto Stand.\nThis safety alert returns in 5 minutes if it is not cleared."
                } else {
                    "You replied: I'm Coming\nThe reply could NOT be sent (no internet).\nThis safety alert returns in 5 minutes if it is not cleared."
                }
                val largeIconBitmap = try {
                    BitmapFactory.decodeResource(context.resources, R.drawable.img_app_icon)
                } catch (e: Exception) {
                    null
                }
                val confirmationBuilder = NotificationCompat.Builder(context, AutoAlertApplication.CHANNEL_SERVICE_CALLS)
                    .setSmallIcon(R.drawable.ic_notification_auto)
                    .setContentTitle("AutoAlert: Safety alert snoozed")
                    .setContentText("I'm Coming sent. Reminder in 5 minutes.")
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setColor(0xFFFFB300.toInt())
                    .setAutoCancel(true)
                    .setTimeoutAfter(7000L)
                if (largeIconBitmap != null) {
                    confirmationBuilder.setLargeIcon(largeIconBitmap)
                }
                val notificationManager = NotificationManagerCompat.from(context)
                if (notificationManager.areNotificationsEnabled()) {
                    notificationManager.notify(systemNotifId, confirmationBuilder.build())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SAFETY_COMING = "com.example.autoalert.ACTION_SAFETY_COMING"
        const val ACTION_REPLY = "com.example.autoalert.ACTION_REPLY"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_ORIGINAL_NOTIFICATION_ID = "extra_original_notification_id"
        const val EXTRA_SYSTEM_NOTIF_ID = "extra_system_notif_id"
        const val EXTRA_REPLY_STATUS = "extra_reply_status"
    }
}
