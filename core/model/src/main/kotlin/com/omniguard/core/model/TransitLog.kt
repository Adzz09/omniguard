package com.omniguard.core.model

import kotlinx.serialization.Serializable

@Serializable
data class TransitLog(
    val id: String,
    val timestampMillis: Long,
    val eventType: TransitEventType,
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val encryptedPayload: String, // AES-256-GCM encrypted payload
    val iv: String,               // Initialization vector
    val isAutoPurged: Boolean = false
) {
    fun isExpired(currentMillis: Long, maxRetentionDays: Int = 7): Boolean {
        val maxAgeMillis = maxRetentionDays.toLong() * 24 * 60 * 60 * 1000
        return (currentMillis - timestampMillis) > maxAgeMillis
    }
}

@Serializable
enum class TransitEventType(val displayName: String, val isAlert: Boolean) {
    SAFE_ZONE_ENTER("Entered Safe Zone", false),
    SAFE_ZONE_EXIT("Exited Safe Zone", true),
    ROUTE_DEVIATION("Corridor Deviation", true),
    CHECKPOINT_REACHED("Checkpoint Cleared", false),
    DURESS_TRIGGERED("Duress Beacon Emitted", true),
    SOS_ACTIVATED("Emergency SOS Triggered", true),
    FALL_DETECTED("Fall Event Logged", true)
}

@Serializable
data class WatchState(
    val isConnected: Boolean = true,
    val deviceName: String = "OmniBand Ultra v2",
    val macAddress: String = "7C:9E:BD:44:A2:18",
    val batteryPercent: Int = 88,
    val rssiDb: Int = -56,
    val isHapticActive: Boolean = false,
    val lastHeartbeatMillis: Long = System.currentTimeMillis()
)
