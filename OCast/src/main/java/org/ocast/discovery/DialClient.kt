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

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.ocast.core.utils.OCastLog
import org.ocast.discovery.models.UpnpDevice
import org.ocast.discovery.utils.UpnpTools
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.IOException
import java.io.StringReader
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory

/**
 * This class handles the retrieval of OCast devices through the DIAL protocol.
 */
internal open class DialClient{

    /** The OkHttp client which will send device description requests. */
    private val client = OkHttpClient()

    /** A date format compliant with ISO 8601 dates. */
    private val iso8601DateFormat = SimpleDateFormat("E, d MMM yyyy HH:mm:ss z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("GMT")
    }

    /**
     * Retrieves a device.
     * This method launches a device description request according to the DIAL protocol.
     *
     * @param location The device location from the SSDP M-SEARCH response.
     * @param onComplete The lambda that will be called when the device is retrieved. The [UpnpDevice] parameter of the lambda is null if the device could not be retrieved.
     */
    open fun getDevice(location: String, onComplete: (UpnpDevice?) -> Unit) {
        val request = try {
            Request.Builder()
                .header("Date", iso8601DateFormat.format(Date()))
                .url(location)
                .build()
        } catch (exception: Exception) {
            OCastLog.error("Could not build device description request ", exception)
            null
        }

        if (request != null) {
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    onComplete(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    onComplete(parseDeviceDescriptionResponse(response))
                }
            })
        } else {
            onComplete(null)
        }
    }

    /**
     * Parses the response of a device description request.
     *
     * @param response The OkHttp response.
     * @return The device described by the [response], or null if the response could not be parsed properly.
     */
    private fun parseDeviceDescriptionResponse(response: Response): UpnpDevice? {
        var friendlyName: String? = null
        var manufacturer: String? = null
        var modelName: String? = null
        var udn: String? = null
        var applicationURL: URL? = null

        try {
            if (response.isSuccessful) {
                val headers = response.headers()
                val applicationURLString = headers["Application-DIAL-URL"] ?: headers["Application-URL"]
                applicationURL = applicationURLString?.let { URL(applicationURLString) }
                val responseString = response.body()?.string()
                if (responseString != null) {
                    val documentBuilderFactory = DocumentBuilderFactory.newInstance()
                    val documentBuilder = documentBuilderFactory.newDocumentBuilder()
                    val document = documentBuilder.parse(InputSource(StringReader(responseString)))
                    val rootNode = document.childNodes.item("root")
                    val deviceNode = rootNode?.childNodes?.item("device")
                    val deviceChildNodes = deviceNode?.childNodes?.asList().orEmpty()
                    for (node in deviceChildNodes) {
                        when (node.nodeName) {
                            "friendlyName" -> friendlyName = node.textContent
                            "manufacturer" -> manufacturer = node.textContent
                            "modelName" -> modelName = node.textContent
                            "UDN" -> udn = node.textContent
                        }
                    }
                }
            }
        } catch (exception: Exception) {
            OCastLog.error("Parse device description response failed", exception)
        }

        return if (udn != null && applicationURL != null && friendlyName != null && manufacturer != null && modelName != null) {
            UpnpDevice(
                UpnpTools.extractUuid(udn) ?: udn,
                applicationURL,
                friendlyName,
                manufacturer,
                modelName
            )
        } else {
            null
        }
    }
}

//region NodeList extensions

/**
 * Transforms a [NodeList] into a [List] of [Node]s.
 * This makes it easier to use with chaining and higher-order functions such as map and filter.
 *
 * @return The list of [Node]s.
 */
private fun NodeList.asList(): List<Node> {
    return (0 until length).map { item(it) }
}

/**
 * Returns the node with the given [name].
 *
 * @param name The name of the node to find.
 * @return The found node, or null if it does not exist.
 */
private fun NodeList.item(name: String): Node? {
    return asList().firstOrNull { it.nodeName == name }
}

//endregion