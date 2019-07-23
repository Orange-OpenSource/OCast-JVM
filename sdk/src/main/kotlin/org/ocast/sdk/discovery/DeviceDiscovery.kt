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

import java.io.IOException
import java.util.Collections.synchronizedMap
import java.util.Date
import java.util.Timer
import java.util.concurrent.TimeUnit
import kotlin.concurrent.fixedRateTimer
import kotlin.concurrent.schedule
import kotlin.math.max
import org.ocast.sdk.core.utils.OCastLog
import org.ocast.sdk.discovery.models.SsdpMSearchRequest
import org.ocast.sdk.discovery.models.SsdpMSearchResponse
import org.ocast.sdk.discovery.models.SsdpMessage
import org.ocast.sdk.discovery.models.UpnpDevice

/**
 * This class represents the entry point for the discovery of OCast devices.
 *
 * @param socket The SSDP socket.
 * @param upnpClient The client which performs UPnP requests.
 */
internal class DeviceDiscovery constructor(
    private val socket: UDPSocket = UDPSocket(),
    private val upnpClient: UpnpClient = UpnpClient()
) {

    /**
     * Represents the states of the discovery.
     */
    private enum class State {

        /**
         * The discovery is running. M-SEARCH requests are periodically at the specified [interval].
         */
        RUNNING,

        /**
         * The discovery is paused. No M-SEARCH request is sent, but the list of [devices] remains unchanged from when it was running.
         */
        PAUSED,

        /**
         * The discovery is stopped. No M-SEARCH request is sent and the list of [devices] has been cleared.
         */
        STOPPED
    }

    companion object {

        /** The default value for the interval. */
        const val DEFAULT_INTERVAL = 30_000L

        /** The minimum value for the interval. */
        const val MINIMUM_INTERVAL = 5_000L

        /** The SSDP multicast address. */
        private const val SSDP_MULTICAST_ADDRESS = "239.255.255.250"

        /** The SSDP multicast port. */
        private const val SSDP_MULTICAST_PORT: Short = 1900

        /** The value of the MX header in the M-SEARCH request. */
        private const val MSEARCH_MX_VALUE = 3

        /** The name of the thread associated with the timer which refreshes devices. */
        private val REFRESH_DEVICES_THREAD_NAME = "${DeviceDiscovery::class.simpleName} Refresh Devices Timer Thread"

        /** The name of the thread associated with the timer which removes devices. */
        private val REMOVE_DEVICES_THREAD_NAME = "${DeviceDiscovery::class.simpleName} Remove Devices Timer Thread"
    }

    /** The listener. */
    var listener: Listener? = null

    /** The discovered devices. */
    val devices: List<UpnpDevice>
        get() = devicesByUuid.values.toList()

    /**
     * The search targets which the searched devices should correspond to.
     * Setting this property results in sending an SSDP M-SEARCH request immediately if discovery is on-going.
     * */
    var searchTargets = emptySet<String>()
        set(value) {
            field = value
            if (!socket.isClosed) {
                refreshDevices()
            }
        }

    /**
     * The interval to refresh the devices, in milliseconds.
     * Minimum value is 5000 milliseconds.
     * Setting this property results in sending an SSDP M-SEARCH request immediately if discovery is on-going.
     */
    var interval = DEFAULT_INTERVAL
        set(value) {
            field = max(value, MINIMUM_INTERVAL)
            if (!socket.isClosed) {
                refreshDevices()
            }
        }

    /** The current discovery state. */
    private var state = State.PAUSED

    /** A hash map where the key is the UUID of a device and the value is the last date when the corresponding device responded to an SSDP M-SEARCH request. */
    private val ssdpDatesByUuid = synchronizedMap(hashMapOf<String, Date>())

    /** A hash map where the key is the UUID of a device and the value is the device itself. */
    private val devicesByUuid = synchronizedMap(hashMapOf<String, UpnpDevice>())

    /** The timer which launches periodic SSDP M-SEARCH requests. */
    private var refreshDevicesTimer: Timer? = null

    /** The timer which removes devices if they do not respond to SSDP M-SEARCH requests. */
    private var removeDevicesTimer: Timer? = null

    init {
        socket.listener = SsdpSocketListener()
    }

    /**
     * Resumes the discovery process.
     * New initialized instances of [DeviceDiscovery] begin in a paused state, so you need to call this method to start the discovery.
     *
     * @return true if the discovery was successfully resumed, false if there was an issue or if the discovery was already running.
     */
    fun resume(): Boolean {
        return if (state != State.RUNNING) {
            try {
                socket.open()
                state = State.RUNNING
                refreshDevices()
                true
            } catch (exception: IOException) {
                false
            }
        } else {
            false
        }
    }

    /**
     * Stops the discovery process.
     * This clears the list of devices if the discovery was not in a paused state.
     *
     * @return true if the discovery was successfully stopped, false if the discovery was already stopped.
     */
    fun stop(): Boolean {
        return if (state != State.STOPPED) {
            state = State.STOPPED
            stop(true)
            true
        } else {
            false
        }
    }

    /**
     * Pauses the discovery process.
     * This does not clear the list of devices.
     *
     * @return true if the discovery was successfully paused, false if the discovery was not running.
     */
    fun pause(): Boolean {
        return if (state == State.RUNNING) {
            state = State.PAUSED
            stop(false)
            true
        } else {
            false
        }
    }

    /**
     * Closes the socket and performs all the internal cleanup.
     *
     * @param clearDevices Set to true to clear the devices, false to keep the devices list as is.
     * @param error The error, if any.
     */
    private fun stop(clearDevices: Boolean, error: Throwable? = null) {
        refreshDevicesTimer?.cancel()
        refreshDevicesTimer = null
        removeDevicesTimer?.cancel()
        removeDevicesTimer = null
        socket.close()
        if (clearDevices) {
            // Keep this line here because devicesByUuid will be cleared and we need to call onDevicesRemoved with the list of devices computed before they are cleared
            val devices = devices
            ssdpDatesByUuid.clear()
            devicesByUuid.clear()
            if (devices.isNotEmpty()) {
                listener?.onDevicesRemoved(devices)
            }
            listener?.onDiscoveryStopped(error)
        }
    }

    /**
     * Refreshes the devices.
     * This method launches an M-SEARCH request immediately and then periodically according to the [interval] property.
     */
    private fun refreshDevices() {
        refreshDevicesTimer?.cancel()
        refreshDevicesTimer = fixedRateTimer(name = REFRESH_DEVICES_THREAD_NAME, period = interval) {
            sendSsdpMSearchRequest()
        }
    }

    /**
     * Sends an SSDP M-SEARCH request.
     * Calling this method also schedules a task which will remove devices if they did not respond to the request.
     */
    private fun sendSsdpMSearchRequest() {
        for (searchTarget in searchTargets) {
            val host = "$SSDP_MULTICAST_ADDRESS:$SSDP_MULTICAST_PORT"
            val request = SsdpMSearchRequest(host, MSEARCH_MX_VALUE, searchTarget)
            // Send request twice to avoid UDP packet lost
            repeat(2) {
                try {
                    socket.send(request.data, SSDP_MULTICAST_ADDRESS, SSDP_MULTICAST_PORT)
                } catch (exception: IOException) {
                    OCastLog.error(exception) { "Could not send SSDP M-SEARCH request" }
                }
            }
        }
        scheduleRemoveDevicesTask()
    }

    /**
     * Schedules a task to remove devices if they do not respond to M-SEARCH requests.
     */
    private fun scheduleRemoveDevicesTask() {
        if (removeDevicesTimer == null) {
            removeDevicesTimer = Timer(REMOVE_DEVICES_THREAD_NAME)
        }

        val date = Date()
        // Add a 1 second delay to take into account the network round-trip time
        removeDevicesTimer?.schedule(TimeUnit.SECONDS.toMillis(MSEARCH_MX_VALUE.toLong() + 1)) {
            removeDevices(date)
        }
    }

    /**
     * Removes the devices that did not respond to M-SEARCH requests after the given [date].
     *
     * @param date The threshold date.
     */
    @Synchronized
    private fun removeDevices(date: Date) {
        val devicesByUuidToRemove = devicesByUuid.filter {
            val lastSeenDate = ssdpDatesByUuid[it.key]
            return@filter lastSeenDate == null || lastSeenDate < date
        }

        if (devicesByUuidToRemove.isNotEmpty()) {
            devicesByUuidToRemove.keys.forEach {
                devicesByUuid.remove(it)
                ssdpDatesByUuid.remove(it)
            }

            listener?.onDevicesRemoved(devicesByUuidToRemove.values.toList())
        }
    }

    /**
     * Handles an SSDP M-SEARCH response.
     */
    private fun handleSsdpMSearchResponse(response: SsdpMSearchResponse) {
        if (!socket.isClosed) {
            val uuid = UpnpClient.extractUuid(response.usn)
            if (uuid != null) {
                ssdpDatesByUuid[uuid] = Date()
                if (devicesByUuid[uuid] == null) {
                    // Launch a UPnP device description request to retrieve info about the device that responded
                    upnpClient.getDevice(response.location) { result ->
                        result.onSuccess { device ->
                            synchronized(devicesByUuid) {
                                // Check that this device has not already been added because M-SEARCH requests are sent twice
                                if (devicesByUuid[uuid] == null) {
                                    devicesByUuid[uuid] = device
                                    listener?.onDevicesAdded(listOf(device))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * A listener of [DeviceDiscovery] events.
     */
    interface Listener {

        /**
         * Tells the listener that new devices have been found.
         *
         * @param devices The found devices.
         */
        fun onDevicesAdded(devices: List<UpnpDevice>)

        /**
         * Tells the listener that devices have been lost.
         *
         * @param devices The lost devices.
         */
        fun onDevicesRemoved(devices: List<UpnpDevice>)

        /**
         * Tells the listener that the discovery stopped.
         *
         * @param error The error if there was an issue, or null if the discovery stopped normally.
         */
        fun onDiscoveryStopped(error: Throwable?)
    }

    /**
     * An implementation of the [UDPSocket.Listener] interface for the discovery.
     */
    private inner class SsdpSocketListener : UDPSocket.Listener {

        override fun onDataReceived(socket: UDPSocket, data: ByteArray, host: String) {
            val ssdpMSearchResponse = SsdpMessage.fromData(data) as? SsdpMSearchResponse
            if (ssdpMSearchResponse != null) {
                handleSsdpMSearchResponse(ssdpMSearchResponse)
            }
        }

        override fun onSocketClosed(socket: UDPSocket, error: Throwable?) {
            if (error != null) {
                stop(true, error)
            }
        }
    }
}
