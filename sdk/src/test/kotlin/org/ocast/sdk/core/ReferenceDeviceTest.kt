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

import com.fasterxml.jackson.core.JsonProcessingException
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.util.Timer
import java.util.concurrent.TimeUnit
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import kotlin.concurrent.schedule
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.ws.RealWebSocket
import okhttp3.mockwebserver.MockResponse
import org.hamcrest.CoreMatchers.instanceOf
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Stubber
import org.ocast.sdk.common.HttpClientTest
import org.ocast.sdk.common.SynchronizedConsumer
import org.ocast.sdk.common.SynchronizedRunnable
import org.ocast.sdk.common.models.HttpException
import org.ocast.sdk.core.models.Consumer
import org.ocast.sdk.core.models.Media
import org.ocast.sdk.core.models.MediaMetadata
import org.ocast.sdk.core.models.MediaPlaybackStatus
import org.ocast.sdk.core.models.OCastApplicationLayer
import org.ocast.sdk.core.models.OCastCommandParams
import org.ocast.sdk.core.models.OCastDataLayer
import org.ocast.sdk.core.models.OCastDomain
import org.ocast.sdk.core.models.OCastError
import org.ocast.sdk.core.models.UpdateStatus
import org.ocast.sdk.dial.DialClient
import org.ocast.sdk.discovery.models.UpnpDevice
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PowerMockIgnore
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

/**
 * Unit tests for the [ReferenceDevice] class.
 */
@RunWith(PowerMockRunner::class)
@PowerMockIgnore("javax.net.ssl.*") // This fixes a java.lang.AssertionError with OkHttp and PowerMock
@PrepareForTest(fullyQualifiedNames = ["okhttp3.internal.ws.RealWebSocket", "org.ocast.sdk.core.*"])
internal class ReferenceDeviceTest : HttpClientTest() {

    /** The reference device. */
    private val referenceDevice = ReferenceDevice(UpnpDevice(), DialClient(baseURL), 5)

    /** The OCast web socket. */
    private var webSocket: WebSocket? = null

    /** The underlying OkHttp web socket. */
    private val realWebSocket = mock<RealWebSocket>()

    /** The device listener. */
    private val deviceListener = mock<DeviceListener>()

    /** The event listener. */
    private val eventListener = mock<EventListener>()

    @Before
    override fun setUp() {
        super.setUp()

        referenceDevice.deviceListener = deviceListener
        referenceDevice.eventListener = eventListener
        referenceDevice.applicationName = "OrangeTVReceiverProd"
        stubWebSocket()
    }

    //region Connect

    @Test
    fun connectSucceeds() {
        // Given
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody()))
        val onSuccess = mock<Runnable>()
        val synchronizedOnSuccess = SynchronizedRunnable(onSuccess)
        val onError = mock<Consumer<OCastError>>()

        // When
        referenceDevice.connect(null, synchronizedOnSuccess, onError)

        // Then
        synchronizedOnSuccess.await(5, TimeUnit.SECONDS)
        verify(onSuccess, times(1)).run()
        verify(onError, never()).run(any())
        assertEquals(Device.State.CONNECTED, referenceDevice.state)
    }

    @Test
    fun connectWithoutApplicationNameSucceeds() {
        // Given
        referenceDevice.applicationName = null
        val onSuccess = mock<Runnable>()
        val synchronizedOnSuccess = SynchronizedRunnable(onSuccess)
        val onError = mock<Consumer<OCastError>>()

        // When
        referenceDevice.connect(null, synchronizedOnSuccess, onError)

        // Then
        synchronizedOnSuccess.await(5, TimeUnit.SECONDS)
        verify(onSuccess, times(1)).run()
        verify(onError, never()).run(any())
        assertEquals(Device.State.CONNECTED, referenceDevice.state)
    }

    @Test
    fun connectWhenAlreadyConnectedSucceeds() {
        // Given
        awaitDeviceConnected()
        val onSuccess = mock<Runnable>()
        val synchronizedOnSuccess = SynchronizedRunnable(onSuccess)
        val onError = mock<Consumer<OCastError>>()

        // When
        referenceDevice.connect(null, synchronizedOnSuccess, onError)

        // Then
        synchronizedOnSuccess.await(5, TimeUnit.SECONDS)
        verify(onSuccess, times(1)).run()
        verify(onError, never()).run(any())
        assertEquals(Device.State.CONNECTED, referenceDevice.state)
    }

    @Test
    fun connectWhenConnectingFails() {
        // Given
        repeat(2) {
            server.enqueue(MockResponse().setBody(createGetApplicationResponseBody()))
        }
        referenceDevice.connect(null, {}, {})
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)
        assertEquals(Device.State.CONNECTING, referenceDevice.state)

        // When
        referenceDevice.connect(null, onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run()
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
        assertEquals(Device.State.CONNECTING, referenceDevice.state)
    }

    @Test
    fun connectWhenDisconnectingFails() {
        // Given
        awaitDeviceConnected()
        referenceDevice.disconnect({}, {})
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody()))
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)
        assertEquals(Device.State.DISCONNECTING, referenceDevice.state)

        // When
        referenceDevice.connect(null, onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run()
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
        assertEquals(Device.State.DISCONNECTING, referenceDevice.state)
    }

    @Test
    fun connectWithWebSocketErrorFails() {
        // Given
        val throwable = Throwable()
        doAnswer {
            Timer().schedule(100L) {
                webSocket?.onFailure(realWebSocket, throwable, null)
            }
        }.whenever(realWebSocket).connect(any())
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody()))
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)

        // When
        referenceDevice.connect(null, onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run()
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
        assertEquals(throwable, errorCaptor.firstValue.cause)
        assertEquals(Device.State.DISCONNECTED, referenceDevice.state)
        verify(deviceListener, never()).onDeviceDisconnected(any(), anyOrNull())
    }

    @Test
    fun connectWithUnsuccessfulGetApplicationResponseSucceeds() {
        // Given
        server.enqueue(MockResponse().setResponseCode(404))
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnSuccess = SynchronizedRunnable(onSuccess)

        // When
        referenceDevice.connect(null, synchronizedOnSuccess, onError)

        // Then
        synchronizedOnSuccess.await(5, TimeUnit.SECONDS)
        verify(onSuccess, times(1)).run()
        verify(onError, never()).run(any())
        assertEquals(Device.State.CONNECTED, referenceDevice.state)
    }

    @Test
    fun connectWithMalformedGetApplicationResponseSucceeds() {
        // Given
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody("unknown state")))
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnSuccess = SynchronizedRunnable(onSuccess)

        // When
        referenceDevice.connect(null, synchronizedOnSuccess, onError)

        // Then
        synchronizedOnSuccess.await(5, TimeUnit.SECONDS)
        verify(onSuccess, times(1)).run()
        verify(onError, never()).run(any())
        assertEquals(Device.State.CONNECTED, referenceDevice.state)
    }

    //endregion

    //region Disconnect

    @Test
    fun disconnectSucceeds() {
        // Given
        awaitDeviceConnected()
        val onSuccess = mock<Runnable>()
        val synchronizedOnSuccess = SynchronizedRunnable(onSuccess)
        val onError = mock<Consumer<OCastError>>()

        // When
        referenceDevice.disconnect(synchronizedOnSuccess, onError)

        // Then
        synchronizedOnSuccess.await(5, TimeUnit.SECONDS)
        verify(onSuccess, times(1)).run()
        verify(onError, never()).run(any())
        assertEquals(Device.State.DISCONNECTED, referenceDevice.state)
        verify(deviceListener, never()).onDeviceDisconnected(any(), anyOrNull())
    }

    @Test
    fun disconnectWhenAlreadyDisconnectedSucceeds() {
        // Given
        val onSuccess = mock<Runnable>()
        val synchronizedOnSuccess = SynchronizedRunnable(onSuccess)
        val onError = mock<Consumer<OCastError>>()

        // When
        referenceDevice.disconnect(synchronizedOnSuccess, onError)

        // Then
        synchronizedOnSuccess.await(5, TimeUnit.SECONDS)
        verify(onSuccess, times(1)).run()
        verify(onError, never()).run(any())
        assertEquals(Device.State.DISCONNECTED, referenceDevice.state)
        verify(deviceListener, never()).onDeviceDisconnected(any(), anyOrNull())
    }

    @Test
    fun disconnectWhenConnectingFails() {
        // Given
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody()))
        referenceDevice.connect(null, {}, {})
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)
        assertEquals(Device.State.CONNECTING, referenceDevice.state)

        // When
        referenceDevice.disconnect(onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run()
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
        assertEquals(Device.State.CONNECTING, referenceDevice.state)
    }

    @Test
    fun disconnectWhenDisconnectingFails() {
        // Given
        awaitDeviceConnected()
        referenceDevice.disconnect({}, {})
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)
        assertEquals(Device.State.DISCONNECTING, referenceDevice.state)

        // When
        referenceDevice.disconnect(onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run()
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
        assertEquals(Device.State.DISCONNECTING, referenceDevice.state)
    }

    @Test
    fun disconnectWithWebSocketErrorFails() {
        // Given
        val throwable = Throwable()
        doAnswer {
            Timer().schedule(100L) {
                webSocket?.onFailure(realWebSocket, throwable, null)
            }
            true
        }.whenever(realWebSocket).close(any(), any())
        awaitDeviceConnected()
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)

        // When
        referenceDevice.disconnect(onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run()
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
        assertEquals(throwable, errorCaptor.firstValue.cause)
        assertEquals(Device.State.DISCONNECTED, referenceDevice.state)
        verify(deviceListener, never()).onDeviceDisconnected(any(), anyOrNull())
    }

    @Test
    fun unexpectedDisconnectCallsDeviceListenerOnDeviceDisconnected() {
        // Given
        awaitDeviceConnected()

        // When
        val throwable = Throwable()
        Timer().schedule(100L) {
            webSocket?.onFailure(realWebSocket, throwable, null)
        }

        // Then
        Thread.sleep(500)
        verify(deviceListener, times(1)).onDeviceDisconnected(eq(referenceDevice), eq(throwable))
    }

    //endregion

    //region Start application

    @Test
    fun startApplicationSucceeds() {
        // Given
        awaitDeviceConnected()
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody()))
        server.enqueue(MockResponse().setResponseCode(201))
        val message = """
            {
              "dst": "89cf41b8-ef40-48d9-99c3-2a1951abcde5",
              "src": "browser",
              "type": "event",
              "status": "ok",
              "id": 666,
              "message": {
                "service": "org.ocast.webapp",
                "data": {
                  "name": "connectedStatus",
                  "params": {
                    "status": "connected"
                  }
                }
              }
            }
        """.trimIndent()
        scheduleReceivedMessages(message to 1000)
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnSuccess = SynchronizedRunnable(onSuccess)

        // When
        referenceDevice.startApplication(synchronizedOnSuccess, onError)

        // Then
        synchronizedOnSuccess.await(5, TimeUnit.SECONDS)
        verify(onSuccess, times(1)).run()
        verify(onError, never()).run(any())
    }

    @Test
    fun startApplicationWhenAlreadyStartedSucceeds() {
        // Given
        awaitDeviceConnected()
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody("running")))
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnSuccess = SynchronizedRunnable(onSuccess)

        // When
        referenceDevice.startApplication(synchronizedOnSuccess, onError)

        // Then
        synchronizedOnSuccess.await(5, TimeUnit.SECONDS)
        verify(onSuccess, times(1)).run()
        verify(onError, never()).run(any())
    }

    @Test
    fun startApplicationWhenUpdatingApplicationNameFails() {
        // Given
        awaitDeviceConnected()
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody()))
        server.enqueue(MockResponse().setResponseCode(201))
        val message = """
            {
              "dst": "89cf41b8-ef40-48d9-99c3-2a1951abcde5",
              "src": "browser",
              "type": "event",
              "status": "ok",
              "id": 666,
              "message": {
                "service": "org.ocast.webapp",
                "data": {
                  "name": "connectedStatus",
                  "params": {
                    "status": "connected"
                  }
                }
              }
            }
        """.trimIndent()
        scheduleReceivedMessages(message to 1000)
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)

        // When
        referenceDevice.startApplication(onSuccess, synchronizedOnError)
        referenceDevice.applicationName = "OtherApp"

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run()
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
    }

    @Test
    fun startApplicationWithWebAppConnectedStatusEventTimeoutFails() {
        // Given
        awaitDeviceConnected()
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody()))
        server.enqueue(MockResponse().setResponseCode(201))
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)

        // When
        referenceDevice.startApplication(onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(10, TimeUnit.SECONDS)
        verify(onSuccess, never()).run()
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
    }

    @Test
    fun startApplicationWithUnsuccessfulGetApplicationResponseFails() {
        // Given
        awaitDeviceConnected()
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(201))
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)

        // When
        referenceDevice.startApplication(onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run()
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
        val httpException = errorCaptor.firstValue.cause as? HttpException
        assertEquals(404, httpException?.statusCode)
    }

    @Test
    fun startApplicationWithUnsuccessfulStartApplicationResponseFails() {
        // Given
        awaitDeviceConnected()
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody()))
        server.enqueue(MockResponse().setResponseCode(404))
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)

        // When
        referenceDevice.startApplication(onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run()
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
        val httpException = errorCaptor.firstValue.cause as? HttpException
        assertEquals(404, httpException?.statusCode)
    }

    @Test
    fun startApplicationWithoutApplicationNameFails() {
        // Given
        awaitDeviceConnected()
        referenceDevice.applicationName = null
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody()))
        server.enqueue(MockResponse().setResponseCode(201))
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)

        // When
        referenceDevice.startApplication(onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run()
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
    }

    @Test
    fun startApplicationWhenDisconnectedFails() {
        // Given
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody()))
        server.enqueue(MockResponse().setResponseCode(201))
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)
        assertEquals(Device.State.DISCONNECTED, referenceDevice.state)

        // When
        referenceDevice.startApplication(onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run()
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
    }

    @Test
    fun startApplicationWhenConnectingFails() {
        // Given
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody()))
        referenceDevice.connect(null, {}, {})
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody()))
        server.enqueue(MockResponse().setResponseCode(201))
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)
        assertEquals(Device.State.CONNECTING, referenceDevice.state)

        // When
        referenceDevice.startApplication(onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run()
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
    }

    @Test
    fun startApplicationWhenDisconnectingFails() {
        // Given
        awaitDeviceConnected()
        referenceDevice.disconnect({}, {})
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody()))
        server.enqueue(MockResponse().setResponseCode(201))
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)
        assertEquals(Device.State.DISCONNECTING, referenceDevice.state)

        // When
        referenceDevice.startApplication(onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run()
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
    }

    //endregion

    //region Stop application

    @Test
    fun stopApplicationSucceeds() {
        // Given
        awaitDeviceConnected()
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody("running")))
        server.enqueue(MockResponse())
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnSuccess = SynchronizedRunnable(onSuccess)

        // When
        referenceDevice.stopApplication(synchronizedOnSuccess, onError)

        // Then
        synchronizedOnSuccess.await(5, TimeUnit.SECONDS)
        verify(onSuccess, times(1)).run()
        verify(onError, never()).run(any())
    }

    @Test
    fun stopApplicationWithoutApplicationNameFails() {
        // Given
        awaitDeviceConnected()
        referenceDevice.applicationName = null
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody("running")))
        server.enqueue(MockResponse())
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)

        // When
        referenceDevice.stopApplication(onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run()
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
    }

    @Test
    fun stopApplicationWithUnsuccessfulGetApplicationResponseFails() {
        // Given
        awaitDeviceConnected()
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse())
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)

        // When
        referenceDevice.stopApplication(onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run()
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
        val httpException = errorCaptor.firstValue.cause as? HttpException
        assertEquals(404, httpException?.statusCode)
    }

    @Test
    fun stopApplicationWithUnsuccessfulStopApplicationResponseFails() {
        // Given
        awaitDeviceConnected()
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody("running")))
        server.enqueue(MockResponse().setResponseCode(404))
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)

        // When
        referenceDevice.stopApplication(onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run()
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
        val httpException = errorCaptor.firstValue.cause as? HttpException
        assertEquals(404, httpException?.statusCode)
    }

    //endregion

    //region Commands and replies

    data class TestReplyParams(val replyName: String)

    @Test
    fun sendMessagesSucceeds() {
        // Given
        awaitApplicationStarted()
        val firstMessage = OCastApplicationLayer("org.ocast.service", OCastCommandParams("firstCommandName").build())
        val secondMessage = OCastApplicationLayer("org.ocast.service", OCastCommandParams("secondCommandName").build())
        val firstOnSuccess = mock<Consumer<TestReplyParams>>()
        val secondOnSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val firstSynchronizedOnSuccess = SynchronizedConsumer(firstOnSuccess)
        val secondSynchronizedOnSuccess = SynchronizedRunnable(secondOnSuccess)
        val firstReceivedMessage = """
            {
              "dst": "*",
              "src": "browser",
              "type": "reply",
              "status": "OK",
              "id": 1,
              "message": {
                "service": "org.ocast.service.first",
                "data": {
                  "name": "firstCommandName",
                  "params": {
                    "replyName": "replyValue"
                  }
                }
              }
            }
        """.trimIndent()
        val secondReceivedMessage = """
            {
              "dst": "*",
              "src": "settings",
              "type": "reply",
              "status": "OK",
              "id": 2,
              "message": {
                "service": "org.ocast.service.second",
                "data": {
                  "name": "secondCommandName",
                  "params": {
                    "code": 0
                  }
                }
              }
            }
        """.trimIndent()
        stubReceivedMessages(firstReceivedMessage to 200, secondReceivedMessage to 100)

        // When
        referenceDevice.send(firstMessage, OCastDomain.BROWSER, TestReplyParams::class.java, firstSynchronizedOnSuccess, onError)
        referenceDevice.send(secondMessage, OCastDomain.SETTINGS, secondSynchronizedOnSuccess, onError)

        // Then
        firstSynchronizedOnSuccess.await(5, TimeUnit.SECONDS)
        secondSynchronizedOnSuccess.await(5, TimeUnit.SECONDS)
        val firstReplyParamsCaptor = argumentCaptor<TestReplyParams>()
        verify(firstOnSuccess, times(1)).run(firstReplyParamsCaptor.capture())
        assertEquals(TestReplyParams("replyValue"), firstReplyParamsCaptor.firstValue)
        verify(secondOnSuccess, times(1)).run()
        verify(onError, never()).run(any())
    }

    @Test
    fun sendMessageWhileDisconnectFails() {
        // Given
        awaitDeviceConnected()
        val message = OCastApplicationLayer("org.ocast.service", OCastCommandParams("commandName").build())
        val onSuccess = mock<Consumer<TestReplyParams>>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)
        val receivedMessage = """
            {
              "dst": "*",
              "src": "browser",
              "type": "reply",
              "status": "OK",
              "id": 1,
              "message": {
                "service": "org.ocast.service",
                "data": {
                  "name": "commandName",
                  "params": {
                    "replyName": "replyValue"
                  }
                }
              }
            }
        """.trimIndent()
        stubReceivedMessages(receivedMessage to 10000)

        // When
        referenceDevice.send(message, OCastDomain.SETTINGS, TestReplyParams::class.java, onSuccess, synchronizedOnError)
        Thread.sleep(200)
        referenceDevice.disconnect({}, {})

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run(any())
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
    }

    @Test
    fun sendMessageWhenDisconnectedFails() {
        // Given
        val message = OCastApplicationLayer("org.ocast.service", OCastCommandParams("commandName").build())
        val onSuccess = mock<Consumer<TestReplyParams>>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)
        val receivedMessage = """
            {
              "dst": "*",
              "src": "browser",
              "type": "reply",
              "status": "OK",
              "id": 1,
              "message": {
                "service": "org.ocast.service",
                "data": {
                  "name": "commandName",
                  "params": {
                    "replyName": "replyValue"
                  }
                }
              }
            }
        """.trimIndent()
        stubReceivedMessages(receivedMessage to 100)
        assertEquals(Device.State.DISCONNECTED, referenceDevice.state)

        // When
        referenceDevice.send(message, OCastDomain.SETTINGS, TestReplyParams::class.java, onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run(any())
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
    }

    @Test
    fun sendMessageWithWebSocketErrorFails() {
        // Given
        awaitDeviceConnected()
        val message = OCastApplicationLayer("org.ocast.service", OCastCommandParams("commandName").build())
        val onSuccess = mock<Consumer<TestReplyParams>>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)
        doReturn(false).whenever(realWebSocket).send(any<String>())

        // When
        referenceDevice.send(message, OCastDomain.SETTINGS, TestReplyParams::class.java, onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run(any())
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
    }

    @Test
    fun sendMessageWithReceivedErrorStatusFails() {
        // Given
        awaitDeviceConnected()
        val message = OCastApplicationLayer("org.ocast.service", OCastCommandParams("commandName").build())
        val onSuccess = mock<Consumer<TestReplyParams>>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)
        val receivedMessage = """
            {
              "dst": "*",
              "src": "browser",
              "type": "reply",
              "status": "INTERNAL_ERROR",
              "id": 1,
              "message": {
                "service": "org.ocast.service",
                "data": {
                  "name": "commandName",
                  "params": {
                    "replyName": "replyValue"
                  }
                }
              }
            }
        """.trimIndent()
        stubReceivedMessages(receivedMessage to 100)

        // When
        referenceDevice.send(message, OCastDomain.SETTINGS, TestReplyParams::class.java, onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run(any())
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.DEVICE_LAYER_ERROR.code, errorCaptor.firstValue.code)
    }

    @Test
    fun sendMessageWithReceivedErrorCodeFails() {
        // Given
        awaitDeviceConnected()
        val message = OCastApplicationLayer("org.ocast.service", OCastCommandParams("commandName").build())
        val onSuccess = mock<Consumer<TestReplyParams>>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)
        val receivedMessage = """
            {
              "dst": "*",
              "src": "browser",
              "type": "reply",
              "status": "OK",
              "id": 1,
              "message": {
                "service": "org.ocast.service",
                "data": {
                  "name": "commandName",
                  "params": {
                    "code": 1234,
                    "replyName": "replyValue"
                  }
                }
              }
            }
        """.trimIndent()
        stubReceivedMessages(receivedMessage to 100)

        // When
        referenceDevice.send(message, OCastDomain.SETTINGS, TestReplyParams::class.java, onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run(any())
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(1234, errorCaptor.firstValue.code)
    }

    @Test
    fun sendMessageWithEncodeErrorFails() {
        // Given
        awaitDeviceConnected()
        val data = OCastDataLayer("commandName", Object(), null) // Jackson cannot encode empty beans
        val message = OCastApplicationLayer("org.ocast.service", data)
        val onSuccess = mock<Consumer<TestReplyParams>>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)
        val receivedMessage = """
            {
              "dst": "*",
              "src": "browser",
              "type": "reply",
              "status": "OK",
              "id": 1,
              "message": {
                "service": "org.ocast.service",
                "data": {
                  "name": "commandName",
                  "params": {
                    "replyName": "replyValue"
                  }
                }
              }
            }
        """.trimIndent()
        stubReceivedMessages(receivedMessage to 100)

        // When
        referenceDevice.send(message, OCastDomain.SETTINGS, TestReplyParams::class.java, onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run(any())
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
        assertThat(errorCaptor.firstValue.cause, instanceOf(JsonProcessingException::class.java))
    }

    @Test
    fun sendMessageWithMalformedReceivedReplyParamsFails() {
        // Given
        awaitDeviceConnected()
        val message = OCastApplicationLayer("org.ocast.service", OCastCommandParams("commandName").build())
        val onSuccess = mock<Consumer<TestReplyParams>>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)
        val receivedMessage = """
            {
              "dst": "*",
              "src": "browser",
              "type": "reply",
              "status": "OK",
              "id": 1,
              "message": {
                "service": "org.ocast.service",
                "data": {
                  "name": "commandName",
                  "params": {
                    "wrongReplyName": "replyValue"
                  }
                }
              }
            }
        """.trimIndent()
        stubReceivedMessages(receivedMessage to 100)

        // When
        referenceDevice.send(message, OCastDomain.SETTINGS, TestReplyParams::class.java, onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run(any())
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.DECODE_ERROR.code, errorCaptor.firstValue.code)
    }

    @Test
    fun sendBrowserMessageWhenApplicationStoppedSucceeds() {
        // Given
        awaitDeviceConnected()
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody()))
        server.enqueue(MockResponse().setResponseCode(201))
        val connectedStatusMessage = """
            {
              "dst": "89cf41b8-ef40-48d9-99c3-2a1951abcde5",
              "src": "browser",
              "type": "event",
              "status": "ok",
              "id": 666,
              "message": {
                "service": "org.ocast.webapp",
                "data": {
                  "name": "connectedStatus",
                  "params": {
                    "status": "connected"
                  }
                }
              }
            }
        """.trimIndent()
        scheduleReceivedMessages(connectedStatusMessage to 1000)
        val message = OCastApplicationLayer("org.ocast.service", OCastCommandParams("commandName").build())
        val onSuccess = mock<Consumer<TestReplyParams>>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnSuccess = SynchronizedConsumer(onSuccess)
        val receivedMessage = """
            {
              "dst": "*",
              "src": "browser",
              "type": "reply",
              "status": "OK",
              "id": 1,
              "message": {
                "service": "org.ocast.service",
                "data": {
                  "name": "commandName",
                  "params": {
                    "replyName": "replyValue"
                  }
                }
              }
            }
        """.trimIndent()
        stubReceivedMessages(receivedMessage to 100)

        // When
        referenceDevice.send(message, OCastDomain.BROWSER, TestReplyParams::class.java, synchronizedOnSuccess, onError)

        // Then
        synchronizedOnSuccess.await(5, TimeUnit.SECONDS)
        val replyParamsCaptor = argumentCaptor<TestReplyParams>()
        verify(onSuccess, times(1)).run(replyParamsCaptor.capture())
        assertEquals(TestReplyParams("replyValue"), replyParamsCaptor.firstValue)
        verify(onError, never()).run(any())
    }

    @Test
    fun sendBrowserMessageWithUnsuccessfulGetApplicationResponseFails() {
        // Given
        awaitDeviceConnected()
        server.enqueue(MockResponse().setResponseCode(404))
        val message = OCastApplicationLayer("org.ocast.service", OCastCommandParams("commandName").build())
        val onSuccess = mock<Consumer<TestReplyParams>>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)
        val receivedMessage = """
            {
              "dst": "*",
              "src": "browser",
              "type": "reply",
              "status": "OK",
              "id": 1,
              "message": {
                "service": "org.ocast.service",
                "data": {
                  "name": "commandName",
                  "params": {
                    "replyName": "replyValue"
                  }
                }
              }
            }
        """.trimIndent()
        stubReceivedMessages(receivedMessage to 100)

        // When
        referenceDevice.send(message, OCastDomain.BROWSER, TestReplyParams::class.java, onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run(any())
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
    }

    //endregion

    //region Events

    @Test
    fun receiveMediaPlaybackStatusEventCallsEventListenerOnMediaPlaybackStatus() {
        // Given
        awaitDeviceConnected()
        val receivedMessage = """
            {
              "dst": "89cf41b8-ef40-48d9-99c3-2a1951abcde5",
              "src": "browser",
              "type": "event",
              "status": "OK",
              "id": 1,
              "message": {
                "service": "org.ocast.media",
                "data": {
                  "name": "playbackStatus",
                  "params": {
                    "volume": 0.3,
                    "mute": true,
                    "state": 3,
                    "position": 303,
                    "duration": 4673.7
                  }
                }
              }
            }
        """.trimIndent()

        // When
        scheduleReceivedMessages(receivedMessage to 100)

        // Then
        Thread.sleep(2000) // For some reason decoding JSON on a background thread takes a lot of time
        val playbackStatusCaptor = argumentCaptor<MediaPlaybackStatus>()
        verify(eventListener, times(1)).onMediaPlaybackStatus(eq(referenceDevice), playbackStatusCaptor.capture())
        val playbackStatus = playbackStatusCaptor.firstValue
        assertEquals(0.3, playbackStatus.volume, 0.0)
        assertEquals(true, playbackStatus.isMuted)
        assertEquals(MediaPlaybackStatus.State.PAUSED, playbackStatus.state)
        assertEquals(303.0, playbackStatus.position, 0.0)
        assertEquals(4673.7, playbackStatus.duration)
    }

    @Test
    fun receiveMediaMetadataChangedEventCallsEventListenerOnMediaMetadataChanged() {
        // Given
        awaitDeviceConnected()
        val receivedMessage = """
            {
              "dst": "89cf41b8-ef40-48d9-99c3-2a1951abcde5",
              "src": "browser",
              "type": "event",
              "status": "OK",
              "id": 1,
              "message": {
                "service": "org.ocast.media",
                "data": {
                  "name": "metadataChanged",
                  "params": {
                    "title": "La cité de la peur",
                    "subtitle": "Un film de les nuls",
                    "logo": "http://localhost/logo",
                    "mediaType": "video",
                    "textTracks": [],
                    "audioTracks": [
                      {
                        "type": "audio",
                        "language": "de",
                        "label": "Audio DE",
                        "enable": true,
                        "trackId": "id123"
                      }
                    ]
                  }
                }
              }
            }
        """.trimIndent()

        // When
        scheduleReceivedMessages(receivedMessage to 100)

        // Then
        Thread.sleep(2000) // For some reason decoding JSON on a background thread takes a lot of time
        val metadataCaptor = argumentCaptor<MediaMetadata>()
        verify(eventListener, times(1)).onMediaMetadataChanged(eq(referenceDevice), metadataCaptor.capture())
        val metadata = metadataCaptor.firstValue
        assertEquals("La cité de la peur", metadata.title)
        assertEquals("Un film de les nuls", metadata.subtitle)
        assertEquals("http://localhost/logo", metadata.logo)
        assertEquals(Media.Type.VIDEO, metadata.mediaType)
        assertEquals(0, metadata.subtitleTracks?.size)
        assertEquals(1, metadata.audioTracks?.size)
        val audioTrack = metadata.audioTracks?.firstOrNull()
        assertEquals("id123", audioTrack?.id)
        assertEquals("de", audioTrack?.language)
        assertEquals("Audio DE", audioTrack?.label)
        assertEquals(true, audioTrack?.isEnabled)
        assertNull(metadata.videoTracks)
    }

    @Test
    fun receiveUpdateStatusEventCallsEventListenerOnUpdateStatus() {
        // Given
        awaitDeviceConnected()
        val receivedMessage = """
            {
              "dst": "89cf41b8-ef40-48d9-99c3-2a1951abcde5",
              "src": "browser",
              "type": "event",
              "status": "OK",
              "id": 1,
              "message": {
                "service": "org.ocast.settings.device",
                "data": {
                  "name": "updateStatus",
                  "params": {
                    "state": "downloading",
                    "version": "1.0",
                    "progress": 50
                  }
                }
              }
            }
        """.trimIndent()

        // When
        scheduleReceivedMessages(receivedMessage to 100)

        // Then
        Thread.sleep(2000) // For some reason decoding JSON on a background thread takes a lot of time
        val updateStatusCaptor = argumentCaptor<UpdateStatus>()
        verify(eventListener, times(1)).onUpdateStatus(eq(referenceDevice), updateStatusCaptor.capture())
        val updateStatus = updateStatusCaptor.firstValue
        assertEquals(UpdateStatus.State.DOWNLOADING, updateStatus.state)
        assertEquals(50, updateStatus.progress)
        assertEquals("1.0", updateStatus.version)
    }

    @Test
    fun receiveCustomEventCallsEventListenerOnCustomEvent() {
        // Given
        awaitDeviceConnected()
        val receivedMessage = """
            {
              "dst": "89cf41b8-ef40-48d9-99c3-2a1951abcde5",
              "src": "browser",
              "type": "event",
              "status": "OK",
              "id": 1,
              "message": {
                "service": "org.ocast.service",
                "data": {
                  "name": "customEvent",
                  "params": {
                    "paramName": "paramValue"
                  }
                }
              }
            }
        """.trimIndent()

        // When
        scheduleReceivedMessages(receivedMessage to 100)

        // Then
        Thread.sleep(2000) // For some reason decoding JSON on a background thread takes a lot of time
        verify(eventListener, times(1)).onCustomEvent(eq(referenceDevice), eq("customEvent"), eq("{\"paramName\":\"paramValue\"}"))
    }

    //endregion

    //region Private methods

    /**
     * Stubs the underlying OkHttp web socket.
     */
    private fun stubWebSocket() {
        val webSocket = object : WebSocket("wss://192.168.1.65:4433/ocast", null, referenceDevice) {

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
     * Schedules a list of messages received on the web socket.
     *
     * @param messages The list of messages with their associated delay.
     */
    private fun scheduleReceivedMessages(vararg messages: Pair<String, Long>) {
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
    private fun stubReceivedMessages(vararg messages: Pair<String, Long>) {
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
    private fun awaitDeviceConnected() {
        if (referenceDevice.state != Device.State.CONNECTED) {
            server.enqueue(MockResponse().setBody(createGetApplicationResponseBody()))
            val synchronizedOnSuccess = SynchronizedRunnable(Runnable {})
            referenceDevice.connect(null, synchronizedOnSuccess, {})
            synchronizedOnSuccess.await(5, TimeUnit.SECONDS)
        }
        assertEquals(Device.State.CONNECTED, referenceDevice.state)
    }

    /**
     * Waits for the application to start.
     */
    private fun awaitApplicationStarted() {
        awaitDeviceConnected()
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody("running")))
        val synchronizedOnSuccess = SynchronizedRunnable(Runnable {})
        referenceDevice.startApplication(synchronizedOnSuccess, {})
        synchronizedOnSuccess.await(5, TimeUnit.SECONDS)
    }

    /**
     * Creates a response body for the DIAL get application request.
     *
     * @param state The state of the DIAL application.
     * @return The response body.
     */
    private fun createGetApplicationResponseBody(state: String = "stopped"): String {
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
