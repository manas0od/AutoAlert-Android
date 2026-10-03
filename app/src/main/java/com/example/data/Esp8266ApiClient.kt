package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** [topic] is the ONE shared ntfy topic published to by the ESP8266 (there is no separate backup/tilt topic). */
data class PairResult(
    val success: Boolean,
    val topic: String,
    val message: String
)

/** [topic] is the shared ntfy topic returned by /link-viewer (empty for revoke / on failure). */
data class ViewerResult(
    val success: Boolean,
    val message: String,
    val topic: String = ""
)

data class VerifyResult(
    val success: Boolean,
    val message: String
)

data class PingResult(
    val success: Boolean,
    val message: String
)

data class BatteryResult(
    val success: Boolean,
    val batteryPercent: Int?,
    val voltage: Float?,
    val isCharging: Boolean,
    val message: String
)

data class WifiConfigResult(
    val success: Boolean,
    val ip: String = "",
    val message: String = "",
    val rawResponse: String = ""
)

class Esp8266ApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun pairDevice(
        ip: String,
        phone: String,
        password: String,
        backupPhone: String = "",
        isSimulated: Boolean = false
    ): PairResult = withContext(Dispatchers.IO) {
        if (isSimulated) {
            val generatedTopic = "autoalert-kerala-" + (100000..999999).random()
            return@withContext PairResult(
                success = true,
                topic = generatedTopic,
                message = "Simulated pairing successful with device $ip"
            )
        }

        val formattedIp = cleanIpOrHostname(ip)
        val url = "http://$formattedIp/pair"
        val payload = JSONObject().apply {
            put("phone", phone)
            put("password", password)
            if (backupPhone.isNotBlank()) {
                put("backup_phone", backupPhone)
            }
        }.toString()

        try {
            val request = Request.Builder()
                .url(url)
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful && responseBody.isNotEmpty()) {
                    val json = JSONObject(responseBody)
                    val status = json.optString("status", "")
                    // The ESP8266 returns the ONE shared notification topic as "topic".
                    val topic = json.optString("topic", "")
                    val msg = json.optString("message", "Paired successfully")

                    if (status == "ok" || topic.isNotEmpty()) {
                        PairResult(success = true, topic = topic, message = msg)
                    } else {
                        PairResult(success = false, topic = "", message = msg.ifEmpty { "Invalid response from ESP8266" })
                    }
                } else {
                    var errorMessage = "ESP8266 returned HTTP code ${response.code}"
                    if (responseBody.isNotEmpty()) {
                        try {
                            val json = JSONObject(responseBody)
                            val msgField = json.optString("message", "")
                            if (msgField.isNotBlank()) {
                                errorMessage = msgField
                            }
                        } catch (e: Exception) {
                            // If response isn't JSON, use generic message
                        }
                    }
                    PairResult(success = false, topic = "", message = errorMessage)
                }
            }
        } catch (e: Exception) {
            PairResult(
                success = false,
                topic = "",
                message = "Could not reach ESP8266 at $formattedIp (${e.localizedMessage ?: "Connection failed"}). Ensure phone is on device local WiFi."
            )
        }
    }

    suspend fun updateBackupDriver(
        ip: String,
        backupPhone: String,
        password: String,
        isSimulated: Boolean = false
    ): VerifyResult = withContext(Dispatchers.IO) {
        if (isSimulated) {
            return@withContext VerifyResult(success = true, message = "Backup driver updated (Simulated)")
        }

        val formattedIp = cleanIpOrHostname(ip)
        val url = "http://$formattedIp/backup-driver"
        val payload = JSONObject().apply {
            put("backup_phone", backupPhone)
            put("password", password)
        }.toString()

        try {
            val request = Request.Builder()
                .url(url)
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val json = if (responseBody.isNotEmpty()) JSONObject(responseBody) else JSONObject()
                    val status = json.optString("status", "ok")
                    val msg = json.optString("message", "Backup driver number updated successfully on ESP8266")
                    if (status == "ok" || status == "success") {
                        VerifyResult(success = true, message = msg)
                    } else {
                        VerifyResult(success = false, message = msg)
                    }
                } else {
                    // Fallback to /pair (re-register) with correct field name "phone"
                    val fallbackUrl = "http://$formattedIp/pair"
                    val fallbackPayload = JSONObject().apply {
                        put("phone", backupPhone)
                        put("password", password)
                    }.toString()

                    val fallbackReq = Request.Builder()
                        .url(fallbackUrl)
                        .post(fallbackPayload.toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(fallbackReq).execute().use { fbResponse ->
                        val fbBody = fbResponse.body?.string() ?: ""
                        if (fbResponse.isSuccessful && fbBody.isNotEmpty()) {
                            val fbJson = JSONObject(fbBody)
                            val fbStatus = fbJson.optString("status", "")
                            val fbMsg = fbJson.optString("message", "Phone updated on device")
                            if (fbStatus == "ok" || fbStatus == "success") {
                                VerifyResult(success = true, message = fbMsg)
                            } else {
                                VerifyResult(success = false, message = fbMsg)
                            }
                        } else {
                            VerifyResult(success = false, message = "ESP8266 error code ${fbResponse.code}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            VerifyResult(
                success = false,
                message = "Could not reach ESP8266 at $formattedIp (${e.localizedMessage ?: "Connection error"})"
            )
        }
    }

    suspend fun verifyPassword(ip: String, password: String, isSimulated: Boolean = false): VerifyResult = withContext(Dispatchers.IO) {
        if (isSimulated) {
            return@withContext VerifyResult(success = true, message = "Password verified (Simulated)")
        }

        val formattedIp = cleanIpOrHostname(ip)
        val url = "http://$formattedIp/verify"
        val payload = JSONObject().apply {
            put("password", password)
        }.toString()

        try {
            val request = Request.Builder()
                .url(url)
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful && responseBody.isNotEmpty()) {
                    val json = JSONObject(responseBody)
                    val status = json.optString("status", "")
                    val msg = json.optString("message", "Password verified")

                    if (status == "ok" || status == "success" || json.optBoolean("valid", false)) {
                        VerifyResult(success = true, message = msg)
                    } else {
                        VerifyResult(success = false, message = msg.ifEmpty { "Incorrect password" })
                    }
                } else {
                    var errorMessage = "ESP8266 HTTP ${response.code}"
                    if (responseBody.isNotEmpty()) {
                        try {
                            val json = JSONObject(responseBody)
                            val msgField = json.optString("message", "")
                            if (msgField.isNotBlank()) {
                                errorMessage = msgField
                            }
                        } catch (e: Exception) {
                            // If response isn't JSON, use generic message
                        }
                    }
                    VerifyResult(success = false, message = errorMessage)
                }
            }
        } catch (e: Exception) {
            VerifyResult(
                success = false,
                message = "Connection error to $formattedIp (${e.localizedMessage ?: "Timeout"}). Make sure you are connected to device WiFi."
            )
        }
    }

    suspend fun checkBattery(ip: String, isSimulated: Boolean = false): BatteryResult = withContext(Dispatchers.IO) {
        if (isSimulated) {
            val mockBattery = (45..98).random()
            return@withContext BatteryResult(
                success = true,
                batteryPercent = mockBattery,
                voltage = 3.95f,
                isCharging = false,
                message = "Battery check completed (Simulated)"
            )
        }

        val formattedIp = cleanIpOrHostname(ip)
        val url = "http://$formattedIp/battery"

        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful && responseBody.isNotEmpty()) {
                    val json = JSONObject(responseBody)
                    val percent = json.optInt("battery", json.optInt("percentage", 85))
                    val voltage = json.optDouble("voltage", 4.1).toFloat()
                    val charging = json.optBoolean("charging", false)

                    BatteryResult(
                        success = true,
                        batteryPercent = percent,
                        voltage = voltage,
                        isCharging = charging,
                        message = "Battery status received"
                    )
                } else {
                    var errorMessage = "ESP8266 returned HTTP ${response.code}"
                    if (responseBody.isNotEmpty()) {
                        try {
                            val json = JSONObject(responseBody)
                            val msgField = json.optString("message", "")
                            if (msgField.isNotBlank()) {
                                errorMessage = msgField
                            }
                        } catch (e: Exception) {
                            // If response isn't JSON, use generic message
                        }
                    }
                    BatteryResult(
                        success = false,
                        batteryPercent = null,
                        voltage = null,
                        isCharging = false,
                        message = errorMessage
                    )
                }
            }
        } catch (e: Exception) {
            BatteryResult(
                success = false,
                batteryPercent = null,
                voltage = null,
                isCharging = false,
                message = "Unable to connect to ESP8266 at $formattedIp (${e.localizedMessage ?: "Timeout"}). Ensure local WiFi connection."
            )
        }
    }

    suspend fun pingDevice(ip: String, isSimulated: Boolean = false): PingResult = withContext(Dispatchers.IO) {
        if (isSimulated) {
            return@withContext PingResult(success = true, message = "Device $ip reachable (Simulated)")
        }

        val formattedIp = cleanIpOrHostname(ip)
        val url = "http://$formattedIp/ping"

        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    PingResult(success = true, message = "Device at $formattedIp is online & responsive (HTTP ${response.code})")
                } else {
                    PingResult(success = false, message = "ESP8266 returned HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            PingResult(
                success = false,
                message = "Ping failed to $formattedIp: ${e.localizedMessage ?: "Timeout/Unreachable"}"
            )
        }
    }

    suspend fun configureWifiAp(
        ip: String,
        ssid: String,
        password: String,
        slot: String = "primary",
        currentPassword: String = "",
        isSimulated: Boolean = false
    ): WifiConfigResult = withContext(Dispatchers.IO) {
        if (isSimulated) {
            val simIp = "192.168.1." + (100..220).random()
            return@withContext WifiConfigResult(
                success = true,
                ip = simIp,
                message = "Connected to $ssid ($slot) successfully (Simulated AP)",
                rawResponse = "{\"success\":true,\"ip\":\"$simIp\",\"slot\":\"$slot\"}"
            )
        }

        val formattedIp = cleanIpOrHostname(ip)
        val url = "http://$formattedIp/wifi-config"
        val payload = JSONObject().apply {
            put("ssid", ssid)
            put("password", password)
            put("slot", slot)
            if (currentPassword.isNotBlank()) {
                put("currentPassword", currentPassword)
            }
        }.toString()

        try {
            val request = Request.Builder()
                .url(url)
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful && responseBody.isNotEmpty()) {
                    val json = JSONObject(responseBody)
                    val success = json.optBoolean("success", false) ||
                            json.optString("status").equals("ok", ignoreCase = true) ||
                            json.optString("status").equals("success", ignoreCase = true) ||
                            (json.optString("staIp").isNotBlank() && !json.has("error"))
                    val newIp = json.optString("staIp", "")
                    val errorMsg = json.optString("error", "")
                    val msg = json.optString("message", if (errorMsg.isNotBlank()) errorMsg else if (success) "WiFi configured successfully" else "Failed to configure WiFi")

                    WifiConfigResult(
                        success = success,
                        ip = newIp,
                        message = if (errorMsg.isNotBlank()) errorMsg else msg,
                        rawResponse = responseBody
                    )
                } else {
                    // Try to extract error message from response body
                    var errorMessage = "ESP8266 returned HTTP code ${response.code}"
                    if (responseBody.isNotEmpty()) {
                        try {
                            val json = JSONObject(responseBody)
                            val msgField = json.optString("message", "")
                            if (msgField.isNotBlank()) {
                                errorMessage = msgField
                            }
                        } catch (e: Exception) {
                            // If response isn't JSON, use generic message
                        }
                    }
                    WifiConfigResult(
                        success = false,
                        ip = "",
                        message = errorMessage,
                        rawResponse = responseBody
                    )
                }
            }
        } catch (e: Exception) {
            WifiConfigResult(
                success = false,
                ip = "",
                message = "Could not reach ESP8266 at $formattedIp (${e.localizedMessage ?: "Connection failed"}). Ensure phone is connected to device AP."
            )
        }
    }

    suspend fun linkViewer(
        ip: String,
        password: String,
        viewerPhone: String,
        isSimulated: Boolean = false
    ): ViewerResult = withContext(Dispatchers.IO) {
        if (isSimulated) {
            val simTopic = "autoalert-kerala-" + (100000..999999).random()
            return@withContext ViewerResult(
                success = true,
                message = "Backup viewer linked successfully (Simulated)",
                topic = simTopic
            )
        }

        val formattedIp = cleanIpOrHostname(ip)
        val url = "http://$formattedIp/link-viewer"
        val payload = JSONObject().apply {
            put("password", password)
            put("viewerPhone", viewerPhone)
        }.toString()

        try {
            val request = Request.Builder()
                .url(url)
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful && responseBody.isNotEmpty()) {
                    val json = JSONObject(responseBody)
                    val status = json.optString("status", "")
                    val topic = json.optString("topic", "")  // the ONE shared ntfy topic
                    val msg = json.optString("message", if (status == "ok" || topic.isNotEmpty()) "Backup viewer linked successfully" else "Failed to link backup viewer")
                    if (status == "ok" || status == "success" || topic.isNotEmpty()) {
                        ViewerResult(
                            success = true,
                            message = msg,
                            topic = topic
                        )
                    } else {
                        ViewerResult(
                            success = false,
                            message = msg,
                            topic = ""
                        )
                    }
                } else {
                    var errorMessage = "ESP8266 returned HTTP code ${response.code}"
                    if (responseBody.isNotEmpty()) {
                        try {
                            val json = JSONObject(responseBody)
                            val msgField = json.optString("message", "")
                            if (msgField.isNotBlank()) {
                                errorMessage = msgField
                            }
                        } catch (e: Exception) {
                            // If response isn't JSON, use generic message
                        }
                    }
                    ViewerResult(
                        success = false,
                        message = errorMessage,
                        topic = ""
                    )
                }
            }
        } catch (e: Exception) {
            ViewerResult(
                success = false,
                message = "Could not reach ESP8266 at $formattedIp (${e.localizedMessage ?: "Connection error"}). Ensure phone is on local device WiFi.",
                topic = ""
            )
        }
    }

    suspend fun revokeViewers(
        ip: String,
        password: String,
        isSimulated: Boolean = false
    ): ViewerResult = withContext(Dispatchers.IO) {
        if (isSimulated) {
            return@withContext ViewerResult(
                success = true,
                message = "All backup viewers revoked successfully (Simulated)",
                topic = ""
            )
        }

        val formattedIp = cleanIpOrHostname(ip)
        val url = "http://$formattedIp/revoke-viewers"
        val payload = JSONObject().apply {
            put("password", password)
        }.toString()

        try {
            val request = Request.Builder()
                .url(url)
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = if (responseBody.isNotEmpty()) JSONObject(responseBody) else JSONObject()
                    val status = json.optString("status", "ok")
                    val topic = json.optString("topic", "")  // the ONE shared ntfy topic
                    val msg = json.optString("message", "All backup viewers revoked successfully")
                    if (status == "ok" || status == "success") {
                        ViewerResult(
                            success = true,
                            message = msg,
                            topic = topic
                        )
                    } else {
                        ViewerResult(
                            success = false,
                            message = msg,
                            topic = ""
                        )
                    }
                } else {
                    var errorMessage = "ESP8266 returned HTTP code ${response.code}"
                    if (responseBody.isNotEmpty()) {
                        try {
                            val json = JSONObject(responseBody)
                            val msgField = json.optString("message", "")
                            if (msgField.isNotBlank()) {
                                errorMessage = msgField
                            }
                        } catch (e: Exception) {
                            // If response isn't JSON, use generic message
                        }
                    }
                    ViewerResult(
                        success = false,
                        message = errorMessage,
                        topic = ""
                    )
                }
            }
        } catch (e: Exception) {
            ViewerResult(
                success = false,
                message = "Could not reach ESP8266 at $formattedIp (${e.localizedMessage ?: "Connection error"}). Ensure phone is on local device WiFi.",
                topic = ""
            )
        }
    }

    private fun cleanIpOrHostname(input: String): String {
        var clean = input.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .removeSuffix("/")
            .trim()
        if (clean.isEmpty() || clean.equals("autoalert.local", ignoreCase = true) || clean.equals("autoalert", ignoreCase = true)) {
            clean = "192.168.4.1"
        }
        return clean
    }
}
