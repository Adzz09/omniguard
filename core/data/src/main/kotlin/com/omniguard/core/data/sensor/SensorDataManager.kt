package com.omniguard.core.data.sensor

import com.omniguard.core.model.CancellationSource
import com.omniguard.core.model.FallIncident
import com.omniguard.core.model.UserRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import java.util.UUID
import kotlin.math.sqrt

/**
 * 3-axis IMU sensor telemetry snapshot.
 */
data class SensorSample(
    val timestamp: Long,
    val accX: Double,
    val accY: Double,
    val accZ: Double,
    val gyroX: Double = 0.0,
    val gyroY: Double = 0.0,
    val gyroZ: Double = 0.0
) {
    /**
     * Total acceleration vector magnitude in standard gravity (g) where 1.0g ~ 9.80665 m/s^2.
     */
    val magnitudeG: Double
        get() = sqrt(accX * accX + accY * accY + accZ * accZ) / 9.80665
}

/**
 * State representation for active fall countdown alert.
 */
sealed interface FallAlertState {
    data object Idle : FallAlertState
    data class Countdown(
        val incident: FallIncident,
        val remainingSeconds: Int
    ) : FallAlertState
    data class Escalated(val incident: FallIncident) : FallAlertState
    data class Cancelled(val incident: FallIncident, val source: CancellationSource) : FallAlertState
}

/**
 * Configuration thresholds tailored by [UserRole].
 */
data class FallDetectionConfig(
    val gForceThreshold: Double = 3.0,
    val countdownDurationSeconds: Int = 30,
    val inactivityPeriodMillis: Long = 2000L
) {
    companion object {
        fun forRole(role: UserRole): FallDetectionConfig = when (role) {
            UserRole.BIKER -> FallDetectionConfig(gForceThreshold = 6.5, countdownDurationSeconds = 30)
            UserRole.STUDENT -> FallDetectionConfig(gForceThreshold = 4.0, countdownDurationSeconds = 30)
            UserRole.ELDERLY -> FallDetectionConfig(gForceThreshold = 2.5, countdownDurationSeconds = 30)
        }
    }
}

/**
 * Contract for managing accelerometer/gyroscope streaming and fall/crash detection.
 */
interface SensorDataManager {
    val rawSensorFlow: Flow<SensorSample>
    val alertStateFlow: StateFlow<FallAlertState>
    val detectedIncidentsFlow: Flow<FallIncident>

    suspend fun feedSensorSample(sample: SensorSample, currentLat: Double = 0.0, currentLng: Double = 0.0)
    suspend fun cancelActiveAlert(source: CancellationSource): Boolean
    fun updateConfig(config: FallDetectionConfig)
}

/**
 * Default implementation of [SensorDataManager] providing real-time impact detection and 30-second cancellation timer.
 */
class DefaultSensorDataManager(
    private val scope: CoroutineScope,
    private var config: FallDetectionConfig = FallDetectionConfig(),
    private val clock: Clock = Clock.System
) : SensorDataManager {

    private val mutex = Mutex()

    private val _rawSensorFlow = MutableSharedFlow<SensorSample>(extraBufferCapacity = 256)
    override val rawSensorFlow: Flow<SensorSample> = _rawSensorFlow.asSharedFlow()

    private val _alertStateFlow = MutableStateFlow<FallAlertState>(FallAlertState.Idle)
    override val alertStateFlow: StateFlow<FallAlertState> = _alertStateFlow.asStateFlow()

    private val _detectedIncidentsFlow = MutableSharedFlow<FallIncident>(extraBufferCapacity = 32)
    override val detectedIncidentsFlow: Flow<FallIncident> = _detectedIncidentsFlow.asSharedFlow()

    private var countdownJob: Job? = null
    private var activeIncident: FallIncident? = null

    override fun updateConfig(config: FallDetectionConfig) {
        this.config = config
    }

    override suspend fun feedSensorSample(sample: SensorSample, currentLat: Double, currentLng: Double) {
        _rawSensorFlow.tryEmit(sample)

        val gForce = sample.magnitudeG
        if (gForce >= config.gForceThreshold) {
            triggerImpactAlert(gForce, sample.timestamp, currentLat, currentLng)
        }
    }

    private suspend fun triggerImpactAlert(gForce: Double, timestamp: Long, lat: Double, lng: Double) = mutex.withLock {
        // Prevent re-triggering if already in active countdown or escalated
        if (_alertStateFlow.value is FallAlertState.Countdown || _alertStateFlow.value is FallAlertState.Escalated) {
            return@withLock
        }

        val incident = FallIncident(
            id = UUID.randomUUID().toString(),
            timestamp = if (timestamp > 0L) timestamp else clock.now().toEpochMilliseconds(),
            peakGForce = gForce,
            lat = lat,
            lng = lng,
            isCancelled = false
        )
        activeIncident = incident

        countdownJob?.cancel()
        countdownJob = scope.launch(Dispatchers.Default) {
            var remaining = config.countdownDurationSeconds
            while (isActive && remaining > 0) {
                _alertStateFlow.value = FallAlertState.Countdown(incident, remaining)
                delay(1000L)
                remaining--
            }

            if (isActive && remaining <= 0) {
                val escalatedIncident = incident.copy(
                    isCancelled = false,
                    cancellationSource = CancellationSource.TIMEOUT
                )
                _alertStateFlow.value = FallAlertState.Escalated(escalatedIncident)
                _detectedIncidentsFlow.tryEmit(escalatedIncident)
            }
        }
    }

    override suspend fun cancelActiveAlert(source: CancellationSource): Boolean = mutex.withLock {
        val current = _alertStateFlow.value
        if (current !is FallAlertState.Countdown) {
            return@withLock false
        }

        countdownJob?.cancel()
        countdownJob = null

        val incident = activeIncident ?: return@withLock false
        val cancelledIncident = incident.copy(
            isCancelled = true,
            cancellationSource = source
        )

        _alertStateFlow.value = FallAlertState.Cancelled(cancelledIncident, source)
        _detectedIncidentsFlow.tryEmit(cancelledIncident)
        true
    }
}
