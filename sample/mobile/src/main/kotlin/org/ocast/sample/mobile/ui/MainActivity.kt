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
import androidx.lifecycle.ViewModelProvider
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
import org.ocast.sdk.core.models.MediaMetadata
import org.ocast.sdk.core.models.MediaPlaybackStatus
import org.ocast.sdk.core.models.PrepareMediaCommandParams
import org.ocast.sdk.core.utils.OCastLog
import java.util.logging.LogManager

class MainActivity : AppCompatActivity(), EventListener {

    companion object {

        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var mainViewModel: MainViewModel
    private var mediaRouterCallback = MediaRouterCallback()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainViewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        binding = DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main).apply {
            lifecycleOwner = this@MainActivity
            viewModel = mainViewModel
        }
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        try {
            val inputStream = resources.openRawResource(R.raw.logging)
            LogManager.getLogManager().readConfiguration(inputStream)
        } catch (exception: Exception) {
            exception.printStackTrace()
        }
        OCastLog.level = OCastLog.Level.ALL
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

        menuInflater.inflate(R.menu.menu_main, menu)
        val mediaRouteMenuItem = menu.findItem(R.id.item_all_media_route)
        val actionProvider = MenuItemCompat.getActionProvider(mediaRouteMenuItem) as MediaRouteActionProvider
        actionProvider.routeSelector = OCastMediaRouteHelper.mediaRouteSelector

        return true
    }

    private fun connect(device: Device) {
        device.applicationName = "Orange-DefaultReceiver-DEV"
        device.connect(
            null,
            { prepareMedia(device) },
            { Log.w(TAG, "connect error ${it.message}") }
        )
    }

    private fun prepareMedia(device: Device) {
        val params = PrepareMediaCommandParams(
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/CastVideos/mp4/BigBuckBunny.mp4",
            1,
            "Big Buck Bunny",
            "sampleAppKotlin",
            "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
            Media.Type.VIDEO,
            Media.TransferMode.STREAMED,
            true
        )
        device.prepareMedia(
            params,
            null,
            { Log.i(TAG, "prepareMedia OK") },
            { Log.w(TAG, "prepareMedia error ${it.status}") }
        )
    }

    override fun onMediaPlaybackStatus(device: Device, mediaPlaybackStatus: MediaPlaybackStatus) {
        if (mainViewModel.selectedDevice.value == device) {
            Log.i(TAG, "onMediaPlaybackStatus status=${mediaPlaybackStatus.state} position=${mediaPlaybackStatus.position}")
            mainViewModel.playbackStatus.updateValue(mediaPlaybackStatus)
        }
    }

    override fun onMediaMetadataChanged(device: Device, mediaMetadata: MediaMetadata) {
        if (mainViewModel.selectedDevice.value == device) {
            mainViewModel.mediaMetadata.updateValue(mediaMetadata)
        }
    }

    private inner class MediaRouterCallback : MediaRouter.Callback() {

        override fun onRouteSelected(router: MediaRouter?, route: MediaRouter.RouteInfo?) {
            OCastMediaRouteHelper.getDeviceFromRoute(route)?.apply {
                Log.i(TAG, "OCast device selected: $friendlyName")
                mainViewModel.selectedDevice.updateValue(this)
                connect(this)
            }
        }

        override fun onRouteUnselected(mediaRouter: MediaRouter?, route: MediaRouter.RouteInfo?) {
            OCastMediaRouteHelper.getDeviceFromRoute(route)?.apply {
                Log.i(TAG, "OCast device unselected")
                stopApplication({}, {})
            }
        }

        override fun onRouteRemoved(router: MediaRouter?, route: MediaRouter.RouteInfo?) {
            if (OCastMediaRouteHelper.isOCastRouteInfo(route)) {
                Log.i(TAG, "OCast device removed")
            }
        }

        override fun onRouteAdded(router: MediaRouter?, route: MediaRouter.RouteInfo?) {
            if (OCastMediaRouteHelper.isOCastRouteInfo(route)) {
                Log.i(TAG, "OCast device added")
            }
        }

        override fun onRouteChanged(router: MediaRouter?, route: MediaRouter.RouteInfo?) {
            if (OCastMediaRouteHelper.isOCastRouteInfo(route)) {
                Log.i(TAG, "OCast device changed")
            }
        }
    }
}
