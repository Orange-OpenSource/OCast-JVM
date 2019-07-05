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

package org.ocast.core.utils

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

object JsonTools {

    val objectMapper = jacksonObjectMapper()

    init {
        objectMapper.registerModule(KotlinModule())
        objectMapper.registerModule(JsonOrgModule())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
        objectMapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
    }

    @Throws(Exception::class)
    inline fun <reified T> decode(json: String): T {
        return objectMapper.readValue(json)
    }

    @Throws(Exception::class)
    fun <T> decode(json: String, clazz: Class<T>): T {
        return objectMapper.readValue(json, clazz)
    }
}

class RawJsonDeserializer : JsonDeserializer<String>() {

    @Throws(Exception::class)
    override fun deserialize(parser: JsonParser?, context: DeserializationContext?): String {
        val mapper = parser?.codec as? ObjectMapper?
        val node = mapper?.readTree<TreeNode>(parser)
        return mapper?.writeValueAsString(node).orEmpty()
    }
}
