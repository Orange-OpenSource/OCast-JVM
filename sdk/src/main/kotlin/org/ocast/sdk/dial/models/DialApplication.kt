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

package org.ocast.sdk.dial.models

import java.net.MalformedURLException
import java.net.URI
import java.net.URL
import org.ocast.sdk.common.utils.XMLParser

/**
 * This class represents a DIAL application.
 *
 * @param name The application name.
 * @param isStopAllowed Indicates if the application supports the stop operation.
 * @param state The current state of the application.
 * @param instancePath The application instance path. Used to build the instance URL.
 * @param additionalData The additional data for this application.
 */
internal data class DialApplication(
    val name: String,
    val isStopAllowed: Boolean,
    val state: State? = null,
    private val instancePath: String? = null,
    val additionalData: OCastAdditionalData
) {

    companion object {

        /**
         * Decodes an XML string to a [DialApplication].
         *
         * @param xml The XML string to decode.
         * @return The decoded application.
         * @throws Exception If an error occurs while decoding the DIAL application.
         */
        @Throws(Exception::class)
        fun decode(xml: String): DialApplication {
            return Decoder.decode(xml)
        }
    }

    /**
     * Generates and returns the instance URL of the application.
     *
     * @param baseURL The base URL of the DIAL server.
     * @return The instance URL, or `null` if the URL could not be generated.
     */
    fun getInstanceURL(baseURL: URL): URL? {
        return runCatching { URL(instancePath) }
            .recoverCatching { URL("$baseURL/$name/${instancePath ?: "run"}") }
            .getOrNull()
    }

    /**
     * This class represents a state of a DIAL application.
     */
    sealed class State {

        /**
         * Indicates​ ​that​ ​the​ ​application​ ​is​ ​installed​ ​and​ ​either starting​ ​or​ ​running.
         */
        object Running : State()

        /**
         * ​Indicates​ ​that​ ​the​ ​application​ ​is​ ​installed​ ​and​ ​not running.
         */
        object Stopped : State()

        /**
         * ​Indicates​ ​that​ ​the​ ​application​ ​is​ ​not installed​ ​but​ ​is​ ​available​ ​for​ ​installation.​
         *
         * @param url The URL that can be used to install the application.
         */
        data class Installable(val url: URL) : State()

        /**
         * ​Indicates​ ​that​ ​the​ ​application​ ​is​ ​running​ ​but​ ​is​ ​not currently​ ​visible​ ​to​ ​the​ ​user.​
         */
        object Hidden : State()
    }

    /**
     * This object decodes instances of [DialApplication] from XML strings.
     */
    private object Decoder {

        const val XML_SERVICE_ELEMENT_NAME = "service"
        const val XML_NAME_ELEMENT_NAME = "name"
        const val XML_OPTIONS_ELEMENT_NAME = "options"
        const val XML_STATE_ELEMENT_NAME = "state"
        const val XML_LINK_ELEMENT_NAME = "link"
        const val XML_HREF_ATTRIBUTE_NAME = "href"
        const val XML_ADDITIONAL_DATA_ELEMENT_NAME = "additionalData"
        const val XML_ALLOW_STOP_ATTRIBUTE_NAME = "allowStop"
        const val XML_OCAST_APP_2_APP_URL_ELEMENT_NAME = "ocast:X_OCAST_App2AppURL"
        const val XML_OCAST_VERSION_ELEMENT_NAME = "ocast:X_OCAST_Version"
        const val XML_TRUE_TEXT_VALUE = "true"
        const val XML_RUNNING_STATE_TEXT_VALUE = "running"
        const val XML_STOPPED_STATE_TEXT_VALUE = "stopped"
        const val XML_HIDDEN_STATE_TEXT_VALUE = "hidden"
        const val XML_INSTALLABLE_STATE_TEXT_VALUE = "installable"

        /**
         * Decodes an XML string to a [DialApplication].
         * Below is an XML string example:
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
         * @param xml The XML string to decode.
         * @return The decoded application.
         * @throws Exception If an error occurs while decoding the DIAL application.
         */
        @Throws(Exception::class)
        fun decode(xml: String): DialApplication {
            val rootXMLElement = XMLParser.parse(xml)
            val serviceXMLElement = rootXMLElement[XML_SERVICE_ELEMENT_NAME]
            val name = serviceXMLElement[XML_NAME_ELEMENT_NAME].value
            val isStopAllowed = serviceXMLElement[XML_OPTIONS_ELEMENT_NAME].attributes[XML_ALLOW_STOP_ATTRIBUTE_NAME] == XML_TRUE_TEXT_VALUE
            val state = decodeState(serviceXMLElement[XML_STATE_ELEMENT_NAME].value)
            val instancePath = serviceXMLElement
                .getOrNull(XML_LINK_ELEMENT_NAME)
                ?.attributes
                ?.get(XML_HREF_ATTRIBUTE_NAME)
            val additionalDataXMLElement = serviceXMLElement[XML_ADDITIONAL_DATA_ELEMENT_NAME]
            val webSocketURL = additionalDataXMLElement
                .getOrNull(XML_OCAST_APP_2_APP_URL_ELEMENT_NAME)
                ?.run {
                    runCatching { URI(value) }.getOrNull()
                }
            val version = additionalDataXMLElement
                .getOrNull(XML_OCAST_VERSION_ELEMENT_NAME)
                ?.value

            return DialApplication(
                name,
                isStopAllowed,
                state,
                instancePath,
                OCastAdditionalData(webSocketURL, version)
            )
        }

        /**
         * Decodes a raw state to a [State].
         *
         * @param rawState The raw state to decode.
         * @return The decoded state.
         * @throws DialError If the state is unknown.
         * @throws MalformedURLException If state is [State.Installable] but URL is malformed.
         */
        @Throws(DialError::class, MalformedURLException::class)
        private fun decodeState(rawState: String): State {
            return when (rawState) {
                XML_RUNNING_STATE_TEXT_VALUE -> State.Running
                XML_STOPPED_STATE_TEXT_VALUE -> State.Stopped
                XML_HIDDEN_STATE_TEXT_VALUE -> State.Hidden
                else -> {
                    val installationUrlString = try {
                        rawState.split("$XML_INSTALLABLE_STATE_TEXT_VALUE=").elementAt(1)
                    } catch (exception: Exception) {
                        throw DialError("Unknown DIAL application state")
                    }
                    State.Installable(URL(installationUrlString))
                }
            }
        }
    }
}

/**
 * This class represents the additional data related to OCast when receiving DIAL application information response.
 *
 * @param webSocketURL The OCast web socket URL.
 * @param version The OCast version.
 */
internal data class OCastAdditionalData(
    val webSocketURL: URI? = null,
    val version: String? = null
)
