package com.omniguard.feature.sos.panic

import com.omniguard.core.model.SOSState
import com.omniguard.core.model.SOSTriggerSource
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
import java.util.UUID

/**
 * Panic button and hardware multi-press detector (Wear Crown / Volume Key).
 * Detects rapid 3-tap panic gesture (within 1500ms window) and initiates immediate SOS.
 */
class SOSPanicManager(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val multiPressWindowMs: Long = 1500L,
    private val trackingBaseUrl: String = "https://omniguard.app/live"
) {
    private val _sosEvents = MutableSharedFlow<SOSEmergencyEvent>(extraBufferCapacity = 10)
    val sosEvents: SharedFlow<SOSEmergencyEvent> = _sosEvents.asSharedFlow()

    private val _sosState = MutableStateFlow(SOSState())
    val sosState: StateFlow<SOSState> = _sosState.asStateFlow()

    private val pressTimestamps = mutableListOf<Long>()

    /**
     * Registers a single hardware button press. If 3 presses occur within [multiPressWindowMs], fires SOS.
     */
    fun registerButtonPress(
        currentMillis: Long = System.currentTimeMillis(),
        currentLat: Double = 0.0,
        currentLng: Double = 0.0,
        isWearable: Boolean = true
    ): Boolean {
        // Clean out old presses outside time window
        pressTimestamps.removeAll { currentMillis - it > multiPressWindowMs }
        pressTimestamps.add(currentMillis)

        if (pressTimestamps.size >= 3) {
            pressTimestamps.clear()
            val source = if (isWearable) SOSTriggerSource.TRIPLE_PRESS_WATCH else SOSTriggerSource.MANUAL_APP
            triggerSOS(source = source, lat = currentLat, lng = currentLng, isSilent = false)
            return true
        }
        return false
    }

    /**
     * Directly triggers emergency SOS escalation.
     */
    fun triggerSOS(
        source: SOSTriggerSource = SOSTriggerSource.MANUAL_APP,
        lat: Double = 0.0,
        lng: Double = 0.0,
        isSilent: Boolean = false
    ): SOSEmergencyEvent {
        val sessionId = "SOS-${UUID.randomUUID().toString().take(8).uppercase()}"
        val liveUrl = "$trackingBaseUrl/$sessionId"
        val timestamp = System.currentTimeMillis()

        val event = SOSEmergencyEvent(
            eventId = UUID.randomUUID().toString(),
            timestampMillis = timestamp,
            triggerSource = source,
            isSilentDuress = isSilent,
            latitude = lat,
            longitude = lng,
            accuracyMeters = 3.5f,
            batteryPercent = 90,
            sessionId = sessionId,
            trackingUrl = liveUrl
        )

        _sosState.value = SOSState(
            isActive = true,
            isDuress = isSilent,
            triggerSource = source,
            timestamp = timestamp,
            liveTrackingUrl = liveUrl,
            sessionId = sessionId
        )

        scope.launch {
            _sosEvents.emit(event)
        }

        return event
    }

    fun cancelSOS() {
        _sosState.value = SOSState(isActive = false)
    }
}
