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

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue

//region Message

/**
 *
 *
 * @param data
 */
class MediaMessage<T>(data: OCastDataLayer<T>) : OCastApplicationLayer<T>(Service.MEDIA, data)

//endregion

//region Command

/**
 * Play the media
 *
 * @param position position (in second)
 */
class PlayMediaCommandParams(
    @JsonProperty("position") val position: Double = 0.0
) : OCastCommandParams("play")

/**
 * Stop the media
 */
class StopMediaCommandParams : OCastCommandParams("stop")

/**
 * Pause the media
 */
class PauseMediaCommandParams : OCastCommandParams("pause")

/**
 * Resume the media
 */
class ResumeMediaCommandParams : OCastCommandParams("resume")

/**
 * Prepare the media
 *
 * @param url
 * @param updateFrequency Update playback status frequency (0 = no event)
 * @param title
 * @param subtitle
 * @param logo
 * @param mediaType
 * @param transferMode
 * @param autoplay
 */
class PrepareMediaCommandParams(
    @JsonProperty("url")val url: String,
    @JsonProperty("frequency")val updateFrequency: Int,
    @JsonProperty("title")val title: String,
    @JsonProperty("subtitle")val subtitle: String?,
    @JsonProperty("logo")val logo: String?,
    @JsonProperty("mediaType")val mediaType: Media.Type,
    @JsonProperty("transferMode") val transferMode: Media.TransferMode,
    @JsonProperty("autoplay") val autoplay: Boolean = true
) : OCastCommandParams("prepare")

/**
 * Change volume
 *
 * @param volume
 */
class SetMediaVolumeCommandParams(
    @JsonProperty("volume") val volume: Double
) : OCastCommandParams("volume")

/**
 * Change a track of the current playback
 *
 * @param type
 * @param trackID
 * @param isEnabled
 */
class SetMediaTrackCommandParams(
    @JsonProperty("type") val type: Type,
    @JsonProperty("trackId") val trackID: String,
    @get:JsonIgnore @field:JsonProperty("enable") val isEnabled: Boolean
) : OCastCommandParams("track") {

    enum class Type {
        @JsonProperty("text") SUBTITLE,
        @JsonProperty("audio") AUDIO,
        @JsonProperty("video") VIDEO
    }
}

/**
 * Seek to the position (in second)
 *
 * @param position
 */
class SeekMediaCommandParams(
    @JsonProperty("position") val position: Double
) : OCastCommandParams("seek")

/**
 * Mute volume
 *
 * @param isMuted
 */
class MuteMediaCommandParams(
    @get:JsonIgnore @field:JsonProperty("mute") val isMuted: Boolean
) : OCastCommandParams("mute")

/**
 * Get playback status
 */
class GetMediaPlaybackStatusCommandParams : OCastCommandParams("getPlaybackStatus")

/**
 * Get metadata
 */
class GetMediaMetadataCommandParams : OCastCommandParams("getMetadata")

//endregion

//region Reply

/**
 * Media playback status
 *
 * @param position
 * @param duration
 * @param state
 * @param volume
 * @param isMuted
 */
class MediaPlaybackStatus(
    @JsonProperty("position") val position: Double,
    @JsonProperty("duration") val duration: Double?,
    @JsonProperty("state") val state: State,
    @JsonProperty("volume") val volume: Double,
    @get:JsonIgnore @field:JsonProperty("mute") val isMuted: Boolean
) {

    enum class State(private val state: Int) {

        UNKNOWN(0),
        IDLE(1),
        PLAYING(2),
        PAUSED(3),
        BUFFERING(4);

        @JsonValue
        fun toValue() = state
    }
}

/**
 *
 *
 * @param title
 * @param subtitle
 * @param logo
 * @param mediaType
 * @param subtitleTracks
 * @param audioTracks
 * @param videoTracks
 */
class MediaMetadata(
    @JsonProperty("title") val title: String,
    @JsonProperty("subtitle") val subtitle: String?,
    @JsonProperty("logo") val logo: String?,
    @JsonProperty("mediaType") val mediaType: Media.Type,
    @JsonProperty("textTracks") val subtitleTracks: List<Track>?,
    @JsonProperty("audioTracks") val audioTracks: List<Track>?,
    @JsonProperty("videoTracks") val videoTracks: List<Track>?
) {

    /**
     * Track
     *
     * @param language
     * @param label
     * @param isEnabled
     * @param id
     */
    class Track(
        @JsonProperty("language") val language: String,
        @JsonProperty("label") val label: String,
        @get:JsonIgnore @field:JsonProperty("enable") val isEnabled: Boolean,
        @JsonProperty("trackId") val id: String
    )
}

//endregion

//region Enums

class Media {

    enum class Type {
        @JsonProperty("audio") AUDIO,
        @JsonProperty("image") IMAGE,
        @JsonProperty("video") VIDEO
    }

    enum class TransferMode {
        @JsonProperty("buffered") BUFFERED,
        @JsonProperty("streamed") STREAMED
    }
}

//endregion
