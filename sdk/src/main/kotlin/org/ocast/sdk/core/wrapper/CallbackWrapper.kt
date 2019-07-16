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

package org.ocast.sdk.core.wrapper

import org.ocast.sdk.core.models.Consumer

/**
 * An interface which is implemented by objects that wrap callbacks.
 *
 * For instance, this interface can be used to run callbacks on the main thread, which depends on the platform OCast is running on.
 */
interface CallbackWrapper {

    /**
     * Wraps a [Consumer] into a new one.
     *
     * @param T The type of the [Consumer] parameter.
     * @param consumer The consumer to wrap.
     * @return The wrapping consumer.
     */
    fun <T> wrap(consumer: Consumer<T>): Consumer<T>

    /**
     * Wraps a [Runnable] into a new one.
     *
     * @param runnable The runnable to wrap.
     * @return The wrapping runnable.
     */
    fun wrap(runnable: Runnable): Runnable
}
