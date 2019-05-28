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

import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node

/**
 * Transforms a [NamedNodeMap] into a [Map] of [Node]s indexed by their names.
 * This makes it easier to use with chaining and higher-order functions such as map and filter.
 *
 * @return The [Map] of [Node]s indexed by their names.
 */
internal fun NamedNodeMap.asMap(): Map<String, Node> {
    return (0 until length).associate { index ->
        with(item(index)) { Pair(nodeName, this) }
    }
}