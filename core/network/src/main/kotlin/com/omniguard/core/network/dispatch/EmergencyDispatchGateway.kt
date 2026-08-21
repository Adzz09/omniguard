package com.omniguard.core.network.dispatch

import com.omniguard.core.model.EmergencyContact
import com.omniguard.core.model.SOSTriggerSource
import com.omniguard.core.network.model.DispatchAlertRequest
import com.omniguard.core.network.model.DispatchAlertResponse
import com.omniguard.core.network.model.SmsFallbackMessage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock

/**
 * Result outcome of an emergency dispatch attempt.
 */
sealed interface DispatchResult {
    data class Success(
        val sessionId: String,
        val liveTrackingUrl: String,
        val pushSuccess: Boolean,
        val smsFallbackUsed: Boolean,
        val notifiedRecipients: List<String>
    ) : DispatchResult

    data class Failure(
        val reason: String,
        val smsFallbackSent: Boolean,
        val cause: Throwable? = null
    ) : DispatchResult
}

/**
 * Interface representing native or platform-specific telephony SMS provider.
 */
interface SmsSender {
    suspend fun sendSms(recipientPhone: String, messageText: String): Result<Unit>
}

/**
 * Gateway interface for broadcasting urgent emergency dispatch alerts with automatic cellular SMS fallback.
 * Implements NFR-02 (< 10s latency) and FR-03 (SMS coordinates fallback).
 */
interface EmergencyDispatchGateway {
    suspend fun triggerEmergencyDispatch(
        sessionId: String,
        userId: String,
        userName: String,
        contacts: List<EmergencyContact>,
        triggerSource: SOSTriggerSource,
        isDuress: Boolean,
        latitude: Double,
        longitude: Double,
        customMessage: String? = null
    ): DispatchResult
}

/**
 * Default implementation of [EmergencyDispatchGateway] orchestrating cloud push dispatch with local SMS fallback.
 */
class DefaultEmergencyDispatchGateway(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://api.omniguard.app/v1",
    private val smsSender: SmsSender? = null,
    private val clock: Clock = Clock.System
) : EmergencyDispatchGateway {

    override suspend fun triggerEmergencyDispatch(
        sessionId: String,
        userId: String,
        userName: String,
        contacts: List<EmergencyContact>,
        triggerSource: SOSTriggerSource,
        isDuress: Boolean,
        latitude: Double,
        longitude: Double,
        customMessage: String?
    ): DispatchResult {
        val now = clock.now().toEpochMilliseconds()
        val mapsUrl = "https://maps.google.com/?q=$latitude,$longitude"
        val fallbackTrackingUrl = "https://omniguard.app/track/$sessionId"

        val request = DispatchAlertRequest(
            sessionId = sessionId,
            userId = userId,
            triggerSource = triggerSource,
            isDuress = isDuress,
            latitude = latitude,
            longitude = longitude,
            timestamp = now,
            contactPhones = contacts.map { it.phone },
            customMessage = customMessage
        )

        // Enforce NFR-02: 10s hard timeout before immediately switching to SMS fallback
        val httpResponse = withTimeoutOrNull(DISPATCH_TIMEOUT_MS) {
            runCatching {
                httpClient.post("$baseUrl/dispatch/alert") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
            }.getOrNull()
        }

        if (httpResponse != null && httpResponse.status.isSuccess()) {
            return try {
                val responseBody = httpResponse.body<DispatchAlertResponse>()
                DispatchResult.Success(
                    sessionId = responseBody.sessionId,
                    liveTrackingUrl = responseBody.liveTrackingUrl.ifBlank { fallbackTrackingUrl },
                    pushSuccess = responseBody.pushDispatched,
                    smsFallbackUsed = responseBody.smsFallbackTriggered,
                    notifiedRecipients = contacts.map { it.name }
                )
            } catch (e: Exception) {
                // If decoding fails, invoke SMS fallback to guarantee safety
                val smsOk = executeSmsFallback(userName, contacts, latitude, longitude, fallbackTrackingUrl, isDuress)
                DispatchResult.Success(
                    sessionId = sessionId,
                    liveTrackingUrl = fallbackTrackingUrl,
                    pushSuccess = false,
                    smsFallbackUsed = smsOk,
                    notifiedRecipients = contacts.map { it.name }
                )
            }
        } else {
            // Cloud server offline or timed out (>10s) -> FR-03 / NFR-02 local cellular SMS fallback
            val smsOk = executeSmsFallback(userName, contacts, latitude, longitude, fallbackTrackingUrl, isDuress)
            return DispatchResult.Failure(
                reason = "Cloud dispatch server unreachable or timed out. SMS fallback initiated.",
                smsFallbackSent = smsOk
            )
        }
    }

    private suspend fun executeSmsFallback(
        userName: String,
        contacts: List<EmergencyContact>,
        latitude: Double,
        longitude: Double,
        trackingUrl: String,
        isDuress: Boolean
    ): Boolean {
        if (smsSender == null || contacts.isEmpty()) return false

        val mapsUrl = "https://maps.google.com/?q=$latitude,$longitude"
        val messageText = buildString {
            if (isDuress) {
                append("EMERGENCY ALERT: $userName may be in danger and triggered an SOS.\n")
            } else {
                append("EMERGENCY ALERT: $userName needs immediate assistance!\n")
            }
            append("Location: $mapsUrl\n")
            append("Live Route: $trackingUrl\n")
            append("(Sent automatically via OmniGuard SOS)")
        }

        var anySuccess = false
        for (contact in contacts) {
            val result = smsSender.sendSms(contact.phone, messageText)
            if (result.isSuccess) {
                anySuccess = true
            }
        }
        return anySuccess
    }

    companion object {
        private const val DISPATCH_TIMEOUT_MS = 10_000L // 10 seconds max as per NFR-02
    }
}
