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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import org.json.JSONObject
import org.ocast.sdk.core.utils.RawJsonDeserializer

/**
 * Represents an OCast settings service.
 */
class SettingsService {

    /**
     * The companion object.
     */
    companion object {

        /** The name of the device settings service. */
        internal const val DEVICE = "org.ocast.settings.device"

        /** The name of the input settings service. */
        internal const val INPUT = "org.ocast.settings.input"
    }
}

/**
 * Represents an OCast service.
 */
class Service {

    /**
     * The companion object.
     */
    companion object {

        /** The name of the web application service. */
        internal const val APPLICATION = "org.ocast.webapp"

        /** The name of the media service. */
        internal const val MEDIA = "org.ocast.media"
    }
}

/**
 * Represents an OCast event.
 */
class Event {

    /**
     * Represents a media event.
     */
    class Media {

        /**
         * The companion object.
         */
        companion object {

            /** The name of the media playback status event. */
            internal const val PLAYBACK_STATUS = "playbackStatus"

            /** The name of the media metadata changed event. */
            internal const val METADATA_CHANGED = "metadataChanged"
        }
    }

    /**
     * Represents a device settings event.
     */
    class Device {

        /**
         * The companion object.
         */
        companion object {

            /** The name of the firmware update status event. */
            internal const val UPDATE_STATUS = "updateStatus"

            /** The name of the firmware volume changed event. */
            internal const val VOLUME_CHANGED = "volumeChanged"
        }
    }
}

/**
 * This class represents an SSL configuration.
 *
 * @property trustManager The trust manager.
 * @property socketFactory The SSL socket factory.
 * @property hostnameVerifier The hostname verifier.
 * @constructor Creates an instance of [SSLConfiguration].
 */
class SSLConfiguration(val trustManager: X509TrustManager, val socketFactory: SSLSocketFactory, val hostnameVerifier: HostnameVerifier)

/**
 * Represents the OCast domains.
 *
 * @property value The domain raw value.
 */
enum class OCastDomain(val value: String) {

    /** The browser domain. Used to send commands to web applications. */
    BROWSER("browser"),

    /** The public settings domain. */
    SETTINGS("settings")
}

//region Device layer

/**
 * Represents an OCast device layer containing a raw message (i.e. the `data` property of `message` is a `String`).
 *
 * @property source The component which sends the message.
 * @property destination The component to which the message is sent to.
 * @property type The type of message.
 * @property status The device layer transport status. Equals to `null` for commands and events.
 * @property identifier The message identifier.
 * @property message The raw message to send.
 * @constructor Creates an instance of [OCastRawDeviceLayer].
 */
class OCastRawDeviceLayer(
    @JsonProperty("src") val source: String,
    @JsonProperty("dst") val destination: String,
    @JsonProperty("type") val type: Type,
    @JsonProperty("status") val status: Status?,
    @JsonProperty("id") val identifier: Long,
    @JsonProperty("message") val message: OCastRawApplicationLayer
) {

    /**
     * Represents the transport status of an OCast device layer.
     */
    enum class Status {

        /** No errors were found while processing the command. */
        @JsonProperty("ok") OK,

        /** There is an error in the JSON format. */
        @JsonProperty("json_format_error") JSON_FORMAT_ERROR,

        /** There is an error in the packet, typically caused by a wrongly formatted value. */
        @JsonProperty("value_format_error") VALUE_FORMAT_ERROR,

        /** There is an error in the packet, typically caused by a missing field. */
        @JsonProperty("missing_mandatory_field ") MISSING_MANDATORY_FIELD,

        /** The packet has no right to access the required destination or service. */
        @JsonProperty("forbidden_unsecure_mode ") FORBIDDEN_UNSECURE_MODE,

        /** The WiFi password is too short. */
        @JsonProperty("password_too_short") WIFI_PASSWORD_TOO_SHORT,

        /** There is an internal error. */
        @JsonProperty("internal_error") INTERNAL_ERROR
    }

    /**
     * Represents the type of message being sent in a device layer.
     */
    enum class Type {

        /** The message is an event sent by a device. */
        @JsonProperty("event") EVENT,

        /** The message is a reply sent by a device. */
        @JsonProperty("reply") REPLY,

        /** The message is a command sent to a device. */
        @JsonProperty("command") COMMAND
    }
}

/**
 * Represents an OCast device layer containing a command message.
 *
 * @param T The type of the data parameters contained in the command message.
 * @property source The component which sends the message.
 * @property destination The component to which the message is sent to.
 * @property identifier The message identifier.
 * @property message The message to send.
 * @constructor Creates an instance of [OCastCommandDeviceLayer].
 */
class OCastCommandDeviceLayer<T>(
    @JsonProperty("src") val source: String,
    @JsonProperty("dst") val destination: String,
    @JsonProperty("id") val identifier: Long,
    @JsonProperty("message") val message: OCastApplicationLayer<T>
) {
    @JsonProperty("type")
    val type = OCastRawDeviceLayer.Type.COMMAND
}

//endregion

//region Application layer

/**
 * Represents an OCast application layer containing raw data (i.e. the `data` property is a `String`).
 *
 * @property service The identifier of the service associated to the data.
 * @property data The raw data.
 * @constructor Creates an instance of [OCastRawApplicationLayer].
 */
open class OCastRawApplicationLayer(
    @JsonProperty("service") val service: String,
    @JsonDeserialize(using = RawJsonDeserializer::class)
    @JsonProperty("data") val data: String
)

/**
 * Represents an OCast application layer containing data.
 *
 * @param T The type of the parameters contained in the data layer.
 * @property service The identifier of the service associated to the data.
 * @property data The data.
 * @constructor Creates an instance of [OCastApplicationLayer].
 */
open class OCastApplicationLayer<T>(
    @JsonProperty("service") val service: String,
    @JsonProperty("data") val data: OCastDataLayer<T>
)

//endregion

//region Data layer

/**
 * Represents an OCast data layer.
 *
 * @param T The type of the data parameters.
 * @property name The name of the data.
 * @property params The data parameters.
 * @property options The options associated with this data, if any.
 * @constructor Creates an instance of [OCastDataLayer].
 */
open class OCastDataLayer<T>(
    @JsonProperty("name") var name: String,
    @JsonProperty("params") val params: T,
    @JsonProperty("options") val options: JSONObject?
)

/**
 * Represents an OCast data layer containing raw parameters (i.e. the `params` property is a `String`).
 *
 * @property name The name of the data.
 * @property params The raw data parameters.
 * @property options The options associated with this data, if any.
 * @constructor Creates an instance of [OCastRawDataLayer].
 */
open class OCastRawDataLayer(
    @JsonProperty("name") var name: String,
    @JsonDeserialize(using = RawJsonDeserializer::class)
    @JsonProperty("params") val params: String,
    @JsonProperty("options") val options: JSONObject?
)

//endregion

//region Params

/**
 * Represents parameters for the data layer of a reply or an event.
 *
 * @property code The code associated with the data. Equals `null` for events.
 * @constructor Creates an instance of [OCastReplyEventParams].
 */
class OCastReplyEventParams(
    @JsonProperty("code") val code: Int?
)

/**
 * Represents parameters for the data layer of a command.
 *
 * @property name The name of the command.
 * @constructor Creates an instance of [OCastCommandParams].
 */
open class OCastCommandParams(
    @JsonIgnore val name: String
) {

    /** The data layer builder. */
    private val builder by lazy { OCastDataLayerBuilder(name, this) }

    /**
     * Builds an instance of [OCastDataLayer] from the command parameters and options.
     *
     * @return The built data layer.
     */
    fun build(): OCastDataLayer<OCastCommandParams> {
        return builder.build()
    }

    /**
     * Apply the specified options to the command.
     *
     * @param options The options to apply.
     * @return `this`.
     */
    fun options(options: JSONObject?) = apply { builder.options(options) }
}

/**
 * Represents a builder of [OCastDataLayer].
 *
 * @param T The type of parameters of the data layer to build.
 * @property name The name of the data layer to build.
 * @property params The parameters of the data layer to build.
 * @property options The options of the data layer to build.
 * @constructor Creates an instance of [OCastDataLayerBuilder].
 */
private class OCastDataLayerBuilder<T>(
    var name: String,
    var params: T,
    var options: JSONObject? = null
) {

    /**
     * Builds an instance of [OCastDataLayer] with the builder name, parameters and options.
     *
     * @return The built data layer.
     */
    fun build(): OCastDataLayer<T> {
        return OCastDataLayer(name, params, options)
    }

    /**
     * Updates the name of the data layer to build.
     *
     * @param name The data layer name.
     * @return `this`.
     */
    fun name(name: String) = apply { this.name = name }

    /**
     * Updates the parameters of the data layer to build.
     *
     * @param params The data layer parameters.
     * @return `this`.
     */
    fun params(params: T) = apply { this.params = params }

    /**
     * Updates the options of the data layer to build.
     *
     * @param options The data layer options.
     * @return `this`.
     */
    fun options(options: JSONObject?) = apply { this.options = options }
}

//endregion
