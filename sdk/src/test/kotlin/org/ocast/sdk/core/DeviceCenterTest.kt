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
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import java.net.URL
import org.hamcrest.CoreMatchers.instanceOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import org.ocast.sdk.common.stubDeviceDescriptionResponses
import org.ocast.sdk.common.stubMSearchResponses
import org.ocast.sdk.core.models.Media
import org.ocast.sdk.core.models.MediaMetadata
import org.ocast.sdk.core.models.MediaPlaybackStatus
import org.ocast.sdk.core.models.UpdateStatus
import org.ocast.sdk.discovery.DeviceDiscovery
import org.ocast.sdk.discovery.UDPSocket
import org.ocast.sdk.discovery.UpnpClient
import org.ocast.sdk.discovery.models.UpnpDevice

/**
 * Unit tests for the [DeviceCenter] class.
 */
internal class DeviceCenterTest {

    /** The UDP socket used for the discovery. */
    private val socket = spy<UDPSocket>()

    /** The UPnP client used for the discovery. */
    private val upnpClient = mock<UpnpClient>()

    /** The discovery. */
    private val deviceDiscovery = DeviceDiscovery(socket, upnpClient)

    /** The device center. */
    private val deviceCenter = DeviceCenter(deviceDiscovery)

    /** The device listeners. */
    private val deviceListeners = listOf<DeviceListener>(mock(), mock(), mock())

    /** The event listeners. */
    private val eventListeners = listOf<EventListener>(mock(), mock(), mock())

    @Before
    fun setUp() {
        deviceCenter.discoveryInterval = DeviceCenter.MINIMUM_DISCOVERY_INTERVAL
        deviceCenter.addDeviceListener(deviceListeners[0])
        deviceCenter.addDeviceListener(deviceListeners[1])
        deviceCenter.addDeviceListener(deviceListeners[1]) // Test that listener methods are called only once
        deviceCenter.addDeviceListener(deviceListeners[2])
        deviceCenter.removeDeviceListener(deviceListeners[2]) // Test that listener methods are not called once it is removed
        deviceCenter.addEventListener(eventListeners[0])
        deviceCenter.addEventListener(eventListeners[1])
        deviceCenter.addEventListener(eventListeners[1]) // Test that listener methods are called only once
        deviceCenter.addEventListener(eventListeners[2])
        deviceCenter.removeEventListener(eventListeners[2]) // Test that listener methods are not called once it is removed
    }

    @After
    fun tearDown() {
        deviceCenter.removeDeviceListener(deviceListeners[0])
        deviceCenter.removeDeviceListener(deviceListeners[1])
        deviceCenter.removeEventListener(eventListeners[0])
        deviceCenter.removeEventListener(eventListeners[1])
        deviceCenter.stopDiscovery()
    }

    //region Device listeners

    @Test
    fun addDevicesCallsDeviceListenersOnDevicesAdded() {
        // Given
        deviceCenter.registerDevice(ReferenceDevice::class.java)
        val firstUpnpDevice = UpnpDevice("UDN1", URL("http://foo1"), "Name1", "Orange SA", "Model1")
        val secondUpnpDevice = UpnpDevice("UDN2", URL("http://foo2"), "Name2", "Other manufacturer", "Model2")
        stubDeviceDiscovery(listOf(firstUpnpDevice to 200L, secondUpnpDevice to 100L))

        // When
        deviceCenter.resumeDiscovery()

        // Then
        Thread.sleep(500)
        val devicesCaptor = argumentCaptor<List<Device>>()
        verify(deviceListeners[0], times(1)).onDevicesAdded(devicesCaptor.capture())
        verify(deviceListeners[1], times(1)).onDevicesAdded(devicesCaptor.capture())
        verify(deviceListeners[2], never()).onDevicesAdded(any())
        assertEquals(1, devicesCaptor.firstValue.size)
        val device = devicesCaptor.firstValue.firstOrNull()
        assertThat(device, instanceOf(ReferenceDevice::class.java))
        assertEquals("UDN1", device?.upnpID)
        assertEquals(URL("http://foo1"), device?.dialURL)
        assertEquals("Name1", device?.friendlyName)
        assertEquals("Model1", device?.modelName)
        assertEquals("Orange SA", device?.manufacturer)
        assertEquals(Device.State.DISCONNECTED, device?.state)
        assertEquals(devicesCaptor.firstValue, devicesCaptor.secondValue)
        assertEquals(devicesCaptor.firstValue, deviceCenter.devices)
    }

    @Test
    fun removeDevicesCallsDeviceListenersOnDevicesRemoved() {
        // Given
        awaitDeviceAdded()
        val addedDevice = deviceCenter.devices.first()

        // When
        Thread.sleep(9000) // Wait at least next SSDP request (5 sec) + MX (3 sec) + network round-trip time (1 sec)

        // Then
        val devicesCaptor = argumentCaptor<List<Device>>()
        verify(deviceListeners[0], times(1)).onDevicesRemoved(devicesCaptor.capture())
        verify(deviceListeners[1], times(1)).onDevicesRemoved(devicesCaptor.capture())
        verify(deviceListeners[2], never()).onDevicesRemoved(any())
        assertEquals(listOf(addedDevice), devicesCaptor.firstValue)
        assertEquals(listOf(addedDevice), devicesCaptor.secondValue)
        assertEquals(0, deviceCenter.devices.size)
    }

    @Test
    fun changeDevicesCallsDeviceListenersOnDevicesChanged() {
        // Given
        awaitDeviceAdded()
        val addedDevice = deviceCenter.devices.first()
        val changedUpnpDevice = with(addedDevice) { UpnpDevice(upnpID, dialURL, "Changed name", manufacturer, modelName) }

        // When
        stubDeviceDiscovery(listOf(changedUpnpDevice to 100L))

        // Then
        Thread.sleep(5000)
        val devicesCaptor = argumentCaptor<List<Device>>()
        verify(deviceListeners[0], times(1)).onDevicesChanged(devicesCaptor.capture())
        verify(deviceListeners[1], times(1)).onDevicesChanged(devicesCaptor.capture())
        verify(deviceListeners[2], never()).onDevicesRemoved(any())
        assertEquals(1, devicesCaptor.firstValue.size)
        val changedDevice = devicesCaptor.firstValue.firstOrNull()
        assertEquals(addedDevice.upnpID, changedDevice?.upnpID)
        assertEquals(addedDevice.dialURL, changedDevice?.dialURL)
        assertEquals("Changed name", changedDevice?.friendlyName)
        assertEquals(addedDevice.modelName, changedDevice?.modelName)
        assertEquals(addedDevice.manufacturer, changedDevice?.manufacturer)
        assertEquals(devicesCaptor.firstValue, devicesCaptor.secondValue)
        assertEquals(devicesCaptor.firstValue, deviceCenter.devices)
    }

    @Test
    fun stopDiscoveryCallsDeviceListenersOnDeviceRemoved() {
        // Given
        awaitDeviceAdded()
        val device = deviceCenter.devices.first()

        // When
        deviceCenter.stopDiscovery()

        // Then
        verify(deviceListeners[0], times(1)).onDevicesRemoved(eq(listOf(device)))
        verify(deviceListeners[1], times(1)).onDevicesRemoved(eq(listOf(device)))
        verify(deviceListeners[2], never()).onDevicesRemoved(anyOrNull())
    }

    @Test
    fun stopDiscoveryCallsDeviceListenersOnDiscoveryStoppedWithoutError() {
        // Given
        deviceDiscovery.resume()

        // When
        deviceCenter.stopDiscovery()

        // Then
        verify(deviceListeners[0], times(1)).onDiscoveryStopped(isNull())
        verify(deviceListeners[1], times(1)).onDiscoveryStopped(isNull())
        verify(deviceListeners[2], never()).onDiscoveryStopped(anyOrNull())
    }

    @Test
    fun discoverySocketFailureCallsDeviceListenersOnDiscoveryStoppedWithError() {
        // Given
        val throwable = Throwable()

        // When
        socket.listener?.onSocketClosed(socket, throwable)

        // Then
        verify(deviceListeners[0], times(1)).onDiscoveryStopped(eq(throwable))
        verify(deviceListeners[1], times(1)).onDiscoveryStopped(eq(throwable))
        verify(deviceListeners[2], never()).onDiscoveryStopped(anyOrNull())
    }

    @Test
    fun deviceWebSocketFailureCallsDeviceListenersOnDeviceDisconnected() {
        // Given
        awaitDeviceAdded()
        val device = deviceCenter.devices.first()
        val throwable = Throwable()

        // When
        device.deviceListener?.onDeviceDisconnected(device, throwable)

        // Then
        verify(deviceListeners[0], times(1)).onDeviceDisconnected(eq(device), eq(throwable))
        verify(deviceListeners[1], times(1)).onDeviceDisconnected(eq(device), eq(throwable))
        verify(deviceListeners[2], never()).onDeviceDisconnected(any(), anyOrNull())
    }

    //endregion

    //region Event listeners

    @Test
    fun receiveMediaPlaybackStatusEventCallsEventListenersOnMediaPlaybackStatus() {
        // Given
        awaitDeviceAdded()
        val device = deviceCenter.devices.first()
        val playbackStatus = MediaPlaybackStatus(
            303.0,
            4673.7,
            MediaPlaybackStatus.State.PAUSED,
            0.3,
            true
        )

        // When
        device.eventListener?.onMediaPlaybackStatus(device, playbackStatus)

        // Then
        val playbackStatusCaptor = argumentCaptor<MediaPlaybackStatus>()
        verify(eventListeners[0], times(1)).onMediaPlaybackStatus(eq(device), playbackStatusCaptor.capture())
        verify(eventListeners[1], times(1)).onMediaPlaybackStatus(eq(device), playbackStatusCaptor.capture())
        verify(eventListeners[2], never()).onMediaPlaybackStatus(any(), any())
        assertEquals(playbackStatus, playbackStatusCaptor.firstValue)
        assertEquals(playbackStatus, playbackStatusCaptor.secondValue)
    }

    @Test
    fun receiveMediaMetadataChangedEventCallsEventListenersOnMediaMetadataChanged() {
        // Given
        awaitDeviceAdded()
        val device = deviceCenter.devices.first()
        val metadata = MediaMetadata(
            "La cité de la peur",
            "Un film de les nuls",
            "http://localhost/logo",
            Media.Type.VIDEO,
            listOf(),
            listOf(MediaMetadata.Track("de", "Audio DE", true, "id123")),
            listOf()
        )

        // When
        device.eventListener?.onMediaMetadataChanged(device, metadata)

        // Then
        val metadataCaptor = argumentCaptor<MediaMetadata>()
        verify(eventListeners[0], times(1)).onMediaMetadataChanged(eq(device), metadataCaptor.capture())
        verify(eventListeners[1], times(1)).onMediaMetadataChanged(eq(device), metadataCaptor.capture())
        verify(eventListeners[2], never()).onMediaMetadataChanged(any(), any())
        assertEquals(metadata, metadataCaptor.firstValue)
        assertEquals(metadata, metadataCaptor.secondValue)
    }

    @Test
    fun receiveUpdateStatusEventCallsEventListenersOnUpdateStatus() {
        // Given
        awaitDeviceAdded()
        val device = deviceCenter.devices.first()
        val updateStatus = UpdateStatus(
            UpdateStatus.State.DOWNLOADING,
            "1.0",
            50
        )

        // When
        device.eventListener?.onUpdateStatus(device, updateStatus)

        // Then
        val updateStatusCaptor = argumentCaptor<UpdateStatus>()
        verify(eventListeners[0], times(1)).onUpdateStatus(eq(device), updateStatusCaptor.capture())
        verify(eventListeners[1], times(1)).onUpdateStatus(eq(device), updateStatusCaptor.capture())
        verify(eventListeners[2], never()).onUpdateStatus(any(), any())
        assertEquals(updateStatus, updateStatusCaptor.firstValue)
        assertEquals(updateStatus, updateStatusCaptor.secondValue)
    }

    @Test
    fun receiveCustomEventCallsEventListenersOnCustomEvent() {
        // Given
        awaitDeviceAdded()
        val device = deviceCenter.devices.first()
        val name = "customEvent"
        val params = "{\"paramName\":\"paramValue\"}"

        // When
        device.eventListener?.onCustomEvent(device, name, params)

        // Then
        verify(eventListeners[0], times(1)).onCustomEvent(eq(device), eq(name), eq(params))
        verify(eventListeners[1], times(1)).onCustomEvent(eq(device), eq(name), eq(params))
        verify(eventListeners[2], never()).onCustomEvent(any(), any(), any())
    }

    //endregion

    //region Private methods

    /**
     * Waits for a device to be added by the discovery.
     */
    private fun awaitDeviceAdded() {
        deviceCenter.registerDevice(ReferenceDevice::class.java)
        val upnpDevice = UpnpDevice("UDN", URL("http://foo"), "Name", "Orange SA", "Model")
        stubDeviceDiscovery(listOf(upnpDevice to 100L))
        deviceCenter.resumeDiscovery()
        Thread.sleep(200)
        assertEquals(1, deviceCenter.devices.size)
        val device = deviceCenter.devices.first()
        assertThat(device, instanceOf(ReferenceDevice::class.java))
        assertEquals("UDN", device.upnpID)
        assertEquals(URL("http://foo"), device.dialURL)
        assertEquals("Name", device.friendlyName)
        assertEquals("Model", device.modelName)
        assertEquals("Orange SA", device.manufacturer)
    }

    /**
     * Stubs the discovery of devices.
     *
     * @param devices A list of UPnP devices and the delay after which they will be added by the discovery.
     */
    private fun stubDeviceDiscovery(devices: List<Pair<UpnpDevice, Long>>) {
        val locations = devices.mapIndexed { index, _ ->
            "http://10.0.0.$index:56790/device-desc.xml"
        }
        val mSearchResponseStrings = locations.mapIndexed { index, location ->
            "HTTP/1.1 200 OK\r\n" +
                "LOCATION: $location\r\n" +
                "CACHE-CONTROL: max-age=1800\r\n" +
                "EXT:\r\n" +
                "BOOTID.UPNP.ORG: 1\r\n" +
                "SERVER: Linux/4.9 UPnP/1.1 quick_ssdp/1.1\r\n" +
                "ST: urn:cast-ocast-org:service:cast:1\r\n" +
                "USN: uuid:b042f955-9ae7-44a8-ba6c-0009743932f7-$index\r\n" +
                "WAKEUP: MAC=00:09:74:39:32:f7;Timeout=10"
        }
        val mSearchResponses = mSearchResponseStrings.mapIndexed { index, string ->
            string to devices[index].second
        }
        socket.stubMSearchResponses(mSearchResponses)
        val devicesByLocation = locations.mapIndexed { index, location ->
            location to devices[index].first
        }
        upnpClient.stubDeviceDescriptionResponses(hashMapOf(*devicesByLocation.toTypedArray()))
    }

    //endregion
}
