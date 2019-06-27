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

package org.ocast.core

import org.ocast.common.utils.CallbackWrapperOwner
import org.ocast.core.models.CallbackWrapper
import org.ocast.core.models.CustomEvent
import org.ocast.core.models.MetadataChangedEvent
import org.ocast.core.models.PlaybackStatusEvent
import org.ocast.core.models.UpdateStatusEvent
import org.ocast.core.utils.SimpleCallbackWrapper
import org.ocast.discovery.DeviceDiscovery
import org.ocast.discovery.models.UpnpDevice
import java.util.Collections

open class OCastCenter : CallbackWrapperOwner {

    override var callbackWrapper: CallbackWrapper = SimpleCallbackWrapper()
        set(value) {
            field = value
            devices.forEach { it.callbackWrapper = value }
        }

    private val deviceDiscovery = DeviceDiscovery()

    private val eventListeners = mutableSetOf<EventListener>()
    private val deviceListeners = mutableSetOf<DeviceListener>()
    private var oCastCenterListeners = mutableSetOf<OCastCenterListener>()

    private val registeredDevicesByManufacturer = mutableMapOf<String, Class<out Device>>()
    private val detectedDevices = Collections.synchronizedList(mutableListOf<Device>())

    val devices: List<Device>
        get() = detectedDevices.toList()

    private fun createDevice(device: UpnpDevice): Device? {
        return registeredDevicesByManufacturer[device.manufacturer]
            ?.getConstructor(UpnpDevice::class.java)
            ?.newInstance(device)
            ?.apply {
                deviceListener = this@OCastCenter.deviceListener
                eventListener = this@OCastCenter.eventListener
                callbackWrapper = this@OCastCenter.callbackWrapper
                // Custom actions on custom device
                onCreateDevice(this)
                detectedDevices.add(this)
            }
    }

    private fun removeDevice(device: Device) {
        device.apply {
            deviceListener = null
            eventListener = null
            // Custom actions on custom device
            onRemoveDevice(this)
            detectedDevices.remove(this)
        }
    }

    protected open fun onCreateDevice(device: Device) {
    }

    protected open fun onRemoveDevice(device: Device) {
    }

    fun registerDevice(deviceClass: Class<out Device>) {
        val device = deviceClass.getConstructor(UpnpDevice::class.java).newInstance(UpnpDevice())
        registeredDevicesByManufacturer[device.manufacturer] = deviceClass
        deviceDiscovery.searchTargets += device.searchTarget
    }

    fun addEventListener(listener: EventListener) {
        eventListeners.add(listener)
    }

    fun removeEventListener(listener: EventListener) {
        eventListeners.remove(listener)
    }

    fun addDeviceListener(listener: DeviceListener) {
        deviceListeners.add(listener)
    }

    fun removeDeviceListener(listener: DeviceListener) {
        deviceListeners.remove(listener)
    }

    fun addOCastCenterListener(oCastCenterListener: OCastCenterListener) {
        oCastCenterListeners.add(oCastCenterListener)
    }

    fun removeOCastCenterListener(oCastCenterListener: OCastCenterListener) {
        oCastCenterListeners.remove(oCastCenterListener)
    }

    @JvmOverloads fun resumeDiscovery(isActiveScan: Boolean = false) {
        deviceDiscovery.listener = deviceDiscoveryListener
        deviceDiscovery.resume()
    }

    fun stopDiscovery() {
        deviceDiscovery.listener = null
        deviceDiscovery.stop()
    }

    //region Discovery listener

    private val deviceDiscoveryListener = object : DeviceDiscovery.Listener {
        override fun onDevicesAdded(devices: List<UpnpDevice>) {
            devices.forEach { upnpDevice ->
                createDevice(upnpDevice)?.let { device ->
                    this@OCastCenter.deviceListener.onDeviceAdded(device)
                }
            }
        }

        override fun onDevicesRemoved(devices: List<UpnpDevice>) {
            devices.forEach { device ->
                synchronized(detectedDevices) {
                    detectedDevices.firstOrNull { device.uuid == it.uuid }?.let {
                        this@OCastCenter.deviceListener.onDeviceRemoved(it)
                        removeDevice(it)
                    }
                }
            }
        }

        override fun onDiscoveryStopped(error: Throwable?) {
            oCastCenterListeners.wrapForEach { it.onDiscoveryStopped(error) }
        }
    }

    //endregion

    //region Event listener

    private val eventListener = object : EventListener {

        override fun onPlaybackStatus(device: Device, status: PlaybackStatusEvent) {
            eventListeners.wrapForEach { it.onPlaybackStatus(device, status) }
        }

        override fun onMetadataChanged(device: Device, metadata: MetadataChangedEvent) {
            eventListeners.wrapForEach { it.onMetadataChanged(device, metadata) }
        }

        override fun onUpdateStatus(device: Device, updateStatus: UpdateStatusEvent) {
            eventListeners.wrapForEach { it.onUpdateStatus(device, updateStatus) }
        }

        override fun onCustomEvent(device: Device, customEvent: CustomEvent) {
            eventListeners.wrapForEach { it.onCustomEvent(device, customEvent) }
        }
    }

    //endregion

    //region Device listener

    private val deviceListener = object : DeviceListener {

        override fun onDeviceAdded(device: Device) {
            deviceListeners.wrapForEach { it.onDeviceAdded(device) }
        }

        override fun onDeviceRemoved(device: Device) {
            deviceListeners.wrapForEach { it.onDeviceRemoved(device) }
        }

        override fun onDeviceDisconnected(device: Device, error: Throwable?) {
            deviceListeners.wrapForEach { it.onDeviceDisconnected(device, error) }
        }
    }

    //endregion
}

interface OCastCenterListener {

    fun onDiscoveryStopped(error: Throwable?)
}
