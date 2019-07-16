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

/**
 * Represents a listener of events related to devices and discovery.
 */
interface DeviceListener {

    /**
     * Tells the listener that a device has been disconnected.
     *
     * @param device The disconnected device.
     * @param error The error that caused the disconnection, or `null` if the disconnection was initiated by the user.
     */
    @JvmDefault
    fun onDeviceDisconnected(device: Device, error: Throwable?) {
    }

    /**
     * Tells the listener that one or more devices have been discovered.
     *
     * @param devices The discovered devices.
     */
    @JvmDefault
    fun onDevicesAdded(devices: List<Device>) {
    }

    /**
     * Tells the listener that one or more devices have been lost.
     *
     * @param devices The lost devices.
     */
    @JvmDefault
    fun onDevicesRemoved(devices: List<Device>) {
    }

    /**
     * Tells the listener that the device discovery stopped.
     *
     * @param error The error, or `null` is the discovery was stopped by the user.
     */
    @JvmDefault
    fun onDevicesChanged(devices: List<Device>) {
    }

    @JvmDefault
    fun onDiscoveryStopped(error: Throwable?) {
    }
}
