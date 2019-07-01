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

    // Media event
    fun onPlaybackStatus(device: Device, status: PlaybackStatusEvent)
    fun onMetadataChanged(device: Device, metadata: MetadataChangedEvent)

    // Settings device event
    fun onUpdateStatus(device: Device, updateStatus: UpdateStatusEvent)

    // Custom event
    fun onCustomEvent(device: Device, customEvent: CustomEvent)
}
