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

package org.ocast.sdk.dial.models

import org.ocast.sdk.core.utils.LoggableError

/**
 * This class represents a DIAL error.
 *
 * @param message The error message.
 * @param cause The cause of the error, if any.
 * @constructor Creates an instance of [DialError] with the specified message and cause.
 */
internal class DialError(message: String?, cause: Throwable?) : Throwable(message, cause), LoggableError {

    /**
     * @param message The error message.
     * @constructor Creates an instance of [DialError] with the specified message.
     */
    constructor(message: String) : this(message, null)

    /**
     * @param cause The cause of the error.
     * @constructor Creates an instance of [DialError] with the specified cause.
     */
    constructor(cause: Throwable) : this(cause.toString(), cause)
}
