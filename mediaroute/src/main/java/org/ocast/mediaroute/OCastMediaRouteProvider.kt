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
import android.support.v7.media.MediaRouteDescriptor
import android.support.v7.media.MediaRouteDiscoveryRequest
import android.support.v7.media.MediaRouteProvider
import android.support.v7.media.MediaRouteProviderDescriptor
import android.util.Log
import org.ocast.mediaroute.models.MediaRouteDevice
import org.ocast.core.Device
import org.ocast.core.DeviceListener
import org.ocast.core.OCastCenter
import java.util.Collections

internal class OCastMediaRouteProvider(context: Context, private val oCastCenter: OCastCenter, private val mainHandler: Handler) : MediaRouteProvider(context) {

    internal companion object {
        internal const val FILTER_CATEGORY_OCAST = "org.ocast.CATEGORY_OCAST"
        private const val TAG = "OCastMediaRouteProvider"
    }

    private val routeDescriptorsByUuid = Collections.synchronizedMap(mutableMapOf<String, MediaRouteDescriptor>())
    private var isWifiMonitorReceiverRegistered = false
    private var isActiveScan = false

    init {
        oCastCenter.addDeviceListener(OCastMediaRouteDeviceListener())
    }

    private fun createMediaRouteDescriptor(device: Device): MediaRouteDescriptor {
        val bundledDevice = Bundle().apply {
            putParcelable(
                MediaRouteDevice.EXTRA_DEVICE,
                MediaRouteDevice(device)
            )
        }
        val controlFilter = IntentFilter().apply {
            addCategory(FILTER_CATEGORY_OCAST)
        }
        return MediaRouteDescriptor.Builder(device.uuid, device.friendlyName)
            .setDescription(device.modelName)
            .addControlFilter(controlFilter)
            .setExtras(bundledDevice)
            .build()
    }

    private fun publishRoutes() {
        mainHandler.post {
            descriptor = synchronized(routeDescriptorsByUuid) {
                MediaRouteProviderDescriptor.Builder()
                    .apply { addRoutes(routeDescriptorsByUuid.values) }
                    .build()
            }
        }
    }

    private fun onConnectionStateChanged(isConnected: Boolean) {
        if (isConnected) {
            // onConnectionStateChanged(false) is not necessarily called when changing WiFi network
            // This is why stopDiscovery is called here
            // Otherwise the list of devices is not cleared
            oCastCenter.stopDiscovery()
            oCastCenter.resumeDiscovery(isActiveScan)
        } else {
            oCastCenter.stopDiscovery()
        }
    }

    override fun onDiscoveryRequestChanged(request: MediaRouteDiscoveryRequest?) {
        if (request != null) {
            Log.d(TAG, "onDiscoveryRequest $request")
            isActiveScan = request.isActiveScan
            if (!isWifiMonitorReceiverRegistered) {
                isWifiMonitorReceiverRegistered = true
                val wifiMonitorIntentFilter = IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                context.registerReceiver(wifiMonitorReceiver, wifiMonitorIntentFilter)
            }
            val activeNetwork = (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetworkInfo
            @Suppress("DEPRECATION")
            val isWifiConnected = activeNetwork?.isConnectedOrConnecting == true && activeNetwork.type == ConnectivityManager.TYPE_WIFI
            if (isWifiConnected) {
                oCastCenter.resumeDiscovery(isActiveScan)
            }
        } else {
            if (isWifiMonitorReceiverRegistered) {
                context.unregisterReceiver(wifiMonitorReceiver)
                isWifiMonitorReceiverRegistered = false
            }
            oCastCenter.stopDiscovery()
        }
    }

    private inner class OCastMediaRouteDeviceListener : DeviceListener {

        override fun onDeviceAdded(device: Device) {
            routeDescriptorsByUuid[device.uuid] = createMediaRouteDescriptor(device)
            publishRoutes()
        }

        override fun onDeviceRemoved(device: Device) {
            synchronized(routeDescriptorsByUuid) {
                routeDescriptorsByUuid
                    .keys
                    .firstOrNull { it == device.uuid }
                    ?.run { routeDescriptorsByUuid.remove(this) }
            }
            publishRoutes()
        }

        override fun onDeviceDisconnected(device: Device, error: Throwable?) {
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