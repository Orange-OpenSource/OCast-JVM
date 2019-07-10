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

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import org.json.JSONObject
import org.ocast.core.utils.RawJsonDeserializer
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * SSL configuration
 *
 * @param trustManager
 * @param socketFactory
 * @param hostnameVerifier
 */
class SSLConfiguration(val trustManager: X509TrustManager, val socketFactory: SSLSocketFactory, val hostnameVerifier: HostnameVerifier)

//region Device layer

/**
 * OCast layer
 *
 * @param source
 * @param destination
 * @param type
 * @param status Status (REPLY only)
 * @param identifier
 * @param message
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
     *
     * OK : No errors were found while processing the command
     * JSON_FORMAT_ERROR : There is an error in the JSON formatting
     * VALUE_FORMAT_ERROR : This is an error in the packet, typically caused by a malformatted value
     * MISSING_MANDATORY_FIELD : This is an error in the packet, typically caused by missing a field
     * FORBIDDEN_UNSECURE_MODE : Packet has no right to access the required destination or service.
     * INTERNAL_ERROR : All other cases
     */
    enum class Status {
        @JsonProperty("ok") OK,
        @JsonProperty("json_format_error") JSON_FORMAT_ERROR,
        @JsonProperty("value_format_error") VALUE_FORMAT_ERROR,
        @JsonProperty("missing_mandatory_field ") MISSING_MANDATORY_FIELD,
        @JsonProperty("forbidden_unsecure_mode ") FORBIDDEN_UNSECURE_MODE,
        @JsonProperty("internal_error") INTERNAL_ERROR,
        @JsonEnumDefaultValue
        @JsonProperty("unknown") UNKNOWN
    }

    enum class Type {
        @JsonProperty("event") EVENT,
        @JsonProperty("reply") REPLY,
        @JsonProperty("command") COMMAND
    }
}

/**
 *
 *
 * @param source
 * @param destination
 * @param type
 * @param identifier
 * @param message
 */
class OCastCommandDeviceLayer<T>(
    @JsonProperty("src") val source: String,
    @JsonProperty("dst") val destination: String,
    @JsonProperty("type") val type: OCastRawDeviceLayer.Type,
    @JsonProperty("id") val identifier: Long,
    @JsonProperty("message") val message: OCastApplicationLayer<T>
)

//endregion

//region Application layer

/**
 *
 *
 * @param service
 * @param data
 */
open class OCastRawApplicationLayer(
    @JsonProperty("service") val service: String,
    @JsonDeserialize(using = RawJsonDeserializer::class)
    @JsonProperty("data") val data: String
)

/**
 *
 *
 * @param service
 * @param data
 */
open class OCastApplicationLayer<T>(
    @JsonProperty("service") val service: String,
    @JsonProperty("data") val data: OCastDataLayer<T>
)

//endregion

//region Data layer

/**
 *
 *
 * @param name
 * @param params
 * @param options
 */
open class OCastDataLayer<T>(
    @JsonProperty("name") var name: String,
    @JsonProperty("params") val params: T,
    @JsonProperty("options") val options: JSONObject?
)

/**
 *
 *
 * @param name
 * @param params
 * @param options
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
 *
 *
 * @param code
 */
open class OCastReplyParams(
    @JsonProperty("code") internal open val code: Int
)

open class OCastDataLayerParams(
    @JsonIgnore val name: String
) {

    private val builder by lazy { OCastDataLayerBuilder(name, this) }

    fun build(): OCastDataLayer<OCastDataLayerParams> {
        return builder.build()
    }

    fun options(options: JSONObject?) = apply { builder.options(options) }
}

open class OCastDataLayerBuilder<T>(
    var name: String,
    var params: T,
    var options: JSONObject? = null
) {

    fun build(): OCastDataLayer<T> {
        return OCastDataLayer(name, params, options)
    }

    fun name(name: String) = apply { this.name = name }

    fun params(params: T) = apply { this.params = params }

    fun options(options: JSONObject?) = apply { this.options = options }
}

//endregion
