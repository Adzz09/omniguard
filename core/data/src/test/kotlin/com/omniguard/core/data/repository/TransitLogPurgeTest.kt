package com.omniguard.core.data.repository

import app.cash.turbine.test
import com.omniguard.core.model.TransitEventType
import com.omniguard.core.model.TransitLog
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TransitLogPurgeTest {

    private lateinit var repository: DefaultTransitLogRepository

    @BeforeEach
    fun setup() {
        repository = DefaultTransitLogRepository()
    }

    @Test
    fun `purgeExpiredLogs removes logs older than 7 days while preserving active recent logs`() = runTest {
        val now = 1724240000000L // Reference timestamp
        val oneDayMillis = 24L * 60 * 60 * 1000

        val logToday = TransitLog(
            id = "log-today",
            timestampMillis = now - (1 * oneDayMillis), // 1 day old
            eventType = TransitEventType.SAFE_ZONE_ENTER,
            locationName = "University Campus",
            latitude = 37.7749,
            longitude = -122.4194,
            accuracyMeters = 5.0f,
            encryptedPayload = "enc-data-today",
            iv = "iv-today"
        )

        val logFiveDaysOld = TransitLog(
            id = "log-5d",
            timestampMillis = now - (5 * oneDayMillis), // 5 days old
            eventType = TransitEventType.CHECKPOINT_REACHED,
            locationName = "Transit Corridor Main St",
            latitude = 37.7755,
            longitude = -122.4180,
            accuracyMeters = 4.0f,
            encryptedPayload = "enc-data-5d",
            iv = "iv-5d"
        )

        val logEightDaysOld = TransitLog(
            id = "log-8d",
            timestampMillis = now - (8 * oneDayMillis), // 8 days old (Expired!)
            eventType = TransitEventType.SAFE_ZONE_EXIT,
            locationName = "Home Perimeter",
            latitude = 37.7700,
            longitude = -122.4100,
            accuracyMeters = 6.0f,
            encryptedPayload = "enc-data-8d",
            iv = "iv-8d"
        )

        val logTenDaysOld = TransitLog(
            id = "log-10d",
            timestampMillis = now - (10 * oneDayMillis), // 10 days old (Expired!)
            eventType = TransitEventType.ROUTE_DEVIATION,
            locationName = "Side Alley",
            latitude = 37.7680,
            longitude = -122.4050,
            accuracyMeters = 10.0f,
            encryptedPayload = "enc-data-10d",
            iv = "iv-10d"
        )

        // Seed logs
        repository.insertLogs(listOf(logToday, logFiveDaysOld, logEightDaysOld, logTenDaysOld))
        assertEquals(4, repository.getLogs().size)

        // Verify expiration check logic
        assertFalse(logToday.isExpired(now, maxRetentionDays = 7))
        assertFalse(logFiveDaysOld.isExpired(now, maxRetentionDays = 7))
        assertTrue(logEightDaysOld.isExpired(now, maxRetentionDays = 7))
        assertTrue(logTenDaysOld.isExpired(now, maxRetentionDays = 7))

        // Execute 7-day automatic data purge
        val purgedCount = repository.purgeExpiredLogs(currentMillis = now, maxRetentionDays = 7)
        assertEquals(2, purgedCount)

        // Verify remaining retained records
        val remainingLogs = repository.getLogs()
        assertEquals(2, remainingLogs.size)
        assertTrue(remainingLogs.any { it.id == "log-today" })
        assertTrue(remainingLogs.any { it.id == "log-5d" })
        assertFalse(remainingLogs.any { it.id == "log-8d" })
        assertFalse(remainingLogs.any { it.id == "log-10d" })
    }

    @Test
    fun `logsFlow emits live updates after purging expired logs`() = runTest {
        val now = 1724240000000L
        val oneDayMillis = 24L * 60 * 60 * 1000

        val oldLog = TransitLog(
            id = "expired-log",
            timestampMillis = now - (9 * oneDayMillis),
            eventType = TransitEventType.SAFE_ZONE_ENTER,
            locationName = "Downtown Library",
            latitude = 37.78,
            longitude = -122.42,
            accuracyMeters = 5.0f,
            encryptedPayload = "enc",
            iv = "iv"
        )

        repository.insertLog(oldLog)

        repository.logsFlow.test {
            val initial = awaitItem()
            assertEquals(1, initial.size)

            repository.purgeExpiredLogs(currentMillis = now, maxRetentionDays = 7)

            val updated = awaitItem()
            assertEquals(0, updated.size)
        }
    }
}
