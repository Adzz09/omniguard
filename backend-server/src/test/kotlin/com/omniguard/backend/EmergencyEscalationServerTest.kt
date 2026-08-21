package com.omniguard.backend

import com.omniguard.backend.model.CancelEmergencyRequest
import com.omniguard.backend.model.CancelEmergencyResponse
import com.omniguard.backend.model.GeofencePingRequest
import com.omniguard.backend.model.GeofencePingResponse
import com.omniguard.backend.model.LocationPingRequest
import com.omniguard.backend.model.SOSRequestPayload
import com.omniguard.backend.model.SOSResponsePayload
import com.omniguard.backend.model.TrackingSessionState
import com.omniguard.backend.service.EmergencySessionManager
import com.omniguard.backend.service.NotificationDispatchService
import com.omniguard.core.model.SOSTriggerSource
import com.omniguard.core.model.TransitEventType
import com.omniguard.core.model.UserRole
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EmergencyEscalationServerTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun `full emergency escalation lifecycle - SOS trigger, live polling, pinging, and cancellation`() = testApplication {
        val sessionManager = EmergencySessionManager()
        val dispatchService = NotificationDispatchService()

        application {
            module(sessionManager = sessionManager, dispatchService = dispatchService)
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(this@EmergencyEscalationServerTest.json)
            }
        }

        // 1. Trigger Emergency SOS: POST /api/v1/emergency/sos
        val sosRequest = SOSRequestPayload(
            userId = "user_biker_99",
            userRole = UserRole.BIKER,
            triggerSource = SOSTriggerSource.MANUAL_APP,
            isDuress = false,
            latitude = 37.7749,
            longitude = -122.4194,
            accuracyMeters = 4.5f,
            peakGForce = 4.8,
            batteryPercent = 92
        )

        val sosResponse = client.post("/api/v1/emergency/sos") {
            contentType(ContentType.Application.Json)
            setBody(sosRequest)
        }

        assertEquals(HttpStatusCode.Created, sosResponse.status)
        val sosBody = sosResponse.body<SOSResponsePayload>()
        assertNotNull(sosBody.sessionId)
        assertTrue(sosBody.sessionId.startsWith("EMG-"))
        assertNotNull(sosBody.trackingToken)
        assertTrue(sosBody.trackingUrl.contains(sosBody.sessionId))

        val sessionId = sosBody.sessionId

        // 2. Fetch Live Tracking Snapshot: GET /api/v1/tracking/{sessionId}
        val getTrackingResponse = client.get("/api/v1/tracking/$sessionId")
        assertEquals(HttpStatusCode.OK, getTrackingResponse.status)
        val trackingState = getTrackingResponse.body<TrackingSessionState>()
        assertEquals(sessionId, trackingState.sessionId)
        assertEquals("user_biker_99", trackingState.userId)
        assertEquals(UserRole.BIKER, trackingState.userRole)
        assertEquals(37.7749, trackingState.currentLatitude, 0.0001)
        assertEquals(1, trackingState.breadcrumbs.size)
        assertFalse(trackingState.isCancelled)

        // 3. Send Location Ping: POST /api/v1/tracking/{sessionId}/ping
        val ping = LocationPingRequest(
            latitude = 37.7780,
            longitude = -122.4150,
            accuracyMeters = 3.0f,
            speedKmh = 28.5,
            altitudeMeters = 15.0,
            batteryPercent = 90
        )
        val pingResponse = client.post("/api/v1/tracking/$sessionId/ping") {
            contentType(ContentType.Application.Json)
            setBody(ping)
        }
        assertEquals(HttpStatusCode.OK, pingResponse.status)
        val updatedTracking = pingResponse.body<TrackingSessionState>()
        assertEquals(37.7780, updatedTracking.currentLatitude, 0.0001)
        assertEquals(28.5, updatedTracking.speedKmh, 0.1)
        assertEquals(2, updatedTracking.breadcrumbs.size)

        // 4. Test Web Viewer: GET /live/{sessionId}
        val viewerResponse = client.get("/live/$sessionId")
        assertEquals(HttpStatusCode.OK, viewerResponse.status)
        val htmlContent = viewerResponse.bodyAsText()
        assertTrue(htmlContent.contains("OmniGuard Live Escort"))
        assertTrue(htmlContent.contains(sessionId))
        assertTrue(htmlContent.contains("leaflet.js"))

        // 5. Cancel Emergency: POST /api/v1/emergency/cancel
        val cancelRequest = CancelEmergencyRequest(
            sessionId = sessionId,
            reason = "User verified safe via PIN"
        )
        val cancelResponse = client.post("/api/v1/emergency/cancel") {
            contentType(ContentType.Application.Json)
            setBody(cancelRequest)
        }
        assertEquals(HttpStatusCode.OK, cancelResponse.status)
        val cancelBody = cancelResponse.body<CancelEmergencyResponse>()
        assertEquals("CANCELLED", cancelBody.status)

        // Verify state is now cancelled
        val finalState = client.get("/api/v1/tracking/$sessionId").body<TrackingSessionState>()
        assertTrue(finalState.isCancelled)
    }

    @Test
    fun `geofence ping endpoint dispatches simulated SMS and FCM alerts`() = testApplication {
        val sessionManager = EmergencySessionManager()
        val dispatchService = NotificationDispatchService()

        application {
            module(sessionManager = sessionManager, dispatchService = dispatchService)
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(this@EmergencyEscalationServerTest.json)
            }
        }

        val geofencePing = GeofencePingRequest(
            zoneId = "zone-campus-1",
            zoneName = "University Campus Library Safe Zone",
            userId = "student_jane_doe",
            userRole = UserRole.STUDENT,
            eventType = TransitEventType.SAFE_ZONE_EXIT,
            latitude = 37.7750,
            longitude = -122.4190,
            notifyContactIds = listOf("Mom (+15551234)", "Roommate (+15555678)")
        )

        val response = client.post("/api/v1/geofence/ping") {
            contentType(ContentType.Application.Json)
            setBody(geofencePing)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = response.body<GeofencePingResponse>()
        assertEquals("DISPATCHED", responseBody.status)
        assertEquals(TransitEventType.SAFE_ZONE_EXIT, responseBody.eventType)
        assertEquals(2, responseBody.dispatchedSmsCount)
        assertEquals(2, responseBody.dispatchedPushCount)
        assertEquals(4, dispatchService.getHistory().size)
    }
}
