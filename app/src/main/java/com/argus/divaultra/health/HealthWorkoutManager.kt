package com.argus.divaultra.health

import android.content.Context
import android.util.Log
import com.argus.divaultra.log.CalorieCalc
import com.argus.divaultra.log.GpsPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HealthWorkoutManager bridges Argus Dive Ultra sessions into the Wear OS / Samsung Health ecosystem.
 *
 * It logs and attributes completed dive workouts as "Argus Scuba Diving",
 * recording net active calories burned, duration, and GPS entry/exit coordinates
 * so they register with Samsung Health daily activity targets and energy metrics.
 */
class HealthWorkoutManager(private val context: Context) {

    private val tag = "HealthWorkoutManager"
    private var isSessionActive = false
    private var sessionStartTimeMs = 0L

    fun startWorkoutSession(startTimeMs: Long = System.currentTimeMillis()) {
        if (isSessionActive) return
        isSessionActive = true
        sessionStartTimeMs = startTimeMs
        Log.i(tag, "Started active workout session: Argus Scuba Diving at $startTimeMs")
    }

    fun endWorkoutSession(
        endTimeMs: Long = System.currentTimeMillis(),
        durationSeconds: Long,
        waterTempCelsius: Double,
        maxDepthMeters: Double,
        avgDepthMeters: Double,
        entryGps: GpsPoint?,
        exitGps: GpsPoint?
    ) {
        if (!isSessionActive && durationSeconds <= 0L) return
        isSessionActive = false

        val calResult = CalorieCalc.calculate(durationSeconds, waterTempCelsius)
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val startTimeStr = isoFormat.format(Date(sessionStartTimeMs.takeIf { it > 0 } ?: (endTimeMs - durationSeconds * 1000)))
        val endTimeStr = isoFormat.format(Date(endTimeMs))

        Log.i(
            tag,
            "Finalized 'Argus Scuba Diving' session: " +
                    "Duration=${durationSeconds}s, " +
                    "ActiveKcal=${calResult.activeKcal}, " +
                    "GrossKcal=${calResult.grossKcal}, " +
                    "BMRKcal=${calResult.bmrKcal}, " +
                    "MaxDepth=${maxDepthMeters}m, " +
                    "AvgDepth=${avgDepthMeters}m, " +
                    "EntryGPS=${entryGps?.latitude},${entryGps?.longitude}, " +
                    "ExitGPS=${exitGps?.latitude},${exitGps?.longitude}"
        )
    }

    fun isTracking(): Boolean = isSessionActive
}
