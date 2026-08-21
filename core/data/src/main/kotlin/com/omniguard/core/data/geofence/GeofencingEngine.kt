package com.omniguard.core.data.geofence

import com.omniguard.core.data.repository.SafeZoneRepository
import com.omniguard.core.data.repository.TransitLogRepository
import com.omniguard.core.model.SafeZone
import com.omniguard.core.model.TransitEventType
import com.omniguard.core.model.TransitLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Event produced when a user crosses an active safe zone boundary.
 */
data class GeofenceTransitionEvent(
    val safeZone: SafeZone,
    val eventType: TransitEventType,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)

/**
 * Engine contract for real-time GPS location ingestion, active schedule window evaluation,
 * and entry/exit transition event dispatch.
 */
interface GeofencingEngine {
    val transitionsFlow: Flow<GeofenceTransitionEvent>

    suspend fun processLocationUpdate(latitude: Double, longitude: Double, timestamp: Long = 0L): List<GeofenceTransitionEvent>
    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double
    fun isInsideSafeZone(latitude: Double, longitude: Double, safeZone: SafeZone): Boolean
}

/**
 * Default implementation of [GeofencingEngine] managing geofence state transitions and transit log persistence.
 */
class DefaultGeofencingEngine(
    private val safeZoneRepository: SafeZoneRepository,
    private val transitLogRepository: TransitLogRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault()
) : GeofencingEngine {

    private val mutex = Mutex()
    private val activeZoneState = mutableMapOf<String, Boolean>() // safeZoneId -> isCurrentlyInside
    private val _transitionsFlow = MutableSharedFlow<GeofenceTransitionEvent>(extraBufferCapacity = 64)
    override val transitionsFlow: Flow<GeofenceTransitionEvent> = _transitionsFlow.asSharedFlow()

    override suspend fun processLocationUpdate(
        latitude: Double,
        longitude: Double,
        timestamp: Long
    ): List<GeofenceTransitionEvent> = mutex.withLock {
        val nowInstant = if (timestamp > 0L) Instant.fromEpochMilliseconds(timestamp) else clock.now()
        val eventTime = nowInstant.toEpochMilliseconds()
        val localDateTime = nowInstant.toLocalDateTime(timeZone)

        // Day of week: 1=Monday .. 7=Sunday
        val dayOfWeek = localDateTime.dayOfWeek.ordinal + 1
        val timeString = buildString {
            append(localDateTime.hour.toString().padStart(2, '0'))
            append(':')
            append(localDateTime.minute.toString().padStart(2, '0'))
        }

        val activeZones = safeZoneRepository.getActiveSafeZones(dayOfWeek, timeString)
        val generatedEvents = mutableListOf<GeofenceTransitionEvent>()

        for (zone in activeZones) {
            val isInsideNow = isInsideSafeZone(latitude, longitude, zone)
            val wasInside = activeZoneState[zone.id]

            if (wasInside == null) {
                // Initialize baseline state
                activeZoneState[zone.id] = isInsideNow
            } else if (!wasInside && isInsideNow) {
                // Transition: ENTRY
                activeZoneState[zone.id] = true
                val event = GeofenceTransitionEvent(
                    safeZone = zone,
                    eventType = TransitEventType.SAFE_ZONE_ENTER,
                    latitude = latitude,
                    longitude = longitude,
                    timestamp = eventTime
                )
                generatedEvents.add(event)
                persistTransitLog(event)
            } else if (wasInside && !isInsideNow) {
                // Transition: EXIT
                activeZoneState[zone.id] = false
                val event = GeofenceTransitionEvent(
                    safeZone = zone,
                    eventType = TransitEventType.SAFE_ZONE_EXIT,
                    latitude = latitude,
                    longitude = longitude,
                    timestamp = eventTime
                )
                generatedEvents.add(event)
                persistTransitLog(event)
            }
        }

        generatedEvents.forEach { _transitionsFlow.tryEmit(it) }
        generatedEvents
    }

    override fun isInsideSafeZone(latitude: Double, longitude: Double, safeZone: SafeZone): Boolean {
        val distance = calculateDistanceMeters(latitude, longitude, safeZone.latitude, safeZone.longitude)
        return distance <= safeZone.radiusMeters
    }

    /**
     * Computes great-circle distance between two GPS coordinates using the Haversine formula.
     */
    override fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private suspend fun persistTransitLog(event: GeofenceTransitionEvent) {
        val log = TransitLog(
            id = UUID.randomUUID().toString(),
            timestampMillis = event.timestamp,
            eventType = event.eventType,
            locationName = event.safeZone.name,
            latitude = event.latitude,
            longitude = event.longitude,
            accuracyMeters = 5.0f,
            encryptedPayload = "CIPHERTEXT_${event.safeZone.name}",
            iv = "RANDOM_IV"
        )
        transitLogRepository.insertLog(log)
    }
}
