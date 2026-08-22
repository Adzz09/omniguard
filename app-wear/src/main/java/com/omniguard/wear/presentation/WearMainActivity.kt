package com.omniguard.wear.presentation

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.omniguard.wear.ui.screens.FallCountdownScreen
import com.omniguard.wear.ui.screens.WearNavScreen
import com.omniguard.wear.ui.screens.WearSilentSosScreen
import com.omniguard.wear.ui.screens.WearStatusScreen
import com.omniguard.wear.ui.theme.OmniGuardWearTheme

class WearMainActivity : ComponentActivity() {

    private val viewModel = WearMainViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            OmniGuardWearTheme {
                val uiState by viewModel.uiState.collectAsState()

                when (uiState.currentRoute) {
                    WearScreenRoute.STATUS -> {
                        WearStatusScreen(
                            locationName = uiState.statusLocation,
                            arrivalTimestamp = uiState.statusTime,
                            batteryPercent = uiState.batteryPercent
                        )
                    }
                    WearScreenRoute.FALL_COUNTDOWN -> {
                        FallCountdownScreen(
                            remainingSeconds = uiState.fallRemainingSeconds,
                            totalSeconds = uiState.fallTotalSeconds,
                            impactG = uiState.fallImpactG,
                            onCancelClicked = { viewModel.cancelFallAlarm() }
                        )
                    }
                    WearScreenRoute.NAVIGATION -> {
                        WearNavScreen(
                            maneuverIcon = uiState.navManeuverIcon,
                            distanceText = uiState.navDistanceText,
                            streetName = uiState.navStreetName,
                            instruction = uiState.navInstruction,
                            isArrival = uiState.isArrival
                        )
                    }
                    WearScreenRoute.SILENT_SOS -> {
                        WearSilentSosScreen(
                            isDispatched = uiState.isSilentSosDispatched,
                            onTripleTapTriggered = { viewModel.triggerSilentSos() },
                            onDismissOrReset = { viewModel.updateGeofenceStatus("Home", "Safe") }
                        )
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (viewModel.handleHardwareKey(keyCode)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
