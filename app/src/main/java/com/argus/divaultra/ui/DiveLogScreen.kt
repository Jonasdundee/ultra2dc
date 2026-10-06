package com.argus.divaultra.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import com.argus.divaultra.log.DiveLogDetails
import com.argus.divaultra.log.DiveLogManager
import com.argus.divaultra.log.DiveLogSummary
import com.argus.divaultra.log.DiveProfileSample
import java.util.Locale

@Composable
fun DiveLogScreen(
    logManager: DiveLogManager,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedDiveId by remember { mutableStateOf<String?>(null) }
    var logs by remember { mutableStateOf<List<DiveLogSummary>>(emptyList()) }

    LaunchedEffect(Unit) {
        logs = logManager.getAllDiveLogs()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBackground),
        contentAlignment = Alignment.Center
    ) {
        if (selectedDiveId != null) {
            val details = remember(selectedDiveId) { logManager.getDiveLogDetails(selectedDiveId!!) }
            if (details != null) {
                DiveLogDetailView(
                    details = details,
                    onBack = { selectedDiveId = null }
                )
            } else {
                selectedDiveId = null
            }
        } else {
            DiveLogListView(
                logs = logs,
                onSelectDive = { id -> selectedDiveId = id },
                onClose = onClose
            )
        }
    }
}

@Composable
private fun DiveLogListView(
    logs: List<DiveLogSummary>,
    onSelectDive: (String) -> Unit,
    onClose: () -> Unit
) {
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 24.dp)
    ) {
        // Title Header
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Text(
                    text = "📖 DIVE LOGS",
                    color = ColorGarminCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${logs.size} RECORDED DIVES",
                    color = ColorTextMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (logs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(ColorCardBackground, RoundedCornerShape(10.dp))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "NO DIVES LOGGED YET",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Dives save automatically after a 5-min surface interval",
                            color = ColorTextMuted,
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(logs.size) { index ->
                val log = logs[index]
                DiveLogSummaryCard(
                    log = log,
                    diveNumber = logs.size - index,
                    onClick = { onSelectDive(log.diveId) }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // Close / Back Button
        item {
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(top = 8.dp),
                colors = ButtonDefaults.primaryButtonColors(backgroundColor = ColorGarminCyan)
            ) {
                Text(
                    text = "DIVE SCREEN",
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun DiveLogSummaryCard(
    log: DiveLogSummary,
    diveNumber: Int,
    onClick: () -> Unit
) {
    val durationMin = log.durationSeconds / 60
    val durationSec = log.durationSeconds % 60

    Box(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .background(ColorCardBackground, RoundedCornerShape(10.dp))
            .border(1.dp, ColorSurfaceGray, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Column {
            // Row 1: Dive Number & Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DIVE #$diveNumber",
                    color = ColorGarminCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = log.startTimeFormatted,
                    color = ColorTextMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Row 2: Max Depth, Duration, Gas Mix
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Max Depth
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format(Locale.US, "%.1f", log.maxDepthMeters),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = " M",
                        color = ColorGarminCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Duration
                Text(
                    text = String.format(Locale.US, "%02d:%02d", durationMin, durationSec),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                // Gas Mix Badge
                Box(
                    modifier = Modifier
                        .background(ColorSurfaceGray, RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = log.gasMix,
                        color = ColorGarminGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Row 3: Safety stop badge if completed
            if (log.safetyStopCompleted) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "✅ 3-MIN SAFETY STOP COMPLETED",
                        color = ColorGarminGreen,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun DiveLogDetailView(
    details: DiveLogDetails,
    onBack: () -> Unit
) {
    val summary = details.summary
    val durationMin = summary.durationSeconds / 60
    val durationSec = summary.durationSeconds % 60
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 20.dp)
    ) {
        // Back Button
        item {
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(32.dp),
                colors = ButtonDefaults.secondaryButtonColors(backgroundColor = ColorSurfaceGray)
            ) {
                Text(
                    text = "← BACK TO LIST",
                    color = ColorGarminCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Header Title
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            ) {
                Text(
                    text = summary.startTimeFormatted,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "MIX: ${summary.gasMix} • ${summary.sampleCount} SAMPLES",
                    color = ColorGarminGreen,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Primary Metrics Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .background(ColorCardBackground, RoundedCornerShape(10.dp))
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
                                text = String.format(Locale.US, "%.1f M", summary.maxDepthMeters),
                                color = ColorGarminCyan,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("DURATION", color = ColorTextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = String.format(Locale.US, "%02d:%02d", durationMin, durationSec),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("AVG DEPTH", color = ColorTextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = String.format(Locale.US, "%.1f M", summary.avgDepthMeters),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("WATER TEMP", color = ColorTextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = String.format(Locale.US, "%.1f °C", summary.waterTempCelsius),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (summary.safetyStopCompleted) "SAFETY STOP: COMPLETED ✅" else "SAFETY STOP: NONE",
                        color = if (summary.safetyStopCompleted) ColorGarminGreen else ColorTextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Visual Depth Profile Graph
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "DEPTH PROFILE",
                    color = ColorTextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(2.dp))
                DiveProfileCanvas(
                    samples = details.samples,
                    maxDepth = summary.maxDepthMeters,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ColorCardBackground)
                        .padding(6.dp)
                )
            }
        }

        // GPS Coordinates (if available)
        if (summary.entryGpsFormatted != null || summary.exitGpsFormatted != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .background(ColorCardBackground, RoundedCornerShape(8.dp))
                        .padding(6.dp)
                ) {
                    Column {
                        summary.entryGpsFormatted?.let {
                            Text("ENTRY GPS: $it", color = ColorGarminCyan, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                        }
                        summary.exitGpsFormatted?.let {
                            Text("EXIT GPS:  $it", color = ColorGarminGreen, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        // Return Button
        item {
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(top = 4.dp),
                colors = ButtonDefaults.primaryButtonColors(backgroundColor = ColorGarminCyan)
            ) {
                Text("DONE", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun DiveProfileCanvas(
    samples: List<DiveProfileSample>,
    maxDepth: Double,
    modifier: Modifier = Modifier
) {
    if (samples.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("NO PROFILE DATA", color = ColorTextMuted, fontSize = 9.sp)
        }
        return
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val safeMaxDepth = maxOf(1.0, maxDepth)
        val maxSec = maxOf(1L, samples.last().second)

        // Draw horizontal grid lines (0m, mid depth, max depth)
        val gridColor = Color(0xFF1E2E28)
        drawLine(gridColor, Offset(0f, 0f), Offset(w, 0f), strokeWidth = 1f)
        drawLine(gridColor, Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 1f)
        drawLine(gridColor, Offset(0f, h), Offset(w, h), strokeWidth = 1f)

        // Draw Safety Stop corridor band (3m - 6m)
        if (safeMaxDepth >= 6.0) {
            val y3m = (3.0 / safeMaxDepth).toFloat() * h
            val y6m = (6.0 / safeMaxDepth).toFloat() * h
            drawRect(
                color = ColorGarminAmber.copy(alpha = 0.15f),
                topLeft = Offset(0f, y3m),
                size = Size(w, y6m - y3m)
            )
        }

        val path = Path()
        val fillPath = Path()

        samples.forEachIndexed { index, sample ->
            val x = (sample.second.toFloat() / maxSec.toFloat()) * w
            val y = (sample.depthMeters.toFloat() / safeMaxDepth.toFloat()) * h

            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, 0f)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        fillPath.lineTo(w, 0f)
        fillPath.close()

        // Depth Area Fill
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(ColorGarminCyan.copy(alpha = 0.35f), ColorGarminCyan.copy(alpha = 0.05f)),
                startY = 0f,
                endY = h
            )
        )

        // Depth Profile Line
        drawPath(
            path = path,
            color = ColorGarminCyan,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw Dot at deepest point
        val deepestSample = samples.maxByOrNull { it.depthMeters }
        if (deepestSample != null) {
            val mx = (deepestSample.second.toFloat() / maxSec.toFloat()) * w
            val my = (deepestSample.depthMeters.toFloat() / safeMaxDepth.toFloat()) * h
            drawCircle(color = ColorGarminAmber, radius = 3.dp.toPx(), center = Offset(mx, my))
        }
    }
}
