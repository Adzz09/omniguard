package com.omniguard.backend

import com.omniguard.backend.routing.emergencyRouting
import com.omniguard.backend.routing.geofenceRouting
import com.omniguard.backend.routing.trackingRouting
import com.omniguard.backend.service.EmergencySessionManager
import com.omniguard.backend.service.NotificationDispatchService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(CIO, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module(
    sessionManager: EmergencySessionManager = EmergencySessionManager(),
    dispatchService: NotificationDispatchService = NotificationDispatchService()
) {
    val defaultJson = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    install(ContentNegotiation) {
        json(defaultJson)
    }

    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
        contentConverter = KotlinxWebsocketSerializationConverter(defaultJson)
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
    }

    install(CallLogging) {
        level = Level.INFO
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to (cause.message ?: "Internal server error occurred"))
            )
        }
    }

    routing {
        get("/") {
            call.respond(
                mapOf(
                    "service" to "OmniGuard Emergency Backend",
                    "status" to "HEALTHY",
                    "version" to "1.0.0",
                    "endpoints" to listOf(
                        "POST /api/v1/emergency/sos",
                        "POST /api/v1/emergency/cancel",
                        "GET  /api/v1/tracking/{sessionId}",
                        "POST /api/v1/tracking/{sessionId}/ping",
                        "WS   /api/v1/tracking/{sessionId}",
                        "POST /api/v1/geofence/ping",
                        "GET  /live/{sessionId}"
                    )
                )
            )
        }

        get("/health") {
            call.respondText("OK")
        }

        emergencyRouting(sessionManager)
        trackingRouting(sessionManager)
        geofenceRouting(dispatchService)
    }
}
