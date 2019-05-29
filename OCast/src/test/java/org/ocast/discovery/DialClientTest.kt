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

package org.ocast.discovery

import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.ocast.common.SynchronizedFunction1
import org.ocast.discovery.models.UpnpDevice
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PowerMockIgnore
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Unit tests for the [DialClient] class.
 */
@RunWith(PowerMockRunner::class)
@PowerMockIgnore("javax.net.ssl.*") // This fixes a java.lang.AssertionError with OkHttp and PowerMock
@PrepareForTest(DialClient::class)
class DialClientTest {

    /** The mocked web server. */
    private val server = MockWebServer()

    /** The DialClient to test. */
    private val dialClient = DialClient()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        // Sometimes an exception is thrown when shutting down the server although everything seems to be OK
        try {
            server.shutdown()
        } catch (exception: Exception) {
        }
    }

    @Test
    fun getDeviceRequestContainsDateHeader() {
        // Given
        val date = Date(1554400230000) // Thu, 4 Apr 2019 17:50:30 GMT
        PowerMockito.whenNew(Date::class.java).withNoArguments().thenReturn(date)
        val callback = mock<(UpnpDevice?) -> Unit>()

        // When
        dialClient.getDevice(server.url("/").toString(), callback)

        // Then
        assertEquals("Thu, 4 Apr 2019 17:50:30 GMT", server.takeRequest().getHeader("Date"))
    }

    @Test
    fun getDeviceWithSuccessfulResponseSucceeds() {
        // Given
        val response = """
                <root xmlns="urn:schemas-upnp-org:device-1-0" xmlns:r="urn:restful-tv-org:schemas:upnp-dd">
                  <specVersion>
                    <major>1</major>
                    <minor>0</minor>
                    </specVersion>
                  <device>
                    <deviceType>urn:schemas-upnp-org:device:tvdevice:1</deviceType>
                    <friendlyName>LaCléTV-32F7</friendlyName>
                    <manufacturer>Innopia</manufacturer>
                    <modelName>cléTV</modelName>
                    <UDN>uuid:b042f955-9ae7-44a8-ba6c-0009743932f7</UDN>
                  </device>
                </root>
            """.trimIndent()

        server.enqueue(
            MockResponse()
                .setHeader("Application-DIAL-URL", "http://127.0.0.1:8008/apps")
                .setBody(response)
        )
        val callback = mock<(UpnpDevice?) -> Unit>()
        val synchronizedCallback = SynchronizedFunction1(callback)

        // When
        dialClient.getDevice(server.url("/").toString(), synchronizedCallback)

        // Then
        synchronizedCallback.waitUntilInvoked(5, TimeUnit.SECONDS)
        val deviceCaptor = argumentCaptor<UpnpDevice>()
        verify(callback, times(1)).invoke(deviceCaptor.capture())
        val device = deviceCaptor.firstValue
        assertEquals("http://127.0.0.1:8008/apps", device.applicationURL.toString())
        assertEquals("LaCléTV-32F7", device.friendlyName)
        assertEquals("Innopia", device.manufacturer)
        assertEquals("cléTV", device.modelName)
        assertEquals("b055-9ae7-44a8-ba6c-0009743932f7", device.uuid)
    }

    @Test
    fun getDeviceWithUnsuccessfulResponseFails() {
        // Given
        server.enqueue(MockResponse().setResponseCode(404)) // Unsuccessful response
        val callback = mock<(UpnpDevice?) -> Unit>()
        val synchronizedCallback = SynchronizedFunction1(callback)

        // When
        dialClient.getDevice(server.url("/").toString(), synchronizedCallback)

        // Then
        synchronizedCallback.waitUntilInvoked(5, TimeUnit.SECONDS)
        val deviceCaptor = argumentCaptor<UpnpDevice>()
        verify(callback, times(1)).invoke(deviceCaptor.capture())
        assertNull(deviceCaptor.firstValue)
    }

    @Test
    fun getDeviceWithMalformedLocationFails() {
        // Given
        val response = """
                <root xmlns="urn:schemas-upnp-org:device-1-0" xmlns:r="urn:restful-tv-org:schemas:upnp-dd">
                  <specVersion>
                    <major>1</major>
                    <minor>0</minor>
                    </specVersion>
                  <device>
                    <deviceType>urn:schemas-upnp-org:device:tvdevice:1</deviceType>
                    <friendlyName>LaCléTV-32F7</friendlyName>
                    <manufacturer>Innopia</manufacturer>
                    <modelName>cléTV</modelName>
                    <UDN>uuid:b042f955-9ae7-44a8-ba6c-0009743932f7</UDN>
                  </device>
                </root>
            """.trimIndent()

        server.enqueue(
            MockResponse()
                .setHeader("Application-DIAL-URL", "http://127.0.0.1:8008/apps")
                .setBody(response)
        )
        val callback = mock<(UpnpDevice?) -> Unit>()
        val synchronizedCallback = SynchronizedFunction1(callback)

        // When
        dialClient.getDevice(":(", synchronizedCallback) // Malformed location

        // Then
        synchronizedCallback.waitUntilInvoked(5, TimeUnit.SECONDS)
        val deviceCaptor = argumentCaptor<UpnpDevice>()
        verify(callback, times(1)).invoke(deviceCaptor.capture())
        assertNull(deviceCaptor.firstValue)
    }

    @Test
    fun getDeviceWithMissingApplicationUrlHeaderFails() {
        // Given
        val response = """
                <root xmlns="urn:schemas-upnp-org:device-1-0" xmlns:r="urn:restful-tv-org:schemas:upnp-dd">
                  <specVersion>
                    <major>1</major>
                    <minor>0</minor>
                    </specVersion>
                  <device>
                    <deviceType>urn:schemas-upnp-org:device:tvdevice:1</deviceType>
                    <friendlyName>LaCléTV-32F7</friendlyName>
                    <manufacturer>Innopia</manufacturer>
                    <modelName>cléTV</modelName>
                    <UDN>uuid:b042f955-9ae7-44a8-ba6c-0009743932f7</UDN>
                  </device>
                </root>
            """.trimIndent()

        server.enqueue(MockResponse().setBody(response)) // Missing application URL header
        val callback = mock<(UpnpDevice?) -> Unit>()
        val synchronizedCallback = SynchronizedFunction1(callback)

        // When
        dialClient.getDevice(server.url("/").toString(), synchronizedCallback)

        // Then
        synchronizedCallback.waitUntilInvoked(5, TimeUnit.SECONDS)
        val deviceCaptor = argumentCaptor<UpnpDevice>()
        verify(callback, times(1)).invoke(deviceCaptor.capture())
        assertNull(deviceCaptor.firstValue)
    }

    @Test
    fun getDeviceWithReadResponseHeaderTimeoutFails() {
        // Given
        val response = """
                <root xmlns="urn:schemas-upnp-org:device-1-0" xmlns:r="urn:restful-tv-org:schemas:upnp-dd">
                  <specVersion>
                    <major>1</major>
                    <minor>0</minor>
                    </specVersion>
                  <device>
                    <deviceType>urn:schemas-upnp-org:device:tvdevice:1</deviceType>
                    <friendlyName>LaCléTV-32F7</friendlyName>
                    <manufacturer>Innopia</manufacturer>
                    <modelName>cléTV</modelName>
                    <UDN>uuid:b042f955-9ae7-44a8-ba6c-0009743932f7</UDN>
                  </device>
                </root>
            """.trimIndent()

        server.enqueue(
            MockResponse()
                .setHeader("Application-DIAL-URL", "http://127.0.0.1:8008/apps")
                .setBody(response)
                .setSocketPolicy(SocketPolicy.NO_RESPONSE) // Read response header timeout
        )
        val callback = mock<(UpnpDevice?) -> Unit>()
        val synchronizedCallback = SynchronizedFunction1(callback)

        // When
        dialClient.getDevice(server.url("/").toString(), synchronizedCallback)

        // Then
        synchronizedCallback.waitUntilInvoked(2, TimeUnit.MINUTES)
        val deviceCaptor = argumentCaptor<UpnpDevice>()
        verify(callback, times(1)).invoke(deviceCaptor.capture())
        assertNull(deviceCaptor.firstValue)
    }

    @Test
    fun getDeviceWithReadResponseBodyTimeoutFails() {
        // Given
        val response = """
                <root xmlns="urn:schemas-upnp-org:device-1-0" xmlns:r="urn:restful-tv-org:schemas:upnp-dd">
                  <specVersion>
                    <major>1</major>
                    <minor>0</minor>
                    </specVersion>
                  <device>
                    <deviceType>urn:schemas-upnp-org:device:tvdevice:1</deviceType>
                    <friendlyName>LaCléTV-32F7</friendlyName>
                    <manufacturer>Innopia</manufacturer>
                    <modelName>cléTV</modelName>
                    <UDN>uuid:b042f955-9ae7-44a8-ba6c-0009743932f7</UDN>
                  </device>
                </root>
            """.trimIndent()

        server.enqueue(
            MockResponse()
                .setHeader("Application-DIAL-URL", "http://127.0.0.1:8008/apps")
                .setBody(response)
                .setBodyDelay(1, TimeUnit.DAYS) // Read response body timeout
        )
        val callback = mock<(UpnpDevice?) -> Unit>()
        val synchronizedCallback = SynchronizedFunction1(callback)

        // When
        dialClient.getDevice(server.url("/").toString(), synchronizedCallback)

        // Then
        synchronizedCallback.waitUntilInvoked(2, TimeUnit.MINUTES)
        val deviceCaptor = argumentCaptor<UpnpDevice>()
        verify(callback, times(1)).invoke(deviceCaptor.capture())
        assertNull(deviceCaptor.firstValue)
    }

    @RunWith(Parameterized::class)
    class WithParameterizedMissingElements(private val element: String) {

        /** The mocked web server. */
        private val server = MockWebServer()

        /** The DialClient to test. */
        private val dialClient = DialClient()

        companion object {

            @JvmStatic
            @Parameterized.Parameters
            fun data() = listOf("friendlyName", "manufacturer", "modelName", "UDN")
        }

        @Test
        fun getDeviceWithMissingElementFails() {
            // Given
            val response = """
                <root xmlns="urn:schemas-upnp-org:device-1-0" xmlns:r="urn:restful-tv-org:schemas:upnp-dd">
                  <specVersion>
                    <major>1</major>
                    <minor>0</minor>
                    </specVersion>
                  <device>
                    <deviceType>urn:schemas-upnp-org:device:tvdevice:1</deviceType>
                    <friendlyName>LaCléTV-32F7</friendlyName>
                    <manufacturer>Innopia</manufacturer>
                    <modelName>cléTV</modelName>
                    <UDN>uuid:b042f955-9ae7-44a8-ba6c-0009743932f7</UDN>
                  </device>
                </root>
            """.trimIndent().removeElement(element) // Missing element

            server.enqueue(
                MockResponse()
                    .setHeader("Application-DIAL-URL", "http://127.0.0.1:8008/apps")
                    .setBody(response)
            )
            val callback = mock<(UpnpDevice?) -> Unit>()
            val synchronizedCallback = SynchronizedFunction1(callback)

            // When
            dialClient.getDevice(server.url("/").toString(), synchronizedCallback)

            // Then
            synchronizedCallback.waitUntilInvoked(5, TimeUnit.SECONDS)
            val deviceCaptor = argumentCaptor<UpnpDevice>()
            verify(callback, times(1)).invoke(deviceCaptor.capture())
            assertNull(deviceCaptor.firstValue)
        }

        /**
         * Removes an element from an XML response.
         *
         * @param element The element to remove.
         * @return The modified XML response.
         */
        private fun String.removeElement(element: String): String {
            return split("\\R+".toRegex())
                .toMutableList()
                .apply { removeIf { it.matches("^\\s*<$element>.*</$element>\\s*$".toRegex()) } }
                .joinToString("\r\n")
        }
    }
}