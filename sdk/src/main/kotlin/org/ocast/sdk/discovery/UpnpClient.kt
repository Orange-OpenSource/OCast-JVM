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

package org.ocast.sdk.discovery

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import okhttp3.OkHttpClient
import okhttp3.Request
import org.ocast.sdk.common.extensions.enqueue
import org.ocast.sdk.common.extensions.toMap
import org.ocast.sdk.core.utils.OCastLog
import org.ocast.sdk.discovery.models.UpnpDevice

/**
 * This class handles the retrieval of information about devices through the UPnP protocol.
 *
 * @constructor Creates an instance of [UpnpClient].
 */
internal open class UpnpClient {

    /**
     * The companion object.
     */
    companion object {

        /**
         * The regex used to extract a device UUID from a string.
         */
        private val UUID_REGEX = "^uuid:([^:]*)".toRegex()

        /**
         * Extracts the UUID of a device from a string.
         *
         * @param string The string to extract the UUID from. This is typically a Unique Service Name or a Unique Device Name.
         * @return The extracted UUID, or `null` if the UUID could not be extracted.
         */
        fun extractUuid(string: String): String? {
            return UUID_REGEX.find(string)?.groupValues?.elementAtOrNull(1)
        }
    }

    /** The OkHttp client which will send device description requests. */
    private val client = OkHttpClient()

    /** A date format compliant with ISO 8601 dates. */
    private val iso8601DateFormat = SimpleDateFormat("E, d MMM yyyy HH:mm:ss z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("GMT")
    }

    /**
     * Retrieves a device.
     *
     * This method launches a device description request according to the UPnP protocol.
     *
     * @param location The device location extracted from the SSDP M-SEARCH response.
     * @param onComplete The lambda that will be called when the request is completed.
     */
    open fun getDevice(location: String, onComplete: (Result<UpnpDevice>) -> Unit) {
        try {
            val request = Request.Builder()
                .header("Date", iso8601DateFormat.format(Date()))
                .url(location)
                .build()
            client.newCall(request).enqueue { getDeviceResult ->
                var xml: String? = null
                val result = getDeviceResult
                    .mapCatching { response ->
                        xml = response.body()?.string()
                        val headers = response.headers().toMap()
                        UpnpDevice.decode(xml.orEmpty(), headers)
                    }
                    .onFailure { OCastLog.error(it) { "Failed to retrieve description for UPnP device at location $location" } }
                    .onSuccess { OCastLog.debug { "Retrieved description for UPnP device at location $location:\n${xml.orEmpty().prependIndent()}" } }
                onComplete(result)
            }
        } catch (exception: Exception) {
            OCastLog.error(exception) { "Failed to retrieve description for UPnP device at location $location" }
            onComplete(Result.failure(exception))
        }
    }
}
