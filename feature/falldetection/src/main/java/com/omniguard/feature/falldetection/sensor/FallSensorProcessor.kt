package com.omniguard.feature.falldetection.sensor

import com.omniguard.feature.falldetection.model.FallDetectionConfig
import com.omniguard.feature.falldetection.model.FallImpactData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * High-performance IMU sensor processor for fall detection.
 * Analyzes high-G impact (threshold > 3.5G) followed by sudden deceleration and immobility.
 */
class FallSensorProcessor(
    private val config: FallDetectionConfig = FallDetectionConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    companion object {
        const val STANDARD_GRAVITY = 9.80665f
    }

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _potentialFallDetected = MutableSharedFlow<FallImpactData>(replay = 1, extraBufferCapacity = 64)
    val potentialFallDetected: SharedFlow<FallImpactData> = _potentialFallDetected.asSharedFlow()

    private val _confirmedFallEvent = MutableSharedFlow<FallImpactData>(replay = 1, extraBufferCapacity = 64)
    val confirmedFallEvent: SharedFlow<FallImpactData> = _confirmedFallEvent.asSharedFlow()

    // Sliding window of recent acceleration magnitudes (in G)
    private val windowSamples = ArrayDeque<Pair<Long, Float>>()
    private val windowDurationMs = config.immobilityWindowMs

    private var impactCandidate: FallImpactData? = null
    private var impactTimestamp: Long = 0L

    fun startMonitoring() {
        _isMonitoring.value = true
        windowSamples.clear()
        impactCandidate = null
    }

    fun stopMonitoring() {
        _isMonitoring.value = false
        windowSamples.clear()
        impactCandidate = null
    }

    /**
     * Ingests 3-axis accelerometer readings (in m/s^2) and optional gyroscope data.
     * Computes vector magnitude, compares with impact threshold (>3.5G),
     * and evaluates post-impact immobility variance.
     */
    fun processSensorSample(
        ax: Float,
        ay: Float,
        az: Float,
        gx: Float = 0f,
        gy: Float = 0f,
        gz: Float = 0f,
        timestampMs: Long = System.currentTimeMillis()
    ) {
        if (!_isMonitoring.value) return

        // Compute total acceleration vector magnitude in Gs
        val rawMagnitude = sqrt(ax * ax + ay * ay + az * az)
        val accelerationG = rawMagnitude / STANDARD_GRAVITY
        val gyroMagnitude = sqrt(gx * gx + gy * gy + gz * gz)

        // Maintain sliding window for variance computation
        windowSamples.addLast(timestampMs to accelerationG)
        val cutoff = timestampMs - windowDurationMs
        while (windowSamples.isNotEmpty() && windowSamples.first().first < cutoff) {
            windowSamples.removeFirst()
        }

        // 1. Check for High-G Impact (> 3.5G default)
        if (accelerationG >= config.impactThresholdG && impactCandidate == null) {
            val impactData = FallImpactData(
                accelerationG = accelerationG,
                gyroMagnitudeRadS = gyroMagnitude,
                timestampMs = timestampMs,
                isImpact = true,
                isImmobile = false
            )
            impactCandidate = impactData
            impactTimestamp = timestampMs
            scope.launch {
                _potentialFallDetected.emit(impactData)
            }
            return
        }

        // 2. Post-Impact Immobility Phase Check
        val activeImpact = impactCandidate
        if (activeImpact != null) {
            val timeSinceImpact = timestampMs - impactTimestamp
            if (timeSinceImpact >= config.immobilityWindowMs) {
                // Calculate variance of acceleration over the post-impact immobility window (excluding the impact spike)
                val postImpactSamples = windowSamples.filter { it.first > impactTimestamp }
                val isImmobile = if (postImpactSamples.isNotEmpty()) {
                    val mean = postImpactSamples.map { it.second }.average().toFloat()
                    val variance = postImpactSamples.map { (it.second - mean).toDouble().pow(2.0) }.average().toFloat()
                    variance <= config.immobilityVarianceThreshold
                } else {
                    false
                }

                if (isImmobile) {
                    val confirmedData = activeImpact.copy(isImmobile = true)
                    _confirmedFallEvent.tryEmit(confirmedData)
                    scope.launch {
                        _confirmedFallEvent.emit(confirmedData)
                    }
                }
                // Reset impact candidate after window evaluation
                impactCandidate = null
            }
        }
    }
}
