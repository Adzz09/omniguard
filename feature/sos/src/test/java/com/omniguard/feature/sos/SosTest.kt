package com.omniguard.feature.sos

import com.omniguard.feature.sos.detector.TriplePressDetector
import com.omniguard.feature.sos.model.DuressVerificationResult
import com.omniguard.feature.sos.model.SilentPanicPayload
import com.omniguard.feature.sos.panic.SilentEmergencyNetworkService
import com.omniguard.feature.sos.panic.SilentPanicDispatcher
import com.omniguard.feature.sos.panic.StealthHardwareController
import com.omniguard.feature.sos.pin.DuressPinVerifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SosTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun testTriplePressCadenceDetection() {
        val detector = TriplePressDetector(windowDurationMs = 1200L)
        val t0 = 1000L

        // Press 1
        assertFalse(detector.registerButtonPress(t0))
        // Press 2
        assertFalse(detector.registerButtonPress(t0 + 250))
        // Press 3 (within 1200ms)
        assertTrue(detector.registerButtonPress(t0 + 500))
    }

    @Test
    fun testDuressPinTriggerTriggersSilentPanicAndDecoy() = testScope.runTest {
        var screenSuppressed = false
        var audioMuted = false
        var payloadTransmitted: SilentPanicPayload? = null

        val mockHardware = object : StealthHardwareController {
            override fun keepScreenOff() { screenSuppressed = true }
            override fun muteAllAudio() { audioMuted = true }
            override fun suppressVibrations() {}
        }

        val mockNetwork = object : SilentEmergencyNetworkService {
            override suspend fun transmitSilentAlert(payload: SilentPanicPayload): Boolean {
                payloadTransmitted = payload
                return true
            }
        }

        val dispatcher = SilentPanicDispatcher(
            networkService = mockNetwork,
            hardwareController = mockHardware,
            scope = testScope
        )

        val verifier = DuressPinVerifier(
            silentPanicDispatcher = dispatcher,
            standardPinHashProvider = { "1234" },
            duressPinHashProvider = { "9999" }
        )

        // Test normal PIN
        val normalResult = verifier.verifyPin("1234")
        assertEquals(DuressVerificationResult.NormalUnlock, normalResult)
        assertFalse(screenSuppressed)

        // Test duress PIN (covert emergency)
        val duressResult = verifier.verifyPin("9999", currentLatitude = 37.77, currentLongitude = -122.41)
        assertTrue(duressResult is DuressVerificationResult.DuressTriggered)
        assertTrue(screenSuppressed)
        assertTrue(audioMuted)

        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(37.77, payloadTransmitted?.latitude ?: 0.0, 0.01)
    }
}
