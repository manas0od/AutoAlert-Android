package com.example

import com.example.data.UsbWifiProtocolParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UsbSerialWifiTest {

    @Test
    fun `test formatCommand produces expected protocol string`() {
        val cmd = UsbWifiProtocolParser.formatCommand("MyHomeWifi", "SecretPass123")
        assertTrue(cmd.startsWith("CONFIG_WIFI:"))
        assertTrue(cmd.endsWith("\n"))
        assertTrue(cmd.contains("\"ssid\":\"MyHomeWifi\""))
        assertTrue(cmd.contains("\"password\":\"SecretPass123\""))
        assertTrue(cmd.contains("\"slot\":\"primary\""))
    }

    @Test
    fun `test formatCommand with explicit safety slot`() {
        val cmd = UsbWifiProtocolParser.formatCommand("HotspotBackup", "HotspotPass456", slot = "safety")
        assertTrue(cmd.startsWith("CONFIG_WIFI:"))
        assertTrue(cmd.endsWith("\n"))
        assertTrue(cmd.contains("\"ssid\":\"HotspotBackup\""))
        assertTrue(cmd.contains("\"password\":\"HotspotPass456\""))
        assertTrue(cmd.contains("\"slot\":\"safety\""))
    }

    @Test
    fun `test parseLine successfully parses WIFI_RESULT ok`() {
        val line = "WIFI_RESULT:{\"status\":\"ok\",\"ip\":\"192.168.1.142\",\"message\":\"Connected successfully\"}"
        val result = UsbWifiProtocolParser.parseLine(line)
        assertNotNull(result)
        assertTrue(result!!.success)
        assertEquals("192.168.1.142", result.ip)
        assertEquals("Connected successfully", result.message)
    }

    @Test
    fun `test parseLine parses failure result`() {
        val line = "WIFI_RESULT:{\"success\":false,\"error\":\"Authentication failed\"}"
        val result = UsbWifiProtocolParser.parseLine(line)
        assertNotNull(result)
        assertFalse(result!!.success)
        assertEquals("Authentication failed", result.message)
    }

    @Test
    fun `test parseLine ignores debug lines without prefix`() {
        val debugLine = "[DEBUG] [ESP8266] Connecting to WiFi..."
        val result = UsbWifiProtocolParser.parseLine(debugLine)
        assertNull(result)
    }
}
