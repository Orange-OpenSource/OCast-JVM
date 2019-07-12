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

package org.ocast.sample.mobile.ui

import android.os.Bundle
import android.util.Log
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.MenuItemCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProviders
import androidx.mediarouter.app.MediaRouteActionProvider
import androidx.mediarouter.media.MediaRouter
import org.ocast.mediaroute.OCastMediaRouteHelper
import org.ocast.sample.mobile.R
import org.ocast.sample.mobile.databinding.ActivityMainBinding
import org.ocast.sample.mobile.utils.updateValue
import org.ocast.sdk.core.Device
import org.ocast.sdk.core.EventListener
import org.ocast.sdk.core.ReferenceDevice
import org.ocast.sdk.core.models.Media
import org.ocast.sdk.core.models.Metadata
import org.ocast.sdk.core.models.PlaybackStatus

class MainActivity : AppCompatActivity(), EventListener {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var mainViewModel: MainViewModel

    private var mediaRouterCallback = MediaRouterCallback()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainViewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)

        binding = DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main).apply {
            lifecycleOwner = this@MainActivity
            viewModel = mainViewModel
        }

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        OCastMediaRouteHelper.initialize(this, listOf(ReferenceDevice::class.java))
    }

    override fun onStart() {
        super.onStart()

        OCastMediaRouteHelper.addMediaRouterCallback(mediaRouterCallback)
        OCastMediaRouteHelper.addEventListener(this)
    }

    override fun onStop() {
        super.onStop()

        OCastMediaRouteHelper.removeEventListener(this)
        OCastMediaRouteHelper.removeMediaRouterCallback(mediaRouterCallback)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)

        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)

        val mediaRouteMenuItem = menu.findItem(R.id.item_all_media_route)
        val actionProvider = MenuItemCompat.getActionProvider(mediaRouteMenuItem) as MediaRouteActionProvider

        // Set the MediaRouteActionProvider selector for device discovery.
        actionProvider.routeSelector = OCastMediaRouteHelper.mediaRouteSelector

        return true
    }

    private fun connect(device: Device) {
        device.applicationName = "Orange-DefaultReceiver-DEV"
        device.connect({
            prepareMedia(device)
        }, {
            oCastError -> Log.e(TAG, "connect error ${oCastError.message}")
        })
    }

    private fun prepareMedia(device: Device) {
        device.prepareMedia("https://commondatastorage.googleapis.com/gtv-videos-bucket/CastVideos/mp4/BigBuckBunny.mp4",
            1,
            "Big Buck Bunny",
            "sampleAppKotlin",
            "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
            Media.Type.VIDEO,
            Media.TransferMode.STREAMED,
            true,
            null, {
                Log.d(TAG, "prepareMedia OK")
            }, {
                oCastError -> Log.e(TAG, "prepareMedia error ${oCastError.status}")
            })
    }

    override fun onPlaybackStatus(device: Device, playbackStatus: PlaybackStatus) {
        if (mainViewModel.selectedDevice.value == device) {
            Log.d(TAG, "onPlaybackStatus status=${playbackStatus.state} position=${playbackStatus.position}")
            mainViewModel.playbackStatus.updateValue(playbackStatus)
        }
    }

    override fun onMetadataChanged(device: Device, metadata: Metadata) {
        if (mainViewModel.selectedDevice.value == device) {
            mainViewModel.mediaMetadata.updateValue(metadata)
        }
    }

    private inner class MediaRouterCallback : MediaRouter.Callback() {

        override fun onRouteSelected(router: MediaRouter?, route: MediaRouter.RouteInfo?) {
            OCastMediaRouteHelper.getDeviceFromRoute(route)?.apply {
                Log.d(TAG, "OCast device selected: $friendlyName")
                mainViewModel.selectedDevice.updateValue(this)
                connect(this)
            }
        }

        override fun onRouteUnselected(mediaRouter: MediaRouter?, route: MediaRouter.RouteInfo?) {
            OCastMediaRouteHelper.getDeviceFromRoute(route)?.apply {
                Log.d(TAG, "OCast device unselected")
                stopApplication({}, {})
            }
        }

        override fun onRouteRemoved(router: MediaRouter?, route: MediaRouter.RouteInfo?) {
            if (OCastMediaRouteHelper.isOCastRouteInfo(route)) {
                Log.d(TAG, "OCast device removed")
            }
        }

        override fun onRouteAdded(router: MediaRouter?, route: MediaRouter.RouteInfo?) {
            if (OCastMediaRouteHelper.isOCastRouteInfo(route)) {
                Log.d(TAG, "OCast device added")
            }
        }

        override fun onRouteChanged(router: MediaRouter?, route: MediaRouter.RouteInfo?) {
            if (OCastMediaRouteHelper.isOCastRouteInfo(route)) {
                Log.d(TAG, "OCast device changed")
            }
        }
    }
}
