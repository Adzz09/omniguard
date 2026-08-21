package com.omniguard.feature.sos.presentation

import com.omniguard.feature.sos.detector.TriplePressDetector
import com.omniguard.feature.sos.model.DuressVerificationResult
import com.omniguard.feature.sos.model.FakeScreenType
import com.omniguard.feature.sos.model.SilentPanicPayload
import com.omniguard.feature.sos.model.SosState
import com.omniguard.feature.sos.model.SosTriggerType
import com.omniguard.feature.sos.panic.SilentPanicDispatcher
import com.omniguard.feature.sos.pin.DuressPinVerifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for SOS feature.
 */
data class SosUiState(
    val sosState: SosState = SosState.Idle,
    val isSilentPanicActive: Boolean = false,
    val activeFakeScreen: FakeScreenType? = null,
    val isDecoyMode: Boolean = false,
    val lastDispatchedPayload: SilentPanicPayload? = null,
    val statusBanner: String = "OmniGuard SOS Ready"
)

/**
 * SOS ViewModel handling silent panic triggers, duress PIN verification, and decoy screen switching.
 */
class SosViewModel(
    private val triplePressDetector: TriplePressDetector,
    private val silentPanicDispatcher: SilentPanicDispatcher,
    private val duressPinVerifier: DuressPinVerifier,
    private val viewModelScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(SosUiState())
    val uiState: StateFlow<SosUiState> = _uiState.asStateFlow()

    init {
        observeTriplePress()
        observePanicDispatch()
    }

    private fun observeTriplePress() {
        viewModelScope.launch {
            triplePressDetector.triplePressEvents.collect {
                triggerSilentPanic(SosTriggerType.PHYSICAL_TRIPLE_PRESS_SIDE_BUTTON)
            }
        }
    }

    private fun observePanicDispatch() {
        viewModelScope.launch {
            silentPanicDispatcher.dispatchedEvents.collect { payload ->
                _uiState.value = _uiState.value.copy(
                    sosState = SosState.SilentPanicActive(payload),
                    isSilentPanicActive = true,
                    lastDispatchedPayload = payload,
                    statusBanner = "Silent emergency broadcast in progress"
                )
            }
        }
    }

    fun onPhysicalButtonPressed() {
        triplePressDetector.registerButtonPress()
    }

    fun triggerSilentPanic(trigger: SosTriggerType = SosTriggerType.PHYSICAL_TRIPLE_PRESS_SIDE_BUTTON) {
        silentPanicDispatcher.dispatchSilentPanic(trigger = trigger)
    }

    fun submitPin(pin: String, lat: Double = 0.0, lon: Double = 0.0): DuressVerificationResult {
        val result = duressPinVerifier.verifyPin(pin, lat, lon)
        when (result) {
            is DuressVerificationResult.NormalUnlock -> {
                _uiState.value = _uiState.value.copy(
                    sosState = SosState.Idle,
                    isDecoyMode = false,
                    activeFakeScreen = null,
                    statusBanner = "Unlocked"
                )
            }
            is DuressVerificationResult.DuressTriggered -> {
                // Duress PIN entered: UI displays decoy fake screen while panic payload is dispatched
                _uiState.value = _uiState.value.copy(
                    sosState = SosState.FakeScreenDisplayed(result.fakeScreenType),
                    isDecoyMode = true,
                    activeFakeScreen = result.fakeScreenType,
                    statusBanner = "System Ready"
                )
            }
            is DuressVerificationResult.InvalidPin -> {
                _uiState.value = _uiState.value.copy(
                    statusBanner = "Incorrect PIN. Try again."
                )
            }
        }
        return result
    }

    fun dismissDecoy() {
        _uiState.value = _uiState.value.copy(
            isDecoyMode = false,
            activeFakeScreen = null
        )
    }
}
