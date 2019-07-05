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

package org.ocast.discovery.models

import org.ocast.common.utils.XMLParser
import org.ocast.discovery.UpnpClient
import java.net.URL

/**
 * This class represents an OCast device.
 *
 * @param uuid The Universally Unique IDentifier of the device.
 * @param applicationURL The URL of the Dial service.
 * @param friendlyName The friendly name of the device.
 * @param manufacturer The manufacturer of the device.
 * @param modelName The model name.
 */
class UpnpDevice(
    val uuid: String,
    val applicationURL: URL,
    val friendlyName: String,
    val manufacturer: String,
    val modelName: String
) {

    /**
     * A constructor with default values.
     * For internal use only.
     */
    internal constructor() : this("", URL("http://"), "", "", "")

    companion object {

        /**
         * Decodes an HTTP response to a [UpnpDevice].
         *
         * @param xml The XML body of the HTTP response.
         * @param headers The headers of the HTTP response.
         * @return The decoded device.
         * @throws Exception If an error occurs while decoding the device.
         */
        @Throws(Exception::class)
        fun decode(xml: String, headers: Map<String, String>): UpnpDevice {
            return Decoder.decode(xml, headers)
        }
    }

    /**
     * This object decodes instances of [UpnpDevice] from HTTP responses.
     */
    private object Decoder {

        const val APPLICATION_URL_HEADER_NAME = "Application-DIAL-URL"
        const val APPLICATION_URL_ALTERNATE_HEADER_NAME = "Application-URL"
        const val XML_ROOT_ELEMENT_NAME = "root"
        const val XML_DEVICE_ELEMENT_NAME = "device"
        const val XML_FRIENDLY_NAME_ELEMENT_NAME = "friendlyName"
        const val XML_MANUFACTURER_ELEMENT_NAME = "manufacturer"
        const val XML_MODEL_NAME_ELEMENT_NAME = "modelName"
        const val XML_UDN_ELEMENT_NAME = "UDN"

        /**
         * Decodes an HTTP response to a [UpnpDevice].
         * Below is an XML string example:
         * <?xml​ ​version="1.0"​ ​encoding="UTF-8"?>
         * <root xmlns="urn:schemas-upnp-org:device-1-0" xmlns:r="urn:restful-tv-org:schemas:upnp-dd">
         *     <specVersion>
         *         <major>1</major>
         *         <minor>0</minor>
         *     </specVersion>
         *     <device>
         *         <deviceType>urn:schemas-upnp-org:device:tvdevice:1</deviceType>
         *         <friendlyName>LaCléTV-32F7</friendlyName>
         *         <manufacturer>Innopia</manufacturer>
         *         <modelName>cléTV</modelName>
         *         <UDN>uuid:b042f955-9ae7-44a8-ba6c-0009743932f7</UDN>
         *     </device>
         * </root>
         *
         * @param xml The XML body of the HTTP response.
         * @param headers The headers of the HTTP response.
         * @return The decoded device.
         * @throws Exception If an error occurs while decoding the DIAL application.
         */
        @Throws(Exception::class)
        fun decode(xml: String, headers: Map<String, String>): UpnpDevice {
            val applicationURLString = headers[APPLICATION_URL_HEADER_NAME] ?: headers[APPLICATION_URL_ALTERNATE_HEADER_NAME]
            val applicationURL = URL(applicationURLString)
            val rootXMLElement = XMLParser.parse(xml)
            val deviceXMLElement = rootXMLElement[XML_ROOT_ELEMENT_NAME][XML_DEVICE_ELEMENT_NAME]
            val udn = deviceXMLElement[XML_UDN_ELEMENT_NAME].value
            val uuid = UpnpClient.extractUuid(udn) ?: udn
            val friendlyName = deviceXMLElement[XML_FRIENDLY_NAME_ELEMENT_NAME].value
            val manufacturer = deviceXMLElement[XML_MANUFACTURER_ELEMENT_NAME].value
            val modelName = deviceXMLElement[XML_MODEL_NAME_ELEMENT_NAME].value

            return UpnpDevice(uuid, applicationURL, friendlyName, manufacturer, modelName)
        }
    }
}
