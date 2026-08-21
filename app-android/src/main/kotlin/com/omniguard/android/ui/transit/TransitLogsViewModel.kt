package com.omniguard.android.ui.transit

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniguard.core.model.TransitEventType
import com.omniguard.core.model.TransitLog
import com.omniguard.feature.geofencing.crypto.LogEncryptor
import com.omniguard.feature.geofencing.service.ScheduleGeofenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@Immutable
data class TransitLogsUiState(
    val logs: List<TransitLog> = emptyList(),
    val selectedFilter: TransitEventType? = null,
    val inspectingLog: TransitLog? = null,
    val decryptedPayload: String? = null,
    val totalLogsCount: Int = 0,
    val retentionDays: Int = 7
)

class TransitLogsViewModel(
    private val geofenceManager: ScheduleGeofenceManager
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow<TransitEventType?>(null)
    private val _inspectingLog = MutableStateFlow<TransitLog?>(null)
    private val _decryptedPayload = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TransitLogsUiState> = combine(
        geofenceManager.transitLogs,
        _selectedFilter,
        _inspectingLog,
        _decryptedPayload
    ) { logs, filter, inspecting, decrypted ->
        val filtered = if (filter == null) logs else logs.filter { it.eventType == filter }
        TransitLogsUiState(
            logs = filtered,
            selectedFilter = filter,
            inspectingLog = inspecting,
            decryptedPayload = decrypted,
            totalLogsCount = logs.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransitLogsUiState()
    )

    fun setFilter(type: TransitEventType?) {
        _selectedFilter.value = type
    }

    fun inspectLog(log: TransitLog) {
        val decrypted = LogEncryptor.decrypt(log.encryptedPayload, log.iv)
        _inspectingLog.value = log
        _decryptedPayload.value = decrypted
    }

    fun dismissInspectDialog() {
        _inspectingLog.value = null
        _decryptedPayload.value = null
    }

    fun purgeExpiredLogs() {
        geofenceManager.purgeExpiredLogs()
    }

    fun clearAllLogs() {
        geofenceManager.clearAllLogs()
    }
}
