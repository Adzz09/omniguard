package com.omniguard.backend.model

import com.omniguard.core.model.CancellationSource
import com.omniguard.core.model.SOSTriggerSource
import com.omniguard.core.model.TransitEventType
import com.omniguard.core.model.UserRole
import kotlinx.serialization.Serializable

/**
 * Request payload sent by Android/Wear client when initiating an SOS emergency.
 */
@Serializable
data class SOSRequestPayload(
    val userId: String,
    val userRole: UserRole = UserRole.STUDENT,
    val triggerSource: SOSTriggerSource = SOSTriggerSource.MANUAL_APP,
    val isDuress: Boolean = false,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float = 5.0f,
    val peakGForce: Double? = null,
    val batteryPercent: Int = 100,
    val contactIds: List<String> = emptyList(),
    val notes: String? = null
)

/**
 * Response returned to client upon SOS registration.
 */
@Serializable
data class SOSResponsePayload(
    val sessionId: String,
    val trackingToken: String,
    val trackingUrl: String,
    val status: String = "ACTIVE",
    val timestamp: Long = System.currentTimeMillis(),
    val message: String = "Emergency incident escalated. Live GPS tracking broadcast initiated."
)

/**
 * Request to cancel an active emergency incident.
 */
@Serializable
data class CancelEmergencyRequest(
    val sessionId: String,
    val cancellationSource: CancellationSource = CancellationSource.SCREEN,
    val reason: String = "User entered verification PIN to abort countdown",
    val pin: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Response for emergency cancellation.
 */
@Serializable
data class CancelEmergencyResponse(
    val sessionId: String,
    val status: String = "CANCELLED",
    val cancelledAt: Long = System.currentTimeMillis(),
    val message: String = "Emergency alert was cancelled successfully."
)

/**
 * Breadcrumb waypoint for tracking user journey.
 */
@Serializable
data class BreadcrumbPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val speedKmh: Double = 0.0,
    val altitudeMeters: Double = 0.0,
    val accuracyMeters: Float = 5.0f
)

/**
 * Real-time location telemetry packet streamed via WebSocket or POST /ping.
 */
@Serializable
data class LocationPingRequest(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float = 5.0f,
    val speedKmh: Double = 0.0,
    val altitudeMeters: Double = 0.0,
    val headingDegrees: Float = 0.0f,
    val batteryPercent: Int = 100,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Complete server-side tracking session state.
 */
@Serializable
data class TrackingSessionState(
    val sessionId: String,
    val userId: String,
    val userRole: UserRole,
    val status: EmergencyStatus,
    val triggerSource: SOSTriggerSource,
    val isDuress: Boolean,
    val initialTimestamp: Long,
    val lastUpdatedTimestamp: Long,
    val currentLatitude: Double,
    val currentLongitude: Double,
    val currentAccuracyMeters: Float,
    val speedKmh: Double,
    val batteryPercent: Int,
    val isCancelled: Boolean = false,
    val cancellationReason: String? = null,
    val breadcrumbs: List<BreadcrumbPoint> = emptyList(),
    val activeViewerCount: Int = 0
)

@Serializable
enum class EmergencyStatus {
    ACTIVE_SOS,
    FALL_ESCALATED,
    SILENT_DURESS,
    LIVE_ESCORT,
    CANCELLED,
    RESOLVED
}

/**
 * WebSocket streaming message payload.
 */
@Serializable
data class LiveTrackingMessage(
    val type: String, // "INITIAL_STATE", "LOCATION_UPDATE", "STATUS_CHANGE", "CANCELLED"
    val session: TrackingSessionState,
    val latestPing: BreadcrumbPoint? = null,
    val message: String? = null
)

/**
 * Geofence arrival / departure ping payload.
 */
@Serializable
data class GeofencePingRequest(
    val zoneId: String,
    val zoneName: String,
    val userId: String,
    val userRole: UserRole = UserRole.STUDENT,
    val eventType: TransitEventType,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val notifyContactIds: List<String> = emptyList()
)

/**
 * Geofence dispatch response.
 */
@Serializable
data class GeofencePingResponse(
    val status: String = "DISPATCHED",
    val notificationId: String,
    val zoneName: String,
    val eventType: TransitEventType,
    val dispatchedSmsCount: Int,
    val dispatchedPushCount: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String
)
