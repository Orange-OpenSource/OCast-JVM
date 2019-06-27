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

package org.ocast.core.wrapper

import org.ocast.core.models.Consumer

/**
 * This class is a simple implementation of a [CallbackWrapper].
 */
class SimpleCallbackWrapper : CallbackWrapper {

    override fun <T> wrap(consumer: Consumer<T>): Consumer<T> {
        return consumer
    }

    override fun wrap(runnable: Runnable): Runnable {
        return runnable
    }
}
