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
import android.os.Handler
import android.os.Looper
import android.support.v7.media.MediaRouteProvider
import android.support.v7.media.MediaRouteSelector
import android.support.v7.media.MediaRouter
import org.ocast.mediaroute.models.MediaRouteDevice
import org.ocast.core.Device
import org.ocast.core.OCastCenter
import org.ocast.core.EventListener

class OCastMediaRouteHelper(context: Context, devices: List<Class<out Device>>) {

    private val oCastCenter = OCastCenter(AndroidUIThreadCallbackWrapper())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mediaRouter: MediaRouter

    val mediaRouteSelector: MediaRouteSelector
    val devices: List<Device>
        get() = oCastCenter.devices

    init {
        if (Looper.getMainLooper() != Looper.myLooper()) {
            throw RuntimeException("${this::class.java.simpleName} should be instantiated on the UI thread.")
        }
        devices.forEach {
            oCastCenter.registerDevice(it)
        }
        val oCastProvider = OCastMediaRouteProvider(context.applicationContext, oCastCenter, mainHandler)
        mediaRouter = MediaRouter.getInstance(context.applicationContext)
        mediaRouter.addProvider(oCastProvider)
        mediaRouteSelector = MediaRouteSelector.Builder()
            .addControlCategory(OCastMediaRouteProvider.FILTER_CATEGORY_OCAST)
            .build()
    }

    /**
     * Add the callback on start to tell the media router what kinds of routes the application is interested in so that it can try to discover suitable ones.
     *
     * @param mediaRouterCallback
     */
    fun addMediaRouterCallback(mediaRouterCallback: MediaRouter.Callback) {
        mainHandler.post {
            mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        }
    }

    /**
     * Remove the selector on stop to tell the media router that it no longer needs to invest effort trying to discover routes of these kinds for now.
     *
     * @param mediaRouterCallback
     */
    fun removeMediaRouterCallback(mediaRouterCallback: MediaRouter.Callback) {
        mainHandler.post {
            mediaRouter.removeCallback(mediaRouterCallback)
        }
    }

    fun getDeviceFromRoute(routeInfo: MediaRouter.RouteInfo?): Device? {
        val mediaRouteDevice = routeInfo?.extras?.get(MediaRouteDevice.EXTRA_DEVICE) as? MediaRouteDevice
        return oCastCenter.devices.firstOrNull { it.uuid == mediaRouteDevice?.uuid }
    }

    fun isOCastRouteInfo(routeInfo: MediaRouter.RouteInfo?) = routeInfo?.matchesSelector(mediaRouteSelector) == true

    fun addEventListener(listener: EventListener) {
        oCastCenter.addEventListener(listener)
    }

    fun removePublicEventListener(listener: EventListener) {
        oCastCenter.removeEventListener(listener)
    }

    internal fun addMediaRouteProvider(mediaRouteProvider: MediaRouteProvider) {
        mainHandler.post {
            mediaRouter.addProvider(mediaRouteProvider)
        }
    }

    internal fun removeMediaRouteProvider(mediaRouteProvider: MediaRouteProvider) {
        mainHandler.post {
            mediaRouter.removeProvider(mediaRouteProvider)
        }
    }
}