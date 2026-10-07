package com.argus.divaultra.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.argus.divaultra.core.DiveTelemetry
import com.argus.divaultra.core.NoFlyStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WatchfaceScreen(
    telemetry: DiveTelemetry,
    noFlyStatus: NoFlyStatus,
    onStartDive: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTimeMs by remember { mutableStateOf(System.currentTimeMillis()) }

    // Live clock ticker
    LaunchedEffect(Unit) {
        while (isActive) {
            currentTimeMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val secFormat = remember { SimpleDateFormat("ss", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEE · dd MMM", Locale.getDefault()) }

    val timeString = remember(currentTimeMs) { timeFormat.format(Date(currentTimeMs)) }
    val secString = remember(currentTimeMs) { secFormat.format(Date(currentTimeMs)) }
    val dateString = remember(currentTimeMs) { dateFormat.format(Date(currentTimeMs)).uppercase(Locale.US) }

    // Pulse animation for active no-fly icon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val planeAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "planeAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBackground),
        contentAlignment = Alignment.Center
    ) {
        // 1. Tactical Circular Outer Gauge
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 5.dp.toPx()
            val diameter = size.minDimension - strokeWidth * 2 - 8.dp.toPx()
            val radius = diameter / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // Background Outer Track
            drawCircle(
                color = ColorSurfaceGray.copy(alpha = 0.4f),
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth)
            )

            // Outer Tick Marks (Every 30 degrees)
            for (i in 0 until 12) {
                val angleRad = Math.toRadians((i * 30.0) - 90.0)
                val innerR = radius - 6.dp.toPx()
                val outerR = radius + 2.dp.toPx()
                val start = Offset(
                    (center.x + innerR * Math.cos(angleRad)).toFloat(),
                    (center.y + innerR * Math.sin(angleRad)).toFloat()
                )
                val end = Offset(
                    (center.x + outerR * Math.cos(angleRad)).toFloat(),
                    (center.y + outerR * Math.sin(angleRad)).toFloat()
                )
                drawLine(
                    color = if (i % 3 == 0) ColorGarminCyan else ColorTextMuted.copy(alpha = 0.5f),
                    start = start,
                    end = end,
                    strokeWidth = if (i % 3 == 0) 2.dp.toPx() else 1.dp.toPx()
                )
            }

            // No-Fly Progress Arc
            if (noFlyStatus.isNoFlyActive) {
                val sweep = noFlyStatus.progressFraction * 360f
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(ColorGarminRed, ColorGarminAmber, ColorGarminGreen)
                    ),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            } else {
                // Full Safe Neon Green Halo Ring
                drawCircle(
                    color = ColorGarminGreen,
                    radius = radius,
                    center = center,
                    style = Stroke(width = strokeWidth)
                )
            }
        }

        // 2. Primary Watchface Vertical Layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Top Row: Status Badges
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gas Mix Badge
                Box(
                    modifier = Modifier
                        .background(ColorSurfaceGray, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (noFlyStatus.lastDiveGasMix.isNotBlank() && noFlyStatus.lastDiveGasMix != "AIR")
                            noFlyStatus.lastDiveGasMix else "EAN${(telemetry.fractionO2 * 100).toInt()}",
                        color = ColorGarminGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Date
                Text(
                    text = dateString,
                    color = ColorTextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                // Surface Interval Pill
                Box(
                    modifier = Modifier
                        .background(ColorSurfaceGray, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "SURF ${noFlyStatus.formattedSurfaceInterval}",
                        color = ColorGarminCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Center Area: Tactical Digital Clock
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = timeString,
                        color = Color.White,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-1).sp
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = secString,
                        color = ColorGarminCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }

            // 3. ✈️ PADI No-Fly Tactical Complication Card
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .background(
                        if (noFlyStatus.isNoFlyActive) ColorCardBackground else Color(0xFF0D2418),
                        RoundedCornerShape(10.dp)
                    )
                    .border(
                        1.dp,
                        if (noFlyStatus.isNoFlyActive) ColorGarminAmber.copy(alpha = 0.6f) else ColorGarminGreen.copy(alpha = 0.6f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (noFlyStatus.isNoFlyActive) {
                        // Active No-Fly Countdown
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "✈️",
                                fontSize = 12.sp,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(
                                text = "NO FLY: ${noFlyStatus.formattedCountdown}",
                                color = ColorGarminAmber,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Reason & Permitted flight time
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = noFlyStatus.reason,
                                color = ColorTextMuted,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "FLY: ${noFlyStatus.formattedFlyPermittedTime}",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        // Safe To Fly Status
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "✈️ ", fontSize = 12.sp)
                            Text(
                                text = "OK TO FLY",
                                color = ColorGarminGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(
                            text = "DESATURATION COMPLETE · READY FOR AIR TRAVEL",
                            color = ColorTextMuted,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Last dive telemetry summary line if available
                    if (noFlyStatus.lastDiveMaxDepthMeters > 0) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = String.format(
                                Locale.US,
                                "LAST DIVE: %.1fM · %dM · %s",
                                noFlyStatus.lastDiveMaxDepthMeters,
                                noFlyStatus.lastDiveDurationSeconds / 60,
                                noFlyStatus.lastDiveGasMix
                            ),
                            color = ColorGarminCyan,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 4. Tactical Bottom Action Shortcuts
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // DIVE COMPUTER LAUNCH BUTTON
                Box(
                    modifier = Modifier
                        .background(ColorGarminCyan, RoundedCornerShape(12.dp))
                        .clickable { onStartDive() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "🌊 DIVE",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // DIVE LOGS BUTTON
                Box(
                    modifier = Modifier
                        .background(ColorSurfaceGray, RoundedCornerShape(12.dp))
                        .border(1.dp, ColorGarminCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .clickable { onOpenLogs() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "📖 LOGS",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // SETTINGS / NITROX BUTTON
                Box(
                    modifier = Modifier
                        .background(ColorSurfaceGray, RoundedCornerShape(12.dp))
                        .clickable { onOpenSettings() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "⚙️",
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
