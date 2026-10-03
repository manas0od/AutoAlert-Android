package com.example.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.hoho.android.usbserial.driver.Ch34xSerialDriver
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.Cp21xxSerialDriver
import com.hoho.android.usbserial.driver.FtdiSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume

data class DiscoveredUsbDevice(
    val device: UsbDevice,
    val driver: UsbSerialDriver?,
    val displayName: String,
    val chipType: String,
    val vendorId: Int,
    val productId: Int,
    val hasPermission: Boolean
)

object UsbWifiProtocolParser {
    const val CMD_PREFIX = "CONFIG_WIFI:"
    const val RESULT_PREFIX = "WIFI_RESULT:"
    const val DEFAULT_TIMEOUT_MS = 12_000L
    const val BAUD_RATE = 115200

    fun formatCommand(ssid: String, password: String, slot: String = "primary"): String {
        val json = JSONObject().apply {
            put("ssid", ssid)
            put("password", password)
            put("slot", slot)
        }
        return "$CMD_PREFIX$json\n"
    }

    fun parseLine(rawLine: String): WifiConfigResult? {
        val trimmed = rawLine.trim()
        if (!trimmed.startsWith(RESULT_PREFIX)) return null
        val payload = trimmed.substring(RESULT_PREFIX.length).trim()
        return try {
            val json = JSONObject(payload)
            val success = json.optBoolean("success", false) ||
                    json.optString("status").equals("ok", ignoreCase = true) ||
                    json.optString("status").equals("success", ignoreCase = true) ||
                    (json.optString("ip").isNotBlank() && !json.has("error"))
            val ip = json.optString("ip", "")
            val errorMsg = json.optString("error", "")
            val msg = json.optString("message", if (errorMsg.isNotBlank()) errorMsg else if (success) "WiFi configured successfully" else "Failed to connect to WiFi")

            WifiConfigResult(
                success = success,
                ip = ip,
                message = if (errorMsg.isNotBlank()) errorMsg else msg,
                rawResponse = payload
            )
        } catch (e: Exception) {
            WifiConfigResult(
                success = false,
                ip = "",
                message = "Invalid JSON response: ${e.message}",
                rawResponse = payload
            )
        }
    }
}

class UsbSerialManager(private val context: Context) {

    private val usbManager: UsbManager? =
        context.getSystemService(Context.USB_SERVICE) as? UsbManager

    companion object {
        private const val TAG = "UsbSerialManager"
        const val ACTION_USB_PERMISSION = "com.example.autoalert.USB_PERMISSION"
    }

    /**
     * Builds a prober including standard default drivers and fallback custom entries
     * for various CH340, CP2102, and FTDI chips commonly paired with ESP8266.
     */
    private fun getProber(): UsbSerialProber {
        val customTable = ProbeTable()
        // WCH CH340 / CH341 chips (ESP8266 NodeMCU / D1 Mini)
        customTable.addProduct(0x1a86, 0x7523, Ch34xSerialDriver::class.java)
        customTable.addProduct(0x1a86, 0x5523, Ch34xSerialDriver::class.java)
        customTable.addProduct(0x1a86, 0x5512, Ch34xSerialDriver::class.java)
        customTable.addProduct(0x1a86, 0x0445, Ch34xSerialDriver::class.java)
        customTable.addProduct(0x1a86, 0x55d4, Ch34xSerialDriver::class.java)
        // Silicon Labs CP2102 / CP2104
        customTable.addProduct(0x10c4, 0xea60, Cp21xxSerialDriver::class.java)
        // FTDI FT232R
        customTable.addProduct(0x0403, 0x6001, FtdiSerialDriver::class.java)

        return UsbSerialProber(customTable)
    }

    /**
     * Scans for attached USB devices and identifies serial converters (CH340, etc.)
     */
    fun findConnectedSerialDevices(): List<DiscoveredUsbDevice> {
        val manager = usbManager ?: return emptyList()
        val results = mutableListOf<DiscoveredUsbDevice>()

        val defaultProber = UsbSerialProber.getDefaultProber()
        val customProber = getProber()

        val defaultDrivers = defaultProber.findAllDrivers(manager)
        val customDrivers = customProber.findAllDrivers(manager)

        val driverMap = mutableMapOf<UsbDevice, UsbSerialDriver>()
        for (d in defaultDrivers) {
            driverMap[d.device] = d
        }
        for (d in customDrivers) {
            if (!driverMap.containsKey(d.device)) {
                driverMap[d.device] = d
            }
        }

        val attachedDevices = manager.deviceList.values
        for (device in attachedDevices) {
            val driver = driverMap[device]
            val hasPermission = manager.hasPermission(device)
            val chipName = detectChipName(device.vendorId, device.productId, driver)
            val displayName = device.productName?.ifBlank { null }
                ?: "$chipName (VID:${Integer.toHexString(device.vendorId).uppercase()})"

            if (hasPermission) {
                Log.i(TAG, "[USB] Permission granted at detection for ${device.deviceName} ($chipName)")
            } else {
                Log.d(TAG, "[USB] Permission NOT granted at detection for ${device.deviceName} ($chipName)")
            }

            results.add(
                DiscoveredUsbDevice(
                    device = device,
                    driver = driver,
                    displayName = displayName,
                    chipType = chipName,
                    vendorId = device.vendorId,
                    productId = device.productId,
                    hasPermission = hasPermission
                )
            )
        }

        return results
    }

    private fun detectChipName(vid: Int, pid: Int, driver: UsbSerialDriver?): String {
        return when {
            vid == 0x1A86 -> "ESP8266 (CH340/CH341 Serial)"
            vid == 0x10C4 -> "ESP8266 (CP2102/CP2104 Serial)"
            vid == 0x0403 -> "FTDI Serial Converter"
            vid == 0x2341 || vid == 0x2A03 -> "Arduino / CDC USB Serial"
            driver is Ch34xSerialDriver -> "ESP8266 (CH340 Serial)"
            driver is Cp21xxSerialDriver -> "ESP8266 (CP210x Serial)"
            driver is CdcAcmSerialDriver -> "CDC/ACM Serial Device"
            driver is FtdiSerialDriver -> "FTDI Serial Device"
            driver != null -> "USB-to-Serial Device"
            else -> "USB Peripheral"
        }
    }

    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager?.hasPermission(device) == true
    }

    fun requestUsbPermission(
        context: Context,
        device: UsbDevice,
        onPermissionResult: (Boolean) -> Unit
    ) {
        val manager = usbManager ?: run {
            onPermissionResult(false)
            return
        }

        if (manager.hasPermission(device)) {
            Log.i(TAG, "[USB] Permission granted at detection for ${device.deviceName}")
            onPermissionResult(true)
            return
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val permissionAction = "$ACTION_USB_PERMISSION.detection.${device.deviceId}.${System.currentTimeMillis()}"
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            device.deviceId,
            Intent(permissionAction).setPackage(context.packageName),
            flags
        )

        val filter = IntentFilter(permissionAction)
        var receiver: BroadcastReceiver? = null
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == permissionAction) {
                    try {
                        ctx.unregisterReceiver(this)
                    } catch (_: Exception) {}
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted) {
                        Log.i(TAG, "[USB] Permission granted at detection for ${device.deviceName}")
                    } else {
                        Log.w(TAG, "[USB] Permission denied at detection for ${device.deviceName}")
                    }
                    onPermissionResult(granted)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        manager.requestPermission(device, permissionIntent)
    }

    suspend fun requestUsbPermissionSuspend(
        device: UsbDevice,
        timeoutMs: Long = 30_000L
    ): Boolean = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine<Boolean> { continuation ->
            val manager = usbManager
            if (manager == null) {
                if (continuation.isActive) continuation.resume(false)
                return@suspendCancellableCoroutine
            }

            if (manager.hasPermission(device)) {
                if (continuation.isActive) continuation.resume(true)
                return@suspendCancellableCoroutine
            }

            val permissionAction = "$ACTION_USB_PERMISSION.connection.${device.deviceId}.${System.currentTimeMillis()}"
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val permissionIntent = PendingIntent.getBroadcast(
                context,
                device.deviceId + 1000,
                Intent(permissionAction).setPackage(context.packageName),
                flags
            )

            val filter = IntentFilter(permissionAction)
            var isResumed = false
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.action == permissionAction) {
                        try {
                            ctx.unregisterReceiver(this)
                        } catch (_: Exception) {}

                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        if (!isResumed && continuation.isActive) {
                            isResumed = true
                            continuation.resume(granted)
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }

            continuation.invokeOnCancellation {
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: Exception) {}
            }

            try {
                manager.requestPermission(device, permissionIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting USB permission at connection open", e)
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: Exception) {}
                if (!isResumed && continuation.isActive) {
                    isResumed = true
                    continuation.resume(false)
                }
            }
        }
    } ?: false

    /**
     * Executes the USB Serial WiFi configuration protocol:
     * 1. Opens connection to the device at 115200 baud
     * 2. Sends CONFIG_WIFI:{"ssid":"...","password":"..."}\n
     * 3. Listens for lines starting with WIFI_RESULT: (ignores debug lines, streams them to onDebugLog)
     * 4. Parses JSON after WIFI_RESULT:
     * 5. Times out after timeoutMs with "No response from device — check the cable connection"
     */
    suspend fun configureWifiOverUsb(
        driver: UsbSerialDriver?,
        ssid: String,
        password: String,
        slot: String = "primary",
        isSimulated: Boolean = false,
        timeoutMs: Long = UsbWifiProtocolParser.DEFAULT_TIMEOUT_MS,
        onDebugLog: (String) -> Unit = {}
    ): WifiConfigResult = withContext(Dispatchers.IO) {
        if (isSimulated || driver == null) {
            // Simulated flow for emulator/testing
            return@withContext runSimulatedUsbExchange(ssid, password, slot, onDebugLog)
        }

        val manager = usbManager ?: return@withContext WifiConfigResult(
            success = false,
            ip = "",
            message = "USB Host is not supported on this device"
        )

        // Resolve latest, non-stale UsbDevice and driver instance from current deviceList
        val currentDevice = manager.deviceList.values.find {
            it.deviceId == driver.device.deviceId ||
            (it.vendorId == driver.device.vendorId && it.productId == driver.device.productId)
        } ?: driver.device

        val customProber = getProber()
        val activeDriver = customProber.probeDevice(currentDevice)
            ?: UsbSerialProber.getDefaultProber().probeDevice(currentDevice)
            ?: driver
        val targetDevice = activeDriver.device
        val chipName = detectChipName(targetDevice.vendorId, targetDevice.productId, activeDriver)

        // 1. Immediately before actually opening the serial port, check usbManager.hasPermission(device) again
        var hasPermissionAtOpen = manager.hasPermission(targetDevice)
        if (hasPermissionAtOpen) {
            onDebugLog("[USB] Permission granted at connection open for ${targetDevice.productName ?: chipName}")
            Log.i(TAG, "[USB] Permission granted at connection open for ${targetDevice.deviceName}")
        } else {
            // 2. If permission is missing at that point, request it via usbManager.requestPermission()
            // with a proper PendingIntent and a registered BroadcastReceiver, and only proceed to open
            // the connection after receiving the permission-granted callback
            onDebugLog("[USB] Permission missing at connection open for ${targetDevice.productName ?: chipName}. Requesting permission from user...")
            Log.w(TAG, "[USB] Permission missing at connection open for ${targetDevice.deviceName}. Requesting via PendingIntent...")

            val granted = requestUsbPermissionSuspend(targetDevice)
            if (granted && manager.hasPermission(targetDevice)) {
                hasPermissionAtOpen = true
                onDebugLog("[USB] Permission granted at connection open for ${targetDevice.productName ?: chipName}")
                Log.i(TAG, "[USB] Permission granted at connection open (via callback) for ${targetDevice.deviceName}")
            } else {
                onDebugLog("[USB] Permission denied at connection open for ${targetDevice.productName ?: chipName}")
                Log.e(TAG, "[USB] Permission denied at connection open for ${targetDevice.deviceName}")
                return@withContext WifiConfigResult(
                    success = false,
                    ip = "",
                    message = "USB permission not granted for ESP8266"
                )
            }
        }

        var connection: UsbDeviceConnection? = null
        var port: UsbSerialPort? = null

        try {
            connection = manager.openDevice(targetDevice)
                ?: return@withContext WifiConfigResult(
                    success = false,
                    ip = "",
                    message = "Could not open USB device connection. Ensure cable is firmly connected."
                )

            if (activeDriver.ports.isEmpty()) {
                return@withContext WifiConfigResult(
                    success = false,
                    ip = "",
                    message = "No serial ports found on USB device"
                )
            }

            port = activeDriver.ports[0]
            port.open(connection)

            // 115200 baud, 8 data bits, 1 stop bit, no parity (matches Serial.begin(115200))
            port.setParameters(
                UsbWifiProtocolParser.BAUD_RATE,
                8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )

            // Keep DTR and RTS low so ESP8266 is not held in reset
            try {
                port.dtr = false
                port.rts = false
            } catch (e: Exception) {
                Log.w(TAG, "Could not set DTR/RTS: ${e.message}")
            }

            onDebugLog("[USB] Connected to ${driver.device.productName ?: "ESP8266"} @ 115200 baud")

            // Format command: CONFIG_WIFI:{"ssid":"...","password":"...","slot":"..."}\n
            val command = UsbWifiProtocolParser.formatCommand(ssid, password, slot)
            onDebugLog("[TX] $command".trimEnd())

            // Transmit over serial connection
            port.write(command.toByteArray(Charsets.UTF_8), 2000)

            // Listen for WIFI_RESULT: response line
            val buffer = ByteArray(1024)
            val lineAccumulator = StringBuilder()
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                val bytesRead = try {
                    port.read(buffer, 250)
                } catch (e: IOException) {
                    Log.w(TAG, "Read error: ${e.message}")
                    0
                }

                if (bytesRead > 0) {
                    val incoming = String(buffer, 0, bytesRead, Charsets.UTF_8)
                    lineAccumulator.append(incoming)

                    // Process complete lines
                    while (lineAccumulator.contains("\n")) {
                        val newlineIndex = lineAccumulator.indexOf("\n")
                        val line = lineAccumulator.substring(0, newlineIndex).trimEnd('\r')
                        lineAccumulator.delete(0, newlineIndex + 1)

                        if (line.isBlank()) continue

                        // Check for exact protocol prefix: WIFI_RESULT:
                        if (line.startsWith(UsbWifiProtocolParser.RESULT_PREFIX)) {
                            onDebugLog("[RX-PROTOCOL] $line")
                            val result = UsbWifiProtocolParser.parseLine(line)
                            if (result != null) {
                                return@withContext result
                            }
                        } else {
                            // Normal debug log line from ESP8266 firmware — ignore for result, forward to log console
                            onDebugLog("[DEBUG] $line")
                        }
                    }
                }

                delay(50)
            }

            // Timeout occurred without receiving WIFI_RESULT:
            return@withContext WifiConfigResult(
                success = false,
                ip = "",
                message = "No response from device — check the cable connection"
            )

        } catch (e: Exception) {
            Log.e(TAG, "USB Serial communication error", e)
            return@withContext WifiConfigResult(
                success = false,
                ip = "",
                message = "USB communication error: ${e.localizedMessage ?: "I/O failure"}"
            )
        } finally {
            try {
                port?.close()
            } catch (_: Exception) {}
            try {
                connection?.close()
            } catch (_: Exception) {}
            onDebugLog("[USB] Port closed")
        }
    }

    private suspend fun runSimulatedUsbExchange(
        ssid: String,
        password: String,
        slot: String = "primary",
        onDebugLog: (String) -> Unit
    ): WifiConfigResult {
        onDebugLog("[USB] Simulation: Virtual ESP8266 (CH340) opened @ 115200 baud")
        delay(400)
        val cmd = UsbWifiProtocolParser.formatCommand(ssid, password, slot)
        onDebugLog("[TX] $cmd".trimEnd())
        delay(600)
        onDebugLog("[DEBUG] [ESP8266] Received command CONFIG_WIFI (slot: $slot)")
        delay(500)
        onDebugLog("[DEBUG] [ESP8266] Disconnecting from previous network...")
        delay(800)
        onDebugLog("[DEBUG] [ESP8266] Connecting to WiFi SSID: '$ssid' ($slot)...")
        delay(1200)

        if (password.equals("wrongpass", ignoreCase = true) || ssid.isBlank()) {
            onDebugLog("[DEBUG] [ESP8266] Connection status: WL_CONNECT_FAILED (Auth Error)")
            delay(400)
            val failLine = "WIFI_RESULT:{\"success\":false,\"error\":\"WiFi authentication failed: Incorrect password\",\"slot\":\"$slot\"}"
            onDebugLog("[RX-PROTOCOL] $failLine")
            return UsbWifiProtocolParser.parseLine(failLine)!!
        }

        val assignedIp = "192.168.1." + (100..240).random()
        onDebugLog("[DEBUG] [ESP8266] WiFi Connected! Signal: -52 dBm")
        delay(400)
        onDebugLog("[DEBUG] [ESP8266] DHCP address assigned: $assignedIp")
        delay(500)
        val successLine = "WIFI_RESULT:{\"status\":\"ok\",\"ip\":\"$assignedIp\",\"message\":\"Connected to $ssid ($slot) successfully\",\"slot\":\"$slot\"}"
        onDebugLog("[RX-PROTOCOL] $successLine")
        return UsbWifiProtocolParser.parseLine(successLine)!!
    }
}
