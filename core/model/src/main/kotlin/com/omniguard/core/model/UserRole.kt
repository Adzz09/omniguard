package com.omniguard.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class UserRole(
    val title: String,
    val subtitle: String,
    val description: String,
    val defaultPreset: SafetyPreset
) {
    BIKER(
        title = "Biker / Commuter",
        subtitle = "High speed & impact protection",
        description = "Optimized for cyclists & motorcycle commuters. High fall/crash sensitivity, auto-collision broadcast, and live route corridor check-ins.",
        defaultPreset = SafetyPreset(
            fallDetectionSensitivity = FallSensitivity.HIGH,
            autoAlertDelaySeconds = 15,
            enableLiveRouteEscort = true,
            silentDuressEnabled = true,
            hapticIntensity = HapticIntensity.STRONG,
            inactivityTimeoutMinutes = 5
        )
    ),
    STUDENT(
        title = "Student / Night Transit",
        subtitle = "Schedule safe zones & duress escort",
        description = "Geared towards university and city commuters. Timetabled safe zones (e.g., Campus/Home), stealth duress PINs, and low-profile watch pings.",
        defaultPreset = SafetyPreset(
            fallDetectionSensitivity = FallSensitivity.MEDIUM,
            autoAlertDelaySeconds = 20,
            enableLiveRouteEscort = true,
            silentDuressEnabled = true,
            hapticIntensity = HapticIntensity.MEDIUM,
            inactivityTimeoutMinutes = 10
        )
    ),
    ELDERLY(
        title = "Elderly / Independent Living",
        subtitle = "Maximum fall safety & easy SOS",
        description = "Focused on gentle, fail-safe monitoring. Maximum fall sensitivity, inactivity alerts, direct caregiver voice link, and large-target emergency triggers.",
        defaultPreset = SafetyPreset(
            fallDetectionSensitivity = FallSensitivity.MAXIMUM,
            autoAlertDelaySeconds = 30,
            enableLiveRouteEscort = false,
            silentDuressEnabled = false,
            hapticIntensity = HapticIntensity.MAXIMUM,
            inactivityTimeoutMinutes = 15
        )
    )
}

@Serializable
data class SafetyPreset(
    val fallDetectionSensitivity: FallSensitivity = FallSensitivity.MEDIUM,
    val autoAlertDelaySeconds: Int = 20,
    val enableLiveRouteEscort: Boolean = true,
    val silentDuressEnabled: Boolean = true,
    val hapticIntensity: HapticIntensity = HapticIntensity.MEDIUM,
    val inactivityTimeoutMinutes: Int = 10
)

@Serializable
enum class FallSensitivity {
    LOW, MEDIUM, HIGH, MAXIMUM
}

@Serializable
enum class HapticIntensity {
    GENTLE, MEDIUM, STRONG, MAXIMUM
}
