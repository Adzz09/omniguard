package com.omniguard.core.data.repository

import com.omniguard.core.model.SafeZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Repository interface managing configured SafeZones and active schedule status.
 */
interface SafeZoneRepository {
    val safeZonesFlow: Flow<List<SafeZone>>

    suspend fun getSafeZones(): List<SafeZone>
    suspend fun getSafeZoneById(id: String): SafeZone?
    suspend fun addSafeZone(safeZone: SafeZone): Result<Unit>
    suspend fun updateSafeZone(safeZone: SafeZone): Result<Unit>
    suspend fun deleteSafeZone(id: String): Result<Unit>
    suspend fun getActiveSafeZones(dayOfWeek: Int, timeString: String): List<SafeZone>
}

/**
 * Thread-safe in-memory implementation of [SafeZoneRepository].
 */
class DefaultSafeZoneRepository(
    initialSafeZones: List<SafeZone> = emptyList()
) : SafeZoneRepository {

    private val mutex = Mutex()
    private val _safeZones = MutableStateFlow<List<SafeZone>>(initialSafeZones)
    override val safeZonesFlow: Flow<List<SafeZone>> = _safeZones.asStateFlow()

    override suspend fun getSafeZones(): List<SafeZone> = mutex.withLock {
        _safeZones.value
    }

    override suspend fun getSafeZoneById(id: String): SafeZone? = mutex.withLock {
        _safeZones.value.find { it.id == id }
    }

    override suspend fun addSafeZone(safeZone: SafeZone): Result<Unit> = mutex.withLock {
        runCatching {
            if (_safeZones.value.any { it.id == safeZone.id }) {
                throw IllegalArgumentException("SafeZone with id ${safeZone.id} already exists")
            }
            _safeZones.update { it + safeZone }
        }
    }

    override suspend fun updateSafeZone(safeZone: SafeZone): Result<Unit> = mutex.withLock {
        runCatching {
            if (_safeZones.value.none { it.id == safeZone.id }) {
                throw NoSuchElementException("SafeZone with id ${safeZone.id} not found")
            }
            _safeZones.update { list -> list.map { if (it.id == safeZone.id) safeZone else it } }
        }
    }

    override suspend fun deleteSafeZone(id: String): Result<Unit> = mutex.withLock {
        runCatching {
            if (_safeZones.value.none { it.id == id }) {
                throw NoSuchElementException("SafeZone with id $id not found")
            }
            _safeZones.update { list -> list.filterNot { it.id == id } }
        }
    }

    override suspend fun getActiveSafeZones(dayOfWeek: Int, timeString: String): List<SafeZone> = mutex.withLock {
        _safeZones.value.filter { it.isScheduleActive(dayOfWeek, timeString) }
    }
}
