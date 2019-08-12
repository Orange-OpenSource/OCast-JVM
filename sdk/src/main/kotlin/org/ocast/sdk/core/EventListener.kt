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

package org.ocast.sdk.core

import org.ocast.sdk.core.models.MediaMetadata
import org.ocast.sdk.core.models.MediaPlaybackStatus
import org.ocast.sdk.core.models.UpdateStatus

/**
 * Represents a listener of OCast protocol events.
 */
interface EventListener {

    /**
     * Tells the listener that a media playback status event was sent by a device.
     *
     * This event is sent at a rate defined by the frequency parameter of the prepare command.
     *
     * @param device The device that sent the event.
     * @param mediaPlaybackStatus The playback status of the current media.
     */
    @JvmDefault
    fun onMediaPlaybackStatus(device: Device, mediaPlaybackStatus: MediaPlaybackStatus) {
    }

    /**
     * Tells the listener that a media metadata changed event was sent by a device.
     *
     * @param device The device that sent the event.
     * @param mediaMetadata The changed media metadata.
     */
    @JvmDefault
    fun onMediaMetadataChanged(device: Device, mediaMetadata: MediaMetadata) {
    }

    /**
     * Tells the listener that a firmware update status event was sent by a device.
     *
     * This event is sent each seconds when downloading a new firmware, otherwise when state changes.
     *
     * @param device The device that sent the event.
     * @param updateStatus The firmware update status of the device.
     */
    @JvmDefault
    fun onUpdateStatus(device: Device, updateStatus: UpdateStatus) {
    }

    /**
     * Tells the listener that a custom event from a web application was sent by a device.
     *
     * @param device The device that sent the event.
     * @param name The name of the web application where the event originated.
     * @param params The parameters of the event.
     */
    @JvmDefault
    fun onCustomEvent(device: Device, name: String, params: String) {
    }
}
