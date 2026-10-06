package com.argus.divaultra.log

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiveProfileSample(
    val timeSeconds: Long,
    val depthMeters: Double,
    val temperatureCelsius: Double,
    val ndlMinutes: Int
)

data class DiveLogEntry(
    val id: Long,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val durationSeconds: Long,
    val maxDepthMeters: Double,
    val averageDepthMeters: Double,
    val minTemperatureCelsius: Double,
    val fractionO2: Double,
    val profileSamples: List<DiveProfileSample> = emptyList()
)

class DiveLogManager(private val baseDir: File) {

    private val logs = mutableListOf<DiveLogEntry>()

    fun recordDive(entry: DiveLogEntry) {
        logs.add(entry)
        saveJsonExport(entry)
    }

    fun getAllDives(): List<DiveLogEntry> = logs.toList()

    /**
     * Exports dive to Universal Dive Data Format (UDDF) XML format.
     * Fully compatible with Subsurface, MacDive, and Divelogs.de.
     */
    fun exportToUDDF(entry: DiveLogEntry): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
        val startDate = Date(entry.startTimeMillis)

        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        sb.appendLine("""<uddf version="3.2.0">""")
        sb.appendLine("""  <generator>""")
        sb.appendLine("""    <name>Argus Dive Computer for Galaxy Watch Ultra</name>""")
        sb.appendLine("""    <version>1.0</version>""")
        sb.appendLine("""  </generator>""")
        sb.appendLine("""  <gasdefinitions>""")
        sb.appendLine("""    <mix id="gas1">""")
        sb.appendLine("""      <name>EAN${(entry.fractionO2 * 100).toInt()}</name>""")
        sb.appendLine("""      <o2>${String.format(Locale.US, "%.3f", entry.fractionO2)}</o2>""")
        sb.appendLine("""      <n2>${String.format(Locale.US, "%.3f", 1.0 - entry.fractionO2)}</n2>""")
        sb.appendLine("""      <he>0.000</he>""")
        sb.appendLine("""    </mix>""")
        sb.appendLine("""  </gasdefinitions>""")
        sb.appendLine("""  <profiledata>""")
        sb.appendLine("""    <repetitiongroup id="rg1">""")
        sb.appendLine("""      <dive id="dive_${entry.id}">""")
        sb.appendLine("""        <informationbeforedive>""")
        sb.appendLine("""          <datetime>${dateFormat.format(startDate)}T${timeFormat.format(startDate)}</datetime>""")
        sb.appendLine("""        </informationbeforedive>""")
        sb.appendLine("""        <samples>""")
        for (sample in entry.profileSamples) {
            sb.appendLine("""          <waypoint>""")
            sb.appendLine("""            <divetime>${sample.timeSeconds}</divetime>""")
            sb.appendLine("""            <depth>${String.format(Locale.US, "%.2f", sample.depthMeters)}</depth>""")
            sb.appendLine("""            <temperature>${String.format(Locale.US, "%.1f", sample.temperatureCelsius + 273.15)}</temperature>""")
            sb.appendLine("""          </waypoint>""")
        }
        sb.appendLine("""        </samples>""")
        sb.appendLine("""        <informationafterdive>""")
        sb.appendLine("""          <greatestdepth>${String.format(Locale.US, "%.2f", entry.maxDepthMeters)}</greatestdepth>""")
        sb.appendLine("""          <averagedepth>${String.format(Locale.US, "%.2f", entry.averageDepthMeters)}</averagedepth>""")
        sb.appendLine("""          <diveduration>${entry.durationSeconds}</diveduration>""")
        sb.appendLine("""        </informationafterdive>""")
        sb.appendLine("""      </dive>""")
        sb.appendLine("""    </repetitiongroup>""")
        sb.appendLine("""  </profiledata>""")
        sb.appendLine("""</uddf>""")
        return sb.toString()
    }

    private fun saveJsonExport(entry: DiveLogEntry) {
        if (!baseDir.exists()) baseDir.mkdirs()
        val file = File(baseDir, "dive_${entry.id}.json")
        val json = """
        {
          "diveId": ${entry.id},
          "timestamp": ${entry.startTimeMillis},
          "durationSeconds": ${entry.durationSeconds},
          "maxDepthMeters": ${entry.maxDepthMeters},
          "avgDepthMeters": ${entry.averageDepthMeters},
          "gasMixO2": ${entry.fractionO2},
          "minTemperatureC": ${entry.minTemperatureCelsius},
          "sampleCount": ${entry.profileSamples.size}
        }
        """.trimIndent()
        file.writeText(json)
    }
}
