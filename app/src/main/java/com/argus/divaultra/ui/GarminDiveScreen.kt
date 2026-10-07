package com.argus.divaultra.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import com.argus.divaultra.log.CalorieCalc
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
    onReturnToWatchface: (() -> Unit)? = null,
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

        // 3. Central Tactical Cluster / Active Multi-Page HUD
        when (telemetry.underwaterScreenIndex) {
            1 -> CompassNavigationHud(
                telemetry = telemetry,
                onOpenSettings = onOpenSettings,
                onReturnToWatchface = onReturnToWatchface
            )
            2 -> BuhlmannTissueLoadingHud(
                telemetry = telemetry,
                onOpenSettings = onOpenSettings,
                onReturnToWatchface = onReturnToWatchface
            )
            3 -> DetailedDiveStatsHud(
                telemetry = telemetry,
                onOpenSettings = onOpenSettings,
                onReturnToWatchface = onReturnToWatchface
            )
            else -> {
                // Central Tactical Cluster (Standard Primary Depth Screen 0)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 70.dp, bottom = 8.dp)
                ) {
                    // UPPER INDICATORS (Above Depth): TIME on left, EANx on right
                    TopStatusBar(
                        telemetry = telemetry,
                        onOpenSettings = onOpenSettings,
                        onReturnToWatchface = onReturnToWatchface,
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

                    Spacer(modifier = Modifier.height(4.dp))
                    PageDotsIndicator(selectedIndex = 0)
                }
            }
        }
    }
}

/**
 * Upper Indicators: TIME (left) and EANx/LOGS (right) positioned in the wide upper-middle
 */
@Composable
fun TopStatusBar(
    telemetry: DiveTelemetry,
    onOpenSettings: () -> Unit,
    onOpenLogs: () -> Unit = {},
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

        // Surface Mode: Clickable Setup & Logs Badges; Dive Mode: Gas Mix Badge
        if (telemetry.phase == DivePhase.SURFACE || telemetry.phase == DivePhase.COMPLETED) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(ColorSurfaceGray, RoundedCornerShape(8.dp))
                        .border(1.dp, ColorGarminGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { onOpenLogs() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "📖 LOGS",
                        color = ColorGarminGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .background(ColorSurfaceGray, RoundedCornerShape(8.dp))
                        .border(1.dp, ColorGarminCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { onOpenSettings() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "⚙ EAN${(telemetry.fractionO2 * 100).toInt()}",
                        color = ColorGarminCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                }
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
 * Standard Depth Display: Massive digits with METERS centered underneath
 * (Depth is NEVER blocked - stop info displays underneath)
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
        Text(
            text = String.format(Locale.US, "%.1f", telemetry.currentDepthMeters),
            color = Color.White,
            fontSize = 52.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )

        // Depth Unit / Active Stop Sub-banner
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
        // Normal Dive / Surface State: Clean METERS label
        else {
            Text(
                text = "METERS",
                color = ColorGarminCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp
            )
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
 * Sleek, bezel-aligned top arc with compact numeric degree readout
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
            val normalizedHeading = (headingDegrees % 360f + 360f) % 360f
            val headingSweepOffset = ((normalizedHeading / 360f) * 100f) - 50f
            val cursorAngle = 270f + headingSweepOffset.coerceIn(-48f, 48f)

            val rad = Math.toRadians(cursorAngle.toDouble())
            val radius = diameter / 2f
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val cursorX = (centerX + radius * kotlin.math.cos(rad)).toFloat()
            val cursorY = (centerY + radius * kotlin.math.sin(rad)).toFloat()

            drawCircle(
                color = ColorGarminCyan,
                radius = 3.5.dp.toPx(),
                center = Offset(cursorX, cursorY)
            )
        }

        // Sleek compact heading badge pinned right at top bezel
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .background(ColorSurfaceGray.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                .border(0.5.dp, ColorGarminCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text(
                text = "${headingDegrees.toInt()}° $cardinal",
                color = ColorGarminCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun PageDotsIndicator(selectedIndex: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 4) {
            Box(
                modifier = Modifier
                    .size(if (i == selectedIndex) 6.dp else 4.dp)
                    .clip(CircleShape)
                    .background(if (i == selectedIndex) ColorGarminCyan else ColorSurfaceGray)
            )
            if (i < 3) Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

@Composable
fun CompassNavigationHud(
    telemetry: DiveTelemetry,
    onOpenSettings: () -> Unit,
    onReturnToWatchface: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(top = 68.dp, bottom = 10.dp)
    ) {
        TopStatusBar(
            telemetry = telemetry,
            onOpenSettings = onOpenSettings,
            onReturnToWatchface = onReturnToWatchface,
            modifier = Modifier.fillMaxWidth(0.80f)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .background(ColorCardBackground, RoundedCornerShape(12.dp))
                .border(1.dp, ColorGarminCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🧭 TACTICAL COMPASS",
                    color = ColorGarminCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "${telemetry.compassHeadingDegrees.toInt()}° ${telemetry.cardinalDirection}",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(4.dp))

                val locked = telemetry.lockedBearingDegrees
                if (locked != null) {
                    val diff = ((telemetry.compassHeadingDegrees - locked + 540f) % 360f) - 180f
                    val deviationText = when {
                        kotlin.math.abs(diff) <= 5f -> "ON COURSE ✅"
                        diff > 0 -> String.format(Locale.US, "◀ %d° PORT", diff.toInt())
                        else -> String.format(Locale.US, "▶ %d° STBD", (-diff).toInt())
                    }
                    val deviationColor = if (kotlin.math.abs(diff) <= 5f) ColorGarminGreen else ColorGarminAmber

                    Box(
                        modifier = Modifier
                            .background(ColorSurfaceGray, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "🔒 LOCKED: ${locked.toInt()}° · $deviationText",
                            color = deviationColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Text(
                        text = "HOLD ORANGE BUTTON TO LOCK BEARING",
                        color = ColorTextMuted,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(0.78f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format(Locale.US, "DEPTH: %.1f M", telemetry.currentDepthMeters),
                color = ColorGarminCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "NDL: ${telemetry.ndlMinutes} MIN",
                color = if (telemetry.ndlMinutes <= 5) ColorGarminAmber else ColorGarminGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        PageDotsIndicator(selectedIndex = 1)
    }
}

@Composable
fun BuhlmannTissueLoadingHud(
    telemetry: DiveTelemetry,
    onOpenSettings: () -> Unit,
    onReturnToWatchface: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(top = 68.dp, bottom = 10.dp)
    ) {
        TopStatusBar(
            telemetry = telemetry,
            onOpenSettings = onOpenSettings,
            onReturnToWatchface = onReturnToWatchface,
            modifier = Modifier.fillMaxWidth(0.80f)
        )

        Spacer(modifier = Modifier.height(3.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(ColorCardBackground, RoundedCornerShape(12.dp))
                .border(1.dp, ColorSurfaceGray, RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BÜHLMANN ZHL-16C TISSUES",
                        color = ColorGarminCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = if (telemetry.ceilingMeters > 0) "CEIL: ${telemetry.ceilingMeters.toInt()}M" else "NO DECO",
                        color = if (telemetry.ceilingMeters > 0) ColorGarminRed else ColorGarminGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    val count = 16
                    val barWidth = (size.width / count) * 0.75f
                    val spacing = size.width / count

                    for (i in 0 until count) {
                        val sat = telemetry.tissueSaturations.getOrElse(i) { 0.05f }
                        val barHeight = (sat.coerceIn(0.05f, 1.2f) * size.height).coerceAtMost(size.height)
                        val color = when {
                            sat > 0.95f -> ColorGarminRed
                            sat > 0.75f -> ColorGarminAmber
                            else -> ColorGarminGreen
                        }
                        val x = i * spacing + (spacing - barWidth) / 2f
                        val y = size.height - barHeight

                        drawRect(
                            color = color,
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1 (4m)", color = ColorTextMuted, fontSize = 7.sp)
                    Text("8 (77m)", color = ColorTextMuted, fontSize = 7.sp)
                    Text("16 (635m)", color = ColorTextMuted, fontSize = 7.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(0.78f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format(Locale.US, "DEPTH: %.1f M", telemetry.currentDepthMeters),
                color = ColorGarminCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "NDL: ${telemetry.ndlMinutes} MIN",
                color = if (telemetry.ndlMinutes <= 5) ColorGarminAmber else ColorGarminGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        PageDotsIndicator(selectedIndex = 2)
    }
}

@Composable
fun DetailedDiveStatsHud(
    telemetry: DiveTelemetry,
    onOpenSettings: () -> Unit,
    onReturnToWatchface: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(top = 68.dp, bottom = 10.dp)
    ) {
        TopStatusBar(
            telemetry = telemetry,
            onOpenSettings = onOpenSettings,
            onReturnToWatchface = onReturnToWatchface,
            modifier = Modifier.fillMaxWidth(0.80f)
        )

        Spacer(modifier = Modifier.height(3.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .background(ColorCardBackground, RoundedCornerShape(12.dp))
                .border(1.dp, ColorSurfaceGray, RoundedCornerShape(12.dp))
                .padding(8.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("MAX DEPTH", color = ColorTextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = String.format(Locale.US, "%.1f M", telemetry.maxDepthMeters),
                            color = ColorGarminCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("AVG DEPTH", color = ColorTextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = String.format(Locale.US, "%.1f M", telemetry.averageDepthMeters),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("PO2 / MOD", color = ColorTextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = String.format(Locale.US, "%.2f / %.1fM", telemetry.currentPO2, telemetry.modMeters),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("WATER TEMP", color = ColorTextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = String.format(Locale.US, "%.1f °C", telemetry.waterTemperatureCelsius),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "CNS O2: ${telemetry.cnsPercent.toInt()}%",
                        color = if (telemetry.cnsPercent > 80) ColorGarminRed else ColorGarminGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ENERGY: ${CalorieCalc.calculateKcal(telemetry.diveTimeSeconds, telemetry.waterTemperatureCelsius)} KCAL",
                        color = ColorGarminAmber,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(0.78f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format(Locale.US, "DEPTH: %.1f M", telemetry.currentDepthMeters),
                color = ColorGarminCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "NDL: ${telemetry.ndlMinutes} MIN",
                color = if (telemetry.ndlMinutes <= 5) ColorGarminAmber else ColorGarminGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        PageDotsIndicator(selectedIndex = 3)
    }
}
