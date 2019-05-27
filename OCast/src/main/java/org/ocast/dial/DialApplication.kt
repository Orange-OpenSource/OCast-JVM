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

package org.ocast.dial

import java.net.URL

/**
 * This class represents a DIAL application.
 *
 * @param name The application name.
 * @param isStopAllowed Indicates if the application supports the stop operation.
 * @param state The current state of the application.
 * @param instanceURL The application instance URL.
 * @param additionalData The additional data for this application.
 */
internal data class DialApplication<T>(
    val name: String,
    val isStopAllowed: Boolean,
    val state: State?,
    val instanceURL: URL?,
    val additionalData: T
) where T : DialAdditionalData {

    /**
     * This class represents a state of a DIAL application.
     */
    sealed class State {

        /**
         * Indicates​ ​that​ ​the​ ​application​ ​is​ ​installed​ ​and​ ​either starting​ ​or​ ​running.
         */
        object Running : State()

        /**
         * ​Indicates​ ​that​ ​the​ ​application​ ​is​ ​installed​ ​and​ ​not running.
         */
        object Stopped : State()

        /**
         * ​Indicates​ ​that​ ​the​ ​application​ ​is​ ​not installed​ ​but​ ​is​ ​available​ ​for​ ​installation.​
         *
         * @param url The URL that can be used to install the application.
         */
        class Installable(val url: URL) : State()

        /**
         * ​Indicates​ ​that​ ​the​ ​application​ ​is​ ​running​ ​but​ ​is​ ​not currently​ ​visible​ ​to​ ​the​ ​user.​
         */
        object Hidden : State()
    }
}

/**
 * A class that represents additional data in a DIAL application information response.
 */
internal open class DialAdditionalData

/**
 * This class represents the additional data related to OCast when receiving DIAL application information response.
 *
 * @param webSocketURL The OCast web socket URL.
 * @param version The OCast version.
 */
internal class OCastAdditionalData(
    val webSocketURL: URL,
    val version: String
) : DialAdditionalData()