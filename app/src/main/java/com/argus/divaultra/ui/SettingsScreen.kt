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
    onGasSelected: (Double, Boolean) -> Unit,
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp)
    ) {
        // 1. Header
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Text(
                    text = "PRE-DIVE SETUP",
                    color = ColorGarminCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "GAS SELECTION & SAFETY",
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
                    .fillMaxWidth(0.92f)
                    .background(ColorCardBackground, RoundedCornerShape(10.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val o2Percent = (telemetry.fractionO2 * 100).toInt()
                    val gasLabel = if (o2Percent == 21) "AIR (21% O2)" else "NITROX EAN$o2Percent"
                    Text(
                        text = gasLabel,
                        color = ColorGarminGreen,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = String.format(Locale.US, "MOD: %.1f M (PO2 1.4 MAX)", telemetry.modMeters),
                        color = ColorGarminCyan,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 3. Gas Analysis Confirmation Card (Claude Safety Requirement)
        item {
            val isNitrox = telemetry.fractionO2 > 0.215
            if (isNitrox) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(top = 4.dp)
                        .background(
                            if (telemetry.isGasConfirmed) ColorCardBackground else Color(0x33FFAA00),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (telemetry.isGasConfirmed) {
                            Text(
                                text = "✅ O2 ANALYZED & CONFIRMED",
                                color = ColorGarminGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        } else {
                            Text(
                                text = "⚠️ UNCONFIRMED NITROX",
                                color = ColorGarminAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Defaults to Air unless personally confirmed",
                                color = ColorTextPrimary,
                                fontSize = 8.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = { onGasSelected(telemetry.fractionO2, true) },
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(28.dp),
                                colors = ButtonDefaults.buttonColors(backgroundColor = ColorGarminAmber, contentColor = Color.Black),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "CONFIRM ANALYZED %",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Quick Nitrox Presets
        item {
            Text(
                text = "POPULAR PRESETS",
                color = ColorTextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(0.95f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                GasPresetButton("AIR", 0.21, telemetry.fractionO2) { mix -> onGasSelected(mix, true) }
                GasPresetButton("28%", 0.28, telemetry.fractionO2) { mix -> onGasSelected(mix, false) }
                GasPresetButton("30%", 0.30, telemetry.fractionO2) { mix -> onGasSelected(mix, false) }
                GasPresetButton("32%", 0.32, telemetry.fractionO2) { mix -> onGasSelected(mix, false) }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                GasPresetButton("34%", 0.34, telemetry.fractionO2) { mix -> onGasSelected(mix, false) }
                GasPresetButton("36%", 0.36, telemetry.fractionO2) { mix -> onGasSelected(mix, false) }
                GasPresetButton("38%", 0.38, telemetry.fractionO2) { mix -> onGasSelected(mix, false) }
                GasPresetButton("40%", 0.40, telemetry.fractionO2) { mix -> onGasSelected(mix, false) }
            }
        }

        // 5. Precise 1% O2 Stepper (+ / -)
        item {
            val currentPct = kotlin.math.round(telemetry.fractionO2 * 100).toInt()
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CompactButton(
                    onClick = {
                        val next = (currentPct - 1).coerceIn(21, 40)
                        val isAir = next == 21
                        onGasSelected(next / 100.0, isAir)
                    },
                    colors = ButtonDefaults.secondaryButtonColors(backgroundColor = ColorSurfaceGray)
                ) {
                    Text("-1%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }

                Text(
                    text = "$currentPct% O2",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )

                CompactButton(
                    onClick = {
                        val next = (currentPct + 1).coerceIn(21, 40)
                        onGasSelected(next / 100.0, false)
                    },
                    colors = ButtonDefaults.secondaryButtonColors(backgroundColor = ColorSurfaceGray)
                ) {
                    Text("+1%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        // 6. Back to Dive Screen Button
        item {
            Button(
                onClick = onReturnToDive,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(top = 6.dp),
                colors = ButtonDefaults.primaryButtonColors(backgroundColor = ColorGarminCyan)
            ) {
                Text(
                    text = "DIVE SCREEN",
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp
                )
            }
        }

        // 7. View Dive Logs Button
        item {
            Button(
                onClick = onOpenLogs,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(top = 4.dp),
                colors = ButtonDefaults.secondaryButtonColors(backgroundColor = ColorSurfaceGray)
            ) {
                Text(
                    text = "📖 VIEW DIVE LOGS",
                    color = ColorGarminGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }

        // 8. Dry-Land Demo Mode
        item {
            Button(
                onClick = onStartSimulation,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(top = 4.dp),
                colors = ButtonDefaults.secondaryButtonColors(backgroundColor = ColorSurfaceGray)
            ) {
                Text(
                    text = "DRY-LAND SIMULATION",
                    color = ColorGarminAmber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }

        // 9. Legal Disclaimers in Footer
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
            ) {
                Text(
                    text = "SECONDARY GAUGE ONLY",
                    color = ColorTextMuted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Calories estimated. Compass rough aid.",
                    color = ColorTextMuted,
                    fontSize = 7.sp
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
        modifier = Modifier.size(width = 44.dp, height = 32.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = bgColor),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = label,
            color = txtColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
    }
}
