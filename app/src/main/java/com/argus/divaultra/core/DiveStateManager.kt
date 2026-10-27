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
    COMPLETED,
    LOCKOUT_GAUGE
}

enum class SafetyStopStatus {
    NOT_REQUIRED,
    REQUIRED_PENDING,     // Requirement triggered (>10m or NDL<15), awaiting ascent to stop zone
    IN_STOP_COUNTING,     // Diver in 3.0m - 6.0m window, timer counting down
    PAUSED_TOO_DEEP,      // Diver descended below 6.0m, timer paused
    PAUSED_TOO_SHALLOW,   // Diver drifted above 2.8m, alert to descend
    COMPLETED,            // Countdown hit 0:00, clear to surface
    VIOLATED              // Surfaced without completing required safety stop
}

enum class AscentRateStatus {
    OPTIMAL,   // 0 - 8 m/min (Green)
    CAUTION,   // 8 - 10 m/min (Yellow)
    DANGER,    // 10 - 12 m/min (Red & Haptic alert)
    CRITICAL   // > 12 m/min (Extreme rapid ascent alarm)
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
    // Nitrox & Decompression Metrics (Default is 21% Air as per safety guidelines)
    val fractionO2: Double = 0.21,
    val isGasConfirmed: Boolean = false,
    val currentPO2: Double = 0.21,
    val modMeters: Double = 56.6,
    val isModWarning: Boolean = false,
    val isModExceeded: Boolean = false,
    val cnsPercent: Double = 0.0,
    val accumulatedOTU: Double = 0.0,
    // Lockout & Safety Violations
    val isLockoutActive: Boolean = false,
    val lockoutReason: String = "",
    val lockoutRemainingSeconds: Long = 0L,
    val hasMissedSafetyStop: Boolean = false,
    val hasMissedDecompression: Boolean = false,
    // Battery & Hardware Safety
    val batteryLevelPercent: Int = 100,
    val isBatteryLow: Boolean = false,       // <= 20%
    val isBatteryCritical: Boolean = false,  // <= 10%
    // Tactical Compass Heading
    val compassHeadingDegrees: Float = 245f,
    val cardinalDirection: String = "SW",
    // Surface Interval Hysteresis (5 minutes countdown upon surfacing)
    val surfaceIntervalRemainingSeconds: Int = 300,
    // Tactile Button & Underwater HUD States
    val underwaterScreenIndex: Int = 0,
    val isBacklightBoosted: Boolean = false,
    val lockedBearingDegrees: Float? = null,
    val tissueSaturations: FloatArray = FloatArray(16) { 0.05f }
)

class DiveStateManager(
    val decoEngine: BuhlmannZHL16C = BuhlmannZHL16C(),
    private var persistenceManager: TissuePersistenceManager? = null
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

    fun attachPersistence(pm: TissuePersistenceManager) {
        this.persistenceManager = pm
        val restored = pm.loadAndRestoreState(decoEngine)
        _telemetry.value = _telemetry.value.copy(
            isLockoutActive = restored.isLockoutActive,
            lockoutReason = restored.lockoutReason,
            lockoutRemainingSeconds = restored.lockoutRemainingSeconds,
            fractionO2 = restored.fractionO2,
            isGasConfirmed = restored.isGasConfirmed,
            modMeters = decoEngine.calculateMOD(restored.fractionO2, 1.4),
            cnsPercent = restored.restoredCns,
            accumulatedOTU = restored.restoredOtu,
            tissueSaturations = decoEngine.getCompartmentSaturationFractions()
        )
    }

    /**
     * Sets and confirms gas mix with analysed O2 confirmation.
     */
    fun setGasMix(fractionO2: Double, isConfirmed: Boolean = true) {
        val mod = decoEngine.calculateMOD(fractionO2, 1.4)
        persistenceManager?.setGasConfirmed(isConfirmed)
        _telemetry.value = _telemetry.value.copy(
            fractionO2 = fractionO2,
            isGasConfirmed = isConfirmed,
            modMeters = mod
        )
    }

    /**
     * Updates battery telemetry and evaluates safety thresholds.
     */
    fun updateBatteryLevel(levelPercent: Int) {
        val isLow = levelPercent <= 20
        val isCritical = levelPercent <= 10
        _telemetry.value = _telemetry.value.copy(
            batteryLevelPercent = levelPercent,
            isBatteryLow = isLow,
            isBatteryCritical = isCritical
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
     * Process high-frequency sensor depth readings (~10Hz).
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

            // If user did not explicitly confirm Nitrox mix before descending, default safely to AIR (21%)
            if (!current.isGasConfirmed && current.fractionO2 > 0.21) {
                setGasMix(0.21, isConfirmed = false)
            }
        } else if (isSubmerged && depthMeters >= 1.2 && current.phase == DivePhase.SURFACING) {
            // Diver redescended during 5-minute surface interval: Resume dive!
            surfaceIntervalRemainingSec = 300
        } else if (isSubmerged && depthMeters < 0.5 && current.diveTimeSeconds > 10) {
            // Diver surfaced: evaluate any missed stop violations
            var missedDeco = current.hasMissedDecompression
            var missedSafety = current.hasMissedSafetyStop

            if (current.ceilingMeters >= 1.0) {
                missedDeco = true
                persistenceManager?.triggerLockout("MISSED DECOMPRESSION OBLIGATION - 24H GAUGE LOCKOUT", 24)
            } else if (isSafetyStopTriggered && !safetyStopCompleted) {
                missedSafety = true
            }

            _telemetry.value = current.copy(
                phase = if (current.isLockoutActive) DivePhase.LOCKOUT_GAUGE else DivePhase.SURFACING,
                currentDepthMeters = depthMeters,
                waterTemperatureCelsius = temperatureCelsius,
                surfaceIntervalRemainingSeconds = surfaceIntervalRemainingSec,
                hasMissedDecompression = missedDeco,
                hasMissedSafetyStop = missedSafety,
                safetyStopStatus = if (missedSafety) SafetyStopStatus.VIOLATED else current.safetyStopStatus
            )
            return
        }

        if (!isSubmerged) {
            val isLockout = persistenceManager?.isLockoutActive() ?: false
            val lockoutSec = persistenceManager?.getLockoutRemainingSeconds() ?: 0L
            val lockoutReason = if (isLockout) persistenceManager?.getLockoutReason() ?: "" else ""

            _telemetry.value = current.copy(
                phase = if (isLockout) DivePhase.LOCKOUT_GAUGE else DivePhase.SURFACE,
                currentDepthMeters = depthMeters,
                waterTemperatureCelsius = temperatureCelsius,
                currentPO2 = decoEngine.calculatePO2(depthMeters, current.fractionO2),
                ascentRateMetersPerMin = 0.0,
                ascentRateStatus = AscentRateStatus.OPTIMAL,
                isLockoutActive = isLockout,
                lockoutReason = lockoutReason,
                lockoutRemainingSeconds = lockoutSec
            )
            return
        }

        // 2. High-precision ascent rate with low-pass filter
        val dtSec = if (lastDepthTimestampMs > 0L) (nowMs - lastDepthTimestampMs) / 1000.0 else 0.0
        if (dtSec >= 0.25) {
            val rawRate = ((previousDepth - depthMeters) / dtSec) * 60.0
            smoothedAscentRate = (smoothedAscentRate * 0.65) + (rawRate * 0.35)
            previousDepth = depthMeters
            lastDepthTimestampMs = nowMs
        }

        val ascentStatus = when {
            smoothedAscentRate > 12.0 -> AscentRateStatus.CRITICAL
            smoothedAscentRate > 10.0 -> AscentRateStatus.DANGER
            smoothedAscentRate >= 8.0 -> AscentRateStatus.CAUTION
            else -> AscentRateStatus.OPTIMAL
        }

        maxDepthSeen = max(maxDepthSeen, depthMeters)
        depthSum += depthMeters
        depthSampleCount++
        val avgDepth = depthSum / depthSampleCount

        // MOD Warning and Violation Checks
        val isModNear = depthMeters >= (current.modMeters - 2.0)
        val isModOver = depthMeters >= current.modMeters

        _telemetry.value = current.copy(
            currentDepthMeters = depthMeters,
            maxDepthMeters = maxDepthSeen,
            averageDepthMeters = avgDepth,
            waterTemperatureCelsius = temperatureCelsius,
            ascentRateMetersPerMin = max(0.0, smoothedAscentRate),
            ascentRateStatus = ascentStatus,
            isModWarning = isModNear,
            isModExceeded = isModOver
        )
    }

    /**
     * Dedicated 1-Second Clock Ticker: Called strictly once per real-world second.
     * Advances dive timer, Bühlmann decompression calculations, safety stop, and state persistence.
     */
    fun onOneSecondTick() {
        val current = _telemetry.value
        val isLockout = persistenceManager?.isLockoutActive() ?: false
        val lockoutSec = persistenceManager?.getLockoutRemainingSeconds() ?: 0L

        if (isLockout != current.isLockoutActive || lockoutSec != current.lockoutRemainingSeconds) {
            _telemetry.value = current.copy(
                isLockoutActive = isLockout,
                lockoutRemainingSeconds = lockoutSec,
                lockoutReason = if (isLockout) persistenceManager?.getLockoutReason() ?: "" else ""
            )
        }

        if (!isSubmerged) {
            // Continuously offgas at surface every second if tissues are loaded
            decoEngine.desaturateSurface(1.0 / 60.0)
            return
        }

        // Surface interval countdown handler (5 minutes auto-end window)
        if (current.phase == DivePhase.SURFACING) {
            surfaceIntervalRemainingSec--
            if (surfaceIntervalRemainingSec <= 0) {
                isSubmerged = false
                _telemetry.value = current.copy(
                    phase = DivePhase.COMPLETED,
                    surfaceIntervalRemainingSeconds = 0
                )
                // Default gas back to AIR (21%) after dive session concludes
                setGasMix(0.21, isConfirmed = false)
            } else {
                _telemetry.value = current.copy(
                    surfaceIntervalRemainingSeconds = surfaceIntervalRemainingSec
                )
            }
            persistenceManager?.saveState(
                telemetry = _telemetry.value,
                tissues = decoEngine.getTissueP_N2(),
                cns = decoEngine.cnsToxicityPercent,
                otu = decoEngine.accumulatedOTU,
                isSubmerged = isSubmerged
            )
            return
        }

        val depthMeters = current.currentDepthMeters

        // 1. Advance dive time by exactly 1 real second
        val newDiveTime = current.diveTimeSeconds + 1L

        // 2. Advance Bühlmann ZHL-16C by exactly 1.0 second
        decoEngine.update(depthMeters, 1.0, current.fractionO2)
        val ndl = if (isLockout) 0 else decoEngine.calculateNDL(depthMeters, current.fractionO2)
        val rawCeiling = decoEngine.calculateCeilingMeters()
        val po2 = decoEngine.calculatePO2(depthMeters, current.fractionO2)
        val cns = decoEngine.cnsToxicityPercent
        val otu = decoEngine.accumulatedOTU

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

        // 4. State Machine: Deco vs Safety Stop vs Active Diving vs Lockout
        var safetyStatus = current.safetyStopStatus
        var phase = if (isLockout) DivePhase.LOCKOUT_GAUGE else DivePhase.DIVING

        if (!isLockout) {
            if (isTrueDeco) {
                phase = DivePhase.DECO_STOP
            } else if (isSafetyStopTriggered) {
                if (safetyStopCompleted) {
                    if (depthMeters >= 9.0) {
                        safetyStopCompleted = false
                        safetyStopRemainingSec = safetyStopTotalSec
                        safetyStatus = SafetyStopStatus.REQUIRED_PENDING
                    } else {
                        safetyStatus = SafetyStopStatus.COMPLETED
                    }
                } else {
                    when {
                        depthMeters in 3.0..6.0 -> {
                            phase = DivePhase.SAFETY_STOP
                            safetyStatus = SafetyStopStatus.IN_STOP_COUNTING
                            safetyStopRemainingSec = max(0, safetyStopRemainingSec - 1)
                            if (safetyStopRemainingSec == 0) {
                                safetyStopCompleted = true
                                safetyStatus = SafetyStopStatus.COMPLETED
                            }
                        }
                        depthMeters < 2.8 && safetyStopRemainingSec < safetyStopTotalSec -> {
                            phase = DivePhase.SAFETY_STOP
                            safetyStatus = SafetyStopStatus.PAUSED_TOO_SHALLOW
                        }
                        depthMeters > 6.0 && safetyStopRemainingSec < safetyStopTotalSec && depthMeters < 9.0 -> {
                            phase = DivePhase.SAFETY_STOP
                            safetyStatus = SafetyStopStatus.PAUSED_TOO_DEEP
                        }
                        else -> {
                            safetyStatus = SafetyStopStatus.REQUIRED_PENDING
                        }
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
            accumulatedOTU = otu,
            isSafetyStopRequired = isSafetyStopTriggered,
            safetyStopStatus = safetyStatus,
            safetyStopTotalSeconds = safetyStopTotalSec,
            safetyStopRemainingSeconds = safetyStopRemainingSec,
            tissueSaturations = decoEngine.getCompartmentSaturationFractions()
        )

        // Continuous State Persistence (every second while diving)
        persistenceManager?.saveState(
            telemetry = _telemetry.value,
            tissues = decoEngine.getTissueP_N2(),
            cns = cns,
            otu = otu,
            isSubmerged = isSubmerged
        )
    }

    fun cycleUnderwaterScreen() {
        val next = (_telemetry.value.underwaterScreenIndex + 1) % 4
        _telemetry.value = _telemetry.value.copy(underwaterScreenIndex = next)
    }

    fun toggleBacklightBoost() {
        val next = !_telemetry.value.isBacklightBoosted
        _telemetry.value = _telemetry.value.copy(isBacklightBoosted = next)
    }

    fun toggleBearingLock() {
        val current = _telemetry.value.lockedBearingDegrees
        val heading = _telemetry.value.compassHeadingDegrees
        val next = if (current == null) heading else null
        _telemetry.value = _telemetry.value.copy(lockedBearingDegrees = next)
    }

    fun endDiveNow() {
        if (isSubmerged || _telemetry.value.phase == DivePhase.SURFACING) {
            isSubmerged = false
            _telemetry.value = _telemetry.value.copy(
                phase = DivePhase.COMPLETED,
                surfaceIntervalRemainingSeconds = 0
            )
            persistenceManager?.saveState(
                telemetry = _telemetry.value,
                tissues = decoEngine.getTissueP_N2(),
                cns = decoEngine.cnsToxicityPercent,
                otu = decoEngine.accumulatedOTU,
                isSubmerged = false
            )
        }
    }

    fun resetToSurface() {
        isSubmerged = false
        surfaceIntervalRemainingSec = 300
        _telemetry.value = DiveTelemetry(
            fractionO2 = 0.21, // Always default back to AIR
            isGasConfirmed = false,
            modMeters = decoEngine.calculateMOD(0.21, 1.4),
            compassHeadingDegrees = _telemetry.value.compassHeadingDegrees,
            cardinalDirection = _telemetry.value.cardinalDirection,
            batteryLevelPercent = _telemetry.value.batteryLevelPercent,
            isBatteryLow = _telemetry.value.isBatteryLow,
            isBatteryCritical = _telemetry.value.isBatteryCritical,
            isLockoutActive = persistenceManager?.isLockoutActive() ?: false,
            lockoutReason = persistenceManager?.getLockoutReason() ?: "",
            lockoutRemainingSeconds = persistenceManager?.getLockoutRemainingSeconds() ?: 0L
        )
    }
}
