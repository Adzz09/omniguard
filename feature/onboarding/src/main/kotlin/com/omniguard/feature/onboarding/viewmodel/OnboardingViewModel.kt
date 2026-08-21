package com.omniguard.feature.onboarding.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniguard.core.model.ContactRelationship
import com.omniguard.core.model.EmergencyContact
import com.omniguard.core.model.UserRole
import com.omniguard.core.model.WatchState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class OnboardingStep {
    ROLE_SELECTION,
    WATCH_PAIRING,
    PERMISSIONS,
    EMERGENCY_CONTACTS,
    HARDWARE_DRY_RUN,
    COMPLETED
}

@Immutable
data class PermissionItem(
    val id: String,
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val isMandatory: Boolean = true,
    val rationale: String
)

enum class DryRunStage {
    READY_TO_TEST,
    SIMULATING_FALL,
    ALERT_COUNTDOWN,
    VERIFIED_SUCCESS,
    CANCELLED_BY_USER
}

@Immutable
data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.ROLE_SELECTION,
    val selectedRole: UserRole = UserRole.STUDENT,
    val isScanningWatch: Boolean = false,
    val watchState: WatchState = WatchState(isConnected = false, batteryPercent = 0, rssiDb = 0),
    val permissions: List<PermissionItem> = listOf(
        PermissionItem(
            id = "location_always",
            title = "Background Location (Always)",
            description = "Required for passive geofencing & safe zone schedule tracking",
            isGranted = false,
            isMandatory = true,
            rationale = "Allows OmniGuard to verify when you safely enter or leave scheduled zones like Campus or Work."
        ),
        PermissionItem(
            id = "motion_sensors",
            title = "Motion & Physical Activity",
            description = "Enables wearable & device fall/impact accelerometer heuristics",
            isGranted = false,
            isMandatory = true,
            rationale = "Powers the high-accuracy fall and crash detection algorithm."
        ),
        PermissionItem(
            id = "notifications",
            title = "Critical Notifications",
            description = "Bypass Do-Not-Disturb for active emergency countdowns",
            isGranted = false,
            isMandatory = true,
            rationale = "Ensures you never miss a fall countdown or safe zone alert."
        ),
        PermissionItem(
            id = "sms_fallback",
            title = "SMS Emergency Dispatch",
            description = "Direct fallback when data or cell roaming is unavailable",
            isGranted = false,
            isMandatory = false,
            rationale = "Sends direct SMS distress links to trusted contacts in low-connectivity areas."
        )
    ),
    val contacts: List<EmergencyContact> = listOf(
        EmergencyContact(
            id = UUID.randomUUID().toString(),
            name = "Mom (Primary)",
            phoneNumber = "+1 (555) 234-5678",
            relationship = ContactRelationship.PARENT,
            notifyOnGeofence = true,
            notifyOnFall = true,
            priority = 1
        )
    ),
    val dryRunStage: DryRunStage = DryRunStage.READY_TO_TEST,
    val countdownSecondsRemaining: Int = 15,
    val simulatedImpactForceG: Float = 0f,
    val dryRunResultMessage: String? = null,
    val isOnboardingComplete: Boolean = false
)

class OnboardingViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    fun selectRole(role: UserRole) {
        _uiState.update { it.copy(selectedRole = role) }
    }

    fun proceedToNextStep() {
        val next = when (_uiState.value.currentStep) {
            OnboardingStep.ROLE_SELECTION -> OnboardingStep.WATCH_PAIRING
            OnboardingStep.WATCH_PAIRING -> OnboardingStep.PERMISSIONS
            OnboardingStep.PERMISSIONS -> OnboardingStep.EMERGENCY_CONTACTS
            OnboardingStep.EMERGENCY_CONTACTS -> OnboardingStep.HARDWARE_DRY_RUN
            OnboardingStep.HARDWARE_DRY_RUN -> {
                _uiState.update { it.copy(isOnboardingComplete = true) }
                OnboardingStep.COMPLETED
            }
            OnboardingStep.COMPLETED -> OnboardingStep.COMPLETED
        }
        _uiState.update { it.copy(currentStep = next) }
    }

    fun navigateToStep(step: OnboardingStep) {
        _uiState.update { it.copy(currentStep = step) }
    }

    // --- Watch Pairing Simulator ---
    fun startWatchPairingScan() {
        _uiState.update { it.copy(isScanningWatch = true) }
        viewModelScope.launch {
            delay(1800) // Simulate BLE discovery
            _uiState.update {
                it.copy(
                    isScanningWatch = false,
                    watchState = WatchState(
                        isConnected = true,
                        deviceName = "OmniBand Ultra v2",
                        macAddress = "7C:9E:BD:44:A2:18",
                        batteryPercent = 94,
                        rssiDb = -52,
                        isHapticActive = false
                    )
                )
            }
        }
    }

    fun testWatchHapticPulse() {
        viewModelScope.launch {
            _uiState.update { it.copy(watchState = it.watchState.copy(isHapticActive = true)) }
            delay(800)
            _uiState.update { it.copy(watchState = it.watchState.copy(isHapticActive = false)) }
        }
    }

    // --- Permission Management ---
    fun togglePermission(permissionId: String, granted: Boolean) {
        _uiState.update { state ->
            val updatedList = state.permissions.map {
                if (it.id == permissionId) it.copy(isGranted = granted) else it
            }
            state.copy(permissions = updatedList)
        }
    }

    fun grantAllPermissions() {
        _uiState.update { state ->
            val updated = state.permissions.map { it.copy(isGranted = true) }
            state.copy(permissions = updated)
        }
    }

    // --- Emergency Contacts Setup ---
    fun addContact(name: String, phone: String, relationship: ContactRelationship) {
        if (_uiState.value.contacts.size >= 5) return
        val newContact = EmergencyContact(
            id = UUID.randomUUID().toString(),
            name = name,
            phoneNumber = phone,
            relationship = relationship,
            notifyOnGeofence = true,
            notifyOnFall = true,
            priority = _uiState.value.contacts.size + 1
        )
        _uiState.update { it.copy(contacts = it.contacts + newContact) }
    }

    fun removeContact(contactId: String) {
        _uiState.update { it.copy(contacts = it.contacts.filterNot { c -> c.id == contactId }) }
    }

    fun updateContactGeofenceAlert(contactId: String, notify: Boolean) {
        _uiState.update { state ->
            val updated = state.contacts.map {
                if (it.id == contactId) it.copy(notifyOnGeofence = notify) else it
            }
            state.copy(contacts = updated)
        }
    }

    // --- Interactive Hardware Dry-Run Test ---
    fun startFallSimulation() {
        countdownJob?.cancel()
        _uiState.update {
            it.copy(
                dryRunStage = DryRunStage.SIMULATING_FALL,
                simulatedImpactForceG = 3.8f,
                countdownSecondsRemaining = 15,
                dryRunResultMessage = null
            )
        }

        viewModelScope.launch {
            delay(1200) // Simulated sudden drop & impact spike
            _uiState.update { it.copy(dryRunStage = DryRunStage.ALERT_COUNTDOWN) }

            countdownJob = launch {
                for (i in 15 downTo 1) {
                    _uiState.update { it.copy(countdownSecondsRemaining = i) }
                    delay(1000)
                }
                // If countdown expires without button press or cancel -> Trigger emergency test broadcast
                _uiState.update {
                    it.copy(
                        dryRunStage = DryRunStage.VERIFIED_SUCCESS,
                        dryRunResultMessage = "Hardware Dry-Run Complete: Fall detected (3.8G), automated countdown verified, silent watch ping active."
                    )
                }
            }
        }
    }

    fun triggerHardwareSideButtonPress() {
        countdownJob?.cancel()
        _uiState.update {
            it.copy(
                dryRunStage = DryRunStage.VERIFIED_SUCCESS,
                dryRunResultMessage = "Hardware Side Button Press Confirmed! Emergency cancelled immediately by user. All sensors operational."
            )
        }
    }

    fun cancelDryRun() {
        countdownJob?.cancel()
        _uiState.update {
            it.copy(
                dryRunStage = DryRunStage.CANCELLED_BY_USER,
                dryRunResultMessage = "Test cancelled. You can retry the dry-run at any time."
            )
        }
    }

    fun resetDryRun() {
        countdownJob?.cancel()
        _uiState.update {
            it.copy(
                dryRunStage = DryRunStage.READY_TO_TEST,
                countdownSecondsRemaining = 15,
                dryRunResultMessage = null
            )
        }
    }
}
