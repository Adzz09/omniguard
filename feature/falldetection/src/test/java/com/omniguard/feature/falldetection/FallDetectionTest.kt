package com.omniguard.feature.falldetection

import com.omniguard.feature.falldetection.hardware.HardwareButtonCancellationListener
import com.omniguard.feature.falldetection.model.CancellationSource
import com.omniguard.feature.falldetection.model.FallDetectionConfig
import com.omniguard.feature.falldetection.sensor.FallSensorProcessor
import com.omniguard.feature.falldetection.timer.FallCountdownTimer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest


import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FallDetectionTest {

    @Test
    fun testHighGImpactAndImmobilityDetection() = runTest {
        val config = FallDetectionConfig(impactThresholdG = 3.5f, immobilityWindowMs = 2000L)
        val processor = FallSensorProcessor(config = config, scope = backgroundScope)
        processor.startMonitoring()

        val now = System.currentTimeMillis()

        // 1. High-G impact (4.0G -> 4.0 * 9.80665 = 39.22 m/s^2)
        processor.processSensorSample(0f, 0f, 39.5f, timestampMs = now)

        // 2. Post impact immobility (static 1G readings)
        for (i in 1..25) {
            processor.processSensorSample(0f, 0f, 9.80665f, timestampMs = now + (i * 100))
        }

        processor.confirmedFallEvent.test {
            val item = awaitItem()
            assertTrue(item.isImmobile)
        }
    }

    @Test
    fun testFallCountdownTimerCancellation() = runTest {
        val timer = FallCountdownTimer(scope = backgroundScope)
        var completed = false

        timer.startCountdown(durationSeconds = 60, onComplete = { completed = true })
        testScheduler.advanceTimeBy(10000L)
        testScheduler.runCurrent()

        assertEquals(50, timer.countdownState.value.remainingSeconds)
        assertFalse(completed)

        timer.cancelCountdown()
        assertTrue(timer.countdownState.value.isCancelled)
        assertFalse(timer.countdownState.value.isActive)
    }

    @Test
    fun testHardwareCancellationListener() = runTest {
        val listener = HardwareButtonCancellationListener()
        var cancelledSource: CancellationSource? = null

        val listenerJob = backgroundScope.launch {
            listener.cancellationEvents.collect {
                cancelledSource = it
            }
        }

        // KeyCode 25 = Volume Down
        val handled = listener.onHardwareKeyEvent(keyCode = 25)
        testScheduler.runCurrent()
        assertTrue(handled)
        listenerJob.cancel()
    }
}

