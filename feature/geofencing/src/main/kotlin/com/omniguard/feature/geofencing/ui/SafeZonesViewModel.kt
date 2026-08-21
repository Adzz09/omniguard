package com.omniguard.feature.geofencing.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniguard.core.model.EmergencyContact
import com.omniguard.core.model.SafeZone
import com.omniguard.core.model.ScheduleWindow
import com.omniguard.feature.geofencing.service.ScheduleGeofenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

@Immutable
data class SafeZonesUiState(
    val safeZones: List<SafeZone> = emptyList(),
    val availableContacts: List<EmergencyContact> = emptyList(),
    val isCreatingNew: Boolean = false,
    val draftZone: SafeZone = SafeZone(
        id = "",
        name = "",
        latitude = 37.7749,
        longitude = -122.4194,
        radiusMeters = 200f,
        scheduleWindow = ScheduleWindow(
            activeDays = setOf(1, 2, 3, 4, 5),
            startHour = 16,
            startMinute = 0,
            endHour = 18,
            endMinute = 0
        )
    ),
    val recentBoundaryAlertMessage: String? = null
)

class SafeZonesViewModel(
    private val geofenceManager: ScheduleGeofenceManager
) : ViewModel() {

    private val _isCreatingNew = MutableStateFlow(false)
    private val _draftZone = MutableStateFlow(createDefaultDraft())
    private val _recentAlertMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SafeZonesUiState> = combine(
        geofenceManager.safeZones,
        geofenceManager.emergencyContacts,
        _isCreatingNew,
        _draftZone,
        _recentAlertMessage
    ) { safeZones, contacts, isCreating, draft, alertMsg ->
        SafeZonesUiState(
            safeZones = safeZones,
            availableContacts = contacts,
            isCreatingNew = isCreating,
            draftZone = draft,
            recentBoundaryAlertMessage = alertMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SafeZonesUiState()
    )

    fun startNewZoneCreation(defaultLat: Double = 37.7749, defaultLng: Double = -122.4194) {
        _draftZone.value = createDefaultDraft().copy(
            id = UUID.randomUUID().toString(),
            latitude = defaultLat,
            longitude = defaultLng
        )
        _isCreatingNew.value = true
    }

    fun dismissCreationDialog() {
        _isCreatingNew.value = false
    }

    fun updateDraftName(name: String) {
        _draftZone.update { it.copy(name = name) }
    }

    fun updateDraftRadius(radiusMeters: Float) {
        _draftZone.update { it.copy(radiusMeters = radiusMeters) }
    }

    fun updateDraftLocation(lat: Double, lng: Double) {
        _draftZone.update { it.copy(latitude = lat, longitude = lng) }
    }

    fun toggleDraftDay(dayOfWeekValue: Int) {
        _draftZone.update { current ->
            val currentDays = current.scheduleWindow.activeDays.toMutableSet()
            if (currentDays.contains(dayOfWeekValue)) {
                if (currentDays.size > 1) currentDays.remove(dayOfWeekValue)
            } else {
                currentDays.add(dayOfWeekValue)
            }
            current.copy(scheduleWindow = current.scheduleWindow.copy(activeDays = currentDays))
        }
    }

    fun updateDraftTime(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) {
        _draftZone.update { current ->
            current.copy(
                scheduleWindow = current.scheduleWindow.copy(
                    startHour = startHour,
                    startMinute = startMinute,
                    endHour = endHour,
                    endMinute = endMinute
                )
            )
        }
    }

    fun toggleDraftContactAlert(contactId: String) {
        _draftZone.update { current ->
            val currentList = current.notifyContactIds.toMutableList()
            if (currentList.contains(contactId)) {
                currentList.remove(contactId)
            } else {
                currentList.add(contactId)
            }
            current.copy(notifyContactIds = currentList)
        }
    }

    fun saveDraftZone() {
        val currentDraft = _draftZone.value
        if (currentDraft.name.isNotBlank()) {
            geofenceManager.addSafeZone(currentDraft)
            _isCreatingNew.value = false
            _recentAlertMessage.value = "Safe zone '${currentDraft.name}' activated with schedule ${currentDraft.scheduleWindow.formattedTimeRange()}!"
        }
    }

    fun toggleZoneEnabled(zoneId: String, isEnabled: Boolean) {
        geofenceManager.toggleSafeZone(zoneId, isEnabled)
    }

    fun deleteZone(zoneId: String) {
        geofenceManager.removeSafeZone(zoneId)
    }

    fun dismissAlertMessage() {
        _recentAlertMessage.value = null
    }

    private fun createDefaultDraft(): SafeZone {
        return SafeZone(
            id = UUID.randomUUID().toString(),
            name = "Campus Safe Zone",
            latitude = 37.7749,
            longitude = -122.4194,
            radiusMeters = 250f,
            scheduleWindow = ScheduleWindow(
                activeDays = setOf(1, 2, 3, 4, 5),
                startHour = 16,
                startMinute = 0,
                endHour = 18,
                endMinute = 0
            ),
            isEnabled = true,
            notifyContactIds = emptyList()
        )
    }
}
