package com.omniguard.android.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniguard.core.model.SafeZone
import com.omniguard.core.model.WatchState
import com.omniguard.feature.geofencing.service.EmergencyDispatcher
import com.omniguard.feature.geofencing.service.ScheduleGeofenceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@Immutable
data class HomeUiState(
    val activeGeofences: List<SafeZone> = emptyList(),
    val activeSchedulesCount: Int = 0,
    val watchState: WatchState = WatchState(),
    val isSosActive: Boolean = false,
    val sosCountdownSeconds: Int = 5,
    val isSosDispatched: Boolean = false,
    val lastBroadcastMessage: String? = null
)

class HomeViewModel(
    private val geofenceManager: ScheduleGeofenceManager,
    private val emergencyDispatcher: EmergencyDispatcher
) : ViewModel() {

    private val _watchState = MutableStateFlow(
        WatchState(
            isConnected = true,
            deviceName = "OmniBand Ultra v2",
            macAddress = "7C:9E:BD:44:A2:18",
            batteryPercent = 91,
            rssiDb = -54
        )
    )

    private val _isSosActive = MutableStateFlow(false)
    private val _sosCountdownSeconds = MutableStateFlow(5)
    private val _isSosDispatched = MutableStateFlow(false)
    private val _lastBroadcastMessage = MutableStateFlow<String?>(null)
    private var sosCountdownJob: Job? = null

    private data class SosState(
        val isActive: Boolean = false,
        val countdownSeconds: Int = 5,
        val isDispatched: Boolean = false,
        val message: String? = null
    )

    private val _sosState = combine(
        _isSosActive,
        _sosCountdownSeconds,
        _isSosDispatched,
        _lastBroadcastMessage
    ) { active, countdown, dispatched, msg ->
        SosState(active, countdown, dispatched, msg)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        geofenceManager.safeZones,
        _watchState,
        _sosState
    ) { safeZones, watch, sos ->
        val now = LocalDateTime.now()
        val currentDay = now.dayOfWeek
        val currentTime = now.toLocalTime()

        val activeCount = safeZones.count { it.isEnabled && it.scheduleWindow.isWithinWindow(currentDay, currentTime) }

        HomeUiState(
            activeGeofences = safeZones.filter { it.isEnabled },
            activeSchedulesCount = activeCount,
            watchState = watch,
            isSosActive = sos.isActive,
            sosCountdownSeconds = sos.countdownSeconds,
            isSosDispatched = sos.isDispatched,
            lastBroadcastMessage = sos.message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun startSosCountdown() {
        sosCountdownJob?.cancel()
        _isSosActive.value = true
        _isSosDispatched.value = false
        _sosCountdownSeconds.value = 5

        sosCountdownJob = viewModelScope.launch {
            for (i in 5 downTo 1) {
                _sosCountdownSeconds.value = i
                delay(1000)
            }
            dispatchEmergencySos()
        }
    }

    fun cancelSos() {
        sosCountdownJob?.cancel()
        _isSosActive.value = false
        _isSosDispatched.value = false
        _lastBroadcastMessage.value = "SOS cancelled by user."
    }

    fun dispatchEmergencySos() {
        sosCountdownJob?.cancel()
        _isSosActive.value = false
        _isSosDispatched.value = true
        _lastBroadcastMessage.value = "EMERGENCY BROADCAST SENT: Live GPS sent to all guardians & emergency dispatch."

        viewModelScope.launch {
            val contacts = geofenceManager.emergencyContacts.value
            emergencyDispatcher.dispatchEmergencySos(
                contacts = contacts,
                reason = "User Activated Emergency SOS Button",
                latitude = 37.7749,
                longitude = -122.4194,
                isDuress = false
            )
        }
    }

    fun dismissBroadcastBanner() {
        _lastBroadcastMessage.value = null
    }
}
