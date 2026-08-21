package com.omniguard.feature.sos.model

import com.omniguard.core.model.SOSTriggerSource
import com.omniguard.core.model.SOSState
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Trigger source for SOS signals.
 */
enum class SosTriggerType {
    PHYSICAL_TRIPLE_PRESS_SIDE_BUTTON,
    DURESS_PIN_ENTERED,
    WATCH_EMERGENCY_TILE,
    MOBILE_PANIC_GESTURE
}

/**
 * Types of decoy screens displayed when duress PIN is entered.
 */
enum class FakeScreenType {
    GENERIC_CALCULATOR,
    WEATHER_DASHBOARD,
    NOTES_APP,
    SYSTEM_SETTINGS
}

/**
 * Result of Duress PIN verification.
 */
sealed interface DuressVerificationResult {
    data object NormalUnlock : DuressVerificationResult
    data class DuressTriggered(val fakeScreenType: FakeScreenType) : DuressVerificationResult
    data object InvalidPin : DuressVerificationResult
}

/**
 * Payload sent during covert panic dispatch.
 */
@Serializable
data class SilentPanicPayload(
    val emergencyId: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val triggerSource: SosTriggerType,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float = 5.0f,
    val isSilentMode: Boolean = true,
    val batteryPercent: Int = 100
)

/**
 * State machine for SOS feature.
 */
sealed interface SosState {
    data object Idle : SosState
    data class SilentPanicActive(val payload: SilentPanicPayload) : SosState
    data class FakeScreenDisplayed(val fakeScreen: FakeScreenType) : SosState
}

/**
 * Result of PIN verification.
 */
sealed interface PinValidationResult {
    data object Correct : PinValidationResult
    data object DuressTriggered : PinValidationResult
    data class Incorrect(val attemptsRemaining: Int) : PinValidationResult
    data object LockedOut : PinValidationResult
}

/**
 * Duress PIN security configuration.
 */
@Serializable
data class DuressPinConfig(
    val realPinHash: String,
    val duressPinHash: String,
    val maxFailedAttempts: Int = 5,
    val lockoutDurationSeconds: Long = 300,
    val isSilentEscalationEnabled: Boolean = true
)

/**
 * Emergency SOS Dispatch Event.
 */
data class SOSEmergencyEvent(
    val eventId: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val triggerSource: SOSTriggerSource,
    val isSilentDuress: Boolean,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val batteryPercent: Int,
    val sessionId: String,
    val trackingUrl: String
)

