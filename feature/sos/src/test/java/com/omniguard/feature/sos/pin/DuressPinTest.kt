package com.omniguard.feature.sos.pin

import app.cash.turbine.test
import com.omniguard.core.model.SOSTriggerSource
import com.omniguard.feature.sos.model.DuressPinConfig
import com.omniguard.feature.sos.model.PinValidationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DuressPinTest {

    private lateinit var pinManager: DuressPinManager
    private val realPin = "1234"
    private val duressPin = "9999"

    @BeforeEach
    fun setup() {
        val config = DuressPinConfig(
            realPinHash = DuressPinManager.hashPin(realPin),
            duressPinHash = DuressPinManager.hashPin(duressPin),
            maxFailedAttempts = 3,
            lockoutDurationSeconds = 60
        )
        pinManager = DuressPinManager(config)
    }

    @Test
    fun `entering correct real PIN unlocks normally without triggering duress flow`() {
        val result = pinManager.validatePin("1234")
        assertEquals(PinValidationResult.Correct, result)
        assertFalse(pinManager.sosState.value.isActive)
        assertFalse(pinManager.sosState.value.isDuress)
    }

    @Test
    fun `entering duress PIN covertly triggers SOS event stream and updates state`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val manager = DuressPinManager(
            config = DuressPinConfig(
                realPinHash = DuressPinManager.hashPin(realPin),
                duressPinHash = DuressPinManager.hashPin(duressPin)
            ),
            scope = testScope,
            trackingBaseUrl = "https://omniguard.app/live"
        )

        manager.duressEvents.test {
            val result = manager.validatePin(
                enteredPin = "9999",
                currentLat = 37.7749,
                currentLng = -122.4194,
                batteryPercent = 95
            )

            assertEquals(PinValidationResult.DuressTriggered, result)
            assertTrue(manager.sosState.value.isActive)
            assertTrue(manager.sosState.value.isDuress)
            assertEquals(SOSTriggerSource.MOBILE_DURESS, manager.sosState.value.triggerSource)

            testDispatcher.scheduler.runCurrent()

            val duressEvent = awaitItem()
            assertTrue(duressEvent.isSilentDuress)
            assertEquals(SOSTriggerSource.MOBILE_DURESS, duressEvent.triggerSource)
            assertEquals(37.7749, duressEvent.latitude)
            assertEquals(-122.4194, duressEvent.longitude)
            assertEquals(95, duressEvent.batteryPercent)
            assertTrue(duressEvent.trackingUrl.startsWith("https://omniguard.app/live/"))
        }
    }

    @Test
    fun `entering invalid PIN decrements attempts and causes lockout after max failed attempts`() {
        val r1 = pinManager.validatePin("0000")
        assertTrue(r1 is PinValidationResult.Incorrect)
        assertEquals(2, (r1 as PinValidationResult.Incorrect).attemptsRemaining)

        val r2 = pinManager.validatePin("1111")
        assertTrue(r2 is PinValidationResult.Incorrect)
        assertEquals(1, (r2 as PinValidationResult.Incorrect).attemptsRemaining)

        val r3 = pinManager.validatePin("2222")
        assertEquals(PinValidationResult.LockedOut, r3)

        // Subsequent attempt while locked out
        val r4 = pinManager.validatePin("1234") // even right pin is locked
        assertEquals(PinValidationResult.LockedOut, r4)
    }
}
