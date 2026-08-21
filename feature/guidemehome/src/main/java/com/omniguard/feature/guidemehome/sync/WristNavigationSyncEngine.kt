package com.omniguard.feature.guidemehome.sync

import com.omniguard.feature.guidemehome.model.HapticCueType
import com.omniguard.feature.guidemehome.model.ManeuverInstruction
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Data payload serialized across Wear OS DataLayer / Bluetooth.
 */
data class WristManeuverPayload(
    val streetName: String,
    val instruction: String,
    val distanceRemainingMeters: Double,
    val hapticCue: HapticCueType,
    val isArrival: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Interface representing the Wear OS communication channel.
 */
interface WearableDataSyncChannel {
    suspend fun sendManeuverToWearable(payload: WristManeuverPayload): Boolean
}

/**
 * Wrist Turn-by-Turn Syncing Engine.
 * Synchronizes real-time navigation updates and transmits distinctive haptic vibration cues to Wear OS.
 */
class WristNavigationSyncEngine(
    private val syncChannel: WearableDataSyncChannel
) {
    private val _syncedManeuvers = MutableSharedFlow<WristManeuverPayload>(extraBufferCapacity = 1)
    val syncedManeuvers: SharedFlow<WristManeuverPayload> = _syncedManeuvers.asSharedFlow()

    private var lastSentCue: HapticCueType = HapticCueType.NONE
    private var lastStepIndex: Int = -1

    /**
     * Syncs maneuver instruction and triggers haptic cues on wearable.
     */
    suspend fun syncManeuverToWrist(
        maneuver: ManeuverInstruction,
        distanceMeters: Double
    ) {
        val payload = WristManeuverPayload(
            streetName = maneuver.streetName,
            instruction = maneuver.instructionText,
            distanceRemainingMeters = distanceMeters,
            hapticCue = maneuver.hapticCue,
            isArrival = maneuver.maneuver == com.omniguard.feature.guidemehome.model.ManeuverType.ARRIVE_DESTINATION
        )

        // Only trigger prominent haptic if it's a new step or proximity cue (< 30m)
        if (maneuver.stepIndex != lastStepIndex || distanceMeters <= 30.0) {
            syncChannel.sendManeuverToWearable(payload)
            _syncedManeuvers.emit(payload)
            lastSentCue = maneuver.hapticCue
            lastStepIndex = maneuver.stepIndex
        }
    }
}
