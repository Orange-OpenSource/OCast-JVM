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

package org.ocast.dial

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.ocast.common.asList
import org.ocast.common.enqueue
import org.ocast.common.item
import org.ocast.core.utils.OCastLog
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

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
                .url("$baseURL/$name")
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
                if (application.isStopAllowed && application.instanceURL != null) {
                    try {
                        val request = Request.Builder()
                            .url(application.instanceURL)
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
    fun getApplication(name: String, onComplete: (Result<DialApplication<OCastAdditionalData>>) -> Unit) {
        try {
            val request = Request.Builder()
                .url("$baseURL/$name")
                .build()
            client.newCall(request).enqueue { result ->
                onComplete(result.mapCatching { response ->
                    val application = parseApplicationInformationResponse(response)
                    return@mapCatching application ?: throw DialError("Could not parse application information response")
                })
            }
        } catch (exception: Exception) {
            onComplete(Result.failure(exception))
        }
    }

    /**
     * Parses the response of an application information request.
     *
     * Below is an example of an application information response:
     *
     * <?xml​ ​version="1.0"​ ​encoding="UTF-8"?>
     * <service​ ​xmlns="urn:dial-multiscreen-org:schemas:dial"​ ​dialVer="1.7">
     *     <name>Name</name>
     *     <options allowStop="true"/>
     *     <state>running</state>
     *     <link rel="run" href="run"/>
     *     <additionalData>
     *         <ocast:X_OCAST_App2AppURL>wss://IP:4433/ocast</ocast:X_OCAST_App2AppURL>
     *         <ocast:X_OCAST_Version>1.0</ocast:X_OCAST_Version >
     *     </additionalData>
     * </service>
     *
     * @param response The OkHttp response.
     * @return The application described by the [response], or null if the response could not be parsed properly.
     */
    private fun parseApplicationInformationResponse(response: Response): DialApplication<OCastAdditionalData>? {
        var name: String? = null
        var isStopAllowed: Boolean? = null
        var state: DialApplication.State? = null
        var instanceURL: URL? = null
        var webSocketURL: URL? = null
        var version: String? = null

        try {
            if (response.isSuccessful) {
                val responseString = response.body()?.string()
                if (responseString != null) {
                    val documentBuilderFactory = DocumentBuilderFactory.newInstance()
                    val documentBuilder = documentBuilderFactory.newDocumentBuilder()
                    val document = documentBuilder.parse(InputSource(StringReader(responseString)))
                    val serviceNode = document.childNodes.item("service")
                    val serviceChildNodes = serviceNode?.childNodes?.asList().orEmpty()
                    for (node in serviceChildNodes) {
                        when (node.nodeName) {
                            "name" -> {
                                name = node.textContent
                                isStopAllowed = node.attributes.getNamedItem("allowStop").textContent == "true"
                            }
                            "state" -> state = parseApplicationState(node.textContent.orEmpty())
                            "link" -> instanceURL =
                                runCatching { URL(node.attributes.getNamedItem("href").textContent) }.getOrNull()
                            "additionalData" -> {
                                val additionalDataChildNodes = node.childNodes.asList()
                                for (otherNode in additionalDataChildNodes) {
                                    when (otherNode.nodeName) {
                                        "ocast:X_OCAST_App2AppURL" -> webSocketURL =
                                            runCatching { URL(otherNode.textContent) }.getOrNull()
                                        "ocast:X_OCAST_Version" -> version = otherNode.textContent
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (exception: Exception) {
            OCastLog.error("Parse application information response failed", exception)
        }

        return if (name != null && isStopAllowed != null && webSocketURL != null && version != null) {
            DialApplication(name, isStopAllowed, state, instanceURL, OCastAdditionalData(webSocketURL, version))
        } else {
            null
        }
    }

    /**
     * Parses the application state.
     *
     * @param string The string to parse the state from.
     * @return The state, or null if the state could not be parsed properly.
     */
    private fun parseApplicationState(string: String): DialApplication.State? {
        return when (string) {
            "running" -> DialApplication.State.Running
            "stopped" -> DialApplication.State.Stopped
            "hidden" -> DialApplication.State.Hidden
            else -> {
                string
                    .split("installable=")
                    .takeIf { it.size > 1 }
                    ?.elementAtOrNull(1)
                    ?.let {
                        try {
                            DialApplication.State.Installable(URL(it))
                        } catch (exception: java.lang.Exception) {
                            null
                        }
                    }
            }
        }
    }
}