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

package org.ocast.core

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.ocast.core.models.SSLConfiguration
import org.ocast.core.utils.OCastLog
import java.util.concurrent.TimeUnit

/**
 * Class to manage the web socket connection
 *
 * @param webSocketURL
 * @param sslConfiguration
 */
open class WebSocketProvider(private val webSocketURL: String, private val sslConfiguration: SSLConfiguration?, private val listener: Listener) : WebSocketListener() {

    companion object {
        const val MAX_PAYLOAD_SIZE = 4096
        const val PING_INTERVAL = 5L
    }

    private var webSocket: WebSocket? = null

    var isConnected = false
        private set

    /**
     * Connects the socket to the remote host.
     */
    fun connect() {
        if (!isConnected) {
            OCastLog.debug("Socket: Connecting...")
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
            } catch (e: Exception) {
                OCastLog.error("Socket: Error create socket", e)
                listener.onDisconnected(this, e)
            }
        }
    }

    /**
     * Disconnects the socket from the remote host.
     * @return `true` if the disconnection is performed, `false` if the the socket is not connected.
     */
    fun disconnect(): Boolean {
        var success = false
        if (isConnected) {
            isConnected = false
            OCastLog.debug("Socket: Disconnecting...")
            success = webSocket?.close(1000, "normal closure") ?: false
            if (success) {
                listener.onDisconnected(this, null)
            }
        }
        return success
    }

    /**
     * Sends a message on the socket.
     * @param message The message to send.
     * @return `true` if the send is performed, `false` if the the socket is not connected or the payload is too long.
     */
    fun send(message: String): Boolean {
        OCastLog.debug("Socket: send $message")
        return if (message.length <= MAX_PAYLOAD_SIZE) {
            webSocket?.send(message) ?: false
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
    override fun onOpen(webSocket: WebSocket, response: Response) {
        isConnected = true
        OCastLog.debug("Socket: Connected !")
        listener.onConnected(this, webSocketURL)
    }

    /**
     * Invoked when a text message has been received.
     *
     * @param webSocket
     * @param text
     */
    override fun onMessage(webSocket: WebSocket, text: String) {
        listener.onDataReceived(this, text)
    }

    /**
     * Invoked when a web socket has been closed due to an error reading from or writing to the network.
     * Both outgoing and incoming messages may have been lost. No further calls to this listener will be made.
     *
     * @param webSocket
     * @param t
     * @param response
     */
    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        isConnected = false
        listener.onDisconnected(this, t)
    }

    interface Listener {
        /**
         * Tells the listener that the socket provider has received a message.
         *
         * @param webSocketProvider The connected socket provider.
         * @param data: The data received.
         */
        fun onDataReceived(webSocketProvider: WebSocketProvider, data: String)

        /**
         * Tells the listener that the socket provider has been disconnected from the device.
         *
         * @param webSocketProvider The connected socket provider.
         * @param error The error.
         */
        fun onDisconnected(webSocketProvider: WebSocketProvider, error: Throwable?)

        /**
         * Tells the listener that the socket provider is connected to the device.
         *
         * @param webSocketProvider
         * @param url The connection URL.
         */
        fun onConnected(webSocketProvider: WebSocketProvider, url: String)
    }
}