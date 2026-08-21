package com.omniguard.backend.routing

import com.omniguard.backend.model.LocationPingRequest
import com.omniguard.backend.service.EmergencySessionManager
import com.omniguard.backend.view.LiveTrackingWebViewer
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import org.slf4j.LoggerFactory

fun Route.trackingRouting(sessionManager: EmergencySessionManager) {
    val logger = LoggerFactory.getLogger("TrackingRouting")

    // HTML Web Viewers (FR-03: Zero-install live viewer for contacts)
    get("/live/{sessionId}") {
        val sessionId = call.parameters["sessionId"] ?: return@get call.respond(
            HttpStatusCode.BadRequest,
            "Missing sessionId"
        )
        val session = sessionManager.getSession(sessionId)
        if (session != null) {
            val html = LiveTrackingWebViewer.renderHtml(session)
            call.respondText(html, ContentType.Text.Html)
        } else {
            call.respondText(
                "<h3>Session $sessionId not found or expired.</h3>",
                ContentType.Text.Html,
                HttpStatusCode.NotFound
            )
        }
    }

    route("/api/v1/tracking") {
        /**
         * GET /api/v1/tracking/{sessionId}/viewer
         */
        get("/{sessionId}/viewer") {
            val sessionId = call.parameters["sessionId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                "Missing sessionId"
            )
            val session = sessionManager.getSession(sessionId)
            if (session != null) {
                call.respondText(LiveTrackingWebViewer.renderHtml(session), ContentType.Text.Html)
            } else {
                call.respondText(
                    "<h3>Session $sessionId not found.</h3>",
                    ContentType.Text.Html,
                    HttpStatusCode.NotFound
                )
            }
        }

        /**
         * GET /api/v1/tracking/{sessionId}
         * Returns current session metadata and coordinates JSON snapshot.
         */
        get("/{sessionId}") {
            val sessionId = call.parameters["sessionId"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Missing sessionId parameter")
            )
            val session = sessionManager.getSession(sessionId)
            if (session != null) {
                call.respond(HttpStatusCode.OK, session)
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Tracking session $sessionId not found")
                )
            }
        }

        /**
         * POST /api/v1/tracking/{sessionId}/ping
         * Ingests high-frequency GPS telemetry and broadcasts to WebSockets.
         */
        post("/{sessionId}/ping") {
            val sessionId = call.parameters["sessionId"] ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Missing sessionId parameter")
            )
            try {
                val ping = call.receive<LocationPingRequest>()
                val updatedSession = sessionManager.updateLocation(sessionId, ping)
                if (updatedSession != null) {
                    call.respond(HttpStatusCode.OK, updatedSession)
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Tracking session $sessionId not found")
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Invalid location ping payload"))
                )
            }
        }

        /**
         * WebSocket /api/v1/tracking/{sessionId}
         * Continuous bi-directional / reactive push channel for browser and contact dashboards.
         */
        webSocket("/{sessionId}") {
            val sessionId = call.parameters["sessionId"]
            if (sessionId == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "SessionId required"))
                return@webSocket
            }

            val session = sessionManager.getSession(sessionId)
            if (session == null) {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Session not found"))
                return@webSocket
            }

            logger.info("WebSocket client connected to live tracking session: {}", sessionId)

            try {
                val flow = sessionManager.getSessionFlow(sessionId)
                flow.collectLatest { message ->
                    if (isActive) {
                        sendSerialized(message)
                    }
                }
            } catch (e: Exception) {
                logger.warn("WebSocket session {} terminated: {}", sessionId, e.message)
            }
        }
    }
}
