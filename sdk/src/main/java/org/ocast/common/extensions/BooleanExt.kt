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

package org.ocast.common.extensions

/**
 * Returns the boolean if it is not null, or true otherwise.
 */
fun Boolean?.orTrue(): Boolean {
    return this ?: true
}

/**
 * Returns the boolean if it is not null, or false otherwise.
 */
fun Boolean?.orFalse(): Boolean {
    return this ?: false
}
