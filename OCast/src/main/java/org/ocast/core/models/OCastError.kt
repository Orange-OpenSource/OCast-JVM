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

class OCastError(val code: Int, val errorMessage: String) {

    constructor(errorMessage: String) : this(Status.CLIENT_ERROR.code, errorMessage)

    enum class Status(val code: Int) {
        SUCCESS(0),
        CLIENT_ERROR(-2),
        DEVICE_LAYER_ERROR(-3)
    }
}

class OCastDeviceSettingsError(val errorMessage: String, val status: Status) {

    constructor(error: OCastError) : this(error.errorMessage, Status.values().firstOrNull { it.code == error.code } ?: Status.UNKNOWN_ERROR)

    enum class Status(val code: Int) {
        SUCCESS(0),
        UNKNOWN_ERROR(-1),
        CLIENT_ERROR(-2),
        DEVICE_LAYER_ERROR(-3)
    }
}

class OCastInputSettingsError(val errorMessage: String, val status: Status) {

    constructor(error: OCastError) : this(error.errorMessage, Status.values().firstOrNull { it.code == error.code } ?: Status.UNKNOWN_ERROR)

    enum class Status(val code: Int) {
        SUCCESS(0),
        UNKNOWN_ERROR(-1),
        CLIENT_ERROR(-2),
        DEVICE_LAYER_ERROR(-3)
    }
}

class OCastMediaError(val errorMessage: String, val status: Status) {

    constructor(error: OCastError) : this(error.errorMessage, Status.values().firstOrNull { it.code == error.code } ?: Status.UNKNOWN_ERROR)

    /**
     * Media controller error codes
     *
     * SUCCESS : No error
     * NOT_IMPLEMENTED : the command is not yet implemented by the web application
     * INVALID_SERVICE : the service is not implemented by the web application
     * MISSING_PARAMETER : a mandatory parameter is missing
     * INVALID_PLAYER_STATE : the command could not be performed according to the player state
     * NO_PLAYER_INITIALIZED : the player could not be initialized
     * INVALID_TRACK : the track ID is not valid
     * UNKNOWN_MEDIA_TYPE : unknown media type
     * UNKNOWN_TRANSFERT_MODE : unknown transfer mode
     * PARAMETERS_ARE_MISSING : Parameter(s) are missing
     * INTERNAL_ERROR : Internal error
     */
    enum class Status(val code: Int) {
        SUCCESS(0),
        NOT_IMPLEMENTED(2400),
        INVALID_SERVICE(2404),
        INVALID_PLAYER_STATE(2412),
        NO_PLAYER_INITIALIZED(2413),
        INVALID_TRACK(2414),
        UNKNOWN_MEDIA_TYPE(2415),
        UNKNOWN_TRANSFER_MODE(2416),
        MISSING_PARAMETER(2422),
        INTERNAL_ERROR(2500),
        UNKNOWN_ERROR(-1),
        CLIENT_ERROR(-2),
        DEVICE_LAYER_ERROR(-3)
    }
}