package com.omniguard.core.model

import kotlinx.serialization.Serializable

/**
 * Origin source or trigger mechanism used to dismiss or resolve a fall alert.
 */
@Serializable
enum class CancellationSource {
    /** Dismissed via physical hardware button (e.g. watch crown or phone volume button) */
    HARDWARE_BUTTON,

    /** Dismissed via touchscreen swipe or PIN on device screen */
    SCREEN,

    /** Alert was not cancelled in time; countdown timed out and escalated to dispatch */
    TIMEOUT
}

/**
 * Fall / crash incident event record captured by sensor telemetry.
 *
 * @property id Unique incident identifier.
 * @property timestamp Epoch timestamp in milliseconds when high-G impact occurred.
 * @property peakGForce Maximum recorded acceleration magnitude in units of standard gravity (g).
 * @property lat Latitude coordinate where incident was detected.
 * @property lng Longitude coordinate where incident was detected.
 * @property isCancelled Whether the user aborted the emergency dispatch during the 30s countdown.
 * @property cancellationSource Method of cancellation, or [CancellationSource.TIMEOUT] if escalated.
 * @property audioClipPath Local URI to the recorded 10-second ambient audio snippet for situational context.
 * @property confidenceScore ML model / heuristic detection confidence (0.0 to 1.0).
 */
@Serializable
data class FallIncident(
    val id: String,
    val timestamp: Long,
    val peakGForce: Double,
    val lat: Double,
    val lng: Double,
    val isCancelled: Boolean = false,
    val cancellationSource: CancellationSource? = null,
    val audioClipPath: String? = null,
    val confidenceScore: Double = 1.0
)
