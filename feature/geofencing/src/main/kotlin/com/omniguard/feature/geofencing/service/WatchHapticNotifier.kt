package com.omniguard.feature.geofencing.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class HapticPattern {
    SILENT_ENTRY_PING,
    SILENT_EXIT_PING,
    DURESS_BEACON,
    SOS_URGENT,
    TEST_PULSE
}

class WatchHapticNotifier(private val context: Context? = null) {

    private val _hapticEvents = MutableSharedFlow<HapticEvent>(extraBufferCapacity = 10)
    val hapticEvents: SharedFlow<HapticEvent> = _hapticEvents.asSharedFlow()

    data class HapticEvent(
        val pattern: HapticPattern,
        val timestampMillis: Long = System.currentTimeMillis(),
        val description: String
    )

    fun triggerWatchHaptic(pattern: HapticPattern, reason: String) {
        val event = HapticEvent(
            pattern = pattern,
            description = "Watch Haptic: $pattern triggered ($reason)"
        )
        _hapticEvents.tryEmit(event)
        Log.d(TAG, "Sent Silent Watch BLE Haptic: $pattern | Reason: $reason")

        // Trigger local companion haptic if context available
        context?.let { ctx ->
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                if (vibrator != null && vibrator.hasVibrator()) {
                    val timings = when (pattern) {
                        HapticPattern.SILENT_ENTRY_PING -> longArrayOf(0, 100, 100, 100)
                        HapticPattern.SILENT_EXIT_PING -> longArrayOf(0, 250, 150, 250)
                        HapticPattern.DURESS_BEACON -> longArrayOf(0, 80, 50, 80, 50, 80)
                        HapticPattern.SOS_URGENT -> longArrayOf(0, 500, 200, 500, 200, 500)
                        HapticPattern.TEST_PULSE -> longArrayOf(0, 150)
                    }
                    val amplitudes = when (pattern) {
                        HapticPattern.SILENT_ENTRY_PING -> intArrayOf(0, 80, 0, 80)
                        HapticPattern.SILENT_EXIT_PING -> intArrayOf(0, 150, 0, 150)
                        HapticPattern.DURESS_BEACON -> intArrayOf(0, 60, 0, 60, 0, 60)
                        HapticPattern.SOS_URGENT -> intArrayOf(0, 255, 0, 255, 0, 255)
                        HapticPattern.TEST_PULSE -> intArrayOf(0, 180)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(timings, -1)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to vibrate phone fallback: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "WatchHapticNotifier"
    }
}
