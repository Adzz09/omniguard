package com.omniguard.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.omniguard.wear.ui.theme.WearDarkBackground
import com.omniguard.wear.ui.theme.WearSafetyGreen
import com.omniguard.wear.ui.theme.WearSurfaceDark
import com.omniguard.wear.ui.theme.WearTextSecondary

/**
 * Status and Geofence Confirmation Screen for Wear OS.
 * Displays clear arrival check-in updates (e.g. "Arrived at Tuition at 4:02 PM").
 */
@Composable
fun WearStatusScreen(
    locationName: String = "Tuition",
    arrivalTimestamp: String = "4:02 PM",
    isSafe: Boolean = true,
    batteryPercent: Int = 88,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WearDarkBackground)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Safe Indicator Badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isSafe) WearSafetyGreen.copy(alpha = 0.2f) else WearSurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isSafe) "✓" else "!",
                    color = if (isSafe) WearSafetyGreen else Color.Yellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Status Headline
            Text(
                text = "Arrived at $locationName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            // Arrival Time
            Text(
                text = "at $arrivalTimestamp",
                style = MaterialTheme.typography.bodyMedium,
                color = WearSafetyGreen,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Guardian Notification Confirmation & Battery
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Parents Notified",
                    style = MaterialTheme.typography.bodyExtraSmall,
                    color = WearTextSecondary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "•  🔋 $batteryPercent%",
                    style = MaterialTheme.typography.bodyExtraSmall,
                    color = WearTextSecondary
                )
            }
        }
    }
}
