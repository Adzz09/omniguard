package com.omniguard.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
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
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.omniguard.wear.ui.theme.WearDarkBackground
import com.omniguard.wear.ui.theme.WearEmergencyRed
import com.omniguard.wear.ui.theme.WearSurfaceDark
import com.omniguard.wear.ui.theme.WearTextSecondary

/**
 * Wear OS Silent SOS Screen.
 * Provides triple-tap gesture area and physical side button guidance for emergency panic dispatch.
 */
@Composable
fun WearSilentSosScreen(
    isDispatched: Boolean,
    onTripleTapTriggered: () -> Unit,
    onDismissOrReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

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
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Text(
                text = if (isDispatched) "SILENT SOS SENT" else "STEALTH SOS",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isDispatched) Color.White else WearEmergencyRed,
                textAlign = TextAlign.Center
            )

            // Central Touch Action Area
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(if (isDispatched) WearSurfaceDark else WearEmergencyRed)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onTripleTapTriggered
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isDispatched) "✔\nALERTED" else "TAP 3X\nFOR SOS",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }

            // Hardware Instructions
            Text(
                text = if (isDispatched) "Dispatched covertly" else "Or press Side Button 3x",
                style = MaterialTheme.typography.bodyExtraSmall,
                color = WearTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
