package com.omniguard.feature.falldetection.timer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State representing active fall countdown timer.
 */
data class FallCountdownState(
    val isActive: Boolean = false,
    val totalSeconds: Int = 60,
    val remainingSeconds: Int = 60,
    val progress: Float = 1.0f,
    val isCriticalWarning: Boolean = false, // True when < 15 seconds remaining
    val isCompleted: Boolean = false,
    val isCancelled: Boolean = false
)

/**
 * High-precision countdown timer for fall detection emergency escalation.
 * Configurable duration between 60s and 120s with real-time StateFlow emissions.
 */
class FallCountdownTimer(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _countdownState = MutableStateFlow(FallCountdownState())
    val countdownState: StateFlow<FallCountdownState> = _countdownState.asStateFlow()

    private var countdownJob: Job? = null

    /**
     * Starts the emergency countdown.
     * @param durationSeconds Countdown time (clamped between 60s and 120s).
     * @param onComplete Callback invoked when countdown reaches 0s without cancellation.
     */
    fun startCountdown(
        durationSeconds: Int = 60,
        onComplete: suspend () -> Unit = {}
    ) {
        countdownJob?.cancel()
        val clampedDuration = durationSeconds.coerceIn(60, 120)

        _countdownState.value = FallCountdownState(
            isActive = true,
            totalSeconds = clampedDuration,
            remainingSeconds = clampedDuration,
            progress = 1.0f,
            isCriticalWarning = false,
            isCompleted = false,
            isCancelled = false
        )

        countdownJob = scope.launch {
            for (sec in clampedDuration downTo 1) {
                val progress = sec.toFloat() / clampedDuration.toFloat()
                _countdownState.value = FallCountdownState(
                    isActive = true,
                    totalSeconds = clampedDuration,
                    remainingSeconds = sec,
                    progress = progress,
                    isCriticalWarning = sec <= 15,
                    isCompleted = false,
                    isCancelled = false
                )
                delay(1000L)
            }

            _countdownState.value = _countdownState.value.copy(
                isActive = false,
                remainingSeconds = 0,
                progress = 0.0f,
                isCompleted = true
            )
            onComplete()
        }
    }

    /**
     * Cancels the active countdown timer.
     */
    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _countdownState.value = _countdownState.value.copy(
            isActive = false,
            isCancelled = true
        )
    }

    /**
     * Resets timer back to idle state.
     */
    fun reset() {
        countdownJob?.cancel()
        countdownJob = null
        _countdownState.value = FallCountdownState()
    }
}
