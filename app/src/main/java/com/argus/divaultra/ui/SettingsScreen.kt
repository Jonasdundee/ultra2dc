package com.argus.divaultra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.Text
import com.argus.divaultra.core.DiveTelemetry
import java.util.Locale

@Composable
fun SettingsScreen(
    telemetry: DiveTelemetry,
    onGasSelected: (Double) -> Unit,
    onStartSimulation: () -> Unit,
    onReturnToDive: () -> Unit,
    onOpenLogs: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBackground),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp)
    ) {
        // 1. Header
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Text(
                    text = "PRE-DIVE SETUP",
                    color = ColorGarminCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "SWIPE OR TAP BELOW",
                    color = ColorTextMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // 2. Gas Mix & MOD Readout
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(ColorCardBackground, RoundedCornerShape(10.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val o2Percent = (telemetry.fractionO2 * 100).toInt()
                    val gasLabel = if (o2Percent == 21) "AIR (21%)" else "NITROX EAN$o2Percent"
                    Text(
                        text = gasLabel,
                        color = ColorGarminGreen,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = String.format(Locale.US, "MOD: %.1f M (PO2 1.4)", telemetry.modMeters),
                        color = ColorGarminCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 3. Quick Nitrox Presets
        item {
            Text(
                text = "POPULAR PRESETS",
                color = ColorTextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(0.95f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                GasPresetButton("28%", 0.28, telemetry.fractionO2, onGasSelected)
                GasPresetButton("29%", 0.29, telemetry.fractionO2, onGasSelected)
                GasPresetButton("30%", 0.30, telemetry.fractionO2, onGasSelected)
                GasPresetButton("32%", 0.32, telemetry.fractionO2, onGasSelected)
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                GasPresetButton("AIR", 0.21, telemetry.fractionO2, onGasSelected)
                GasPresetButton("34%", 0.34, telemetry.fractionO2, onGasSelected)
                GasPresetButton("36%", 0.36, telemetry.fractionO2, onGasSelected)
                GasPresetButton("40%", 0.40, telemetry.fractionO2, onGasSelected)
            }
        }

        // 4. Precise 1% O2 Stepper (+ / -)
        item {
            val currentPct = kotlin.math.round(telemetry.fractionO2 * 100).toInt()
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CompactButton(
                    onClick = {
                        val next = (currentPct - 1).coerceIn(21, 40)
                        onGasSelected(next / 100.0)
                    },
                    colors = ButtonDefaults.secondaryButtonColors(backgroundColor = ColorSurfaceGray)
                ) {
                    Text("-1%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }

                Text(
                    text = "$currentPct% O2",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )

                CompactButton(
                    onClick = {
                        val next = (currentPct + 1).coerceIn(21, 40)
                        onGasSelected(next / 100.0)
                    },
                    colors = ButtonDefaults.secondaryButtonColors(backgroundColor = ColorSurfaceGray)
                ) {
                    Text("+1%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        // 5. Back to Dive Screen Button
        item {
            Button(
                onClick = onReturnToDive,
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

        // 6. View Dive Logs Button
        item {
            Button(
                onClick = onOpenLogs,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(top = 6.dp),
                colors = ButtonDefaults.secondaryButtonColors(backgroundColor = ColorSurfaceGray)
            ) {
                Text(
                    text = "📖 VIEW DIVE LOGS",
                    color = ColorGarminGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        // 6. Dry-Land Demo Mode (Separated Safely)
        item {
            Button(
                onClick = onStartSimulation,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(top = 6.dp),
                colors = ButtonDefaults.secondaryButtonColors(backgroundColor = ColorSurfaceGray)
            ) {
                Text(
                    text = "DRY-LAND SIMULATION",
                    color = ColorGarminAmber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun GasPresetButton(
    label: String,
    fraction: Double,
    currentFraction: Double,
    onSelect: (Double) -> Unit
) {
    val isSelected = kotlin.math.abs(fraction - currentFraction) < 0.005
    val bgColor = if (isSelected) ColorGarminGreen else ColorSurfaceGray
    val txtColor = if (isSelected) Color.Black else Color.White

    Button(
        onClick = { onSelect(fraction) },
        modifier = Modifier.size(width = 46.dp, height = 34.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = bgColor),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = label,
            color = txtColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
    }
}
