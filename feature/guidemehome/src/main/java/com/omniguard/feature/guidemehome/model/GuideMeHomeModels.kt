package com.omniguard.feature.guidemehome.model

import java.time.Instant

/**
 * Lighting rating for street segments.
 */
enum class LightingLevel(val safetyWeight: Float) {
    EXCELLENT_LIT(1.0f),
    MODERATE_LIT(0.7f),
    POORLY_LIT(0.3f),
    UNLIT(0.05f)
}

/**
 * Foot traffic / commercial activity rating.
 */
enum class FootTrafficLevel(val safetyWeight: Float) {
    HIGH_DENSITY(1.0f),
    MEDIUM_DENSITY(0.75f),
    LOW_DENSITY(0.4f),
    ISOLATED(0.1f)
}

/**
 * Navigation maneuver types for turn-by-turn navigation and haptic cues.
 */
enum class ManeuverType {
    STRAIGHT,
    TURN_LEFT,
    TURN_RIGHT,
    TURN_SHARP_LEFT,
    TURN_SHARP_RIGHT,
    U_TURN,
    ROUNDABOUT_ENTER,
    ROUNDABOUT_EXIT,
    ARRIVE_DESTINATION
}

/**
 * Haptic cue pattern identifiers for wrist navigation.
 */
enum class HapticCueType {
    NONE,
    PULSE_LEFT,          // 2 distinct short pulses
    PULSE_RIGHT,         // 3 distinct short pulses
    PULSE_U_TURN,        // 1 long continuous buzz
    PULSE_WARNING,       // 2 sharp buzzes (off route / safety hazard)
    PULSE_ARRIVAL        // Long celebration pulse
}

/**
 * Coordinate model.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double = 0.0
)

/**
 * Individual street or pathway segment along a route.
 */
data class SafeRouteSegment(
    val startPoint: GeoPoint,
    val endPoint: GeoPoint,
    val streetName: String,
    val distanceMeters: Double,
    val lightingLevel: LightingLevel,
    val footTrafficLevel: FootTrafficLevel,
    val hasCctvSurveillance: Boolean,
    val isMainThoroughfare: Boolean,
    val safetyScore: Float // 0.0 to 100.0
)

/**
 * Complete calculated safe route.
 */
data class SafeRoute(
    val routeId: String,
    val origin: GeoPoint,
    val destination: GeoPoint,
    val segments: List<SafeRouteSegment>,
    val totalDistanceMeters: Double,
    val estimatedDurationSeconds: Long,
    val compositeSafetyScore: Float, // Higher score = safer, well-lit thoroughfare
    val maneuvers: List<ManeuverInstruction>
)

/**
 * Turn-by-turn navigation maneuver instruction.
 */
data class ManeuverInstruction(
    val stepIndex: Int,
    val maneuver: ManeuverType,
    val instructionText: String,
    val streetName: String,
    val distanceToManeuverMeters: Double,
    val location: GeoPoint,
    val hapticCue: HapticCueType
)

/**
 * User consent state for live tracking with trusted contacts.
 */
sealed interface LiveStreamingConsentState {
    data object Idle : LiveStreamingConsentState
    data class PromptingConsent(
        val promptMessage: String = "Do you want to send your live location and route to your trusted contacts?",
        val targetContactsCount: Int
    ) : LiveStreamingConsentState
    data class Granted(val timestamp: Instant, val sharedWithContacts: List<String>) : LiveStreamingConsentState
    data class Denied(val timestamp: Instant, val reason: String = "User declined sharing") : LiveStreamingConsentState
}

/**
 * Real-time navigation tracking state.
 */
data class LiveRouteTrackingState(
    val isNavigating: Boolean = false,
    val currentPosition: GeoPoint? = null,
    val destination: GeoPoint? = null,
    val currentManeuver: ManeuverInstruction? = null,
    val distanceRemainingMeters: Double = 0.0,
    val timeRemainingSeconds: Long = 0,
    val hasArrivedHome: Boolean = false,
    val streamingConsentState: LiveStreamingConsentState = LiveStreamingConsentState.Idle,
    val lastSyncToWristTimestamp: Long = 0L
)
