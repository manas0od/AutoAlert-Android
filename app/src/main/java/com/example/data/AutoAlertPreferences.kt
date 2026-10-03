package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auto_alert_prefs")

class AutoAlertPreferences(private val context: Context) {

    companion object {
        private val IS_PAIRED = booleanPreferencesKey("is_paired")
        private val NTFY_TOPIC = stringPreferencesKey("ntfy_topic")
        private val DRIVER_PHONE = stringPreferencesKey("driver_phone")
        private val BACKUP_DRIVER_PHONE = stringPreferencesKey("backup_driver_phone")
        // LEGACY keys from the old multi-topic design. They are no longer written or read for
        // notifications; they exist only so migrateLegacyTopics() can find and delete them.
        private val LEGACY_BACKUP_NTFY_TOPIC = stringPreferencesKey("backup_ntfy_topic")
        private val LEGACY_TILT_TOPIC = stringPreferencesKey("tilt_topic")
        private val DEVICE_IP = stringPreferencesKey("device_ip")
        private val IS_AVAILABLE = booleanPreferencesKey("is_available")
        private val IS_SIMULATION_MODE = booleanPreferencesKey("is_simulation_mode")
        private val ALERT_SOUND = stringPreferencesKey("alert_sound")
        private val REQUEST_TIMEOUT = stringPreferencesKey("request_timeout")
        private val APP_LANGUAGE = stringPreferencesKey("app_language")
        private val IS_BACKUP_VIEWER = booleanPreferencesKey("is_backup_viewer")
        private val VIEWER_PHONE = stringPreferencesKey("viewer_phone")
        // BACKUP-role local message filters (the ESP8266 knows nothing about phones or roles)
        private val BACKUP_BUTTON_ENABLED = booleanPreferencesKey("backup_button_messages_enabled")
        private val BACKUP_SAFETY_ENABLED = booleanPreferencesKey("backup_safety_messages_enabled")
        private val LAST_KNOWN_BATTERY_PERCENT = intPreferencesKey("last_known_battery_percent")
        private val LAST_KNOWN_BATTERY_TIMESTAMP = longPreferencesKey("last_known_battery_timestamp")
    }

    val lastKnownBatteryPercent: Flow<Int?> = context.dataStore.data.map { prefs ->
        prefs[LAST_KNOWN_BATTERY_PERCENT]
    }

    val lastKnownBatteryTimestamp: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[LAST_KNOWN_BATTERY_TIMESTAMP]
    }

    val isBackupViewer: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IS_BACKUP_VIEWER] ?: false
    }

    /** BACKUP role only: show BUTTON events. Default ON. Changing it is gated by the ESP /verify password in the UI. */
    val backupButtonEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[BACKUP_BUTTON_ENABLED] ?: true
    }

    /** BACKUP role only: show SAFETY events. Default ON (a safety alert must not be missed by default). */
    val backupSafetyEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[BACKUP_SAFETY_ENABLED] ?: true
    }

    val viewerPhone: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[VIEWER_PHONE] ?: ""
    }

    val isPaired: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IS_PAIRED] ?: false
    }

    val ntfyTopic: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[NTFY_TOPIC] ?: ""
    }

    val driverPhone: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DRIVER_PHONE] ?: ""
    }

    val backupDriverPhone: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[BACKUP_DRIVER_PHONE] ?: "+91 9447000000"
    }

    val appLanguage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[APP_LANGUAGE] ?: "en"
    }

    val deviceIp: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DEVICE_IP] ?: "192.168.4.1"
    }

    val isAvailable: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IS_AVAILABLE] ?: true
    }

    val isSimulationMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IS_SIMULATION_MODE] ?: false
    }

    val alertSound: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[ALERT_SOUND] ?: "Loud Chime (Default)"
    }

    val requestTimeout: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[REQUEST_TIMEOUT] ?: "5 Minutes"
    }

    suspend fun savePairingData(
        topic: String,
        phone: String,
        password: String = "",
        ip: String,
        isSimulated: Boolean = false,
        backupPhone: String = ""
    ) {
        context.dataStore.edit { prefs ->
            prefs[IS_PAIRED] = true
            prefs[IS_BACKUP_VIEWER] = false   // PRIMARY role
            prefs[NTFY_TOPIC] = topic          // the ONE shared ntfy topic
            prefs[DRIVER_PHONE] = phone
            prefs[DEVICE_IP] = ip
            prefs[IS_SIMULATION_MODE] = isSimulated
            if (backupPhone.isNotBlank()) {
                prefs[BACKUP_DRIVER_PHONE] = backupPhone
            }
            prefs.remove(LEGACY_BACKUP_NTFY_TOPIC)
            prefs.remove(LEGACY_TILT_TOPIC)
        }
    }

    /** BACKUP role: subscribes to the SAME shared topic as the primary phone; events are filtered locally. */
    suspend fun saveBackupViewerData(
        topic: String,
        viewerPhone: String,
        ip: String,
        isSimulated: Boolean = false
    ) {
        context.dataStore.edit { prefs ->
            prefs[IS_PAIRED] = true
            prefs[IS_BACKUP_VIEWER] = true   // BACKUP role (persisted)
            prefs[NTFY_TOPIC] = topic          // the ONE shared ntfy topic
            prefs[DRIVER_PHONE] = viewerPhone
            prefs[VIEWER_PHONE] = viewerPhone
            prefs[DEVICE_IP] = ip
            prefs[IS_SIMULATION_MODE] = isSimulated
            prefs.remove(LEGACY_BACKUP_NTFY_TOPIC)
            prefs.remove(LEGACY_TILT_TOPIC)
            // CRITICAL: Master password is NEVER stored locally for backup viewers
        }
    }

    suspend fun setBackupButtonEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[BACKUP_BUTTON_ENABLED] = enabled
        }
    }

    suspend fun setBackupSafetyEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[BACKUP_SAFETY_ENABLED] = enabled
        }
    }

    /**
     * One-time, idempotent cleanup for installs made before the single-shared-topic architecture.
     *  - Deletes the legacy backup-topic and tilt-topic keys.
     *  - PRIMARY phones keep working untouched: their saved topic already IS the shared topic.
     *  - A BACKUP phone saved the retired separate tilt topic as its only topic; the ESP8266 no longer
     *    publishes there, so it would silently receive nothing forever. It is returned to the pairing screen
     *    so it can re-link and obtain the shared topic (this needs the device password once).
     */
    suspend fun migrateLegacyTopics() {
        context.dataStore.edit { prefs ->
            val hasLegacy = prefs.contains(LEGACY_TILT_TOPIC) || prefs.contains(LEGACY_BACKUP_NTFY_TOPIC)
            if (hasLegacy) {
                val legacyTilt = prefs[LEGACY_TILT_TOPIC] ?: ""
                val isViewer = prefs[IS_BACKUP_VIEWER] ?: false
                val savedTopic = prefs[NTFY_TOPIC] ?: ""
                if (isViewer && legacyTilt.isNotBlank() && savedTopic == legacyTilt) {
                    prefs[IS_PAIRED] = false
                    prefs[IS_BACKUP_VIEWER] = false
                    prefs[NTFY_TOPIC] = ""
                    prefs[VIEWER_PHONE] = ""
                }
                prefs.remove(LEGACY_TILT_TOPIC)
                prefs.remove(LEGACY_BACKUP_NTFY_TOPIC)
            }
        }
    }

    suspend fun updatePhoneLabel(phone: String) {
        context.dataStore.edit { prefs ->
            prefs[DRIVER_PHONE] = phone
        }
    }

    suspend fun updateBackupDriverPhone(backupPhone: String) {
        context.dataStore.edit { prefs ->
            prefs[BACKUP_DRIVER_PHONE] = backupPhone
        }
    }

    suspend fun updateAppLanguage(language: String) {
        context.dataStore.edit { prefs ->
            prefs[APP_LANGUAGE] = language
        }
    }

    suspend fun updateDeviceIp(ip: String) {
        context.dataStore.edit { prefs ->
            prefs[DEVICE_IP] = ip
        }
    }

    suspend fun setAvailable(available: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[IS_AVAILABLE] = available
        }
    }

    suspend fun updateAlertSound(sound: String) {
        context.dataStore.edit { prefs ->
            prefs[ALERT_SOUND] = sound
        }
    }

    suspend fun updateRequestTimeout(timeout: String) {
        context.dataStore.edit { prefs ->
            prefs[REQUEST_TIMEOUT] = timeout
        }
    }

    suspend fun updateLastKnownBattery(percent: Int, timestamp: Long = System.currentTimeMillis()) {
        context.dataStore.edit { prefs ->
            prefs[LAST_KNOWN_BATTERY_PERCENT] = percent
            prefs[LAST_KNOWN_BATTERY_TIMESTAMP] = timestamp
        }
    }

    suspend fun clearPairing() {
        context.dataStore.edit { prefs ->
            prefs[IS_PAIRED] = false
            prefs[IS_BACKUP_VIEWER] = false
            prefs[NTFY_TOPIC] = ""
            prefs[VIEWER_PHONE] = ""
            prefs.remove(LEGACY_BACKUP_NTFY_TOPIC)
            prefs.remove(LEGACY_TILT_TOPIC)
            prefs.remove(BACKUP_BUTTON_ENABLED)
            prefs.remove(BACKUP_SAFETY_ENABLED)
            prefs.remove(LAST_KNOWN_BATTERY_PERCENT)
            prefs.remove(LAST_KNOWN_BATTERY_TIMESTAMP)
        }
    }
}
