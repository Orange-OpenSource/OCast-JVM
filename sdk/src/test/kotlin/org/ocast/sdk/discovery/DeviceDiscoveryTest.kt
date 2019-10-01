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

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.net.URL
import java.util.Timer
import kotlin.concurrent.schedule
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import org.ocast.sdk.common.stubDeviceDescriptionResponses
import org.ocast.sdk.common.stubMSearchResponses
import org.ocast.sdk.discovery.models.UpnpDevice

/**
 * Unit tests for the [DeviceDiscovery] class.
 */
internal class DeviceDiscoveryTest {

    /** The mocked socket. */
    private val socket = spy<UDPSocket>()

    /** The mocked UPnP client. */
    private val upnpClient = mock<UpnpClient>()

    /** The mocked discovery listener. */
    private val listener = mock<DeviceDiscovery.Listener>()

    /** The discovery object. */
    private val discovery = DeviceDiscovery(socket, upnpClient)

    @Before
    fun setUp() {
        discovery.listener = listener
        discovery.searchTargets = setOf("urn:cast-ocast-org:service:cast:1")
        discovery.interval = 5
    }

    @After
    fun tearDown() {
        discovery.stop()
    }

    //region Resume

    @Test
    fun receiveMSearchResponseCallsListenerOnDevicesAdded() {
        // Given
        val firstMSearchResponseString = "HTTP/1.1 200 OK\r\n" +
            "LOCATION: http://10.0.0.28:56790/device-desc.xml\r\n" +
            "CACHE-CONTROL: max-age=1800\r\n" +
            "EXT:\r\n" +
            "BOOTID.UPNP.ORG: 1\r\n" +
            "SERVER: Linux/4.9 UPnP/1.1 quick_ssdp/1.1\r\n" +
            "ST: urn:cast-ocast-org:service:cast:1\r\n" +
            "USN: uuid:b042f955-9ae7-44a8-ba6c-0009743932f7\r\n" +
            "WAKEUP: MAC=00:09:74:39:32:f7;Timeout=10"
        val secondMSearchResponseString = "HTTP/1.1 200 OK\r\n" +
            "LOCATION: http://10.0.0.29:56790/device-desc.xml\r\n" +
            "CACHE-CONTROL: max-age=1800\r\n" +
            "EXT:\r\n" +
            "BOOTID.UPNP.ORG: 1\r\n" +
            "SERVER: Linux/4.9 UPnP/1.1 quick_ssdp/1.1\r\n" +
            "ST: urn:cast-ocast-org:service:cast:1\r\n" +
            "USN: uuid:c1530a66-abf8-55b9-cb7d-111a854a4308\r\n" +
            "WAKEUP: MAC=00:09:74:39:32:f7;Timeout=10"
        socket.stubMSearchResponses(listOf(firstMSearchResponseString to 200L, secondMSearchResponseString to 100L))
        val firstDevice = UpnpDevice("UDN1", URL("http://foo1"), "Name1", "Manufacturer1", "Model1")
        val secondDevice = UpnpDevice("UDN2", URL("http://foo2"), "Name2", "Manufacturer2", "Model2")
        upnpClient.stubDeviceDescriptionResponses(
            hashMapOf(
                "http://10.0.0.28:56790/device-desc.xml" to firstDevice,
                "http://10.0.0.29:56790/device-desc.xml" to secondDevice
            )
        )

        // When
        discovery.resume()

        // Then
        Thread.sleep(1000)
        assertThat(discovery.devices, containsInAnyOrder(firstDevice, secondDevice))
        val addedDevicesCaptor = argumentCaptor<List<UpnpDevice>>()
        verify(listener, times(2)).onDevicesAdded(addedDevicesCaptor.capture())
        assertEquals(listOf(secondDevice), addedDevicesCaptor.firstValue)
        assertEquals(listOf(firstDevice), addedDevicesCaptor.secondValue)
        verify(listener, never()).onDevicesChanged(any())
        verify(listener, never()).onDevicesRemoved(any())
        verify(listener, never()).onDiscoveryStopped(anyOrNull())
    }

    @Test
    fun mSearchResponseTimeoutCallsListenerOnDevicesRemoved() {
        // Given
        val firstMSearchResponseString = "HTTP/1.1 200 OK\r\n" +
            "LOCATION: http://10.0.0.28:56790/device-desc.xml\r\n" +
            "CACHE-CONTROL: max-age=1800\r\n" +
            "EXT:\r\n" +
            "BOOTID.UPNP.ORG: 1\r\n" +
            "SERVER: Linux/4.9 UPnP/1.1 quick_ssdp/1.1\r\n" +
            "ST: urn:cast-ocast-org:service:cast:1\r\n" +
            "USN: uuid:b042f955-9ae7-44a8-ba6c-0009743932f7\r\n" +
            "WAKEUP: MAC=00:09:74:39:32:f7;Timeout=10"
        val secondMSearchResponseString = "HTTP/1.1 200 OK\r\n" +
            "LOCATION: http://10.0.0.29:56790/device-desc.xml\r\n" +
            "CACHE-CONTROL: max-age=1800\r\n" +
            "EXT:\r\n" +
            "BOOTID.UPNP.ORG: 1\r\n" +
            "SERVER: Linux/4.9 UPnP/1.1 quick_ssdp/1.1\r\n" +
            "ST: urn:cast-ocast-org:service:cast:1\r\n" +
            "USN: uuid:c1530a66-abf8-55b9-cb7d-111a854a4308\r\n" +
            "WAKEUP: MAC=00:09:74:39:32:f7;Timeout=10"
        socket.stubMSearchResponses(
            listOf(firstMSearchResponseString to 100L, secondMSearchResponseString to 200L),
            // First device does not respond to second M-SEARCH request
            listOf(secondMSearchResponseString to 300L)
        )
        val firstDevice = UpnpDevice("UDN1", URL("http://foo1"), "Name1", "Manufacturer1", "Model1")
        val secondDevice = UpnpDevice("UDN2", URL("http://foo2"), "Name2", "Manufacturer2", "Model2")
        upnpClient.stubDeviceDescriptionResponses(
            hashMapOf(
                "http://10.0.0.28:56790/device-desc.xml" to firstDevice,
                "http://10.0.0.29:56790/device-desc.xml" to secondDevice
            )
        )

        // When
        discovery.resume()

        // Then
        Thread.sleep(1000)
        assertThat(discovery.devices, containsInAnyOrder(firstDevice, secondDevice))
        Thread.sleep(9000)
        assertEquals(listOf(secondDevice), discovery.devices)
        val addedDevicesCaptor = argumentCaptor<List<UpnpDevice>>()
        verify(listener, times(2)).onDevicesAdded(addedDevicesCaptor.capture())
        assertEquals(listOf(firstDevice), addedDevicesCaptor.firstValue)
        assertEquals(listOf(secondDevice), addedDevicesCaptor.secondValue)
        val removedDevicesCaptor = argumentCaptor<List<UpnpDevice>>()
        verify(listener, times(1)).onDevicesRemoved(removedDevicesCaptor.capture())
        assertEquals(listOf(firstDevice), removedDevicesCaptor.firstValue)
        verify(listener, never()).onDevicesChanged(any())
        verify(listener, never()).onDiscoveryStopped(anyOrNull())
    }

    @Test
    fun receiveChangedDeviceDescriptionCallsListenerOnDevicesChanged() {
        // Given
        val oldMSearchResponseString = "HTTP/1.1 200 OK\r\n" +
                "LOCATION: http://10.0.0.28:56790/old-device-desc.xml\r\n" +
                "CACHE-CONTROL: max-age=1800\r\n" +
                "EXT:\r\n" +
                "BOOTID.UPNP.ORG: 1\r\n" +
                "SERVER: Linux/4.9 UPnP/1.1 quick_ssdp/1.1\r\n" +
                "ST: urn:cast-ocast-org:service:cast:1\r\n" +
                "USN: uuid:b042f955-9ae7-44a8-ba6c-0009743932f7\r\n" +
                "WAKEUP: MAC=00:09:74:39:32:f7;Timeout=10"

        val newMSearchResponseString = "HTTP/1.1 200 OK\r\n" +
                "LOCATION: http://10.0.0.28:56790/new-device-desc.xml\r\n" +
                "CACHE-CONTROL: max-age=1800\r\n" +
                "EXT:\r\n" +
                "BOOTID.UPNP.ORG: 1\r\n" +
                "SERVER: Linux/4.9 UPnP/1.1 quick_ssdp/1.1\r\n" +
                "ST: urn:cast-ocast-org:service:cast:1\r\n" +
                "USN: uuid:b042f955-9ae7-44a8-ba6c-0009743932f7\r\n" +
                "WAKEUP: MAC=00:09:74:39:32:f7;Timeout=10"

        socket.stubMSearchResponses(listOf(oldMSearchResponseString to 200L, newMSearchResponseString to 400L))
        val oldDevice = UpnpDevice("UDN", URL("http://foo"), "OldName", "Manufacturer", "Model")
        upnpClient.stubDeviceDescriptionResponses(hashMapOf("http://10.0.0.28:56790/old-device-desc.xml" to oldDevice))
        val newDevice = UpnpDevice("UDN", URL("http://foo"), "NewName", "Manufacturer", "Model")
        upnpClient.stubDeviceDescriptionResponses(hashMapOf("http://10.0.0.28:56790/new-device-desc.xml" to newDevice))

        // When
        discovery.resume()

        // Then
        Thread.sleep(300)
        assertThat(discovery.devices, containsInAnyOrder(oldDevice))
        Thread.sleep(700)
        assertThat(discovery.devices, containsInAnyOrder(newDevice))

        verify(listener, times(1)).onDevicesAdded(any())
        verify(listener, never()).onDevicesRemoved(any())
        val changedDevicesCaptor = argumentCaptor<List<UpnpDevice>>()
        verify(listener, times(1)).onDevicesChanged(changedDevicesCaptor.capture())
        assertEquals(listOf(newDevice), changedDevicesCaptor.firstValue)
        verify(listener, never()).onDiscoveryStopped(anyOrNull())
    }

    @Test
    fun resumeDiscoveryOnlySucceedsIfPausedOrStopped() {
        // Given

        // When
        val firstResumeSuccess = discovery.resume()
        discovery.stop()
        val secondResumeSuccess = discovery.resume()
        val thirdResumeSuccess = discovery.resume()

        // Then
        assert(firstResumeSuccess)
        assert(secondResumeSuccess)
        assert(!thirdResumeSuccess)
    }

    //endregion

    //region Pause

    @Test
    fun pauseDiscoveryDoesNotClearDevices() {
        // Given
        val firstMSearchResponseString = "HTTP/1.1 200 OK\r\n" +
            "LOCATION: http://10.0.0.28:56790/device-desc.xml\r\n" +
            "CACHE-CONTROL: max-age=1800\r\n" +
            "EXT:\r\n" +
            "BOOTID.UPNP.ORG: 1\r\n" +
            "SERVER: Linux/4.9 UPnP/1.1 quick_ssdp/1.1\r\n" +
            "ST: urn:cast-ocast-org:service:cast:1\r\n" +
            "USN: uuid:b042f955-9ae7-44a8-ba6c-0009743932f7\r\n" +
            "WAKEUP: MAC=00:09:74:39:32:f7;Timeout=10"
        val secondMSearchResponseString = "HTTP/1.1 200 OK\r\n" +
            "LOCATION: http://10.0.0.29:56790/device-desc.xml\r\n" +
            "CACHE-CONTROL: max-age=1800\r\n" +
            "EXT:\r\n" +
            "BOOTID.UPNP.ORG: 1\r\n" +
            "SERVER: Linux/4.9 UPnP/1.1 quick_ssdp/1.1\r\n" +
            "ST: urn:cast-ocast-org:service:cast:1\r\n" +
            "USN: uuid:c1530a66-abf8-55b9-cb7d-111a854a4308\r\n" +
            "WAKEUP: MAC=00:09:74:39:32:f7;Timeout=10"
        socket.stubMSearchResponses(
            listOf(firstMSearchResponseString to 100L, secondMSearchResponseString to 200L),
            // This line is for the second call to resume()
            listOf(firstMSearchResponseString to 100L, secondMSearchResponseString to 200L)
        )
        val firstDevice = UpnpDevice("UDN1", URL("http://foo1"), "Name1", "Manufacturer1", "Model1")
        val secondDevice = UpnpDevice("UDN2", URL("http://foo2"), "Name2", "Manufacturer2", "Model2")
        upnpClient.stubDeviceDescriptionResponses(
            hashMapOf(
                "http://10.0.0.28:56790/device-desc.xml" to firstDevice,
                "http://10.0.0.29:56790/device-desc.xml" to secondDevice
            )
        )

        // When
        discovery.resume()
        Thread.sleep(1000)
        discovery.pause()
        discovery.resume()

        // Then
        Thread.sleep(1000)
        assertThat(discovery.devices, containsInAnyOrder(firstDevice, secondDevice))
        val addedDevicesCaptor = argumentCaptor<List<UpnpDevice>>()
        verify(listener, times(2)).onDevicesAdded(addedDevicesCaptor.capture())
        assertEquals(listOf(firstDevice), addedDevicesCaptor.firstValue)
        assertEquals(listOf(secondDevice), addedDevicesCaptor.secondValue)
        verify(listener, never()).onDevicesChanged(any())
        verify(listener, never()).onDevicesRemoved(any())
        verify(listener, never()).onDiscoveryStopped(any())
    }

    @Test
    fun pauseDiscoveryImmediatelyAfterResumeDoesNotAddAnyDevice() {
        // Given
        val mSearchResponseString = "HTTP/1.1 200 OK\r\n" +
            "LOCATION: http://10.0.0.28:56790/device-desc.xml\r\n" +
            "CACHE-CONTROL: max-age=1800\r\n" +
            "EXT:\r\n" +
            "BOOTID.UPNP.ORG: 1\r\n" +
            "SERVER: Linux/4.9 UPnP/1.1 quick_ssdp/1.1\r\n" +
            "ST: urn:cast-ocast-org:service:cast:1\r\n" +
            "USN: uuid:b042f955-9ae7-44a8-ba6c-0009743932f7\r\n" +
            "WAKEUP: MAC=00:09:74:39:32:f7;Timeout=10"
        socket.stubMSearchResponses(listOf(mSearchResponseString to 100L))
        val device = UpnpDevice("UDN", URL("http://foo"), "Name", "Manufacturer", "Model")
        upnpClient.stubDeviceDescriptionResponses(hashMapOf("http://10.0.0.28:56790/device-desc.xml" to device))

        // When
        discovery.resume()
        discovery.pause()

        // Then
        Thread.sleep(1000)
        assertEquals(emptyList<UpnpDevice>(), discovery.devices)
        verify(listener, never()).onDevicesChanged(any())
        verify(listener, never()).onDevicesAdded(any())
        verify(listener, never()).onDevicesRemoved(any())
        verify(listener, never()).onDiscoveryStopped(any())
    }

    @Test
    fun pauseDiscoveryOnlySucceedsIfRunning() {
        // Given

        // When
        val firstPauseSuccess = discovery.pause()
        discovery.resume()
        val secondPauseSuccess = discovery.pause()
        discovery.stop()
        val thirdPauseSuccess = discovery.pause()

        // Then
        assert(!firstPauseSuccess)
        assert(secondPauseSuccess)
        assert(!thirdPauseSuccess)
    }

    //endregion

    //region Stop

    @Test
    fun stopDiscoveryClearsDevices() {
        // Given
        val firstMSearchResponseString = "HTTP/1.1 200 OK\r\n" +
            "LOCATION: http://10.0.0.28:56790/device-desc.xml\r\n" +
            "CACHE-CONTROL: max-age=1800\r\n" +
            "EXT:\r\n" +
            "BOOTID.UPNP.ORG: 1\r\n" +
            "SERVER: Linux/4.9 UPnP/1.1 quick_ssdp/1.1\r\n" +
            "ST: urn:cast-ocast-org:service:cast:1\r\n" +
            "USN: uuid:b042f955-9ae7-44a8-ba6c-0009743932f7\r\n" +
            "WAKEUP: MAC=00:09:74:39:32:f7;Timeout=10"
        val secondMSearchResponseString = "HTTP/1.1 200 OK\r\n" +
            "LOCATION: http://10.0.0.29:56790/device-desc.xml\r\n" +
            "CACHE-CONTROL: max-age=1800\r\n" +
            "EXT:\r\n" +
            "BOOTID.UPNP.ORG: 1\r\n" +
            "SERVER: Linux/4.9 UPnP/1.1 quick_ssdp/1.1\r\n" +
            "ST: urn:cast-ocast-org:service:cast:1\r\n" +
            "USN: uuid:c1530a66-abf8-55b9-cb7d-111a854a4308\r\n" +
            "WAKEUP: MAC=00:09:74:39:32:f7;Timeout=10"
        socket.stubMSearchResponses(listOf(firstMSearchResponseString to 100L, secondMSearchResponseString to 200L))
        val firstDevice = UpnpDevice("UDN1", URL("http://foo1"), "Name1", "Manufacturer1", "Model1")
        val secondDevice = UpnpDevice("UDN2", URL("http://foo2"), "Name2", "Manufacturer2", "Model2")
        upnpClient.stubDeviceDescriptionResponses(
            hashMapOf(
                "http://10.0.0.28:56790/device-desc.xml" to firstDevice,
                "http://10.0.0.29:56790/device-desc.xml" to secondDevice
            )
        )

        // When
        discovery.resume()
        Thread.sleep(1000)
        discovery.stop()

        // Then
        Thread.sleep(1000)
        assertEquals(emptyList<UpnpDevice>(), discovery.devices)
        val addedDevicesCaptor = argumentCaptor<List<UpnpDevice>>()
        verify(listener, times(2)).onDevicesAdded(addedDevicesCaptor.capture())
        assertEquals(listOf(firstDevice), addedDevicesCaptor.firstValue)
        assertEquals(listOf(secondDevice), addedDevicesCaptor.secondValue)
        val removedDevicesCaptor = argumentCaptor<List<UpnpDevice>>()
        verify(listener, times(1)).onDevicesRemoved(removedDevicesCaptor.capture())
        assertThat(removedDevicesCaptor.firstValue, containsInAnyOrder(firstDevice, secondDevice))
        verify(listener, times(1)).onDiscoveryStopped(isNull())
        verify(listener, never()).onDevicesChanged(any())
    }

    @Test
    fun stopDiscoveryAfterPauseClearsDevices() {
        // Given
        val firstMSearchResponseString = "HTTP/1.1 200 OK\r\n" +
            "LOCATION: http://10.0.0.28:56790/device-desc.xml\r\n" +
            "CACHE-CONTROL: max-age=1800\r\n" +
            "EXT:\r\n" +
            "BOOTID.UPNP.ORG: 1\r\n" +
            "SERVER: Linux/4.9 UPnP/1.1 quick_ssdp/1.1\r\n" +
            "ST: urn:cast-ocast-org:service:cast:1\r\n" +
            "USN: uuid:b042f955-9ae7-44a8-ba6c-0009743932f7\r\n" +
            "WAKEUP: MAC=00:09:74:39:32:f7;Timeout=10"
        val secondMSearchResponseString = "HTTP/1.1 200 OK\r\n" +
            "LOCATION: http://10.0.0.29:56790/device-desc.xml\r\n" +
            "CACHE-CONTROL: max-age=1800\r\n" +
            "EXT:\r\n" +
            "BOOTID.UPNP.ORG: 1\r\n" +
            "SERVER: Linux/4.9 UPnP/1.1 quick_ssdp/1.1\r\n" +
            "ST: urn:cast-ocast-org:service:cast:1\r\n" +
            "USN: uuid:c1530a66-abf8-55b9-cb7d-111a854a4308\r\n" +
            "WAKEUP: MAC=00:09:74:39:32:f7;Timeout=10"
        socket.stubMSearchResponses(listOf(firstMSearchResponseString to 100L, secondMSearchResponseString to 200L))
        val firstDevice = UpnpDevice("UDN1", URL("http://foo1"), "Name1", "Manufacturer1", "Model1")
        val secondDevice = UpnpDevice("UDN2", URL("http://foo2"), "Name2", "Manufacturer2", "Model2")
        upnpClient.stubDeviceDescriptionResponses(
            hashMapOf(
                "http://10.0.0.28:56790/device-desc.xml" to firstDevice,
                "http://10.0.0.29:56790/device-desc.xml" to secondDevice
            )
        )

        // When
        discovery.resume()
        Thread.sleep(1000)
        discovery.pause()
        discovery.stop()

        // Then
        Thread.sleep(1000)
        assertEquals(emptyList<UpnpDevice>(), discovery.devices)
        val addedDevicesCaptor = argumentCaptor<List<UpnpDevice>>()
        verify(listener, times(2)).onDevicesAdded(addedDevicesCaptor.capture())
        assertEquals(listOf(firstDevice), addedDevicesCaptor.firstValue)
        assertEquals(listOf(secondDevice), addedDevicesCaptor.secondValue)
        val removedDevicesCaptor = argumentCaptor<List<UpnpDevice>>()
        verify(listener, times(1)).onDevicesRemoved(removedDevicesCaptor.capture())
        assertThat(removedDevicesCaptor.firstValue, containsInAnyOrder(firstDevice, secondDevice))
        verify(listener, times(1)).onDiscoveryStopped(isNull())
        verify(listener, never()).onDevicesChanged(any())
    }

    @Test
    fun stopDiscoveryImmediatelyAfterResumeDoesNotAddAnyDevice() {
        // Given
        val mSearchResponseString = "HTTP/1.1 200 OK\r\n" +
            "LOCATION: http://10.0.0.28:56790/device-desc.xml\r\n" +
            "CACHE-CONTROL: max-age=1800\r\n" +
            "EXT:\r\n" +
            "BOOTID.UPNP.ORG: 1\r\n" +
            "SERVER: Linux/4.9 UPnP/1.1 quick_ssdp/1.1\r\n" +
            "ST: urn:cast-ocast-org:service:cast:1\r\n" +
            "USN: uuid:b042f955-9ae7-44a8-ba6c-0009743932f7\r\n" +
            "WAKEUP: MAC=00:09:74:39:32:f7;Timeout=10"
        socket.stubMSearchResponses(listOf(mSearchResponseString to 100L))
        val device = UpnpDevice("UDN", URL("http://foo"), "Name", "Manufacturer", "Model")
        upnpClient.stubDeviceDescriptionResponses(hashMapOf("http://10.0.0.28:56790/device-desc.xml" to device))

        // When
        discovery.resume()
        discovery.stop()

        // Then
        Thread.sleep(1000)
        assertEquals(emptyList<UpnpDevice>(), discovery.devices)
        verify(listener, never()).onDevicesChanged(any())
        verify(listener, never()).onDevicesAdded(any())
        verify(listener, never()).onDevicesRemoved(any())
        verify(listener, times(1)).onDiscoveryStopped(isNull())
    }

    @Test
    fun stopDiscoveryOnlySucceedsIfRunningOrPaused() {
        // Given

        // When
        val firstStopSuccess = discovery.stop()
        discovery.resume()
        val secondStopSuccess = discovery.stop()
        val thirdStopSuccess = discovery.stop()

        // Then
        assert(firstStopSuccess)
        assert(secondStopSuccess)
        assert(!thirdStopSuccess)
        verify(listener, times(2)).onDiscoveryStopped(isNull())
    }

    @Test
    fun stopDiscoveryAfterUnexpectedStopFails() {
        // Given
        val throwable = Throwable()
        doAnswer {
            Timer().schedule(100L) {
                socket.listener?.onSocketClosed(socket, throwable)
            }
        }.doAnswer {
            // Following calls do nothing
        }.whenever(socket).send(any(), any(), any())

        // When
        discovery.resume()
        Thread.sleep(200)
        val success = discovery.stop()

        // Then
        assert(!success)
        verify(listener, times(1)).onDiscoveryStopped(eq(throwable))
    }

    //endregion
}
