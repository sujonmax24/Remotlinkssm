package com.sujon.remotlinkssm.signaling

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

interface SignalingListener {
    fun onConnected()
    fun onMessage(message: SignalingMessage)
    fun onFailure(t: Throwable)
    fun onClosed()
}

/** WebSocket transport. It intentionally contains no pairing or authorization policy. */
class SignalingClient(
    private val endpoint: String,
    private val listener: SignalingListener,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
) {
    private var socket: WebSocket? = null

    fun connect(): Boolean {
        if (!endpoint.startsWith("wss://")) {
            listener.onFailure(IllegalArgumentException("Signaling endpoint must use wss://"))
            return false
        }
        socket?.cancel()
        val request = Request.Builder().url(endpoint).build()
        socket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = listener.onConnected()

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { SignalingMessage.fromJson(text) }
                    .onSuccess(listener::onMessage)
                    .onFailure(listener::onFailure)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onFailure(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onClosed()
            }
        })
        return true
    }

    fun send(message: SignalingMessage): Boolean = socket?.send(message.toJson()) == true

    fun close() {
        socket?.close(1000, "RemoteLink session ended")
        socket = null
    }
}
