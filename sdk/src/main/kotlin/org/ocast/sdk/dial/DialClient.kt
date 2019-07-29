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

package org.ocast.sdk.dial

import java.net.URL
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.ocast.sdk.common.extensions.enqueue
import org.ocast.sdk.dial.models.DialApplication
import org.ocast.sdk.dial.models.DialError

/**
 * Instances of this class handle DIAL requests.
 *
 * @param baseURL The base URL of the DIAL service.
 */
internal class DialClient(private val baseURL: URL) {

    /** The OkHttp client which will send DIAL requests. */
    private val client = OkHttpClient()

    /**
     * Starts an application.
     *
     * @param name The name of the application to start.
     * @param onComplete The lambda that will be called when the request is completed.
     */
    fun startApplication(name: String, onComplete: (Result<Unit>) -> Unit) {
        try {
            val request = Request.Builder()
                .url(URL("$baseURL/$name"))
                .post(RequestBody.create(null, byteArrayOf()))
                .build()
            client.newCall(request).enqueue { result ->
                onComplete(result.map {})
            }
        } catch (exception: Exception) {
            onComplete(Result.failure(exception))
        }
    }

    /**
     * Stops an application.
     *
     * @param name The name of the application to stop.
     * @param onComplete The lambda that will be called when the request is completed.
     */
    fun stopApplication(name: String, onComplete: (Result<Unit>) -> Unit) {
        getApplication(name) { getApplicationResult ->
            getApplicationResult.onFailure { onComplete(getApplicationResult.map {}) }
            getApplicationResult.onSuccess { application ->
                val instanceURL = application.getInstanceURL(baseURL)
                if (application.isStopAllowed && instanceURL != null) {
                    try {
                        val request = Request.Builder()
                            .url(instanceURL)
                            .delete()
                            .build()
                        client.newCall(request).enqueue { stopApplicationResult ->
                            onComplete(stopApplicationResult.map {})
                        }
                    } catch (exception: Exception) {
                        onComplete(Result.failure(exception))
                    }
                } else {
                    onComplete(Result.failure(DialError("Could not stop application $name")))
                }
            }
        }
    }

    /**
     * Retrieves information about a DIAL application.
     * This method launches an application information request.
     *
     * @param name The name of the application to retrieve information from.
     * @param onComplete The lambda that will be called when the request is completed.
     */
    fun getApplication(name: String, onComplete: (Result<DialApplication>) -> Unit) {
        try {
            val request = Request.Builder()
                .url(URL("$baseURL/$name"))
                .build()
            client.newCall(request).enqueue { result ->
                onComplete(result.mapCatching { response ->
                    DialApplication.decode(response.body()?.string().orEmpty())
                })
            }
        } catch (exception: Exception) {
            onComplete(Result.failure(exception))
        }
    }
}
