package com.omniguard.feature.geofencing.service

import android.util.Log
import com.omniguard.core.model.EmergencyContact
import com.omniguard.core.model.SafeZone
import com.omniguard.core.model.TransitEventType
import com.omniguard.core.model.TransitLog
import com.omniguard.feature.geofencing.crypto.LogEncryptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GeofenceStatus(
    val safeZoneId: String,
    val isInside: Boolean,
    val lastEvaluatedMillis: Long = System.currentTimeMillis()
)

class ScheduleGeofenceManager(
    private val hapticNotifier: WatchHapticNotifier,
    private val emergencyDispatcher: EmergencyDispatcher,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _safeZones = MutableStateFlow<List<SafeZone>>(emptyList())
    val safeZones: StateFlow<List<SafeZone>> = _safeZones.asStateFlow()

    private val _emergencyContacts = MutableStateFlow<List<EmergencyContact>>(emptyList())
    val emergencyContacts: StateFlow<List<EmergencyContact>> = _emergencyContacts.asStateFlow()

    private val _transitLogs = MutableStateFlow<List<TransitLog>>(emptyList())
    val transitLogs: StateFlow<List<TransitLog>> = _transitLogs.asStateFlow()

    // Tracks current presence for each zone: zoneId -> isInside
    private val zonePresenceMap = mutableMapOf<String, Boolean>()

    fun setSafeZones(zones: List<SafeZone>) {
        _safeZones.value = zones
    }

    fun setEmergencyContacts(contacts: List<EmergencyContact>) {
        _emergencyContacts.value = contacts
    }

    fun addSafeZone(zone: SafeZone) {
        _safeZones.update { it + zone }
    }

    fun removeSafeZone(zoneId: String) {
        _safeZones.update { it.filterNot { zone -> zone.id == zoneId } }
        zonePresenceMap.remove(zoneId)
    }

    fun toggleSafeZone(zoneId: String, isEnabled: Boolean) {
        _safeZones.update { list ->
            list.map { if (it.id == zoneId) it.copy(isEnabled = isEnabled) else it }
        }
    }

    /**
     * Called whenever a passive location update is received (e.g. from fused location provider / foreground service).
     * Evaluates schedule window, calculates distances, detects boundary transitions, logs encrypted entry/exit,
     * triggers silent watch haptic ping, and dispatches SMS/Push notifications to contacts.
     */
    fun onLocationReceived(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float = 10f,
        dateTime: LocalDateTime = LocalDateTime.now()
    ) {
        val currentDay = dateTime.dayOfWeek
        val currentTime = dateTime.toLocalTime()

        _safeZones.value.filter { it.isEnabled }.forEach { zone ->
            val isScheduleActive = zone.scheduleWindow.isWithinWindow(currentDay, currentTime)
            if (!isScheduleActive) {
                // Not in active schedule window (e.g., outside Mon-Fri 4PM - 6PM)
                Log.d(TAG, "Zone '${zone.name}' skipped: outside active schedule window (${zone.scheduleWindow.formattedTimeRange()})")
                return@forEach
            }

            val distanceMeters = calculateDistanceMeters(latitude, longitude, zone.latitude, zone.longitude)
            val isInsideNow = distanceMeters <= zone.radiusMeters
            val wasInside = zonePresenceMap[zone.id]

            if (wasInside == null) {
                // Initialize presence baseline without firing boundary cross
                zonePresenceMap[zone.id] = isInsideNow
                Log.d(TAG, "Baseline established for zone '${zone.name}': inside=$isInsideNow (dist=${distanceMeters.toInt()}m)")
                return@forEach
            }

            if (wasInside != isInsideNow) {
                // Boundary crossed during active schedule window!
                zonePresenceMap[zone.id] = isInsideNow
                handleBoundaryCross(
                    zone = zone,
                    isEntry = isInsideNow,
                    latitude = latitude,
                    longitude = longitude,
                    accuracyMeters = accuracyMeters,
                    dateTime = dateTime
                )
            }
        }
    }

    private fun handleBoundaryCross(
        zone: SafeZone,
        isEntry: Boolean,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        dateTime: LocalDateTime
    ) {
        val eventType = if (isEntry) TransitEventType.SAFE_ZONE_ENTER else TransitEventType.SAFE_ZONE_EXIT
        Log.i(TAG, "Boundary Cross Detected: ${zone.name} -> $eventType at $latitude, $longitude")

        // 1. Log encrypted Entry / Exit event
        val rawPayload = "Zone=${zone.name}|Event=${eventType.name}|Lat=$latitude|Lng=$longitude|Time=${dateTime}|Radius=${zone.radiusMeters}m"
        val encryptionResult = LogEncryptor.encrypt(rawPayload)

        val logEntry = TransitLog(
            id = UUID.randomUUID().toString(),
            timestampMillis = System.currentTimeMillis(),
            eventType = eventType,
            locationName = zone.name,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            encryptedPayload = encryptionResult.cipherTextBase64,
            iv = encryptionResult.ivBase64
        )

        _transitLogs.update { currentList ->
            // Auto-purge items older than 7 days
            val now = System.currentTimeMillis()
            val filtered = currentList.filterNot { it.isExpired(now) }
            listOf(logEntry) + filtered
        }

        // 2. Trigger Silent Watch Haptic Ping via BLE
        val hapticPattern = if (isEntry) HapticPattern.SILENT_ENTRY_PING else HapticPattern.SILENT_EXIT_PING
        hapticNotifier.triggerWatchHaptic(
            pattern = hapticPattern,
            reason = "Scheduled Safe Zone ${if (isEntry) "Entry" else "Exit"} for '${zone.name}'"
        )

        // 3. Dispatch external SMS/Push to trusted contacts
        scope.launch {
            val targetedContacts = if (zone.notifyContactIds.isEmpty()) {
                _emergencyContacts.value
            } else {
                _emergencyContacts.value.filter { zone.notifyContactIds.contains(it.id) }
            }

            emergencyDispatcher.dispatchGeofenceAlert(
                contacts = targetedContacts,
                safeZoneName = zone.name,
                isEntry = isEntry,
                latitude = latitude,
                longitude = longitude
            )
        }
    }

    /**
     * Haversine formula for battery-efficient distance computation in meters
     */
    private fun calculateDistanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    fun purgeExpiredLogs() {
        val now = System.currentTimeMillis()
        _transitLogs.update { list -> list.filterNot { it.isExpired(now) } }
    }

    fun clearAllLogs() {
        _transitLogs.value = emptyList()
    }

    companion object {
        private const val TAG = "ScheduleGeofenceMgr"
    }
}
