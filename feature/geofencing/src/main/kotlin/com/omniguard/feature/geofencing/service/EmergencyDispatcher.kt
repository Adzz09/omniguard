package com.omniguard.feature.geofencing.service

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import com.omniguard.core.model.EmergencyContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

data class DispatchedAlert(
    val contactName: String,
    val phoneNumber: String,
    val message: String,
    val channel: AlertChannel,
    val isSuccess: Boolean,
    val timestampMillis: Long = System.currentTimeMillis()
)

enum class AlertChannel {
    SMS, PUSH, WEBSOCKET, CLOUD_RELAY
}

class EmergencyDispatcher(private val context: Context? = null) {

    private val _dispatchedAlerts = MutableSharedFlow<DispatchedAlert>(extraBufferCapacity = 50)
    val dispatchedAlerts: SharedFlow<DispatchedAlert> = _dispatchedAlerts.asSharedFlow()

    suspend fun dispatchGeofenceAlert(
        contacts: List<EmergencyContact>,
        safeZoneName: String,
        isEntry: Boolean,
        latitude: Double,
        longitude: Double
    ): List<DispatchedAlert> = withContext(Dispatchers.IO) {
        val eventType = if (isEntry) "ENTERED" else "LEFT"
        val mapsLink = "https://maps.google.com/?q=$latitude,$longitude"
        val message = "[OmniGuard Safety Alert] User has $eventType scheduled safe zone '$safeZoneName'. Live Location: $mapsLink"

        val results = mutableListOf<DispatchedAlert>()

        contacts.filter { it.notifyOnGeofence }.forEach { contact ->
            val smsSuccess = sendSms(contact.phoneNumber, message)
            val pushSuccess = sendPushNotification(contact.id, message)

            val alertSms = DispatchedAlert(
                contactName = contact.name,
                phoneNumber = contact.phoneNumber,
                message = message,
                channel = AlertChannel.SMS,
                isSuccess = smsSuccess
            )
            val alertPush = DispatchedAlert(
                contactName = contact.name,
                phoneNumber = contact.phoneNumber,
                message = message,
                channel = AlertChannel.PUSH,
                isSuccess = pushSuccess
            )

            results.add(alertSms)
            results.add(alertPush)
            _dispatchedAlerts.tryEmit(alertSms)
            _dispatchedAlerts.tryEmit(alertPush)
        }

        results
    }

    suspend fun dispatchEmergencySos(
        contacts: List<EmergencyContact>,
        reason: String,
        latitude: Double,
        longitude: Double,
        isDuress: Boolean
    ): List<DispatchedAlert> = withContext(Dispatchers.IO) {
        val prefix = if (isDuress) "[OmniGuard SILENT DURESS]" else "[OmniGuard EMERGENCY SOS]"
        val mapsLink = "https://maps.google.com/?q=$latitude,$longitude"
        val message = "$prefix Reason: $reason. Live Coordinates: $mapsLink. Immediate assistance requested!"

        val results = mutableListOf<DispatchedAlert>()

        contacts.forEach { contact ->
            val smsSuccess = sendSms(contact.phoneNumber, message)
            val pushSuccess = sendPushNotification(contact.id, message)

            val alert = DispatchedAlert(
                contactName = contact.name,
                phoneNumber = contact.phoneNumber,
                message = message,
                channel = if (smsSuccess) AlertChannel.SMS else AlertChannel.PUSH,
                isSuccess = smsSuccess || pushSuccess
            )
            results.add(alert)
            _dispatchedAlerts.tryEmit(alert)
        }

        results
    }

    private fun sendSms(phoneNumber: String, message: String): Boolean {
        return try {
            if (context != null && phoneNumber.isNotBlank()) {
                val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                smsManager?.sendTextMessage(phoneNumber, null, message, null, null)
                Log.d(TAG, "Sent SMS to $phoneNumber: $message")
                true
            } else {
                Log.d(TAG, "Simulated SMS to $phoneNumber: $message")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $phoneNumber: ${e.message}")
            false
        }
    }

    private fun sendPushNotification(contactId: String, message: String): Boolean {
        // High-priority FCM / Cloud Relay dispatch simulation
        Log.d(TAG, "Dispatched Push Alert to ContactId $contactId: $message")
        return true
    }

    companion object {
        private const val TAG = "EmergencyDispatcher"
    }
}
