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

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.util.EnumSet
import org.ocast.sdk.common.extensions.orElse
import org.ocast.sdk.core.models.Bitflag

/**
 * A singleton object that decodes and encodes JSON.
 */
object JsonTools {

    /** The Jackson object mapper. */
    @PublishedApi
    internal val objectMapper = jacksonObjectMapper()

    init {
        objectMapper.registerModule(KotlinModule())
        objectMapper.registerModule(JsonOrgModule())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
        objectMapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
    }

    /**
     * Decodes a JSON string to an object.
     *
     * @param T The type of the object to decode the JSON string to.
     * @param json The JSON string to decode.
     * @return The JSON object.
     * @throws Exception If an error occurs while decoding the JSON string.
     */
    @Throws(Exception::class)
    inline fun <reified T : Any> decode(json: String): T {
        return objectMapper.readValue(json)
    }

    /**
     * Decodes a JSON string to an object.
     *
     * @param T The type of the object to decode the JSON string to.
     * @param json The JSON string to decode.
     * @param clazz The class of the object to decode the JSON string to.
     * @return The decoded JSON object.
     * @throws Exception If an error occurs while decoding the JSON string.
     */
    @Throws(Exception::class)
    fun <T> decode(json: String, clazz: Class<T>): T {
        return objectMapper.readValue(json, clazz)
    }

    /**
     * Encodes a JSON object to a string.
     *
     * @param value The JSON object to encode.
     * @return The JSON string.
     * @throws Exception If an error occurs while encoding the JSON object.
     */
    @Throws(Exception::class)
    fun encode(value: Any): String {
        return objectMapper.writeValueAsString(value)
    }
}

/**
 * A Jackson [StdDeserializer] that deserializes a JSON object to a raw string.
 */
class RawJsonDeserializer : StdDeserializer<String>(String::class.java) {

    @Throws(Exception::class)
    override fun deserialize(parser: JsonParser?, context: DeserializationContext?): String {
        val mapper = parser?.codec as? ObjectMapper
        val node = mapper?.readTree<TreeNode>(parser)
        return mapper?.writeValueAsString(node).orEmpty()
    }
}

/**
 * A serializer that serializes an [EnumSet] to an integer which represents bitflags.
 *
 * @param T The type of the bitflags enum.
 * @constructor Creates an instance of [BitflagsSerializer].
 */
class BitflagsSerializer<T> : StdSerializer<EnumSet<T>>(EnumSet::class.java, false) where T : Enum<T>, T : Bitflag {

    @Throws(Exception::class)
    override fun serialize(value: EnumSet<T>?, gen: JsonGenerator?, serializers: SerializerProvider?) {
        val bitflags = value.orEmpty().sumBy { 1 shl it.bit }
        gen?.writeNumber(bitflags)
    }
}

/**
 * A deserializer that deserializes an integer which represents bitflags to an [EnumSet].
 *
 * @param T The type of the bitflags enum.
 * @property clazz The class of the bitflags enum.
 * @constructor Creates an instance of [BitflagsDeserializer].
 */
open class BitflagsDeserializer<T>(private val clazz: Class<T>) : StdDeserializer<EnumSet<T>>(EnumSet::class.java) where T : Enum<T>, T : Bitflag {

    @Throws(Exception::class)
    override fun deserialize(p: JsonParser?, ctxt: DeserializationContext?): EnumSet<T> {
        val bitflags = p?.valueAsInt.orElse { 0 }
        val enums = clazz.enumConstants.mapNotNull { enum ->
            val bitmask = 1 shl enum.bit
            enum.takeIf { bitmask and bitflags == bitmask }
        }

        return if (enums.isEmpty()) EnumSet.noneOf(clazz) else EnumSet.copyOf(enums)
    }
}
