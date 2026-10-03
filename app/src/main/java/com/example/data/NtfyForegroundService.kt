package com.example.data

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.AutoAlertApplication
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NtfyForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null
    private var ntfyManager: NtfyManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as AutoAlertApplication
        val notification = createForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Ensure process network binding is released so service can use Cellular or Wi-Fi freely
        DeviceDiscoveryManager.releaseNetworkBinding(this)

        job?.cancel()
        job = serviceScope.launch {
            // One-time cleanup of the retired backup/tilt topics from older installs (idempotent).
            app.preferences.migrateLegacyTopics()
            // PRIMARY and BACKUP both subscribe to the ONE shared topic; backup filtering happens locally.
            val topic = app.preferences.ntfyTopic.first()
            if (topic.isNotBlank()) {
                if (ntfyManager == null) {
                    ntfyManager = NtfyManager(applicationContext, NotificationRepository(app.database.notificationDao()))
                }
                ntfyManager?.startListening(topic)
            }
        }

        return START_STICKY
    }

    private fun createForegroundNotification(): Notification {
        val largeIconBitmap = try {
            BitmapFactory.decodeResource(resources, R.drawable.img_app_icon)
        } catch (e: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(this, AutoAlertApplication.CHANNEL_FOREGROUND_SERVICE)
            .setSmallIcon(R.drawable.ic_notification_auto)
            .setContentTitle("AutoAlert Driver Service")
            .setContentText("Connected & listening for passenger calls")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(0xFFFFB300.toInt())
            .setOngoing(true)

        if (largeIconBitmap != null) {
            builder.setLargeIcon(largeIconBitmap)
        }

        return builder.build()
    }

    override fun onDestroy() {
        job?.cancel()
        ntfyManager?.cleanup()
        DeviceDiscoveryManager.releaseNetworkBinding(this)
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 8801
    }
}
