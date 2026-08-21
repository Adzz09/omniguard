package com.omniguard.feature.guidemehome.presentation

import com.omniguard.feature.guidemehome.consent.EmergencyConsentManager
import com.omniguard.feature.guidemehome.model.GeoPoint
import com.omniguard.feature.guidemehome.model.LiveRouteTrackingState
import com.omniguard.feature.guidemehome.model.LiveStreamingConsentState
import com.omniguard.feature.guidemehome.model.SafeRoute
import com.omniguard.feature.guidemehome.router.SafeRouteResolver
import com.omniguard.feature.guidemehome.sync.WristNavigationSyncEngine
import com.omniguard.feature.guidemehome.tracking.LiveRouteStreamingCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the Guide Me Home feature.
 */
data class GuideMeHomeUiState(
    val activeRoute: SafeRoute? = null,
    val trackingState: LiveRouteTrackingState = LiveRouteTrackingState(),
    val consentState: LiveStreamingConsentState = LiveStreamingConsentState.Idle,
    val destinationLabel: String = "Home",
    val safetyScore: Float = 0.0f,
    val userStatusBanner: String = "Ready for safe journey"
)

/**
 * Guide Me Home ViewModel orchestrating routing, consent, streaming, and wrist haptic sync.
 */
class GuideMeHomeViewModel(
    private val routeResolver: SafeRouteResolver,
    private val consentManager: EmergencyConsentManager,
    private val streamingCoordinator: LiveRouteStreamingCoordinator,
    private val wristSyncEngine: WristNavigationSyncEngine,
    private val viewModelScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(GuideMeHomeUiState())
    val uiState: StateFlow<GuideMeHomeUiState> = _uiState.asStateFlow()

    init {
        observeConsent()
        observeTracking()
    }

    private fun observeConsent() {
        viewModelScope.launch {
            consentManager.consentState.collect { consent ->
                _uiState.value = _uiState.value.copy(
                    consentState = consent,
                    trackingState = _uiState.value.trackingState.copy(streamingConsentState = consent)
                )
            }
        }
    }

    private fun observeTracking() {
        viewModelScope.launch {
            streamingCoordinator.trackingState.collect { tracking ->
                _uiState.value = _uiState.value.copy(
                    trackingState = tracking,
                    userStatusBanner = if (tracking.hasArrivedHome) "Arrived safely at Home" else "Navigating on safe lit route"
                )

                // Sync maneuver and haptic cues to Wear OS
                val maneuver = tracking.currentManeuver
                if (maneuver != null && tracking.isNavigating) {
                    wristSyncEngine.syncManeuverToWrist(maneuver, tracking.distanceRemainingMeters)
                }
            }
        }

        viewModelScope.launch {
            streamingCoordinator.safeArrivalEvent.collect { arrivalMessage ->
                _uiState.value = _uiState.value.copy(
                    userStatusBanner = arrivalMessage
                )
            }
        }
    }

    fun requestSafeJourney(origin: GeoPoint, destination: GeoPoint) {
        val safeRoute = routeResolver.resolveSafestRoute(origin, destination)
        _uiState.value = _uiState.value.copy(
            activeRoute = safeRoute,
            safetyScore = safeRoute.compositeSafetyScore,
            userStatusBanner = "Safe route resolved (${String.format("%.0f", safeRoute.compositeSafetyScore)}% lit/monitored)"
        )
        // Request consent before starting broadcast
        consentManager.requestConsent()
    }

    fun onConsentGranted() {
        consentManager.grantConsent()
        val route = _uiState.value.activeRoute
        if (route != null) {
            streamingCoordinator.startJourney(route)
        }
    }

    fun onConsentDenied() {
        consentManager.denyConsent()
        val route = _uiState.value.activeRoute
        if (route != null) {
            // Still provide local routing, but do not broadcast
            streamingCoordinator.startJourney(route)
        }
    }

    fun onLocationChanged(newLocation: GeoPoint) {
        streamingCoordinator.onLocationUpdate(newLocation)
    }

    fun endJourney() {
        streamingCoordinator.stopJourney()
        consentManager.reset()
        _uiState.value = _uiState.value.copy(
            activeRoute = null,
            userStatusBanner = "Journey ended."
        )
    }
}
