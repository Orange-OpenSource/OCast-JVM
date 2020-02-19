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

package org.ocast.sdk.core.utils

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import java.util.EnumSet
import junit.framework.TestCase.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ocast.sdk.core.models.Bitflag

/**
 * Unit tests for the JsonTools object.
 */
class JsonToolsTest {

    //region Deserialization

    class TestEntity(
        @JsonProperty("foo") val foo: String,
        @JsonProperty("bar") val bar: Int,
        @JsonProperty("baz") val baz: String?,
        @JsonProperty("qux") val qux: Int?
    )

    @Test
    fun deserializeJsonWithUnknownValueSucceeds() {
        // Given
        val json = """
            {
              "foo": "string",
              "bar": 0,
              "baz": null,
              "qux": null,
              "unknownName": "unknownValue"
            }
        """.trimIndent()

        // When
        val entity = JsonTools.decode(json, TestEntity::class.java)

        // Then
        assertEquals("string", entity.foo)
        assertEquals(0, entity.bar)
        assertNull(entity.baz)
        assertNull(entity.qux)
    }

    @Test(expected = MissingKotlinParameterException::class)
    fun deserializeJsonWithMissingValueFails() {
        // Given
        val json = """
            {
              "bar": 0,
              "baz": null,
              "qux": null
            }
        """.trimIndent()

        // When
        JsonTools.decode(json, TestEntity::class.java)

        // Then
        // An exception is thrown
    }

    @Test(expected = MismatchedInputException::class)
    fun deserializeJsonWithMissingPrimitiveValueFails() {
        // Given
        val json = """
            {
              "foo": "string",
              "baz": null,
              "qux": null
            }
        """.trimIndent()

        // When
        JsonTools.decode(json, TestEntity::class.java)

        // Then
        // An exception is thrown
    }

    @Test(expected = MissingKotlinParameterException::class)
    fun deserializeJsonWithNullValueFails() {
        // Given
        val json = """
            {
              "foo": null,
              "bar": 0,
              "baz": null,
              "qux": null
            }
        """.trimIndent()

        // When
        JsonTools.decode(json, TestEntity::class.java)

        // Then
        // An exception is thrown
    }

    @Test(expected = MismatchedInputException::class)
    fun deserializeJsonWithNullPrimitiveValueFails() {
        // Given
        val json = """
            {
              "foo": "string",
              "bar": null,
              "baz": null,
              "qux": null
            }
        """.trimIndent()

        // When
        JsonTools.decode(json, TestEntity::class.java)

        // Then
        // An exception is thrown
    }

    //endregion

    //region RawJsonDeserializer

    /**
     * This class represents a container of a raw JSON string.
     */
    class RawJsonContainer(
        /** The raw JSON string */
        @JsonDeserialize(using = RawJsonDeserializer::class)
        val rawJson: String
    )

    @Test
    fun deserializeRawJsonSucceeds() {
        // Given
        val rawJson = """
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

        val containerString = "{ \"rawJson\": $rawJson }"

        // When
        val containerObject = JsonTools.decode<RawJsonContainer>(containerString)

        // Then
        assertEquals(rawJson, containerObject.rawJson)
    }

    //endregion

    //region Bitflags

    /**
     * This class represents a container of bitflags.
     */
    class BitflagsContainer(
        /** The bitflags. */
        @JsonSerialize(using = BitflagsSerializer::class)
        @JsonDeserialize(using = TestBitflagsDeserializer::class)
        val bitflags: EnumSet<TestBitflag>
    )

    /**
     * This class is a [BitflagsDeserializer] of [TestBitflag].
     *
     * The [JsonDeserialize] annotation only takes a class for its `using` parameter.
     * However the [BitflagsDeserializer] constructor has a parameter.
     * Thus we need to define a subclass of [BitflagsDeserializer] with the desired parameter and use this subclass with [JsonDeserialize].
     */
    class TestBitflagsDeserializer : BitflagsDeserializer<TestBitflag>(TestBitflag::class.java)

    /**
     * This class is an enum of bitflags for tests.
     */
    enum class TestBitflag(override val bit: Int) : Bitflag {

        BITFLAG_0(0),
        BITFLAG_1(1),
        BITFLAG_2(2)
    }

    @Test
    fun serializeNoBitflagSucceeds() {
        // Given
        val container = BitflagsContainer(EnumSet.noneOf(TestBitflag::class.java))

        // When
        val bitflags = JsonTools.encode(container)

        // Then
        assertEquals("{\"bitflags\":0}", bitflags)
    }

    @Test
    fun serializeSingleBitflagSucceeds() {
        // Given
        val container = BitflagsContainer(EnumSet.of(TestBitflag.BITFLAG_1))

        // When
        val bitflags = JsonTools.encode(container)

        // Then
        assertEquals("{\"bitflags\":2}", bitflags)
    }

    @Test
    fun serializeAllBitflagsSucceeds() {
        // Given
        val container = BitflagsContainer(EnumSet.allOf(TestBitflag::class.java))

        // When
        val bitflags = JsonTools.encode(container)

        // Then
        assertEquals("{\"bitflags\":7}", bitflags)
    }

    @Test
    fun deserializeNoBitflagSucceeds() {
        // Given
        val containerString = "{\"bitflags\": 0}"

        // When
        val containerObject = JsonTools.decode<BitflagsContainer>(containerString)

        // Then
        assertEquals(EnumSet.noneOf(TestBitflag::class.java), containerObject.bitflags)
    }

    @Test
    fun deserializeSingleBitflagSucceeds() {
        // Given
        val containerString = "{\"bitflags\": 2}"

        // When
        val containerObject = JsonTools.decode<BitflagsContainer>(containerString)

        // Then
        assertEquals(EnumSet.of(TestBitflag.BITFLAG_1), containerObject.bitflags)
    }

    @Test
    fun deserializeAllBitflagsSucceeds() {
        // Given
        val containerString = "{\"bitflags\": 7}"

        // When
        val containerObject = JsonTools.decode<BitflagsContainer>(containerString)

        // Then
        assertEquals(EnumSet.allOf(TestBitflag::class.java), containerObject.bitflags)
    }

    //endregion
}
