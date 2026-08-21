package com.omniguard.wear.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.Text
import com.omniguard.wear.ui.theme.WearDarkBackground
import com.omniguard.wear.ui.theme.WearEmergencyRed
import com.omniguard.wear.ui.theme.WearSurfaceDark
import com.omniguard.wear.ui.theme.WearTextSecondary
import com.omniguard.wear.ui.theme.WearWarningOrange

/**
 * High-contrast, glanceable Fall Countdown Screen for Wear OS.
 * Features prominent countdown ring, giant tactile Cancel button, and hardware crown hook hints.
 */
@Composable
fun FallCountdownScreen(
    remainingSeconds: Int,
    totalSeconds: Int,
    impactG: Float,
    onCancelClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (totalSeconds > 0) remainingSeconds.toFloat() / totalSeconds.toFloat() else 0f
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "CountdownProgress")
    val isUrgent = remainingSeconds <= 15

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WearDarkBackground)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer Countdown Progress Ring
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxSize(),
            startAngle = 270f,
            strokeWidth = 6.dp,
            colors = ProgressIndicatorDefaults.colors(
                indicatorColor = if (isUrgent) WearEmergencyRed else WearWarningOrange,
                trackColor = WearSurfaceDark
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Warning Title
            Text(
                text = "FALL DETECTED",
                style = MaterialTheme.typography.labelSmall,
                color = if (isUrgent) WearEmergencyRed else WearWarningOrange,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            // Giant Countdown Number
            Text(
                text = "$remainingSeconds",
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            // Large Glanceable Cancel Button
            Button(
                onClick = onCancelClicked,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(44.dp)
                    .clip(CircleShape),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    text = "I'M OK (CANCEL)",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Hardware Button Interceptor Hint
            Text(
                text = "Press Side Key or Crown",
                style = MaterialTheme.typography.bodyExtraSmall,
                color = WearTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
