package com.argus.divaultra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.wear.compose.material.Text

@Composable
fun EulaScreen(
    onAccept: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ColorBackground),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 20.dp)
    ) {
        // 1. Header
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = "⚠️ SAFETY NOTICE",
                    color = ColorGarminAmber,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "TERMS & DISCLAIMER",
                    color = ColorTextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // 2. Primary Disclaimer Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .background(ColorCardBackground, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Text(
                        text = "SECONDARY AID ONLY",
                        color = ColorGarminCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Argus Dive is a secondary planning and logging aid only. It is NOT a certified primary dive computer and MUST NEVER replace a certified primary dive computer, pressure gauge, and depth-time tables.",
                        color = ColorTextPrimary,
                        fontSize = 10.sp,
                        lineHeight = 13.sp
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(6.dp)) }

        // 3. No DCS Warranty Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .background(ColorCardBackground, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Text(
                        text = "NO DCS WARRANTY",
                        color = ColorGarminRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Decompression models (Bühlmann ZHL-16C) are mathematical estimates. DCS can occur even within limits. Calculations do NOT guarantee prevention of decompression sickness or hyperoxia.",
                        color = ColorTextPrimary,
                        fontSize = 10.sp,
                        lineHeight = 13.sp
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(6.dp)) }

        // 4. Gas & Hardware
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .background(ColorCardBackground, RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Text(
                        text = "GAS & SENSORS",
                        color = ColorGarminAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Diver must personally analyze all breathing gases. Hardware pressure ratings are Samsung's, not Argus. Compass & calories are rough aids only.",
                        color = ColorTextPrimary,
                        fontSize = 10.sp,
                        lineHeight = 13.sp
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(10.dp)) }

        // 5. Mandatory Acceptance Button
        item {
            Button(
                onClick = onAccept,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(44.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = ColorGarminGreen,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = "I ACCEPT & DIVE SAFE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        item {
            Text(
                text = "Scroll down & tap to confirm",
                color = ColorTextMuted,
                fontSize = 8.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
        }
    }
}
