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

import android.arch.lifecycle.ViewModelProviders
import android.databinding.DataBindingUtil
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.app.AppCompatDelegate
import android.support.v7.media.MediaRouter
import org.ocast.core.Device
import org.ocast.core.EventListener
import org.ocast.core.models.CustomEvent
import org.ocast.core.models.MetadataChangedEvent
import org.ocast.core.models.PlaybackStatusEvent
import org.ocast.core.models.UpdateStatusEvent
import org.ocast.sample.mobile.R
import org.ocast.sample.mobile.databinding.ActivityMainBinding
import org.ocast.sample.mobile.utils.updateValue

class MainActivity : AppCompatActivity(), EventListener {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel

    private var mediaRouterCallback: MediaRouter.Callback = MediaRouterCallback()
    // private lateinit var oCastRouteHelper: OCastRouteHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)

        binding = DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main).apply {
            this.lifecycleOwner = this@MainActivity
            this.viewModel = viewModel
        }

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)

        // oCastRouteHelper = OCastRouteHelper(this, UICallbackWrapper(), listOf(ReferenceDevice::class.java))
    }

    override fun onStart() {
        super.onStart()

        // oCastRouteHelper.addPublicEventListener(this)
    }

    override fun onStop() {
        super.onStop()

        // oCastRouteHelper.removePublicEventListener(this)
    }

    /*
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)

        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)

        val mediaRouteMenuItem = menu.findItem(R.id.item_all_media_route)
        val actionProvider = MenuItemCompat.getActionProvider(mediaRouteMenuItem) as MediaRouteActionProvider

        // Set the MediaRouteActionProvider selector for device discovery.
        actionProvider.routeSelector = oCastRouteHelper.mediaRouteSelector

        return true
    }
    */

    override fun onPlaybackStatus(device: Device, status: PlaybackStatusEvent) {
        if (viewModel.selectedDevice.value == device) {
            viewModel.playbackStatus.updateValue(status)
        }
    }

    override fun onMetadataChanged(device: Device, metadata: MetadataChangedEvent) {
        if (viewModel.selectedDevice.value == device) {
            viewModel.mediaMetadata.updateValue(metadata)
        }
    }

    override fun onUpdateStatus(device: Device, updateStatus: UpdateStatusEvent) {
    }

    override fun onCustomEvent(device: Device, customEvent: CustomEvent) {
    }

    private inner class MediaRouterCallback : MediaRouter.Callback() {
        override fun onRouteSelected(router: MediaRouter?, route: MediaRouter.RouteInfo?) {

            /*
            val device = oCastRouteHelper.getDeviceFromRoute(route)
            if (device != null) {
                Log.d(TAG, "OCast device selected: ${device.friendlyName}")
                viewModel.selectedDevice.updateValue(device)
                // TODO connect device
            }
            */
        }

        override fun onRouteUnselected(mediaRouter: MediaRouter?, route: MediaRouter.RouteInfo?) {
            /*
            if (oCastRouteHelper.isOCastRouteInfo(route)) {
                Log.d(TAG, "OCast device unselected")
                // TODO : disconnect device
            }
            */
        }

        override fun onRouteRemoved(router: MediaRouter?, route: MediaRouter.RouteInfo?) {
            /*
            if (oCastRouteHelper.isOCastRouteInfo(route)) {
                Log.d(TAG, "OCast device removed")
            }
            */
        }

        override fun onRouteAdded(router: MediaRouter?, route: MediaRouter.RouteInfo?) {
            /*
            if (oCastRouteHelper.isOCastRouteInfo(route)) {
                Log.d(TAG, "OCast device added")
            }
            */
        }

        override fun onRouteChanged(router: MediaRouter?, route: MediaRouter.RouteInfo?) {
            /*
            if (oCastRouteHelper.isOCastRouteInfo(route)) {
                Log.d(TAG, "OCast device changed")
            }
            */
        }
    }
}
