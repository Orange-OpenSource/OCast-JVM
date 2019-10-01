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

import junit.framework.TestCase.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test
import org.ocast.sdk.common.Assert.assertJsonEquals
import org.ocast.sdk.core.utils.JsonTools

/**
 * Unit tests for model classes related to media service.
 */
internal class MediaTest {

    //region Commands

    @Test
    fun encodePlayMediaCommandSucceeds() {
        // Given
        val dataLayer = PlayMediaCommandParams(10.0).build()

        // When
        val json = JsonTools.encode(dataLayer)

        // Then
        val expectedJson = """
            {
              "name": "play", 
              "params": {
                "position": 10.0
              }
            }
        """.trimIndent()

        assertJsonEquals(expectedJson, json)
    }

    @Test
    fun encodeStopMediaCommandSucceeds() {
        // Given
        val dataLayer = StopMediaCommandParams().build()

        // When
        val json = JsonTools.encode(dataLayer)

        // Then
        val expectedJson = """
            {
              "name": "stop", 
              "params": {
              }
            }
        """.trimIndent()

        assertJsonEquals(expectedJson, json)
    }

    @Test
    fun encodePauseMediaCommandSucceeds() {
        // Given
        val dataLayer = PauseMediaCommandParams().build()

        // When
        val json = JsonTools.encode(dataLayer)

        // Then
        val expectedJson = """
            {
              "name": "pause", 
              "params": {
              }
            }
        """.trimIndent()

        assertJsonEquals(expectedJson, json)
    }

    @Test
    fun encodeResumeMediaCommandSucceeds() {
        // Given
        val dataLayer = ResumeMediaCommandParams().build()

        // When
        val json = JsonTools.encode(dataLayer)

        // Then
        val expectedJson = """
            {
              "name": "resume", 
              "params": {
              }
            }
        """.trimIndent()

        assertJsonEquals(expectedJson, json)
    }

    @Test
    fun encodePrepareMediaCommandSucceeds() {
        // Given
        val dataLayer = PrepareMediaCommandParams(
            "http://localhost/media",
            10,
            "La cité de la peur",
            "Un film de les nuls",
            "http://localhost/logo",
            Media.Type.VIDEO,
            Media.TransferMode.STREAMED,
            true
        ).build()

        // When
        val json = JsonTools.encode(dataLayer)

        // Then
        val expectedJson = """
            {
              "name": "prepare", 
              "params": {
                "url": "http://localhost/media",
                "frequency": 10,
                "title": "La cité de la peur",
                "subtitle": "Un film de les nuls",
                "logo": "http://localhost/logo",
                "mediaType": "video",
                "transferMode": "streamed",
                "autoplay": true
              }
            }
        """.trimIndent()

        assertJsonEquals(expectedJson, json)
    }

    @Test
    fun encodeSetMediaVolumeCommandSucceeds() {
        // Given
        val dataLayer = SetMediaVolumeCommandParams(0.6).build()

        // When
        val json = JsonTools.encode(dataLayer)

        // Then
        val expectedJson = """
            {
              "name": "volume", 
              "params": {
                "volume": 0.6
              }
            }
        """.trimIndent()

        assertJsonEquals(expectedJson, json)
    }

    @Test
    fun encodeSetMediaTrackCommandSucceeds() {
        // Given
        val dataLayer = SetMediaTrackCommandParams(
            SetMediaTrackCommandParams.Type.AUDIO,
            "id",
            true
        ).build()

        // When
        val json = JsonTools.encode(dataLayer)

        // Then
        val expectedJson = """
            {
              "name": "track", 
              "params": {
                "type": "audio",
                "trackId": "id",
                "enabled": true
              }
            }
        """.trimIndent()

        assertJsonEquals(expectedJson, json)
    }

    @Test
    fun encodeSeekMediaCommandSucceeds() {
        // Given
        val dataLayer = SeekMediaCommandParams(357.0).build()

        // When
        val json = JsonTools.encode(dataLayer)

        // Then
        val expectedJson = """
            {
              "name": "seek", 
              "params": {
                "position": 357.0
              }
            }
        """.trimIndent()

        assertJsonEquals(expectedJson, json)
    }

    @Test
    fun encodeMuteMediaCommandSucceeds() {
        // Given
        val dataLayer = MuteMediaCommandParams(false).build()

        // When
        val json = JsonTools.encode(dataLayer)

        // Then
        val expectedJson = """
            {
              "name": "mute", 
              "params": {
                "mute": false
              }
            }
        """.trimIndent()

        assertJsonEquals(expectedJson, json)
    }

    @Test
    fun encodeGetMediaPlaybackStatusCommandSucceeds() {
        // Given
        val dataLayer = GetMediaPlaybackStatusCommandParams().build()

        // When
        val json = JsonTools.encode(dataLayer)

        // Then
        val expectedJson = """
            {
              "name": "getPlaybackStatus", 
              "params": {
              }
            }
        """.trimIndent()

        assertJsonEquals(expectedJson, json)
    }

    @Test
    fun encodeGetMediaMetadataCommandSucceeds() {
        // Given
        val dataLayer = GetMediaMetadataCommandParams().build()

        // When
        val json = JsonTools.encode(dataLayer)

        // Then
        val expectedJson = """
            {
              "name": "getMetadata", 
              "params": {
              }
            }
        """.trimIndent()

        assertJsonEquals(expectedJson, json)
    }

    //endregion

    //region Replies and events

    @Test
    fun decodeMediaPlaybackStatusSucceeds() {
        // Given
        val json = """
            {
              "volume": 0.3,
              "mute": true,
              "state": 3,
              "position": 303,
              "duration": 4673.7
            }
        """.trimIndent()

        // When
        val playbackStatus = JsonTools.decode<MediaPlaybackStatus>(json)

        // Then
        assertEquals(0.3, playbackStatus.volume, 0.0)
        assertEquals(true, playbackStatus.isMuted)
        assertEquals(MediaPlaybackStatus.State.PAUSED, playbackStatus.state)
        assertEquals(303.0, playbackStatus.position, 0.0)
        assertEquals(4673.7, playbackStatus.duration)
    }

    @Test
    fun decodeMediaMetadataSucceeds() {
        // Given
        val json = """
            {
              "title": "La cité de la peur",
              "subtitle": "Un film de les nuls",
              "logo": "http://localhost/logo",
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
        """.trimIndent()

        // When
        val metadata = JsonTools.decode<MediaMetadata>(json)

        // Then
        assertEquals("La cité de la peur", metadata.title)
        assertEquals("Un film de les nuls", metadata.subtitle)
        assertEquals("http://localhost/logo", metadata.logo)
        assertEquals(Media.Type.VIDEO, metadata.mediaType)
        assertEquals(0, metadata.subtitleTracks?.size)
        assertEquals(1, metadata.audioTracks?.size)
        val audioTrack = metadata.audioTracks?.firstOrNull()
        assertEquals("id123", audioTrack?.id)
        assertEquals("de", audioTrack?.language)
        assertEquals("Audio DE", audioTrack?.label)
        assertEquals(true, audioTrack?.isEnabled)
        assertNull(metadata.videoTracks)
    }

    //endregion
}
