package com.omniguard.feature.guidemehome.tracking

import app.cash.turbine.test
import com.omniguard.core.model.SafeZone
import com.omniguard.core.model.ScheduleWindow
import com.omniguard.core.model.TransitEventType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalTime

class ScheduleGeofenceTest {

    private lateinit var geofenceManager: ScheduleGeofenceManager

    @BeforeEach
    fun setup() {
        geofenceManager = ScheduleGeofenceManager()
    }

    @Test
    fun `ScheduleWindow correctly evaluates normal and overnight time windows`() {
        // Monday-Friday 16:00 to 18:00
        val window = ScheduleWindow(
            activeDays = setOf(1, 2, 3, 4, 5),
            startHour = 16,
            startMinute = 0,
            endHour = 18,
            endMinute = 0
        )

        assertTrue(window.isWithinWindow(DayOfWeek.MONDAY, LocalTime.of(16, 30)))
        assertTrue(window.isWithinWindow(DayOfWeek.FRIDAY, LocalTime.of(16, 0)))
        assertTrue(window.isWithinWindow(DayOfWeek.FRIDAY, LocalTime.of(18, 0)))
        assertFalse(window.isWithinWindow(DayOfWeek.WEDNESDAY, LocalTime.of(15, 59)))
        assertFalse(window.isWithinWindow(DayOfWeek.WEDNESDAY, LocalTime.of(18, 1)))
        assertFalse(window.isWithinWindow(DayOfWeek.SATURDAY, LocalTime.of(16, 30)))

        // Overnight window: 22:00 to 06:00
        val overnight = ScheduleWindow(
            activeDays = setOf(1, 2, 3, 4, 5, 6, 7),
            startHour = 22,
            startMinute = 0,
            endHour = 6,
            endMinute = 0
        )

        assertTrue(overnight.isWithinWindow(DayOfWeek.SATURDAY, LocalTime.of(23, 15)))
        assertTrue(overnight.isWithinWindow(DayOfWeek.SUNDAY, LocalTime.of(3, 45)))
        assertFalse(overnight.isWithinWindow(DayOfWeek.SUNDAY, LocalTime.of(12, 0)))
    }

    @Test
    fun `processLocationUpdate emits enter and exit transitions during active schedule window`() = runTest {
        val campusZone = SafeZone(
            id = "zone-campus",
            name = "University Campus Library",
            latitude = 37.7749,
            longitude = -122.4194,
            radiusMeters = 200.0f,
            scheduleWindow = ScheduleWindow(
                activeDays = setOf(1, 2, 3, 4, 5),
                startHour = 16,
                startMinute = 0,
                endHour = 20,
                endMinute = 0
            )
        )

        geofenceManager.addSafeZone(campusZone)

        geofenceManager.transitions.test {
            // User outside campus at 17:00 (Active window, ~500m away)
            val transitions1 = geofenceManager.processLocationUpdate(
                lat = 37.7790,
                lng = -122.4194,
                dayOfWeek = DayOfWeek.TUESDAY,
                time = LocalTime.of(17, 0)
            )
            assertEquals(0, transitions1.size)
            assertFalse(geofenceManager.activeInsideZones.value.contains("zone-campus"))

            // User steps inside campus radius (~50m away from center)
            val transitions2 = geofenceManager.processLocationUpdate(
                lat = 37.7752,
                lng = -122.4194,
                dayOfWeek = DayOfWeek.TUESDAY,
                time = LocalTime.of(17, 15)
            )
            assertEquals(1, transitions2.size)
            assertEquals(TransitEventType.SAFE_ZONE_ENTER, transitions2.first().eventType)
            assertTrue(geofenceManager.activeInsideZones.value.contains("zone-campus"))

            val enterEvent = awaitItem()
            assertEquals(TransitEventType.SAFE_ZONE_ENTER, enterEvent.eventType)
            assertEquals("zone-campus", enterEvent.safeZone.id)

            // User steps out of campus (~600m away)
            val transitions3 = geofenceManager.processLocationUpdate(
                lat = 37.7800,
                lng = -122.4194,
                dayOfWeek = DayOfWeek.TUESDAY,
                time = LocalTime.of(17, 45)
            )
            assertEquals(1, transitions3.size)
            assertEquals(TransitEventType.SAFE_ZONE_EXIT, transitions3.first().eventType)
            assertFalse(geofenceManager.activeInsideZones.value.contains("zone-campus"))

            val exitEvent = awaitItem()
            assertEquals(TransitEventType.SAFE_ZONE_EXIT, exitEvent.eventType)
        }
    }

    @Test
    fun `processLocationUpdate does not trigger transitions when schedule window is inactive`() = runTest {
        val campusZone = SafeZone(
            id = "zone-campus",
            name = "University Campus",
            latitude = 37.7749,
            longitude = -122.4194,
            radiusMeters = 200.0f,
            scheduleWindow = ScheduleWindow(
                activeDays = setOf(1, 2, 3, 4, 5),
                startHour = 16,
                startMinute = 0,
                endHour = 18,
                endMinute = 0
            )
        )
        geofenceManager.addSafeZone(campusZone)

        // Inside coordinates, but time is 10:00 AM (Schedule inactive)
        val transitions = geofenceManager.processLocationUpdate(
            lat = 37.7749,
            lng = -122.4194,
            dayOfWeek = DayOfWeek.WEDNESDAY,
            time = LocalTime.of(10, 0)
        )

        assertEquals(0, transitions.size)
        assertFalse(geofenceManager.activeInsideZones.value.contains("zone-campus"))
    }
}
