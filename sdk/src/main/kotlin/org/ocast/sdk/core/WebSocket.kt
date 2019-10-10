/*
 * Copyright 2019 Orange
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ocast.sdk.core

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocketListener
import org.ocast.sdk.common.extensions.orFalse
import org.ocast.sdk.core.models.SSLConfiguration
import org.ocast.sdk.core.utils.OCastLog

/**
 * This class represents a web socket.
 *
 * @property webSocketURL The web socket URL.
 * @property sslConfiguration The SSL configuration if the web socket is secure, or `null` if is not secure.
 * @property listener The listener of the web socket events.
 * @constructor Creates an instance of [WebSocket].
 */
open class WebSocket(val webSocketURL: String, private val sslConfiguration: SSLConfiguration?, private val listener: Listener) : WebSocketListener() {

    /**
     * The companion object.
     */
    companion object {

        /** The maximum payload size in bytes. */
        const val MAX_PAYLOAD_SIZE = 4096

        /** The ping interval in seconds. */
        const val PING_INTERVAL = 5L
    }

    /** Represents the states of the web socket. */
    enum class State {

        /** The web socket is being connected. */
        CONNECTING,

        /** The web socket is connected. */
        CONNECTED,

        /** The web socket is being disconnected. */
        DISCONNECTING,

        /** The web socket is disconnected. */
        DISCONNECTED
    }

    /** The underlying OkHttp web socket. */
    private var webSocket: okhttp3.WebSocket? = null

    /** The current state of the web socket. */
    var state = State.DISCONNECTED
        private set

    /**
     * Connects the web socket to the remote host.
     *
     * @return `true` is the web socket connected successfully, otherwise `false`.
     */
    fun connect(): Boolean {
        return if (state == State.DISCONNECTED || state == State.DISCONNECTING) {
            state = State.CONNECTING
            OCastLog.debug { "Web socket with URL $webSocketURL is connecting" }
            webSocket = null
            try {
                webSocket = createWebSocket()
                true
            } catch (exception: Exception) {
                state = State.DISCONNECTED
                OCastLog.error(exception) { "Failed to create web socket with URL $webSocketURL" }
                listener.onDisconnected(this, exception)
                false
            }
        } else {
            state == State.CONNECTING
        }
    }

    /**
     * Disconnects the web socket from the remote host.
     *
     * @return `true` is the web socket disconnected successfully, otherwise `false`.
     */
    fun disconnect(): Boolean {
        return if (state == State.CONNECTED || state == State.CONNECTING) {
            state = State.DISCONNECTING
            OCastLog.debug { "Web socket with URL $webSocketURL is disconnecting" }
            if (webSocket?.close(1000, "Normal closure") == true) {
                true
            } else {
                state = State.DISCONNECTED
                OCastLog.error { "Failed to disconnect web socket with URL $webSocketURL" }
                listener.onDisconnected(this, null)
                false
            }
        } else {
            state == State.DISCONNECTING
        }
    }

    /**
     * Sends a message on the web socket.
     *
     * @param message The message to send.
     * @return `true` if the message was sent successfully, otherwise `false`.
     */
    fun send(message: String): Boolean {
        return if (state == State.CONNECTED) {
            if (message.length <= MAX_PAYLOAD_SIZE) {
                webSocket?.send(message).orFalse()
            } else {
                false
            }
        } else {
            false
        }.also { success ->
            if (success) {
                OCastLog.debug { "Sent message on web socket with URL $webSocketURL:\n${message.trim().prependIndent()}" }
            } else {
                OCastLog.error { "Failed to send message on web socket with URL $webSocketURL:\n${message.trim().prependIndent()}" }
            }
        }
    }

    /**
     * Creates and returns a web socket.
     *
     * @return The web socket.
     */
    protected open fun createWebSocket(): okhttp3.WebSocket {
        val builder = OkHttpClient.Builder().apply {
            if (sslConfiguration != null) {
                sslSocketFactory(sslConfiguration.socketFactory, sslConfiguration.trustManager)
                hostnameVerifier(sslConfiguration.hostnameVerifier)
            }
            pingInterval(PING_INTERVAL, TimeUnit.SECONDS)
            connectTimeout(5, TimeUnit.SECONDS)
        }
        val client = builder.build()
        val request = Request.Builder().url(webSocketURL).build()

        return client.newWebSocket(request, this)
    }

    override fun onOpen(webSocket: okhttp3.WebSocket, response: Response) {
        if (this.webSocket == webSocket) {
            state = State.CONNECTED
            OCastLog.debug { "Web socket with URL $webSocketURL did connect" }
            listener.onConnected(this)
        }
    }

    override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
        if (this.webSocket == webSocket) {
            OCastLog.debug { "Received message on web socket with URL $webSocketURL:\n${text.trim().prependIndent()}" }
            listener.onDataReceived(this, text)
        }
    }

    override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
        if (this.webSocket == webSocket) {
            state = State.DISCONNECTED
            OCastLog.debug { "Web socket with URL $webSocketURL did disconnect successfully with reason: $reason" }
            listener.onDisconnected(this, null)
        }
    }

    override fun onFailure(webSocket: okhttp3.WebSocket, throwable: Throwable, response: Response?) {
        if (this.webSocket == webSocket) {
            state = State.DISCONNECTED
            OCastLog.error(throwable) { "Web socket with URL $webSocketURL did disconnect" }
            listener.onDisconnected(this, throwable)
        }
    }

    /**
     * A listener of events on a [WebSocket].
     */
    interface Listener {

        /**
         * Tells the listener that the socket has received a message.
         *
         * @param webSocket The web socket which informs the listener.
         * @param data: The message received.
         */
        fun onDataReceived(webSocket: WebSocket, data: String)

        /**
         * Tells the listener that the socket has been disconnected from the device.
         *
         * @param webSocket The web socket which informs the listener.
         * @param error The disconnection error, or `null` if the disconnection was initiated by the user.
         */
        fun onDisconnected(webSocket: WebSocket, error: Throwable?)

        /**
         * Tells the listener that the socket is connected to the device.
         *
         * @param webSocket The web socket which informs the listener.
         */
        fun onConnected(webSocket: WebSocket)
    }
}
