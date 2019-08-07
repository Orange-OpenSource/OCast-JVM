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

/**
 * This singleton provides methods to take advantage of the Android media route framework with OCast devices.
 */
object OCastMediaRouteHelper {

    /** The key to store a [Device] as an extra in the Android media route framework. */
    internal const val EXTRA_DEVICE = "org.ocast.mediaroute.extra.DEVICE"

    /** The device center. */
    private val deviceCenter = DeviceCenter().apply { callbackWrapper = AndroidUIThreadCallbackWrapper() }

    /** A handler on the main thread. */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** The media router singleton. */
    @SuppressLint("StaticFieldLeak")
    private var mediaRouter: MediaRouter? = null

    /** Indicates if the [OCastMediaRouteHelper] singleton has been initialized. */
    private var initialized = false

    /**
     *  The OCast media route selector.
     *
     *  Set the `routeSelector` property of the `actionProvider` of your menu item with the value returned by this property
     *  to show a dialog which displays a list of the discovered OCast devices.
     */
    val mediaRouteSelector = MediaRouteSelector.Builder()
        .addControlCategory(OCastMediaRouteProvider.FILTER_CATEGORY_OCAST)
        .build()

    /** The discovered OCast devices. */
    val devices: List<Device>
        get() = deviceCenter.devices

    /**
     * Initializes the [OCastMediaRouteHelper] singleton with the specified device classes.
     *
     * This method MUST be called on the main thread prior to any other method calls.
     *
     * @param context The context.
     * @param devices The classes of devices that will be searched during the discovery process.
     */
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
     * Adds a media router callback.
     *
     * @param mediaRouterCallback The callback to add.
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
     * Removes a media router callback that has been previously added with the `addMediaRouterCallback(@NotNull MediaRouter.Callback mediaRouterCallback)` method.
     *
     * @param mediaRouterCallback The callback to remove.
     */
    fun removeMediaRouterCallback(mediaRouterCallback: MediaRouter.Callback) {
        runOnMainThread {
            mediaRouter?.removeCallback(mediaRouterCallback)
        }
    }

    /**
     * Returns the OCast device which is associated to a [MediaRouter.RouteInfo].
     *
     * @param routeInfo The route info.
     * @return The associated OCast device.
     */
    fun getDeviceFromRoute(routeInfo: MediaRouter.RouteInfo?): Device? {
        val device = routeInfo?.extras?.get(EXTRA_DEVICE) as? Device
        return deviceCenter.devices.firstOrNull { it.upnpID == device?.upnpID }
    }

    /**
     * Indicates if a route info is associated with an OCast device.
     *
     * @param `true` if the route info is associated to an OCast device, otherwise `false`.
     */
    fun isOCastRouteInfo(routeInfo: MediaRouter.RouteInfo?) = routeInfo?.matchesSelector(mediaRouteSelector) == true

    /**
     * Adds a listener for the OCast protocol events.
     *
     * @param listener The listener to add.
     */
    fun addEventListener(listener: EventListener) {
        deviceCenter.addEventListener(listener)
    }

    /**
     * Removes a listener which has been previously added with the `addEventListener(@NotNull EventListener listener)` method.
     *
     * @param listener The listener to remove.
     */
    fun removeEventListener(listener: EventListener) {
        deviceCenter.removeEventListener(listener)
    }

    /**
     * Adds a [MediaRouteProvider] to the media router singleton.
     *
     * This method is used for tests purpose only.
     *
     * @param mediaRouteProvider The media route provider to add.
     */
    internal fun addMediaRouteProvider(mediaRouteProvider: MediaRouteProvider) {
        if (initialized) {
            runOnMainThread {
                mediaRouter?.addProvider(mediaRouteProvider)
            }
        } else {
            throw RuntimeException("${this::class.java.simpleName} not initialized. Call initialize with proper configuration")
        }
    }

    /**
     * Removes a [MediaRouteProvider] from the media router singleton.
     *
     * This method is used for tests purpose only.
     *
     * @param mediaRouteProvider The media route provider to remove.
     */
    internal fun removeMediaRouteProvider(mediaRouteProvider: MediaRouteProvider) {
        runOnMainThread {
            mediaRouter?.removeProvider(mediaRouteProvider)
        }
    }

    /**
     * Indicates if the current thread is the main thread.
     *
     * @return `true` if the current thread is the main thread, otherwise `false`.
     */
    private fun isMainThread(): Boolean {
        return Looper.getMainLooper() == Looper.myLooper()
    }

    /**
     * Runs the specified block of code on the main thread.
     *
     * @param block The block of code to run.
     */
    private fun runOnMainThread(block: () -> Unit) {
        if (isMainThread()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
