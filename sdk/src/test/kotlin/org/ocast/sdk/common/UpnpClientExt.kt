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

package org.ocast.sdk.common

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.whenever
import org.ocast.sdk.discovery.UpnpClient
import org.ocast.sdk.discovery.models.UpnpDevice

/**
 * Stubs the responses to the device description requests received by the UPnP client.
 *
 * @param devicesByLocation A hash map where the key is the location used to perform the device description request and the value is the associated device.
 */
internal fun UpnpClient.stubDeviceDescriptionResponses(devicesByLocation: HashMap<String, UpnpDevice>) {
    devicesByLocation.forEach { (location, device) ->
        whenever(getDevice(eq(location), any())).doAnswer { invocationOnMock ->
            // Directly invoke the callback with the desired device
            val callback = invocationOnMock.getArgument<(Result<UpnpDevice>) -> Unit>(1)
            callback.invoke(Result.success(device))
        }
    }
}
