package com.omniguard.android.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.omniguard.android.OmniGuardApplication
import com.omniguard.android.ui.duress.DuressPinScreen
import com.omniguard.android.ui.duress.DuressPinViewModel
import com.omniguard.android.ui.guidemehome.GuideMeHomeMapScreen
import com.omniguard.android.ui.guidemehome.GuideMeHomeViewModel
import com.omniguard.android.ui.home.HomeScreen
import com.omniguard.android.ui.home.HomeViewModel
import com.omniguard.android.ui.safezones.SafeZonesScreen
import com.omniguard.android.ui.transit.TransitLogsScreen
import com.omniguard.android.ui.transit.TransitLogsViewModel
import com.omniguard.feature.geofencing.ui.SafeZonesViewModel
import com.omniguard.feature.onboarding.ui.OnboardingFlowContainer
import com.omniguard.feature.onboarding.viewmodel.OnboardingViewModel

@Composable
fun OmniGuardNavHost(
    navController: NavHostController,
    app: OmniGuardApplication,
    startDestination: String = Screen.Onboarding.route,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Onboarding.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val onboardingVm: OnboardingViewModel = viewModel()
            OnboardingFlowContainer(
                viewModel = onboardingVm,
                onOnboardingComplete = {
                    try {
                        OmniGuardForegroundService.startService(context)
                    } catch (e: Exception) {
                        // Handled safely
                    }
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            val homeVm: HomeViewModel = viewModel {
                HomeViewModel(
                    geofenceManager = app.geofenceManager,
                    emergencyDispatcher = app.emergencyDispatcher
                )
            }
            val uiState by homeVm.uiState.collectAsStateWithLifecycle()

            HomeScreen(
                uiState = uiState,
                onStartSos = homeVm::startSosCountdown,
                onCancelSos = homeVm::cancelSos,
                onDismissAlert = homeVm::dismissBroadcastBanner,
                onNavigateToGuideMeHome = { navController.navigate(Screen.GuideMeHome.route) },
                onNavigateToSafeZones = { navController.navigate(Screen.SafeZones.route) },
                onNavigateToTransitLogs = { navController.navigate(Screen.TransitLogs.route) },
                onNavigateToDuressPin = { navController.navigate(Screen.DuressPin.route) }
            )
        }

        composable(Screen.GuideMeHome.route) {
            val guideVm: GuideMeHomeViewModel = viewModel {
                GuideMeHomeViewModel(geofenceManager = app.geofenceManager)
            }
            val uiState by guideVm.uiState.collectAsStateWithLifecycle()

            GuideMeHomeMapScreen(
                uiState = uiState,
                onToggleLiveConsent = guideVm::toggleLiveSharingConsent,
                onEndEscort = {
                    guideVm.endNavigation()
                    navController.popBackStack()
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SafeZones.route) {
            val safeZonesVm: SafeZonesViewModel = viewModel {
                SafeZonesViewModel(geofenceManager = app.geofenceManager)
            }
            val uiState by safeZonesVm.uiState.collectAsStateWithLifecycle()

            SafeZonesScreen(
                uiState = uiState,
                onStartCreateZone = { safeZonesVm.startNewZoneCreation() },
                onDismissCreateDialog = safeZonesVm::dismissCreationDialog,
                onUpdateDraftName = safeZonesVm::updateDraftName,
                onUpdateDraftRadius = safeZonesVm::updateDraftRadius,
                onToggleDraftDay = safeZonesVm::toggleDraftDay,
                onSaveDraftZone = safeZonesVm::saveDraftZone,
                onToggleZoneEnabled = safeZonesVm::toggleZoneEnabled,
                onDeleteZone = safeZonesVm::deleteZone,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.DuressPin.route) {
            val duressVm: DuressPinViewModel = viewModel {
                DuressPinViewModel(
                    emergencyDispatcher = app.emergencyDispatcher,
                    geofenceManager = app.geofenceManager
                )
            }
            val uiState by duressVm.uiState.collectAsStateWithLifecycle()

            DuressPinScreen(
                uiState = uiState,
                onDigitClick = duressVm::appendDigit,
                onDeleteClick = duressVm::deleteDigit,
                onClearClick = duressVm::clearPin,
                onExitDummy = duressVm::exitDummyScreen,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.TransitLogs.route) {
            val transitVm: TransitLogsViewModel = viewModel {
                TransitLogsViewModel(geofenceManager = app.geofenceManager)
            }
            val uiState by transitVm.uiState.collectAsStateWithLifecycle()

            TransitLogsScreen(
                uiState = uiState,
                onFilterSelect = transitVm::setFilter,
                onInspectLog = transitVm::inspectLog,
                onDismissInspect = transitVm::dismissInspectDialog,
                onPurgeExpired = transitVm::purgeExpiredLogs,
                onClearAll = transitVm::clearAllLogs,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
