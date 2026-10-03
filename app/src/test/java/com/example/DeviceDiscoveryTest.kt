package com.example

import com.example.data.DiscoveredDevice
import com.example.data.Esp8266ApiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeviceDiscoveryTest {

    @Test
    fun `test simulated pairing returns valid topic with numeric IP`() = runBlocking {
        val client = Esp8266ApiClient()
        val result = client.pairDevice(
            ip = "192.168.4.1",
            phone = "+91 9876543210",
            password = "auto123",
            backupPhone = "+91 9876500000",
            isSimulated = true
        )
        assertTrue(result.success)
        assertTrue(result.topic.startsWith("autoalert-kerala-"))
        // ONE shared topic only: no "-backup" / "-tilt" topics are derived any more
        assertTrue(!result.topic.endsWith("-backup") && !result.topic.endsWith("-tilt"))
    }

    @Test
    fun `test simulated ping with numeric IP`() = runBlocking {
        val client = Esp8266ApiClient()
        val result = client.pingDevice(
            ip = "192.168.4.1",
            isSimulated = true
        )
        assertTrue(result.success)
        assertNotNull(result.message)
    }

    @Test
    fun `test simulated battery check with numeric IP`() = runBlocking {
        val client = Esp8266ApiClient()
        val result = client.checkBattery(
            ip = "192.168.4.1",
            isSimulated = true
        )
        assertTrue(result.success)
        assertTrue((result.batteryPercent ?: 0) > 0)
    }

    @Test
    fun `test simulated update backup driver`() = runBlocking {
        val client = Esp8266ApiClient()
        val result = client.updateBackupDriver(
            ip = "192.168.4.1",
            backupPhone = "+91 9876500000",
            password = "auto123",
            isSimulated = true
        )
        assertTrue(result.success)
    }

    @Test
    fun `test discovered device stores numeric IP address`() {
        val dev = DiscoveredDevice(
            ip = "192.168.1.105",
            hostname = "autoalert",
            description = "Found via mDNS/NSD (192.168.1.105)",
            isReachable = true
        )
        assertEquals("192.168.1.105", dev.ip)
        assertTrue(dev.ip.matches(Regex("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")))
    }
}
