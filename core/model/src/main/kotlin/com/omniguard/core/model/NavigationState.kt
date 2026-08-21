package com.omniguard.core.model

import kotlinx.serialization.Serializable

/**
 * Step-by-step turn or checkpoint along a safe navigation route.
 *
 * @property stepIndex Zero-based index of the navigation step.
 * @property instruction Human-readable direction (e.g., "Turn left onto High Street").
 * @property distanceMeters Distance until the next waypoint.
 * @property lat Target latitude for this step.
 * @property lng Target longitude for this step.
 * @property isCompleted Whether this waypoint has been passed.
 */
@Serializable
data class NavStep(
    val stepIndex: Int,
    val instruction: String,
    val distanceMeters: Double,
    val lat: Double,
    val lng: Double,
    val isCompleted: Boolean = false
)

/**
 * Real-time navigation monitoring and live journey tracking state.
 *
 * @property destinationName Human-readable name of journey target (e.g., "Home").
 * @property targetLat Target destination latitude.
 * @property targetLng Target destination longitude.
 * @property currentLat Last known user latitude.
 * @property currentLng Last known user longitude.
 * @property steps Ordered list of remaining and completed navigation steps.
 * @property isSharingLiveLocation Whether real-time telemetry is actively being broadcast to contacts.
 * @property homeGeofenceReached Whether the arrival safe zone perimeter has been entered.
 * @property estimatedArrivalTimestamp Epoch timestamp in milliseconds for ETA.
 * @property routeDeviationMeters Measured perpendicular distance deviation from planned safe path.
 */
@Serializable
data class NavigationState(
    val destinationName: String,
    val targetLat: Double,
    val targetLng: Double,
    val currentLat: Double,
    val currentLng: Double,
    val steps: List<NavStep> = emptyList(),
    val isSharingLiveLocation: Boolean = false,
    val homeGeofenceReached: Boolean = false,
    val estimatedArrivalTimestamp: Long = 0L,
    val routeDeviationMeters: Double = 0.0
)
