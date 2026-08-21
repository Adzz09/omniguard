package com.omniguard.feature.falldetection.model

import java.time.Instant

/**
 * Cancellation sources for fall detection events.
 */
enum class CancellationSource {
    USER_UI_TOUCH,
    HARDWARE_SIDE_KEY,
    VOICE_CONFIRMATION,
    WEARABLE_CROWN_TAP,
    AUTO_RECOVERY_MOTION
}

/**
 * Current status of fall detection cycle.
 */
sealed interface FallDetectionState {
    data object Idle : FallDetectionState
    data class ImpactDetected(val impactG: Float, val timestamp: Long) : FallDetectionState
    data class ImmobilityDetected(val impactG: Float, val durationMs: Long) : FallDetectionState
    data class WarningCountdown(
        val remainingSeconds: Int,
        val totalSeconds: Int,
        val impactG: Float,
        val progress: Float
    ) : FallDetectionState
    data class Cancelled(val source: CancellationSource, val timestamp: Long) : FallDetectionState
    data class Escalated(val payload: EscalationPayload) : FallDetectionState
}

/**
 * Sensor sample container for impact analysis.
 */
data class FallImpactData(
    val accelerationG: Float,
    val gyroMagnitudeRadS: Float,
    val timestampMs: Long,
    val isImpact: Boolean,
    val isImmobile: Boolean
)

/**
 * Configurable thresholds and countdown windows for fall detection.
 */
data class FallDetectionConfig(
    val impactThresholdG: Float = 3.5f,
    val immobilityVarianceThreshold: Float = 0.15f,
    val immobilityWindowMs: Long = 2500L,
    val countdownDurationSeconds: Int = 60, // 60s - 120s configurable
    val audioMetadataRecordingSeconds: Int = 10,
    val isHighPrecisionGpsRequired: Boolean = true
)

/**
 * Payload dispatched when fall countdown expires without cancellation.
 */
data class EscalationPayload(
    val incidentId: String,
    val timestamp: Instant,
    val peakImpactG: Float,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val audioMetadata: AudioSnapshotMetadata,
    val batteryPercent: Int,
    val dispatchTriggered: Boolean
)

/**
 * Ambient audio recording metadata captured during fall incident.
 */
data class AudioSnapshotMetadata(
    val durationSeconds: Int,
    val sampleRateHz: Int = 44100,
    val averageDecibels: Float,
    val peakDecibels: Float,
    val audioFileUri: String?,
    val speechDetected: Boolean
)
