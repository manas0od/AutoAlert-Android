package com.example

import com.example.data.NotificationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InsightsCalculationTest {

    @Test
    fun `test weekly 7-day metrics aggregation`() {
        val oneDayMillis = 24L * 60 * 60 * 1000

        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.SUNDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val sundayStart = cal.timeInMillis

        // Add 3 notifications on Sunday, 2 on Monday, 5 on Tuesday
        val notifications = listOf(
            NotificationEntity(title = "Service Requested", message = "Passenger at stand", timestamp = sundayStart + 3600000, status = "Accepted"),
            NotificationEntity(title = "Service Requested", message = "Passenger at stand", timestamp = sundayStart + 7200000, status = "Accepted"),
            NotificationEntity(title = "Service Requested", message = "Passenger at stand", timestamp = sundayStart + 10800000, status = "Completed"),
            NotificationEntity(title = "Service Requested", message = "Passenger at stand", timestamp = sundayStart + oneDayMillis + 3600000, status = "Accepted"),
            NotificationEntity(title = "Service Requested", message = "Passenger at stand", timestamp = sundayStart + oneDayMillis + 7200000, status = "Cancelled"),
            NotificationEntity(title = "Service Requested", message = "Passenger at stand", timestamp = sundayStart + (2 * oneDayMillis) + 3600000, status = "Accepted"),
            NotificationEntity(title = "Service Requested", message = "Passenger at stand", timestamp = sundayStart + (2 * oneDayMillis) + 7200000, status = "Accepted"),
            NotificationEntity(title = "Service Requested", message = "Passenger at stand", timestamp = sundayStart + (2 * oneDayMillis) + 10800000, status = "Accepted"),
            NotificationEntity(title = "Service Requested", message = "Passenger at stand", timestamp = sundayStart + (2 * oneDayMillis) + 14400000, status = "Accepted"),
            NotificationEntity(title = "Service Requested", message = "Passenger at stand", timestamp = sundayStart + (2 * oneDayMillis) + 18000000, status = "Accepted"),
            // Low battery alert should be filtered out from service request counts
            NotificationEntity(title = "Low Battery Warning", message = "Module 18% remaining", timestamp = sundayStart + 3600000, status = "Low Battery")
        )

        val serviceRequests = notifications.filter {
            !it.status.equals("Low Battery", ignoreCase = true) && !it.title.contains("Battery", ignoreCase = true)
        }
        assertEquals(10, serviceRequests.size)

        val sundayRequests = serviceRequests.count { it.timestamp in sundayStart until (sundayStart + oneDayMillis) }
        val mondayRequests = serviceRequests.count { it.timestamp in (sundayStart + oneDayMillis) until (sundayStart + 2 * oneDayMillis) }
        val tuesdayRequests = serviceRequests.count { it.timestamp in (sundayStart + 2 * oneDayMillis) until (sundayStart + 3 * oneDayMillis) }

        assertEquals(3, sundayRequests)
        assertEquals(2, mondayRequests)
        assertEquals(5, tuesdayRequests)
    }

    @Test
    fun `test busiest hour computation`() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 17) // 5 PM
        cal.set(Calendar.MINUTE, 30)
        val peakTime1 = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 17)
        cal.set(Calendar.MINUTE, 45)
        val peakTime2 = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 10) // 10 AM
        val morningTime = cal.timeInMillis

        val requests = listOf(
            NotificationEntity(title = "Service Requested", message = "Passenger at stand", timestamp = peakTime1),
            NotificationEntity(title = "Service Requested", message = "Passenger at stand", timestamp = peakTime2),
            NotificationEntity(title = "Service Requested", message = "Passenger at stand", timestamp = morningTime)
        )

        val hourCounts = IntArray(24)
        val tempCal = Calendar.getInstance()
        for (req in requests) {
            tempCal.timeInMillis = req.timestamp
            val hour = tempCal.get(Calendar.HOUR_OF_DAY)
            hourCounts[hour]++
        }

        val peakHour = hourCounts.indices.maxByOrNull { hourCounts[it] } ?: 0
        assertEquals(17, peakHour)
    }

    @Test
    fun `test average response time calculation`() {
        val now = System.currentTimeMillis()
        val requests = listOf(
            NotificationEntity(
                title = "Service Requested",
                message = "Passenger at stand",
                timestamp = now,
                replyStatus = "coming",
                replyTimestamp = now + 20_000L // 20 seconds
            ),
            NotificationEntity(
                title = "Service Requested",
                message = "Passenger at stand",
                timestamp = now,
                replyStatus = "busy",
                replyTimestamp = now + 40_000L // 40 seconds
            )
        )

        var totalDuration = 0L
        for (r in requests) {
            if (r.replyTimestamp != null) {
                totalDuration += (r.replyTimestamp - r.timestamp)
            }
        }
        val avgSeconds = totalDuration / requests.size / 1000L
        assertEquals(30L, avgSeconds)
    }

    @Test
    fun `test page 2 coverage and performance rates`() {
        val requests = listOf(
            NotificationEntity(title = "Service Requested", message = "Passenger waiting", locationLabel = "Main Gate Stand", status = "Accepted", replyStatus = "coming"),
            NotificationEntity(title = "Service Requested", message = "Passenger waiting", locationLabel = "Main Gate Stand", status = "Accepted", replyStatus = "coming"),
            NotificationEntity(title = "Service Requested", message = "Passenger waiting", locationLabel = "Main Gate Stand", status = "Accepted", replyStatus = "coming"),
            NotificationEntity(title = "Service Requested", message = "Passenger waiting", locationLabel = "Town Hall Stand", status = "Cancelled", replyStatus = "busy"),
            NotificationEntity(title = "Service Requested", message = "Passenger waiting", locationLabel = "Railway Stand", status = "Pending", replyStatus = null)
        )

        // Stand coverage calculation
        val standCounts = requests.groupBy { it.locationLabel }
        val topStand = standCounts.maxByOrNull { it.value.size }
        assertNotNull(topStand)
        assertEquals("Main Gate Stand", topStand!!.key)
        val coveragePct = (topStand.value.size.toDouble() / requests.size * 100).roundToInt()
        assertEquals(60, coveragePct)

        // Response rate (% that got an answer/reply)
        val repliedCount = requests.count { it.replyStatus != null || !it.status.equals("Pending", ignoreCase = true) }
        val responseRate = (repliedCount.toDouble() / requests.size * 100).roundToInt()
        assertEquals(80, responseRate) // 4 out of 5 replied

        // Completion rate (% resolved as Coming / Accepted)
        val comingCount = requests.count {
            it.status.equals("Accepted", ignoreCase = true) ||
                    it.replyStatus.equals("coming", ignoreCase = true) ||
                    it.status.equals("Completed", ignoreCase = true)
        }
        val completionRate = (comingCount.toDouble() / requests.size * 100).roundToInt()
        assertEquals(60, completionRate) // 3 out of 5 accepted
    }
}
