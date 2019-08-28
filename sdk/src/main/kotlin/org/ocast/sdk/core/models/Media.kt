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

//region Messages

/**
 * Represents a media message.
 *
 * @param T The type of params.
 * @param data The data layer conveyed by the media message.
 * @constructor Creates an instance of [MediaMessage].
 */
class MediaMessage<T>(data: OCastDataLayer<T>) : OCastApplicationLayer<T>(Service.MEDIA, data)

//endregion

//region Commands

/**
 * Represents the parameters of a `play` command.
 *
 * @property position The starting position of the media, in seconds.
 * @constructor Creates an instance of [PlayMediaCommandParams].
 */
class PlayMediaCommandParams(
    @JsonProperty("position") val position: Double = 0.0
) : OCastCommandParams("play")

/**
 * Represents the parameters of a `stop` command.
 *
 * @constructor Creates an instance of [StopMediaCommandParams].
 */
class StopMediaCommandParams : OCastCommandParams("stop")

/**
 * Represents the parameters of a `pause` command.
 *
 * @constructor Creates an instance of [PauseMediaCommandParams].
 */
class PauseMediaCommandParams : OCastCommandParams("pause")

/**
 * Represents the parameters of a `resume` command.
 *
 * @constructor Creates an instance of [ResumeMediaCommandParams].
 */
class ResumeMediaCommandParams : OCastCommandParams("resume")

/**
 * Represents the parameters of a `prepare` command.
 *
 * @property url The media URL.
 * @property updateFrequency The frequency of [MediaPlaybackStatus] events, in seconds. 0 means no event.
 * @property title The media title.
 * @property subtitle The media subtitle.
 * @property logo The media thumbnail.
 * @property mediaType The media type.
 * @property transferMode The media transfer mode.
 * @property autoplay Indicates if the media should play automatically once the prepare command is complete.
 * @constructor Creates an instance of [PrepareMediaCommandParams].
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
 * Represents the parameters of a `volume` command.
 *
 * @property volume The media volume, ranging from 0.0 to 1.0.
 * @constructor Creates an instance of [SetMediaVolumeCommandParams].
 */
class SetMediaVolumeCommandParams(
    @JsonProperty("volume") val volume: Double
) : OCastCommandParams("volume")

/**
 * Represents the parameters of a `track` command.
 *
 * @param type The track type.
 * @param trackID The track identifier.
 * @param isEnabled Indicates if the track is enabled or not.
 * @constructor Creates an instance of [SetMediaTrackCommandParams].
 */
class SetMediaTrackCommandParams(
    @JsonProperty("type") val type: Type,
    @JsonProperty("trackId") val trackID: String,
    @get:JsonIgnore @field:JsonProperty("enabled") val isEnabled: Boolean
) : OCastCommandParams("track") {

    /**
     * Represents the different types of [MediaMetadata.Track].
     */
    enum class Type {

        /** A subtitle track. */
        @JsonProperty("text") SUBTITLE,

        /** An audio track. */
        @JsonProperty("audio") AUDIO,

        /** A video track. */
        @JsonProperty("video") VIDEO
    }
}

/**
 * Represents the parameters of a `seek` command.
 *
 * @param position The media position, in seconds.
 * @constructor Creates an instance of [SeekMediaCommandParams].
 */
class SeekMediaCommandParams(
    @JsonProperty("position") val position: Double
) : OCastCommandParams("seek")

/**
 * Represents the parameters of a `mute` command.
 *
 * @param isMuted Indicates if the media is muted or not.
 * @constructor Creates an instance of [MuteMediaCommandParams].
 */
class MuteMediaCommandParams(
    @get:JsonIgnore @field:JsonProperty("mute") val isMuted: Boolean
) : OCastCommandParams("mute")

/**
 * Represents the parameters of a `getPlaybackStatus` command.
 *
 * @constructor Creates an instance of [GetMediaPlaybackStatusCommandParams].
 */
class GetMediaPlaybackStatusCommandParams : OCastCommandParams("getPlaybackStatus")

/**
 * Represents the parameters of a `getMetadata` command.
 *
 * @constructor Creates an instance of [GetMediaMetadataCommandParams].
 */
class GetMediaMetadataCommandParams : OCastCommandParams("getMetadata")

//endregion

//region Replies and events

/**
 * Represents the playback status of a media.
 *
 * @property position The media position, in seconds.
 * @property duration The media duration, in seconds.
 * @property state The playback state.
 * @property volume The media volume, ranging from 0.0 to 1.0.
 * @property isMuted Indicates if the media is muted or not.
 * @constructor Creates an instance of [MediaPlaybackStatus].
 */
class MediaPlaybackStatus(
    @JsonProperty("position") val position: Double,
    @JsonProperty("duration") val duration: Double?,
    @JsonProperty("state") val state: State,
    @JsonProperty("volume") val volume: Double,
    @get:JsonIgnore @field:JsonProperty("mute") val isMuted: Boolean
) {

    /**
     * Represents the playback states of a media.
     *
     * @property state The raw state value.
     */
    enum class State(private val state: Int) {

        /** The media playback state is unknown. */
        UNKNOWN(0),

        /** The media is idle. */
        IDLE(1),

        /** The media is playing. */
        PLAYING(2),

        /** The media is paused. */
        PAUSED(3),

        /** The media is buffering. */
        BUFFERING(4);

        /**
         * Returns the raw state value.
         */
        @JsonValue
        fun toValue() = state
    }
}

/**
 * Represents the media metadata.
 *
 * @property title The media title.
 * @property subtitle The media subtitle.
 * @property logo The media thumbnail.
 * @property mediaType The media type.
 * @property subtitleTracks The list of subtitle tracks of the media.
 * @property audioTracks The list of audio tracks of the media.
 * @property videoTracks The list of video tracks of the media.
 * @constructor Creates an instance of [MediaMetadata].
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
     * Represents a media track.
     *
     * @property language The track language.
     * @property label The track label.
     * @property isEnabled Indicates if the track is enabled or not.
     * @property id The track identifier.
     * @constructor Creates an instance of [Track].
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

/**
 * This class is a container for enum classes which are used by several other media classes.
 */
class Media {

    /**
     * Represents the type of a media.
     */
    enum class Type {

        /** The media is audio. */
        @JsonProperty("audio") AUDIO,

        /** The media is an image. */
        @JsonProperty("image") IMAGE,

        /** The media is a video. */
        @JsonProperty("video") VIDEO
    }

    /**
     * Represents the transfer mode of a media.
     */
    enum class TransferMode {

        /** The media is buffered. */
        @JsonProperty("buffered") BUFFERED,

        /** The media is streamed. */
        @JsonProperty("streamed") STREAMED
    }
}

//endregion
