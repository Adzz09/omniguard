package com.omniguard.android.ui.guidemehome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FmdGood
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omniguard.android.ui.theme.EmeraldSafe
import com.omniguard.android.ui.theme.TealAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideMeHomeMapScreen(
    uiState: GuideMeHomeUiState,
    onToggleLiveConsent: (Boolean) -> Unit,
    onEndEscort: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Guide Me Home Escort",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Interactive Map Simulator & Route Polyline
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF1E293B)) // Map Dark Canvas
            ) {
                // Route Canvas Polyline Simulation
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Grid lines (Mock street blocks)
                    for (i in 1..5) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.06f),
                            start = Offset(0f, h * i / 6),
                            end = Offset(w, h * i / 6),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.06f),
                            start = Offset(w * i / 6, 0f),
                            end = Offset(w * i / 6, h),
                            strokeWidth = 2f
                        )
                    }

                    // Safe Corridor Outer Buffer
                    val p1 = Offset(w * 0.25f, h * 0.75f)
                    val p2 = Offset(w * 0.45f, h * 0.55f)
                    val p3 = Offset(w * 0.50f, h * 0.35f)
                    val p4 = Offset(w * 0.75f, h * 0.20f)

                    val corridorColor = Color(0xFF0D9488).copy(alpha = 0.25f)
                    drawLine(corridorColor, p1, p2, strokeWidth = 36f, cap = StrokeCap.Round)
                    drawLine(corridorColor, p2, p3, strokeWidth = 36f, cap = StrokeCap.Round)
                    drawLine(corridorColor, p3, p4, strokeWidth = 36f, cap = StrokeCap.Round)

                    // Active Polyline Route
                    val routeColor = Color(0xFF2DD4BF)
                    drawLine(routeColor, p1, p2, strokeWidth = 8f, cap = StrokeCap.Round)
                    drawLine(routeColor, p2, p3, strokeWidth = 8f, cap = StrokeCap.Round)
                    drawLine(routeColor, p3, p4, strokeWidth = 8f, cap = StrokeCap.Round)

                    // Home Geofence Radar Circle
                    drawCircle(
                        color = Color(0xFF2E7D32).copy(alpha = 0.2f),
                        radius = 80f,
                        center = p4
                    )
                    drawCircle(
                        color = Color(0xFF2E7D32),
                        radius = 80f,
                        center = p4,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 3f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                        )
                    )
                }

                // Map Markers Overlays
                // Start Marker
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 60.dp, bottom = 70.dp)
                ) {
                    Surface(shape = CircleShape, color = Color(0xFF0284C7), modifier = Modifier.size(28.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.NearMe, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Home Geofence Marker
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 65.dp, top = 50.dp)
                ) {
                    Surface(shape = CircleShape, color = EmeraldSafe, modifier = Modifier.size(36.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Home, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Floating Map Controls Badge
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF2DD4BF), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Well-Lit Safe Corridor Active", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Bottom Navigation Details Card
            Card(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${uiState.etaMinutes} min (${uiState.distanceRemainingKm} km)",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "En route to: ${uiState.destinationName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "GPS ON",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Contact Sharing Consent Switch
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ShareLocation,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Live Guardian Escort Tracking",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (uiState.isLiveSharingConsentGranted) "Sharing encrypted live ETA with guardians" else "Location hidden from contacts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.isLiveSharingConsentGranted,
                                onCheckedChange = onToggleLiveConsent
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onEndEscort,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(
                            text = "End Safe Escort Navigation",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
