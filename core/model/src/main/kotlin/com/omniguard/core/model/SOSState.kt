package com.omniguard.core.model

import kotlinx.serialization.Serializable

/**
 * Origin trigger for high-priority emergency SOS activation.
 */
@Serializable
enum class SOSTriggerSource {
    /** Watch hardware emergency trigger: rapid triple-press on physical button */
    TRIPLE_PRESS_WATCH,

    /** Covert mobile duress trigger: false PIN entered on unlock keypad or hidden gesture */
    MOBILE_DURESS,

    /** High-G crash detection timeout or manual in-app SOS button */
    MANUAL_APP,

    /** Autonomous escalation from unanswered fall countdown */
    FALL_TIMEOUT
}

/**
 * Active SOS state snapshot and escalation metadata.
 *
 * @property isActive Whether an emergency sequence is currently live.
 * @property isDuress Whether the trigger is in silent duress mode (covert dispatch without alarms).
 * @property triggerSource The input mechanism that initiated the SOS broadcast.
 * @property timestamp Epoch timestamp in milliseconds when SOS was fired.
 * @property liveTrackingUrl Dynamic shareable URL for authorized emergency contacts to view real-time location.
 * @property sessionId Unique token identifying this active emergency dispatch session.
 */
@Serializable
data class SOSState(
    val isActive: Boolean = false,
    val isDuress: Boolean = false,
    val triggerSource: SOSTriggerSource = SOSTriggerSource.MANUAL_APP,
    val timestamp: Long = 0L,
    val liveTrackingUrl: String = "",
    val sessionId: String = ""
)
