package com.omniguard.feature.guidemehome.tracking

import com.omniguard.feature.guidemehome.model.GeoPoint
import com.omniguard.feature.guidemehome.model.LiveRouteTrackingState
import com.omniguard.feature.guidemehome.model.ManeuverInstruction
import com.omniguard.feature.guidemehome.model.SafeRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Dispatcher interface for notifying trusted contacts of live tracking updates and safe arrival.
 */
interface ContactBroadcastService {
    suspend fun streamLocationUpdate(currentLocation: GeoPoint, routeId: String)
    suspend fun broadcastSafeArrivalNotification(message: String = "User reached home safely")
}

/**
 * Coordinates live route tracking, location streaming, and home geofence arrival detection.
 */
class LiveRouteStreamingCoordinator(
    private val broadcastService: ContactBroadcastService,
    private val arrivalGeofenceRadiusMeters: Double = 25.0,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _trackingState = MutableStateFlow(LiveRouteTrackingState())
    val trackingState: StateFlow<LiveRouteTrackingState> = _trackingState.asStateFlow()

    private val _safeArrivalEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val safeArrivalEvent: SharedFlow<String> = _safeArrivalEvent.asSharedFlow()

    private var activeRoute: SafeRoute? = null

    fun startJourney(route: SafeRoute) {
        activeRoute = route
        _trackingState.value = LiveRouteTrackingState(
            isNavigating = true,
            currentPosition = route.origin,
            destination = route.destination,
            currentManeuver = route.maneuvers.firstOrNull(),
            distanceRemainingMeters = route.totalDistanceMeters,
            timeRemainingSeconds = route.estimatedDurationSeconds,
            hasArrivedHome = false
        )
    }

    /**
     * Ingests real-time GPS location updates.
     * Computes remaining distance, maneuvers, and detects home geofence arrival.
     */
    fun onLocationUpdate(currentLocation: GeoPoint) {
        val route = activeRoute ?: return
        if (!_trackingState.value.isNavigating) return

        val distanceToDestination = computeDistanceMeters(currentLocation, route.destination)

        // 1. Geofence Arrival Check (< 25m radius)
        if (distanceToDestination <= arrivalGeofenceRadiusMeters) {
            handleHomeArrival()
            return
        }

        // 2. Resolve current maneuver instruction
        val upcomingManeuver = route.maneuvers.firstOrNull { maneuver ->
            computeDistanceMeters(currentLocation, maneuver.location) <= 30.0
        } ?: _trackingState.value.currentManeuver

        _trackingState.value = _trackingState.value.copy(
            currentPosition = currentLocation,
            distanceRemainingMeters = distanceToDestination,
            currentManeuver = upcomingManeuver
        )

        // 3. Stream location update to trusted contacts
        scope.launch {
            broadcastService.streamLocationUpdate(currentLocation, route.routeId)
        }
    }

    private fun handleHomeArrival() {
        val arrivalMessage = "User reached home safely"
        _trackingState.value = _trackingState.value.copy(
            isNavigating = false,
            hasArrivedHome = true,
            distanceRemainingMeters = 0.0,
            timeRemainingSeconds = 0
        )

        scope.launch {
            broadcastService.broadcastSafeArrivalNotification(arrivalMessage)
            _safeArrivalEvent.emit(arrivalMessage)
        }
    }

    fun stopJourney() {
        _trackingState.value = _trackingState.value.copy(isNavigating = false)
        activeRoute = null
    }

    /**
     * Haversine distance formula between two geographic coordinates.
     */
    private fun computeDistanceMeters(p1: GeoPoint, p2: GeoPoint): Double {
        val earthRadiusM = 6371000.0
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusM * c
    }
}
