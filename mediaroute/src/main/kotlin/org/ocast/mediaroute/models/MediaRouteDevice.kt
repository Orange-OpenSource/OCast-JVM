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

package org.ocast.mediaroute.models

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import org.ocast.sdk.core.Device

/**
 * Represents an OCast device in the Android media route framework.
 *
 * @property upnpID The UPnP identifier of the device.
 * @property friendlyName The friendly name of the device.
 * @property manufacturer The manufacturer of the device.
 * @property modelName The model name of the device.
 * @constructor Creates an instance of [MediaRouteDevice].
 */
@Parcelize
data class MediaRouteDevice(
    var upnpID: String,
    var friendlyName: String,
    var manufacturer: String,
    var modelName: String
) : Parcelable {

    /**
     * The companion object.
     */
    companion object {

        /** The key to store a [MediaRouteDevice] as an extra of the various classes of the Android media route framework. */
        const val EXTRA_DEVICE = "org.ocast.mediaroute.extra.DEVICE"
    }

    /**
     * @param device The OCast device.
     * @constructor Creates an instance of [MediaRouteDevice] from a [Device].
     */
    constructor(device: Device) : this(device.upnpID, device.friendlyName, device.manufacturer, device.modelName)
}
