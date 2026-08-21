package com.omniguard.core.model

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalTime

@Serializable
data class SafeZone(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 150f,
    val scheduleWindow: ScheduleWindow = ScheduleWindow(),
    val isEnabled: Boolean = true,
    val notifyContactIds: List<String> = emptyList()
) {
    fun isScheduleActive(now: java.time.LocalDateTime = java.time.LocalDateTime.now()): Boolean {
        return isEnabled && scheduleWindow.isWithinWindow(now.dayOfWeek, now.toLocalTime())
    }

    fun isScheduleActive(dayOfWeek: Int, timeString: String): Boolean {
        if (!isEnabled) return false
        val dow = DayOfWeek.of(dayOfWeek)
        val parts = timeString.split(":")
        val time = LocalTime.of(parts[0].toInt(), parts[1].toInt())
        return scheduleWindow.isWithinWindow(dow, time)
    }
}

@Serializable
data class ScheduleWindow(
    val activeDays: Set<Int> = setOf(1, 2, 3, 4, 5), // 1 = Monday, 7 = Sunday
    val startHour: Int = 16, // 4:00 PM
    val startMinute: Int = 0,
    val endHour: Int = 18,   // 6:00 PM
    val endMinute: Int = 0
) {
    fun isWithinWindow(dayOfWeek: DayOfWeek, time: LocalTime): Boolean {
        if (!activeDays.contains(dayOfWeek.value)) return false
        val currentMinutes = time.hour * 60 + time.minute
        val startMinutes = startHour * 60 + startMinute
        val endMinutes = endHour * 60 + endMinute

        return if (startMinutes <= endMinutes) {
            currentMinutes in startMinutes..endMinutes
        } else {
            // Over midnight window (e.g. 22:00 to 06:00)
            currentMinutes >= startMinutes || currentMinutes <= endMinutes
        }
    }

    fun formattedTimeRange(): String {
        fun formatTime(hour: Int, min: Int): String {
            val period = if (hour < 12) "AM" else "PM"
            val displayHour = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            return "%d:%02d %s".format(displayHour, min, period)
        }
        return "${formatTime(startHour, startMinute)} - ${formatTime(endHour, endMinute)}"
    }

    fun formattedDays(): String {
        val daysMap = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
        if (activeDays.size == 7) return "Everyday"
        if (activeDays == setOf(1, 2, 3, 4, 5)) return "Mon - Fri"
        if (activeDays == setOf(6, 7)) return "Weekends"
        return activeDays.sorted().mapNotNull { daysMap[it] }.joinToString(", ")
    }
}
