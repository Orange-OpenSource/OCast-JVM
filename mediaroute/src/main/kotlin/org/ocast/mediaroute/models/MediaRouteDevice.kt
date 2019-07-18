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
import java.net.URL
import kotlinx.android.parcel.Parcelize
import org.ocast.sdk.core.Device

@Parcelize
data class MediaRouteDevice(
    var upnpID: String,
    var friendlyName: String,
    var manufacturer: String,
    var modelName: String
) : Parcelable {

    companion object {
        const val EXTRA_DEVICE = "org.ocast.mediaroute.extra.DEVICE"
    }

    constructor(device: Device) : this(device.upnpID, device.friendlyName, device.manufacturer, device.modelName)
}
