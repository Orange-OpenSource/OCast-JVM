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

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.mediarouter.media.MediaRouteProvider
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import org.ocast.mediaroute.wrapper.AndroidUIThreadCallbackWrapper
import org.ocast.sdk.core.Device
import org.ocast.sdk.core.DeviceCenter
import org.ocast.sdk.core.EventListener

object OCastMediaRouteHelper {

    internal const val EXTRA_DEVICE = "org.ocast.mediaroute.extra.DEVICE"
    private val deviceCenter = DeviceCenter().apply { callbackWrapper = AndroidUIThreadCallbackWrapper() }
    private val mainHandler = Handler(Looper.getMainLooper())
    @SuppressLint("StaticFieldLeak")
    private var mediaRouter: MediaRouter? = null
    private var initialized = false

    val mediaRouteSelector = MediaRouteSelector.Builder()
        .addControlCategory(OCastMediaRouteProvider.FILTER_CATEGORY_OCAST)
        .build()
    val devices: List<Device>
        get() = deviceCenter.devices

    fun initialize(context: Context, devices: List<Class<out Device>>) {
        if (!initialized) {
            if (!isMainThread()) {
                throw RuntimeException("${this::class.java.simpleName} should be instantiated on the UI thread.")
            }
            initialized = true
            devices.forEach { deviceCenter.registerDevice(it) }
            val oCastProvider = OCastMediaRouteProvider(context.applicationContext, deviceCenter, mainHandler)
            mediaRouter = MediaRouter.getInstance(context.applicationContext)
            mediaRouter?.addProvider(oCastProvider)
        }
    }

    /**
     * Add the callback on start to tell the media router what kinds of routes the application is interested in so that it can try to discover suitable ones.
     *
     * @param mediaRouterCallback
     */
    fun addMediaRouterCallback(mediaRouterCallback: MediaRouter.Callback) {
        if (initialized) {
            runOnMainThread {
                mediaRouter?.addCallback(mediaRouteSelector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
            }
        } else {
            throw RuntimeException("${this::class.java.simpleName} not initialized. Call initialize with proper configuration")
        }
    }

    /**
     * Remove the selector on stop to tell the media router that it no longer needs to invest effort trying to discover routes of these kinds for now.
     *
     * @param mediaRouterCallback
     */
    fun removeMediaRouterCallback(mediaRouterCallback: MediaRouter.Callback) {
        runOnMainThread {
            mediaRouter?.removeCallback(mediaRouterCallback)
        }
    }

    fun getDeviceFromRoute(routeInfo: MediaRouter.RouteInfo?): Device? {
        val device = routeInfo?.extras?.get(EXTRA_DEVICE) as? Device
        return deviceCenter.devices.firstOrNull { it.upnpID == device?.upnpID }
    }

    fun isOCastRouteInfo(routeInfo: MediaRouter.RouteInfo?) = routeInfo?.matchesSelector(mediaRouteSelector) == true

    fun addEventListener(listener: EventListener) {
        deviceCenter.addEventListener(listener)
    }

    fun removeEventListener(listener: EventListener) {
        deviceCenter.removeEventListener(listener)
    }

    internal fun addMediaRouteProvider(mediaRouteProvider: MediaRouteProvider) {
        if (initialized) {
            runOnMainThread {
                mediaRouter?.addProvider(mediaRouteProvider)
            }
        } else {
            throw RuntimeException("${this::class.java.simpleName} not initialized. Call initialize with proper configuration")
        }
    }

    internal fun removeMediaRouteProvider(mediaRouteProvider: MediaRouteProvider) {
        runOnMainThread {
            mediaRouter?.removeProvider(mediaRouteProvider)
        }
    }

    private fun isMainThread(): Boolean {
        return Looper.getMainLooper() == Looper.myLooper()
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (isMainThread()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
