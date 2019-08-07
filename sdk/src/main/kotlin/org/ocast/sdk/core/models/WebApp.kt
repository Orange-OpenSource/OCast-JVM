// ktlint-disable filename
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

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents the connected status event of a web app.
 *
 * @property status The status of the web app.
 * @constructor Creates an instance of [WebAppConnectedStatusEvent].
 */
class WebAppConnectedStatusEvent(
    @JsonProperty("status") val status: WebAppStatus
)

/**
 * Represents the status of a web app.
 */
enum class WebAppStatus {

    /** The web app is connected to the web socket server. */
    @JsonProperty("connected") CONNECTED,

    /** The web app is disconnected from the web socket server. */
    @JsonProperty("disconnected") DISCONNECTED,

    /** The web app status is unknown. */
    @JsonEnumDefaultValue
    @JsonProperty("unknown") UNKNOWN
}
