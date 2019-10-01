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

import java.util.Collections
import org.ocast.sdk.common.extensions.ifNotNull
import org.ocast.sdk.common.extensions.orElse
import org.ocast.sdk.core.models.MediaMetadata
import org.ocast.sdk.core.models.MediaPlaybackStatus
import org.ocast.sdk.core.models.UpdateStatus
import org.ocast.sdk.core.utils.OCastLog
import org.ocast.sdk.core.wrapper.CallbackWrapper
import org.ocast.sdk.core.wrapper.CallbackWrapperOwner
import org.ocast.sdk.core.wrapper.SimpleCallbackWrapper
import org.ocast.sdk.discovery.DeviceDiscovery
import org.ocast.sdk.discovery.models.UpnpDevice

/**
 * This class is the entry point to discover and use OCast devices.
 *
 * In order to receive discovery events, you first need to register a device class by calling the
 * `registerDevice(@NotNull Class deviceClass)` method. Then add a [DeviceListener] with
 * `addDeviceListener(@NotNull DeviceListener listener)` and start the discovery with `resumeDiscovery()`.
 *
 * @property deviceDiscovery The object which manages the discovery of OCast devices.
 * @constructor Creates an instance of [DeviceCenter] with a device discovery.
 */
open class DeviceCenter internal constructor(private val deviceDiscovery: DeviceDiscovery) : CallbackWrapperOwner {

    /**
     * @constructor Creates an instance of [DeviceCenter].
     */
    constructor() : this(DeviceDiscovery())

    /**
     * The companion object.
     */
    companion object {

        /** The default value for the discovery interval. */
        const val DEFAULT_DISCOVERY_INTERVAL = DeviceDiscovery.DEFAULT_INTERVAL

        /** The minimum value for the discovery interval. */
        const val MINIMUM_DISCOVERY_INTERVAL = DeviceDiscovery.MINIMUM_INTERVAL
    }

    override var callbackWrapper: CallbackWrapper = SimpleCallbackWrapper()
        set(value) {
            field = value
            devices.forEach { it.callbackWrapper = value }
        }

    /** The list of registered OCast event listeners. */
    private val eventListeners = mutableSetOf<EventListener>()

    /** The list of registered device listeners. */
    private val deviceListeners = mutableSetOf<DeviceListener>()

    /** A map of the registered device classes indexed by their manufacturer. */
    private val registeredDevicesByManufacturer = mutableMapOf<String, Class<out Device>>()

    /** The list of detected OCast devices. This list is synchronized to avoid concurrency issues. */
    private val detectedDevices = Collections.synchronizedList(mutableListOf<Device>())

    /** The list of detected OCast devices. */
    val devices: List<Device>
        get() = detectedDevices.toList()

    /**
     * The interval to refresh the devices, in milliseconds.
     *
     * Minimum value is 5000 milliseconds. Setting this property results in sending an SSDP M-SEARCH request immediately if discovery is on-going.
     */
    var discoveryInterval: Long
        get() = deviceDiscovery.interval
        set(value) {
            deviceDiscovery.interval = value
        }

    /**
     * Creates an OCast device from the specified UPnP device and adds it to the list of detected devices.
     *
     * @param device The UPnP device to create the OCast device from.
     * @return The created OCast device.
     */
    private fun createDevice(device: UpnpDevice): Device? {
        return registeredDevicesByManufacturer[device.manufacturer]
            ?.getConstructor(UpnpDevice::class.java)
            ?.newInstance(device)
            ?.apply {
                deviceListener = this@DeviceCenter.deviceListener
                eventListener = this@DeviceCenter.eventListener
                callbackWrapper = this@DeviceCenter.callbackWrapper
                // Custom actions on custom device
                onAddDevice(this)
                detectedDevices.add(this)
            }.orElse {
                OCastLog.error { "Failed to create device ${device.friendlyName} from UPnP device. Please verify that you registered a device class for manufacturer ${device.manufacturer}" }
                null
            }
    }

    /**
     * Removes an OCast device from the list of detected devices.
     *
     * @param device The device to remove.
     */
    private fun removeDevice(device: Device) {
        device.apply {
            deviceListener = null
            eventListener = null
            // Custom actions on custom device
            onRemoveDevice(this)
            detectedDevices.remove(this)
        }
    }

    /**
     * This method is called when an OCast device has been created and before it is added to the list of detected devices.
     *
     * Default implementation does nothing. This method is meant to be overridden by subclasses.
     *
     * @param device The device to add.
     */
    protected open fun onAddDevice(device: Device) {
    }

    /**
     * This method is called before an OCast device is removed from the list of detected devices.
     *
     * Default implementation does nothing. This method is meant to be overridden by subclasses.
     *
     * @param device The device to remove.
     */
    protected open fun onRemoveDevice(device: Device) {
    }

    /**
     * Registers a class of devices to discover.
     *
     * @param deviceClass The class of devices that will be searched during the discovery process.
     */
    fun registerDevice(deviceClass: Class<out Device>) {
        val device = deviceClass.getConstructor(UpnpDevice::class.java).newInstance(UpnpDevice())
        registeredDevicesByManufacturer[device.manufacturer] = deviceClass
        deviceDiscovery.searchTargets += device.searchTarget
        OCastLog.info { "Registered device class for manufacturer ${device.manufacturer} and search target ${device.searchTarget}" }
    }

    /**
     * Adds a listener for the OCast protocol events.
     *
     * @param listener The listener to add.
     */
    fun addEventListener(listener: EventListener) {
        eventListeners.add(listener)
    }

    /**
     * Removes a listener which has been previously added with the `addEventListener(@NotNull EventListener listener)` method.
     *
     * @param listener The listener to remove.
     */
    fun removeEventListener(listener: EventListener) {
        eventListeners.remove(listener)
    }

    /**
     * Adds a listener for the device events.
     *
     * @param listener The listener to add.
     */
    fun addDeviceListener(listener: DeviceListener) {
        deviceListeners.add(listener)
    }

    /**
     * Removes a listener which has been previously added with the `addDeviceListener(@NotNull EventListener listener)` method.
     *
     * @param listener The listener to remove.
     */
    fun removeDeviceListener(listener: DeviceListener) {
        deviceListeners.remove(listener)
    }

    /**
     * Resumes the discovery process.
     *
     * Newly initialized instances of [DeviceCenter] begin in a paused state, so you need to call this method to start the discovery.
     *
     * @return `true` if the discovery was successfully resumed, `false` if there was an issue or if the discovery was already running.
     */
    fun resumeDiscovery(): Boolean {
        return deviceDiscovery.resume()
    }

    /**
     * Stops the discovery process.
     *
     * This clears the list of detected devices if the discovery was not in a paused state.
     *
     * @return `true` if the discovery was successfully stopped, `false` if the discovery was already stopped.
     */
    fun stopDiscovery(): Boolean {
        return deviceDiscovery.stop()
    }

    /**
     * Pauses the discovery process.
     *
     * This does not clear the list of detected devices.
     *
     * @return `true` if the discovery was successfully paused, `false` if the discovery was not running.
     */
    fun pauseDiscovery(): Boolean {
        return deviceDiscovery.pause()
    }

    //region Discovery listener

    /** The discovery listener. */
    private val deviceDiscoveryListener = object : DeviceDiscovery.Listener {

        override fun onDevicesAdded(devices: List<UpnpDevice>) {
            val devicesAdded = devices.mapNotNull { createDevice(it) }
            if (devicesAdded.isNotEmpty()) {
                deviceListener.onDevicesAdded(devicesAdded)
            }
        }

        override fun onDevicesRemoved(devices: List<UpnpDevice>) {
            synchronized(detectedDevices) {
                val devicesRemoved = devices.mapNotNull { device ->
                    detectedDevices
                        .firstOrNull { device.id == it.upnpID }
                        .ifNotNull { removeDevice(it) }
                }
                if (devicesRemoved.isNotEmpty()) {
                    deviceListener.onDevicesRemoved(devicesRemoved)
                }
            }
        }

        override fun onDevicesChanged(devices: List<UpnpDevice>) {
            val devicesChanged = devices.mapNotNull { device ->
                detectedDevices
                        .firstOrNull { device.id == it.upnpID }
                        .ifNotNull { it.upnpDevice = device }
            }
            if (devicesChanged.isNotEmpty()) {
                deviceListener.onDevicesChanged(devicesChanged)
            }
        }

        override fun onDiscoveryStopped(error: Throwable?) {
            deviceListener.onDiscoveryStopped(error)
        }
    }

    //endregion

    //region Event listener

    /**
     * The actual event listener.
     *
     * This listener dispatches OCast protocol events to all listeners in `eventListeners`.
     */
    private val eventListener = object : EventListener {

        override fun onMediaPlaybackStatus(device: Device, mediaPlaybackStatus: MediaPlaybackStatus) {
            eventListeners.wrapForEach { it.onMediaPlaybackStatus(device, mediaPlaybackStatus) }
        }

        override fun onMediaMetadataChanged(device: Device, mediaMetadata: MediaMetadata) {
            eventListeners.wrapForEach { it.onMediaMetadataChanged(device, mediaMetadata) }
        }

        override fun onUpdateStatus(device: Device, updateStatus: UpdateStatus) {
            eventListeners.wrapForEach { it.onUpdateStatus(device, updateStatus) }
        }

        override fun onCustomEvent(device: Device, name: String, params: String) {
            eventListeners.wrapForEach { it.onCustomEvent(device, name, params) }
        }
    }

    //endregion

    //region Device listener

    /**
     * The actual device listener.
     *
     * This listener dispatches device events to all listeners in `deviceListeners`.
     */
    private val deviceListener = object : DeviceListener {

        override fun onDevicesAdded(devices: List<Device>) {
            deviceListeners.wrapForEach { it.onDevicesAdded(devices) }
        }

        override fun onDevicesRemoved(devices: List<Device>) {
            deviceListeners.wrapForEach { it.onDevicesRemoved(devices) }
        }

        override fun onDevicesChanged(devices: List<Device>) {
            deviceListeners.wrapForEach { it.onDevicesChanged(devices) }
        }

        override fun onDeviceDisconnected(device: Device, error: Throwable?) {
            deviceListeners.wrapForEach { it.onDeviceDisconnected(device, error) }
        }

        override fun onDiscoveryStopped(error: Throwable?) {
            deviceListeners.wrapForEach { it.onDiscoveryStopped(error) }
        }
    }

    //endregion

    init {
        deviceDiscovery.listener = deviceDiscoveryListener
    }
}
