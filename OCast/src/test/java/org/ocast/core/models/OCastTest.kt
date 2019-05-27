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

package org.ocast.core.models

import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.ocast.core.ReferenceDevice

/**
 * Unit tests for the OCast model.
 */
class OCastTest {

    @Test
    fun decodeWebAppConnectedStatusEventSucceeds() {
        // Given
        val data = """
            {
              "dst": "89cf41b8-ef40-48d9-99c3-2a1951abcde5",
              "src": "browser",
              "type": "event",
              "status": "ok",
              "id": 666,
              "message": {
                "service": "org.ocast.webapp",
                "data": {
                  "name": "connectedStatus",
                  "params": {
                    "status": "connected"
                  }
                }
              }
            }
        """.trimIndent()

        // When
        val deviceLayer = OCastRawDeviceLayer.decode(data)
        val ocastData = OCastRawDataLayer.decode(deviceLayer.message.data)
        val webAppConnectedStatus = OCastDataLayer.decode<WebAppConnectedStatus>(ocastData.params)

        // Then
        assertEquals(OCastRawDeviceLayer.Status.OK, deviceLayer.status)
        assertEquals("89cf41b8-ef40-48d9-99c3-2a1951abcde5", deviceLayer.destination)
        assertEquals(ReferenceDevice.DOMAIN_BROWSER, deviceLayer.source)
        assertEquals(OCastRawDeviceLayer.Type.EVENT, deviceLayer.type)
        assertEquals(666L, deviceLayer.identifier)
        assertEquals(ReferenceDevice.SERVICE_APPLICATION, deviceLayer.message.service)

        assertEquals("connectedStatus", ocastData.name)

        assertEquals(WebAppStatus.CONNECTED, webAppConnectedStatus.status)
    }

    @Test
    fun decodeMetadataChangedEventSucceeds() {

        // Given
        val data = """
            {
              "dst": "89cf41b8-ef40-48d9-99c3-2a1951abcde5",
              "src": "browser",
              "type": "event",
              "status": "OK",
              "id": 666,
              "message": {
                "service": "org.ocast.media",
                "data": {
                  "name": "metadataChanged",
                  "params": {
                    "title": "La_cité_de_la_peur",
                    "subtitle": "Un_film_de_les_nuls",
                    "mediaType": "video",
                    "textTracks": [],
                    "audioTracks": [
                        {
                            "type": "audio",
                            "language": "de",
                            "label": "Audio DE",
                            "enable": true,
                            "trackId": "id123"
                        }
                    ]
                  }
                }
              }
            }
        """.trimIndent()

        // When
        val deviceLayer = OCastRawDeviceLayer.decode(data)
        val ocastData = OCastRawDataLayer.decode(deviceLayer.message.data)
        val metadataChanged = OCastDataLayer.decode<MetadataChangedEvent>(ocastData.params)

        // Then
        assertEquals(OCastRawDeviceLayer.Status.OK, deviceLayer.status)
        assertEquals("89cf41b8-ef40-48d9-99c3-2a1951abcde5", deviceLayer.destination)
        assertEquals(ReferenceDevice.DOMAIN_BROWSER, deviceLayer.source)
        assertEquals(OCastRawDeviceLayer.Type.EVENT, deviceLayer.type)
        assertEquals(666L, deviceLayer.identifier)
        assertEquals(ReferenceDevice.SERVICE_MEDIA, deviceLayer.message.service)

        assertEquals("metadataChanged", ocastData.name)

        assertNull(metadataChanged.logo)
        assertEquals("La_cité_de_la_peur", metadataChanged.title)
        assertEquals("Un_film_de_les_nuls", metadataChanged.subtitle)
        assertEquals(Media.Type.VIDEO, metadataChanged.mediaType)

        assertEquals(1, metadataChanged.audioTracks?.size)
        val audioTrack = metadataChanged.audioTracks?.elementAtOrNull(0)
        assertEquals("id123", audioTrack?.id)
        assertEquals("de", audioTrack?.language)
        assertEquals("Audio DE", audioTrack?.label)
        assertEquals(true, audioTrack?.isEnabled)

        assertEquals(0, metadataChanged.textTracks?.size)

        assertNull(metadataChanged.videoTracks)
    }

    @Test
    fun decodePlaybackStatusReplySucceeds() {

        // Given
        val data = """
            {
              "dst": "89cf41b8-ef40-48d9-99c3-2a1951abcde5",
              "src": "browser",
              "type": "reply",
              "status": "OK",
              "id": 666,
              "message": {
                "service": "org.ocast.media",
                "data": {
                  "name": "playbackStatus",
                  "params": {
                    "code": 0,
                    "position": 1234.56,
                    "duration": 5678.9,
                    "state": 2,
                    "volume": 0.45,
                    "mute": true
                  }
                }
              }
            }
        """.trimIndent()

        // When
        val deviceLayer = OCastRawDeviceLayer.decode(data)
        val ocastData = OCastRawDataLayer.decode(deviceLayer.message.data)
        val replyData = OCastDataLayer.decode<OCastReplyParams>(deviceLayer.message.data)
        val playbackStatus = OCastDataLayer.decode<PlaybackStatus>(ocastData.params)

        // Then
        assertEquals(OCastRawDeviceLayer.Status.OK, deviceLayer.status)
        assertEquals("89cf41b8-ef40-48d9-99c3-2a1951abcde5", deviceLayer.destination)
        assertEquals(ReferenceDevice.DOMAIN_BROWSER, deviceLayer.source)
        assertEquals(OCastRawDeviceLayer.Type.REPLY, deviceLayer.type)
        assertEquals(666L, deviceLayer.identifier)
        assertEquals(ReferenceDevice.SERVICE_MEDIA, deviceLayer.message.service)

        assertEquals("playbackStatus", ocastData.name)

        assertEquals(OCastMediaError.Status.SUCCESS.code, playbackStatus.code)
        assertEquals(OCastMediaError.Status.SUCCESS.code, replyData.code)

        assertEquals(Media.PlayerState.PLAYING, playbackStatus.state)
        assertEquals(0.45, playbackStatus.volume, 0.00)
        assertEquals(1234.56, playbackStatus.position, 0.00)
        assertEquals(5678.9, playbackStatus.duration, 0.00)
        assertEquals(true, playbackStatus.isMuted)
    }

    @Test
    fun encodePrepareCommandWithoutLogoSucceeds() {

        // Given
        val options = JSONObject(hashMapOf("auth_cookie" to "azertyuiop1234"))
        val prepareMessage = MediaMessage(Prepare(
            "http://localhost",
            4,
            "La cité de la peur",
            "Un film de les nuls",
            null,
            Media.Type.VIDEO,
            Media.TransferMode.STREAMED,
            true
        ).options(options).build())
        val uuid = "89cf41b8-ef40-48d9-99c3-2a1951abcde5"
        val identifier = 666L

        // When
        val layerMessage = OCastCommandDeviceLayer(uuid, ReferenceDevice.DOMAIN_BROWSER, OCastRawDeviceLayer.Type.COMMAND, identifier, prepareMessage).encode()

        // Then
        val ocastMessage = """
            {
              "src": "89cf41b8-ef40-48d9-99c3-2a1951abcde5",
              "dst": "browser",
              "type": "command",
              "id": 666,
              "message": {
                "service": "org.ocast.media",
                "data": {
                  "name": "prepare",
                  "params": {
                    "url": "http://localhost",
                    "frequency": 4,
                    "title": "La cité de la peur",
                    "subtitle": "Un film de les nuls",
                    "mediaType": "video",
                    "transferMode": "streamed",
                    "autoplay": true
                  },
                  "options": {
                    "auth_cookie": "azertyuiop1234"
                  }
                }
              }
            }
        """.trimIndent()
            .split("\\R".toRegex())
            .joinToString("") {
                it.trim().replace("\": ", "\":")
            }

        assertEquals(ocastMessage, layerMessage)
    }

    @Test
    fun encodeCustomCommandSucceeds() {

        // Given
        val options = JSONObject(hashMapOf("my option" to "azertyuiop1234"))
        val params = JSONObject(hashMapOf("my param" to "1234azertyuiop"))

        val customMessage = OCastApplicationLayer("my service", OCastDataLayerBuilder("my command", params, options).build())

        val uuid = "89cf41b8-ef40-48d9-99c3-2a1951abcde5"
        val identifier = 666L

        // When
        val layerMessage = OCastCommandDeviceLayer(uuid, "my destination", OCastRawDeviceLayer.Type.COMMAND, identifier, customMessage).encode()

        // Then
        val ocastMessage = """
            {
              "src": "89cf41b8-ef40-48d9-99c3-2a1951abcde5",
              "dst": "my destination",
              "type": "command",
              "id": 666,
              "message": {
                "service": "my service",
                "data": {
                  "name": "my command",
                  "params": {
                    "my param": "1234azertyuiop"
                  },
                  "options": {
                    "my option": "azertyuiop1234"
                  }
                }
              }
            }
        """.trimIndent()
            .split("\\R".toRegex())
            .joinToString("") {
                it.trim().replace("\": ", "\":")
            }

        assertEquals(ocastMessage, layerMessage)
    }
}