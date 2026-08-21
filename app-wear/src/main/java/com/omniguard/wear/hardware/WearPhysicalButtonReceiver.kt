package com.omniguard.wear.hardware

import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event types triggered by physical Wear OS watch buttons.
 */
sealed interface WearPhysicalButtonAction {
    data object FallAlarmCancelTriggered : WearPhysicalButtonAction
    data object SosTriplePressTriggered : WearPhysicalButtonAction
    data object NavigationNextStepRequested : WearPhysicalButtonAction
}

/**
 * Hardware Button & Crown Interceptor for Wear OS.
 * Handles KEYCODE_STEM_PRIMARY, STEM_1, STEM_2, and KEYCODE_NAVIGATE_NEXT.
 */
class WearPhysicalButtonReceiver {

    private val _buttonActions = MutableSharedFlow<WearPhysicalButtonAction>(extraBufferCapacity = 2)
    val buttonActions: SharedFlow<WearPhysicalButtonAction> = _buttonActions.asSharedFlow()

    private val pressHistory = mutableListOf<Long>()
    private val triplePressWindowMs = 1200L

    /**
     * Dispatches key events from Wear OS Activity or Compose key listeners.
     */
    fun handleKeyEvent(keyCode: Int, isFallAlarmActive: Boolean): Boolean {
        val now = System.currentTimeMillis()

        return when (keyCode) {
            KeyEvent.KEYCODE_STEM_PRIMARY,
            KeyEvent.KEYCODE_STEM_1,
            KeyEvent.KEYCODE_STEM_2 -> {
                // If a fall alarm is currently counting down, any side key immediately cancels the alarm
                if (isFallAlarmActive) {
                    _buttonActions.tryEmit(WearPhysicalButtonAction.FallAlarmCancelTriggered)
                    return true
                }

                // Check for SOS triple-press cadence
                pressHistory.add(now)
                val cutoff = now - triplePressWindowMs
                pressHistory.removeAll { it < cutoff }

                if (pressHistory.size >= 3) {
                    pressHistory.clear()
                    _buttonActions.tryEmit(WearPhysicalButtonAction.SosTriplePressTriggered)
                    return true
                }
                false
            }
            KeyEvent.KEYCODE_NAVIGATE_NEXT -> {
                _buttonActions.tryEmit(WearPhysicalButtonAction.NavigationNextStepRequested)
                true
            }
            else -> false
        }
    }
}
