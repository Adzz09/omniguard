package com.omniguard.wear

import com.omniguard.wear.presentation.WearMainViewModel
import com.omniguard.wear.presentation.WearScreenRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WearMainViewModelTest {

    @Test
    fun testFallDetectionWorkflowOnWear() = runTest {
        val viewModel = WearMainViewModel(viewModelScope = backgroundScope)

        viewModel.onFallDetected(impactG = 4.2f, countdownSeconds = 60)
        assertEquals(WearScreenRoute.FALL_COUNTDOWN, viewModel.uiState.value.currentRoute)
        assertTrue(viewModel.uiState.value.isFallAlarmActive)
        assertEquals(60, viewModel.uiState.value.fallRemainingSeconds)

        viewModel.onFallCountdownTick(45)
        assertEquals(45, viewModel.uiState.value.fallRemainingSeconds)

        viewModel.cancelFallAlarm()
        assertFalse(viewModel.uiState.value.isFallAlarmActive)
        assertEquals(WearScreenRoute.STATUS, viewModel.uiState.value.currentRoute)
    }

    @Test
    fun testWearNavigationSyncUpdates() = runTest {
        val viewModel = WearMainViewModel(viewModelScope = backgroundScope)

        viewModel.updateNavigation(
            icon = "↱",
            distance = "30 m",
            street = "Grand Plaza Way",
            instruction = "Turn Right in 30 meters"
        )

        assertEquals(WearScreenRoute.NAVIGATION, viewModel.uiState.value.currentRoute)
        assertEquals("↱", viewModel.uiState.value.navManeuverIcon)
        assertEquals("30 m", viewModel.uiState.value.navDistanceText)
        assertEquals("Grand Plaza Way", viewModel.uiState.value.navStreetName)
    }

    @Test
    fun testWearGeofenceStatusUpdate() = runTest {
        val viewModel = WearMainViewModel(viewModelScope = backgroundScope)

        viewModel.updateGeofenceStatus("Tuition", "4:02 PM")
        assertEquals(WearScreenRoute.STATUS, viewModel.uiState.value.currentRoute)
        assertEquals("Tuition", viewModel.uiState.value.statusLocation)
        assertEquals("4:02 PM", viewModel.uiState.value.statusTime)
    }
}
