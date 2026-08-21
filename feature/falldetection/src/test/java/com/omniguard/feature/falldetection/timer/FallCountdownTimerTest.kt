package com.omniguard.feature.falldetection.timer

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FallCountdownTimerTest {

    @Test
    fun `countdown timer initializes with correct duration and decrements properly`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val timer = FallCountdownTimer(scope = testScope)

        timer.countdownState.test {
            // Initial idle state
            val initialState = awaitItem()
            assertFalse(initialState.isActive)
            assertEquals(60, initialState.remainingSeconds)

            // Start 60-second countdown
            var completedCalled = false
            timer.startCountdown(durationSeconds = 60) {
                completedCalled = true
            }

            testDispatcher.scheduler.runCurrent()

            // Active countdown emission
            val activeState = awaitItem()
            assertTrue(activeState.isActive)
            assertEquals(60, activeState.remainingSeconds)
            assertEquals(1.0f, activeState.progress)
            assertFalse(activeState.isCriticalWarning)

            // Advance 45 seconds -> 15 seconds remaining (Critical warning threshold)
            testScope.advanceTimeBy(45000L)
            testDispatcher.scheduler.runCurrent()

            // Discard intermediary tick emissions until remainingSeconds == 15
            var currentState = activeState
            while (currentState.remainingSeconds > 15) {
                currentState = awaitItem()
            }

            assertEquals(15, currentState.remainingSeconds)
            assertTrue(currentState.isCriticalWarning)

            // Advance remaining 15 seconds to completion
            testScope.advanceTimeBy(15000L)
            testDispatcher.scheduler.runCurrent()

            while (!currentState.isCompleted) {
                currentState = awaitItem()
            }

            assertTrue(currentState.isCompleted)
            assertFalse(currentState.isActive)
            assertEquals(0, currentState.remainingSeconds)
            assertTrue(completedCalled)
        }
    }

    @Test
    fun `cancelling countdown aborts timer and updates cancellation state`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val timer = FallCountdownTimer(scope = testScope)

        timer.countdownState.test {
            awaitItem() // idle

            var completedCalled = false
            timer.startCountdown(durationSeconds = 60) {
                completedCalled = true
            }
            testDispatcher.scheduler.runCurrent()

            val active = awaitItem()
            assertTrue(active.isActive)

            // Advance 10 seconds
            testScope.advanceTimeBy(10000L)
            testDispatcher.scheduler.runCurrent()

            // Cancel countdown
            timer.cancelCountdown()

            var latestState = active
            while (!latestState.isCancelled) {
                latestState = awaitItem()
            }

            assertTrue(latestState.isCancelled)
            assertFalse(latestState.isActive)
            assertFalse(completedCalled)
        }
    }
}
