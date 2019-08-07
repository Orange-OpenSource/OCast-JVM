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

package org.ocast.mediaroute

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.mediarouter.media.MediaRouteDescriptor
import androidx.mediarouter.media.MediaRouteDiscoveryRequest
import androidx.mediarouter.media.MediaRouteProvider
import androidx.mediarouter.media.MediaRouteProviderDescriptor
import java.util.Collections
import org.ocast.sdk.core.Device
import org.ocast.sdk.core.DeviceCenter
import org.ocast.sdk.core.DeviceListener

internal class OCastMediaRouteProvider(context: Context, private val deviceCenter: DeviceCenter, private val mainHandler: Handler) : MediaRouteProvider(context) {

    internal companion object {
        internal const val FILTER_CATEGORY_OCAST = "org.ocast.CATEGORY_OCAST"
        private const val TAG = "OCastMediaRouteProvider"
    }

    private val routeDescriptorsByUpnpID = Collections.synchronizedMap(mutableMapOf<String, MediaRouteDescriptor>())
    private var isWifiMonitorReceiverRegistered = false

    init {
        deviceCenter.addDeviceListener(OCastMediaRouteDeviceListener())
    }

    private fun createMediaRouteDescriptor(device: Device): MediaRouteDescriptor {
        val bundledDevice = Bundle().apply {
            putSerializable(
                OCastMediaRouteHelper.EXTRA_DEVICE,
                device
            )
        }
        val controlFilter = IntentFilter().apply {
            addCategory(FILTER_CATEGORY_OCAST)
        }
        return MediaRouteDescriptor.Builder(device.upnpID, device.friendlyName)
            .setDescription(device.modelName)
            .addControlFilter(controlFilter)
            .setExtras(bundledDevice)
            .build()
    }

    private fun publishRoutes() {
        mainHandler.post {
            descriptor = synchronized(routeDescriptorsByUpnpID) {
                MediaRouteProviderDescriptor.Builder()
                    .apply { addRoutes(routeDescriptorsByUpnpID.values) }
                    .build()
            }
        }
    }

    private fun onConnectionStateChanged(isConnected: Boolean) {
        if (isConnected) {
            // onConnectionStateChanged(false) is not necessarily called when changing WiFi network
            // This is why stopDiscovery is called here
            // Otherwise the list of devices is not cleared
            deviceCenter.stopDiscovery()
            deviceCenter.resumeDiscovery()
        } else {
            deviceCenter.stopDiscovery()
        }
    }

    override fun onDiscoveryRequestChanged(request: MediaRouteDiscoveryRequest?) {
        if (request != null) {
            Log.d(TAG, "onDiscoveryRequest $request")
            deviceCenter.discoveryInterval = if (request.isActiveScan) DeviceCenter.MINIMUM_DISCOVERY_INTERVAL else DeviceCenter.DEFAULT_DISCOVERY_INTERVAL
            if (!isWifiMonitorReceiverRegistered) {
                isWifiMonitorReceiverRegistered = true
                val wifiMonitorIntentFilter = IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                context.registerReceiver(wifiMonitorReceiver, wifiMonitorIntentFilter)
            }
            val activeNetwork = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetworkInfo
            @Suppress("DEPRECATION")
            val isWifiConnected = activeNetwork?.isConnectedOrConnecting == true && activeNetwork.type == ConnectivityManager.TYPE_WIFI
            if (isWifiConnected) {
                deviceCenter.resumeDiscovery()
            }
        } else {
            if (isWifiMonitorReceiverRegistered) {
                context.unregisterReceiver(wifiMonitorReceiver)
                isWifiMonitorReceiverRegistered = false
            }
            deviceCenter.stopDiscovery()
        }
    }

    private inner class OCastMediaRouteDeviceListener : DeviceListener {

        override fun onDevicesAdded(devices: List<Device>) {
            devices.forEach { device ->
                routeDescriptorsByUpnpID[device.upnpID] = createMediaRouteDescriptor(device)
            }
            publishRoutes()
        }

        override fun onDevicesRemoved(devices: List<Device>) {
            synchronized(routeDescriptorsByUpnpID) {
                devices.forEach { device ->
                    routeDescriptorsByUpnpID
                        .keys
                        .firstOrNull { it == device.upnpID }
                        ?.run { routeDescriptorsByUpnpID.remove(this) }
                }
            }
            publishRoutes()
        }
    }

    private val wifiMonitorReceiver = object : BroadcastReceiver() {

        private var isConnected = false

        override fun onReceive(context: Context?, intent: Intent?) {
            if (!isInitialStickyBroadcast && intent?.action == WifiManager.NETWORK_STATE_CHANGED_ACTION) {
                val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)
                if (networkInfo?.isConnected == true) {
                    if (!isConnected) {
                        Log.d(TAG, "Wifi is connected: $networkInfo")
                        onConnectionStateChanged(true)
                        isConnected = true
                    }
                } else {
                    if (isConnected) {
                        Log.d(TAG, "Wifi is disconnected: $networkInfo")
                        isConnected = false
                        onConnectionStateChanged(false)
                    }
                }
            }
        }
    }
}
