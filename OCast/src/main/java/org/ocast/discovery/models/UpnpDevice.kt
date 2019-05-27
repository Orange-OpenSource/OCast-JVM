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

package org.ocast.discovery.models

import java.net.URL

/**
 * This class represents an OCast device.
 *
 * @param uuid The Universally Unique IDentifier of the device.
 * @param applicationURL The URL of the Dial service.
 * @param friendlyName The friendly name of the device.
 * @param manufacturer The manufacturer of the device.
 * @param modelName The model name.
 */
class UpnpDevice(
    val uuid: String,
    val applicationURL: URL,
    val friendlyName: String,
    val manufacturer: String,
    val modelName: String
) {

    /**
     * A constructor with default values.
     * For internal use only.
     */
    internal constructor() : this("", URL("http://"), "", "", "")
}