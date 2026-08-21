package com.omniguard.core.data.repository

import com.omniguard.core.model.EmergencyContact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Repository interface governing CRUD operations for trusted emergency contacts.
 * Enforces FR-01: Maximum 5 verified contacts per user profile.
 */
interface EmergencyContactRepository {
    val contactsFlow: Flow<List<EmergencyContact>>

    suspend fun getContacts(): List<EmergencyContact>
    suspend fun getContactById(id: String): EmergencyContact?
    suspend fun addContact(contact: EmergencyContact): Result<Unit>
    suspend fun updateContact(contact: EmergencyContact): Result<Unit>
    suspend fun deleteContact(id: String): Result<Unit>
    suspend fun setContactNotified(id: String, isNotified: Boolean): Result<Unit>
    suspend fun resetAllNotificationStatus(): Result<Unit>
}

/**
 * In-memory thread-safe implementation of [EmergencyContactRepository] with strict capacity constraint validation.
 */
class DefaultEmergencyContactRepository(
    initialContacts: List<EmergencyContact> = emptyList()
) : EmergencyContactRepository {

    private val mutex = Mutex()
    private val _contacts = MutableStateFlow<List<EmergencyContact>>(
        initialContacts.take(MAX_CONTACTS_ALLOWED).sortedBy { it.priority }
    )
    override val contactsFlow: Flow<List<EmergencyContact>> = _contacts.asStateFlow()

    override suspend fun getContacts(): List<EmergencyContact> = mutex.withLock {
        _contacts.value
    }

    override suspend fun getContactById(id: String): EmergencyContact? = mutex.withLock {
        _contacts.value.find { it.id == id }
    }

    override suspend fun addContact(contact: EmergencyContact): Result<Unit> = mutex.withLock {
        runCatching {
            val currentList = _contacts.value
            if (currentList.size >= MAX_CONTACTS_ALLOWED) {
                throw IllegalStateException("Cannot add more than $MAX_CONTACTS_ALLOWED emergency contacts (FR-01)")
            }
            if (currentList.any { it.id == contact.id }) {
                throw IllegalArgumentException("Contact with ID ${contact.id} already exists")
            }
            if (currentList.any { it.phone == contact.phone }) {
                throw IllegalArgumentException("Contact with phone number ${contact.phone} already exists")
            }

            _contacts.update { (it + contact).sortedBy { c -> c.priority } }
        }
    }

    override suspend fun updateContact(contact: EmergencyContact): Result<Unit> = mutex.withLock {
        runCatching {
            val currentList = _contacts.value
            val existingIndex = currentList.indexOfFirst { it.id == contact.id }
            if (existingIndex == -1) {
                throw NoSuchElementException("Emergency contact with id ${contact.id} not found")
            }

            _contacts.update { list ->
                list.map { if (it.id == contact.id) contact else it }.sortedBy { it.priority }
            }
        }
    }

    override suspend fun deleteContact(id: String): Result<Unit> = mutex.withLock {
        runCatching {
            val currentList = _contacts.value
            if (currentList.none { it.id == id }) {
                throw NoSuchElementException("Emergency contact with id $id not found")
            }
            _contacts.update { list -> list.filterNot { it.id == id } }
        }
    }

    override suspend fun setContactNotified(id: String, isNotified: Boolean): Result<Unit> = mutex.withLock {
        runCatching {
            _contacts.update { list ->
                list.map { if (it.id == id) it.copy(isNotified = isNotified) else it }
            }
        }
    }

    override suspend fun resetAllNotificationStatus(): Result<Unit> = mutex.withLock {
        runCatching {
            _contacts.update { list ->
                list.map { it.copy(isNotified = false) }
            }
        }
    }

    companion object {
        const val MAX_CONTACTS_ALLOWED = 5
    }
}
