package com.omniguard.core.network.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingPeriod
import io.ktor.client.plugins.websocket.timeout
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

/**
 * Factory providing configured Ktor 3.x HttpClient instances for HTTP and WebSocket communication.
 */
object OmniGuardHttpClientFactory {

    fun create(
        enableLogging: Boolean = true,
        connectTimeoutSeconds: Long = 10,
        requestTimeoutSeconds: Long = 15,
        socketTimeoutSeconds: Long = 15,
        pingIntervalSeconds: Long = 15
    ): HttpClient {
        return HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        prettyPrint = false
                        isLenient = true
                        ignoreUnknownKeys = true
                        encodeDefaults = true
                    }
                )
            }

            install(WebSockets) {
                pingPeriod = pingIntervalSeconds.seconds
                timeout = 30.seconds
                maxFrameSize = Long.MAX_VALUE
            }

            install(HttpTimeout) {
                connectTimeoutMillis = connectTimeoutSeconds * 1000L
                requestTimeoutMillis = requestTimeoutSeconds * 1000L
                socketTimeoutMillis = socketTimeoutSeconds * 1000L
            }

            if (enableLogging) {
                install(Logging) {
                    logger = Logger.DEFAULT
                    level = LogLevel.INFO
                }
            }
        }
    }
}
