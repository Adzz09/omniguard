package com.omniguard.wear.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Haptic feedback types supported on Wear OS.
 */
enum class WearHapticType {
    MANEUVER_LEFT,         // 2 short pulses (e.g. 100ms on, 100ms off, 100ms on)
    MANEUVER_RIGHT,        // 3 short pulses
    MANEUVER_U_TURN,       // 1 long continuous buzz (400ms)
    SAFE_ARRIVAL,          // Gentle celebratory buzz
    FALL_COUNTDOWN_TICK,   // Warning pulse (rhythmic)
    FALL_CRITICAL_ALARM,   // Heavy escalating alarm pulse
    SILENT_SOS_CONFIRM     // Discreet micro-tap (so user knows SOS dispatched without alert sounds)
}

/**
 * Wear OS Haptic Feedback Controller.
 * Provides distinct, glance-free vibration cues for bikers, visually impaired, and elderly users.
 */
class WearHapticController(
    context: Context? = null
) {
    private val vibrator: Vibrator? = context?.let { ctx ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun playHaptic(type: WearHapticType) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = when (type) {
                WearHapticType.MANEUVER_LEFT -> {
                    // Two pulses for left
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 120, 100, 120),
                        intArrayOf(0, 200, 0, 200),
                        -1
                    )
                }
                WearHapticType.MANEUVER_RIGHT -> {
                    // Three pulses for right
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 100, 80, 100, 80, 100),
                        intArrayOf(0, 220, 0, 220, 0, 220),
                        -1
                    )
                }
                WearHapticType.MANEUVER_U_TURN -> {
                    VibrationEffect.createOneShot(450, 255)
                }
                WearHapticType.SAFE_ARRIVAL -> {
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 200, 100, 300),
                        intArrayOf(0, 180, 0, 255),
                        -1
                    )
                }
                WearHapticType.FALL_COUNTDOWN_TICK -> {
                    VibrationEffect.createOneShot(80, 160)
                }
                WearHapticType.FALL_CRITICAL_ALARM -> {
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 250, 80, 250, 80, 400),
                        intArrayOf(0, 255, 0, 255, 0, 255),
                        -1
                    )
                }
                WearHapticType.SILENT_SOS_CONFIRM -> {
                    // Very discreet low-amplitude double micro-tap
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 30, 60, 30),
                        intArrayOf(0, 70, 0, 70),
                        -1
                    )
                }
            }
            vib.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            when (type) {
                WearHapticType.MANEUVER_LEFT -> vib.vibrate(longArrayOf(0, 120, 100, 120), -1)
                WearHapticType.MANEUVER_RIGHT -> vib.vibrate(longArrayOf(0, 100, 80, 100, 80, 100), -1)
                WearHapticType.MANEUVER_U_TURN -> vib.vibrate(450)
                WearHapticType.SAFE_ARRIVAL -> vib.vibrate(longArrayOf(0, 200, 100, 300), -1)
                WearHapticType.FALL_COUNTDOWN_TICK -> vib.vibrate(80)
                WearHapticType.FALL_CRITICAL_ALARM -> vib.vibrate(longArrayOf(0, 250, 80, 250), -1)
                WearHapticType.SILENT_SOS_CONFIRM -> vib.vibrate(longArrayOf(0, 30, 60, 30), -1)
            }
        }
    }
}
