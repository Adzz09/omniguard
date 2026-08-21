package com.omniguard.feature.guidemehome.consent

import com.omniguard.feature.guidemehome.model.LiveStreamingConsentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/**
 * Manages privacy consent flow for broadcasting live GPS journey data to trusted contacts.
 */
class EmergencyConsentManager(
    private val trustedContactsProvider: () -> List<String> = { listOf("Mom (+1-555-0192)", "Emergency Buddy (+1-555-0143)") }
) {
    private val _consentState = MutableStateFlow<LiveStreamingConsentState>(LiveStreamingConsentState.Idle)
    val consentState: StateFlow<LiveStreamingConsentState> = _consentState.asStateFlow()

    /**
     * Prompts the user with the consent dialog before starting journey streaming.
     */
    fun requestConsent() {
        val contacts = trustedContactsProvider()
        _consentState.value = LiveStreamingConsentState.PromptingConsent(
            promptMessage = "Do you want to send your live location and route to your trusted contacts?",
            targetContactsCount = contacts.size
        )
    }

    /**
     * User accepts sharing live location and route.
     */
    fun grantConsent() {
        val contacts = trustedContactsProvider()
        _consentState.value = LiveStreamingConsentState.Granted(
            timestamp = Instant.now(),
            sharedWithContacts = contacts
        )
    }

    /**
     * User explicitly declines sharing live location.
     */
    fun denyConsent(reason: String = "User declined") {
        _consentState.value = LiveStreamingConsentState.Denied(
            timestamp = Instant.now(),
            reason = reason
        )
    }

    /**
     * Resets consent state to idle.
     */
    fun reset() {
        _consentState.value = LiveStreamingConsentState.Idle
    }
}
