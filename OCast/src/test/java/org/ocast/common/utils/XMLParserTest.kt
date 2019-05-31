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

package org.ocast.common.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the [XMLParser] class.
 */
class XMLParserTest {

    @Test
    fun parseXMLSucceeds() {
        // Given
        val xml = """
            <foo fooFirstAttributeKey="fooFirstAttributeValue" fooSecondAttributeKey="fooSecondAttributeValue">
              FOO
              <bar>BAR</bar>
              <baz bazAttributeKey="bazAttributeValue"/>
            </foo>
        """.trimIndent()

        // When
        val rootXMLElement = XMLParser().parse(xml)

        // Then
        assertEquals("", rootXMLElement.name)
        assertEquals("", rootXMLElement.value)
        assertEquals(0, rootXMLElement.attributes.size)
        assertEquals(1, rootXMLElement.children.size)
        val fooXMLElement = rootXMLElement["foo"]
        assertEquals("foo", fooXMLElement.name)
        assertEquals("FOO", fooXMLElement.value)
        assertEquals(2, fooXMLElement.attributes.size)
        assertEquals("fooFirstAttributeValue", fooXMLElement.attributes["fooFirstAttributeKey"])
        assertEquals("fooSecondAttributeValue", fooXMLElement.attributes["fooSecondAttributeKey"])
        assertEquals(2, fooXMLElement.children.size)
        val barXMLElement = fooXMLElement["bar"]
        assertEquals("bar", barXMLElement.name)
        assertEquals("BAR", barXMLElement.value)
        assertEquals(0, barXMLElement.attributes.size)
        assertEquals(0, barXMLElement.children.size)
        val bazXMLElement = fooXMLElement["baz"]
        assertEquals("baz", bazXMLElement.name)
        assertEquals("", bazXMLElement.value)
        assertEquals(1, bazXMLElement.attributes.size)
        assertEquals("bazAttributeValue", bazXMLElement.attributes["bazAttributeKey"])
        assertEquals(0, bazXMLElement.children.size)
    }

    @Test(expected = Exception::class)
    fun parseMalformedXMLFails() {
        // Given
        val xml = "<malformed></xml>"

        // When
        val rootXMLElement = XMLParser().parse(xml)

        // Then
        // An exception is thrown
    }
}
