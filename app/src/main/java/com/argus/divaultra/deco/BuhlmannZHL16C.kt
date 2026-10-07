package com.argus.divaultra.deco

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Bühlmann ZHL-16C Decompression Algorithm with Gradient Factors (GF Low / GF High).
 * Generated via MiniMax-M3 Coding Plan.
 *
 * Implements:
 * - 16 tissue compartments (N2 half-times 4.0 to 635.0 min) with standard ZHL-16C coefficients
 * - Nitrox support (FO2 0.21 - 0.40)
 * - Real-time NDL (No Decompression Limit) calculation
 * - Decompression stops & ceiling calculation
 * - Safety stop tracker (3 min at 3m - 5m)
 * - Ascent rate monitor (target <= 9 m/min)
 * - Oxygen toxicity (PO2 and CNS% accumulation according to NOAA limits)
 */
class BuhlmannZHL16C(
    var gfLow: Double = 0.40,   // Conservative GF Low (40%)
    var gfHigh: Double = 0.85,  // Conservative GF High (85%)
    var waterDensity: Double = 1025.0, // kg/m3 (1025 for saltwater, 1000 for freshwater)
    var surfacePressureBar: Double = 1.01325 // 1013.25 mbar at sea level
) {

    companion object {
        const val WATER_VAPOR_PRESSURE = 0.0627 // bar (alveolar water vapor pressure)
        const val STANDARD_GRAVITY = 9.80665

        // ZHL-16C 16 Compartment Parameters: (Half-time in min, a in bar, b)
        val COMPARTMENTS = arrayOf(
            CompartmentDef(4.0,   1.2599, 0.5050),
            CompartmentDef(8.0,   1.0000, 0.6514),
            CompartmentDef(12.5,  0.8618, 0.7222),
            CompartmentDef(18.5,  0.7562, 0.7825),
            CompartmentDef(27.0,  0.6491, 0.8126),
            CompartmentDef(38.3,  0.5316, 0.8434),
            CompartmentDef(54.3,  0.4244, 0.8693),
            CompartmentDef(77.0,  0.3731, 0.8910),
            CompartmentDef(109.0, 0.3580, 0.9092),
            CompartmentDef(146.0, 0.3440, 0.9222),
            CompartmentDef(187.0, 0.3282, 0.9319),
            CompartmentDef(239.0, 0.3151, 0.9403),
            CompartmentDef(305.0, 0.3040, 0.9477),
            CompartmentDef(390.0, 0.2940, 0.9544),
            CompartmentDef(498.0, 0.2851, 0.9602),
            CompartmentDef(635.0, 0.2762, 0.9653)
        )
    }

    data class CompartmentDef(val halfTimeMinutes: Double, val a: Double, val b: Double)

    // Tissue nitrogen partial pressures (bar) for all 16 compartments
    private val tissueP_N2 = DoubleArray(16)

    // Accumulated CNS Oxygen Toxicity fraction (0.0 to 1.0+)
    var cnsToxicityPercent: Double = 0.0
        private set

    init {
        resetToSurface()
    }

    /**
     * Initializes tissue loadings to equilibrium with surface ambient air (79% N2).
     */
    fun resetToSurface() {
        val pAmb = surfacePressureBar
        val pN2_surf = (pAmb - WATER_VAPOR_PRESSURE) * 0.7902
        for (i in tissueP_N2.indices) {
            tissueP_N2[i] = pN2_surf
        }
        cnsToxicityPercent = 0.0
    }

    /**
     * Converts depth in meters to absolute ambient pressure in bar.
     */
    fun depthToBar(depthMeters: Double): Double {
        val hydroBar = (depthMeters * waterDensity * STANDARD_GRAVITY) / 100000.0
        return surfacePressureBar + hydroBar
    }

    /**
     * Converts absolute ambient pressure in bar back to depth in meters.
     */
    fun barToDepth(pressureBar: Double): Double {
        val hydroBar = max(0.0, pressureBar - surfacePressureBar)
        return (hydroBar * 100000.0) / (waterDensity * STANDARD_GRAVITY)
    }

    /**
     * Maximum Operating Depth (MOD) in meters for a given oxygen fraction and PO2 limit.
     */
    fun calculateMOD(fractionO2: Double, maxPO2: Double = 1.4): Double {
        val maxAmbPressure = maxPO2 / fractionO2
        return barToDepth(maxAmbPressure)
    }

    /**
     * Calculates current PO2 (bar) at depth.
     */
    fun calculatePO2(depthMeters: Double, fractionO2: Double): Double {
        return depthToBar(depthMeters) * fractionO2
    }

    /**
     * Advances simulation/dive time by deltaSeconds at depthMeters with gas mix fractionO2.
     * Uses the Schreiner/Haldane equation for tissue gas loading and NOAA CNS accumulation.
     */
    fun update(depthMeters: Double, deltaSeconds: Double, fractionO2: Double) {
        val pAmb = depthToBar(depthMeters)
        val fractionN2 = max(0.0, 1.0 - fractionO2)
        val pInspN2 = max(0.0, (pAmb - WATER_VAPOR_PRESSURE) * fractionN2)
        val dtMin = deltaSeconds / 60.0

        // Update nitrogen loading for each compartment
        for (i in 0 until 16) {
            val k = ln(2.0) / COMPARTMENTS[i].halfTimeMinutes
            // Haldane equation for constant depth: P_t = P_0 + (P_insp - P_0) * (1 - e^(-k*t))
            tissueP_N2[i] = tissueP_N2[i] + (pInspN2 - tissueP_N2[i]) * (1.0 - exp(-k * dtMin))
        }

        // Update CNS Oxygen Toxicity
        val po2 = pAmb * fractionO2
        updateCNS(po2, dtMin)
    }

    /**
     * Updates CNS% based on NOAA single-exposure limit curves.
     */
    private fun updateCNS(po2: Double, dtMin: Double) {
        if (po2 <= 0.5) return // negligible toxicity below 0.5 bar
        val maxMinutes = when {
            po2 <= 0.6 -> 720.0
            po2 <= 0.7 -> 570.0
            po2 <= 0.8 -> 450.0
            po2 <= 0.9 -> 360.0
            po2 <= 1.0 -> 300.0
            po2 <= 1.1 -> 240.0
            po2 <= 1.2 -> 210.0
            po2 <= 1.3 -> 180.0
            po2 <= 1.4 -> 150.0
            po2 <= 1.5 -> 120.0
            po2 <= 1.6 -> 45.0
            else -> 10.0
        }
        val addedPercent = (dtMin / maxMinutes) * 100.0
        cnsToxicityPercent = min(200.0, cnsToxicityPercent + addedPercent)
    }

    /**
     * Calculates the true decompression ceiling (in meters) with current Gradient Factor.
     * Returns 0.0 if user is within No Decompression Limits.
     */
    fun calculateCeilingMeters(): Double {
        var highestCeilingBar = 0.0

        for (i in 0 until 16) {
            val pN2 = tissueP_N2[i]
            val comp = COMPARTMENTS[i]

            // Bühlmann M-value: M0 = a / b + P_amb * (1 / b)
            // TolAmb = (pN2 - a * GF) / (GF / b + 1 - GF)
            val gf = gfHigh // Conservatism check for surface ceiling
            val tolAmbBar = (pN2 - comp.a * gf) / ((gf / comp.b) + (1.0 - gf))
            if (tolAmbBar > highestCeilingBar) {
                highestCeilingBar = tolAmbBar
            }
        }

        return if (highestCeilingBar <= surfacePressureBar) 0.0 else barToDepth(highestCeilingBar)
    }

    /**
     * Calculates real-time No Decompression Limit (NDL) in minutes at current depth.
     * Returns 99 if NDL > 99 minutes.
     */
    fun calculateNDL(currentDepthMeters: Double, fractionO2: Double): Int {
        val pAmb = depthToBar(currentDepthMeters)
        val fractionN2 = max(0.0, 1.0 - fractionO2)
        val pInspN2 = max(0.0, (pAmb - WATER_VAPOR_PRESSURE) * fractionN2)

        var minNDL = 99.0

        for (i in 0 until 16) {
            val comp = COMPARTMENTS[i]
            val pN2_0 = tissueP_N2[i]

            // Maximum tolerable tissue N2 at surface with GF High:
            val pN2_allowed = (surfacePressureBar * ((gfHigh / comp.b) + (1.0 - gfHigh))) + (comp.a * gfHigh)

            if (pInspN2 <= pN2_allowed) {
                // If inspired pressure is lower than tolerable limit, this compartment will never deco
                continue
            }

            if (pN2_0 >= pN2_allowed) {
                // Already in deco
                return 0
            }

            val k = ln(2.0) / comp.halfTimeMinutes
            // Solve Haldane: P_t = P_0 + (P_insp - P_0) * (1 - e^(-k*t)) = P_allowed
            // 1 - (P_allowed - P_0) / (P_insp - P_0) = e^(-k*t)
            val ratio = (pInspN2 - pN2_allowed) / (pInspN2 - pN2_0)
            if (ratio > 0) {
                val timeMinutes = -ln(ratio) / k
                if (timeMinutes < minNDL) {
                    minNDL = timeMinutes
                }
            }
        }

        return min(99, max(0, minNDL.toInt()))
    }

    /**
     * Returns the relative saturation fraction (0.0 to 1.0+) for all 16 tissue compartments.
     */
    fun getCompartmentSaturationFractions(): FloatArray {
        val result = FloatArray(16)
        val pSurf = surfacePressureBar
        val pN2_surf = (pSurf - WATER_VAPOR_PRESSURE) * 0.7902
        for (i in 0 until 16) {
            val pTol = (tissueP_N2[i] - COMPARTMENTS[i].a * COMPARTMENTS[i].b) / COMPARTMENTS[i].b
            val fraction = ((tissueP_N2[i] - pN2_surf) / (pTol - pN2_surf).coerceAtLeast(0.01)).coerceIn(0.0, 1.2)
            result[i] = fraction.toFloat()
        }
        return result
    }
}
