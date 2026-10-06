package com.argus.divaultra

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.lifecycleScope
import com.argus.divaultra.core.AscentRateStatus
import com.argus.divaultra.core.DiveStateManager
import com.argus.divaultra.core.SafetyStopStatus
import com.argus.divaultra.ui.GarminDiveScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var pressureSensor: Sensor? = null
    private var tempSensor: Sensor? = null
    private var rotationSensor: Sensor? = null
    private val stateManager = DiveStateManager()

    private var surfacePressureHpa = 1013.25f
    private var currentTempCelsius = 24.0
    private var isSimulating = false

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on continuously while diving
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize Sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        tempSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        // Listen for haptic alarm triggers
        lifecycleScope.launch {
            var prevSafetyStatus = SafetyStopStatus.NOT_REQUIRED
            var prevAscentStatus = AscentRateStatus.OPTIMAL

            stateManager.telemetry.collect { telemetry ->
                // Vibrate on safety stop completed
                if (telemetry.safetyStopStatus == SafetyStopStatus.COMPLETED && prevSafetyStatus != SafetyStopStatus.COMPLETED) {
                    triggerHapticPattern(longArrayOf(0, 300, 150, 300))
                }
                // Vibrate on dangerous ascent
                if (telemetry.ascentRateStatus == AscentRateStatus.DANGER && prevAscentStatus != AscentRateStatus.DANGER) {
                    triggerHapticPattern(longArrayOf(0, 100, 100, 100, 100, 100))
                }
                prevSafetyStatus = telemetry.safetyStopStatus
                prevAscentStatus = telemetry.ascentRateStatus
            }
        }

        setContent {
            val telemetry by stateManager.telemetry.collectAsState()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        // Double tap to toggle demo simulation on dry land
                        detectTapGestures(
                            onDoubleTap = {
                                toggleSimulation()
                            }
                        )
                    }
            ) {
                GarminDiveScreen(telemetry = telemetry)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        pressureSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        tempSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || isSimulating) return

        when (event.sensor.type) {
            Sensor.TYPE_PRESSURE -> {
                val currentHpa = event.values[0]
                // 1 bar = 1000 hPa. 1 meter seawater ≈ 100.55 hPa
                val deltaHpa = max(0.0f, currentHpa - surfacePressureHpa)
                val depthMeters = deltaHpa / 100.55 // Saltwater gradient
                stateManager.onNewDepthSample(depthMeters.toDouble(), currentTempCelsius, 1.0)
            }
            Sensor.TYPE_AMBIENT_TEMPERATURE -> {
                currentTempCelsius = event.values[0].toDouble()
            }
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val azimuthRad = orientationAngles[0]
                val azimuthDeg = (Math.toDegrees(azimuthRad.toDouble()).toFloat() + 360f) % 360f
                stateManager.updateHeading(azimuthDeg)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun toggleSimulation() {
        isSimulating = !isSimulating
        if (isSimulating) {
            triggerHapticPattern(longArrayOf(0, 150))
            lifecycleScope.launch {
                // Quick simulated dive: descent to 18m, hold, ascent, safety stop, surface
                val depths = listOf(
                    2.0, 5.0, 12.0, 18.0, 18.0, 18.0, 18.0, 14.0, 8.0, 5.0, 5.0, 5.0, 5.0, 5.0, 2.0, 0.0
                )
                for (d in depths) {
                    if (!isSimulating) break
                    stateManager.onNewDepthSample(d, 23.0, 2.0)
                    delay(2000)
                }
                isSimulating = false
            }
        }
    }

    private fun triggerHapticPattern(timings: LongArray) {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
    }
}
