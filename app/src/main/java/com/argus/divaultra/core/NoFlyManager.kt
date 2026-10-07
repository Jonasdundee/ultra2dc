package com.argus.divaultra.core

import android.content.Context
import com.argus.divaultra.log.DiveLogManager
import com.argus.divaultra.log.DiveLogSummary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

data class NoFlyStatus(
    val isNoFlyActive: Boolean,
    val requiredHours: Int, // 0, 12, 18, 24
    val remainingSeconds: Long,
    val formattedCountdown: String, // "17:42:15"
    val flyPermittedTimestampMs: Long,
    val formattedFlyPermittedTime: String, // "Tomorrow 07:56" or "Today 18:30"
    val reason: String, // "Single Dive (12h)", "Repetitive (18h)", "Deco Required (24h)", "OK to Fly"
    val divesCountInWindow: Int,
    val surfaceIntervalSeconds: Long,
    val formattedSurfaceInterval: String, // "08:12"
    val progressFraction: Float, // 0.0f (just surfaced) to 1.0f (safe to fly)
    val lastDiveMaxDepthMeters: Double = 0.0,
    val lastDiveDurationSeconds: Long = 0L,
    val lastDiveGasMix: String = "AIR"
)

class NoFlyManager(
    private val context: Context,
    private val logManager: DiveLogManager
) {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }
    private val fileIdFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }
    private val displayTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val displayDateTimeFormat = SimpleDateFormat("EEE HH:mm", Locale.getDefault())

    fun calculateStatus(
        currentPhase: DivePhase = DivePhase.SURFACE,
        currentDiveDurationSeconds: Long = 0L
    ): NoFlyStatus {
        val nowMs = System.currentTimeMillis()

        // If currently submerged/diving, flying is strictly prohibited!
        if (currentPhase == DivePhase.DIVING || currentPhase == DivePhase.SAFETY_STOP || currentPhase == DivePhase.DECO_STOP) {
            return NoFlyStatus(
                isNoFlyActive = true,
                requiredHours = 18,
                remainingSeconds = 18 * 3600L,
                formattedCountdown = "UNDERWATER",
                flyPermittedTimestampMs = nowMs + (18 * 3600 * 1000L),
                formattedFlyPermittedTime = "IN PROGRESS",
                reason = "ACTIVE DIVE IN PROGRESS",
                divesCountInWindow = 1,
                surfaceIntervalSeconds = 0L,
                formattedSurfaceInterval = "00:00",
                progressFraction = 0.0f
            )
        }

        val allLogs = logManager.getAllDiveLogs()
        if (allLogs.isEmpty()) {
            return NoFlyStatus(
                isNoFlyActive = false,
                requiredHours = 0,
                remainingSeconds = 0L,
                formattedCountdown = "00:00:00",
                flyPermittedTimestampMs = nowMs,
                formattedFlyPermittedTime = "SAFE NOW",
                reason = "NO RECENT DIVES",
                divesCountInWindow = 0,
                surfaceIntervalSeconds = 0L,
                formattedSurfaceInterval = "--:--",
                progressFraction = 1.0f
            )
        }

        // Parse dive end timestamps for all logs
        data class ResolvedDive(
            val summary: DiveLogSummary,
            val startTimeMs: Long,
            val endTimeMs: Long
        )

        val resolvedDives = mutableListOf<ResolvedDive>()
        for (log in allLogs) {
            var startMs = 0L
            // Try parsing diveId: DIVE_yyyyMMdd_HHmmss
            if (log.diveId.startsWith("DIVE_")) {
                val cleanId = log.diveId.removePrefix("DIVE_")
                try {
                    val d = fileIdFormat.parse(cleanId)
                    if (d != null) startMs = d.time
                } catch (ignored: Exception) {}
            }

            // Fallback: parse startTimeFormatted
            if (startMs == 0L) {
                try {
                    val d = isoFormat.parse(log.startTimeFormatted)
                    if (d != null) startMs = d.time
                } catch (ignored: Exception) {}
            }

            if (startMs > 0L) {
                val endMs = startMs + (log.durationSeconds * 1000L)
                resolvedDives.add(ResolvedDive(log, startMs, endMs))
            }
        }

        if (resolvedDives.isEmpty()) {
            return NoFlyStatus(
                isNoFlyActive = false,
                requiredHours = 0,
                remainingSeconds = 0L,
                formattedCountdown = "00:00:00",
                flyPermittedTimestampMs = nowMs,
                formattedFlyPermittedTime = "SAFE NOW",
                reason = "NO RECENT DIVES",
                divesCountInWindow = 0,
                surfaceIntervalSeconds = 0L,
                formattedSurfaceInterval = "--:--",
                progressFraction = 1.0f
            )
        }

        // Sort latest first
        resolvedDives.sortByDescending { it.endTimeMs }
        val latestDive = resolvedDives.first()
        val latestEndMs = latestDive.endTimeMs

        // Dives within 48 hours of now
        val window48hMs = 48L * 3600L * 1000L
        val window24hMs = 24L * 3600L * 1000L
        val divesInPast48h = resolvedDives.filter { nowMs - it.endTimeMs in 0L..window48hMs }
        val divesInPast24h = resolvedDives.filter { nowMs - it.endTimeMs in 0L..window24hMs }

        val divesCount = max(1, divesInPast24h.size)

        // PADI / DAN Flying After Diving Guidelines:
        // 1. Single no-decompression dive: 12 hours minimum
        // 2. Repetitive dives in a single day or daily repetitive dives: 18 hours minimum
        // 3. Decompression dives (missed or required staged deco): 24 hours minimum
        val requiredHours = when {
            // Check for decompression stop requirement (e.g. max depth > 40m or ceiling violation)
            divesInPast48h.any { it.summary.maxDepthMeters > 40.0 } -> 24
            divesInPast24h.size >= 2 -> 18 // Repetitive dives in 24 hours (Jonas today: 2 dives!)
            divesInPast48h.size >= 2 -> 18 // Multiple days of diving
            else -> 12 // Single no-deco dive
        }

        val requiredIntervalMs = requiredHours * 3600L * 1000L
        val elapsedSinceLastDiveMs = max(0L, nowMs - latestEndMs)
        val remainingMs = max(0L, requiredIntervalMs - elapsedSinceLastDiveMs)
        val remainingSeconds = remainingMs / 1000L
        val flyPermittedTimestampMs = latestEndMs + requiredIntervalMs

        val surfaceIntervalSec = elapsedSinceLastDiveMs / 1000L
        val surfHours = surfaceIntervalSec / 3600
        val surfMinutes = (surfaceIntervalSec % 3600) / 60
        val formattedSurface = String.format(Locale.US, "%02d:%02d", surfHours, surfMinutes)

        val remHours = remainingSeconds / 3600
        val remMinutes = (remainingSeconds % 3600) / 60
        val remSec = remainingSeconds % 60
        val formattedCountdown = String.format(Locale.US, "%02d:%02d:%02d", remHours, remMinutes, remSec)

        // Format permitted fly time
        val permittedCal = Calendar.getInstance().apply { timeInMillis = flyPermittedTimestampMs }
        val todayCal = Calendar.getInstance()
        val formattedPermitted = if (permittedCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)) {
            "Today " + displayTimeFormat.format(Date(flyPermittedTimestampMs))
        } else {
            displayDateTimeFormat.format(Date(flyPermittedTimestampMs))
        }

        val reason = when (requiredHours) {
            24 -> "DECO DIVE (24H RULE)"
            18 -> if (divesInPast24h.size >= 2) "REPETITIVE (${divesInPast24h.size} DIVES TODAY)" else "MULTI-DAY REPETITIVE"
            12 -> "SINGLE NO-DECO DIVE (12H)"
            else -> "OK TO FLY"
        }

        val progressFraction = if (requiredIntervalMs > 0) {
            (elapsedSinceLastDiveMs.toFloat() / requiredIntervalMs.toFloat()).coerceIn(0.0f, 1.0f)
        } else 1.0f

        val isNoFlyActive = remainingSeconds > 0

        return NoFlyStatus(
            isNoFlyActive = isNoFlyActive,
            requiredHours = requiredHours,
            remainingSeconds = remainingSeconds,
            formattedCountdown = if (isNoFlyActive) formattedCountdown else "00:00:00",
            flyPermittedTimestampMs = flyPermittedTimestampMs,
            formattedFlyPermittedTime = if (isNoFlyActive) formattedPermitted else "OK TO FLY",
            reason = if (isNoFlyActive) reason else "DESAT COMPLETE",
            divesCountInWindow = divesCount,
            surfaceIntervalSeconds = surfaceIntervalSec,
            formattedSurfaceInterval = formattedSurface,
            progressFraction = progressFraction,
            lastDiveMaxDepthMeters = latestDive.summary.maxDepthMeters,
            lastDiveDurationSeconds = latestDive.summary.durationSeconds,
            lastDiveGasMix = latestDive.summary.gasMix
        )
    }
}
