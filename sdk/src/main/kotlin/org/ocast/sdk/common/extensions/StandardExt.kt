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

package org.ocast.sdk.common.extensions

/**
 * Calls the specified function [block] if `this` is not null.
 * This method is an alias to the [also] method from the standard library and is meant to be used in conjunction with the [orElse] method.
 *
 * @param block The function to execute if `this` is not null.
 * @return `this` value.
 */
inline fun <T> T?.ifNotNull(block: (T) -> Unit): T? {
    return this?.also(block)
}

/**
 * Calls the specified function [block] and returns its result is `this` is null.
 *
 * @param block The function to execute if `this` is null.
 * @return `this` if it is not null, or the [block] result if `this` is null.
 */
inline fun <R> R?.orElse(block: () -> R): R {
    return this ?: run(block)
}
