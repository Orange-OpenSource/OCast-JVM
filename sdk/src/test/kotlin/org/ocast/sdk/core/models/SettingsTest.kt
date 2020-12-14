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

package org.ocast.sdk.core.models

import org.junit.Assert.assertEquals
import org.junit.Test
import org.ocast.sdk.common.Assert.assertJsonEquals
import org.ocast.sdk.core.utils.JsonTools
import java.util.EnumSet

/**
 * Unit tests for model classes related to settings services.
 */
class SettingsTest {

    //region Commands

    @Test
    fun encodeGetUpdateStatusCommandSucceeds() {
        // Given
        val dataLayer = GetUpdateStatusCommandParams().build()

        // When
        val json = JsonTools.encode(dataLayer)

        // Then
        val expectedJson = """
            {
              "name": "getUpdateStatus", 
              "params": {
              }
            }
        """.trimIndent()

        assertJsonEquals(expectedJson, json)
    }

    @Test
    fun encodeGetDeviceIDCommandSucceeds() {
        // Given
        val dataLayer = GetDeviceIDCommandParams().build()

        // When
        val json = JsonTools.encode(dataLayer)

        // Then
        val expectedJson = """
            {
              "name": "getDeviceID", 
              "params": {
              }
            }
        """.trimIndent()

        assertJsonEquals(expectedJson, json)
    }

    @Test
    fun encodeSendKeyEventCommandSucceeds() {
        // Given
        val dataLayer = SendKeyEventCommandParams(
            "O",
            "KeyO",
            ctrl = true,
            alt = true,
            shift = true,
            meta = true,
            location = SendKeyEventCommandParams.DOMKeyLocation.STANDARD
        ).build()

        // When
        val json = JsonTools.encode(dataLayer)

        // Then
        val expectedJson = """
            {
              "name": "keyPressed", 
              "params": {
                "key": "O",
                "code": "KeyO",
                "ctrl": true,
                "alt": true,
                "shift": true,
                "meta": true,
                "location": 0
              }
            }
        """.trimIndent()

        assertJsonEquals(expectedJson, json)
    }

    @Test
    fun encodeSendMouseEventCommandSucceeds() {
        // Given
        val dataLayer = SendMouseEventCommandParams(
            1,
            2,
            EnumSet.of(SendMouseEventCommandParams.Button.PRIMARY, SendMouseEventCommandParams.Button.MIDDLE)
        ).build()

        // When
        val json = JsonTools.encode(dataLayer)

        // Then
        val expectedJson = """
            {
              "name": "mouseEvent", 
              "params": {
                "x": 1,
                "y": 2,
                "buttons": 5
              }
            }
        """.trimIndent()

        assertJsonEquals(expectedJson, json)
    }

    @Test
    fun encodeSendGamepadEventCommandSucceeds() {
        // Given
        val dataLayer = SendGamepadEventCommandParams(
            listOf(SendGamepadEventCommandParams.Axis(1.0, 2.0, SendGamepadEventCommandParams.Axis.Type.RIGHT_STICK_HORIZONTAL)),
            EnumSet.of(SendGamepadEventCommandParams.Button.RIGHT_CLUSTER_LEFT, SendGamepadEventCommandParams.Button.RIGHT_CLUSTER_TOP)
        ).build()

        // When
        val json = JsonTools.encode(dataLayer)

        // Then
        val expectedJson = """
            {
              "name": "gamepadEvent", 
              "params": {
                "axes": [
                  {
                    "num": 2,
                    "x": 1.0,
                    "y": 2.0
                  }
                ],
                "buttons": 12
              }
            }
        """.trimIndent()

        assertJsonEquals(expectedJson, json)
    }

    //endregion

    //region Replies and events

    @Test
    fun decodeDeviceIDSucceeds() {
        // Given
        val json = """
            {
              "id": "identifier"
            }
        """.trimIndent()

        // When
        val deviceID = JsonTools.decode<DeviceID>(json)

        // Then
        assertEquals("identifier", deviceID.id)
    }

    fun decodeUpdateStatusSucceeds() {
        // Given
        val json = """
            {
              "state": "newVersionFound",
              "version": "2.0.3",
              "progress": 85
            }
        """.trimIndent()

        // When
        val updateStatus = JsonTools.decode<UpdateStatus>(json)

        // Then
        assertEquals(UpdateStatus.State.NEW_VERSION_FOUND, updateStatus.state)
        assertEquals("2.0.3", updateStatus.version)
        assertEquals(85, updateStatus.progress)
    }

    //endregion
}
