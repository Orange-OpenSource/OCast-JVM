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
 *
 *
 * @param status
 */
class WebAppConnectedStatusEvent(
    @JsonProperty("status") val status: WebAppStatus
)

enum class WebAppStatus {
    @JsonProperty("connected") CONNECTED,
    @JsonProperty("disconnected") DISCONNECTED,
    @JsonEnumDefaultValue
    @JsonProperty("unknown") UNKNOWN
}
