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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import android.graphics.Paint
import android.graphics.Typeface
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

            // DYNAMIC CENTER: Giant Depth or Safety Stop Dashboard
            if (telemetry.phase == DivePhase.SAFETY_STOP || telemetry.safetyStopStatus == SafetyStopStatus.COMPLETED) {
                SafetyStopDashboard(
                    telemetry = telemetry,
                    modifier = Modifier.fillMaxWidth(0.84f)
                )
            } else {
                StandardDepthDisplay(
                    telemetry = telemetry,
                    onEndDiveNow = onEndDiveNow
                )
            }

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

        // Deco Warning Banner: Displayed ONLY during true mandatory staged decompression (NDL exhausted)
        if (telemetry.phase == DivePhase.DECO_STOP && telemetry.ceilingMeters >= 3.0) {
            Box(
                modifier = Modifier
                    .padding(top = 1.dp)
                    .background(ColorGarminRed.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .border(1.dp, ColorGarminRed, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "DECO STOP: ${telemetry.ceilingMeters.toInt()} M",
                    color = ColorGarminRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
        } else if (telemetry.phase == DivePhase.SURFACING) {
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
 * Tactical Garmin Descent Mk-style Curved Compass Ribbon:
 * Features a dynamic scrolling compass arc along the top bezel with:
 * - 10-degree tick marks & subtle 5-degree tick marks
 * - Prominent Cardinal markers: N (tactical red), E, S, W (bright white), and intercardinals (cyan)
 * - Fixed center lubber pointer triangle ▼ at 12 o'clock
 * - Digital heading badge pinned ~5mm down in the upper-middle (top = 40.dp)
 */
@Composable
fun CompassTopArcGauge(
    headingDegrees: Float,
    cardinal: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        // Tactical Curved Compass Ribbon along the top curve
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.dp.toPx()
            val outerRadius = size.minDimension / 2f - 14.dp.toPx()
            val centerX = size.width / 2f
            val centerY = size.height / 2f

            // Top ribbon background track: arc from 225° to 315° (90° sweep centered at 270° / 12 o'clock)
            val diameter = outerRadius * 2f
            val topLeft = Offset(centerX - outerRadius, centerY - outerRadius)
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = ColorSurfaceGray.copy(alpha = 0.5f),
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Draw ticks every 5° & 10° plus Cardinal Letters (N, NE, E, SE, S, SW, W, NW)
            drawIntoCanvas { canvas ->
                val paintCardinal = Paint().apply {
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                    textSize = 10.dp.toPx()
                }

                for (deg in 0 until 360 step 5) {
                    // Shortest delta from current heading in range [-180, 180]
                    val delta = ((deg - headingDegrees + 540f) % 360f) - 180f
                    // Visible window across top arc is ±40°
                    if (delta in -40f..40f) {
                        val angleDeg = 270f + delta
                        val rad = Math.toRadians(angleDeg.toDouble())
                        val cosA = kotlin.math.cos(rad).toFloat()
                        val sinA = kotlin.math.sin(rad).toFloat()

                        val isCardinal = deg % 90 == 0
                        val isIntercardinal = deg % 45 == 0 && !isCardinal
                        val is10Deg = deg % 10 == 0

                        val tickLength = when {
                            isCardinal -> 8.dp.toPx()
                            isIntercardinal -> 6.dp.toPx()
                            is10Deg -> 5.dp.toPx()
                            else -> 3.dp.toPx() // 5-deg tick
                        }

                        val pOuterX = centerX + outerRadius * cosA
                        val pOuterY = centerY + outerRadius * sinA
                        val pInnerX = centerX + (outerRadius - tickLength) * cosA
                        val pInnerY = centerY + (outerRadius - tickLength) * sinA

                        val tickColor = when {
                            isCardinal && deg == 0 -> Color(0xFFFF3B30) // N in tactical red
                            isCardinal -> Color.White
                            isIntercardinal -> ColorGarminCyan.copy(alpha = 0.85f)
                            is10Deg -> Color.White.copy(alpha = 0.6f)
                            else -> ColorSurfaceGray.copy(alpha = 0.8f)
                        }

                        drawLine(
                            color = tickColor,
                            start = Offset(pOuterX, pOuterY),
                            end = Offset(pInnerX, pInnerY),
                            strokeWidth = if (isCardinal || isIntercardinal) 2.dp.toPx() else 1.2.dp.toPx()
                        )

                        // Draw Cardinal text inside the arc
                        if (isCardinal || isIntercardinal) {
                            val textRadius = outerRadius - 15.dp.toPx()
                            val textX = centerX + textRadius * cosA
                            val textY = centerY + textRadius * sinA + 3.5.dp.toPx() // Optical baseline adjustment

                            val label = when (deg) {
                                0 -> "N"
                                45 -> "NE"
                                90 -> "E"
                                135 -> "SE"
                                180 -> "S"
                                225 -> "SW"
                                270 -> "W"
                                315 -> "NW"
                                else -> ""
                            }

                            if (deg == 0) {
                                paintCardinal.color = android.graphics.Color.parseColor("#FF3B30")
                                paintCardinal.textSize = 11.dp.toPx()
                            } else if (isCardinal) {
                                paintCardinal.color = android.graphics.Color.WHITE
                                paintCardinal.textSize = 10.dp.toPx()
                            } else {
                                paintCardinal.color = android.graphics.Color.parseColor("#00E5FF")
                                paintCardinal.textSize = 8.5.dp.toPx()
                            }

                            canvas.nativeCanvas.drawText(label, textX, textY, paintCardinal)
                        }
                    }
                }
            }

            // Fixed Center Lubber Indicator: Downward cyan pointer triangle ▼ at 12 o'clock
            val pointerTopY = centerY - outerRadius - 2.dp.toPx()
            val pointerBottomY = centerY - outerRadius + 6.dp.toPx()
            val pointerHalfWidth = 4.dp.toPx()

            val pointerPath = Path().apply {
                moveTo(centerX, pointerBottomY)
                lineTo(centerX - pointerHalfWidth, pointerTopY)
                lineTo(centerX + pointerHalfWidth, pointerTopY)
                close()
            }
            drawPath(path = pointerPath, color = ColorGarminCyan)
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
