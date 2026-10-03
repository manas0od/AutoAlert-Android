package com.example

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
class Esp8266WifiConfigTest {

    private val apiClient = Esp8266ApiClient()

    @Test
    fun `test simulated primary wifi config`() = runBlocking {
        val result = apiClient.configureWifiAp(
            ip = "192.168.4.1",
            ssid = "AutoStandWifi",
            password = "StandPassword",
            slot = "primary",
            currentPassword = "auto123",
            isSimulated = true
        )
        assertTrue(result.success)
        assertTrue(result.ip.isNotBlank())
        assertTrue(result.message.contains("AutoStandWifi"))
        assertTrue(result.message.contains("primary"))
        assertNotNull(result.rawResponse)
        assertTrue(result.rawResponse!!.contains("\"slot\":\"primary\""))
    }

    @Test
    fun `test simulated safety backup hotspot config`() = runBlocking {
        val result = apiClient.configureWifiAp(
            ip = "192.168.4.1",
            ssid = "DriverPhoneHotspot",
            password = "HotspotPassword123",
            slot = "safety",
            currentPassword = "auto123",
            isSimulated = true
        )
        assertTrue(result.success)
        assertTrue(result.ip.isNotBlank())
        assertTrue(result.message.contains("DriverPhoneHotspot"))
        assertTrue(result.message.contains("safety"))
        assertNotNull(result.rawResponse)
        assertTrue(result.rawResponse!!.contains("\"slot\":\"safety\""))
    }

    @Test
    fun `test simulated linkViewer returns the shared topic`() = runBlocking {
        val result = apiClient.linkViewer(
            ip = "192.168.4.1",
            password = "masterPassword123",
            viewerPhone = "+91 9447111222",
            isSimulated = true
        )
        assertTrue(result.success)
        assertTrue(result.topic.isNotBlank())
        assertTrue(result.topic.startsWith("autoalert-kerala-"))
        assertTrue(result.message.contains("Simulated"))
    }

    @Test
    fun `test simulated revokeViewers succeeds`() = runBlocking {
        val result = apiClient.revokeViewers(
            ip = "192.168.4.1",
            password = "masterPassword123",
            isSimulated = true
        )
        assertTrue(result.success)
        assertTrue(result.message.contains("revoked"))
    }
}
