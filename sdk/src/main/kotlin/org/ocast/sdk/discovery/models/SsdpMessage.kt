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

package org.ocast.sdk.discovery.models

/**
 * This class is the base class for all SSDP messages.
 *
 * @property startLine The start line of the message.
 * @constructor Creates an instance of [SsdpMessage].
 */
internal sealed class SsdpMessage(private val startLine: StartLine) {

    /**
     * An enum which represents the start line of an SSDP message.
     *
     * @param value The value of the start line.
     */
    protected enum class StartLine(val value: String) {

        /** The start line for an M-SEARCH request. */
        M_SEARCH_REQUEST("M-SEARCH * HTTP/1.1"),

        /** The start line for an M-SEARCH response. */
        M_SEARCH_RESPONSE("HTTP/1.1 200 OK")
    }

    /**
     * An enum which represents the headers of an SSDP message.
     */
    protected enum class Header {

        /** The destination host address and port. */
        HOST,

        /**
         * Required by the HTTP Extension Framework.
         *
         * Value shall be `ssdp:discover` enclosed in double quotes.
         */
        MAN,

        /** The maximum wait time in seconds. */
        MX,

        /**
         * The search target.
         *
         * Describes the devices or the services being searched.
         */
        ST,

        /** The URL to the UPnP description of the device. */
        LOCATION,

        /** A header which concatenates various info about the device, such as the OS name and the product name. */
        SERVER,

        /** The unique service name which identifies a device or a service. */
        USN;
    }

    /**
     * The companion object.
     */
    companion object {

        /** Carriage Return Line Feed characters. */
        private const val CRLF = "\r\n"

        /** The list of all newline characters. */
        private val newlineCharacters = arrayOf("\u000D\u000A", "\u000A", "\u000B", "\u000C", "\u000D", "\u0085", "\u2028", "\u2029")

        /**
         * Converts data into an SSDP message.
         *
         * @param data The data to convert.
         * @return The SSDP message or `null` if the data could not be converted.
         */
        fun fromData(data: ByteArray): SsdpMessage? {
            var message: SsdpMessage? = null
            val lines = String(data)
                .split(*newlineCharacters) // Do not use a regex with the \R linebreak matcher because it is not available on some platforms
                .filter { it.isNotBlank() }
                .map { it.trim() }
                .toMutableList()

            if (lines.isNotEmpty()) {
                // Retrieve start line and headers
                val startLine = lines.removeAt(0)
                val headers = lines.associate { line ->
                    val header = line.split(":", limit = 2)
                    val key = Header.values().firstOrNull { it.name.equals(header[0].trim(), true) }
                    val value = header.elementAtOrNull(1)?.trim()

                    return@associate Pair(key, value)
                }

                when (startLine) {
                    StartLine.M_SEARCH_REQUEST.value -> {
                        val host = headers[Header.HOST]
                        val maxTime = headers[Header.MX]?.toIntOrNull()
                        val searchTarget = headers[Header.ST]
                        if (host != null && searchTarget != null) {
                            message = SsdpMSearchRequest(host, maxTime, searchTarget)
                        }
                    }
                    StartLine.M_SEARCH_RESPONSE.value -> {
                        val location = headers[Header.LOCATION]
                        val server = headers[Header.SERVER]
                        val usn = headers[Header.USN]
                        val searchTarget = headers[Header.ST]
                        if (location != null && server != null && usn != null && searchTarget != null) {
                            message = SsdpMSearchResponse(location, server, usn, searchTarget)
                        }
                    }
                    else -> {
                    }
                }
            }

            return message
        }
    }

    /** The headers of the message. */
    protected abstract val headers: HashMap<Header, String>

    /** The raw data which represents the message. */
    val data: ByteArray
        get() {
            return listOf(startLine.value)
                .plus(headers.map { "${it.key.name}: ${it.value}" })
                .joinToString(CRLF)
                .toByteArray()
        }
}

/**
 * This class represents an M-SEARCH request.
 *
 * @property host The destination host address and port.
 * @property maxTime The maximum wait time in seconds, or `null for a unicast request.
 * @property searchTarget The search target.
 * @constructor Creates an instance of [SsdpMSearchRequest].
 */
internal data class SsdpMSearchRequest(
    val host: String,
    val maxTime: Int?,
    val searchTarget: String
) : SsdpMessage(StartLine.M_SEARCH_REQUEST) {

    override val headers: HashMap<Header, String> by lazy {
        hashMapOf(
            Header.HOST to host,
            Header.MAN to "\"ssdp:discover\"",
            Header.ST to searchTarget
        ).apply {
            if (maxTime != null) {
                put(Header.MX, maxTime.toString())
            }
        }
    }
}

/**
 * This class represents an M-SEARCH response.
 *
 * @property location The URL to the UPnP description of the device.
 * @property server The OS name /version and the product name / version.
 * @property usn The unique ser`vice name.
 * @property searchTarget The search target.
 * @constructor Creates an instance of [SsdpMSearchRequest].
 */
internal data class SsdpMSearchResponse(
    val location: String,
    val server: String,
    val usn: String,
    val searchTarget: String
) : SsdpMessage(StartLine.M_SEARCH_RESPONSE) {

    override val headers: HashMap<Header, String> by lazy {
        hashMapOf(
            Header.LOCATION to location,
            Header.SERVER to server,
            Header.USN to usn,
            Header.ST to searchTarget
        )
    }
}
