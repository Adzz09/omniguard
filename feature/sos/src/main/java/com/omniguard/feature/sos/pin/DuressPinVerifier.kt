package com.omniguard.feature.sos.pin

import com.omniguard.feature.sos.model.DuressVerificationResult
import com.omniguard.feature.sos.model.FakeScreenType
import com.omniguard.feature.sos.model.SosTriggerType
import com.omniguard.feature.sos.panic.SilentPanicDispatcher

/**
 * Duress PIN Verification engine.
 * Protects users under duress (e.g. forced phone unlock).
 * When duress PIN is entered, silently triggers panic dispatch and presents a realistic decoy screen.
 */
class DuressPinVerifier(
    private val silentPanicDispatcher: SilentPanicDispatcher,
    private val standardPinHashProvider: () -> String = { "1234" },
    private val duressPinHashProvider: () -> String = { "9999" },
    private val defaultFakeScreen: FakeScreenType = FakeScreenType.GENERIC_CALCULATOR
) {
    /**
     * Verifies entered PIN and takes covert action if duress PIN is detected.
     */
    fun verifyPin(
        enteredPin: String,
        currentLatitude: Double = 0.0,
        currentLongitude: Double = 0.0
    ): DuressVerificationResult {
        val standardPin = standardPinHashProvider()
        val duressPin = duressPinHashProvider()

        return when (enteredPin) {
            standardPin -> {
                DuressVerificationResult.NormalUnlock
            }
            duressPin -> {
                // Covertly dispatch silent panic alert
                silentPanicDispatcher.dispatchSilentPanic(
                    trigger = SosTriggerType.DURESS_PIN_ENTERED,
                    latitude = currentLatitude,
                    longitude = currentLongitude
                )
                // Return decoy/fake screen so aggressor is not alerted
                DuressVerificationResult.DuressTriggered(defaultFakeScreen)
            }
            else -> {
                DuressVerificationResult.InvalidPin
            }
        }
    }
}
