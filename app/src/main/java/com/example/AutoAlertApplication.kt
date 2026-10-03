package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.data.AutoAlertDatabase
import com.example.data.AutoAlertPreferences

class AutoAlertApplication : Application() {

    val database by lazy { AutoAlertDatabase.getDatabase(this) }
    val preferences by lazy { AutoAlertPreferences(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST)
                .build()

            val defaultSound = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)

            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_CALLS,
                "Auto Service Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time service call alerts from ESP8266 Auto-Rickshaw button"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(defaultSound, audioAttributes)
            }

            val lowBatteryChannel = NotificationChannel(
                CHANNEL_LOW_BATTERY,
                "Device Battery Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Low battery warnings from device"
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setSound(defaultSound, audioAttributes)
            }

            val foregroundServiceChannel = NotificationChannel(
                CHANNEL_FOREGROUND_SERVICE,
                "Background Listener Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing notification for AutoAlert background connection"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(serviceChannel)
            notificationManager?.createNotificationChannel(lowBatteryChannel)
            notificationManager?.createNotificationChannel(foregroundServiceChannel)
        }
    }

    companion object {
        const val CHANNEL_SERVICE_CALLS = "autoalert_service_calls"
        const val CHANNEL_LOW_BATTERY = "autoalert_low_battery"
        const val CHANNEL_FOREGROUND_SERVICE = "autoalert_foreground_service"
    }
}
