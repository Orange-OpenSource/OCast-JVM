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

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ocast.sdk.common.Assert.assertJsonEquals
import org.ocast.sdk.core.utils.JsonTools

/**
 * Unit tests for the OCast model.
 */
class OCastTest {

    //region Device layer

    @Test
    fun encodeOCastCommandDeviceLayerSucceeds() {
        // Given
        val dataLayer = OCastDataLayer(
            "nameValue",
            mapOf("paramName" to "paramValue"),
            JSONObject(mapOf("optionName" to "optionValue"))
        )
        val deviceLayer = OCastCommandDeviceLayer(
            "source",
            "destination",
            1L,
            OCastApplicationLayer("org.ocast.service", dataLayer)
        )

        // When
        val json = JsonTools.encode(deviceLayer)

        // Then
        val expectedJson = """
            {
              "src": "source",
              "dst": "destination",
              "type": "command",
              "id": 1,
              "message": {
                "service": "org.ocast.service",
                "data": {
                  "name": "nameValue",
                  "params": {
                    "paramName": "paramValue"
                  },
                  "options": {
                    "optionName": "optionValue"
                  }
                }
              }
            }
        """.trimIndent()

        assertJsonEquals(expectedJson, json)
    }

    @Test
    fun decodeOCastRawDeviceLayerSucceeds() {
        // Given
        val dataJson = """
            {
              "name": "nameValue",
              "params": {
                "paramName": "paramValue"
              }
            }
        """.trimIndent()

        val json = """
            {
              "dst": "destination",
              "src": "source",
              "type": "event",
              "status": "OK",
              "id": 666,
              "message": {
                "service": "org.ocast.service",
                "data": $dataJson
              }
            }
        """.trimIndent()

        // When
        val deviceLayer = JsonTools.decode<OCastRawDeviceLayer>(json)

        // Then
        assertEquals("destination", deviceLayer.destination)
        assertEquals("source", deviceLayer.source)
        assertEquals(OCastRawDeviceLayer.Type.EVENT, deviceLayer.type)
        assertEquals(OCastRawDeviceLayer.Status.OK, deviceLayer.status)
        assertEquals(666L, deviceLayer.identifier)
        assertEquals("org.ocast.service", deviceLayer.message.service)
        assertJsonEquals(dataJson, deviceLayer.message.data)
    }

    //endregion

    //region Data layer

    class TestCommandParams(val paramName: String) : OCastCommandParams("commandName")
    class TestReplyParams(val replyName: String)

    @Test
    fun decodeOCastDataLayerSucceeds() {
        // Given
        val json = """
            {
              "name": "nameValue",
              "params": {
                "replyName": "replyValue"
              }
            }
        """.trimIndent()

        // When
        val dataLayer = JsonTools.decode<OCastDataLayer<TestReplyParams>>(json)

        // Then
        assertEquals("nameValue", dataLayer.name)
        assertEquals("replyValue", dataLayer.params.replyName)
    }

    @Test
    fun decodeOCastRawDataLayerSucceeds() {
        // Given
        val paramsJson = """
            {
              "replyName": "replyValue"
            }
        """.trimIndent()

        val json = """
            {
              "name": "dataValue",
              "params": $paramsJson 
            }
        """.trimIndent()

        // When
        val dataLayer = JsonTools.decode<OCastRawDataLayer>(json)

        // Then
        assertEquals("dataValue", dataLayer.name)
        assertJsonEquals(paramsJson, dataLayer.params)
    }

    @Test
    fun buildOCastDataLayerFromOCastCommandParamsSucceeds() {
        // Given
        val options = JSONObject(mapOf("optionName" to "optionValue"))

        // When
        @Suppress("UNCHECKED_CAST")
        val dataLayer = TestCommandParams("paramValue")
            .options(options)
            .build() as OCastDataLayer<TestCommandParams>

        // Then
        assertEquals("commandName", dataLayer.name)
        assertEquals("paramValue", dataLayer.params.paramName)
        assertEquals(options, dataLayer.options)
    }

    //endregion
}
