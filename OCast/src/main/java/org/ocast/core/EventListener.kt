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

package org.ocast.core

import org.ocast.core.models.CustomEvent
import org.ocast.core.models.MetadataChangedEvent
import org.ocast.core.models.PlaybackStatusEvent
import org.ocast.core.models.UpdateStatusEvent

interface EventListener {

    /**
     * Playback Status of the player, sent at a rate defined by the frequency parameter of the prepare method.
     */
    @JvmDefault
    fun onPlaybackStatus(device: Device, status: PlaybackStatusEvent) {
    }

    /**
     * Metadata sent each time is changed on the current playback.
     */
    @JvmDefault
    fun onMetadataChanged(device: Device, metadata: MetadataChangedEvent) {
    }

    /**
     * Firmware update status of the device, sent each seconds for downloading, otherwise when state changes.
     */
    @JvmDefault
    fun onUpdateStatus(device: Device, updateStatus: UpdateStatusEvent) {
    }

    /**
     * Receive custom event sent from web application
     */
    @JvmDefault
    fun onCustomEvent(device: Device, customEvent: CustomEvent) {
    }
}
