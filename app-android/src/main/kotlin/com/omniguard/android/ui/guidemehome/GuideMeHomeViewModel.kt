package com.omniguard.android.ui.guidemehome

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniguard.core.model.EmergencyContact
import com.omniguard.feature.geofencing.service.ScheduleGeofenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class RouteWaypoint(
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val isCheckpoint: Boolean = false,
    val isHome: Boolean = false
)

@Immutable
data class GuideMeHomeUiState(
    val destinationName: String = "Home Sanctuary",
    val destinationAddress: String = "742 Evergreen Terrace, SF",
    val etaMinutes: Int = 18,
    val distanceRemainingKm: Float = 2.4f,
    val isLiveSharingConsentGranted: Boolean = true,
    val activeEscortContacts: List<EmergencyContact> = emptyList(),
    val waypoints: List<RouteWaypoint> = listOf(
        RouteWaypoint("Current Location (Campus Library)", 37.7749, -122.4194, isCheckpoint = false),
        RouteWaypoint("Market St Safe Corridor", 37.7780, -122.4180, isCheckpoint = true),
        RouteWaypoint("Transit Hub Well-Lit Checkpoint", 37.7810, -122.4170, isCheckpoint = true),
        RouteWaypoint("Home Safe Zone Perimeter", 37.7833, -122.4167, isHome = true)
    ),
    val isHomeGeofenceReached: Boolean = false,
    val isNavigationActive: Boolean = true
)

class GuideMeHomeViewModel(
    private val geofenceManager: ScheduleGeofenceManager
) : ViewModel() {

    private val _isLiveSharingGranted = MutableStateFlow(true)
    private val _isNavigationActive = MutableStateFlow(true)

    val uiState: StateFlow<GuideMeHomeUiState> = combine(
        geofenceManager.emergencyContacts,
        _isLiveSharingGranted,
        _isNavigationActive
    ) { contacts, isSharing, isActive ->
        GuideMeHomeUiState(
            isLiveSharingConsentGranted = isSharing,
            activeEscortContacts = if (isSharing) contacts else emptyList(),
            isNavigationActive = isActive
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GuideMeHomeUiState()
    )

    fun toggleLiveSharingConsent(enabled: Boolean) {
        _isLiveSharingGranted.value = enabled
    }

    fun endNavigation() {
        _isNavigationActive.value = false
    }
}
