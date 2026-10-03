package com.example

import com.example.data.NtfyManager
import com.example.data.formatBatteryAge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NtfyBatteryTagTest {

    @Test
    fun testExtractBatteryPercentFromTags_standard() {
        val tags = listOf("batt64")
        val result = NtfyManager.extractBatteryPercentFromTags(tags)
        assertEquals(64, result)
    }

    @Test
    fun testExtractBatteryPercentFromTags_multipleTags() {
        val tags = listOf("auto_rickshaw", "call", "batt82", "alert")
        val result = NtfyManager.extractBatteryPercentFromTags(tags)
        assertEquals(82, result)
    }

    @Test
    fun testExtractBatteryPercentFromTags_boundaryValues() {
        assertEquals(0, NtfyManager.extractBatteryPercentFromTags(listOf("batt0")))
        assertEquals(100, NtfyManager.extractBatteryPercentFromTags(listOf("batt100")))
        assertEquals(1, NtfyManager.extractBatteryPercentFromTags(listOf("batt1")))
    }

    @Test
    fun testExtractBatteryPercentFromTags_caseInsensitiveAndWhitespace() {
        assertEquals(45, NtfyManager.extractBatteryPercentFromTags(listOf("  BATT45  ")))
        assertEquals(99, NtfyManager.extractBatteryPercentFromTags(listOf("Batt99")))
    }

    @Test
    fun testExtractBatteryPercentFromTags_invalidAndMissing() {
        assertNull(NtfyManager.extractBatteryPercentFromTags(emptyList()))
        assertNull(NtfyManager.extractBatteryPercentFromTags(listOf("auto_rickshaw", "call")))
        assertNull(NtfyManager.extractBatteryPercentFromTags(listOf("batt")))
        assertNull(NtfyManager.extractBatteryPercentFromTags(listOf("battle")))
        assertNull(NtfyManager.extractBatteryPercentFromTags(listOf("battabc")))
        assertNull(NtfyManager.extractBatteryPercentFromTags(listOf("battery64")))
    }

    @Test
    fun testFormatBatteryAge_formatting() {
        val now = System.currentTimeMillis()

        // Just now (< 60s)
        val justNow = formatBatteryAge(now - 15_000L)
        assertEquals("as of just now, from last alert", justNow)

        // 1 min ago
        val oneMin = formatBatteryAge(now - 65_000L)
        assertEquals("as of 1 min ago, from last alert", oneMin)

        // 3 mins ago
        val threeMin = formatBatteryAge(now - 185_000L)
        assertEquals("as of 3 min ago, from last alert", threeMin)

        // 2 hours ago
        val twoHours = formatBatteryAge(now - (2 * 3600_000L + 10_000L))
        assertEquals("as of 2 hours ago, from last alert", twoHours)

        // Combined UI format matches prompt expectation: "64% - as of 3 min ago, from last alert"
        val batteryPercent = 64
        val uiFormatted = "$batteryPercent% - $threeMin"
        assertEquals("64% - as of 3 min ago, from last alert", uiFormatted)
    }
}
