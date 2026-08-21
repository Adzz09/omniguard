package com.omniguard.feature.falldetection.escalation

import app.cash.turbine.test
import com.omniguard.feature.falldetection.model.AudioSnapshotMetadata
import com.omniguard.feature.falldetection.model.FallDetectionConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FallEscalationManagerTest {

    private val gpsProvider: GpsSnapshotProvider = mockk()
    private val audioRecorder: AmbientAudioRecorder = mockk()
    private val dispatchService: EmergencyDispatchService = mockk()

    @BeforeEach
    fun setup() {
        coEvery { gpsProvider.captureHighPrecisionLocation() } returns ((37.7749 to -122.4194) to 3.2f)
        coEvery { audioRecorder.recordAudioMetadata(any()) } returns AudioSnapshotMetadata(
            durationSeconds = 10,
            averageDecibels = 65.4f,
            peakDecibels = 88.1f,
            audioFileUri = "file:///data/audio/fall_clip_1.aac",
            speechDetected = false
        )
        coEvery { dispatchService.dispatchFallEmergency(any()) } returns true
    }

    @Test
    fun `triggerEscalation aggregates GPS snapshot and audio metadata and dispatches emergency payload`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val manager = FallEscalationManager(
            config = FallDetectionConfig(audioMetadataRecordingSeconds = 10),
            gpsProvider = gpsProvider,
            audioRecorder = audioRecorder,
            dispatchService = dispatchService,
            scope = testScope
        )

        manager.escalationEvents.test {
            val payload = manager.triggerEscalation(peakImpactG = 4.2f, batteryPercent = 88)

            assertEquals(4.2f, payload.peakImpactG)
            assertEquals(37.7749, payload.latitude, 0.0001)
            assertEquals(-122.4194, payload.longitude, 0.0001)
            assertEquals(3.2f, payload.accuracyMeters)
            assertEquals(10, payload.audioMetadata.durationSeconds)
            assertEquals(88, payload.batteryPercent)
            assertTrue(payload.dispatchTriggered)

            testDispatcher.scheduler.runCurrent()

            val emittedEvent = awaitItem()
            assertEquals(payload.incidentId, emittedEvent.incidentId)

            coVerify(exactly = 1) { gpsProvider.captureHighPrecisionLocation() }
            coVerify(exactly = 1) { audioRecorder.recordAudioMetadata(10) }
            coVerify(exactly = 1) { dispatchService.dispatchFallEmergency(any()) }
        }
    }
}
