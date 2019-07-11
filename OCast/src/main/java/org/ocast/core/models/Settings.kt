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

import com.fasterxml.jackson.annotation.JsonProperty
import org.json.JSONObject
import org.ocast.core.ReferenceDevice

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
class GetUpdateStatus : OCastDataLayerParams("getUpdateStatus")

/**
 *
 */
class GetDeviceID : OCastDataLayerParams("getDeviceID")

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
class KeyPressed(
    @JsonProperty("key") val key: String,
    @JsonProperty("code") val code: String,
    @JsonProperty("ctrl") val ctrl: Boolean,
    @JsonProperty("alt") val alt: Boolean,
    @JsonProperty("shift") val shift: Boolean,
    @JsonProperty("meta") val meta: Boolean,
    @JsonProperty("location") val location: Int
) : OCastDataLayerParams("keyPressed")

/**
 *
 *
 * @param x
 * @param y
 * @param buttons
 */
class MouseEvent(
    @JsonProperty("x") val x: Int,
    @JsonProperty("y") val y: Int,
    @JsonProperty("buttons") val buttons: Int
) : OCastDataLayerParams("mouseEvent")

/**
 *
 *
 * @param axes
 * @param buttons
 */
class GamepadEvent(
    @JsonProperty("axes") val axes: List<GamepadAxes>,
    @JsonProperty("buttons") val buttons: Int
) : OCastDataLayerParams("gamepadEvent")

/**
 *
 *
 * @param x
 * @param y
 * @param num
 */
class GamepadAxes(
    @JsonProperty("x") val x: Double,
    @JsonProperty("y") val y: Double,
    @JsonProperty("num") val num: Int
)

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
    @JsonProperty("state") val state: String,
    @JsonProperty("version") val version: String,
    @JsonProperty("progress") val progress: Int
) : OCastReplyParams(code)

/**
 *
 *
 * @param code
 * @param id
 */
class DeviceID(
    code: Int?,
    @JsonProperty("id") val id: String
) : OCastReplyParams(code)

/**
 *
 *
 * @param code
 */
class CustomReply(
    code: Int?,
    @JsonProperty("name") val name: String,
    @JsonProperty("params") val params: JSONObject
) : OCastReplyParams(code)

//endregion

//region Event

/**
 *
 * @param name
 * @param params
 */
class CustomEvent(
    @JsonProperty("name") val name: String,
    @JsonProperty("params") val params: JSONObject
)

//endregion
