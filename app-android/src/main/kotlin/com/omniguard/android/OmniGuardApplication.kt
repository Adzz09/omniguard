package com.omniguard.android

import android.app.Application
import com.omniguard.core.model.ContactRelationship
import com.omniguard.core.model.EmergencyContact
import com.omniguard.core.model.SafeZone
import com.omniguard.core.model.ScheduleWindow
import com.omniguard.feature.geofencing.service.EmergencyDispatcher
import com.omniguard.feature.geofencing.service.ScheduleGeofenceManager
import com.omniguard.feature.geofencing.service.WatchHapticNotifier
import java.util.UUID

class OmniGuardApplication : Application() {

    lateinit var watchHapticNotifier: WatchHapticNotifier
        private set

    lateinit var emergencyDispatcher: EmergencyDispatcher
        private set

    lateinit var geofenceManager: ScheduleGeofenceManager
        private set

    override fun onCreate() {
        super.onCreate()

        watchHapticNotifier = WatchHapticNotifier(this)
        emergencyDispatcher = EmergencyDispatcher(this)
        geofenceManager = ScheduleGeofenceManager(
            hapticNotifier = watchHapticNotifier,
            emergencyDispatcher = emergencyDispatcher
        )

        // Seed initial safe zone (e.g. Campus / Work with 4:00 PM - 6:00 PM schedule)
        val defaultCampusZone = SafeZone(
            id = UUID.randomUUID().toString(),
            name = "Campus Safe Zone",
            latitude = 37.7749,
            longitude = -122.4194,
            radiusMeters = 200f,
            scheduleWindow = ScheduleWindow(
                activeDays = setOf(1, 2, 3, 4, 5),
                startHour = 16, // 4:00 PM
                startMinute = 0,
                endHour = 18,   // 6:00 PM
                endMinute = 0
            ),
            isEnabled = true
        )

        val defaultHomeZone = SafeZone(
            id = UUID.randomUUID().toString(),
            name = "Home Perimeter",
            latitude = 37.7833,
            longitude = -122.4167,
            radiusMeters = 150f,
            scheduleWindow = ScheduleWindow(
                activeDays = setOf(1, 2, 3, 4, 5, 6, 7),
                startHour = 19,
                startMinute = 0,
                endHour = 8,
                endMinute = 30
            ),
            isEnabled = true
        )

        val defaultContact = EmergencyContact(
            id = UUID.randomUUID().toString(),
            name = "Mom (Primary)",
            phoneNumber = "+1 (555) 234-5678",
            relationship = ContactRelationship.PARENT,
            notifyOnGeofence = true,
            notifyOnFall = true,
            priority = 1
        )

        geofenceManager.setSafeZones(listOf(defaultCampusZone, defaultHomeZone))
        geofenceManager.setEmergencyContacts(listOf(defaultContact))
    }
}
