package com.omniguard.core.model

import kotlinx.serialization.Serializable

@Serializable
data class EmergencyContact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val relationship: ContactRelationship = ContactRelationship.OTHER,
    val notifyOnGeofence: Boolean = true,
    val notifyOnFall: Boolean = true,
    val priority: Int = 1,
    val isNotified: Boolean = false
) {
    val phone: String get() = phoneNumber
}

@Serializable
enum class ContactRelationship(val displayName: String) {
    PARENT("Parent"),
    PARTNER("Partner"),
    FRIEND("Friend"),
    CAREGIVER("Caregiver"),
    SIBLING("Sibling"),
    COLLEAGUE("Colleague"),
    OTHER("Other")
}
