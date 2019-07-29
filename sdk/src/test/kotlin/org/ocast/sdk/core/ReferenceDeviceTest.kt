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

import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test
import org.ocast.sdk.core.models.UpdateStatus
import org.ocast.sdk.discovery.models.UpnpDevice

class ReferenceDeviceTest {

    private val referenceDevice = ReferenceDevice(UpnpDevice())

    private val eventListener = mock<EventListener>()
    private val webSocket = mock<WebSocket>()

    @Before
    fun setUp() {
        referenceDevice.eventListener = eventListener
    }

    @Test
    fun receiveUpdateStatusEventCallsListener() {

        // Given
        val data = """
            {
              "dst": "89cf41b8-ef40-48d9-99c3-2a1951abcde5",
              "src": "browser",
              "type": "event",
              "status": "OK",
              "id": 1,
              "message": {
                "service": "org.ocast.settings.device",
                "data": {
                  "name": "updateStatus",
                  "params": {
                    "state": "downloading",
                    "version": "1.0",
                    "progress": 50
                  }
                }
              }
            }
        """.trimIndent()

        // When
        referenceDevice.onDataReceived(webSocket, data)

        // Then
        val updateStatusEvent = argumentCaptor<UpdateStatus>()
        verify(eventListener, times(1)).onUpdateStatus(eq(referenceDevice), updateStatusEvent.capture())
        assertEquals(updateStatusEvent.firstValue.state, UpdateStatus.State.DOWNLOADING)
        assertEquals(updateStatusEvent.firstValue.progress, 50)
        assertEquals(updateStatusEvent.firstValue.version, "1.0")
    }
}
