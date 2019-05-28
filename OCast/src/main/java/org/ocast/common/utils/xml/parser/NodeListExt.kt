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
import org.w3c.dom.NodeList

/**
 * Transforms a [NodeList] into a [List] of [Node]s.
 * This makes it easier to use with chaining and higher-order functions such as map and filter.
 *
 * @return The list of [Node]s.
 */
internal fun NodeList.asList(): List<Node> {
    return (0 until length).map { item(it) }
}

/**
 * Returns the node with the given [name].
 *
 * @param name The name of the node to find.
 * @return The found node, or null if it does not exist.
 */
internal fun NodeList.item(name: String): Node? {
    return asList().firstOrNull { it.nodeName == name }
}
