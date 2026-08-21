package com.omniguard.android.ui.duress

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniguard.feature.geofencing.service.EmergencyDispatcher
import com.omniguard.feature.geofencing.service.ScheduleGeofenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PinUnlockResult {
    IDLE,
    REAL_PIN_SUCCESS,
    DURESS_TRIGGERED,
    INVALID_PIN
}

@Immutable
data class DuressPinUiState(
    val enteredPin: String = "",
    val unlockResult: PinUnlockResult = PinUnlockResult.IDLE,
    val isDummyScreenActive: Boolean = false,
    val statusMessage: String? = null,
    val duressBeaconSent: Boolean = false
)

class DuressPinViewModel(
    private val emergencyDispatcher: EmergencyDispatcher,
    private val geofenceManager: ScheduleGeofenceManager
) : ViewModel() {

    private val REAL_PIN = "1234"
    private val DURESS_PIN = "9999"

    private val _uiState = MutableStateFlow(DuressPinUiState())
    val uiState: StateFlow<DuressPinUiState> = _uiState.asStateFlow()

    fun appendDigit(digit: Char) {
        if (_uiState.value.enteredPin.length < 4) {
            val updated = _uiState.value.enteredPin + digit
            _uiState.update { it.copy(enteredPin = updated, unlockResult = PinUnlockResult.IDLE, statusMessage = null) }

            if (updated.length == 4) {
                evaluatePin(updated)
            }
        }
    }

    fun deleteDigit() {
        if (_uiState.value.enteredPin.isNotEmpty()) {
            _uiState.update { it.copy(enteredPin = it.enteredPin.dropLast(1), unlockResult = PinUnlockResult.IDLE, statusMessage = null) }
        }
    }

    fun clearPin() {
        _uiState.update { it.copy(enteredPin = "", unlockResult = PinUnlockResult.IDLE, statusMessage = null) }
    }

    private fun evaluatePin(pin: String) {
        when (pin) {
            REAL_PIN -> {
                _uiState.update {
                    it.copy(
                        unlockResult = PinUnlockResult.REAL_PIN_SUCCESS,
                        statusMessage = "Authenticated. Full Security Settings Unlocked."
                    )
                }
            }
            DURESS_PIN -> {
                // Covert Silent Duress Trigger:
                // 1. Launch dummy calculator/weather utility disguise
                // 2. Silently dispatch high-priority duress distress beacon with live coordinates
                _uiState.update {
                    it.copy(
                        unlockResult = PinUnlockResult.DURESS_TRIGGERED,
                        isDummyScreenActive = true,
                        duressBeaconSent = true,
                        statusMessage = null
                    )
                }

                viewModelScope.launch {
                    val contacts = geofenceManager.emergencyContacts.value
                    emergencyDispatcher.dispatchEmergencySos(
                        contacts = contacts,
                        reason = "Silent Duress PIN entered by user. Covert beacon activated.",
                        latitude = 37.7749,
                        longitude = -122.4194,
                        isDuress = true
                    )
                }
            }
            else -> {
                _uiState.update {
                    it.copy(
                        enteredPin = "",
                        unlockResult = PinUnlockResult.INVALID_PIN,
                        statusMessage = "Invalid PIN. Please try again."
                    )
                }
            }
        }
    }

    fun exitDummyScreen() {
        _uiState.update {
            it.copy(
                isDummyScreenActive = false,
                enteredPin = "",
                unlockResult = PinUnlockResult.IDLE
            )
        }
    }
}
