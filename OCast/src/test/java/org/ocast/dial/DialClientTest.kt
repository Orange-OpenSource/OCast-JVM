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

package org.ocast.dial

import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.ocast.common.HttpClientTest
import org.ocast.common.SynchronizedFunction1
import org.ocast.common.removeXMLElement
import org.ocast.dial.models.DialApplication
import org.ocast.dial.models.DialApplication.Decoder.XML_HIDDEN_STATE_TEXT_VALUE
import org.ocast.dial.models.DialApplication.Decoder.XML_INSTALLABLE_STATE_TEXT_VALUE
import org.ocast.dial.models.DialApplication.Decoder.XML_RUNNING_STATE_TEXT_VALUE
import org.ocast.dial.models.DialApplication.Decoder.XML_STOPPED_STATE_TEXT_VALUE
import java.net.URI
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.xml.ws.http.HTTPException

/**
 * Unit tests for the [DialClient] class.
 */
@RunWith(Enclosed::class)
internal class DialClientTest {

    class NotParameterized : HttpClientTest() {

        /** The instance of [DialClient] to test. */
        private val dialClient = DialClient(baseURL)

        //region Get application

        @Test
        fun getApplicationWithSuccessfulResponseAndShortInstanceURLSucceeds() {
            // Given
            val response = """
            <service xmlns="urn:dial-multiscreen-org:schemas:dial" xmlns:ocast="urn:cast-ocast-org:service:cast:1" dialVer="2.1">
              <name>OrangeTVReceiverProd</name>
              <options allowStop="true"/>
              <state>running</state>
              <additionalData>
                <ocast:X_OCAST_App2AppURL>wss://192.168.1.65:4433/ocast</ocast:X_OCAST_App2AppURL>
                <ocast:X_OCAST_Version>1.0</ocast:X_OCAST_Version>
              </additionalData>
              <link rel="run" href="run"/>
            </service>
            """.trimIndent() // Instance URL is a relative path (run)

            server.enqueue(MockResponse().setBody(response))
            val callback = mock<(Result<DialApplication>) -> Unit>()
            val synchronizedCallback = SynchronizedFunction1(callback)

            // When
            dialClient.getApplication("", synchronizedCallback)

            // Then
            synchronizedCallback.await(5, TimeUnit.SECONDS)
            val resultCaptor = argumentCaptor<Result<DialApplication>>()
            verify(callback, times(1)).invoke(resultCaptor.capture())
            val application = resultCaptor.firstValue.getOrNull()
            assertEquals("OrangeTVReceiverProd", application?.name)
            assertEquals(true, application?.isStopAllowed)
            assertEquals(DialApplication.State.Running, application?.state)
            assertEquals(URL("$baseURL/OrangeTVReceiverProd/run"), application?.getInstanceURL(baseURL))
            assertEquals(URI("wss://192.168.1.65:4433/ocast"), application?.additionalData?.webSocketURL)
            assertEquals("1.0", application?.additionalData?.version)
        }

        @Test
        fun getApplicationWithSuccessfulResponseAndLongInstanceURLSucceeds() {
            // Given
            val response = """
            <service xmlns="urn:dial-multiscreen-org:schemas:dial">
              <name>OrangeTVReceiverProd</name>
              <options allowStop="true"/>
              <state>running</state>
              <additionalData></additionalData>
              <link rel="run" href="http://192.168.1.23:8008/apps/OrangeTVReceiverProd/run"/>
            </service>
            """.trimIndent() // Instance URL is an absolute path (http://192.168.1.23:8008/apps/OrangeTVReceiverProd/run)

            server.enqueue(MockResponse().setBody(response))
            val callback = mock<(Result<DialApplication>) -> Unit>()
            val synchronizedCallback = SynchronizedFunction1(callback)

            // When
            dialClient.getApplication("", synchronizedCallback)

            // Then
            synchronizedCallback.await(5, TimeUnit.SECONDS)
            val resultCaptor = argumentCaptor<Result<DialApplication>>()
            verify(callback, times(1)).invoke(resultCaptor.capture())
            val application = resultCaptor.firstValue.getOrNull()
            assertEquals("OrangeTVReceiverProd", application?.name)
            assertEquals(true, application?.isStopAllowed)
            assertEquals(DialApplication.State.Running, application?.state)
            assertEquals(URL("http://192.168.1.23:8008/apps/OrangeTVReceiverProd/run"), application?.getInstanceURL(baseURL))
            assertNull(application?.additionalData?.webSocketURL)
            assertNull(application?.additionalData?.version)
        }

        @Test
        fun getApplicationWithUnsuccessfulResponseFails() {
            // Given
            server.enqueue(MockResponse().setResponseCode(404)) // Unsuccessful response
            val callback = mock<(Result<DialApplication>) -> Unit>()
            val synchronizedCallback = SynchronizedFunction1(callback)

            // When
            dialClient.getApplication("", synchronizedCallback)

            // Then
            synchronizedCallback.await(5, TimeUnit.SECONDS)
            val resultCaptor = argumentCaptor<Result<DialApplication>>()
            verify(callback, times(1)).invoke(resultCaptor.capture())
            val httpException = resultCaptor.firstValue.exceptionOrNull() as? HTTPException
            assertEquals(404, httpException?.statusCode)
        }

        @Test
        fun getApplicationWithTimeoutFails() {
            // Given
            val response = """
            <service xmlns="urn:dial-multiscreen-org:schemas:dial" xmlns:ocast="urn:cast-ocast-org:service:cast:1" dialVer="2.1">
              <name>OrangeTVReceiverProd</name>
              <options allowStop="true"/>
              <state>running</state>
              <additionalData>
                <ocast:X_OCAST_App2AppURL>wss://192.168.1.65:4433/ocast</ocast:X_OCAST_App2AppURL>
                <ocast:X_OCAST_Version>1.0</ocast:X_OCAST_Version>
              </additionalData>
              <link rel="run" href="run"/>
            </service>
            """.trimIndent()

            server.enqueue(MockResponse().setBody(response).setSocketPolicy(SocketPolicy.NO_RESPONSE)) // Read response header timeout
            server.enqueue(MockResponse().setBody(response).setBodyDelay(1, TimeUnit.DAYS)) // Read response body timeout
            val callback = mock<(Result<DialApplication>) -> Unit>()
            val synchronizedCallback = SynchronizedFunction1(callback, 2)

            // When
            dialClient.getApplication("", synchronizedCallback)
            dialClient.getApplication("", synchronizedCallback)

            // Then
            synchronizedCallback.await(2, TimeUnit.MINUTES)
            val resultCaptor = argumentCaptor<Result<DialApplication>>()
            verify(callback, times(2)).invoke(resultCaptor.capture())
            assertNotNull(resultCaptor.firstValue.exceptionOrNull())
            assertNotNull(resultCaptor.secondValue.exceptionOrNull())
        }

        //endregion

        //region Start application

        @Test
        fun startApplicationWithSuccessfulResponseSucceeds() {
            // Given
            server.enqueue(MockResponse().setResponseCode(201)) // Successful response with 201 HTTP code
            server.enqueue(MockResponse()) // Successful response with 200 HTTP code (application is already starting or running)
            val callback = mock<(Result<Unit>) -> Unit>()
            val synchronizedCallback = SynchronizedFunction1(callback, 2)

            // When
            dialClient.startApplication("", synchronizedCallback)
            dialClient.startApplication("", synchronizedCallback)

            // Then
            synchronizedCallback.await(5, TimeUnit.SECONDS)
            val resultCaptor = argumentCaptor<Result<Unit>>()
            verify(callback, times(2)).invoke(resultCaptor.capture())
            assertNull(resultCaptor.firstValue.exceptionOrNull())
            assertNull(resultCaptor.secondValue.exceptionOrNull())
        }

        @Test
        fun startApplicationWithUnsuccessfulResponseFails() {
            // Given
            server.enqueue(MockResponse().setResponseCode(404)) // Unsuccessful response
            val callback = mock<(Result<Unit>) -> Unit>()
            val synchronizedCallback = SynchronizedFunction1(callback)

            // When
            dialClient.startApplication("", synchronizedCallback)

            // Then
            synchronizedCallback.await(5, TimeUnit.SECONDS)
            val resultCaptor = argumentCaptor<Result<Unit>>()
            verify(callback, times(1)).invoke(resultCaptor.capture())
            val httpException = resultCaptor.firstValue.exceptionOrNull() as? HTTPException
            assertEquals(404, httpException?.statusCode)
        }

        @Test
        fun startApplicationWithTimeoutFails() {
            // Given
            server.enqueue(MockResponse().setResponseCode(201).setSocketPolicy(SocketPolicy.NO_RESPONSE)) // Read response header timeout
            val callback = mock<(Result<Unit>) -> Unit>()
            val synchronizedCallback = SynchronizedFunction1(callback)

            // When
            dialClient.startApplication("", synchronizedCallback)

            // Then
            synchronizedCallback.await(2, TimeUnit.MINUTES)
            val resultCaptor = argumentCaptor<Result<Unit>>()
            verify(callback, times(1)).invoke(resultCaptor.capture())
            assertNotNull(resultCaptor.firstValue.exceptionOrNull())
        }

        //endregion

        //region Stop application

        @Test
        fun stopApplicationWithStopAllowedAndInstanceURLSucceeds() {
            // Given
            val getApplicationResponse = """
            <service xmlns="urn:dial-multiscreen-org:schemas:dial" xmlns:ocast="urn:cast-ocast-org:service:cast:1" dialVer="2.1">
              <name>OrangeTVReceiverProd</name>
              <options allowStop="true"/>
              <state>running</state>
              <additionalData>
                <ocast:X_OCAST_App2AppURL>wss://192.168.1.65:4433/ocast</ocast:X_OCAST_App2AppURL>
                <ocast:X_OCAST_Version>1.0</ocast:X_OCAST_Version>
              </additionalData>
              <link rel="run" href="run"/>
            </service>
            """.trimIndent()

            server.enqueue(MockResponse().setBody(getApplicationResponse))
            server.enqueue(MockResponse())
            val callback = mock<(Result<Unit>) -> Unit>()
            val synchronizedCallback = SynchronizedFunction1(callback)

            // When
            dialClient.stopApplication("", synchronizedCallback)

            // Then
            synchronizedCallback.await(5, TimeUnit.SECONDS)
            val resultCaptor = argumentCaptor<Result<Unit>>()
            verify(callback, times(1)).invoke(resultCaptor.capture())
            assertNull(resultCaptor.firstValue.exceptionOrNull())
        }

        @Test
        fun stopApplicationWithStopNotAllowedAndInstanceURLFails() {
            // Given
            val getApplicationResponse = """
            <service xmlns="urn:dial-multiscreen-org:schemas:dial" xmlns:ocast="urn:cast-ocast-org:service:cast:1" dialVer="2.1">
              <name>OrangeTVReceiverProd</name>
              <options allowStop="false"/>
              <state>running</state>
              <additionalData>
                <ocast:X_OCAST_App2AppURL>wss://192.168.1.65:4433/ocast</ocast:X_OCAST_App2AppURL>
                <ocast:X_OCAST_Version>1.0</ocast:X_OCAST_Version>
              </additionalData>
              <link rel="run" href="run"/>
            </service>
            """.trimIndent() // allowStop is false

            server.enqueue(MockResponse().setBody(getApplicationResponse))
            server.enqueue(MockResponse())
            val callback = mock<(Result<Unit>) -> Unit>()
            val synchronizedCallback = SynchronizedFunction1(callback)

            // When
            dialClient.stopApplication("", synchronizedCallback)

            // Then
            synchronizedCallback.await(5, TimeUnit.SECONDS)
            val resultCaptor = argumentCaptor<Result<Unit>>()
            verify(callback, times(1)).invoke(resultCaptor.capture())
            assertNotNull(resultCaptor.firstValue.exceptionOrNull())
        }

        @Test
        fun stopApplicationWithMalformedInstanceURLFails() {
            // Given
            val getApplicationResponse = """
            <service xmlns="urn:dial-multiscreen-org:schemas:dial" xmlns:ocast="urn:cast-ocast-org:service:cast:1" dialVer="2.1">
              <name>OrangeTVReceiverProd</name>
              <options allowStop="true"/>
              <state>stopped</state>
              <additionalData>
                <ocast:X_OCAST_App2AppURL>wss://192.168.1.65:4433/ocast</ocast:X_OCAST_App2AppURL>
                <ocast:X_OCAST_Version>1.0</ocast:X_OCAST_Version>
              </additionalData>
              <link rel="run" href="http://(^_^)>
            </service>
            """.trimIndent() // Instance URL is malformed

            server.enqueue(MockResponse().setBody(getApplicationResponse))
            server.enqueue(MockResponse())
            val callback = mock<(Result<Unit>) -> Unit>()
            val synchronizedCallback = SynchronizedFunction1(callback)

            // When
            dialClient.stopApplication("", synchronizedCallback)

            // Then
            synchronizedCallback.await(5, TimeUnit.SECONDS)
            val resultCaptor = argumentCaptor<Result<Unit>>()
            verify(callback, times(1)).invoke(resultCaptor.capture())
            assertNotNull(resultCaptor.firstValue.exceptionOrNull())
        }

        @Test
        fun stopApplicationWithUnsuccessfulResponseFails() {
            // Given
            val getApplicationResponse = """
            <service xmlns="urn:dial-multiscreen-org:schemas:dial" xmlns:ocast="urn:cast-ocast-org:service:cast:1" dialVer="2.1">
              <name>OrangeTVReceiverProd</name>
              <options allowStop="true"/>
              <state>running</state>
              <additionalData>
                <ocast:X_OCAST_App2AppURL>wss://192.168.1.65:4433/ocast</ocast:X_OCAST_App2AppURL>
                <ocast:X_OCAST_Version>1.0</ocast:X_OCAST_Version>
              </additionalData>
              <link rel="run" href="run"/>
            </service>
            """.trimIndent()

            server.enqueue(MockResponse().setBody(getApplicationResponse))
            server.enqueue(MockResponse().setResponseCode(404)) // Unsuccessful response
            val callback = mock<(Result<Unit>) -> Unit>()
            val synchronizedCallback = SynchronizedFunction1(callback)

            // When
            dialClient.stopApplication("", synchronizedCallback)

            // Then
            synchronizedCallback.await(5, TimeUnit.SECONDS)
            val resultCaptor = argumentCaptor<Result<Unit>>()
            verify(callback, times(1)).invoke(resultCaptor.capture())
            val httpException = resultCaptor.firstValue.exceptionOrNull() as? HTTPException
            assertEquals(404, httpException?.statusCode)
        }

        @Test
        fun stopApplicationWithUnsuccessfulGetApplicationResponseFails() {
            // Given
            server.enqueue(MockResponse().setResponseCode(404)) // Unsuccessful get application response
            val callback = mock<(Result<Unit>) -> Unit>()
            val synchronizedCallback = SynchronizedFunction1(callback)

            // When
            dialClient.stopApplication("", synchronizedCallback)

            // Then
            synchronizedCallback.await(5, TimeUnit.SECONDS)
            val resultCaptor = argumentCaptor<Result<Unit>>()
            verify(callback, times(1)).invoke(resultCaptor.capture())
            val httpException = resultCaptor.firstValue.exceptionOrNull() as? HTTPException
            assertEquals(404, httpException?.statusCode)
        }

        @Test
        fun stopApplicationWithTimeoutFails() {
            // Given
            val getApplicationResponse = """
            <service xmlns="urn:dial-multiscreen-org:schemas:dial" xmlns:ocast="urn:cast-ocast-org:service:cast:1" dialVer="2.1">
              <name>OrangeTVReceiverProd</name>
              <options allowStop="true"/>
              <state>running</state>
              <additionalData>
                <ocast:X_OCAST_App2AppURL>wss://192.168.1.65:4433/ocast</ocast:X_OCAST_App2AppURL>
                <ocast:X_OCAST_Version>1.0</ocast:X_OCAST_Version>
              </additionalData>
              <link rel="run" href="run"/>
            </service>
            """.trimIndent()

            server.enqueue(MockResponse().setBody(getApplicationResponse))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)) // Read response header timeout
            val callback = mock<(Result<Unit>) -> Unit>()
            val synchronizedCallback = SynchronizedFunction1(callback)

            // When
            dialClient.stopApplication("", synchronizedCallback)

            // Then
            synchronizedCallback.await(2, TimeUnit.MINUTES)
            val resultCaptor = argumentCaptor<Result<Unit>>()
            verify(callback, times(1)).invoke(resultCaptor.capture())
            assertNotNull(resultCaptor.firstValue.exceptionOrNull())
        }

        //endregion
    }

    @RunWith(Parameterized::class)
    class WithParameterizedMissingElements(private val element: String) : HttpClientTest() {

        /** The instance of [DialClient] to test. */
        private val dialClient = DialClient(baseURL)

        companion object {

            @JvmStatic
            @Parameterized.Parameters
            fun data() = listOf("name", "options", "additionalData")
        }

        @Test
        fun getApplicationWithParameterizedMissingElementFails() {
            // Given
            val response = """
            <service xmlns="urn:dial-multiscreen-org:schemas:dial" xmlns:ocast="urn:cast-ocast-org:service:cast:1" dialVer="2.1">
              <name>OrangeTVReceiverProd</name>
              <options allowStop="true"/>
              <state>running</state>
              <additionalData>
                <ocast:X_OCAST_App2AppURL>wss://192.168.1.65:4433/ocast</ocast:X_OCAST_App2AppURL>
                <ocast:X_OCAST_Version>1.0</ocast:X_OCAST_Version>
              </additionalData>
              <link rel="run" href="run"/>
            </service>
            """.trimIndent().removeXMLElement(element) // Missing element

            server.enqueue(MockResponse().setBody(response))
            val callback = mock<(Result<DialApplication>) -> Unit>()
            val synchronizedCallback = SynchronizedFunction1(callback)

            // When
            dialClient.getApplication("", synchronizedCallback)

            // Then
            synchronizedCallback.await(5, TimeUnit.SECONDS)
            val resultCaptor = argumentCaptor<Result<DialApplication>>()
            verify(callback, times(1)).invoke(resultCaptor.capture())
            assertNotNull(resultCaptor.firstValue.exceptionOrNull())
        }
    }

    @RunWith(Parameterized::class)
    class WithParameterizedStates(private val state: Pair<String, DialApplication.State>) : HttpClientTest() {

        /** The instance of [DialClient] to test. */
        private val dialClient = DialClient(baseURL)

        companion object {

            @JvmStatic
            @Parameterized.Parameters
            fun data() = listOf(
                XML_RUNNING_STATE_TEXT_VALUE to DialApplication.State.Running,
                XML_STOPPED_STATE_TEXT_VALUE to DialApplication.State.Stopped,
                "$XML_INSTALLABLE_STATE_TEXT_VALUE=http://" to DialApplication.State.Installable(URL("http://")),
                XML_HIDDEN_STATE_TEXT_VALUE to DialApplication.State.Hidden
            )
        }

        @Test
        fun getApplicationWithParameterizedStateSucceeds() {
            // Given
            val response = """
            <service xmlns="urn:dial-multiscreen-org:schemas:dial" xmlns:ocast="urn:cast-ocast-org:service:cast:1" dialVer="2.1">
              <name>OrangeTVReceiverProd</name>
              <options allowStop="true"/>
              <state>${state.first}</state>
              <additionalData>
                <ocast:X_OCAST_App2AppURL>wss://192.168.1.65:4433/ocast</ocast:X_OCAST_App2AppURL>
                <ocast:X_OCAST_Version>1.0</ocast:X_OCAST_Version>
              </additionalData>
              <link rel="run" href="run"/>
            </service>
            """.trimIndent()

            server.enqueue(MockResponse().setBody(response))
            val callback = mock<(Result<DialApplication>) -> Unit>()
            val synchronizedCallback = SynchronizedFunction1(callback)

            // When
            dialClient.getApplication("", synchronizedCallback)

            // Then
            synchronizedCallback.await(5, TimeUnit.SECONDS)
            val resultCaptor = argumentCaptor<Result<DialApplication>>()
            verify(callback, times(1)).invoke(resultCaptor.capture())
            val application = resultCaptor.firstValue.getOrNull()
            assertEquals(state.second, application?.state)
        }
    }
}
