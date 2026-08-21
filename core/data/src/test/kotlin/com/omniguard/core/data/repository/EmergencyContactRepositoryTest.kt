package com.omniguard.core.data.repository

import app.cash.turbine.test
import com.omniguard.core.model.ContactRelationship
import com.omniguard.core.model.EmergencyContact
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EmergencyContactRepositoryTest {

    private lateinit var repository: DefaultEmergencyContactRepository

    @BeforeEach
    fun setup() {
        repository = DefaultEmergencyContactRepository()
    }

    @Test
    fun `addContact successfully stores contacts up to max 5 limit FR-01`() = runTest {
        for (i in 1..5) {
            val contact = EmergencyContact(
                id = "c$i",
                name = "Contact $i",
                phoneNumber = "+155500000$i",
                relationship = ContactRelationship.FRIEND,
                priority = i
            )
            val result = repository.addContact(contact)
            assertTrue(result.isSuccess)
        }

        val stored = repository.getContacts()
        assertEquals(5, stored.size)

        // Attempting to add 6th contact must fail
        val sixthContact = EmergencyContact(
            id = "c6",
            name = "Contact 6",
            phoneNumber = "+1555000006",
            relationship = ContactRelationship.OTHER
        )
        val overflowResult = repository.addContact(sixthContact)
        assertTrue(overflowResult.isFailure)
        assertTrue(overflowResult.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `contactsFlow emits updated list when contact added or removed`() = runTest {
        repository.contactsFlow.test {
            assertEquals(0, awaitItem().size) // initial

            val contact = EmergencyContact(
                id = "c1",
                name = "Alice",
                phoneNumber = "+1555123456",
                relationship = ContactRelationship.PARTNER,
                priority = 1
            )
            repository.addContact(contact)

            val updated = awaitItem()
            assertEquals(1, updated.size)
            assertEquals("Alice", updated.first().name)

            repository.deleteContact("c1")
            val cleared = awaitItem()
            assertEquals(0, cleared.size)
        }
    }

    @Test
    fun `setContactNotified updates specific contact notification state`() = runTest {
        val contact = EmergencyContact(
            id = "c1",
            name = "Bob Caregiver",
            phoneNumber = "+1555999888",
            relationship = ContactRelationship.CAREGIVER,
            isNotified = false
        )
        repository.addContact(contact)

        repository.setContactNotified("c1", true)
        val updated = repository.getContactById("c1")
        assertNotNull(updated)
        assertTrue(updated!!.isNotified)

        repository.resetAllNotificationStatus()
        val resetContact = repository.getContactById("c1")
        assertFalse(resetContact!!.isNotified)
    }
}
