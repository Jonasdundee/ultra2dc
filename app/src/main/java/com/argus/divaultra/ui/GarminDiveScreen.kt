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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBackground),
        contentAlignment = Alignment.Center
    ) {
        // 1. Concentric Radial Ascent Rate Gauge (Left Arch)
        AscentRateArcGauge(
            ascentRate = telemetry.ascentRateMetersPerMin,
            status = telemetry.ascentRateStatus,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Tactical Compass Heading Arc Gauge with Large Degree Display
        CompassTopArcGauge(
            headingDegrees = telemetry.compassHeadingDegrees,
            cardinal = telemetry.cardinalDirection,
            modifier = Modifier.fillMaxSize()
        )

        // 3. Central Tactical Screen Layout (Safe round insets)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            // TOP SECTION: Large Dive Time & Large Gas Mix / Setup Button
            TopStatusBar(
                telemetry = telemetry,
                onOpenSettings = onOpenSettings
            )

            // MIDDLE SECTION: Dynamic Switch (Large Depth vs Safety Stop Dashboard)
            if (telemetry.phase == DivePhase.SAFETY_STOP || telemetry.safetyStopStatus == SafetyStopStatus.COMPLETED) {
                SafetyStopDashboard(telemetry = telemetry)
            } else {
                StandardDepthDisplay(telemetry = telemetry)
            }

            // BOTTOM SECTION: Large NDL, Large Max Depth, and Large Temperature
            BottomDataBar(telemetry = telemetry)
        }
    }
}

/**
 * Top Status Bar with enlarged dive time, gas mix, and settings button
 */
@Composable
fun TopStatusBar(
    telemetry: DiveTelemetry,
    onOpenSettings: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        // Dive Time (MM:SS) - Doubled font size
        val minutes = telemetry.diveTimeSeconds / 60
        val seconds = telemetry.diveTimeSeconds % 60
        Column(horizontalAlignment = Alignment.Start) {
            Text("TIME", color = ColorTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(
                text = String.format(Locale.US, "%02d:%02d", minutes, seconds),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
        }

        // Surface Mode: Clickable Setup Badge; Dive Mode: Gas Mix Badge (Doubled font size)
        if (telemetry.phase == DivePhase.SURFACE) {
            Box(
                modifier = Modifier
                    .background(ColorSurfaceGray, RoundedCornerShape(8.dp))
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
                        Text("STOP", color = ColorGarminAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
fun StandardDepthDisplay(telemetry: DiveTelemetry) {
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
                modifier = Modifier.padding(bottom = 7.dp)
            )
        }

        // Deco Warning Banner if ceiling exists
        if (telemetry.ceilingMeters > 0.5) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .background(ColorGarminRed.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "DECO STOP: ${String.format(Locale.US, "%.1f M", telemetry.ceilingMeters)}",
                    color = ColorGarminRed,
                    fontSize = 11.sp,
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

        // Large MM:SS Countdown Display (Enlarged)
        Text(
            text = String.format(Locale.US, "%02d:%02d", min, sec),
            color = if (telemetry.safetyStopStatus == SafetyStopStatus.COMPLETED) ColorGarminGreen else Color.White,
            fontSize = 32.sp,
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
 * Bottom Data Bar with doubled font sizes for MAX, NDL, and TEMP
 */
@Composable
fun BottomDataBar(telemetry: DiveTelemetry) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        // Max Depth - Doubled font size (18.sp)
        Column(horizontalAlignment = Alignment.Start) {
            Text("MAX", color = ColorTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "${String.format(Locale.US, "%.1f", telemetry.maxDepthMeters)} M",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
        }

        // NDL Box with large numerals (22.sp) and MIN unit
        Box(
            modifier = Modifier
                .background(
                    when {
                        telemetry.ndlMinutes <= 5 -> ColorGarminRed.copy(alpha = 0.35f)
                        telemetry.ndlMinutes <= 10 -> ColorGarminAmber.copy(alpha = 0.35f)
                        else -> ColorGarminGreen.copy(alpha = 0.25f)
                    },
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${telemetry.ndlMinutes}",
                    color = when {
                        telemetry.ndlMinutes <= 5 -> ColorGarminRed
                        telemetry.ndlMinutes <= 10 -> ColorGarminAmber
                        else -> ColorGarminGreen
                    },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "MIN",
                    color = ColorTextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }

        // Water Temp - Doubled font size (18.sp)
        Column(horizontalAlignment = Alignment.End) {
            Text("TEMP", color = ColorTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "${telemetry.waterTemperatureCelsius.toInt()} °C",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

/**
 * Concentric Left-arc Ascent Rate meter
 */
/**
 * Tactical 6-Step Gradient Ascent Rate Meter (3 Green, 2 Yellow, 1 Red)
 * Inspired by Garmin Descent Mk3 segmented ladder
 */
@Composable
fun AscentRateArcGauge(
    ascentRate: Double,
    status: AscentRateStatus,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 8.dp.toPx()
        val diameter = size.minDimension - strokeWidth - 32.dp.toPx()
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
 * Tactical Curved Compass Bar with Doubled-Size Degree Readout (18.sp)
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
            val diameter = size.minDimension - strokeWidth - 32.dp.toPx()
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = ColorSurfaceGray,
                startAngle = 230f,
                sweepAngle = 80f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            val normalizedHeading = (headingDegrees % 360f + 360f) % 360f
            val headingSweepOffset = ((normalizedHeading / 360f) * 80f) - 40f
            val cursorAngle = 270f + headingSweepOffset.coerceIn(-38f, 38f)

            val rad = Math.toRadians(cursorAngle.toDouble())
            val radius = diameter / 2f
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val cursorX = (centerX + radius * kotlin.math.cos(rad)).toFloat()
            val cursorY = (centerY + radius * kotlin.math.sin(rad)).toFloat()

            drawCircle(
                color = ColorGarminCyan,
                radius = 4.5.dp.toPx(),
                center = Offset(cursorX, cursorY)
            )
        }

        // Digital Heading Readout Badge (Doubled: 18.sp Extra-Bold Cyan)
        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .background(ColorSurfaceGray.copy(alpha = 0.92f), RoundedCornerShape(6.dp))
                .padding(horizontal = 9.dp, vertical = 2.dp)
        ) {
            Text(
                text = "${headingDegrees.toInt()}° $cardinal",
                color = ColorGarminCyan,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        }
    }
}
