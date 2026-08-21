package com.omniguard.feature.sos.detector

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Interface for physical button triple press detection.
 */
interface PhysicalButtonDetector {
    fun onTriplePressSideButton(): Boolean
}

/**
 * Detects triple-press sequences on watch/phone physical side buttons (Power, Crown, STEM).
 * Triggers SOS when 3 presses occur within a configurable time window (default 1200ms).
 */
class TriplePressDetector(
    private val windowDurationMs: Long = 1200L,
    private val debounceDelayMs: Long = 80L
) : PhysicalButtonDetector {

    private val _triplePressEvents = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val triplePressEvents: SharedFlow<Long> = _triplePressEvents.asSharedFlow()

    private val pressTimestamps = mutableListOf<Long>()

    /**
     * Registers a physical button press event.
     * Returns true if this press completed a valid triple-press gesture.
     */
    fun registerButtonPress(timestampMs: Long = System.currentTimeMillis()): Boolean {
        // Debounce mechanical bounce
        val lastPress = pressTimestamps.lastOrNull()
        if (lastPress != null && (timestampMs - lastPress) < debounceDelayMs) {
            return false
        }

        pressTimestamps.add(timestampMs)

        // Remove presses older than window
        val cutoff = timestampMs - windowDurationMs
        pressTimestamps.removeAll { it < cutoff }

        if (pressTimestamps.size >= 3) {
            pressTimestamps.clear()
            _triplePressEvents.tryEmit(timestampMs)
            return true
        }

        return false
    }

    /**
     * Direct invocation when triple-press is confirmed at hardware/OS driver level.
     */
    override fun onTriplePressSideButton(): Boolean {
        val now = System.currentTimeMillis()
        _triplePressEvents.tryEmit(now)
        return true
    }
}
