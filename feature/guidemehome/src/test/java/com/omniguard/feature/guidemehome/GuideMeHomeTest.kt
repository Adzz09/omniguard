package com.omniguard.feature.guidemehome

import com.omniguard.feature.guidemehome.consent.EmergencyConsentManager
import com.omniguard.feature.guidemehome.model.GeoPoint
import com.omniguard.feature.guidemehome.model.LiveStreamingConsentState
import com.omniguard.feature.guidemehome.router.SafeRouteResolver
import com.omniguard.feature.guidemehome.sync.WearableDataSyncChannel
import com.omniguard.feature.guidemehome.sync.WristManeuverPayload
import com.omniguard.feature.guidemehome.sync.WristNavigationSyncEngine
import com.omniguard.feature.guidemehome.tracking.ContactBroadcastService
import com.omniguard.feature.guidemehome.tracking.LiveRouteStreamingCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GuideMeHomeTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun testSafeRouteResolverPrefersWellLitThoroughfares() {
        val resolver = SafeRouteResolver()
        val origin = GeoPoint(37.7749, -122.4194)
        val destination = GeoPoint(37.7849, -122.4094)

        val safeRoute = resolver.resolveSafestRoute(origin, destination)
        assertNotNull(safeRoute)
        assertTrue(safeRoute.compositeSafetyScore > 80.0f)
        assertTrue(safeRoute.maneuvers.isNotEmpty())
    }

    @Test
    fun testConsentManagerFlow() {
        val consentManager = EmergencyConsentManager()
        consentManager.requestConsent()

        val state = consentManager.consentState.value
        assertTrue(state is LiveStreamingConsentState.PromptingConsent)

        consentManager.grantConsent()
        assertTrue(consentManager.consentState.value is LiveStreamingConsentState.Granted)
    }

    @Test
    fun testGeofenceArrivalAutoTermination() = testScope.runTest {
        var arrivalBroadcasted = false
        val mockBroadcastService = object : ContactBroadcastService {
            override suspend fun streamLocationUpdate(currentLocation: GeoPoint, routeId: String) {}
            override suspend fun broadcastSafeArrivalNotification(message: String) {
                arrivalBroadcasted = true
            }
        }

        val coordinator = LiveRouteStreamingCoordinator(
            broadcastService = mockBroadcastService,
            arrivalGeofenceRadiusMeters = 25.0,
            scope = testScope
        )

        val origin = GeoPoint(37.7749, -122.4194)
        val destination = GeoPoint(37.7849, -122.4094)
        val route = SafeRouteResolver().resolveSafestRoute(origin, destination)

        coordinator.startJourney(route)
        assertTrue(coordinator.trackingState.value.isNavigating)

        // Simulate reaching within 10 meters of destination
        coordinator.onLocationUpdate(destination)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(arrivalBroadcasted)
        assertTrue(coordinator.trackingState.value.hasArrivedHome)
    }

    @Test
    fun testWristNavigationSyncEngine() = testScope.runTest {
        var receivedPayload: WristManeuverPayload? = null
        val mockChannel = object : WearableDataSyncChannel {
            override suspend fun sendManeuverToWearable(payload: WristManeuverPayload): Boolean {
                receivedPayload = payload
                return true
            }
        }

        val syncEngine = WristNavigationSyncEngine(syncChannel = mockChannel)
        val route = SafeRouteResolver().resolveSafestRoute(GeoPoint(0.0, 0.0), GeoPoint(0.01, 0.01))
        val firstManeuver = route.maneuvers.first()

        syncEngine.syncManeuverToWrist(firstManeuver, 20.0)

        assertNotNull(receivedPayload)
        assertEquals(firstManeuver.streetName, receivedPayload?.streetName)
    }
}
