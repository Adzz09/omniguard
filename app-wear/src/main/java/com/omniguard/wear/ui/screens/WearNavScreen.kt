package com.omniguard.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.omniguard.wear.ui.theme.WearDarkBackground
import com.omniguard.wear.ui.theme.WearNavCyan
import com.omniguard.wear.ui.theme.WearSafetyGreen
import com.omniguard.wear.ui.theme.WearTextSecondary

/**
 * Glanceable Turn-by-Turn Navigation Screen for Wear OS.
 * High-visibility maneuver iconography and distance readout optimized for cyclists and elderly walkers.
 */
@Composable
fun WearNavScreen(
    maneuverIcon: String = "↱", // e.g. "↑", "↰", "↱", "⮌", "★"
    distanceText: String = "50 m",
    streetName: String = "Grand Plaza Way",
    instruction: String = "Turn Right onto Grand Plaza Way",
    isArrival: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WearDarkBackground)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Big Bold Maneuver Icon
            Text(
                text = maneuverIcon,
                fontSize = 46.sp,
                fontWeight = FontWeight.Black,
                color = if (isArrival) WearSafetyGreen else WearNavCyan,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Prominent Distance
            Text(
                text = distanceText,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Street Name
            Text(
                text = streetName,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.85f)
            )

            // Instruction subtitle
            Text(
                text = instruction,
                style = MaterialTheme.typography.bodyExtraSmall,
                color = WearTextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.88f)
            )
        }
    }
}
