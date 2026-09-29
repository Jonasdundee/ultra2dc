package com.argus.divaultra.log

import android.content.Context
import android.os.Environment
import android.util.Log
import com.argus.divaultra.core.DiveTelemetry
import com.argus.divaultra.core.SafetyStopStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GpsPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timestampMs: Long
)

data class DiveSample(
    val second: Long,
    val depthMeters: Double,
    val temperatureCelsius: Double,
    val ascentRateMetersPerMin: Double,
    val ndlMinutes: Int,
    val cnsPercent: Double,
    val inSafetyStopRange: Boolean
)

class DiveLogManager(private val context: Context) {

    private val tag = "DiveLogManager"
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    private val fileIdFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    private var currentDiveId: String? = null
    private var startTimeMs: Long = 0L
    private var entryGps: GpsPoint? = null
    private var exitGps: GpsPoint? = null
    private val samples = mutableListOf<DiveSample>()
    private var lastSampleSecond = -1L
    private var isRecording = false

    fun setEntryGps(point: GpsPoint) {
        if (entryGps == null) {
            entryGps = point
            Log.d(tag, "Recorded Entry GPS: ${point.latitude}, ${point.longitude}")
        }
    }

    fun setExitGps(point: GpsPoint) {
        exitGps = point
        Log.d(tag, "Recorded Exit GPS: ${point.latitude}, ${point.longitude}")
    }

    fun startDiveLog(fractionO2: Double) {
        val now = System.currentTimeMillis()
        startTimeMs = now
        currentDiveId = "DIVE_${fileIdFormat.format(Date(now))}"
        samples.clear()
        lastSampleSecond = -1L
        isRecording = true
        Log.d(tag, "Started Dive Log: $currentDiveId with O2=${fractionO2 * 100}%")
    }

    /**
     * Samples telemetry at 5-second intervals during the dive
     */
    fun recordSampleIfDue(telemetry: DiveTelemetry) {
        if (!isRecording) return

        val currentSec = telemetry.diveTimeSeconds
        if (currentSec <= 0L) return

        // Record a sample every 5 seconds (5, 10, 15, 20...)
        if (currentSec - lastSampleSecond >= 5L) {
            lastSampleSecond = currentSec
            val sample = DiveSample(
                second = currentSec,
                depthMeters = telemetry.currentDepthMeters,
                temperatureCelsius = telemetry.waterTemperatureCelsius,
                ascentRateMetersPerMin = telemetry.ascentRateMetersPerMin,
                ndlMinutes = telemetry.ndlMinutes,
                cnsPercent = telemetry.cnsPercent,
                inSafetyStopRange = telemetry.currentDepthMeters in 3.0..6.0
            )
            samples.add(sample)
        }
    }

    fun endDiveLog(telemetry: DiveTelemetry): File? {
        if (!isRecording && samples.isEmpty()) return null
        isRecording = false

        val endTimeMs = System.currentTimeMillis()
        val diveId = currentDiveId ?: "DIVE_${fileIdFormat.format(Date(startTimeMs))}"

        try {
            val root = JSONObject()
            root.put("dive_id", diveId)
            root.put("app_version", "Argus Dive Ultra V4")
            root.put("device", "Samsung Galaxy Watch Ultra (Wear OS)")
            root.put("start_time", isoFormat.format(Date(startTimeMs)))
            root.put("end_time", isoFormat.format(Date(endTimeMs)))
            root.put("duration_seconds", telemetry.diveTimeSeconds)
            root.put("fraction_o2", telemetry.fractionO2)
            root.put("gas_mix", "EAN${(telemetry.fractionO2 * 100).toInt()}")
            root.put("max_depth_meters", telemetry.maxDepthMeters)
            root.put("water_temperature_celsius", telemetry.waterTemperatureCelsius)
            root.put("safety_stop_completed", telemetry.safetyStopStatus == SafetyStopStatus.COMPLETED)

            // Entry GPS
            entryGps?.let {
                val eg = JSONObject()
                eg.put("latitude", it.latitude)
                eg.put("longitude", it.longitude)
                eg.put("accuracy_meters", it.accuracyMeters)
                eg.put("timestamp", isoFormat.format(Date(it.timestampMs)))
                root.put("entry_gps", eg)
            }

            // Exit GPS
            exitGps?.let {
                val xg = JSONObject()
                xg.put("latitude", it.latitude)
                xg.put("longitude", it.longitude)
                xg.put("accuracy_meters", it.accuracyMeters)
                xg.put("timestamp", isoFormat.format(Date(it.timestampMs)))
                root.put("exit_gps", xg)
            }

            // Samples Array
            val samplesArray = JSONArray()
            for (s in samples) {
                val so = JSONObject()
                so.put("sec", s.second)
                so.put("depth", String.format(Locale.US, "%.1f", s.depthMeters).toDouble())
                so.put("temp", String.format(Locale.US, "%.1f", s.temperatureCelsius).toDouble())
                so.put("ascent_rate", String.format(Locale.US, "%.1f", s.ascentRateMetersPerMin).toDouble())
                so.put("ndl", s.ndlMinutes)
                so.put("cns", String.format(Locale.US, "%.1f", s.cnsPercent).toDouble())
                so.put("in_safety_zone", s.inSafetyStopRange)
                samplesArray.put(so)
            }
            root.put("samples", samplesArray)

            // Write to watch external files dir: /sdcard/Android/data/com.argus.divaultra/files/dive_logs/
            val logDir = File(context.getExternalFilesDir(null), "dive_logs")
            if (!logDir.exists()) logDir.mkdirs()

            val logFile = File(logDir, "${diveId}.json")
            logFile.writeText(root.toString(2))
            Log.d(tag, "Successfully saved dive log to ${logFile.absolutePath} (${samples.size} samples)")

            // Also mirror to Documents if available
            try {
                val docDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ArgusDive")
                if (!docDir.exists()) docDir.mkdirs()
                val docFile = File(docDir, "${diveId}.json")
                docFile.writeText(root.toString(2))
            } catch (ignored: Exception) {}

            return logFile
        } catch (e: Exception) {
            Log.e(tag, "Failed to save dive log: ${e.message}", e)
            return null
        }
    }
}
