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
 * Class to manage the web socket connection
 *
 * @param webSocketURL
 * @param sslConfiguration
 */
open class WebSocket(private val webSocketURL: String, private val sslConfiguration: SSLConfiguration?, private val listener: Listener) : WebSocketListener() {

    companion object {
        const val MAX_PAYLOAD_SIZE = 4096
        const val PING_INTERVAL = 5L
    }

    enum class State {
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED
    }

    private var webSocket: okhttp3.WebSocket? = null

    var state = State.DISCONNECTED
        private set

    /**
     * Connects the socket to the remote host.
     */
    fun connect(): Boolean {
        return if (state == State.DISCONNECTED || state == State.DISCONNECTING) {
            OCastLog.debug { "Socket: Connecting..." }
            state = State.CONNECTING
            webSocket = null
            try {
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
                webSocket = client.newWebSocket(request, this)
                true
            } catch (e: Exception) {
                OCastLog.error(e) { "Socket: Error create socket" }
                state = State.DISCONNECTED
                listener.onDisconnected(this, e)
                false
            }
        } else {
            state == State.CONNECTING
        }
    }

    /**
     * Disconnects the socket from the remote host.
     * @return `true` if the disconnection is performed, `false` if the the socket is not connected.
     */
    fun disconnect(): Boolean {
        return if (state == State.CONNECTED || state == State.CONNECTING) {
            OCastLog.debug { "Socket: Disconnecting..." }
            state = State.DISCONNECTING
            if (webSocket?.close(1000, "normal closure") == true) {
                true
            } else {
                state = State.DISCONNECTED
                listener.onDisconnected(this, null)
                false
            }
        } else {
            state == State.DISCONNECTING
        }
    }

    /**
     * Sends a message on the socket.
     * @param message The message to send.
     * @return `true` if the send is performed, `false` if the the socket is not connected or the payload is too long.
     */
    fun send(message: String): Boolean {
        return if (state == State.CONNECTED) {
            OCastLog.debug { "Socket: send $message" }
            if (message.length <= MAX_PAYLOAD_SIZE) {
                webSocket?.send(message).orFalse()
            } else {
                false
            }
        } else {
            false
        }
    }

    /**
     * Invoked when a web socket has been accepted by the remote peer and may begin transmitting messages.
     *
     * @param webSocket
     * @param response
     */
    override fun onOpen(webSocket: okhttp3.WebSocket, response: Response) {
        if (this.webSocket == webSocket) {
            OCastLog.debug { "Socket: Connected !" }
            state = State.CONNECTED
            listener.onConnected(this)
        }
    }

    /**
     * Invoked when a text message has been received.
     *
     * @param webSocket
     * @param text
     */
    override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
        if (this.webSocket == webSocket) {
            listener.onDataReceived(this, text)
        }
    }

    /**
     * Invoked when both peers have indicated that no more messages will be transmitted and the
     * connection has been successfully released. No further calls to this listener will be made.
     *
     * @param webSocket
     * @param code
     * @param reason
     */
    override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
        if (this.webSocket == webSocket) {
            OCastLog.debug { "Socket: Closed !" }
            state = State.DISCONNECTED
            listener.onDisconnected(this, null)
        }
    }

    /**
     * Invoked when a web socket has been closed due to an error reading from or writing to the network.
     * Both outgoing and incoming messages may have been lost. No further calls to this listener will be made.
     *
     * @param webSocket
     * @param throwable
     * @param response
     */
    override fun onFailure(webSocket: okhttp3.WebSocket, throwable: Throwable, response: Response?) {
        if (this.webSocket == webSocket) {
            OCastLog.debug { "Socket: Failure !" }
            state = State.DISCONNECTED
            listener.onDisconnected(this, throwable)
        }
    }

    interface Listener {

        /**
         * Tells the listener that the socket has received a message.
         *
         * @param webSocket The connected socket.
         * @param data: The data received.
         */
        fun onDataReceived(webSocket: WebSocket, data: String)

        /**
         * Tells the listener that the socket has been disconnected from the device.
         *
         * @param webSocket The connected socket.
         * @param error The error.
         */
        fun onDisconnected(webSocket: WebSocket, error: Throwable?)

        /**
         * Tells the listener that the socket is connected to the device.
         *
         * @param webSocket
         */
        fun onConnected(webSocket: WebSocket)
    }
}
