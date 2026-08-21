package com.omniguard.core.data.repository

import com.omniguard.core.model.TransitLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Repository for managing encrypted transit telemetry logs with GDPR-compliant data lifecycle retention.
 */
interface TransitLogRepository {
    val logsFlow: Flow<List<TransitLog>>

    suspend fun getLogs(): List<TransitLog>
    suspend fun insertLog(log: TransitLog): Result<Unit>
    suspend fun insertLogs(logs: List<TransitLog>): Result<Unit>
    suspend fun purgeExpiredLogs(currentMillis: Long = System.currentTimeMillis(), maxRetentionDays: Int = 7): Int
    suspend fun deleteLog(id: String): Result<Unit>
    suspend fun clearAllLogs(): Result<Unit>
}

/**
 * In-memory thread-safe implementation of [TransitLogRepository].
 * Automatically handles 7-day data retention pruning to ensure privacy compliance.
 */
class DefaultTransitLogRepository(
    initialLogs: List<TransitLog> = emptyList()
) : TransitLogRepository {

    private val mutex = Mutex()
    private val _logs = MutableStateFlow<List<TransitLog>>(initialLogs.sortedByDescending { it.timestampMillis })
    override val logsFlow: Flow<List<TransitLog>> = _logs.asStateFlow()

    override suspend fun getLogs(): List<TransitLog> = mutex.withLock {
        _logs.value
    }

    override suspend fun insertLog(log: TransitLog): Result<Unit> = mutex.withLock {
        runCatching {
            _logs.update { current ->
                (listOf(log) + current.filterNot { it.id == log.id }).sortedByDescending { it.timestampMillis }
            }
        }
    }

    override suspend fun insertLogs(logs: List<TransitLog>): Result<Unit> = mutex.withLock {
        runCatching {
            _logs.update { current ->
                val newIds = logs.map { it.id }.toSet()
                (logs + current.filterNot { it.id in newIds }).sortedByDescending { it.timestampMillis }
            }
        }
    }

    override suspend fun purgeExpiredLogs(currentMillis: Long, maxRetentionDays: Int): Int = mutex.withLock {
        val currentLogs = _logs.value
        val expiredLogs = currentLogs.filter { it.isExpired(currentMillis, maxRetentionDays) }
        val remainingLogs = currentLogs.filterNot { it.isExpired(currentMillis, maxRetentionDays) }

        _logs.value = remainingLogs
        return@withLock expiredLogs.size
    }

    override suspend fun deleteLog(id: String): Result<Unit> = mutex.withLock {
        runCatching {
            _logs.update { current -> current.filterNot { it.id == id } }
        }
    }

    override suspend fun clearAllLogs(): Result<Unit> = mutex.withLock {
        runCatching {
            _logs.value = emptyList()
        }
    }
}
