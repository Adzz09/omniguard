package com.omniguard.backend.routing

import com.omniguard.backend.model.CancelEmergencyRequest
import com.omniguard.backend.model.CancelEmergencyResponse
import com.omniguard.backend.model.SOSRequestPayload
import com.omniguard.backend.service.EmergencySessionManager
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.emergencyRouting(sessionManager: EmergencySessionManager) {
    route("/api/v1/emergency") {
        /**
         * POST /api/v1/emergency/sos
         * Receives silent SOS / fall escalation payload, generates tracking token, and starts session.
         */
        post("/sos") {
            try {
                val payload = call.receive<SOSRequestPayload>()
                val (response, _) = sessionManager.createSOSSession(payload)
                call.respond(HttpStatusCode.Created, response)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Invalid SOS request payload"))
                )
            }
        }

        /**
         * POST /api/v1/emergency/cancel
         * Processes cancellation if within grace period / verified with PIN.
         */
        post("/cancel") {
            try {
                val request = call.receive<CancelEmergencyRequest>()
                val cancelledSession = sessionManager.cancelSession(request.sessionId, request)
                if (cancelledSession != null) {
                    call.respond(
                        HttpStatusCode.OK,
                        CancelEmergencyResponse(
                            sessionId = request.sessionId,
                            status = "CANCELLED",
                            cancelledAt = System.currentTimeMillis(),
                            message = "Emergency incident ${request.sessionId} successfully cancelled."
                        )
                    )
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Emergency session ${request.sessionId} not found")
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Invalid cancellation request"))
                )
            }
        }
    }
}
