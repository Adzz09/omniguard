package com.omniguard.feature.sos.panic

import com.omniguard.feature.sos.model.SilentPanicPayload
import com.omniguard.feature.sos.model.SosTriggerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * Service contract for silent emergency transmission (SMS, Satellite, WebSocket).
 */
interface SilentEmergencyNetworkService {
    suspend fun transmitSilentAlert(payload: SilentPanicPayload): Boolean
}

/**
 * Stealth hardware controller interface to ensure screen stays off and audio is muted.
 */
interface StealthHardwareController {
    fun keepScreenOff()
    fun muteAllAudio()
    fun suppressVibrations()
}

/**
 * Dispatcher for silent panic emergencies.
 * Transmits emergency payload silently in background without activating screen or playing audio.
 */
class SilentPanicDispatcher(
    private val networkService: SilentEmergencyNetworkService,
    private val hardwareController: StealthHardwareController,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val _dispatchedEvents = MutableSharedFlow<SilentPanicPayload>(extraBufferCapacity = 1)
    val dispatchedEvents: SharedFlow<SilentPanicPayload> = _dispatchedEvents.asSharedFlow()

    /**
     * Executes silent panic dispatch.
     */
    fun dispatchSilentPanic(
        trigger: SosTriggerType,
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        accuracyMeters: Float = 5.0f,
        batteryPercent: Int = 80
    ): SilentPanicPayload {
        // Enforce stealth mode: screen off, muted audio, suppressed vibrations
        hardwareController.keepScreenOff()
        hardwareController.muteAllAudio()
        hardwareController.suppressVibrations()

        val emergencyId = "PANIC-${UUID.randomUUID().toString().take(8).uppercase()}"
        val payload = SilentPanicPayload(
            emergencyId = emergencyId,
            timestampMillis = System.currentTimeMillis(),
            triggerSource = trigger,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            isSilentMode = true,
            batteryPercent = batteryPercent
        )

        scope.launch {
            networkService.transmitSilentAlert(payload)
            _dispatchedEvents.emit(payload)
        }

        return payload
    }
}
