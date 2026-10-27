package com.argus.divaultra.core

import android.content.Context
import android.content.SharedPreferences
import com.argus.divaultra.deco.BuhlmannZHL16C
import kotlin.math.max

data class RestoredState(
    val wasCrashInterrupted: Boolean,
    val isLockoutActive: Boolean,
    val lockoutReason: String,
    val lockoutRemainingSeconds: Long,
    val fractionO2: Double,
    val isGasConfirmed: Boolean,
    val restoredTissues: DoubleArray,
    val restoredCns: Double,
    val restoredOtu: Double,
    val surfaceIntervalSeconds: Long,
    val eulaAccepted: Boolean
)

class TissuePersistenceManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "argus_dive_persistence"
        private const val KEY_TISSUE_P_N2 = "tissue_p_n2"
        private const val KEY_CNS_PERCENT = "cns_percent"
        private const val KEY_ACCUMULATED_OTU = "accumulated_otu"
        private const val KEY_LAST_UPDATE_MS = "last_update_ms"
        private const val KEY_WAS_SUBMERGED = "was_submerged"
        private const val KEY_DIVE_PHASE = "dive_phase"
        private const val KEY_FRACTION_O2 = "fraction_o2"
        private const val KEY_GAS_CONFIRMED = "gas_confirmed"
        private const val KEY_LOCKOUT_ACTIVE = "lockout_active"
        private const val KEY_LOCKOUT_REASON = "lockout_reason"
        private const val KEY_LOCKOUT_EXPIRY_MS = "lockout_expiry_ms"
        private const val KEY_EULA_ACCEPTED = "eula_accepted"
        private const val KEY_EULA_ACCEPTED_MS = "eula_accepted_ms"
    }

    fun isEulaAccepted(): Boolean = prefs.getBoolean(KEY_EULA_ACCEPTED, false)

    fun setEulaAccepted(accepted: Boolean) {
        prefs.edit()
            .putBoolean(KEY_EULA_ACCEPTED, accepted)
            .putLong(KEY_EULA_ACCEPTED_MS, System.currentTimeMillis())
            .apply()
    }

    fun isLockoutActive(): Boolean {
        val active = prefs.getBoolean(KEY_LOCKOUT_ACTIVE, false)
        if (!active) return false
        val expiryMs = prefs.getLong(KEY_LOCKOUT_EXPIRY_MS, 0L)
        if (System.currentTimeMillis() >= expiryMs) {
            clearLockout()
            return false
        }
        return true
    }

    fun getLockoutReason(): String = prefs.getString(KEY_LOCKOUT_REASON, "SAFETY LOCKOUT ACTIVE") ?: "SAFETY LOCKOUT ACTIVE"

    fun getLockoutRemainingSeconds(): Long {
        val expiryMs = prefs.getLong(KEY_LOCKOUT_EXPIRY_MS, 0L)
        val nowMs = System.currentTimeMillis()
        return max(0L, (expiryMs - nowMs) / 1000L)
    }

    fun triggerLockout(reason: String, durationHours: Int = 24) {
        val expiryMs = System.currentTimeMillis() + (durationHours * 3600 * 1000L)
        prefs.edit()
            .putBoolean(KEY_LOCKOUT_ACTIVE, true)
            .putString(KEY_LOCKOUT_REASON, reason)
            .putLong(KEY_LOCKOUT_EXPIRY_MS, expiryMs)
            .apply()
    }

    fun clearLockout() {
        prefs.edit()
            .putBoolean(KEY_LOCKOUT_ACTIVE, false)
            .putString(KEY_LOCKOUT_REASON, "")
            .putLong(KEY_LOCKOUT_EXPIRY_MS, 0L)
            .apply()
    }

    fun setGasConfirmed(confirmed: Boolean) {
        prefs.edit().putBoolean(KEY_GAS_CONFIRMED, confirmed).apply()
    }

    fun isGasConfirmed(): Boolean = prefs.getBoolean(KEY_GAS_CONFIRMED, false)

    /**
     * Persists real-time engine state continuously (called every 5-10s and on state changes).
     */
    fun saveState(
        telemetry: DiveTelemetry,
        tissues: DoubleArray,
        cns: Double,
        otu: Double,
        isSubmerged: Boolean
    ) {
        val tissueString = tissues.joinToString(",") { it.toString() }
        prefs.edit()
            .putString(KEY_TISSUE_P_N2, tissueString)
            .putFloat(KEY_CNS_PERCENT, cns.toFloat())
            .putFloat(KEY_ACCUMULATED_OTU, otu.toFloat())
            .putLong(KEY_LAST_UPDATE_MS, System.currentTimeMillis())
            .putBoolean(KEY_WAS_SUBMERGED, isSubmerged)
            .putString(KEY_DIVE_PHASE, telemetry.phase.name)
            .putFloat(KEY_FRACTION_O2, telemetry.fractionO2.toFloat())
            .apply()
    }

    /**
     * Restores state on application startup.
     * Evaluates whether the app suffered an unexpected interruption mid-dive (crash / reboot).
     * If safely on surface, calculates offgassing for elapsed time.
     */
    fun loadAndRestoreState(decoEngine: BuhlmannZHL16C): RestoredState {
        val eulaAccepted = isEulaAccepted()
        val wasSubmerged = prefs.getBoolean(KEY_WAS_SUBMERGED, false)
        val lastUpdateMs = prefs.getLong(KEY_LAST_UPDATE_MS, 0L)
        val nowMs = System.currentTimeMillis()
        val elapsedMs = if (lastUpdateMs > 0L) max(0L, nowMs - lastUpdateMs) else 0L
        val elapsedMinutes = elapsedMs / 1000.0 / 60.0

        val rawTissuesStr = prefs.getString(KEY_TISSUE_P_N2, null)
        val savedTissues = if (!rawTissuesStr.isNullOrEmpty()) {
            try {
                rawTissuesStr.split(",").map { it.toDouble() }.toDoubleArray()
            } catch (e: Exception) {
                null
            }
        } else null

        val savedCns = prefs.getFloat(KEY_CNS_PERCENT, 0f).toDouble()
        val savedOtu = prefs.getFloat(KEY_ACCUMULATED_OTU, 0f).toDouble()
        var fractionO2 = prefs.getFloat(KEY_FRACTION_O2, 0.21f).toDouble()
        if (fractionO2 < 0.21 || fractionO2 > 0.40) fractionO2 = 0.21

        var wasCrashInterrupted = false

        // Check if an existing lockout is already active
        if (isLockoutActive()) {
            // Already locked out
        } else if (wasSubmerged && elapsedMs < 12 * 3600 * 1000L) {
            // CRITICAL FAILURE MODE HANDLER:
            // App was killed, watch rebooted, or battery died while diver was underwater!
            // Default to conservative safety lockout rather than a clean zero-nitrogen start.
            wasCrashInterrupted = true
            triggerLockout("APP INTERRUPTED MID-DIVE (CRASH/BATTERY LOSS) - GAUGE ONLY", 24)
        }

        // Restore tissue loadings with surface desaturation
        if (savedTissues != null && savedTissues.size == 16) {
            decoEngine.setTissueP_N2(savedTissues)
            decoEngine.setCnsToxicity(savedCns)
            decoEngine.setAccumulatedOTUValue(savedOtu)

            // If app was safely on the surface, desaturate for the elapsed real-world time
            if (!wasSubmerged && elapsedMinutes > 0.0) {
                decoEngine.desaturateSurface(elapsedMinutes)
            }
        } else {
            decoEngine.resetToSurface()
        }

        val lockoutActive = isLockoutActive()
        val lockoutReason = if (lockoutActive) getLockoutReason() else ""
        val lockoutRemainingSec = if (lockoutActive) getLockoutRemainingSeconds() else 0L

        // Default to Air (21%) unless confirmed for this session
        val isGasConfirmed = isGasConfirmed()
        val effectiveFractionO2 = if (isGasConfirmed) fractionO2 else 0.21

        return RestoredState(
            wasCrashInterrupted = wasCrashInterrupted,
            isLockoutActive = lockoutActive,
            lockoutReason = lockoutReason,
            lockoutRemainingSeconds = lockoutRemainingSec,
            fractionO2 = effectiveFractionO2,
            isGasConfirmed = isGasConfirmed,
            restoredTissues = decoEngine.getTissueP_N2(),
            restoredCns = decoEngine.cnsToxicityPercent,
            restoredOtu = decoEngine.accumulatedOTU,
            surfaceIntervalSeconds = (elapsedMs / 1000L),
            eulaAccepted = eulaAccepted
        )
    }
}
