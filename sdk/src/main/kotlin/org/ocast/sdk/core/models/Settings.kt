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

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.util.EnumSet
import org.ocast.sdk.core.ReferenceDevice
import org.ocast.sdk.core.utils.BitflagsSerializer

//region Message

/**
 *
 *
 * @param data
 */
class InputMessage<T>(data: OCastDataLayer<T>) : OCastApplicationLayer<T>(ReferenceDevice.SERVICE_SETTINGS_INPUT, data)

/**
 *
 *
 * @param data
 */
class DeviceMessage<T>(data: OCastDataLayer<T>) : OCastApplicationLayer<T>(ReferenceDevice.SERVICE_SETTINGS_DEVICE, data)

//endregion

//region Command

/**
 *
 */
class GetUpdateStatusCommandParams : OCastCommandParams("getUpdateStatus")

/**
 *
 */
class GetDeviceIDCommandParams : OCastCommandParams("getDeviceID")

/**
 *
 *
 * @param key
 * @param code
 * @param ctrl
 * @param alt
 * @param shift
 * @param meta
 * @param location
 */
class SendKeyEventCommandParams(
    @JsonProperty("key") val key: String,
    @JsonProperty("code") val code: String,
    @JsonProperty("ctrl") val ctrl: Boolean,
    @JsonProperty("alt") val alt: Boolean,
    @JsonProperty("shift") val shift: Boolean,
    @JsonProperty("meta") val meta: Boolean,
    @JsonProperty("location") val location: DOMKeyLocation
) : OCastCommandParams("keyPressed") {

    enum class DOMKeyLocation(private val value: Int) {

        STANDARD(0),
        LEFT(1),
        RIGHT(2),
        NUMPAD(3);

        @JsonValue
        fun toValue() = value
    }
}

/**
 *
 *
 * @param x
 * @param y
 * @param buttons
 */
class SendMouseEventCommandParams(
    @JsonProperty("x") val x: Int,
    @JsonProperty("y") val y: Int,
    @JsonSerialize(using = BitflagsSerializer::class)
    @JsonProperty("buttons") val buttons: EnumSet<Button>
) : OCastCommandParams("mouseEvent") {

    enum class Button(override val bit: Int) : Bitflag {

        PRIMARY(0),
        RIGHT(1),
        MIDDLE(2);

        @JsonValue
        fun toValue() = bit
    }
}

/**
 *
 *
 * @param axes
 * @param buttons
 */

class SendGamepadEventCommandParams(
    @JsonProperty("axes") val axes: List<Axe>,
    @JsonSerialize(using = BitflagsSerializer::class)
    @JsonProperty("buttons") val buttons: EnumSet<Button>
) : OCastCommandParams("gamepadEvent") {

    /**
     *
     *
     * @param x
     * @param y
     * @param type
     */
    class Axe(
        @JsonProperty("x") val x: Double,
        @JsonProperty("y") val y: Double,
        @JsonProperty("num") val type: Type
    ) {

        enum class Type(private val value: Int) {

            LEFT_STICK_HORIZONTAL(0),
            LEFT_STICK_VERTICAL(1),
            RIGHT_STICK_HORIZONTAL(2),
            RIGHT_STICK_VERTICAL(3);

            @JsonValue
            fun toValue() = value
        }
    }

    enum class Button(override val bit: Int) : Bitflag {

        RIGHT_CLUSTER_BOTTOM(0),
        RIGHT_CLUSTER_RIGHT(1),
        RIGHT_CLUSTER_LEFT(2),
        RIGHT_CLUSTER_TOP(3),
        TOP_LEFT_FRONT(4),
        TOP_RIGHT_FRONT(5),
        BOTTOM_LEFT_FRONT(6),
        BOTTOM_RIGHT_FRONT(7),
        CENTER_CLUSTER_LEFT(8),
        CENTER_CLUSTER_RIGHT(9),
        LEFT_STICK_PRESSED(10),
        RIGHT_STICK_PRESSED(11),
        LEFT_CLUSTER_TOP(12),
        LEFT_CLUSTER_BOTTOM(13),
        LEFT_CLUSTER_LEFT(14),
        LEFT_CLUSTER_RIGHT(15),
        CENTER_CLUSTER_MIDDLE(16);

        @JsonValue
        fun toValue() = bit
    }
}

//endregion

//region Reply

/**
 *
 *
 * @param code
 * @param state
 * @param version
 * @param progress
 */
class UpdateStatus(
    code: Int?,
    @JsonProperty("state") val state: State,
    @JsonProperty("version") val version: String,
    @JsonProperty("progress") val progress: Int
) : OCastReplyEventParams(code) {

    enum class State(private val value: String) {

        SUCCESS("success"),
        ERROR("error"),
        NOT_CHECKED("notChecked"),
        UP_TO_DATE("upToDate"),
        NEW_VERSION_FOUND("newVersionFound"),
        DOWNLOADING("downloading"),
        NEW_VERSION_READY("newVersionReady");

        @JsonValue
        fun toValue() = value
    }
}

/**
 *
 *
 * @param code
 * @param id
 */
class DeviceID(
    code: Int?,
    @JsonProperty("id") val id: String
) : OCastReplyEventParams(code)

//endregion
