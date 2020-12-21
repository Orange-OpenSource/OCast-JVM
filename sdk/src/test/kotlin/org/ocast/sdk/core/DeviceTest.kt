/*
 * Copyright 2020 Orange
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
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import junit.framework.TestCase.assertEquals
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.ws.RealWebSocket
import okhttp3.mockwebserver.MockResponse
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Stubber
import org.ocast.sdk.common.HttpClientTest
import org.ocast.sdk.common.SynchronizedRunnable
import org.ocast.sdk.dial.DialClient
import org.ocast.sdk.discovery.models.UpnpDevice
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PowerMockIgnore
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.util.Timer
import java.util.concurrent.TimeUnit
import kotlin.concurrent.schedule

/**
 * An abstract class for unit tests of devices.
 */
@RunWith(PowerMockRunner::class)
@PowerMockIgnore("javax.net.ssl.*") // This fixes a java.lang.AssertionError with OkHttp and PowerMock
@PrepareForTest(fullyQualifiedNames = ["okhttp3.internal.ws.RealWebSocket", "org.ocast.sdk.core.*"])
abstract class DeviceTest<T>(deviceClass: Class<T>) : HttpClientTest() where T : Device, T : WebSocket.Listener {

    /** The device. */
    protected val device: T = deviceClass
        .getConstructor(UpnpDevice::class.java, DialClient::class.java, Long::class.java)
        .newInstance(UpnpDevice(), DialClient(baseURL), 5)

    /** The OCast web socket. */
    protected var webSocket: WebSocket? = null

    /** The underlying OkHttp web socket. */
    protected val realWebSocket = mock<RealWebSocket>()

    /** The device listener. */
    protected val deviceListener = mock<DeviceListener>()

    /** The event listener. */
    protected val eventListener = mock<EventListener>()

    @Before
    override fun setUp() {
        super.setUp()

        device.deviceListener = deviceListener
        device.eventListener = eventListener
        device.applicationName = "OrangeTVReceiverProd"
        stubWebSocket()
    }

    //region Protected methods

    /**
     * Schedules a list of messages received on the web socket.
     *
     * @param messages The list of messages with their associated delay.
     */
    protected fun scheduleReceivedMessages(vararg messages: Pair<String, Long>) {
        messages.forEach { message ->
            Timer().schedule(message.second) {
                webSocket?.onMessage(realWebSocket, message.first)
            }
        }
    }

    /**
     * Stubs a list of messages to be received on the web socket each time the send method is called.
     *
     * @param messages The list of messages with their associated delay.
     */
    protected fun stubReceivedMessages(vararg messages: Pair<String, Long>) {
        var stubbing: Stubber? = null
        messages.forEach { message ->
            val answer: (InvocationOnMock) -> Boolean = {
                scheduleReceivedMessages(message)
                true
            }
            stubbing = if (stubbing == null) Mockito.doAnswer(answer) else stubbing?.doAnswer(answer)
        }
        stubbing?.whenever(realWebSocket)?.send(any<String>())
    }

    /**
     * Waits for the device to connect.
     */
    protected fun awaitDeviceConnected() {
        if (device.state != Device.State.CONNECTED) {
            server.enqueue(MockResponse().setBody(createGetApplicationResponseBody()))
            val synchronizedOnSuccess = SynchronizedRunnable(Runnable {})
            device.connect(null, synchronizedOnSuccess, {})
            synchronizedOnSuccess.await(5, TimeUnit.SECONDS)
        }
        assertEquals(Device.State.CONNECTED, device.state)
    }

    /**
     * Waits for the application to start.
     */
    protected fun awaitApplicationStarted() {
        awaitDeviceConnected()
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody("running")))
        val synchronizedOnSuccess = SynchronizedRunnable(Runnable {})
        device.startApplication(synchronizedOnSuccess, {})
        synchronizedOnSuccess.await(5, TimeUnit.SECONDS)
    }

    //endregion

    //region Private methods

    /**
     * Stubs the underlying OkHttp web socket.
     */
    private fun stubWebSocket() {
        val webSocket = object : WebSocket("wss://192.168.1.65:4433/ocast", null, device) {

            override fun createWebSocket(): okhttp3.WebSocket {
                // The connect method is called in the RealWebSocket constructor
                return realWebSocket.apply { connect(mock()) }
            }
        }
        this.webSocket = webSocket
        PowerMockito.whenNew(WebSocket::class.java).withAnyArguments().thenReturn(webSocket)

        doAnswer {
            val response = Response.Builder()
                .request(Request.Builder().url("http://locahost").build())
                .protocol(Protocol.HTTP_1_1)
                .code(101)
                .message("")
                .build()
            Timer().schedule(100L) {
                webSocket.onOpen(realWebSocket, response)
            }
        }.whenever(realWebSocket).connect(any())

        doAnswer {
            Timer().schedule(100L) {
                webSocket.onClosed(realWebSocket, 1000, "Normal closure")
            }
            true
        }.whenever(realWebSocket).close(any(), any())

        doReturn(true).whenever(realWebSocket).send(any<String>())
    }

    /**
     * Creates a response body for the DIAL get application request.
     *
     * @param state The state of the DIAL application.
     * @return The response body.
     */
    protected fun createGetApplicationResponseBody(state: String = "stopped"): String {
        return """
            <service xmlns="urn:dial-multiscreen-org:schemas:dial" xmlns:ocast="urn:cast-ocast-org:service:cast:1" dialVer="2.1">
              <name>OrangeTVReceiverProd</name>
              <options allowStop="true"/>
              <state>$state</state>
              <additionalData>
                <ocast:X_OCAST_App2AppURL>wss://192.168.1.65:4433/ocast</ocast:X_OCAST_App2AppURL>
                <ocast:X_OCAST_Version>1.0</ocast:X_OCAST_Version>
              </additionalData>
              <link rel="run" href="run"/>
            </service>
        """.trimIndent()
    }

    //endregion
}
