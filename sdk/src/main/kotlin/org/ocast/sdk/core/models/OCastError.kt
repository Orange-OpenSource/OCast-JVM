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

import org.ocast.sdk.common.extensions.orElse
import org.ocast.sdk.core.utils.LoggableError

/**
 * This interface is implemented by all enums which represent OCast error statuses.
 *
 * An enum class which implements this interface MUST have an `UNKNOWN_ERROR` value.
 */
interface ErrorStatus {

    /** The status code. */
    val code: Int

    /**
     * The companion object.
     */
    companion object {

        /**
         * Returns an [ErrorStatus] from the given code.
         *
         * @param T The type of enum value to return.
         * @param code The error code.
         * @return An enum value corresponding to the given error code.
         */
        inline fun <reified T> fromCode(code: Int): T where T : Enum<T>, T : ErrorStatus {
            return enumValues<T>().firstOrNull { it.code == code }.orElse { enumValueOf("UNKNOWN_ERROR") }
        }
    }
}

/**
 * Represents a generic OCast error.
 *
 * @property code The error code.
 * @property message The error message.
 * @property cause The cause of the error, if any.
 * @constructor Creates an [OCastError].
 */
data class OCastError(val code: Int, override val message: String, override val cause: Throwable? = null) : LoggableError {

    /**
     * @property message The error message.
     * @property cause The cause of the error, if any.
     * @constructor Creates an [OCastError] with a `CLIENT_ERROR` status.
     */
    constructor(message: String, cause: Throwable? = null) : this(Status.CLIENT_ERROR.code, message, cause)

    /**
     * Represents the status of an [OCastError].
     */
    enum class Status(override val code: Int) : ErrorStatus {

        /** There is no error. */
        SUCCESS(0),

        /** The error is unknown. */
        UNKNOWN_ERROR(-1),

        /** There was an error in the OCast SDK. */
        CLIENT_ERROR(-2),

        /** There was an error in the received OCast device layer. */
        DEVICE_LAYER_ERROR(-3),

        /** There was an error during decoding message. */
        DECODE_ERROR(-4)
    }
}

/**
 * Represents an error from the device settings service.
 *
 * @property status The error status.
 * @property message The error message.
 * @property cause The cause of the error, if any.
 * @constructor Creates an instance of [OCastDeviceSettingsError].
 */
data class OCastDeviceSettingsError(val status: Status, override val message: String, override val cause: Throwable? = null) : LoggableError {

    /**
     * @param error The generic OCast error.
     * @constructor Creates an instance of [OCastDeviceSettingsError] from a generic [OCastError].
     */
    constructor(error: OCastError) : this(ErrorStatus.fromCode(error.code), error.message, error.cause)

    /**
     * Represents the status of an [OCastDeviceSettingsError].
     */
    enum class Status(override val code: Int) : ErrorStatus {

        /** There is no error. */
        SUCCESS(0),

        /** The error is unknown. */
        UNKNOWN_ERROR(-1),

        /** There was an error in the OCast SDK. */
        CLIENT_ERROR(-2),

        /** There was an error in the received OCast device layer. */
        DEVICE_LAYER_ERROR(-3),

        /** There was an error during decoding message. */
        DECODE_ERROR(-4)
    }
}

/**
 * Represents an error from the input settings service.
 *
 * @property status The error status.
 * @property message The error message.
 * @property cause The cause of the error, if any.
 * @constructor Creates an [OCastInputSettingsError].
 */
data class OCastInputSettingsError(val status: Status, override val message: String, override val cause: Throwable? = null) : LoggableError {

    /**
     * @param error The generic OCast error.
     * @constructor Creates an instance of [OCastInputSettingsError] from a generic [OCastError].
     */
    constructor(error: OCastError) : this(ErrorStatus.fromCode(error.code), error.message, error.cause)

    /**
     * Represents the status of an [OCastInputSettingsError].
     */
    enum class Status(override val code: Int) : ErrorStatus {

        /** There is no error. */
        SUCCESS(0),

        /** The error is unknown. */
        UNKNOWN_ERROR(-1),

        /** There was an error in the OCast SDK. */
        CLIENT_ERROR(-2),

        /** There was an error in the received OCast device layer. */
        DEVICE_LAYER_ERROR(-3),

        /** There was an error during decoding message. */
        DECODE_ERROR(-4)
    }
}

/**
 * Represents an error from the media service.
 *
 * @property status The error status.
 * @property message The error message.
 * @property cause The cause of the error, if any.
 * @constructor Creates an [OCastMediaError].
 */
data class OCastMediaError(val status: Status, override val message: String, override val cause: Throwable? = null) : LoggableError {

    /**
     * @param error The generic OCast error.
     * @constructor Creates an instance of [OCastMediaError] from a generic [OCastError].
     */
    constructor(error: OCastError) : this(ErrorStatus.fromCode(error.code), error.message, error.cause)

    /**
     * Represents the status of an [OCastMediaError].
     */
    enum class Status(override val code: Int) : ErrorStatus {

        /** There is no error. */
        SUCCESS(0),

        /** The command is not yet implemented by the web application. */
        NOT_IMPLEMENTED(2400),

        /** The service is not implemented by the web application. */
        INVALID_SERVICE(2404),

        /** The command could not be performed according to the player state. */
        INVALID_PLAYER_STATE(2412),

        /** The player could not be initialized. */
        NO_PLAYER_INITIALIZED(2413),

        /** The track ID is not valid. */
        INVALID_TRACK(2414),

        /** The media type is unknown. */
        UNKNOWN_MEDIA_TYPE(2415),

        /** The transfer mode is unknown. */
        UNKNOWN_TRANSFER_MODE(2416),

        /** A mandatory parameter is missing. */
        MISSING_PARAMETER(2422),

        /** An internal error, please send details on the tracker. */
        INTERNAL_ERROR(2500),

        /** The error is unknown. */
        UNKNOWN_ERROR(-1),

        /** There was an error in the OCast SDK. */
        CLIENT_ERROR(-2),

        /** There was an error in the received OCast device layer. */
        DEVICE_LAYER_ERROR(-3),

        /** There was an error during decoding message. */
        DECODE_ERROR(-4)
    }
}
