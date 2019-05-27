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

import android.content.Context
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import android.support.v7.media.MediaRouteDescriptor
import android.support.v7.media.MediaRouteDiscoveryRequest
import android.support.v7.media.MediaRouteProvider
import android.support.v7.media.MediaRouteProviderDescriptor
import android.support.v7.media.MediaRouter
import org.ocast.mediaroute.models.MediaRouteDevice
import org.ocast.core.ReferenceDevice
import org.ocast.discovery.models.UpnpDevice
import java.net.URL
import java.util.ArrayList

class FakeDeviceMediaRouteProvider(context: Context) : MediaRouteProvider(context) {

    companion object {
        const val FAKE_DEVICE_UUID = "3df5aeb1-03e8-4402-9bfd-00097439abcd"
        const val FAKE_DEVICE_NAME = "Fake Device"

        private val controlFiltersBasic = ArrayList<IntentFilter>()
        private var mediaRouteDescriptor: MediaRouteDescriptor? = null

        init {
            controlFiltersBasic.add(IntentFilter().apply {
                addCategory(OCastMediaRouteProvider.FILTER_CATEGORY_OCAST)
            })
            val upnpDevice = UpnpDevice(FAKE_DEVICE_UUID, URL("http://127.0.0.1:8008"), FAKE_DEVICE_NAME, "Orange SA", "cléTV")
            val mediaRouteDevice = MediaRouteDevice(ReferenceDevice(upnpDevice))
            val bundledDevice = Bundle()
            bundledDevice.putParcelable(MediaRouteDevice.EXTRA_DEVICE, mediaRouteDevice)

            mediaRouteDescriptor = MediaRouteDescriptor.Builder(upnpDevice.uuid, upnpDevice.friendlyName)
                .setDescription(upnpDevice.modelName)
                .addControlFilters(controlFiltersBasic)
                .setPlaybackStream(AudioManager.STREAM_MUSIC)
                .setPlaybackType(MediaRouter.RouteInfo.PLAYBACK_TYPE_REMOTE)
                .setVolumeHandling(MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE)
                .setVolumeMax(100)
                .setVolume(50)
                .setExtras(bundledDevice)
                .build()
        }
    }

    override fun onDiscoveryRequestChanged(request: MediaRouteDiscoveryRequest?) {
        if (request == null || request.selector == null) {
            return
        }
        publishRoutes()
    }

    override fun onCreateRouteController(routeId: String): RouteController? {
        return null
    }

    private fun publishRoutes() {
        descriptor = MediaRouteProviderDescriptor.Builder().addRoute(mediaRouteDescriptor).build()
    }
}