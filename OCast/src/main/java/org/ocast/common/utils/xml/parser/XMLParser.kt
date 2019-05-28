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

package org.ocast.common.utils.xml.parser

import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * This class parses XML content.
 */
class XMLParser {

    /**
     * Parses an XML string.
     *
     * @param xml The XML string to parse.
     * @return The root [XMLElement].
     * @throws Exception If an error occurs while parsing the XML string.
     */
    @Throws(Exception::class)
    fun parse(xml: String): XMLElement {
        val documentBuilderFactory = DocumentBuilderFactory.newInstance()
        val documentBuilder = documentBuilderFactory.newDocumentBuilder()
        val document = documentBuilder.parse(InputSource(StringReader(xml)))

        return parseNode(document)
    }

    /**
     * Parses an XML [Node].
     *
     * @param node The XML [Node] to parse.
     * @return The [XMLElement] for this node.
     */
    private fun parseNode(node: Node): XMLElement {
        val childNodes = node.childNodes.asList()
        val name = node.nodeName
        val value = childNodes
            .firstOrNull { it.nodeType == Node.TEXT_NODE }
            ?.nodeValue
            ?.trim()
            .orEmpty()
        val attributes = node
            .attributes
            ?.asMap()
            ?.entries
            ?.associate { it.key to it.value.nodeValue }
            .orEmpty()
        val children = childNodes
            .filter { it.nodeType == Node.ELEMENT_NODE }
            .map { parseNode(it) }

        return XMLElement(name, value, attributes, children)
    }
}

/**
 * This class represents an XML element.
 *
 * @param name The name of the XML element.
 * @param value The value of the XML element.
 * @param attributes The attributes associated with the XML element. The value of each attribute is indexed by its name.
 * @param children The children for this XML element.
 */
data class XMLElement(
    val name: String,
    val value: String,
    val attributes: Map<String, String>,
    val children: List<XMLElement>
) {

    /**
     * Returns the first child element matching the given name.
     * @throws [NoSuchElementException] If no such XML element is found.
     */
    @Throws(NoSuchElementException::class)
    operator fun get(childName: String): XMLElement {
        return children.first { it.name == childName }
    }
}