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

import org.junit.Assert.assertEquals
import org.junit.Test
import org.ocast.sdk.core.utils.JsonTools

/**
 * Unit tests for model classes related to web app service.
 */
class WebAppTest {

    @Test
    fun decodeWebAppConnectedStatusEventSucceeds() {
        // Given
        val json = """
            {
              "status": "connected"
            }
        """.trimIndent()

        // When
        val webAppConnectedStatusEvent = JsonTools.decode<WebAppConnectedStatusEvent>(json)

        // Then
        assertEquals(WebAppStatus.CONNECTED, webAppConnectedStatusEvent.status)
    }
}
