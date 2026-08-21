package com.omniguard.backend.routing

import com.omniguard.backend.model.GeofencePingRequest
import com.omniguard.backend.service.NotificationDispatchService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.geofenceRouting(dispatchService: NotificationDispatchService) {
    route("/api/v1/geofence") {
        /**
         * POST /api/v1/geofence/ping
         * Receives passive arrival/departure pings and dispatches simulated Twilio SMS / FCM push.
         */
        post("/ping") {
            try {
                val ping = call.receive<GeofencePingRequest>()
                val response = dispatchService.dispatchGeofenceAlert(ping)
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Invalid geofence ping payload"))
                )
            }
        }
    }
}
