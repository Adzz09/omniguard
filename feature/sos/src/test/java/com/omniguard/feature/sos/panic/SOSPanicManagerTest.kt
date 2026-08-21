package com.omniguard.feature.sos.panic

import app.cash.turbine.test
import com.omniguard.core.model.SOSTriggerSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SOSPanicManagerTest {

    @Test
    fun `rapid triple press within 1500ms triggers SOS panic flow`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val panicManager = SOSPanicManager(
            scope = testScope,
            multiPressWindowMs = 1500L
        )

        panicManager.sosEvents.test {
            val now = 1000000L

            // 1st press
            val p1 = panicManager.registerButtonPress(currentMillis = now, isWearable = true)
            assertFalse(p1)

            // 2nd press (400ms later)
            val p2 = panicManager.registerButtonPress(currentMillis = now + 400, isWearable = true)
            assertFalse(p2)

            // 3rd press (800ms later) -> Fires SOS!
            val p3 = panicManager.registerButtonPress(
                currentMillis = now + 800,
                currentLat = 37.7749,
                currentLng = -122.4194,
                isWearable = true
            )
            assertTrue(p3)
            assertTrue(panicManager.sosState.value.isActive)
            assertEquals(SOSTriggerSource.TRIPLE_PRESS_WATCH, panicManager.sosState.value.triggerSource)

            testDispatcher.scheduler.runCurrent()

            val event = awaitItem()
            assertEquals(SOSTriggerSource.TRIPLE_PRESS_WATCH, event.triggerSource)
            assertEquals(37.7749, event.latitude)
            assertEquals(-122.4194, event.longitude)
        }
    }

    @Test
    fun `slow presses separated by more than window do not trigger panic`() {
        val panicManager = SOSPanicManager(multiPressWindowMs = 1500L)
        val now = 1000000L

        // Press 1
        assertFalse(panicManager.registerButtonPress(currentMillis = now))

        // Press 2 after 2000ms (Window expired)
        assertFalse(panicManager.registerButtonPress(currentMillis = now + 2000))

        // Press 3 after 2000ms
        assertFalse(panicManager.registerButtonPress(currentMillis = now + 4000))

        assertFalse(panicManager.sosState.value.isActive)
    }
}
