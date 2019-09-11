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

package org.ocast.sdk.common

import org.junit.Assert.assertEquals
import org.ocast.sdk.core.utils.JsonTools

/**
 * A singleton object that provides custom assert methods for tests.
 */
object Assert {

    /**
     * Asserts that two JSON strings are equal.
     *
     * @param expected Expected JSON string.
     * @param actual The JSON string to check against [expected].
     */
    fun assertJsonEquals(expected: String, actual: String) {
        assertEquals(JsonTools.objectMapper.readTree(expected), JsonTools.objectMapper.readTree(actual))
    }

    /**
     * Asserts that two SSDP messages are equal.
     *
     * @param expected Expected SSDP message.
     * @param actual The SSDP message to check against [expected].
     */
    fun assertSsdpMessageEquals(expected: String, actual: String) {
        val newlineRegex = "\\R".toRegex()
        val expectedLines = expected.split(newlineRegex).toMutableList()
        val actualLines = actual.split(newlineRegex).toMutableList()

        // Assert start lines are the same
        val expectedStartLine = expectedLines.removeAt(0)
        val actualStartLine = actualLines.removeAt(0)
        assertEquals(expectedStartLine, actualStartLine)

        // Assert other lines are the same, possibly in a different order
        assertEquals(expectedLines.sort(), actualLines.sort())
    }
}
