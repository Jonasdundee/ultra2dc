package com.argus.divaultra.core

import com.argus.divaultra.deco.BuhlmannZHL16C
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

enum class DivePhase {
    SURFACE,
    DIVING,
    SAFETY_STOP,
    DECO_STOP,
    SURFACING,
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
    val cardinalDirection: String = "SW",
    // Surface Interval Hysteresis (5 minutes countdown upon surfacing)
    val surfaceIntervalRemainingSeconds: Int = 300
)

class DiveStateManager(
    private val decoEngine: BuhlmannZHL16C = BuhlmannZHL16C()
) {
    private val _telemetry = MutableStateFlow(DiveTelemetry())
    val telemetry: StateFlow<DiveTelemetry> = _telemetry.asStateFlow()

    private var previousDepth = 0.0
    private var lastDepthTimestampMs = 0L
    private var smoothedAscentRate = 0.0
    private var depthSum = 0.0
    private var depthSampleCount = 0L
    private var maxDepthSeen = 0.0
    private var isSubmerged = false
    
    // Safety Stop Internal State
    private var isSafetyStopTriggered = false
    private var safetyStopRemainingSec = 180
    private var safetyStopTotalSec = 180
    private var safetyStopCompleted = false
    private var surfaceIntervalRemainingSec = 300

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
     * Process high-frequency sensor depth readings (called as sensor fires ~10Hz).
     * Smooths depth, calculates physical ascent speed, and manages submersion transition.
     */
    fun onDepthReading(depthMeters: Double, temperatureCelsius: Double) {
        val current = _telemetry.value
        val nowMs = System.currentTimeMillis()

        // 1. Auto dive entry (>= 1.2m) and exit (< 0.5m after diving)
        if (!isSubmerged && depthMeters >= 1.2) {
            isSubmerged = true
            maxDepthSeen = depthMeters
            depthSum = depthMeters
            depthSampleCount = 1
            isSafetyStopTriggered = false
            safetyStopCompleted = false
            safetyStopTotalSec = 180
            safetyStopRemainingSec = 180
            surfaceIntervalRemainingSec = 300
            previousDepth = depthMeters
            lastDepthTimestampMs = nowMs
            smoothedAscentRate = 0.0
        } else if (isSubmerged && depthMeters >= 1.2 && current.phase == DivePhase.SURFACING) {
            // Diver redescended during 5-minute surface interval: Resume dive!
            surfaceIntervalRemainingSec = 300
        } else if (isSubmerged && depthMeters < 0.5 && current.diveTimeSeconds > 10) {
            // Diver surfaced: Enter 5-minute surface interval hysteresis countdown
            _telemetry.value = current.copy(
                phase = DivePhase.SURFACING,
                currentDepthMeters = depthMeters,
                waterTemperatureCelsius = temperatureCelsius,
                surfaceIntervalRemainingSeconds = surfaceIntervalRemainingSec
            )
            return
        }

        if (!isSubmerged) {
            _telemetry.value = current.copy(
                phase = DivePhase.SURFACE,
                currentDepthMeters = depthMeters,
                waterTemperatureCelsius = temperatureCelsius,
                currentPO2 = decoEngine.calculatePO2(depthMeters, current.fractionO2),
                ascentRateMetersPerMin = 0.0,
                ascentRateStatus = AscentRateStatus.OPTIMAL
            )
            return
        }

        // 2. High-precision ascent rate with low-pass filter (runs at actual wall-clock delta)
        val dtSec = if (lastDepthTimestampMs > 0L) (nowMs - lastDepthTimestampMs) / 1000.0 else 0.0
        if (dtSec >= 0.25) { // update ascent velocity every 250ms minimum
            val rawRate = ((previousDepth - depthMeters) / dtSec) * 60.0
            smoothedAscentRate = (smoothedAscentRate * 0.65) + (rawRate * 0.35)
            previousDepth = depthMeters
            lastDepthTimestampMs = nowMs
        }

        val ascentStatus = when {
            smoothedAscentRate > 10.0 -> AscentRateStatus.DANGER
            smoothedAscentRate >= 9.0 -> AscentRateStatus.CAUTION
            else -> AscentRateStatus.OPTIMAL
        }

        maxDepthSeen = max(maxDepthSeen, depthMeters)
        depthSum += depthMeters
        depthSampleCount++
        val avgDepth = depthSum / depthSampleCount

        _telemetry.value = current.copy(
            currentDepthMeters = depthMeters,
            maxDepthMeters = maxDepthSeen,
            averageDepthMeters = avgDepth,
            waterTemperatureCelsius = temperatureCelsius,
            ascentRateMetersPerMin = max(0.0, smoothedAscentRate),
            ascentRateStatus = ascentStatus
        )
    }

    /**
     * Dedicated 1-Second Clock Ticker: Called strictly once per real-world second.
     * Advances dive timer, Bühlmann decompression calculations, and safety stop counter.
     */
    fun onOneSecondTick() {
        if (!isSubmerged) return
        val current = _telemetry.value

        // Surface interval countdown handler (5 minutes auto-end window)
        if (current.phase == DivePhase.SURFACING) {
            surfaceIntervalRemainingSec--
            if (surfaceIntervalRemainingSec <= 0) {
                isSubmerged = false
                _telemetry.value = current.copy(
                    phase = DivePhase.COMPLETED,
                    surfaceIntervalRemainingSeconds = 0
                )
            } else {
                _telemetry.value = current.copy(
                    surfaceIntervalRemainingSeconds = surfaceIntervalRemainingSec
                )
            }
            return
        }

        val depthMeters = current.currentDepthMeters

        // 1. Advance dive time by exactly 1 real second
        val newDiveTime = current.diveTimeSeconds + 1L

        // 2. Advance Bühlmann ZHL-16C by exactly 1.0 second
        decoEngine.update(depthMeters, 1.0, current.fractionO2)
        val ndl = decoEngine.calculateNDL(depthMeters, current.fractionO2)
        val rawCeiling = decoEngine.calculateCeilingMeters()
        val po2 = decoEngine.calculatePO2(depthMeters, current.fractionO2)
        val cns = decoEngine.cnsToxicityPercent

        // In diving decompression theory:
        // True Deco only occurs when NDL has elapsed to 0 AND raw ceiling is meaningful (>= 1.0m).
        // Stops are quantized in 3-meter stages (3m, 6m, 9m...).
        val isTrueDeco = ndl == 0 && rawCeiling >= 1.0
        val stagedCeilingMeters = if (isTrueDeco) {
            ceil(rawCeiling / 3.0) * 3.0
        } else {
            0.0
        }

        // 3. Safety Stop Trigger: Required if dive exceeded 10m depth, or NDL <= 15m, or dive > 20 min
        if (!isSafetyStopTriggered) {
            if (maxDepthSeen >= 10.0 || ndl <= 15 || newDiveTime >= 1200) {
                isSafetyStopTriggered = true
                if (maxDepthSeen >= 30.0 || ndl <= 5) {
                    safetyStopTotalSec = 300
                    safetyStopRemainingSec = 300
                }
            }
        }

        // 4. State Machine: Deco vs Safety Stop vs Active Diving
        var safetyStatus = SafetyStopStatus.NOT_REQUIRED
        var phase = DivePhase.DIVING

        if (isTrueDeco) {
            // True decompression stop obligation (overrides safety stop)
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
                        safetyStopRemainingSec = max(0, safetyStopRemainingSec - 1)
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
            diveTimeSeconds = newDiveTime,
            ndlMinutes = ndl,
            ceilingMeters = stagedCeilingMeters,
            currentPO2 = po2,
            cnsPercent = cns,
            isSafetyStopRequired = isSafetyStopTriggered,
            safetyStopStatus = safetyStatus,
            safetyStopTotalSeconds = safetyStopTotalSec,
            safetyStopRemainingSeconds = safetyStopRemainingSec
        )
    }

    /**
     * Backward-compatible simulator entry point
     */
    fun onNewDepthSample(depthMeters: Double, temperatureCelsius: Double, deltaSeconds: Double = 1.0) {
        onDepthReading(depthMeters, temperatureCelsius)
        val ticks = max(1, deltaSeconds.toInt())
        for (i in 0 until ticks) {
            onOneSecondTick()
        }
    }

    /**
     * Manually end dive immediately during surface interval without waiting 5 minutes
     */
    fun endDiveNow() {
        if (isSubmerged || _telemetry.value.phase == DivePhase.SURFACING) {
            isSubmerged = false
            _telemetry.value = _telemetry.value.copy(
                phase = DivePhase.COMPLETED,
                surfaceIntervalRemainingSeconds = 0
            )
        }
    }

    /**
     * Reset back to clean surface state ready for next dive
     */
    fun resetToSurface() {
        isSubmerged = false
        surfaceIntervalRemainingSec = 300
        _telemetry.value = DiveTelemetry(
            fractionO2 = _telemetry.value.fractionO2,
            modMeters = _telemetry.value.modMeters,
            compassHeadingDegrees = _telemetry.value.compassHeadingDegrees,
            cardinalDirection = _telemetry.value.cardinalDirection
        )
    }
}
