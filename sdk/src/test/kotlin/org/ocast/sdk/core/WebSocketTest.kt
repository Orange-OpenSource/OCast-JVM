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

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.util.Timer
import kotlin.concurrent.schedule
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.ws.RealWebSocket
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

/**
 * Unit tests for the [WebSocket] class.
 */
@RunWith(PowerMockRunner::class)
@PrepareForTest(RealWebSocket::class)
class WebSocketTest {

    /** The socket listener. */
    private val listener = mock<WebSocket.Listener>()

    /**
     * The underlying web socket used by an instance of [WebSocket].
     * Beware, this variable should be recreated if the connect() method of WebSocket is called more than once, which is not currently the case.
     */
    private val realWebSocket = mock<RealWebSocket>()

    /** The web socket. */
    private val webSocket = object : WebSocket("wss://localhost", null, listener) {

        override fun createWebSocket(): okhttp3.WebSocket {
            // The connect method is called in the RealWebSocket constructor
            return realWebSocket.apply { connect(mock()) }
        }
    }

    @Before
    fun setUp() {
        doAnswer {
            Timer().schedule(100L) {
                val response = Response.Builder()
                    .request(Request.Builder().url("http://locahost").build())
                    .protocol(Protocol.HTTP_1_1)
                    .code(101)
                    .message("")
                    .build()
                webSocket.onOpen(realWebSocket, response)
            }
        }.whenever(realWebSocket).connect(any())

        doAnswer {
            Timer().schedule(100L) {
                webSocket.onClosed(realWebSocket, 1000, "Normal closure")
            }
            true
        }.whenever(realWebSocket).close(any(), any())
    }

    @Test
    fun connectWebSocketCallsListenerOnConnected() {
        // Given

        // When
        webSocket.connect()
        webSocket.connect() // Also test that onConnected is called only once

        // Then
        Thread.sleep(200)
        verify(listener, times(1)).onConnected(eq(webSocket))
        verify(listener, never()).onDisconnected(any(), anyOrNull())
        verify(listener, never()).onDataReceived(any(), any())
    }

    @Test
    fun receiveMessageCallsListenerOnDataReceived() {
        // Given
        webSocket.connect()

        // When
        Thread.sleep(200)
        scheduleReceivedMessages("firstData" to 100L, "secondData" to 200L)

        // Then
        Thread.sleep(300)
        val dataCaptor = argumentCaptor<String>()
        verify(listener, times(1)).onConnected(eq(webSocket))
        verify(listener, times(2)).onDataReceived(eq(webSocket), dataCaptor.capture())
        assertEquals("firstData", dataCaptor.firstValue)
        assertEquals("secondData", dataCaptor.secondValue)
        verify(listener, never()).onDisconnected(any(), anyOrNull())
    }

    @Test
    fun disconnectWebSocketCallsListenerOnDisconnectedWithoutError() {
        // Given
        webSocket.connect()

        // When
        Thread.sleep(200)
        webSocket.disconnect()
        webSocket.disconnect() // Also test that onDisconnected is called only once

        // Then
        Thread.sleep(200)
        verify(listener, times(1)).onConnected(eq(webSocket))
        verify(listener, times(1)).onDisconnected(eq(webSocket), isNull())
        verify(listener, never()).onDataReceived(any(), any())
    }

    @Test
    fun webSocketFailureCallsListenerOnDisconnectedWithError() {
        // Given
        webSocket.connect()

        // When
        Thread.sleep(200)
        val exception = Exception()
        webSocket.onFailure(realWebSocket, exception, null)

        // Then
        verify(listener, times(1)).onConnected(eq(webSocket))
        verify(listener, times(1)).onDisconnected(eq(webSocket), eq(exception))
        verify(listener, never()).onDataReceived(any(), any())
    }

    /**
     * Schedules a list of messages received on the web socket.
     *
     * @param messages The list of messages with their associated delay.
     */
    private fun scheduleReceivedMessages(vararg messages: Pair<String, Long>) {
        messages.forEach { message ->
            Timer().schedule(message.second) {
                webSocket.onMessage(realWebSocket, message.first)
            }
        }
    }
}
