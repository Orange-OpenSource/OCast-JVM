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

package org.ocast.sdk.discovery

import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import java.util.Date
import java.util.concurrent.TimeUnit
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.ocast.sdk.common.HttpClientTest
import org.ocast.sdk.common.SynchronizedFunction1
import org.ocast.sdk.common.removeXMLElement
import org.ocast.sdk.discovery.models.UpnpDevice
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PowerMockIgnore
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

/**
 * Unit tests for the [UpnpClient] class.
 */
@RunWith(Enclosed::class)
internal class UpnpClientTest {

    @RunWith(PowerMockRunner::class)
    @PowerMockIgnore("javax.net.ssl.*") // This fixes a java.lang.AssertionError with OkHttp and PowerMock
    @PrepareForTest(UpnpClient::class)
    class NotParameterized : HttpClientTest() {

        /** The UpnpClient to test. */
        private val upnpClient = UpnpClient()

        @Test
        fun getDeviceRequestContainsDateHeader() {
            // Given
            val date = Date(1554400230000) // Thu, 4 Apr 2019 17:50:30 GMT
            PowerMockito.whenNew(Date::class.java).withNoArguments().thenReturn(date)
            val callback = mock<(Result<UpnpDevice>) -> Unit>()

            // When
            upnpClient.getDevice(server.url("/").toString(), callback)

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
            val callback = mock<(Result<UpnpDevice>) -> Unit>()
            val synchronizedCallback = SynchronizedFunction1(callback)

            // When
            upnpClient.getDevice(server.url("/").toString(), synchronizedCallback)

            // Then
            synchronizedCallback.await(5, TimeUnit.SECONDS)
            val resultCaptor = argumentCaptor<Result<UpnpDevice>>()
            verify(callback, times(1)).invoke(resultCaptor.capture())
            val device = resultCaptor.firstValue.getOrNull()
            assertEquals("http://127.0.0.1:8008/apps", device?.dialURL.toString())
            assertEquals("LaCléTV-32F7", device?.friendlyName)
            assertEquals("Innopia", device?.manufacturer)
            assertEquals("cléTV", device?.modelName)
            assertEquals("b042f955-9ae7-44a8-ba6c-0009743932f7", device?.id)
        }

        @Test
        fun getDeviceWithSuccessfulResponseAndAlternateApplicationURLHeaderSucceeds() {
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
                    .setHeader("Application-URL", "http://127.0.0.1:8008/apps") // Alternate application URL header
                    .setBody(response)
            )
            val callback = mock<(Result<UpnpDevice>) -> Unit>()
            val synchronizedCallback = SynchronizedFunction1(callback)

            // When
            upnpClient.getDevice(server.url("/").toString(), synchronizedCallback)

            // Then
            synchronizedCallback.await(5, TimeUnit.SECONDS)
            val resultCaptor = argumentCaptor<Result<UpnpDevice>>()
            verify(callback, times(1)).invoke(resultCaptor.capture())
            val device = resultCaptor.firstValue.getOrNull()
            assertEquals("http://127.0.0.1:8008/apps", device?.dialURL.toString())
            assertEquals("LaCléTV-32F7", device?.friendlyName)
            assertEquals("Innopia", device?.manufacturer)
            assertEquals("cléTV", device?.modelName)
            assertEquals("b042f955-9ae7-44a8-ba6c-0009743932f7", device?.id)
        }

        @Test
        fun getDeviceWithUnsuccessfulResponseFails() {
            // Given
            server.enqueue(MockResponse().setResponseCode(404)) // Unsuccessful response
            val callback = mock<(Result<UpnpDevice>) -> Unit>()
            val synchronizedCallback = SynchronizedFunction1(callback)

            // When
            upnpClient.getDevice(server.url("/").toString(), synchronizedCallback)

            // Then
            synchronizedCallback.await(5, TimeUnit.SECONDS)
            val resultCaptor = argumentCaptor<Result<UpnpDevice>>()
            verify(callback, times(1)).invoke(resultCaptor.capture())
            assertNull(resultCaptor.firstValue.getOrNull())
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
            val callback = mock<(Result<UpnpDevice>) -> Unit>()
            val synchronizedCallback = SynchronizedFunction1(callback)

            // When
            upnpClient.getDevice(":(", synchronizedCallback) // Malformed location

            // Then
            synchronizedCallback.await(5, TimeUnit.SECONDS)
            val resultCaptor = argumentCaptor<Result<UpnpDevice>>()
            verify(callback, times(1)).invoke(resultCaptor.capture())
            assertNull(resultCaptor.firstValue.getOrNull())
        }

        @Test
        fun getDeviceWithMissingApplicationURLHeaderFails() {
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
            val callback = mock<(Result<UpnpDevice>) -> Unit>()
            val synchronizedCallback = SynchronizedFunction1(callback)

            // When
            upnpClient.getDevice(server.url("/").toString(), synchronizedCallback)

            // Then
            synchronizedCallback.await(5, TimeUnit.SECONDS)
            val resultCaptor = argumentCaptor<Result<UpnpDevice>>()
            verify(callback, times(1)).invoke(resultCaptor.capture())
            assertNull(resultCaptor.firstValue.getOrNull())
        }

        @Test
        fun getDeviceWithTimeoutFails() {
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
            server.enqueue(
                MockResponse()
                    .setHeader("Application-DIAL-URL", "http://127.0.0.1:8008/apps")
                    .setBody(response)
                    .setBodyDelay(1, TimeUnit.DAYS) // Read response body timeout
            )
            val callback = mock<(Result<UpnpDevice>) -> Unit>()
            val synchronizedCallback = SynchronizedFunction1(callback, 2)

            // When
            upnpClient.getDevice(server.url("/").toString(), synchronizedCallback)
            upnpClient.getDevice(server.url("/").toString(), synchronizedCallback)

            // Then
            synchronizedCallback.await(2, TimeUnit.MINUTES)
            val resultCaptor = argumentCaptor<Result<UpnpDevice>>()
            verify(callback, times(2)).invoke(resultCaptor.capture())
            assertNull(resultCaptor.firstValue.getOrNull())
            assertNull(resultCaptor.secondValue.getOrNull())
        }

        @Test
        fun extractUuidFromUSNSucceeds() {
            // Given
            val usn = "uuid:device-UUID"

            // When
            val uuid = UpnpClient.extractUuid(usn)

            // Then
            assertEquals("device-UUID", uuid)
        }

        @Test
        fun extractUuidFromRootDeviceUSNSucceeds() {
            // Given
            val usn = "uuid:device-UUID::upnp:rootdevice"

            // When
            val uuid = UpnpClient.extractUuid(usn)

            // Then
            assertEquals("device-UUID", uuid)
        }

        @Test
        fun extractUuidFromDeviceTypeUSNSucceeds() {
            // Given
            val usn = "uuid:device-UUID::urn:domain-name:device:deviceType:ver"

            // When
            val uuid = UpnpClient.extractUuid(usn)

            // Then
            assertEquals("device-UUID", uuid)
        }

        @Test
        fun extractUuidFromServiceTypeUSNSucceeds() {
            // Given
            val usn = "uuid:device-UUID::urn:domain-name:service:serviceType:ver"

            // When
            val uuid = UpnpClient.extractUuid(usn)

            // Then
            assertEquals("device-UUID", uuid)
        }

        @Test
        fun extractUuidFromMalformedUSNFails() {
            // Given
            val usn = "id:device-UUID"

            // When
            val uuid = UpnpClient.extractUuid(usn)

            // Then
            assertNull(uuid)
        }
    }

    @RunWith(Parameterized::class)
    class WithParameterizedMissingElements(private val element: String) : HttpClientTest() {

        /** The UpnpClient to test. */
        private val upnpClient = UpnpClient()

        companion object {

            @JvmStatic
            @Parameterized.Parameters
            fun data() = listOf("friendlyName", "manufacturer", "modelName", "UDN")
        }

        @Test
        fun getDeviceWithParameterizedMissingElementFails() {
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
            """.trimIndent().removeXMLElement(element) // Missing element

            server.enqueue(
                MockResponse()
                    .setHeader("Application-DIAL-URL", "http://127.0.0.1:8008/apps")
                    .setBody(response)
            )
            val callback = mock<(Result<UpnpDevice>) -> Unit>()
            val synchronizedCallback = SynchronizedFunction1(callback)

            // When
            upnpClient.getDevice(server.url("/").toString(), synchronizedCallback)

            // Then
            synchronizedCallback.await(5, TimeUnit.SECONDS)
            val resultCaptor = argumentCaptor<Result<UpnpDevice>>()
            verify(callback, times(1)).invoke(resultCaptor.capture())
            assertNull(resultCaptor.firstValue.getOrNull())
        }
    }
}
