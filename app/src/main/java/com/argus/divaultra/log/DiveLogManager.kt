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

data class DiveProfileSample(
    val second: Long,
    val depthMeters: Double,
    val tempCelsius: Double
)

object CalorieCalc {
    // Compendium of Physical Activities — code 18310 (Scuba diving, recreational)
    const val BASELINE_METS = 7.0
    const val DIVER_WEIGHT_KG = 96.0 // Jonas's body mass
    const val THERMAL_NEUTRAL_C = 24.0

    fun calculateKcal(durationSeconds: Long, waterTempCelsius: Double = 24.0, weightKg: Double = DIVER_WEIGHT_KG): Int {
        val diveMinutes = durationSeconds / 60.0
        val deltaT = (THERMAL_NEUTRAL_C - waterTempCelsius).coerceAtLeast(0.0)
        val thermalBoost = (0.02 * deltaT).coerceAtMost(0.40)
        val effectiveMets = BASELINE_METS * (1.0 + thermalBoost)
        val kcal = (effectiveMets * 3.5 * weightKg / 200.0) * diveMinutes
        return kotlin.math.round(kcal).toInt()
    }
}

data class DiveLogSummary(
    val diveId: String,
    val startTimeFormatted: String,
    val durationSeconds: Long,
    val maxDepthMeters: Double,
    val avgDepthMeters: Double,
    val gasMix: String,
    val waterTempCelsius: Double,
    val safetyStopCompleted: Boolean,
    val entryGpsFormatted: String?,
    val exitGpsFormatted: String?,
    val sampleCount: Int,
    val caloriesKcal: Int = 0
)

data class DiveLogDetails(
    val summary: DiveLogSummary,
    val samples: List<DiveProfileSample>
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

    fun recordSampleIfDue(telemetry: DiveTelemetry) {
        if (!isRecording) return

        val currentSec = telemetry.diveTimeSeconds
        if (currentSec <= 0L) return

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

    fun addMarker(label: String) {
        val markerText = "[$label @ ${lastSampleSecond}s]"
        Log.i(tag, "Recorded Tactical Event Marker: $markerText")
    }

    fun endDiveLog(telemetry: DiveTelemetry): File? {
        if (!isRecording && samples.isEmpty()) return null
        isRecording = false

        val endTimeMs = System.currentTimeMillis()
        val diveId = currentDiveId ?: "DIVE_${fileIdFormat.format(Date(startTimeMs))}"

        try {
            val root = JSONObject()
            root.put("dive_id", diveId)
            root.put("app_version", "Argus Dive Ultra V5")
            root.put("device", "Samsung Galaxy Watch Ultra (Wear OS)")
            root.put("start_time", isoFormat.format(Date(startTimeMs)))
            root.put("end_time", isoFormat.format(Date(endTimeMs)))
            root.put("duration_seconds", telemetry.diveTimeSeconds)
            root.put("fraction_o2", telemetry.fractionO2)
            root.put("gas_mix", "EAN${(telemetry.fractionO2 * 100).toInt()}")
            root.put("max_depth_meters", telemetry.maxDepthMeters)
            root.put("water_temperature_celsius", telemetry.waterTemperatureCelsius)
            root.put("safety_stop_completed", telemetry.safetyStopStatus == SafetyStopStatus.COMPLETED)
            val calories = CalorieCalc.calculateKcal(telemetry.diveTimeSeconds, telemetry.waterTemperatureCelsius)
            root.put("calories_kcal", calories)

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

            val logDir = File(context.getExternalFilesDir(null), "dive_logs")
            if (!logDir.exists()) logDir.mkdirs()

            val logFile = File(logDir, "${diveId}.json")
            logFile.writeText(root.toString(2))
            Log.d(tag, "Successfully saved dive log to ${logFile.absolutePath} (${samples.size} samples)")

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

    fun getAllDiveLogs(): List<DiveLogSummary> {
        val candidateDirs = listOfNotNull(
            File(context.getExternalFilesDir(null), "dive_logs"),
            File(context.filesDir, "dive_logs"),
            try { File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ArgusDive") } catch (e: Exception) { null }
        )

        val foundFiles = mutableMapOf<String, File>()
        for (dir in candidateDirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles { file -> file.isFile && file.name.endsWith(".json") }?.forEach { f ->
                    if (!foundFiles.containsKey(f.name)) {
                        foundFiles[f.name] = f
                    }
                }
            }
        }
        if (foundFiles.isEmpty()) return emptyList()

        val list = mutableListOf<DiveLogSummary>()
        for (file in foundFiles.values) {
            try {
                val json = JSONObject(file.readText())
                val diveId = json.optString("dive_id", file.nameWithoutExtension)
                val startTimeIso = json.optString("start_time", "")
                val durationSec = json.optLong("duration_seconds", 0L)
                val maxDepth = json.optDouble("max_depth_meters", 0.0)
                val gasMix = json.optString("gas_mix", "EAN32")
                val temp = json.optDouble("water_temperature_celsius", 24.0)
                val safetyStop = json.optBoolean("safety_stop_completed", false)

                var entryGpsStr: String? = null
                if (json.has("entry_gps")) {
                    val eg = json.getJSONObject("entry_gps")
                    val lat = eg.optDouble("latitude", 0.0)
                    val lon = eg.optDouble("longitude", 0.0)
                    entryGpsStr = String.format(Locale.US, "%.4f, %.4f", lat, lon)
                }

                var exitGpsStr: String? = null
                if (json.has("exit_gps")) {
                    val xg = json.getJSONObject("exit_gps")
                    val lat = xg.optDouble("latitude", 0.0)
                    val lon = xg.optDouble("longitude", 0.0)
                    exitGpsStr = String.format(Locale.US, "%.4f, %.4f", lat, lon)
                }

                val samplesArray = json.optJSONArray("samples")
                val sampleCount = samplesArray?.length() ?: 0

                var displayDate = startTimeIso
                try {
                    val date = isoFormat.parse(startTimeIso)
                    if (date != null) {
                        val userFormat = SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault())
                        displayDate = userFormat.format(date)
                    }
                } catch (ignored: Exception) {}

                var avgDepth = 0.0
                if (sampleCount > 0 && samplesArray != null) {
                    var depthSum = 0.0
                    for (i in 0 until sampleCount) {
                        depthSum += samplesArray.getJSONObject(i).optDouble("depth", 0.0)
                    }
                    avgDepth = depthSum / sampleCount
                }

                val calories = json.optInt("calories_kcal", CalorieCalc.calculateKcal(durationSec, temp))

                list.add(
                    DiveLogSummary(
                        diveId = diveId,
                        startTimeFormatted = displayDate,
                        durationSeconds = durationSec,
                        maxDepthMeters = maxDepth,
                        avgDepthMeters = avgDepth,
                        gasMix = gasMix,
                        waterTempCelsius = temp,
                        safetyStopCompleted = safetyStop,
                        entryGpsFormatted = entryGpsStr,
                        exitGpsFormatted = exitGpsStr,
                        sampleCount = sampleCount,
                        caloriesKcal = calories
                    )
                )
            } catch (e: Exception) {
                Log.e(tag, "Failed to parse dive log: ${file.name}", e)
            }
        }

        return list.sortedByDescending { it.diveId }
    }

    fun getDiveLogDetails(diveId: String): DiveLogDetails? {
        val candidateDirs = listOfNotNull(
            File(context.getExternalFilesDir(null), "dive_logs"),
            File(context.filesDir, "dive_logs"),
            try { File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ArgusDive") } catch (e: Exception) { null }
        )
        val file = candidateDirs.map { File(it, "${diveId}.json") }.firstOrNull { it.exists() }
            ?: return null

        return try {
            val json = JSONObject(file.readText())
            val startTimeIso = json.optString("start_time", "")
            val durationSec = json.optLong("duration_seconds", 0L)
            val maxDepth = json.optDouble("max_depth_meters", 0.0)
            val gasMix = json.optString("gas_mix", "EAN32")
            val temp = json.optDouble("water_temperature_celsius", 24.0)
            val safetyStop = json.optBoolean("safety_stop_completed", false)

            var entryGpsStr: String? = null
            if (json.has("entry_gps")) {
                val eg = json.getJSONObject("entry_gps")
                entryGpsStr = String.format(Locale.US, "%.4f, %.4f", eg.optDouble("latitude"), eg.optDouble("longitude"))
            }

            var exitGpsStr: String? = null
            if (json.has("exit_gps")) {
                val xg = json.getJSONObject("exit_gps")
                exitGpsStr = String.format(Locale.US, "%.4f, %.4f", xg.optDouble("latitude"), xg.optDouble("longitude"))
            }

            var displayDate = startTimeIso
            try {
                val date = isoFormat.parse(startTimeIso)
                if (date != null) {
                    val userFormat = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault())
                    displayDate = userFormat.format(date)
                }
            } catch (ignored: Exception) {}

            val profileSamples = mutableListOf<DiveProfileSample>()
            val samplesArray = json.optJSONArray("samples")
            var depthSum = 0.0
            val sampleCount = samplesArray?.length() ?: 0

            if (samplesArray != null) {
                for (i in 0 until sampleCount) {
                    val sObj = samplesArray.getJSONObject(i)
                    val sec = sObj.optLong("sec", i * 5L)
                    val d = sObj.optDouble("depth", 0.0)
                    val t = sObj.optDouble("temp", 24.0)
                    depthSum += d
                    profileSamples.add(DiveProfileSample(second = sec, depthMeters = d, tempCelsius = t))
                }
            }

            val avgDepth = if (sampleCount > 0) depthSum / sampleCount else 0.0
            val calories = json.optInt("calories_kcal", CalorieCalc.calculateKcal(durationSec, temp))

            val summary = DiveLogSummary(
                diveId = diveId,
                startTimeFormatted = displayDate,
                durationSeconds = durationSec,
                maxDepthMeters = maxDepth,
                avgDepthMeters = avgDepth,
                gasMix = gasMix,
                waterTempCelsius = temp,
                safetyStopCompleted = safetyStop,
                entryGpsFormatted = entryGpsStr,
                exitGpsFormatted = exitGpsStr,
                sampleCount = sampleCount,
                caloriesKcal = calories
            )

            DiveLogDetails(summary = summary, samples = profileSamples)
        } catch (e: Exception) {
            Log.e(tag, "Failed to load dive details for $diveId", e)
            null
        }
    }
}
