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
import okhttp3.mockwebserver.MockResponse
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
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
import java.util.Timer
import java.util.concurrent.TimeUnit
import kotlin.concurrent.schedule

/**
 * Unit tests for the [ReferenceDevice] class.
 */
class ReferenceDeviceTest : DeviceTest<ReferenceDevice>(ReferenceDevice::class.java) {

    //region Connect

    @Test
    fun connectSucceeds() {
        // Given
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody()))
        val onSuccess = mock<Runnable>()
        val synchronizedOnSuccess = SynchronizedRunnable(onSuccess)
        val onError = mock<Consumer<OCastError>>()

        // When
        device.connect(null, synchronizedOnSuccess, onError)

        // Then
        synchronizedOnSuccess.await(5, TimeUnit.SECONDS)
        verify(onSuccess, times(1)).run()
        verify(onError, never()).run(any())
        assertEquals(Device.State.CONNECTED, device.state)
    }

    @Test
    fun connectWithoutApplicationNameSucceeds() {
        // Given
        device.applicationName = null
        val onSuccess = mock<Runnable>()
        val synchronizedOnSuccess = SynchronizedRunnable(onSuccess)
        val onError = mock<Consumer<OCastError>>()

        // When
        device.connect(null, synchronizedOnSuccess, onError)

        // Then
        synchronizedOnSuccess.await(5, TimeUnit.SECONDS)
        verify(onSuccess, times(1)).run()
        verify(onError, never()).run(any())
        assertEquals(Device.State.CONNECTED, device.state)
    }

    @Test
    fun connectWhenAlreadyConnectedSucceeds() {
        // Given
        awaitDeviceConnected()
        val onSuccess = mock<Runnable>()
        val synchronizedOnSuccess = SynchronizedRunnable(onSuccess)
        val onError = mock<Consumer<OCastError>>()

        // When
        device.connect(null, synchronizedOnSuccess, onError)

        // Then
        synchronizedOnSuccess.await(5, TimeUnit.SECONDS)
        verify(onSuccess, times(1)).run()
        verify(onError, never()).run(any())
        assertEquals(Device.State.CONNECTED, device.state)
    }

    @Test
    fun connectWhenConnectingFails() {
        // Given
        repeat(2) {
            server.enqueue(MockResponse().setBody(createGetApplicationResponseBody()))
        }
        device.connect(null, {}, {})
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)
        assertEquals(Device.State.CONNECTING, device.state)

        // When
        device.connect(null, onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run()
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
        assertEquals(Device.State.CONNECTING, device.state)
    }

    @Test
    fun connectWhenDisconnectingFails() {
        // Given
        awaitDeviceConnected()
        device.disconnect({}, {})
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody()))
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)
        assertEquals(Device.State.DISCONNECTING, device.state)

        // When
        device.connect(null, onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run()
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
        assertEquals(Device.State.DISCONNECTING, device.state)
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
        device.connect(null, onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run()
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
        assertEquals(throwable, errorCaptor.firstValue.cause)
        assertEquals(Device.State.DISCONNECTED, device.state)
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
        device.connect(null, synchronizedOnSuccess, onError)

        // Then
        synchronizedOnSuccess.await(5, TimeUnit.SECONDS)
        verify(onSuccess, times(1)).run()
        verify(onError, never()).run(any())
        assertEquals(Device.State.CONNECTED, device.state)
    }

    @Test
    fun connectWithMalformedGetApplicationResponseSucceeds() {
        // Given
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody("unknown state")))
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnSuccess = SynchronizedRunnable(onSuccess)

        // When
        device.connect(null, synchronizedOnSuccess, onError)

        // Then
        synchronizedOnSuccess.await(5, TimeUnit.SECONDS)
        verify(onSuccess, times(1)).run()
        verify(onError, never()).run(any())
        assertEquals(Device.State.CONNECTED, device.state)
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
        device.disconnect(synchronizedOnSuccess, onError)

        // Then
        synchronizedOnSuccess.await(5, TimeUnit.SECONDS)
        verify(onSuccess, times(1)).run()
        verify(onError, never()).run(any())
        assertEquals(Device.State.DISCONNECTED, device.state)
        verify(deviceListener, never()).onDeviceDisconnected(any(), anyOrNull())
    }

    @Test
    fun disconnectWhenAlreadyDisconnectedSucceeds() {
        // Given
        val onSuccess = mock<Runnable>()
        val synchronizedOnSuccess = SynchronizedRunnable(onSuccess)
        val onError = mock<Consumer<OCastError>>()
        assertEquals(Device.State.DISCONNECTED, device.state)

        // When
        device.disconnect(synchronizedOnSuccess, onError)

        // Then
        synchronizedOnSuccess.await(5, TimeUnit.SECONDS)
        verify(onSuccess, times(1)).run()
        verify(onError, never()).run(any())
        assertEquals(Device.State.DISCONNECTED, device.state)
        verify(deviceListener, never()).onDeviceDisconnected(any(), anyOrNull())
    }

    @Test
    fun disconnectWhenConnectingFails() {
        // Given
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody()))
        device.connect(null, {}, {})
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)
        assertEquals(Device.State.CONNECTING, device.state)

        // When
        device.disconnect(onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run()
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
        assertEquals(Device.State.CONNECTING, device.state)
    }

    @Test
    fun disconnectWhenDisconnectingFails() {
        // Given
        awaitDeviceConnected()
        device.disconnect({}, {})
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)
        assertEquals(Device.State.DISCONNECTING, device.state)

        // When
        device.disconnect(onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run()
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
        assertEquals(Device.State.DISCONNECTING, device.state)
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
        device.disconnect(onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run()
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.CLIENT_ERROR.code, errorCaptor.firstValue.code)
        assertEquals(throwable, errorCaptor.firstValue.cause)
        assertEquals(Device.State.DISCONNECTED, device.state)
    }

    @Test
    fun webSocketFailureCallsDeviceListenerOnDeviceDisconnectedWithError() {
        // Given
        awaitDeviceConnected()

        // When
        val throwable = Throwable()
        Timer().schedule(100L) {
            webSocket?.onFailure(realWebSocket, throwable, null)
        }

        // Then
        Thread.sleep(500)
        verify(deviceListener, times(1)).onDeviceDisconnected(eq(device), eq(throwable))
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
        device.startApplication(synchronizedOnSuccess, onError)

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
        device.startApplication(synchronizedOnSuccess, onError)

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
        device.startApplication(onSuccess, synchronizedOnError)
        device.applicationName = "OtherApp"

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
        device.startApplication(onSuccess, synchronizedOnError)

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
        device.startApplication(onSuccess, synchronizedOnError)

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
        device.startApplication(onSuccess, synchronizedOnError)

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
        device.applicationName = null
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody()))
        server.enqueue(MockResponse().setResponseCode(201))
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)

        // When
        device.startApplication(onSuccess, synchronizedOnError)

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
        assertEquals(Device.State.DISCONNECTED, device.state)

        // When
        device.startApplication(onSuccess, synchronizedOnError)

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
        device.connect(null, {}, {})
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody()))
        server.enqueue(MockResponse().setResponseCode(201))
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)
        assertEquals(Device.State.CONNECTING, device.state)

        // When
        device.startApplication(onSuccess, synchronizedOnError)

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
        device.disconnect({}, {})
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody()))
        server.enqueue(MockResponse().setResponseCode(201))
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)
        assertEquals(Device.State.DISCONNECTING, device.state)

        // When
        device.startApplication(onSuccess, synchronizedOnError)

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
        device.stopApplication(synchronizedOnSuccess, onError)

        // Then
        synchronizedOnSuccess.await(5, TimeUnit.SECONDS)
        verify(onSuccess, times(1)).run()
        verify(onError, never()).run(any())
    }

    @Test
    fun stopApplicationWithoutApplicationNameFails() {
        // Given
        awaitDeviceConnected()
        device.applicationName = null
        server.enqueue(MockResponse().setBody(createGetApplicationResponseBody("running")))
        server.enqueue(MockResponse())
        val onSuccess = mock<Runnable>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)

        // When
        device.stopApplication(onSuccess, synchronizedOnError)

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
        device.stopApplication(onSuccess, synchronizedOnError)

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
        device.stopApplication(onSuccess, synchronizedOnError)

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
        device.send(firstMessage, OCastDomain.BROWSER, TestReplyParams::class.java, firstSynchronizedOnSuccess, onError)
        device.send(secondMessage, OCastDomain.SETTINGS, secondSynchronizedOnSuccess, onError)

        // Then
        firstSynchronizedOnSuccess.await(5, TimeUnit.SECONDS)
        secondSynchronizedOnSuccess.await(5, TimeUnit.SECONDS)
        verify(firstOnSuccess, times(1)).run(eq(TestReplyParams("replyValue")))
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
        device.send(message, OCastDomain.SETTINGS, TestReplyParams::class.java, onSuccess, synchronizedOnError)
        device.disconnect({}, {})

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
        assertEquals(Device.State.DISCONNECTED, device.state)

        // When
        device.send(message, OCastDomain.SETTINGS, TestReplyParams::class.java, onSuccess, synchronizedOnError)

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
        device.send(message, OCastDomain.SETTINGS, TestReplyParams::class.java, onSuccess, synchronizedOnError)

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
        device.send(message, OCastDomain.SETTINGS, TestReplyParams::class.java, onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run(any())
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.DEVICE_LAYER_INTERNAL_ERROR.code, errorCaptor.firstValue.code)
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
        device.send(message, OCastDomain.SETTINGS, TestReplyParams::class.java, onSuccess, synchronizedOnError)

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
        val data = OCastDataLayer("commandName", Object(), null) // Jackson cannot encode empty beans like Object
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
        device.send(message, OCastDomain.SETTINGS, TestReplyParams::class.java, onSuccess, synchronizedOnError)

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
        device.send(message, OCastDomain.SETTINGS, TestReplyParams::class.java, onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run(any())
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.DECODE_ERROR.code, errorCaptor.firstValue.code)
    }

    @Test
    fun sendMessageWithReceivedForbiddenUnsecureModeStatusFails() {
        // Given
        awaitDeviceConnected()
        val message = OCastApplicationLayer("org.ocast.service", OCastCommandParams("commandName").build())
        val onSuccess = mock<Consumer<TestReplyParams>>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)
        val receivedMessage = """
            {
              "dst": null,
              "src": null,
              "type": "reply",
              "status": "forbidden_unsecure_mode",
              "id": -1,
              "message": {
              }
            }
        """.trimIndent()
        stubReceivedMessages(receivedMessage to 100)

        // When
        device.send(message, OCastDomain.SETTINGS, TestReplyParams::class.java, onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run(any())
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.DEVICE_LAYER_FORBIDDEN_UNSECURE_MODE.code, errorCaptor.firstValue.code)
    }

    @Test
    fun sendMessageWithMissingReceivedStatusFails() {
        // Given
        awaitDeviceConnected()
        val message = OCastApplicationLayer("org.ocast.service", OCastCommandParams("commandName").build())
        val onSuccess = mock<Consumer<TestReplyParams>>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)
        val receivedMessage = """
            {
              "dst": null,
              "src": null,
              "type": "reply",
              "status": null,
              "id": -1,
              "message": {
              }
            }
        """.trimIndent()
        stubReceivedMessages(receivedMessage to 100)

        // When
        device.send(message, OCastDomain.SETTINGS, TestReplyParams::class.java, onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run(any())
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.DEVICE_LAYER_MISSING_STATUS.code, errorCaptor.firstValue.code)
    }

    @Test
    fun sendMessageWithMissingReceivedReplyDataFails() {
        // Given
        awaitDeviceConnected()
        val message = OCastApplicationLayer("org.ocast.service", OCastCommandParams("commandName").build())
        val onSuccess = mock<Consumer<TestReplyParams>>()
        val onError = mock<Consumer<OCastError>>()
        val synchronizedOnError = SynchronizedConsumer(onError)
        val receivedMessage = """
            {
              "dst": null,
              "src": null,
              "type": "reply",
              "status": "ok",
              "id": -1,
              "message": {
              }
            }
        """.trimIndent()
        stubReceivedMessages(receivedMessage to 100)

        // When
        device.send(message, OCastDomain.SETTINGS, TestReplyParams::class.java, onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run(any())
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.DEVICE_LAYER_MISSING_REPLY_DATA.code, errorCaptor.firstValue.code)
    }

    @Test
    fun sendMessageWithUnknownReceivedStatusFails() {
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
              "status": "OCAST_UNKNOWN_STATUS",
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
        device.send(message, OCastDomain.SETTINGS, TestReplyParams::class.java, onSuccess, synchronizedOnError)

        // Then
        synchronizedOnError.await(5, TimeUnit.SECONDS)
        verify(onSuccess, never()).run(any())
        val errorCaptor = argumentCaptor<OCastError>()
        verify(onError, times(1)).run(errorCaptor.capture())
        assertEquals(OCastError.Status.DEVICE_LAYER_UNKNOWN_ERROR.code, errorCaptor.firstValue.code)
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
        device.send(message, OCastDomain.BROWSER, TestReplyParams::class.java, synchronizedOnSuccess, onError)

        // Then
        synchronizedOnSuccess.await(5, TimeUnit.SECONDS)
        verify(onSuccess, times(1)).run(eq(TestReplyParams("replyValue")))
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
        device.send(message, OCastDomain.BROWSER, TestReplyParams::class.java, onSuccess, synchronizedOnError)

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
        Thread.sleep(3000) // Decoding JSON on a background thread for the first time takes a lot of time
        val playbackStatusCaptor = argumentCaptor<MediaPlaybackStatus>()
        verify(eventListener, times(1)).onMediaPlaybackStatus(eq(device), playbackStatusCaptor.capture())
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
        Thread.sleep(3000) // Decoding JSON on a background thread for the first time takes a lot of time
        val metadataCaptor = argumentCaptor<MediaMetadata>()
        verify(eventListener, times(1)).onMediaMetadataChanged(eq(device), metadataCaptor.capture())
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
        Thread.sleep(3000) // Decoding JSON on a background thread for the first time takes a lot of time
        val updateStatusCaptor = argumentCaptor<UpdateStatus>()
        verify(eventListener, times(1)).onUpdateStatus(eq(device), updateStatusCaptor.capture())
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
        Thread.sleep(3000) // Decoding JSON on a background thread for the first time takes a lot of time
        verify(eventListener, times(1)).onCustomEvent(eq(device), eq("customEvent"), eq("{\"paramName\":\"paramValue\"}"))
    }

    //endregion
}
