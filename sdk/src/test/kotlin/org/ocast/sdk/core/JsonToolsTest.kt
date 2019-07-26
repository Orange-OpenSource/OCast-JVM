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

package org.ocast.sdk.core

import com.fasterxml.jackson.databind.module.SimpleModule
import java.util.EnumSet
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ocast.sdk.core.models.SendMouseEventCommandParams
import org.ocast.sdk.core.utils.BitflagsDeserializer
import org.ocast.sdk.core.utils.BitflagsSerializer
import org.ocast.sdk.core.utils.JsonTools
import org.ocast.sdk.core.utils.RawJsonDeserializer

/**
 * Unit tests for the JsonTools object.
 */
class JsonToolsTest {

    @Before
    fun setUp() {
        val module = SimpleModule()
        module.addSerializer(BitflagsSerializer<SendMouseEventCommandParams.Button>())
        module.addDeserializer(EnumSet::class.java, BitflagsDeserializer(SendMouseEventCommandParams.Button::class.java))
        module.addDeserializer(String::class.java, RawJsonDeserializer())
        JsonTools.objectMapper.registerModule(module)
    }

    @Test
    fun deserializeRawStringSucceeds() {
        // Given
        val jsonString = """
            {
              "foo": "string",
              "bar": 1,
              "baz": {
              },
              "qux": [
              ],
              "quux": true,
              "corge": false,
              "grault": null
            }
        """.trimIndent()
            .replace("\\s|\\R".toRegex(), "")

        // When
        val decodedJsonString = JsonTools.decode<String>(jsonString)

        // Then
        assertEquals(jsonString, decodedJsonString)
    }

    @Test
    fun serializeNoBitflagSucceeds() {
        // Given
        val buttons = EnumSet.noneOf(SendMouseEventCommandParams.Button::class.java)

        // When
        val bitflags = JsonTools.encode(buttons).toInt()

        // Then
        assertEquals(0, bitflags)
    }

    @Test
    fun serializeSingleBitflagSucceeds() {
        // Given
        val buttons = EnumSet.of(SendMouseEventCommandParams.Button.MIDDLE)

        // When
        val bitflags = JsonTools.encode(buttons).toInt()

        // Then
        assertEquals(4, bitflags)
    }

    @Test
    fun serializeAllBitflagsSucceeds() {
        // Given
        val buttons = EnumSet.allOf(SendMouseEventCommandParams.Button::class.java)

        // When
        val bitflags = JsonTools.encode(buttons).toInt()

        // Then
        assertEquals(7, bitflags)
    }

    @Test
    fun deserializeNoBitflagSucceeds() {
        // Given
        val bitflags = 0

        // When
        val buttons = JsonTools.decode<EnumSet<SendMouseEventCommandParams.Button>>(bitflags.toString())

        // Then
        assertEquals(EnumSet.noneOf(SendMouseEventCommandParams.Button::class.java), buttons)
    }

    @Test
    fun deserializeSingleBitflagSucceeds() {
        // Given
        val bitflags = 4

        // When
        val buttons = JsonTools.decode<EnumSet<SendMouseEventCommandParams.Button>>(bitflags.toString())

        // Then
        assertEquals(EnumSet.of(SendMouseEventCommandParams.Button.MIDDLE), buttons)
    }

    @Test
    fun deserializeAllBitflagsSucceeds() {
        // Given
        val bitflags = 7

        // When
        val buttons = JsonTools.decode<EnumSet<SendMouseEventCommandParams.Button>>(bitflags.toString())

        // Then
        assertEquals(EnumSet.allOf(SendMouseEventCommandParams.Button::class.java), buttons)
    }
}
