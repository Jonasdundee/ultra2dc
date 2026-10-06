package com.argus.divaultra.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBackground),
        contentAlignment = Alignment.Center
    ) {
        // 1. Outer Radial Ascent Rate Gauge (Left Arch)
        AscentRateArcGauge(
            ascentRate = telemetry.ascentRateMetersPerMin,
            status = telemetry.ascentRateStatus,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Tactical Compass Heading Arc Gauge (Top Half Horizon)
        CompassTopArcGauge(
            headingDegrees = telemetry.compassHeadingDegrees,
            cardinal = telemetry.cardinalDirection,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Central Tactical Screen Layout
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            // TOP SECTION: Dive Time & Gas / Requirement Badge
            TopStatusBar(telemetry = telemetry)

            // MIDDLE SECTION: Dynamic Switch (Depth vs Safety Stop Dashboard)
            if (telemetry.phase == DivePhase.SAFETY_STOP || telemetry.safetyStopStatus == SafetyStopStatus.COMPLETED) {
                // AUTOMATIC SAFETY STOP DISPLAY MODE
                SafetyStopDashboard(telemetry = telemetry)
            } else {
                // STANDARD DIVE DEPTH DISPLAY MODE
                StandardDepthDisplay(telemetry = telemetry)
            }

            // BOTTOM SECTION: NDL, Max Depth, and Water Temperature
            BottomDataBar(telemetry = telemetry)
        }
    }
}

/**
 * Top Status Bar with dive time, gas mix, and safety stop pending badge
 */
@Composable
fun TopStatusBar(telemetry: DiveTelemetry) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp)
    ) {
        // Dive Time (MM:SS)
        val minutes = telemetry.diveTimeSeconds / 60
        val seconds = telemetry.diveTimeSeconds % 60
        Column(horizontalAlignment = Alignment.Start) {
            Text("TIME", color = ColorTextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Text(
                text = String.format(Locale.US, "%02d:%02d", minutes, seconds),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Safety Stop Requirement / Gas Badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (telemetry.safetyStopStatus == SafetyStopStatus.REQUIRED_PENDING) {
                Box(
                    modifier = Modifier
                        .background(ColorGarminAmber.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("STOP REQ", color = ColorGarminAmber, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Gas Mix Badge (e.g., EAN32)
            Box(
                modifier = Modifier
                    .background(ColorSurfaceGray, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "EAN${(telemetry.fractionO2 * 100).toInt()}",
                    color = ColorGarminCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Standard Depth Display when diving at bottom or ascending
 */
@Composable
fun StandardDepthDisplay(telemetry: DiveTelemetry) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = String.format(Locale.US, "%.1f", telemetry.currentDepthMeters),
            color = Color.White,
            fontSize = 46.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "METERS",
            color = ColorGarminCyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp
        )

        // Deco Warning Banner if ceiling exists
        if (telemetry.ceilingMeters > 0.5) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .background(ColorGarminRed.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "DECO STOP: ${String.format(Locale.US, "%.1fm", telemetry.ceilingMeters)}",
                    color = ColorGarminRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Dedicated Garmin Mk-style Safety Stop Dashboard with Countdown & Buoyancy Corridor
 */
@Composable
fun SafetyStopDashboard(telemetry: DiveTelemetry) {
    val remainingSec = telemetry.safetyStopRemainingSeconds
    val min = remainingSec / 60
    val sec = remainingSec % 60

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorCardBackground, RoundedCornerShape(12.dp))
            .border(
                1.dp,
                when (telemetry.safetyStopStatus) {
                    SafetyStopStatus.COMPLETED -> ColorGarminGreen
                    SafetyStopStatus.PAUSED_TOO_SHALLOW -> ColorGarminRed
                    SafetyStopStatus.PAUSED_TOO_DEEP -> ColorGarminAmber
                    else -> ColorGarminAmber
                },
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        // Status Title
        val statusText = when (telemetry.safetyStopStatus) {
            SafetyStopStatus.COMPLETED -> "✅ STOP COMPLETE"
            SafetyStopStatus.PAUSED_TOO_SHALLOW -> "⚠️ TOO SHALLOW - DESCEND!"
            SafetyStopStatus.PAUSED_TOO_DEEP -> "⏸️ PAUSED - ASCEND TO 5M"
            else -> "🛑 SAFETY STOP (5.0m)"
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

        // Large MM:SS Countdown Display
        Text(
            text = String.format(Locale.US, "%02d:%02d", min, sec),
            color = if (telemetry.safetyStopStatus == SafetyStopStatus.COMPLETED) ColorGarminGreen else Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )

        // Current Depth vs Target Depth
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DEPTH: ${String.format(Locale.US, "%.1fm", telemetry.currentDepthMeters)}",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "(TARGET 5.0m)",
                color = ColorTextMuted,
                fontSize = 10.sp
            )
        }

        // Buoyancy Corridor Gauge (3m - 6m zone indicator)
        BuoyancyCorridorBar(
            currentDepth = telemetry.currentDepthMeters,
            minDepth = telemetry.safetyStopMinDepthMeters,
            maxDepth = telemetry.safetyStopMaxDepthMeters,
            targetDepth = telemetry.safetyStopTargetDepthMeters,
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .padding(top = 4.dp)
        )
    }
}

/**
 * Visual Buoyancy corridor bar showing the 3m - 6m safety zone with current depth marker
 */
@Composable
fun BuoyancyCorridorBar(
    currentDepth: Double,
    minDepth: Double,
    maxDepth: Double,
    targetDepth: Double,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Background track (representing 2.0m to 7.0m)
        val displayMin = 2.0
        val displayMax = 7.0
        val range = displayMax - displayMin

        // Draw track
        drawRoundRect(
            color = ColorSurfaceGray,
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
        )

        // Green safe zone (3.0m to 6.0m)
        val safeLeft = (((minDepth - displayMin) / range) * w).toFloat().coerceIn(0f, w)
        val safeRight = (((maxDepth - displayMin) / range) * w).toFloat().coerceIn(0f, w)
        drawRect(
            color = ColorGarminGreen.copy(alpha = 0.35f),
            topLeft = Offset(safeLeft, 0f),
            size = Size(safeRight - safeLeft, h)
        )

        // Target 5.0m tick mark
        val targetX = (((targetDepth - displayMin) / range) * w).toFloat().coerceIn(0f, w)
        drawLine(
            color = ColorGarminGreen,
            start = Offset(targetX, 0f),
            end = Offset(targetX, h),
            strokeWidth = 2.dp.toPx()
        )

        // Current Depth Indicator (Indicator Dot)
        val markerX = (((currentDepth.coerceIn(displayMin, displayMax) - displayMin) / range) * w).toFloat()
        drawCircle(
            color = if (currentDepth in minDepth..maxDepth) ColorGarminGreen else ColorGarminRed,
            radius = (h / 2f) - 1f,
            center = Offset(markerX, h / 2f)
        )
    }
}

/**
 * Bottom Data Bar with NDL, Max Depth, and Temp
 */
@Composable
fun BottomDataBar(telemetry: DiveTelemetry) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp)
    ) {
        // Max Depth
        Column(horizontalAlignment = Alignment.Start) {
            Text("MAX", color = ColorTextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "${String.format(Locale.US, "%.1f", telemetry.maxDepthMeters)}m",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // NDL (No Decompression Limit) in Minutes
        Box(
            modifier = Modifier
                .background(
                    when {
                        telemetry.ndlMinutes <= 5 -> ColorGarminRed.copy(alpha = 0.3f)
                        telemetry.ndlMinutes <= 10 -> ColorGarminAmber.copy(alpha = 0.3f)
                        else -> ColorGarminGreen.copy(alpha = 0.25f)
                    },
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 7.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("NDL", color = ColorTextMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "${telemetry.ndlMinutes}",
                    color = when {
                        telemetry.ndlMinutes <= 5 -> ColorGarminRed
                        telemetry.ndlMinutes <= 10 -> ColorGarminAmber
                        else -> ColorGarminGreen
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Water Temp
        Column(horizontalAlignment = Alignment.End) {
            Text("TEMP", color = ColorTextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "${telemetry.waterTemperatureCelsius.toInt()}°C",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Tactical Left-arc Ascent Rate meter inspired by Garmin Descent Mk3
 */
@Composable
fun AscentRateArcGauge(
    ascentRate: Double,
    status: AscentRateStatus,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 8.dp.toPx()
        val diameter = size.minDimension - strokeWidth - 6.dp.toPx()
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val arcSize = Size(diameter, diameter)

        // Background Track on left edge: 130 degrees to 230 degrees
        drawArc(
            color = ColorSurfaceGray,
            startAngle = 130f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Active Ascent Fill: Map 0 - 12 m/min to 0 - 100 degrees sweep
        val normalizedRate = (ascentRate.coerceIn(0.0, 12.0) / 12.0).toFloat()
        val sweepAngle = normalizedRate * 100f

        val fillColor = when (status) {
            AscentRateStatus.OPTIMAL -> ColorGarminGreen
            AscentRateStatus.CAUTION -> ColorGarminAmber
            AscentRateStatus.DANGER -> ColorGarminRed
        }

        if (sweepAngle > 2f) {
            drawArc(
                color = fillColor,
                startAngle = 130f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Tactical Curved Compass Bar across the top half of the watch face.
 * Features degree tick marks, glowing bearing index, and numeric degree readout.
 */
@Composable
fun CompassTopArcGauge(
    headingDegrees: Float,
    cardinal: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 5.dp.toPx()
            val diameter = size.minDimension - strokeWidth - 6.dp.toPx()
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)

            // Top arc from 220° to 320° (100° sweep centered at 270° / 12 o'clock)
            drawArc(
                color = ColorSurfaceGray,
                startAngle = 220f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Dynamic Compass Needle / Cursor Tick along the top arc
            // Map heading (0 - 360) to a normalized angle offset or window
            val normalizedHeading = (headingDegrees % 360f + 360f) % 360f
            val headingSweepOffset = ((normalizedHeading / 360f) * 100f) - 50f
            val cursorAngle = 270f + headingSweepOffset.coerceIn(-48f, 48f)

            val rad = Math.toRadians(cursorAngle.toDouble())
            val radius = diameter / 2f
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val cursorX = (centerX + radius * kotlin.math.cos(rad)).toFloat()
            val cursorY = (centerY + radius * kotlin.math.sin(rad)).toFloat()

            // Draw glowing cyan heading marker
            drawCircle(
                color = ColorGarminCyan,
                radius = 4.dp.toPx(),
                center = Offset(cursorX, cursorY)
            )
        }

        // Digital Heading Readout Badge at top center (e.g. "245° SW")
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .background(ColorSurfaceGray.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp)
        ) {
            Text(
                text = "${headingDegrees.toInt()}° $cardinal",
                color = ColorGarminCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        }
    }
}
