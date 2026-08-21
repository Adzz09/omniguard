package com.omniguard.wear.presentation

import com.omniguard.wear.haptics.WearHapticController
import com.omniguard.wear.haptics.WearHapticType
import com.omniguard.wear.hardware.WearPhysicalButtonAction
import com.omniguard.wear.hardware.WearPhysicalButtonReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Screen navigation routes for Wear OS.
 */
enum class WearScreenRoute {
    STATUS,
    FALL_COUNTDOWN,
    NAVIGATION,
    SILENT_SOS
}

/**
 * UI State for the Wear OS companion app.
 */
data class WearAppUiState(
    val currentRoute: WearScreenRoute = WearScreenRoute.STATUS,
    val isFallAlarmActive: Boolean = false,
    val fallRemainingSeconds: Int = 60,
    val fallTotalSeconds: Int = 60,
    val fallImpactG: Float = 0f,
    val navManeuverIcon: String = "↱",
    val navDistanceText: String = "50 m",
    val navStreetName: String = "Grand Plaza Way",
    val navInstruction: String = "Turn Right onto Grand Plaza Way",
    val isArrival: Boolean = false,
    val statusLocation: String = "Tuition",
    val statusTime: String = "4:02 PM",
    val isSilentSosDispatched: Boolean = false,
    val batteryPercent: Int = 88
)

/**
 * Wear OS Master ViewModel orchestrating glanceable screens, haptics, and physical crown inputs.
 */
class WearMainViewModel(
    private val hapticController: WearHapticController = WearHapticController(),
    private val buttonReceiver: WearPhysicalButtonReceiver = WearPhysicalButtonReceiver(),
    private val viewModelScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(WearAppUiState())
    val uiState: StateFlow<WearAppUiState> = _uiState.asStateFlow()

    init {
        observePhysicalButtons()
    }

    private fun observePhysicalButtons() {
        viewModelScope.launch {
            buttonReceiver.buttonActions.collect { action ->
                when (action) {
                    is WearPhysicalButtonAction.FallAlarmCancelTriggered -> {
                        cancelFallAlarm()
                    }
                    is WearPhysicalButtonAction.SosTriplePressTriggered -> {
                        triggerSilentSos()
                    }
                    is WearPhysicalButtonAction.NavigationNextStepRequested -> {
                        // Advance or cycle nav step
                    }
                }
            }
        }
    }

    fun onFallDetected(impactG: Float, countdownSeconds: Int = 60) {
        _uiState.value = _uiState.value.copy(
            currentRoute = WearScreenRoute.FALL_COUNTDOWN,
            isFallAlarmActive = true,
            fallRemainingSeconds = countdownSeconds,
            fallTotalSeconds = countdownSeconds,
            fallImpactG = impactG
        )
        hapticController.playHaptic(WearHapticType.FALL_COUNTDOWN_TICK)
    }

    fun onFallCountdownTick(remaining: Int) {
        _uiState.value = _uiState.value.copy(fallRemainingSeconds = remaining)
        if (remaining <= 15) {
            hapticController.playHaptic(WearHapticType.FALL_CRITICAL_ALARM)
        } else {
            hapticController.playHaptic(WearHapticType.FALL_COUNTDOWN_TICK)
        }
    }

    fun cancelFallAlarm() {
        _uiState.value = _uiState.value.copy(
            isFallAlarmActive = false,
            currentRoute = WearScreenRoute.STATUS
        )
    }

    fun updateNavigation(
        icon: String,
        distance: String,
        street: String,
        instruction: String,
        hapticType: WearHapticType = WearHapticType.MANEUVER_RIGHT,
        isArrival: Boolean = false
    ) {
        _uiState.value = _uiState.value.copy(
            currentRoute = WearScreenRoute.NAVIGATION,
            navManeuverIcon = icon,
            navDistanceText = distance,
            navStreetName = street,
            navInstruction = instruction,
            isArrival = isArrival
        )
        hapticController.playHaptic(hapticType)
    }

    fun triggerSilentSos() {
        _uiState.value = _uiState.value.copy(
            currentRoute = WearScreenRoute.SILENT_SOS,
            isSilentSosDispatched = true
        )
        // Discreet haptic feedback for user confirmation
        hapticController.playHaptic(WearHapticType.SILENT_SOS_CONFIRM)
    }

    fun updateGeofenceStatus(locationName: String, time: String) {
        _uiState.value = _uiState.value.copy(
            currentRoute = WearScreenRoute.STATUS,
            statusLocation = locationName,
            statusTime = time
        )
        hapticController.playHaptic(WearHapticType.SAFE_ARRIVAL)
    }

    fun handleHardwareKey(keyCode: Int): Boolean {
        return buttonReceiver.handleKeyEvent(keyCode, _uiState.value.isFallAlarmActive)
    }
}
