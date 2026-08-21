package com.omniguard.feature.falldetection.hardware

import com.omniguard.feature.falldetection.model.CancellationSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Interface defining hardware button cancellation events for fall detection.
 */
interface FallCancellationListener {
    fun cancelFallDetection(source: CancellationSource = CancellationSource.HARDWARE_SIDE_KEY)
}

/**
 * Hardware side key, volume, and crown button cancellation listener.
 * Handles Android KeyEvents (Volume Down, Power, Stem keys) to cancel false-positive fall alarms.
 */
class HardwareButtonCancellationListener : FallCancellationListener {

    private val _cancellationEvents = MutableSharedFlow<CancellationSource>(extraBufferCapacity = 1)
    val cancellationEvents: SharedFlow<CancellationSource> = _cancellationEvents.asSharedFlow()

    override fun cancelFallDetection(source: CancellationSource) {
        _cancellationEvents.tryEmit(source)
    }

    /**
     * Intercepts Android physical KeyEvents.
     * KeyCode 25 = KEYCODE_VOLUME_DOWN
     * KeyCode 26 = KEYCODE_POWER
     * KeyCode 264 = KEYCODE_STEM_1 (Wear OS)
     * KeyCode 265 = KEYCODE_STEM_2 (Wear OS)
     */
    fun onHardwareKeyEvent(keyCode: Int, isLongPress: Boolean = false): Boolean {
        return when (keyCode) {
            25, 24 -> { // Volume Down or Volume Up
                cancelFallDetection(CancellationSource.HARDWARE_SIDE_KEY)
                true
            }
            264, 265 -> { // Wear OS Stem keys
                cancelFallDetection(CancellationSource.WEARABLE_CROWN_TAP)
                true
            }
            26 -> { // Power key
                if (isLongPress) {
                    cancelFallDetection(CancellationSource.HARDWARE_SIDE_KEY)
                    true
                } else false
            }
            else -> false
        }
    }
}
