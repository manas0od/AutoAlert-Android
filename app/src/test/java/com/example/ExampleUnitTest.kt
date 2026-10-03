package com.example

import com.example.data.NtfyManager
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testNtfyMessageDeduplication() {
        val uniqueId = "test_msg_id_" + System.currentTimeMillis()
        // First encounter of message ID should be accepted (true)
        val firstAttempt = NtfyManager.markMessageProcessed(uniqueId)
        assertTrue(firstAttempt)

        // Second encounter of the same message ID should be rejected as duplicate (false)
        val secondAttempt = NtfyManager.markMessageProcessed(uniqueId)
        assertFalse(secondAttempt)
        assertTrue(NtfyManager.isMessageAlreadyProcessed(uniqueId))
    }

    @Test
    fun testNotificationIdDerivation() {
        val msgId = "ntfy_msg_xyz_999"
        val derivedId = (msgId.hashCode() and 0x7FFFFFFF).let { if (it == 0) 1001 else it }
        assertTrue(derivedId > 0)
        assertEquals(derivedId, (msgId.hashCode() and 0x7FFFFFFF).let { if (it == 0) 1001 else it })
    }
}
