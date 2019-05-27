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

package org.ocast.discovery.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the [UpnpTools] object.
 */
class UpnpToolsTest {

    @Test
    fun extractUuidFromUSNSucceeds() {
        // Given
        val usn = "uuid:device-UUID"

        // When
        val uuid = UpnpTools.extractUuid(usn)

        // Then
        assertEquals("device-UUID", uuid)
    }

    @Test
    fun extractUuidFromRootDeviceUSNSucceeds() {
        // Given
        val usn = "uuid:device-UUID::upnp:rootdevice"

        // When
        val uuid = UpnpTools.extractUuid(usn)

        // Then
        assertEquals("device-UUID", uuid)
    }

    @Test
    fun extractUuidFromDeviceTypeUSNSucceeds() {
        // Given
        val usn = "uuid:device-UUID::urn:domain-name:device:deviceType:ver"

        // When
        val uuid = UpnpTools.extractUuid(usn)

        // Then
        assertEquals("device-UUID", uuid)
    }

    @Test
    fun extractUuidFromServiceTypeUSNSucceeds() {
        // Given
        val usn = "uuid:device-UUID::urn:domain-name:service:serviceType:ver"

        // When
        val uuid = UpnpTools.extractUuid(usn)

        // Then
        assertEquals("device-UUID", uuid)
    }

    @Test
    fun extractUuidFromMalformedUSNFails() {
        // Given
        val usn = "id:device-UUID"

        // When
        val uuid = UpnpTools.extractUuid(usn)

        // Then
        assertNull(uuid)
    }
}