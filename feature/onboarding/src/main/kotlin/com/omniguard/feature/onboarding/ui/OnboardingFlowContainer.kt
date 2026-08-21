package com.omniguard.feature.onboarding.ui

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omniguard.feature.onboarding.viewmodel.OnboardingStep
import com.omniguard.feature.onboarding.viewmodel.OnboardingViewModel

@Composable
fun OnboardingFlowContainer(
    viewModel: OnboardingViewModel,
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Crossfade(targetState = uiState.currentStep, label = "OnboardingStepFade", modifier = modifier) { step ->
        when (step) {
            OnboardingStep.ROLE_SELECTION -> {
                RoleSelectionScreen(
                    selectedRole = uiState.selectedRole,
                    onRoleSelected = viewModel::selectRole,
                    onContinue = viewModel::proceedToNextStep
                )
            }
            OnboardingStep.WATCH_PAIRING -> {
                WatchPairingScreen(
                    watchState = uiState.watchState,
                    isScanning = uiState.isScanningWatch,
                    onStartScan = viewModel::startWatchPairingScan,
                    onTestHaptic = viewModel::testWatchHapticPulse,
                    onContinue = viewModel::proceedToNextStep,
                    onSkip = viewModel::proceedToNextStep
                )
            }
            OnboardingStep.PERMISSIONS -> {
                PermissionScreen(
                    permissions = uiState.permissions,
                    onTogglePermission = viewModel::togglePermission,
                    onGrantAll = viewModel::grantAllPermissions,
                    onContinue = viewModel::proceedToNextStep
                )
            }
            OnboardingStep.EMERGENCY_CONTACTS -> {
                EmergencyContactsScreen(
                    contacts = uiState.contacts,
                    onAddContact = viewModel::addContact,
                    onRemoveContact = viewModel::removeContact,
                    onToggleGeofenceAlert = viewModel::updateContactGeofenceAlert,
                    onContinue = viewModel::proceedToNextStep
                )
            }
            OnboardingStep.HARDWARE_DRY_RUN -> {
                HardwareDryRunScreen(
                    dryRunStage = uiState.dryRunStage,
                    countdownSeconds = uiState.countdownSecondsRemaining,
                    impactForceG = uiState.simulatedImpactForceG,
                    resultMessage = uiState.dryRunResultMessage,
                    onStartSimulation = viewModel::startFallSimulation,
                    onPressSideButton = viewModel::triggerHardwareSideButtonPress,
                    onCancelTest = viewModel::cancelDryRun,
                    onResetTest = viewModel::resetDryRun,
                    onFinishOnboarding = onOnboardingComplete
                )
            }
            OnboardingStep.COMPLETED -> {
                onOnboardingComplete()
            }
        }
    }
}
