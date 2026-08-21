package com.omniguard.core.network.model

import com.omniguard.core.model.SOSTriggerSource
import kotlinx.serialization.Serializable

/**
 * REST payload sent to backend dispatch cloud server during active SOS/fall alerts.
 */
@Serializable
data class DispatchAlertRequest(
    val sessionId: String,
    val userId: String,
    val triggerSource: SOSTriggerSource,
    val isDuress: Boolean,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val speedMps: Double? = null,
    val batteryPercent: Int? = null,
    val timestamp: Long,
    val contactPhones: List<String>,
    val customMessage: String? = null
)

/**
 * Response received from backend dispatch cluster confirming alert propagation.
 */
@Serializable
data class DispatchAlertResponse(
    val success: Boolean,
    val sessionId: String,
    val liveTrackingUrl: String,
    val notifiedCount: Int,
    val pushDispatched: Boolean,
    val smsFallbackTriggered: Boolean,
    val message: String? = null
)

/**
 * Formatted cellular SMS fallback payload conforming to NFR-02 / FR-03 specifications.
 */
@Serializable
data class SmsFallbackMessage(
    val recipientPhone: String,
    val senderName: String,
    val body: String,
    val googleMapsUrl: String,
    val liveTrackingUrl: String,
    val timestamp: Long
)

/**
 * Real-time WebSocket frame streamed over live tracking connection.
 */
@Serializable
data class LiveTelemetryFrame(
    val sessionId: String,
    val userId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMps: Double,
    val headingDegrees: Float,
    val batteryLevel: Int,
    val isDuress: Boolean,
    val timestamp: Long
)
