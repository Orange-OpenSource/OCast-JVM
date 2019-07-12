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

/**
 * Removes an element from an XML string.
 *
 * @param element The element to remove.
 * @return The modified XML string.
 */
internal fun String.removeXMLElement(element: String): String {
    val regularTagRegex = "\\h*<$element(\\s+.*)?>(.|\\R)*</$element>\\h*\\R".toRegex()
    val selfClosingTagRegex = "\\h*<$element(\\s+.*)?/>\\h*\\R".toRegex()

    return replace(regularTagRegex, "").replace(selfClosingTagRegex, "")
}
