package com.omniguard.feature.guidemehome.tracking

import com.omniguard.core.model.SafeZone
import com.omniguard.core.model.TransitEventType
import com.omniguard.core.model.TransitLog
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geofence transition event.
 */
data class GeofenceTransition(
    val safeZone: SafeZone,
    val eventType: TransitEventType,
    val timestampMillis: Long = System.currentTimeMillis(),
    val userLat: Double,
    val userLng: Double,
    val distanceToCenterMeters: Double,
    val isScheduleActive: Boolean
)

/**
 * Schedule-aware safe zone manager and geofence transition monitor.
 * Evaluates real-time GPS locations against scheduled safe zones (e.g. Student campus, elderly home, biker safe harbor),
 * respecting scheduled days of week and time windows (including over-midnight shifts).
 */
class ScheduleGeofenceManager(
    private val safeZones: MutableList<SafeZone> = mutableListOf()
) {
    private val _activeInsideZones = MutableStateFlow<Set<String>>(emptySet())
    val activeInsideZones: StateFlow<Set<String>> = _activeInsideZones.asStateFlow()

    private val _transitions = MutableSharedFlow<GeofenceTransition>(extraBufferCapacity = 20)
    val transitions: SharedFlow<GeofenceTransition> = _transitions.asSharedFlow()

    fun addSafeZone(zone: SafeZone) {
        safeZones.removeAll { it.id == zone.id }
        safeZones.add(zone)
    }

    fun removeSafeZone(zoneId: String) {
        safeZones.removeAll { it.id == zoneId }
    }

    fun getSafeZones(): List<SafeZone> = safeZones.toList()

    /**
     * Evaluates a location update against all registered safe zones.
     * Triggers enter/exit transitions only when the schedule window is active.
     */
    suspend fun processLocationUpdate(
        lat: Double,
        lng: Double,
        dayOfWeek: DayOfWeek,
        time: LocalTime,
        timestampMillis: Long = System.currentTimeMillis()
    ): List<GeofenceTransition> {
        val triggeredTransitions = mutableListOf<GeofenceTransition>()
        val currentInside = _activeInsideZones.value.toMutableSet()

        for (zone in safeZones) {
            if (!zone.isEnabled) continue

            val isScheduleActive = zone.scheduleWindow.isWithinWindow(dayOfWeek, time)
            val distance = calculateDistanceMeters(lat, lng, zone.latitude, zone.longitude)
            val isInsideRadius = distance <= zone.radiusMeters

            val wasInside = currentInside.contains(zone.id)

            if (isScheduleActive) {
                if (isInsideRadius && !wasInside) {
                    // Entered Safe Zone
                    currentInside.add(zone.id)
                    val event = GeofenceTransition(
                        safeZone = zone,
                        eventType = TransitEventType.SAFE_ZONE_ENTER,
                        timestampMillis = timestampMillis,
                        userLat = lat,
                        userLng = lng,
                        distanceToCenterMeters = distance,
                        isScheduleActive = true
                    )
                    triggeredTransitions.add(event)
                    _transitions.emit(event)
                } else if (!isInsideRadius && wasInside) {
                    // Exited Safe Zone
                    currentInside.remove(zone.id)
                    val event = GeofenceTransition(
                        safeZone = zone,
                        eventType = TransitEventType.SAFE_ZONE_EXIT,
                        timestampMillis = timestampMillis,
                        userLat = lat,
                        userLng = lng,
                        distanceToCenterMeters = distance,
                        isScheduleActive = true
                    )
                    triggeredTransitions.add(event)
                    _transitions.emit(event)
                }
            } else {
                // If schedule becomes inactive while inside, quietly clear state without false alert
                if (wasInside) {
                    currentInside.remove(zone.id)
                }
            }
        }

        _activeInsideZones.value = currentInside
        return triggeredTransitions
    }

    companion object {
        /**
         * Calculates distance between two coordinates in meters using the Haversine formula.
         */
        fun calculateDistanceMeters(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double
        ): Double {
            val r = 6371000.0 // Earth radius in meters
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return r * c
        }
    }
}
