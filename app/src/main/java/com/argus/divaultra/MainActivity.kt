package com.argus.divaultra

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.argus.divaultra.core.AscentRateStatus
import com.argus.divaultra.core.DivePhase
import com.argus.divaultra.core.DiveStateManager
import com.argus.divaultra.core.NoFlyManager
import com.argus.divaultra.core.NoFlyStatus
import com.argus.divaultra.core.SafetyStopStatus
import com.argus.divaultra.log.DiveLogManager
import com.argus.divaultra.log.GpsPoint
import com.argus.divaultra.ui.GarminDiveScreen
import com.argus.divaultra.ui.SettingsScreen
import com.argus.divaultra.ui.DiveLogScreen
import com.argus.divaultra.ui.WatchfaceScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

class MainActivity : ComponentActivity(), SensorEventListener, LocationListener {

    private lateinit var sensorManager: SensorManager
    private var pressureSensor: Sensor? = null
    private var tempSensor: Sensor? = null
    private var rotationSensor: Sensor? = null
    private val stateManager = DiveStateManager()
    private lateinit var diveLogManager: DiveLogManager
    private lateinit var noFlyManager: NoFlyManager
    private lateinit var healthWorkoutManager: com.argus.divaultra.health.HealthWorkoutManager
    private var locationManager: LocationManager? = null

    private var surfacePressureHpa = 1013.25f
    private var isSurfacePressureCalibrated = false
    private var currentTempCelsius = 24.0
    private var isSimulating = false
    private var hasDiveLogStarted = false
    private var currentScreen by mutableStateOf("dive")

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            fetchSurfaceGps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on continuously while diving
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize Dive Log Manager, No-Fly Manager & Health Workout Manager
        diveLogManager = DiveLogManager(this)
        noFlyManager = NoFlyManager(this, diveLogManager)
        healthWorkoutManager = com.argus.divaultra.health.HealthWorkoutManager(this)

        // Initialize Sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        tempSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        // Initialize Location
        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        checkAndRequestLocationPermissions()

        // 1. DEDICATED 1-SECOND CLOCK TICKER:
        // Guarantees that dive timer, Bühlmann loading, and NDL advance at 1 second per real second!
        lifecycleScope.launch {
            while (isActive) {
                delay(1000)
                stateManager.onOneSecondTick()
            }
        }

        // 2. Telemetry Observer: Haptic alerts and dive profile sampling
        lifecycleScope.launch {
            var prevSafetyStatus = SafetyStopStatus.NOT_REQUIRED
            var prevAscentStatus = AscentRateStatus.OPTIMAL
            var prevPhase = DivePhase.SURFACE

            stateManager.telemetry.collect { telemetry ->
                // Dive start transition: Submersion detected
                if (telemetry.phase != DivePhase.SURFACE && telemetry.phase != DivePhase.COMPLETED && !hasDiveLogStarted) {
                    hasDiveLogStarted = true
                    diveLogManager.startDiveLog(telemetry.fractionO2)
                    healthWorkoutManager.startWorkoutSession()
                    stopGpsUpdates() // Turn off GPS underwater to save battery
                }

                // Sample dive profile every 5 seconds
                if (hasDiveLogStarted) {
                    diveLogManager.recordSampleIfDue(telemetry)
                }

                // Dive complete transition: Surfaced after diving
                if (telemetry.phase == DivePhase.COMPLETED && prevPhase != DivePhase.COMPLETED) {
                    fetchExitGpsAndSave(telemetry)
                    hasDiveLogStarted = false
                    stateManager.resetToSurface()
                }

                // Vibrate on safety stop completed
                if (telemetry.safetyStopStatus == SafetyStopStatus.COMPLETED && prevSafetyStatus != SafetyStopStatus.COMPLETED) {
                    triggerHapticPattern(longArrayOf(0, 300, 150, 300))
                }
                // Vibrate on dangerous ascent (> 10 m/min)
                if (telemetry.ascentRateStatus == AscentRateStatus.DANGER && prevAscentStatus != AscentRateStatus.DANGER) {
                    triggerHapticPattern(longArrayOf(0, 100, 100, 100, 100, 100))
                }

                prevSafetyStatus = telemetry.safetyStopStatus
                prevAscentStatus = telemetry.ascentRateStatus
                prevPhase = telemetry.phase
            }
        }

        setContent {
            val telemetry by stateManager.telemetry.collectAsState()
            var noFlyStatus by remember { mutableStateOf(noFlyManager.calculateStatus(telemetry.phase, telemetry.diveTimeSeconds)) }

            // Live 1-second update for No-Fly calculation
            LaunchedEffect(telemetry.phase, telemetry.diveTimeSeconds) {
                while (isActive) {
                    noFlyStatus = noFlyManager.calculateStatus(telemetry.phase, telemetry.diveTimeSeconds)
                    delay(1000)
                }
            }

            // Whenever active diving is detected, automatically lock onto Dive Screen
            LaunchedEffect(telemetry.phase) {
                if (telemetry.phase != DivePhase.SURFACE && telemetry.phase != DivePhase.COMPLETED) {
                    currentScreen = "dive"
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (currentScreen) {
                    "watchface" -> {
                        WatchfaceScreen(
                            telemetry = telemetry,
                            noFlyStatus = noFlyStatus,
                            onStartDive = { currentScreen = "dive" },
                            onOpenLogs = { currentScreen = "logs" },
                            onOpenSettings = { currentScreen = "settings" }
                        )
                    }
                    "settings" -> {
                        SettingsScreen(
                            telemetry = telemetry,
                            onGasSelected = { newMix ->
                                stateManager.setGasMix(newMix)
                            },
                            onStartSimulation = {
                                currentScreen = "dive"
                                startSimulation()
                            },
                            onReturnToDive = {
                                currentScreen = "dive"
                            },
                            onOpenLogs = {
                                currentScreen = "logs"
                            }
                        )
                    }
                    "logs" -> {
                        DiveLogScreen(
                            logManager = diveLogManager,
                            onClose = {
                                currentScreen = "dive"
                            }
                        )
                    }
                    else -> {
                        GarminDiveScreen(
                            telemetry = telemetry,
                            onOpenSettings = {
                                if (telemetry.phase == DivePhase.SURFACE || telemetry.phase == DivePhase.COMPLETED) {
                                    currentScreen = "settings"
                                }
                            },
                            onOpenLogs = {
                                currentScreen = "logs"
                            },
                            onEndDiveNow = {
                                stateManager.endDiveNow()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun checkAndRequestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchSurfaceGps()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun fetchSurfaceGps() {
        try {
            val lm = locationManager ?: return
            val lastGps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNet = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val best = when {
                lastGps != null && lastNet != null -> if (lastGps.time > lastNet.time) lastGps else lastNet
                lastGps != null -> lastGps
                else -> lastNet
            }
            best?.let {
                diveLogManager.setEntryGps(GpsPoint(it.latitude, it.longitude, it.accuracy, it.time))
            }

            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 5f, this)
            }
        } catch (ignored: SecurityException) {}
    }

    private fun stopGpsUpdates() {
        try {
            locationManager?.removeUpdates(this)
        } catch (ignored: SecurityException) {}
    }

    private fun fetchExitGpsAndSave(telemetry: com.argus.divaultra.core.DiveTelemetry) {
        try {
            val lm = locationManager
            if (lm != null && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                val last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                last?.let {
                    diveLogManager.setExitGps(GpsPoint(it.latitude, it.longitude, it.accuracy, it.time))
                }
            }
        } catch (ignored: SecurityException) {}

        diveLogManager.endDiveLog(telemetry)
        healthWorkoutManager.endWorkoutSession(
            endTimeMs = System.currentTimeMillis(),
            durationSeconds = telemetry.diveTimeSeconds,
            waterTempCelsius = telemetry.waterTemperatureCelsius,
            maxDepthMeters = telemetry.maxDepthMeters,
            avgDepthMeters = telemetry.averageDepthMeters,
            entryGps = null,
            exitGps = null
        )
    }

    override fun onLocationChanged(location: Location) {
        diveLogManager.setEntryGps(GpsPoint(location.latitude, location.longitude, location.accuracy, location.time))
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
        stopGpsUpdates()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || isSimulating) return

        when (event.sensor.type) {
            Sensor.TYPE_PRESSURE -> {
                val currentHpa = event.values[0]
                if (!isSurfacePressureCalibrated) {
                    surfacePressureHpa = currentHpa
                    isSurfacePressureCalibrated = true
                }
                // 1 meter seawater ≈ 100.55 hPa delta
                val deltaHpa = max(0.0f, currentHpa - surfacePressureHpa)
                val depthMeters = deltaHpa / 100.55
                // Feeds real-time depth without advancing dive time prematurely
                stateManager.onDepthReading(depthMeters.toDouble(), currentTempCelsius)
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

    private fun startSimulation() {
        if (isSimulating) return
        isSimulating = true
        triggerHapticPattern(longArrayOf(0, 150))
        lifecycleScope.launch {
            val simulationSteps = listOf(
                2.0, 5.0, 10.0, 15.0, 18.0, 18.0, 18.0, 18.0, 18.0, 18.0,
                14.0, 10.0, 5.0, 5.0, 5.0, 5.0, 5.0, 2.0, 0.0
            )
            for (depth in simulationSteps) {
                if (!isSimulating) break
                stateManager.onDepthReading(depth, 23.0)
                stateManager.onOneSecondTick()
                delay(1000)
            }
            isSimulating = false
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.repeatCount == 0) {
            event.startTracking()
        }
        val isUnderwater = stateManager.telemetry.value.phase != DivePhase.SURFACE && stateManager.telemetry.value.phase != DivePhase.COMPLETED
        return when (keyCode) {
            KeyEvent.KEYCODE_STEM_1, KeyEvent.KEYCODE_BUTTON_1, KeyEvent.KEYCODE_FUNCTION -> true
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_STEM_2 -> {
                if (isUnderwater || currentScreen == "dive") {
                    true
                } else {
                    super.onKeyDown(keyCode, event)
                }
            }
            KeyEvent.KEYCODE_STEM_PRIMARY, KeyEvent.KEYCODE_NAVIGATE_NEXT -> {
                if (isUnderwater || currentScreen == "dive") true else super.onKeyDown(keyCode, event)
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val isUnderwater = stateManager.telemetry.value.phase != DivePhase.SURFACE && stateManager.telemetry.value.phase != DivePhase.COMPLETED

        if (event?.isTracking == true && !event.isCanceled) {
            when (keyCode) {
                // 🟠 MIDDLE ORANGE QUICK BUTTON: SHORT PRESS -> CYCLE UNDERWATER SCREENS
                KeyEvent.KEYCODE_STEM_1, KeyEvent.KEYCODE_BUTTON_1, KeyEvent.KEYCODE_FUNCTION -> {
                    triggerPredefinedHaptic(VibrationEffect.EFFECT_CLICK)
                    stateManager.cycleUnderwaterScreen()
                    return true
                }
                // 🔙 BOTTOM BACK BUTTON: SHORT PRESS -> RETURN TO DIVE SCREEN OR TOGGLE BOOST
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_STEM_2 -> {
                    if (currentScreen != "dive") {
                        currentScreen = "dive"
                        triggerPredefinedHaptic(VibrationEffect.EFFECT_TICK)
                        return true
                    } else {
                        // On dive screen, back button toggles backlight boost instead of exiting app
                        stateManager.toggleBacklightBoost()
                        triggerPredefinedHaptic(VibrationEffect.EFFECT_TICK)
                        return true
                    }
                }
                // ⚪ TOP HOME BUTTON: SHORT PRESS -> DROP DIVE WAYPOINT
                KeyEvent.KEYCODE_STEM_PRIMARY, KeyEvent.KEYCODE_NAVIGATE_NEXT -> {
                    if (isUnderwater || currentScreen == "dive") {
                        diveLogManager.addMarker("WAYPOINT")
                        triggerPredefinedHaptic(VibrationEffect.EFFECT_HEAVY_CLICK)
                        return true
                    }
                }
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            // 🟠 MIDDLE QUICK BUTTON: LONG PRESS -> COMPASS BEARING LOCK (COURSE PIN)
            KeyEvent.KEYCODE_STEM_1, KeyEvent.KEYCODE_BUTTON_1, KeyEvent.KEYCODE_FUNCTION -> {
                stateManager.toggleBearingLock()
                triggerPredefinedHaptic(VibrationEffect.EFFECT_DOUBLE_CLICK)
                return true
            }
            // 🔙 BOTTOM BACK BUTTON: LONG PRESS -> END DIVE (If near surface < 1.0m)
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_STEM_2 -> {
                if (stateManager.telemetry.value.currentDepthMeters < 1.0) {
                    stateManager.endDiveNow()
                    currentScreen = "dive"
                    triggerPredefinedHaptic(VibrationEffect.EFFECT_HEAVY_CLICK)
                    return true
                }
            }
        }
        return super.onKeyLongPress(keyCode, event)
    }

    private fun triggerPredefinedHaptic(effectId: Int) {
        try {
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(effectId))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(60L)
            }
        } catch (ignored: Exception) {}
    }
}
