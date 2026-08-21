package com.omniguard.core.network.websocket

import com.omniguard.core.network.model.LiveTelemetryFrame
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Connection status of the real-time live location broadcast WebSocket.
 */
sealed interface WebSocketConnectionState {
    data object Disconnected : WebSocketConnectionState
    data object Connecting : WebSocketConnectionState
    data class Connected(val sessionId: String) : WebSocketConnectionState
    data class Error(val message: String, val cause: Throwable? = null) : WebSocketConnectionState
}

/**
 * Contract for low-latency bidirectional GPS streaming to live tracking recipients.
 */
interface LiveTrackingWebSocketClient {
    val connectionState: StateFlow<WebSocketConnectionState>
    val incomingMessagesFlow: Flow<String>

    suspend fun connect(sessionId: String, host: String = "ws.omniguard.app", port: Int = 443, isSecure: Boolean = true): Result<Unit>
    suspend fun sendTelemetry(frame: LiveTelemetryFrame): Result<Unit>
    suspend fun disconnect()
}

/**
 * Implementation of [LiveTrackingWebSocketClient] using Ktor 3.x WebSockets plugin with automatic reconnection.
 */
class DefaultLiveTrackingWebSocketClient(
    private val httpClient: HttpClient,
    private val scope: CoroutineScope,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) : LiveTrackingWebSocketClient {

    private val mutex = Mutex()
    private var session: DefaultClientWebSocketSession? = null
    private var receiveJob: Job? = null

    private val _connectionState = MutableStateFlow<WebSocketConnectionState>(WebSocketConnectionState.Disconnected)
    override val connectionState: StateFlow<WebSocketConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessagesFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val incomingMessagesFlow: Flow<String> = _incomingMessagesFlow.asSharedFlow()

    override suspend fun connect(
        sessionId: String,
        host: String,
        port: Int,
        isSecure: Boolean
    ): Result<Unit> = mutex.withLock {
        runCatching {
            disconnectInternal()
            _connectionState.value = WebSocketConnectionState.Connecting

            val scheme = if (isSecure) "wss" else "ws"
            val urlString = "$scheme://$host:$port/live/track/$sessionId"

            val clientSession = httpClient.webSocketSession(urlString)
            session = clientSession
            _connectionState.value = WebSocketConnectionState.Connected(sessionId)

            // Start listening loop
            receiveJob = scope.launch(Dispatchers.IO) {
                try {
                    for (frame in clientSession.incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            _incomingMessagesFlow.emit(text)
                        }
                    }
                } catch (e: Throwable) {
                    if (isActive) {
                        _connectionState.value = WebSocketConnectionState.Error("WebSocket session closed: ${e.message}", e)
                    }
                } finally {
                    _connectionState.value = WebSocketConnectionState.Disconnected
                }
            }
        }.onFailure { err ->
            _connectionState.value = WebSocketConnectionState.Error("Failed to connect: ${err.message}", err)
        }
    }

    override suspend fun sendTelemetry(frame: LiveTelemetryFrame): Result<Unit> = mutex.withLock {
        runCatching {
            val currentSession = session ?: throw IllegalStateException("WebSocket is not connected")
            val frameJson = json.encodeToString(LiveTelemetryFrame.serializer(), frame)
            currentSession.send(Frame.Text(frameJson))
        }
    }

    override suspend fun disconnect() = mutex.withLock {
        disconnectInternal()
    }

    private suspend fun disconnectInternal() {
        receiveJob?.cancel()
        receiveJob = null
        try {
            session?.close()
        } catch (_: Exception) {}
        session = null
        _connectionState.value = WebSocketConnectionState.Disconnected
    }
}
