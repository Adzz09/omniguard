package com.omniguard.feature.falldetection.presentation

import com.omniguard.feature.falldetection.escalation.FallEscalationManager
import com.omniguard.feature.falldetection.hardware.HardwareButtonCancellationListener
import com.omniguard.feature.falldetection.model.CancellationSource
import com.omniguard.feature.falldetection.model.FallDetectionConfig
import com.omniguard.feature.falldetection.model.FallDetectionState
import com.omniguard.feature.falldetection.sensor.FallSensorProcessor
import com.omniguard.feature.falldetection.timer.FallCountdownTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for Fall Detection Screen.
 */
data class FallDetectionUiState(
    val state: FallDetectionState = FallDetectionState.Idle,
    val isMonitoring: Boolean = false,
    val countdownRemaining: Int = 60,
    val countdownTotal: Int = 60,
    val progress: Float = 1.0f,
    val impactG: Float = 0f,
    val statusMessage: String = "Monitoring active for sudden impacts"
)

/**
 * MVI / MVVM ViewModel orchestrating Fall Detection lifecycle.
 */
class FallDetectionViewModel(
    private val sensorProcessor: FallSensorProcessor,
    private val countdownTimer: FallCountdownTimer,
    private val hardwareListener: HardwareButtonCancellationListener,
    private val escalationManager: FallEscalationManager,
    private val config: FallDetectionConfig = FallDetectionConfig(),
    private val viewModelScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(FallDetectionUiState())
    val uiState: StateFlow<FallDetectionUiState> = _uiState.asStateFlow()

    init {
        observeSensorEvents()
        observeCountdown()
        observeHardwareCancellations()
    }

    private fun observeSensorEvents() {
        viewModelScope.launch {
            sensorProcessor.potentialFallDetected.collect { impact ->
                _uiState.value = _uiState.value.copy(
                    state = FallDetectionState.ImpactDetected(impact.accelerationG, impact.timestampMs),
                    impactG = impact.accelerationG,
                    statusMessage = "High impact detected (${String.format("%.1f", impact.accelerationG)}G). Checking immobility..."
                )
            }
        }

        viewModelScope.launch {
            sensorProcessor.confirmedFallEvent.collect { impact ->
                _uiState.value = _uiState.value.copy(
                    state = FallDetectionState.ImmobilityDetected(impact.accelerationG, config.immobilityWindowMs),
                    impactG = impact.accelerationG,
                    statusMessage = "Fall confirmed! Starting emergency countdown."
                )
                startCountdown(impact.accelerationG)
            }
        }
    }

    private fun observeCountdown() {
        viewModelScope.launch {
            countdownTimer.countdownState.collect { timerState ->
                if (timerState.isActive) {
                    _uiState.value = _uiState.value.copy(
                        state = FallDetectionState.WarningCountdown(
                            remainingSeconds = timerState.remainingSeconds,
                            totalSeconds = timerState.totalSeconds,
                            impactG = _uiState.value.impactG,
                            progress = timerState.progress
                        ),
                        countdownRemaining = timerState.remainingSeconds,
                        countdownTotal = timerState.totalSeconds,
                        progress = timerState.progress,
                        statusMessage = "Emergency dispatch in ${timerState.remainingSeconds}s. Press Cancel if safe."
                    )
                }
            }
        }
    }

    private fun observeHardwareCancellations() {
        viewModelScope.launch {
            hardwareListener.cancellationEvents.collect { source ->
                cancelFall(source)
            }
        }
    }

    fun startMonitoring() {
        sensorProcessor.startMonitoring()
        _uiState.value = _uiState.value.copy(
            isMonitoring = true,
            state = FallDetectionState.Idle,
            statusMessage = "Fall monitoring active."
        )
    }

    fun stopMonitoring() {
        sensorProcessor.stopMonitoring()
        countdownTimer.cancelCountdown()
        _uiState.value = _uiState.value.copy(
            isMonitoring = false,
            state = FallDetectionState.Idle,
            statusMessage = "Fall monitoring paused."
        )
    }

    private fun startCountdown(impactG: Float) {
        countdownTimer.startCountdown(
            durationSeconds = config.countdownDurationSeconds,
            onComplete = {
                val payload = escalationManager.triggerEscalation(peakImpactG = impactG)
                _uiState.value = _uiState.value.copy(
                    state = FallDetectionState.Escalated(payload),
                    statusMessage = "Emergency Escalation Sent! Contacts & 911 notified."
                )
            }
        )
    }

    fun cancelFall(source: CancellationSource = CancellationSource.USER_UI_TOUCH) {
        countdownTimer.cancelCountdown()
        _uiState.value = _uiState.value.copy(
            state = FallDetectionState.Cancelled(source, System.currentTimeMillis()),
            countdownRemaining = config.countdownDurationSeconds,
            progress = 1.0f,
            statusMessage = "Fall alarm cancelled via ${source.name}."
        )
    }
}
