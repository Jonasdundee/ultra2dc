package com.argus.divaultra.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.argus.divaultra.core.AscentRateStatus
import com.argus.divaultra.core.DivePhase
import com.argus.divaultra.core.DiveTelemetry
import com.argus.divaultra.core.SafetyStopStatus
import java.util.Locale

// Tactical Color Palette (Garmin Descent Mk3 & Shearwater Teric style)
val ColorBackground = Color(0xFF000000)
val ColorGarminCyan = Color(0xFF00E5FF)
val ColorGarminGreen = Color(0xFF00E676)
val ColorGarminAmber = Color(0xFFFFAB00)
val ColorGarminRed = Color(0xFFFF1744)
val ColorSurfaceGray = Color(0xFF1E242B)
val ColorCardBackground = Color(0xFF13181F)
val ColorTextMuted = Color(0xFF90A4AE)

@Composable
fun GarminDiveScreen(
    telemetry: DiveTelemetry,
    onOpenSettings: () -> Unit = {},
    onEndDiveNow: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBackground),
        contentAlignment = Alignment.Center
    ) {
        // 1. Tactical 6-Step Gradient Ascent Rate Meter (Left Arch)
        AscentRateArcGauge(
            ascentRate = telemetry.ascentRateMetersPerMin,
            status = telemetry.ascentRateStatus,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Compass Heading Gauge (~5mm down in top-center)
        CompassTopArcGauge(
            headingDegrees = telemetry.compassHeadingDegrees,
            cardinal = telemetry.cardinalDirection,
            modifier = Modifier.fillMaxSize()
        )

        // 3. Central Tactical Cluster - 4 indicators brought inward around Depth
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 70.dp, bottom = 12.dp)
        ) {
            // UPPER INDICATORS (Above Depth): TIME on left, EANx on right
            TopStatusBar(
                telemetry = telemetry,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.fillMaxWidth(0.80f)
            )

            Spacer(modifier = Modifier.height(2.dp))

            // CENTRAL DISPLAY: Massive Depth is ALWAYS visible and never blocked!
            StandardDepthDisplay(
                telemetry = telemetry,
                onEndDiveNow = onEndDiveNow
            )

            Spacer(modifier = Modifier.height(2.dp))

            // LOWER INDICATORS (Below Depth): MAX Depth on left, Water Temp on right
            MiddleBottomDataBar(
                telemetry = telemetry,
                modifier = Modifier.fillMaxWidth(0.80f)
            )

            Spacer(modifier = Modifier.height(5.dp))

            // BOTTOM CENTER: Prominent NDL Safety Pill
            NdlSafetyPill(telemetry = telemetry)
        }
    }
}

/**
 * Upper Indicators: TIME (left) and EANx (right) positioned in the wide upper-middle
 */
@Composable
fun TopStatusBar(
    telemetry: DiveTelemetry,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        // Dive Time (MM:SS) - Bold monospace
        val minutes = telemetry.diveTimeSeconds / 60
        val seconds = telemetry.diveTimeSeconds % 60
        Column(horizontalAlignment = Alignment.Start) {
            Text("TIME", color = ColorTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(
                text = String.format(Locale.US, "%02d:%02d", minutes, seconds),
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }

        // Surface Mode: Clickable Setup Badge; Dive Mode: Gas Mix Badge
        if (telemetry.phase == DivePhase.SURFACE) {
            Box(
                modifier = Modifier
                    .background(ColorSurfaceGray, RoundedCornerShape(8.dp))
                    .border(1.dp, ColorGarminCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .clickable { onOpenSettings() }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "⚙ EAN${(telemetry.fractionO2 * 100).toInt()}",
                    color = ColorGarminCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (telemetry.safetyStopStatus == SafetyStopStatus.REQUIRED_PENDING) {
                    Box(
                        modifier = Modifier
                            .background(ColorGarminAmber.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text("STOP", color = ColorGarminAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Box(
                    modifier = Modifier
                        .background(ColorSurfaceGray, RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "EAN${(telemetry.fractionO2 * 100).toInt()}",
                        color = ColorGarminCyan,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

/**
 * Standard Depth Display: Massive digits with elevated "M" unit
 */
@Composable
fun StandardDepthDisplay(
    telemetry: DiveTelemetry,
    onEndDiveNow: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = String.format(Locale.US, "%.1f", telemetry.currentDepthMeters),
                color = Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "M",
                color = ColorGarminCyan,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        // 1. Mandatory Staged Deco Stop Obligation (Priority 1)
        if (telemetry.phase == DivePhase.DECO_STOP && telemetry.ceilingMeters >= 3.0) {
            Box(
                modifier = Modifier
                    .padding(top = 1.dp)
                    .background(ColorGarminRed.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .border(1.dp, ColorGarminRed, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "🛑 DECO STOP: ${telemetry.ceilingMeters.toInt()} M",
                    color = ColorGarminRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        // 2. Active Safety Stop Countdown (Priority 2) - Underneath Depth without blocking!
        else if (telemetry.phase == DivePhase.SAFETY_STOP || telemetry.safetyStopStatus == SafetyStopStatus.IN_STOP_COUNTING) {
            val min = telemetry.safetyStopRemainingSeconds / 60
            val sec = telemetry.safetyStopRemainingSeconds % 60
            Box(
                modifier = Modifier
                    .padding(top = 1.dp)
                    .background(ColorGarminAmber.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .border(1.dp, ColorGarminAmber, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "🛡️ STOP 5M %02d:%02d", min, sec),
                    color = ColorGarminAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        // 3. Safety Stop Completed Clear Banner (Priority 3)
        else if (telemetry.safetyStopStatus == SafetyStopStatus.COMPLETED) {
            Box(
                modifier = Modifier
                    .padding(top = 1.dp)
                    .background(ColorGarminGreen.copy(alpha = 0.30f), RoundedCornerShape(6.dp))
                    .border(1.dp, ColorGarminGreen, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "✅ STOP COMPLETE",
                    color = ColorGarminGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        // 4. Safety Stop Required Notice (approaching 3-5m or at bottom)
        else if (telemetry.safetyStopStatus == SafetyStopStatus.REQUIRED_PENDING) {
            Box(
                modifier = Modifier
                    .padding(top = 1.dp)
                    .background(ColorSurfaceGray.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                    .border(1.dp, ColorGarminAmber.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "STOP 3-5M REQ",
                    color = ColorGarminAmber,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        // 5. Surface Interval Hysteresis Banner (Priority 5)
        else if (telemetry.phase == DivePhase.SURFACING) {
            val surfMin = telemetry.surfaceIntervalRemainingSeconds / 60
            val surfSec = telemetry.surfaceIntervalRemainingSeconds % 60
            Box(
                modifier = Modifier
                    .padding(top = 1.dp)
                    .background(ColorGarminCyan.copy(alpha = 0.30f), RoundedCornerShape(6.dp))
                    .border(1.dp, ColorGarminCyan, RoundedCornerShape(6.dp))
                    .clickable { onEndDiveNow() }
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "SURF %02d:%02d [END]", surfMin, surfSec),
                    color = ColorGarminCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * Lower Indicators: MAX Depth (left) and Water Temp (right) in wide lower-middle
 */
@Composable
fun MiddleBottomDataBar(
    telemetry: DiveTelemetry,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        // Max Depth (17.sp bold)
        Column(horizontalAlignment = Alignment.Start) {
            Text("MAX", color = ColorTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "${String.format(Locale.US, "%.1f", telemetry.maxDepthMeters)} M",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black
            )
        }

        // Water Temp (17.sp bold)
        Column(horizontalAlignment = Alignment.End) {
            Text("TEMP", color = ColorTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "${telemetry.waterTemperatureCelsius.toInt()} °C",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

/**
 * Dedicated Centered NDL Safety Indicator Pill
 */
@Composable
fun NdlSafetyPill(telemetry: DiveTelemetry) {
    val ndlColor = when {
        telemetry.ndlMinutes <= 5 -> ColorGarminRed
        telemetry.ndlMinutes <= 10 -> ColorGarminAmber
        else -> ColorGarminGreen
    }

    Box(
        modifier = Modifier
            .background(
                ndlColor.copy(alpha = 0.22f),
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                ndlColor.copy(alpha = 0.65f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${telemetry.ndlMinutes}",
                color = ndlColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "MIN NDL",
                color = ColorTextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}

/**
 * Dedicated Garmin Mk-style Safety Stop Dashboard with Countdown & Buoyancy Corridor
 */
@Composable
fun SafetyStopDashboard(
    telemetry: DiveTelemetry,
    modifier: Modifier = Modifier
) {
    val remainingSec = telemetry.safetyStopRemainingSeconds
    val min = remainingSec / 60
    val sec = remainingSec % 60

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .background(ColorCardBackground, RoundedCornerShape(10.dp))
            .border(
                1.dp,
                when (telemetry.safetyStopStatus) {
                    SafetyStopStatus.COMPLETED -> ColorGarminGreen
                    SafetyStopStatus.PAUSED_TOO_SHALLOW -> ColorGarminRed
                    SafetyStopStatus.PAUSED_TOO_DEEP -> ColorGarminAmber
                    else -> ColorGarminAmber
                },
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        val statusText = when (telemetry.safetyStopStatus) {
            SafetyStopStatus.COMPLETED -> "✅ STOP CLEAR"
            SafetyStopStatus.PAUSED_TOO_SHALLOW -> "⚠️ TOO SHALLOW!"
            SafetyStopStatus.PAUSED_TOO_DEEP -> "⏸️ PAUSED - AT 5M"
            else -> "🛑 SAFETY STOP (5M)"
        }
        val statusColor = when (telemetry.safetyStopStatus) {
            SafetyStopStatus.COMPLETED -> ColorGarminGreen
            SafetyStopStatus.PAUSED_TOO_SHALLOW -> ColorGarminRed
            SafetyStopStatus.PAUSED_TOO_DEEP -> ColorGarminAmber
            else -> ColorGarminAmber
        }

        Text(
            text = statusText,
            color = statusColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )

        // MM:SS Countdown Display
        Text(
            text = String.format(Locale.US, "%02d:%02d", min, sec),
            color = if (telemetry.safetyStopStatus == SafetyStopStatus.COMPLETED) ColorGarminGreen else Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )

        // Current Depth vs Target Depth
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${String.format(Locale.US, "%.1f", telemetry.currentDepthMeters)} M",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "TARGET: 5.0 M",
                color = ColorGarminCyan,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Horizontal Buoyancy Corridor Gauge
        BuoyancyCorridorBar(
            currentDepth = telemetry.currentDepthMeters,
            targetDepth = telemetry.safetyStopTargetDepthMeters,
            minDepth = telemetry.safetyStopMinDepthMeters,
            maxDepth = telemetry.safetyStopMaxDepthMeters,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(10.dp)
                .padding(top = 2.dp)
        )
    }
}

/**
 * Visual Buoyancy Corridor Horizontal Gauge (3m to 6m Safe Zone)
 */
@Composable
fun BuoyancyCorridorBar(
    currentDepth: Double,
    targetDepth: Double,
    minDepth: Double,
    maxDepth: Double,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val displayMin = 2.0
        val displayMax = 7.0
        val range = displayMax - displayMin

        drawRoundRect(
            color = ColorSurfaceGray,
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
        )

        val safeLeft = (((minDepth - displayMin) / range) * w).toFloat().coerceIn(0f, w)
        val safeRight = (((maxDepth - displayMin) / range) * w).toFloat().coerceIn(0f, w)
        drawRect(
            color = ColorGarminGreen.copy(alpha = 0.35f),
            topLeft = Offset(safeLeft, 0f),
            size = Size(safeRight - safeLeft, h)
        )

        val targetX = (((targetDepth - displayMin) / range) * w).toFloat().coerceIn(0f, w)
        drawLine(
            color = ColorGarminGreen,
            start = Offset(targetX, 0f),
            end = Offset(targetX, h),
            strokeWidth = 2.dp.toPx()
        )

        val markerX = (((currentDepth.coerceIn(displayMin, displayMax) - displayMin) / range) * w).toFloat()
        drawCircle(
            color = if (currentDepth in minDepth..maxDepth) ColorGarminGreen else ColorGarminRed,
            radius = (h / 2f) - 1f,
            center = Offset(markerX, h / 2f)
        )
    }
}

/**
 * Tactical 6-Step Gradient Ascent Rate Meter (3 Green, 2 Yellow, 1 Red)
 * Segmented along left bezel arch
 */
@Composable
fun AscentRateArcGauge(
    ascentRate: Double,
    status: AscentRateStatus,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 8.dp.toPx()
        val diameter = size.minDimension - strokeWidth - 28.dp.toPx()
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val arcSize = Size(diameter, diameter)

        // 6 discrete segmented gradient blocks
        val segmentCount = 6
        val segmentSweep = 11f
        val gapSweep = 2.8f
        val baseStartAngle = 140f

        // Segment threshold speeds in m/min:
        // Segment 0: > 1.0 m/min (Green 1)
        // Segment 1: > 3.0 m/min (Green 2)
        // Segment 2: > 5.5 m/min (Green 3)
        // Segment 3: > 7.5 m/min (Yellow 1)
        // Segment 4: > 9.0 m/min (Yellow 2)
        // Segment 5: > 10.0 m/min (Red 1 - Critical)
        val thresholds = listOf(1.0, 3.0, 5.5, 7.5, 9.0, 10.0)
        val segmentColors = listOf(
            ColorGarminGreen,  // Green 1
            ColorGarminGreen,  // Green 2
            ColorGarminGreen,  // Green 3
            ColorGarminAmber,  // Yellow 1
            ColorGarminAmber,  // Yellow 2
            ColorGarminRed     // Red 1
        )

        for (i in 0 until segmentCount) {
            val startAngle = baseStartAngle + i * (segmentSweep + gapSweep)
            val isLit = ascentRate >= thresholds[i]
            val color = if (isLit) segmentColors[i] else ColorSurfaceGray.copy(alpha = 0.45f)
            val actualStroke = if (isLit) strokeWidth + 1.dp.toPx() else strokeWidth

            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = segmentSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = actualStroke, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Tactical Compass Heading Indicator:
 * Pinned ~5mm down in the upper-middle (top = 40.dp) with bold degree readout and cardinal direction
 */
@Composable
fun CompassTopArcGauge(
    headingDegrees: Float,
    cardinal: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        // Subtle top guide arc & moving cyan tracking dot
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 3.5.dp.toPx()
            val diameter = size.minDimension - strokeWidth - 24.dp.toPx()
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = ColorSurfaceGray.copy(alpha = 0.4f),
                startAngle = 240f,
                sweepAngle = 60f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            val normalizedHeading = (headingDegrees % 360f + 360f) % 360f
            val headingSweepOffset = ((normalizedHeading / 360f) * 60f) - 30f
            val cursorAngle = 270f + headingSweepOffset.coerceIn(-28f, 28f)

            val rad = Math.toRadians(cursorAngle.toDouble())
            val radius = diameter / 2f
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val cursorX = (centerX + radius * kotlin.math.cos(rad)).toFloat()
            val cursorY = (centerY + radius * kotlin.math.sin(rad)).toFloat()

            drawCircle(
                color = ColorGarminCyan,
                radius = 4.dp.toPx(),
                center = Offset(cursorX, cursorY)
            )
        }

        // Digital Heading Readout Badge (~5mm down in the middle)
        Box(
            modifier = Modifier
                .padding(top = 40.dp)
                .background(ColorSurfaceGray.copy(alpha = 0.95f), RoundedCornerShape(8.dp))
                .border(1.dp, ColorGarminCyan.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🧭 ${headingDegrees.toInt()}° $cardinal",
                    color = ColorGarminCyan,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
