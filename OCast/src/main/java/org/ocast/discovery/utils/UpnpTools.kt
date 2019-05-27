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

package org.ocast.discovery.utils

/**
 * A singleton object which provides utility methods related to UPnP.
 */
internal object UpnpTools {

    /**
     * The regex used to extract a device UUID from a string.
     */
    private val UUID_REGEX = "^uuid:([^:]*)".toRegex()

    /**
     * Extracts the UUID of a device from a string.
     *
     * @param string The string to extract the UUID from. This is typically a Unique Service Name or a Unique Device Name.
     * @return The extracted UUID, or null is the UUID could not be extracted.
     */
    fun extractUuid(string: String): String? {
        return UUID_REGEX.find(string)?.groupValues?.elementAtOrNull(1)
    }
}