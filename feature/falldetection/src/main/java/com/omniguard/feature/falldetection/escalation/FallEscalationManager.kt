package com.omniguard.feature.falldetection.escalation

import com.omniguard.feature.falldetection.model.AudioSnapshotMetadata
import com.omniguard.feature.falldetection.model.EscalationPayload
import com.omniguard.feature.falldetection.model.FallDetectionConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * Service contract for dispatching emergency payloads (SMS, VoIP, Cloud REST, Satellite).
 */
interface EmergencyDispatchService {
    suspend fun dispatchFallEmergency(payload: EscalationPayload): Boolean
}

/**
 * GPS snapshot provider contract for obtaining current high-precision coordinates.
 */
interface GpsSnapshotProvider {
    suspend fun captureHighPrecisionLocation(): Pair<Pair<Double, Double>, Float> // ((lat, lon), accuracy)
}

/**
 * Ambient audio metadata recorder contract.
 */
interface AmbientAudioRecorder {
    suspend fun recordAudioMetadata(durationSeconds: Int): AudioSnapshotMetadata
}

/**
 * Escalation coordinator for uncancelled fall alarms.
 * Gathers audio metadata, high-precision GPS coordinates, creates incident payload, and dispatches.
 */
class FallEscalationManager(
    private val config: FallDetectionConfig = FallDetectionConfig(),
    private val gpsProvider: GpsSnapshotProvider,
    private val audioRecorder: AmbientAudioRecorder,
    private val dispatchService: EmergencyDispatchService,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val _escalationEvents = MutableSharedFlow<EscalationPayload>(extraBufferCapacity = 1)
    val escalationEvents: SharedFlow<EscalationPayload> = _escalationEvents.asSharedFlow()

    /**
     * Executes the emergency escalation workflow upon countdown expiration.
     */
    suspend fun triggerEscalation(peakImpactG: Float, batteryPercent: Int = 85): EscalationPayload {
        val incidentId = "FALL-${UUID.randomUUID().toString().take(8).uppercase()}"

        // 1. High precision GPS snapshot
        val (coords, accuracy) = try {
            gpsProvider.captureHighPrecisionLocation()
        } catch (e: Exception) {
            (0.0 to 0.0) to 999.0f
        }

        // 2. Ambient audio metadata recording
        val audioMeta = try {
            audioRecorder.recordAudioMetadata(config.audioMetadataRecordingSeconds)
        } catch (e: Exception) {
            AudioSnapshotMetadata(
                durationSeconds = 0,
                averageDecibels = 0f,
                peakDecibels = 0f,
                audioFileUri = null,
                speechDetected = false
            )
        }

        // 3. Construct escalation payload
        val payload = EscalationPayload(
            incidentId = incidentId,
            timestamp = Instant.now(),
            peakImpactG = peakImpactG,
            latitude = coords.first,
            longitude = coords.second,
            accuracyMeters = accuracy,
            audioMetadata = audioMeta,
            batteryPercent = batteryPercent,
            dispatchTriggered = true
        )

        // 4. Fire emergency dispatch
        scope.launch {
            dispatchService.dispatchFallEmergency(payload)
            _escalationEvents.emit(payload)
        }

        return payload
    }
}
