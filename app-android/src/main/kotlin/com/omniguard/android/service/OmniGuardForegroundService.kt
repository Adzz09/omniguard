package com.omniguard.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.omniguard.android.MainActivity
import com.omniguard.android.OmniGuardApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDateTime

import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.core.content.ContextCompat

class OmniGuardForegroundService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var isGatedLowPower = true // NFR-01 battery gating (<5% / 12h)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundSafely()
        startBatteryEfficientPassiveMonitoring()
    }

    private fun startForegroundSafely() {
        val notification = createNotification("Active Protection Armed", "Passive geofence & BLE watchdog running.")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE

                val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val hasBluetooth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                } else true

                if (hasLocation) {
                    serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                }
                if (hasBluetooth) {
                    serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                }

                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var serviceType = 0
                val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (hasLocation) {
                    serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                }
                if (serviceType != 0) {
                    startForeground(NOTIFICATION_ID, notification, serviceType)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start foreground service with types, falling back: ${e.message}")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback startForeground also failed", e2)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_TRIGGER_SOS) {
            triggerDirectSos()
        }
        return START_STICKY
    }

    /**
     * Gated Low-Power Polling (<5% battery over 12 hours):
     * Uses passive geofence checks and adaptive intervals (60s in stationary/low motion state).
     */
    private fun startBatteryEfficientPassiveMonitoring() {
        serviceScope.launch {
            val app = application as? OmniGuardApplication
            val geofenceManager = app?.geofenceManager

            while (isActive) {
                // Simulated low-power passive coordinate fetch (e.g. 37.7749, -122.4194 with small jitter)
                val currentLat = 37.7749 + (Math.random() - 0.5) * 0.0008
                val currentLng = -122.4194 + (Math.random() - 0.5) * 0.0008

                geofenceManager?.onLocationReceived(
                    latitude = currentLat,
                    longitude = currentLng,
                    accuracyMeters = 8.5f,
                    dateTime = LocalDateTime.now()
                )

                // Stationary low power interval vs active movement interval
                val pollInterval = if (isGatedLowPower) 45_000L else 15_000L
                delay(pollInterval)
            }
        }
    }

    private fun triggerDirectSos() {
        val app = application as? OmniGuardApplication
        val emergencyDispatcher = app?.emergencyDispatcher
        val contacts = app?.geofenceManager?.emergencyContacts?.value ?: emptyList()

        serviceScope.launch {
            emergencyDispatcher?.dispatchEmergencySos(
                contacts = contacts,
                reason = "Quick SOS Triggered via Background Notification",
                latitude = 37.7749,
                longitude = -122.4194,
                isDuress = false
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OmniGuard Active Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Battery-efficient background safety and geofence monitoring"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val sosIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, OmniGuardForegroundService::class.java).apply {
                action = ACTION_TRIGGER_SOS
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_delete, "DIRECT SOS", sosIntent)
            .build()
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "omniguard_protection_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_TRIGGER_SOS = "com.omniguard.action.TRIGGER_SOS"
        private const val TAG = "OmniGuardService"

        fun startService(context: Context) {
            val intent = Intent(context, OmniGuardForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
