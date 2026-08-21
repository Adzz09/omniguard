package com.omniguard.backend

import com.omniguard.backend.model.CancelEmergencyRequest
import com.omniguard.backend.model.LiveTrackingMessage
import com.omniguard.backend.model.LocationPingRequest
import com.omniguard.backend.model.SOSRequestPayload
import com.omniguard.backend.service.EmergencySessionManager
import com.omniguard.backend.service.NotificationDispatchService
import com.omniguard.core.model.SOSTriggerSource
import com.omniguard.core.model.UserRole
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebSocketLiveTrackingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun `websocket client receives live location streaming and cancellation events`() = testApplication {
        val sessionManager = EmergencySessionManager()
        val dispatchService = NotificationDispatchService()

        application {
            module(sessionManager = sessionManager, dispatchService = dispatchService)
        }

        val client = createClient {
            install(WebSockets)
        }

        // Create an active emergency tracking session
        val (sosResponse, _) = sessionManager.createSOSSession(
            payload = SOSRequestPayload(
                userId = "elderly_grandma_1",
                userRole = UserRole.ELDERLY,
                triggerSource = SOSTriggerSource.FALL_TIMEOUT,
                isDuress = false,
                latitude = 37.7749,
                longitude = -122.4194,
                batteryPercent = 78
            )
        )
        val sessionId = sosResponse.sessionId

        client.webSocket("/api/v1/tracking/$sessionId") {
            // 1. Receive Initial State Frame
            val initialFrame = incoming.receive() as Frame.Text
            val initialMsg = json.decodeFromString<LiveTrackingMessage>(initialFrame.readText())
            assertEquals("INITIAL_STATE", initialMsg.type)
            assertEquals(sessionId, initialMsg.session.sessionId)
            assertEquals(UserRole.ELDERLY, initialMsg.session.userRole)

            // 2. Trigger a live location telemetry update
            val ping = LocationPingRequest(
                latitude = 37.7760,
                longitude = -122.4180,
                accuracyMeters = 3.5f,
                speedKmh = 1.2,
                altitudeMeters = 20.0,
                batteryPercent = 77
            )
            sessionManager.updateLocation(sessionId, ping)

            val locationFrame = incoming.receive() as Frame.Text
            val locationMsg = json.decodeFromString<LiveTrackingMessage>(locationFrame.readText())
            assertEquals("LOCATION_UPDATE", locationMsg.type)
            assertNotNull(locationMsg.latestPing)
            assertEquals(37.7760, locationMsg.latestPing!!.latitude, 0.0001)
            assertEquals(2, locationMsg.session.breadcrumbs.size)

            // 3. Cancel the active emergency
            sessionManager.cancelSession(
                sessionId = sessionId,
                request = CancelEmergencyRequest(
                    sessionId = sessionId,
                    reason = "Caregiver assisted user"
                )
            )

            val cancelFrame = incoming.receive() as Frame.Text
            val cancelMsg = json.decodeFromString<LiveTrackingMessage>(cancelFrame.readText())
            assertEquals("CANCELLED", cancelMsg.type)
            assertTrue(cancelMsg.session.isCancelled)
        }
    }
}
