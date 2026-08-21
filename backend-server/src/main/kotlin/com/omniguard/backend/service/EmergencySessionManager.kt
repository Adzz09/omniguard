package com.omniguard.backend.service

import com.omniguard.backend.model.BreadcrumbPoint
import com.omniguard.backend.model.CancelEmergencyRequest
import com.omniguard.backend.model.EmergencyStatus
import com.omniguard.backend.model.LiveTrackingMessage
import com.omniguard.backend.model.LocationPingRequest
import com.omniguard.backend.model.SOSRequestPayload
import com.omniguard.backend.model.SOSResponsePayload
import com.omniguard.backend.model.TrackingSessionState
import com.omniguard.core.model.SOSTriggerSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory manager for active emergency tracking sessions and real-time WebSocket distribution.
 */
class EmergencySessionManager(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val sessions = ConcurrentHashMap<String, TrackingSessionState>()
    private val sessionBroadcasts = ConcurrentHashMap<String, MutableSharedFlow<LiveTrackingMessage>>()

    /**
     * Creates a new emergency tracking session from an incoming SOS payload.
     */
    fun createSOSSession(
        payload: SOSRequestPayload,
        baseUrl: String = "http://localhost:8080"
    ): Pair<SOSResponsePayload, TrackingSessionState> {
        val sessionId = "EMG-${UUID.randomUUID().toString().take(8).uppercase()}"
        val trackingToken = UUID.randomUUID().toString()
        val trackingUrl = "$baseUrl/live/$sessionId"

        val initialStatus = when {
            payload.isDuress -> EmergencyStatus.SILENT_DURESS
            payload.triggerSource == SOSTriggerSource.FALL_TIMEOUT -> EmergencyStatus.FALL_ESCALATED
            else -> EmergencyStatus.ACTIVE_SOS
        }

        val initialPoint = BreadcrumbPoint(
            latitude = payload.latitude,
            longitude = payload.longitude,
            timestamp = System.currentTimeMillis(),
            accuracyMeters = payload.accuracyMeters
        )

        val sessionState = TrackingSessionState(
            sessionId = sessionId,
            userId = payload.userId,
            userRole = payload.userRole,
            status = initialStatus,
            triggerSource = payload.triggerSource,
            isDuress = payload.isDuress,
            initialTimestamp = System.currentTimeMillis(),
            lastUpdatedTimestamp = System.currentTimeMillis(),
            currentLatitude = payload.latitude,
            currentLongitude = payload.longitude,
            currentAccuracyMeters = payload.accuracyMeters,
            speedKmh = 0.0,
            batteryPercent = payload.batteryPercent,
            isCancelled = false,
            cancellationReason = null,
            breadcrumbs = listOf(initialPoint),
            activeViewerCount = 0
        )

        sessions[sessionId] = sessionState
        val flow = getOrCreateSessionFlow(sessionId)

        scope.launch {
            flow.emit(
                LiveTrackingMessage(
                    type = "INITIAL_STATE",
                    session = sessionState,
                    latestPing = initialPoint,
                    message = "Emergency tracking session initialized"
                )
            )
        }

        val response = SOSResponsePayload(
            sessionId = sessionId,
            trackingToken = trackingToken,
            trackingUrl = trackingUrl,
            status = initialStatus.name,
            timestamp = sessionState.initialTimestamp,
            message = "Emergency escalated successfully. Live tracking broadcast active at $trackingUrl"
        )

        return Pair(response, sessionState)
    }

    /**
     * Retrieves session snapshot by ID.
     */
    fun getSession(sessionId: String): TrackingSessionState? {
        return sessions[sessionId]
    }

    /**
     * Appends a new GPS location ping to the breadcrumb trail and broadcasts to WebSocket listeners.
     */
    fun updateLocation(
        sessionId: String,
        ping: LocationPingRequest
    ): TrackingSessionState? {
        val current = sessions[sessionId] ?: return null
        if (current.isCancelled) return current

        val newBreadcrumb = BreadcrumbPoint(
            latitude = ping.latitude,
            longitude = ping.longitude,
            timestamp = ping.timestamp,
            speedKmh = ping.speedKmh,
            altitudeMeters = ping.altitudeMeters,
            accuracyMeters = ping.accuracyMeters
        )

        val updated = current.copy(
            lastUpdatedTimestamp = ping.timestamp,
            currentLatitude = ping.latitude,
            currentLongitude = ping.longitude,
            currentAccuracyMeters = ping.accuracyMeters,
            speedKmh = ping.speedKmh,
            batteryPercent = ping.batteryPercent,
            breadcrumbs = (current.breadcrumbs + newBreadcrumb).takeLast(200) // Keep last 200 waypoints
        )

        sessions[sessionId] = updated
        val flow = getOrCreateSessionFlow(sessionId)

        scope.launch {
            flow.emit(
                LiveTrackingMessage(
                    type = "LOCATION_UPDATE",
                    session = updated,
                    latestPing = newBreadcrumb,
                    message = "Live position updated (${ping.latitude}, ${ping.longitude})"
                )
            )
        }

        return updated
    }

    /**
     * Cancels an active emergency session if within grace period or verified.
     */
    fun cancelSession(
        sessionId: String,
        request: CancelEmergencyRequest
    ): TrackingSessionState? {
        val current = sessions[sessionId] ?: return null

        val updated = current.copy(
            status = EmergencyStatus.CANCELLED,
            isCancelled = true,
            cancellationReason = "${request.cancellationSource}: ${request.reason}",
            lastUpdatedTimestamp = request.timestamp
        )

        sessions[sessionId] = updated
        val flow = getOrCreateSessionFlow(sessionId)

        scope.launch {
            flow.emit(
                LiveTrackingMessage(
                    type = "CANCELLED",
                    session = updated,
                    message = "Emergency alert was cancelled by user: ${request.reason}"
                )
            )
        }

        return updated
    }

    /**
     * Returns a reactive SharedFlow of messages for a session.
     */
    fun getSessionFlow(sessionId: String): SharedFlow<LiveTrackingMessage> {
        return getOrCreateSessionFlow(sessionId).asSharedFlow()
    }

    private fun getOrCreateSessionFlow(sessionId: String): MutableSharedFlow<LiveTrackingMessage> {
        return sessionBroadcasts.computeIfAbsent(sessionId) {
            MutableSharedFlow(replay = 1, extraBufferCapacity = 50)
        }
    }

    fun getAllSessions(): List<TrackingSessionState> = sessions.values.toList()

    fun clearAll() {
        sessions.clear()
        sessionBroadcasts.clear()
    }
}
