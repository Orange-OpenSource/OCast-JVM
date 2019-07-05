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

package org.ocast.discovery

import okhttp3.OkHttpClient
import okhttp3.Request
import org.ocast.common.extensions.enqueue
import org.ocast.common.extensions.toMap
import org.ocast.discovery.models.UpnpDevice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * This class handles the retrieval of OCast devices through the UPnP protocol.
 */
internal open class UpnpClient {

    /** The OkHttp client which will send device description requests. */
    private val client = OkHttpClient()

    /** A date format compliant with ISO 8601 dates. */
    private val iso8601DateFormat = SimpleDateFormat("E, d MMM yyyy HH:mm:ss z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("GMT")
    }

    /**
     * Retrieves a device.
     * This method launches a device description request according to the UPnP protocol.
     *
     * @param location The device location from the SSDP M-SEARCH response.
     * @param onComplete The lambda that will be called when the request is completed.
     */
    open fun getDevice(location: String, onComplete: (Result<UpnpDevice>) -> Unit) {
        try {
            val request = Request.Builder()
                .header("Date", iso8601DateFormat.format(Date()))
                .url(location)
                .build()
            client.newCall(request).enqueue { result ->
                onComplete(result.mapCatching { response ->
                    val xml = response.body()?.string().orEmpty()
                    val headers = response.headers().toMap()
                    UpnpDevice.decode(xml, headers)
                })
            }
        } catch (exception: Exception) {
            onComplete(Result.failure(exception))
        }
    }
}
