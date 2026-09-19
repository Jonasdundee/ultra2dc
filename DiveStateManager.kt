package com.argus.divaultra.core

import com.argus.divaultra.deco.BuhlmannZHL16C
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min

enum class DivePhase {
    SURFACE,
    DIVING,
    SAFETY_STOP,
    DECO_STOP,
    COMPLETED
}

enum class SafetyStopStatus {
    NOT_REQUIRED,
    REQUIRED_PENDING,     // Requirement triggered (>10m or NDL<15), awaiting ascent to stop zone
    IN_STOP_COUNTING,     // Diver in 3.0m - 6.0m window, timer counting down
    PAUSED_TOO_DEEP,      // Diver descended below 6.0m, timer paused
    PAUSED_TOO_SHALLOW,   // Diver drifted above 2.8m, alert to descend
    COMPLETED             // Countdown hit 0:00, clear to surface
}

enum class AscentRateStatus {
    OPTIMAL,  // 0 - 8 m/min (Green)
    CAUTION,  // 9 - 10 m/min (Yellow)
    DANGER    // > 10 m/min (Red & Haptic alert)
}

data class DiveTelemetry(
    val phase: DivePhase = DivePhase.SURFACE,
    val currentDepthMeters: Double = 0.0,
    val maxDepthMeters: Double = 0.0,
    val averageDepthMeters: Double = 0.0,
    val diveTimeSeconds: Long = 0L,
    val waterTemperatureCelsius: Double = 24.0,
    val ndlMinutes: Int = 99,
    val ceilingMeters: Double = 0.0,
    val ascentRateMetersPerMin: Double = 0.0,
    val ascentRateStatus: AscentRateStatus = AscentRateStatus.OPTIMAL,
    // Safety Stop Engine Attributes
    val isSafetyStopRequired: Boolean = false,
    val safetyStopStatus: SafetyStopStatus = SafetyStopStatus.NOT_REQUIRED,
    val safetyStopTotalSeconds: Int = 180,
    val safetyStopRemainingSeconds: Int = 180,
    val safetyStopTargetDepthMeters: Double = 5.0,
    val safetyStopMinDepthMeters: Double = 3.0,
    val safetyStopMaxDepthMeters: Double = 6.0,
    // Nitrox & Decompression Metrics
    val fractionO2: Double = 0.32, // Default EAN32 Nitrox
    val currentPO2: Double = 0.32,
    val modMeters: Double = 33.8,
    val cnsPercent: Double = 0.0,
    // Tactical Compass Heading
    val compassHeadingDegrees: Float = 245f,
    val cardinalDirection: String = "SW"
)

class DiveStateManager(
    private val decoEngine: BuhlmannZHL16C = BuhlmannZHL16C()
) {
    private val _telemetry = MutableStateFlow(DiveTelemetry())
    val telemetry: StateFlow<DiveTelemetry> = _telemetry.asStateFlow()

    private var previousDepth = 0.0
    private var depthSum = 0.0
    private var depthSampleCount = 0L
    private var maxDepthSeen = 0.0
    private var isSubmerged = false
    
    // Safety Stop Internal State
    private var isSafetyStopTriggered = false
    private var safetyStopRemainingSec = 180
    private var safetyStopTotalSec = 180
    private var safetyStopCompleted = false

    fun setGasMix(fractionO2: Double) {
        val mod = decoEngine.calculateMOD(fractionO2, 1.4)
        _telemetry.value = _telemetry.value.copy(
            fractionO2 = fractionO2,
            modMeters = mod
        )
    }

    fun updateHeading(headingDegrees: Float) {
        val normalized = (headingDegrees % 360f + 360f) % 360f
        val cardinal = when (normalized.toInt()) {
            in 23..67 -> "NE"
            in 68..112 -> "E"
            in 113..157 -> "SE"
            in 158..202 -> "S"
            in 203..247 -> "SW"
            in 248..292 -> "W"
            in 293..337 -> "NW"
            else -> "N"
        }
        _telemetry.value = _telemetry.value.copy(
            compassHeadingDegrees = normalized,
            cardinalDirection = cardinal
        )
    }

    /**
     * Process new depth reading from Galaxy Watch Ultra sensor (or simulator)
     */
    fun onNewDepthSample(depthMeters: Double, temperatureCelsius: Double, deltaSeconds: Double = 1.0) {
        val current = _telemetry.value

        // Auto dive start / end trigger (1.2m threshold)
        if (!isSubmerged && depthMeters >= 1.2) {
            isSubmerged = true
            maxDepthSeen = depthMeters
            depthSum = depthMeters
            depthSampleCount = 1
            isSafetyStopTriggered = false
            safetyStopCompleted = false
            safetyStopTotalSec = 180
            safetyStopRemainingSec = 180
            previousDepth = depthMeters
        } else if (isSubmerged && depthMeters < 0.5) {
            // Dive finished / surfaced
            isSubmerged = false
            _telemetry.value = current.copy(phase = DivePhase.COMPLETED)
            return
        }

        if (!isSubmerged) {
            _telemetry.value = current.copy(
                phase = DivePhase.SURFACE,
                currentDepthMeters = depthMeters,
                waterTemperatureCelsius = temperatureCelsius,
                currentPO2 = decoEngine.calculatePO2(depthMeters, current.fractionO2)
            )
            return
        }

        // Active dive update
        val newDiveTime = current.diveTimeSeconds + deltaSeconds.toLong()
        maxDepthSeen = max(maxDepthSeen, depthMeters)
        depthSum += depthMeters
        depthSampleCount++
        val avgDepth = depthSum / depthSampleCount

        // Ascent Rate (m/min)
        val instantAscentRate = if (deltaSeconds > 0) {
            ((previousDepth - depthMeters) / deltaSeconds) * 60.0
        } else 0.0
        previousDepth = depthMeters

        val ascentStatus = when {
            instantAscentRate > 10.0 -> AscentRateStatus.DANGER
            instantAscentRate >= 9.0 -> AscentRateStatus.CAUTION
            else -> AscentRateStatus.OPTIMAL
        }

        // Advance Bühlmann ZHL-16C decompression core
        decoEngine.update(depthMeters, deltaSeconds, current.fractionO2)
        val ndl = decoEngine.calculateNDL(depthMeters, current.fractionO2)
        val ceiling = decoEngine.calculateCeilingMeters()
        val po2 = decoEngine.calculatePO2(depthMeters, current.fractionO2)
        val cns = decoEngine.cnsToxicityPercent

        // 1. SAFETY STOP REQUIREMENT CALCULATION:
        // Automatically required if depth exceeded 10m, or NDL <= 15m, or dive > 20 min
        if (!isSafetyStopTriggered) {
            if (maxDepthSeen >= 10.0 || ndl <= 15 || newDiveTime >= 1200) {
                isSafetyStopTriggered = true
                // If dive was deep (>30m) or NDL critical (<= 5m), extend stop to 5 minutes
                if (maxDepthSeen >= 30.0 || ndl <= 5) {
                    safetyStopTotalSec = 300
                    safetyStopRemainingSec = 300
                }
            }
        }

        // 2. SAFETY STOP STATE MACHINE:
        var safetyStatus = SafetyStopStatus.NOT_REQUIRED
        var phase = DivePhase.DIVING

        if (ceiling > 0.5) {
            // Deco stop takes precedence over safety stop
            phase = DivePhase.DECO_STOP
        } else if (isSafetyStopTriggered) {
            if (safetyStopCompleted) {
                safetyStatus = SafetyStopStatus.COMPLETED
            } else {
                when {
                    // Inside Safety Stop Window (3.0m - 6.0m)
                    depthMeters in 3.0..6.0 -> {
                        phase = DivePhase.SAFETY_STOP
                        safetyStatus = SafetyStopStatus.IN_STOP_COUNTING
                        // Active countdown
                        safetyStopRemainingSec = max(0, (safetyStopRemainingSec - deltaSeconds).toInt())
                        if (safetyStopRemainingSec == 0) {
                            safetyStopCompleted = true
                            safetyStatus = SafetyStopStatus.COMPLETED
                        }
                    }
                    // Breached too shallow during stop (< 2.8m before countdown finished)
                    depthMeters < 2.8 && safetyStopRemainingSec < safetyStopTotalSec -> {
                        phase = DivePhase.SAFETY_STOP
                        safetyStatus = SafetyStopStatus.PAUSED_TOO_SHALLOW
                    }
                    // Drifted too deep (> 6.0m after entering stop)
                    depthMeters > 6.0 && safetyStopRemainingSec < safetyStopTotalSec && depthMeters < 9.0 -> {
                        phase = DivePhase.SAFETY_STOP
                        safetyStatus = SafetyStopStatus.PAUSED_TOO_DEEP
                    }
                    // Still at bottom or ascended back deep
                    else -> {
                        safetyStatus = SafetyStopStatus.REQUIRED_PENDING
                    }
                }
            }
        }

        _telemetry.value = current.copy(
            phase = phase,
            currentDepthMeters = depthMeters,
            maxDepthMeters = maxDepthSeen,
            averageDepthMeters = avgDepth,
            diveTimeSeconds = newDiveTime,
            waterTemperatureCelsius = temperatureCelsius,
            ndlMinutes = ndl,
            ceilingMeters = ceiling,
            ascentRateMetersPerMin = instantAscentRate,
            ascentRateStatus = ascentStatus,
            isSafetyStopRequired = isSafetyStopTriggered,
            safetyStopStatus = safetyStatus,
            safetyStopTotalSeconds = safetyStopTotalSec,
            safetyStopRemainingSeconds = safetyStopRemainingSec,
            currentPO2 = po2,
            cnsPercent = cns
        )
    }
}
