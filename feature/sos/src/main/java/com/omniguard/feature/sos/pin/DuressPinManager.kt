package com.omniguard.feature.sos.pin

import com.omniguard.core.model.SOSState
import com.omniguard.core.model.SOSTriggerSource
import com.omniguard.feature.sos.model.DuressPinConfig
import com.omniguard.feature.sos.model.PinValidationResult
import com.omniguard.feature.sos.model.SOSEmergencyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

/**
 * Covert Duress PIN management and silent panic escalation engine.
 * Allows entering an alternate distress PIN (e.g., student in transit or hostage scenario)
 * which appears to unlock normally or show fake interface while secretly dispatching SOS coordinates.
 */
class DuressPinManager(
    private var config: DuressPinConfig,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val trackingBaseUrl: String = "https://omniguard.app/live"
) {
    private val _duressEvents = MutableSharedFlow<SOSEmergencyEvent>(extraBufferCapacity = 10)
    val duressEvents: SharedFlow<SOSEmergencyEvent> = _duressEvents.asSharedFlow()

    private val _sosState = MutableStateFlow(SOSState())
    val sosState: StateFlow<SOSState> = _sosState.asStateFlow()

    private var failedAttempts = 0
    private var lockoutUntilMillis: Long = 0L

    fun updateConfig(newConfig: DuressPinConfig) {
        config = newConfig
    }

    /**
     * Validates entered PIN against legitimate and duress credentials.
     */
    fun validatePin(
        enteredPin: String,
        currentLat: Double = 0.0,
        currentLng: Double = 0.0,
        batteryPercent: Int = 100
    ): PinValidationResult {
        val currentMillis = System.currentTimeMillis()
        if (currentMillis < lockoutUntilMillis) {
            return PinValidationResult.LockedOut
        }

        val hashed = hashPin(enteredPin)

        return when {
            hashed == config.realPinHash -> {
                failedAttempts = 0
                PinValidationResult.Correct
            }

            hashed == config.duressPinHash -> {
                failedAttempts = 0
                val sessionId = "DUR-${UUID.randomUUID().toString().take(8).uppercase()}"
                val liveUrl = "$trackingBaseUrl/$sessionId"
                
                val event = SOSEmergencyEvent(
                    eventId = UUID.randomUUID().toString(),
                    timestampMillis = currentMillis,
                    triggerSource = SOSTriggerSource.MOBILE_DURESS,
                    isSilentDuress = true,
                    latitude = currentLat,
                    longitude = currentLng,
                    accuracyMeters = 5.0f,
                    batteryPercent = batteryPercent,
                    sessionId = sessionId,
                    trackingUrl = liveUrl
                )

                _sosState.value = SOSState(
                    isActive = true,
                    isDuress = true,
                    triggerSource = SOSTriggerSource.MOBILE_DURESS,
                    timestamp = currentMillis,
                    liveTrackingUrl = liveUrl,
                    sessionId = sessionId
                )

                scope.launch {
                    _duressEvents.emit(event)
                }

                PinValidationResult.DuressTriggered
            }

            else -> {
                failedAttempts++
                if (failedAttempts >= config.maxFailedAttempts) {
                    lockoutUntilMillis = currentMillis + (config.lockoutDurationSeconds * 1000L)
                    PinValidationResult.LockedOut
                } else {
                    PinValidationResult.Incorrect(attemptsRemaining = config.maxFailedAttempts - failedAttempts)
                }
            }
        }
    }

    /**
     * Resets active SOS state.
     */
    fun cancelSOS() {
        _sosState.value = SOSState(isActive = false)
    }

    companion object {
        fun hashPin(pin: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
            return hashBytes.joinToString("") { "%02x".format(it) }
        }
    }
}
