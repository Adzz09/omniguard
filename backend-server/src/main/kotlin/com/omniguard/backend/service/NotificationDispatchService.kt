package com.omniguard.backend.service

import com.omniguard.backend.model.GeofencePingRequest
import com.omniguard.backend.model.GeofencePingResponse
import com.omniguard.core.model.TransitEventType
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Record of simulated notification dispatch (Twilio SMS / FCM Push).
 */
data class DispatchedNotification(
    val id: String,
    val timestamp: Long,
    val recipient: String,
    val channel: NotificationChannel,
    val title: String,
    val body: String,
    val isDelivered: Boolean = true
)

enum class NotificationChannel {
    TWILIO_SMS,
    FCM_PUSH,
    VOIP_CALL
}

/**
 * Service simulating Twilio SMS and Firebase Cloud Messaging (FCM) dispatch
 * for passive geofence check-ins and high-priority SOS broadcasts.
 */
class NotificationDispatchService {
    private val logger = LoggerFactory.getLogger(NotificationDispatchService::class.java)
    private val dispatchHistory = ConcurrentLinkedQueue<DispatchedNotification>()

    /**
     * Dispatches geofence transition notifications to authorized emergency contacts.
     */
    fun dispatchGeofenceAlert(ping: GeofencePingRequest): GeofencePingResponse {
        val notificationId = "NOTIF-${UUID.randomUUID().toString().take(8).uppercase()}"
        val isExit = ping.eventType == TransitEventType.SAFE_ZONE_EXIT

        val eventTitle = if (isExit) "Safe Zone Departure Alert" else "Safe Zone Arrival Confirmation"
        val messageBody = if (isExit) {
            "OmniGuard Alert: User ${ping.userId} has departed safe corridor '${ping.zoneName}' at (${ping.latitude}, ${ping.longitude})."
        } else {
            "OmniGuard Notice: User ${ping.userId} has arrived safely inside '${ping.zoneName}'."
        }

        val targetContacts = if (ping.notifyContactIds.isNotEmpty()) ping.notifyContactIds else listOf("Contact-1", "Contact-2")

        var smsCount = 0
        var pushCount = 0

        for (contactId in targetContacts) {
            // Simulated Twilio SMS
            val sms = DispatchedNotification(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                recipient = "$contactId (SMS)",
                channel = NotificationChannel.TWILIO_SMS,
                title = eventTitle,
                body = messageBody
            )
            dispatchHistory.add(sms)
            smsCount++

            // Simulated FCM Push
            val push = DispatchedNotification(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                recipient = "$contactId (FCM)",
                channel = NotificationChannel.FCM_PUSH,
                title = eventTitle,
                body = messageBody
            )
            dispatchHistory.add(push)
            pushCount++

            logger.info("Simulated Twilio SMS to {}: {}", contactId, messageBody)
            logger.info("Simulated FCM Push to {}: {}", contactId, eventTitle)
        }

        return GeofencePingResponse(
            status = "DISPATCHED",
            notificationId = notificationId,
            zoneName = ping.zoneName,
            eventType = ping.eventType,
            dispatchedSmsCount = smsCount,
            dispatchedPushCount = pushCount,
            timestamp = System.currentTimeMillis(),
            message = "Dispatched $smsCount SMS and $pushCount FCM notifications for safe zone '${ping.zoneName}'."
        )
    }

    fun getHistory(): List<DispatchedNotification> = dispatchHistory.toList()

    fun clearHistory() {
        dispatchHistory.clear()
    }
}
