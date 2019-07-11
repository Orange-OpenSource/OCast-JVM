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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Unit tests for the [SsdpMessage] class.
 * Please note that newlines in raw strings literals are encoded as "\n" and not "\r\n".
 */
@RunWith(Enclosed::class)
class SsdpMessageTest {

    class NotParameterized {

        //region M-Search request

        @Test
        fun createMulticastMSearchRequestSucceeds() {
            // Given
            val mSearchRequest = SsdpMSearchRequest(
                "239.255.255.250:1900",
                5,
                "urn:cast-ocast-org:service:cast:1"
            )

            // When
            val mSearchRequestString = String(mSearchRequest.data)

            // Then
            val expectedMSearchRequestString = "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 5\r\n" +
                "ST: urn:cast-ocast-org:service:cast:1"

            assertSsdpMessageEquals(expectedMSearchRequestString, mSearchRequestString)
        }

        @Test
        fun createUnicastMSearchRequestSucceeds() {
            // Given
            val mSearchRequest = SsdpMSearchRequest(
                "192.168.1.123:1900",
                null, // MX header is not present for unicast request
                "urn:cast-ocast-org:service:cast:1"
            )

            // When
            val mSearchRequestString = String(mSearchRequest.data)

            // Then
            val expectedMSearchRequestString = "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 192.168.1.123:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "ST: urn:cast-ocast-org:service:cast:1"

            assertSsdpMessageEquals(expectedMSearchRequestString, mSearchRequestString)
        }

        //endregion

        //region M-Search response

        @Test
        fun parseMSearchResponseSucceeds() {
            // Given
            val mSearchResponseString = "HTTP/1.1 200 OK\r\n" +
                "LOCATION: http://10.0.0.28:56790/device-desc.xml\r\n" +
                "CACHE-CONTROL: max-age=1800\r\n" +
                "EXT:\r\n" +
                "BOOTID.UPNP.ORG: 1\r\n" +
                "SERVER: Linux/4.9 UPnP/1.1 quick_ssdp/1.1\r\n" +
                "ST: urn:cast-ocast-org:service:cast:1\r\n" +
                "USN: uuid:b042f955-9ae7-44a8-ba6c-0009743932f7\r\n" +
                "WAKEUP: MAC=00:09:74:39:32:f7;Timeout=10"

            // When
            val mSearchResponse = SsdpMessage.fromData(mSearchResponseString.toByteArray()) as? SsdpMSearchResponse

            // Then
            assertEquals("http://10.0.0.28:56790/device-desc.xml", mSearchResponse?.location)
            assertEquals("Linux/4.9 UPnP/1.1 quick_ssdp/1.1", mSearchResponse?.server)
            assertEquals("uuid:b042f955-9ae7-44a8-ba6c-0009743932f7", mSearchResponse?.usn)
            assertEquals("urn:cast-ocast-org:service:cast:1", mSearchResponse?.searchTarget)
        }

        @Test
        fun parseMSearchResponseWithMissingMandatoryHeaderFails() {
            // Given
            val mSearchResponseString = "HTTP/1.1 200 OK\r\n" +
                "LOCATION: http://10.0.0.28:56790/device-desc.xml\r\n" +
                "CACHE-CONTROL: max-age=1800\r\n" +
                "EXT:\r\n" +
                "BOOTID.UPNP.ORG: 1\r\n" +
                "SERVER: Linux/4.9 UPnP/1.1 quick_ssdp/1.1\r\n" +
                "ST: urn:cast-ocast-org:service:cast:1\r\n" +
                // Mandatory USN header is missing
                "WAKEUP: MAC=00:09:74:39:32:f7;Timeout=10"

            // When
            val mSearchResponse = SsdpMessage.fromData(mSearchResponseString.toByteArray())

            // Then
            assertNull(mSearchResponse)
        }

        @Test
        fun parseMSearchResponseWithMisplacedStartLineFails() {
            // Given
            val mSearchResponseString = "LOCATION: http://10.0.0.28:56790/device-desc.xml\r\n" +
                "HTTP/1.1 200 OK\r\n" + // Start line is misplaced
                "CACHE-CONTROL: max-age=1800\r\n" +
                "EXT:\r\n" +
                "BOOTID.UPNP.ORG: 1\r\n" +
                "SERVER: Linux/4.9 UPnP/1.1 quick_ssdp/1.1\r\n" +
                "ST: urn:cast-ocast-org:service:cast:1\r\n" +
                "USN: uuid:b042f955-9ae7-44a8-ba6c-0009743932f7\r\n" +
                "WAKEUP: MAC=00:09:74:39:32:f7;Timeout=10"

            // When
            val mSearchResponse = SsdpMessage.fromData(mSearchResponseString.toByteArray())

            // Then
            assertNull(mSearchResponse)
        }

        @Test
        fun parseMSearchResponseWithLowercaseHeadersSucceeds() {
            // Given
            val mSearchResponseString = "HTTP/1.1 200 OK\r\n" +
                "location: http://10.0.0.28:56790/device-desc.xml\r\n" +
                "cache-control: max-age=1800\r\n" +
                "ext:\r\n" +
                "bootid.upnp.org: 1\r\n" +
                "server: Linux/4.9 UPnP/1.1 quick_ssdp/1.1\r\n" +
                "st: urn:cast-ocast-org:service:cast:1\r\n" +
                "usn: uuid:b042f955-9ae7-44a8-ba6c-0009743932f7\r\n" +
                "wakeup: MAC=00:09:74:39:32:f7;Timeout=10"

            // When
            val mSearchResponse = SsdpMessage.fromData(mSearchResponseString.toByteArray()) as? SsdpMSearchResponse

            // Then
            assertEquals("http://10.0.0.28:56790/device-desc.xml", mSearchResponse?.location)
            assertEquals("Linux/4.9 UPnP/1.1 quick_ssdp/1.1", mSearchResponse?.server)
            assertEquals("uuid:b042f955-9ae7-44a8-ba6c-0009743932f7", mSearchResponse?.usn)
            assertEquals("urn:cast-ocast-org:service:cast:1", mSearchResponse?.searchTarget)
        }

        //endregion

        @Test
        fun parseEmptyMessageFails() {
            // Given
            val messageString = ""

            // When
            val message = SsdpMessage.fromData(messageString.toByteArray())

            // Then
            assertNull(message)
        }

        /**
         * Asserts that two SSDP messages are equal.
         *
         * @param expected Expected SSDP message.
         * @param actual The SSDP message to check against [expected].
         */
        private fun assertSsdpMessageEquals(expected: String, actual: String) {
            val newlineRegex = "\\R".toRegex()
            val expectedLines = expected.split(newlineRegex).toMutableList()
            val actualLines = actual.split(newlineRegex).toMutableList()

            // Assert start lines are the same
            val expectedStartLine = expectedLines.removeAt(0)
            val actualStartLine = actualLines.removeAt(0)
            assertEquals(expectedStartLine, actualStartLine)

            // Assert other lines are the same, possibly in a different order
            assertEquals(expectedLines.sort(), actualLines.sort())
        }
    }

    @RunWith(Parameterized::class)
    class WithParameterizedNewlines(private val newline: String) {

        companion object {

            @JvmStatic
            @Parameterized.Parameters
            fun data() = listOf("\u000D\u000A", "\u000A", "\u000B", "\u000C", "\u000D", "\u0085", "\u2028", "\u2029")
        }

        @Test
        fun parseMSearchResponseWithParameterizedNewlinesSucceeds() {
            // Given
            val mSearchResponseString = "HTTP/1.1 200 OK$newline" +
                    "LOCATION: http://10.0.0.28:56790/device-desc.xml$newline" +
                    "CACHE-CONTROL: max-age=1800$newline" +
                    "EXT:$newline" +
                    "BOOTID.UPNP.ORG: 1$newline" +
                    "SERVER: Linux/4.9 UPnP/1.1 quick_ssdp/1.1$newline" +
                    "ST: urn:cast-ocast-org:service:cast:1$newline" +
                    "USN: uuid:b042f955-9ae7-44a8-ba6c-0009743932f7$newline" +
                    "WAKEUP: MAC=00:09:74:39:32:f7;Timeout=10"

            // When
            val mSearchResponse = SsdpMessage.fromData(mSearchResponseString.toByteArray()) as? SsdpMSearchResponse

            // Then
            assertEquals("http://10.0.0.28:56790/device-desc.xml", mSearchResponse?.location)
            assertEquals("Linux/4.9 UPnP/1.1 quick_ssdp/1.1", mSearchResponse?.server)
            assertEquals("uuid:b042f955-9ae7-44a8-ba6c-0009743932f7", mSearchResponse?.usn)
            assertEquals("urn:cast-ocast-org:service:cast:1", mSearchResponse?.searchTarget)
        }
    }
}
